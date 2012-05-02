package org.xmlBlaster.engine.event;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Logger;

import org.xmlBlaster.engine.EventPlugin;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * Execute a command
 * 
 * @author Marcel
 */
public class ExecHelper {
   private static Logger log = Logger.getLogger(ExecHelper.class.getName());
   private final EventPlugin eventPlugin;
   private String configuration;
   private String command;
   private String[] envArr;
   private File dir;
   private boolean waitFor;
   private boolean daemon = true;
   private String logSevereText = null;
   private int activationDelayAfterStartupSec;
   private int creationTimeSec;

   public ExecHelper(EventPlugin eventPlugin, String configuration) throws XmlBlasterException {
      this.eventPlugin = eventPlugin;
      this.configuration = configuration;
      @SuppressWarnings("unchecked")
      Map<String, String> map = StringPairTokenizer.parseLineToProperties(configuration);
      
      this.creationTimeSec = (int)(System.currentTimeMillis() / 1000l);

      // for example "cmd /c dir"
      this.command = map.get("command");

      // for example "user:jack;amount:200"
      String envp = map.get("envp");
      if (envp != null && envp.length() > 0) {
         this.envArr = StringPairTokenizer.toArray(envp, ";");
         for (int i = 0; i < this.envArr.length; i++) {
            this.envArr[i] = ReplaceVariable.replaceFirst(this.envArr[i], ":", "=");
         }
      } else {
         this.envArr = new String[0];
      }

      // Where to execute, for example "/home/xmlblaster"
      String dirStr = map.get("dir");
      if (dirStr != null && dirStr.length() > 0) {
         this.dir = new File(dirStr);
      }

      if ("true".equals(map.get("waitFor")))
         this.waitFor = true;

      if ("false".equals(map.get("daemon")))
         this.daemon = false; // jvm will not exit during shell execution
      
      this.logSevereText = map.get("logSevereText");
      
      String delay = map.get("activationDelayAfterStartupSec");
      if (delay != null) {
    	  try {
    		  this.activationDelayAfterStartupSec = Integer.valueOf(delay);
    	  }
    	  catch (NumberFormatException e) {
    		  log.warning(getLogId() + "Invalid activationDelayAfterStartupSec=" + delay + " setting is ignored");
    		  e.printStackTrace();
    	  }
      }
   }

   public void execute(String summary, String description, String eventType, String errorCode, SessionName sessionName) {
      if (this.command == null || this.command.length() == 0) {
         log.warning(getLogId() + "Command to execute is empty, nothing done");
         return;
      }
      
      if (this.activationDelayAfterStartupSec > 0) {
    	  int nowSec = (int)(System.currentTimeMillis() / 1000L);
    	  int runningSecs = nowSec - this.creationTimeSec;
    	  if (runningSecs < this.activationDelayAfterStartupSec) {
   	         log.warning(getLogId() + "Plugin is ignored as activationDelayAfterStartupSec=" + this.activationDelayAfterStartupSec + " is not reached");
   	         return;
    	  }
      }
      
      try {
         if (this.logSevereText != null && this.logSevereText.length() > 0) {
        	 log.severe(getLogId() + "Executing command eventType=" + eventType + ", errorCode=" + errorCode + ", sessionName=" + sessionName + " description=" + description + ": " + this.command + ": " + this.logSevereText);
         }
         else {
             log.info(getLogId() + "Executing command eventType=" + eventType + ", errorCode=" + errorCode + ", sessionName=" + sessionName + " description=" + description + ": " + this.command);
         }
         Runtime r = Runtime.getRuntime();
         Process p = null;
         String jvmPid = "";
         try {
            String name = ManagementFactory.getRuntimeMXBean().getName();
            if (name != null) {
               // 28906@localhost on SunJVM on Linux ok
               // I don't know on Windows or Mac or for another JVM
               int index = name.indexOf("@");
               if (index != -1) {
                  jvmPid = name.substring(0, index);
               }
            }
         } catch (Throwable e) {
            e.printStackTrace();
         }

         ArrayList<String> arrayList = new ArrayList<String>();
         if (summary != null && summary.length() > 0)
            arrayList.add("summary=" + summary);
         if (description != null && description.length() > 0)
            arrayList.add("description=" + description);
         if (eventType != null && eventType.length() > 0)
            arrayList.add("eventType=" + eventType);
         if (errorCode != null && errorCode.length() > 0)
            arrayList.add("errorCode=" + errorCode);
         if (sessionName != null)
            arrayList.add("sessionName=" + sessionName.getAbsoluteName());
         arrayList.add("jvmPid=" + jvmPid);
         arrayList.add("waitFor=" + this.waitFor);
         for (int i = 0; i < this.envArr.length; i++) {
            arrayList.add(this.envArr[i]);
         }

         String[] envArray = arrayList.toArray(new String[arrayList.size()]);

         if (envArray.length > 0 && this.dir != null) {
            p = r.exec(this.command, envArray, this.dir);
         } else if (envArray.length > 0) {
            p = r.exec(this.command, envArray);
         } else if (this.dir != null) {
            p = r.exec(this.command, new String[0], this.dir);
         } else {
            p = r.exec(this.command);
         }

         if (this.waitFor) {
            executableOutput(p, true);
            p.waitFor();
            int exitValue = p.exitValue();
            log.info(getLogId() + "Executing command is finished with exitValue=" + exitValue + ": " + this.command);
         } else {
            final Process process = p;
            Thread t = new Thread(new Runnable() {
               public void run() {
                  executableOutput(process, false);
               }
            }, "EventPlugin executable");
            t.setDaemon(this.daemon);
            t.start();
            // java.lang.IllegalThreadStateException: process hasn't exited
            // int exitValue = p.exitValue();
            log.info(getLogId() + "Executing command is spawned: " + this.command);
         }
      } catch (Throwable e) {
         log.warning(getLogId() + "Executing '" + this.command + "' failed, please check your configuration: " + e.toString());
         e.printStackTrace();
      }
   }
   
   private String getLogId() {
	   return "xmlBlasterPlugins.xml EventPlugin " + this.eventPlugin.getUniqueInstanceName() + " ";
   }

   private void executableOutput(Process p, boolean sync) {
      try {
         String prefix = sync ? "<sync> " : "<async> ";
         BufferedReader bri = new BufferedReader(new InputStreamReader(p.getInputStream()));
         BufferedReader bre = new BufferedReader(new InputStreamReader(p.getErrorStream()));
         String line;
         while ((line = bri.readLine()) != null) {
            log.info(prefix + this.command + ": " + line);
         }
         bri.close();
         while ((line = bre.readLine()) != null) {
            log.info(prefix + this.command + ": " + line);
         }
         bre.close();
      } catch (Throwable e) {
         log.warning(getLogId() + "Running '" + this.command + "' failed, please check your configuration: " + e.toString());
         e.printStackTrace();
      }
   }

   public String getCommand() {
      return command;
   }

   public void setCommand(String command) {
      this.command = command;
   }

   public String getConfiguration() {
      return configuration == null ? "" : configuration;
   }
}
