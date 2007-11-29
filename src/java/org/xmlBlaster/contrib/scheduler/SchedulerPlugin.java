package org.xmlBlaster.contrib.scheduler;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import org.quartz.TriggerUtils;
import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.InfoHelper;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.PluginInfo;

public class SchedulerPlugin extends GlobalInfo implements SchedulerPluginMBean, Job {

   private final static String[] WEEK_NAMES = new String[] { "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT" };
   private final static String[] MONTH_NAMES = new String[] { "___", "JAN", "FEB", "MAR", "APR", "MAY", "JUN", 
                                                                 "JUL", "AUG", "SEP", "OCT", "NOV", "DEC" };

   private final static Logger log = Logger.getLogger(SchedulerPlugin.class.getName());
   
   /**
    * 
    * 
    * 
    * The syntax for the configuration is as for crontab:
    *  http://www.adminschoice.com/docs/crontab.htm#Crontab%20file
    *  
    * .   *     *   *   *    *  command to be executed
    * .   -     -    -    -    -
    * .   |     |     |     |     |
    * .   |     |     |     |     +----- day of week (0 - 6) (Sunday=0)
    * .   |     |     |     +------- month (1 - 12)
    * .   |     |     +--------- day of month (1 - 31)
    * .   |     +----------- hour (0 - 23)
    * .   +------------- min (0 - 59) 
    *   
   */
   public class CronData {
      
      private int min = -1;
      private int hour = -1;
      private int dayOfMonth = -1;
      private int dayOfWeek = -1;
      private int month = -1;
      private String command;
      private String[] arguments; // optional
      private final String name;
      private String rawText;
      
      public CronData(String name) {
         this.name = name;
      }
      
      private int getInt(StringTokenizer tokenizer) {
         try {
            String token = tokenizer.nextToken().trim();
            if ("*".equals(token))
               return -1;
            return Integer.parseInt(token);
         }
         catch (NumberFormatException ex) {
            ex.printStackTrace();
            return -1;
         }
      }
      
      private int getInt(StringTokenizer tokenizer, String[] names) {
         String token = tokenizer.nextToken().trim().toUpperCase();
         for (int i=0; i < names.length; i++) {
            if (token.startsWith(names[i]))
               return i;
         }
         try {
            if ("*".equals(token))
               return -1;
            return Integer.parseInt(token);
         }
         catch (NumberFormatException ex) {
            ex.printStackTrace();
            return -1;
         }
      }
      
      public void parse(String txt) throws XmlBlasterException {
         rawText = txt;
         StringTokenizer tokenizer = new StringTokenizer(txt, " ");
         int nmax = tokenizer.countTokens(); 
         if (nmax < 6)
            throw new XmlBlasterException(global, ErrorCode.RESOURCE, "SchedulerPlugin.doInit", "The string '" + txt + "' could not be parsed as a cron syntax");
         min = getInt(tokenizer);
         hour = getInt(tokenizer);
         dayOfMonth = getInt(tokenizer);
         dayOfWeek = getInt(tokenizer, WEEK_NAMES);
         month = getInt(tokenizer, MONTH_NAMES);
         
         command = tokenizer.nextToken().trim();
         int numArgs = nmax - 6;
         arguments = new String[numArgs];
         int i=0;
         while (tokenizer.hasMoreTokens()) {
            arguments[i] = tokenizer.nextToken().trim();
            i++;
         }
      }

      public String[] getArguments() {
         return arguments;
      }

      public String getCommand() {
         return command;
      }

      public int getDayOfWeek() {
         return dayOfWeek;
      }

      public int getDayOfMonth() {
         return dayOfMonth;
      }

      public int getHour() {
         return hour;
      }

      public int getMin() {
         return min;
      }

      public int getMonth() {
         return month;
      }
      
      public String getName() {
         return name;
      }
      
      public String getRawText() {
         return rawText;
      }
      
