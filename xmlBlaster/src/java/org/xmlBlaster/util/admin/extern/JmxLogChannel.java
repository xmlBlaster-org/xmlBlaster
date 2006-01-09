/*------------------------------------------------------------------------------
Name:      JmxLogChannel.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.admin.extern;

import org.jutils.log.*;
import org.xmlBlaster.util.Global;
import javax.management.*;

/**
 * MBean for the Logchannel
 */
public class JmxLogChannel implements JmxLogChannelMBean, org.jutils.log.LogableDevice, NotificationBroadcaster {

  private NotificationBroadcasterSupport broadcaster = new NotificationBroadcasterSupport();
  private long notificationSequence = 0;

  private String ME = "LogChannel";
  public String logText = "";
  private Global glob;
  private StringBuffer sbLogText = new StringBuffer();
  private LogChannel log;


  public void addNotificationListener( NotificationListener listener, NotificationFilter filter, Object handback) {
    broadcaster.addNotificationListener(listener, filter, handback);
  }

  public void removeNotificationListener( NotificationListener listener ) throws ListenerNotFoundException {
    broadcaster.removeNotificationListener(listener);
  }

  public MBeanNotificationInfo[] getNotificationInfo() {
    return new MBeanNotificationInfo[] {
      new MBeanNotificationInfo(
                                new String[]
                                {"org.xmlBlaster.util.admin.extern.JmxLogChannel"},
                                Notification.class.getName(),
                                "Log Notifications"
                                )
      };
  }

  public JmxLogChannel() {
     this.glob = new Global();
     this.log = glob.getLog("core");
     log.addLogDevice(this);
  }

  public JmxLogChannel(Global glob) {
     this.glob = glob;
     this.log = glob.getLog("core");
     log.addLogDevice(this);
   }

   public void addGlobal(org.xmlBlaster.util.Global glob) {
     this.glob = glob;
   }

  public void log(int level, String source, String str)
  {
       str = LogChannel.bitToLogLevel(level) + " [" + source + "] " + str;
     System.out.println("Log des JmxLogChannels: " + str);
     sbLogText.append(str + "\n");
   }

   //management:
   public String getLogText() {
     return  sbLogText.toString();
   }

   public void print() {
     System.out.println("Log: " + sbLogText);
   }

   public void setLogLevel(int level) {
     log.addLogLevel(level);
   }

   public void addErrorLevel(){
     log.addLogLevel(LogChannel.LOG_ERROR);
     log.info(ME,"ErrorLevel added");
     fireNotification();
   }

   public void removeErrorLevel() {
     log.removeLogLevel(LogChannel.LOG_ERROR);
     log.info(ME,"ErrorLevel removed");
     fireNotification();
   }

   public void addDumpLevel(){
     log.addLogLevel(LogChannel.LOG_DUMP);
     log.info(ME,"DumpLevel added");
     fireNotification();
   }

   public void removeDumpLevel(){
     log.removeLogLevel(LogChannel.LOG_DUMP);
     log.info(ME,"Dumplevel removed");
     fireNotification();
   }

   public void clearLocalLog() {
     sbLogText = null;
     sbLogText = new StringBuffer();
     log.info(ME,"clear Log");
     fireNotification();
   }

   private void fireNotification() {
     broadcaster.sendNotification(
       new AttributeChangeNotification(
       this,
       ++notificationSequence,
       System.currentTimeMillis(),
       "logLevel changed",
       "logText",
       String.class.getName(),
       "",
       ""
       )
    );
   }


}