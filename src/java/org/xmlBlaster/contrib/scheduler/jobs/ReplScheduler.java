package org.xmlBlaster.contrib.scheduler.jobs;

import java.util.logging.Logger;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.replication.impl.ReplManagerPlugin;
import org.xmlBlaster.util.Global;

public class ReplScheduler implements Job {

   private final static Logger log = Logger.getLogger(ReplScheduler.class.getName());

   public ReplScheduler() {
      
   }
   
   public void execute(JobExecutionContext context) throws JobExecutionException {
      JobDataMap map = context.getJobDetail().getJobDataMap();
      String operation = map.getString("arg0"); // can not be null
      String prefix = map.getString("arg1"); // can be null 
      String dest = map.getString("arg2"); // can be null
      Global glob = (Global)map.get(GlobalInfo.ORIGINAL_ENGINE_GLOBAL);
      if (glob == null) {
         log.severe("Could not find the ServerScope: can not execute the scheduler job");
      }
      else {
         ReplManagerPlugin plugin = (ReplManagerPlugin)glob.getPluginRegistry().getPlugin(ReplManagerPlugin.getPluginName());
         if (plugin == null) {
            log.severe("Could not find singleton instance of ReplManagerPlugin: can not execute the scheduler job");
         }
         else {
            if ("startDispatcher".equals(operation)) {
               plugin.doExecuteSchedulerJob(true, prefix, dest);
            }
            else if ("stopDispatcher".equals(operation)) {
               plugin.doExecuteSchedulerJob(false, prefix, dest);
            }
            else {
               log.warning("Operation '" + operation + "' not recognized: not doing anything");
            }
         }
      }
   }
   
   
}