      public String toString() {
         StringBuffer buf = new StringBuffer(128);
         buf.append(name).append("\t");
         buf.append(rawText).append("\t(");
         if (min == -1)
            buf.append("* ");
         else
            buf.append(min);
         if (month != -1) {
            buf.append(" YEARLY TRIGGER NOT SUPPORTED");
            return buf.toString();
         }
         if (dayOfMonth != -1) {
            buf.append(" MONTLY ");
            buf.append(dayOfMonth).append(" ").append(hour).append(":").append(min);
         }
         else if (dayOfWeek != -1) {
            buf.append(" WEEKLY ");
            buf.append(WEEK_NAMES[dayOfWeek]).append(" ").append(hour).append(":").append(min);
         }
         else if (hour != -1) {
            buf.append(" DAILY ");
            buf.append(hour).append(":").append(min);

         }
         else if (min != -1) {
            buf.append(" HOURLY every hour at ");
            buf.append(min).append(" minute");
         }
         else {
            buf.append(" NOT SUPPORTED SINCE NOTHING CHOOSEN");
         }
         buf.append(")");
         return buf.toString();
      }
   }
   
   private Scheduler sched;
   private Map cronDataMap = new HashMap();
   private Object mbeanHandle;
   
   public SchedulerPlugin() {
      super((String[])null);
      
   }
   
   private void startSchedule(Scheduler sched, String name, CronData data) throws XmlBlasterException {
      try {
         log.info("Starting scheduler " + data.toString());
         Class clazz = Class.forName(data.getCommand());
         JobDetail jobDetail = new JobDetail(name, null, clazz);
         Object obj = global.getObjectEntry(ORIGINAL_ENGINE_GLOBAL);
         if (obj != null)
            jobDetail.getJobDataMap().put(ORIGINAL_ENGINE_GLOBAL, obj);
         else
            throw new XmlBlasterException(global, ErrorCode.INTERNAL, "SchedulerPlugin.doInit", "Could not find the ServerScope");
         String triggerName = name;
         Trigger trigger = null;
         
         if (data.getMonth() > -1)
            throw new XmlBlasterException(global, ErrorCode.USER_CONFIGURATION, "SchedulerPlugin.doInit", "Yearly Events are not implemented");

         int dayOfMonth = data.getDayOfMonth(); 
         int hour = data.getHour();
         int min = data.getMin();
         if (dayOfMonth > -1) { // monthly trigger
            if (hour < 0)
               throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, "SchedulerPlugin.doInit", "On monthly triggers the hour must be specified");
            if (min < 0)
               throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, "SchedulerPlugin.doInit", "On monthly triggers the min must be specified");
            trigger = TriggerUtils.makeMonthlyTrigger(triggerName, dayOfMonth, hour, min);
            trigger.setStartTime(new Date());  // start now
         }
         else {
            int dayOfWeek = data.getDayOfWeek();
            if (dayOfWeek > -1) {
               if (hour < 0)
                  throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, "SchedulerPlugin.doInit", "On weekly triggers the hour must be specified");
               if (min < 0)
                  throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, "SchedulerPlugin.doInit", "On weekly triggers the min must be specified");
               trigger = TriggerUtils.makeWeeklyTrigger(triggerName, dayOfWeek, hour, min);
               trigger.setStartTime(new Date());  // start now
            }
            else {
               if (hour > -1) {
                  if (min < 0)
                     throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, "SchedulerPlugin.doInit", "On daily triggers the min must be specified");
                  trigger = TriggerUtils.makeDailyTrigger(triggerName, hour, min);
                  trigger.setStartTime(new Date());  // start now
               }
               else {
                  if (min > -1) {
                     trigger = TriggerUtils.makeHourlyTrigger(1);
                     trigger.setName(triggerName);
                     Date startTime = TriggerUtils.getNextGivenMinuteDate(new Date(), min);
                     trigger.setStartTime(startTime);
                  }
                  else {
                     throw new XmlBlasterException(global, ErrorCode.USER_CONFIGURATION, "SchedulerPlugin.doInit", "No time has been specified in the configuration");
                  }
               }
               
            }
         }
         String[] args = data.getArguments();
         for (int i=0; i < args.length; i++)
            jobDetail.getJobDataMap().put("arg" + i, args[i]);
         sched.scheduleJob(jobDetail, trigger);
         cronDataMap.put(name, data);
      }
      catch (ClassNotFoundException ex) {
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE, "", "SchedulerPlugin.doInit", ex);
      }
      catch (SchedulerException ex) {
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE, "", "SchedulerPlugin.doInit", ex);
      }
   }

   private synchronized CronData addSchedulerWithEx(String name, String value) throws SchedulerException, XmlBlasterException {
      CronData data = new CronData(name);
      data.parse(value);
      startSchedule(sched, name, data);
      return data;
   }
   
   public synchronized String removeScheduler(String name) {
      try {
         boolean ret = sched.deleteJob(name, null);
         String val = null;
         if (ret)
            val = "Scheduler '" + name + "' successfully removed";
         else
            val = "Scheduler '" + name + "' could not be removed: are you sure it existed ?";
         cronDataMap.remove(name);
         return val;
      }
      catch (SchedulerException ex) {
         ex.printStackTrace();
         return new String("Could not remove scheduler '" + name + "' because of exception : " + ex.getMessage());
      }
   }
   
   protected void doInit(Global glob, PluginInfo plugInfo) throws XmlBlasterException {
      SchedulerFactory schedFact = new org.quartz.impl.StdSchedulerFactory();
      try {
         String instanceName = getType();
         ContextNode contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG, instanceName,
               this.global.getContextNode());
         if (!this.global.isRegisteredMBean(contextNode))
            this.mbeanHandle = this.global.registerMBean(contextNode, this);
         
         sched = schedFact.getScheduler();
         sched.start();
         String prefix = "scheduler.";
         Map map = InfoHelper.getPropertiesStartingWith(prefix, this, null);
         String[] keys = (String[])map.keySet().toArray( new String[map.size()]);
         for (int i=0; i < keys.length; i++) {
            String value = get("scheduler." + keys[i], "").trim();
            addSchedulerWithEx(keys[i], value);
         }
      }
      catch (SchedulerException ex) {
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE, "", "SchedulerPlugin.doInit", ex);
      }

   }
  
   public void execute(JobExecutionContext context) throws JobExecutionException {
      String jobName = context.getJobDetail().getName();
      String triggerName = context.getTrigger().getName();
      Date date = context.getFireTime();
      
      log.severe("");
      log.severe("SCHEDULER: FIRING trigger='" + triggerName + "' jobName='" + jobName + "' on date '" + date + "'");
      log.severe("");
   }
   
   public void shutdown() throws XmlBlasterException {
      try {
         global.unregisterMBean(mbeanHandle);
         sched.shutdown();
      }
      catch (SchedulerException ex) {
         ex.printStackTrace();
      }
      super.shutdown();
   }
   
   public String getSchedulerList() {
      StringBuffer buf = new StringBuffer(1024);
      CronData[] datas = null;
      synchronized(this) {
         datas = (CronData[])cronDataMap.values().toArray(new CronData[cronDataMap.size()]);
      }
      for (int i=0; i < datas.length; i++) {
         buf.append(datas[i].toString()).append("\n");
      }
      return buf.toString();
   }
   
   public String addScheduler(String name, String value) {
      try {
         removeScheduler(name); // make sure it removes it if it already exists
         CronData data = addSchedulerWithEx(name, value);
         return "Successfully added scheduler : " + data.toString(); 
      }
      catch (SchedulerException ex) {
         ex.printStackTrace();
         return "addScheduler failed because of an exception: "  + ex.getMessage();
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         return "addScheduler failed because of an exception: "  + ex.getMessage();
      }
   }
   
   public String getJobNames() throws SchedulerException {
      String[] names = this.sched.getJobNames(null);
      StringBuffer buf = new StringBuffer(128);
      for (int i=0; i < names.length; i++)
         buf.append(names[i]).append(" ");
      return buf.toString();
   }

}
