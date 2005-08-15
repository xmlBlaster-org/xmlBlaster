/*------------------------------------------------------------------------------
Name:      LowMemoryDetector.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Usage:     java -Xms1M -Xmx2M -Dcom.sun.management.jmxremote -Djava.util.logging.config.file=testlog.properties org.xmlBlaster.util.admin.extern.LowMemoryDetector
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.admin.extern;

import javax.management.ObjectName;
import javax.management.ObjectInstance;
import javax.management.QueryExp;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationEmitter;

// JDK 1.5
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryNotificationInfo;
import javax.management.NotificationListener;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.MemoryType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// Needed only for DefaultLowMemoryListener
import org.xmlBlaster.util.Global;

/**
 * Get notification when heap memory usage exceeds 90%. 
 * @since JDK 1.5 and xmlBlaster 1.0.7
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class LowMemoryDetector {
   private MBeanServer mbeanServer;
   private MemoryPoolMXBean pool;
   private double thresholdFactor;

   /**
    * Access the max available RAM for this JVM. 
    * You can increase it with 'java -Xmx256M ...'
    * @return bytes
    */
   public static long maxJvmMemory() {
       MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
       MemoryUsage usage = mbean.getHeapMemoryUsage() ;
       return usage.getMax();
   }

   /**
    * Default ctor for 90% threshold and registered DefaultLowMemoryListener. 
    */
   public LowMemoryDetector() {
      this((float)0.9);
      register(new DefaultLowMemoryListener());
   }

   /**
    * @param thresholdFactor Use typically 0.9, if 90% of heap is used up the
    * listener is triggered
    */
   public LowMemoryDetector(float thresholdFactor) {
      this.thresholdFactor = thresholdFactor;
      // http://java.sun.com/j2se/1.5.0/docs/api/java/lang/management/MemoryPoolMXBean.html
      this.mbeanServer = MBeanServerFactory.createMBeanServer();

      List list = ManagementFactory.getMemoryPoolMXBeans();
      Iterator it = list.iterator();
      while (it.hasNext()) {
         MemoryPoolMXBean tmpPool = (MemoryPoolMXBean)it.next();
         if (tmpPool.isUsageThresholdSupported() && tmpPool.getType().equals(MemoryType.HEAP)) {
            this.pool = tmpPool;
            // "Tenured Gen" = pool.getName()
            long myThreshold = (long)(this.thresholdFactor * (double)this.pool.getUsage().getMax()); //getCommitted());
            this.pool.setUsageThreshold(myThreshold);
            //System.out.println("Adding maxJvmMemory=" + maxJvmMemory() +
            //      ", committed for heap=" + this.pool.getUsage().getCommitted() +
            //      ", max for heap=" + this.pool.getUsage().getMax() +
            //      ", used threshold=" + this.pool.getUsageThreshold());
            break;
         }
      }

      register(new DefaultLowMemoryListener());
   }

   /**
    * Register your low memory listener. 
    */
   public void register(NotificationListener listener) {
      if (this.pool != null) {
         // register for notification ...
         MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
         NotificationEmitter emitter = (NotificationEmitter) mbean;
         emitter.addNotificationListener(listener, null, this.pool);
      }
   }

   /**
    * Tester: java -Xms1M -Xmx2M -Dcom.sun.management.jmxremote -DxmlBlaster/jmx/exitOnMemoryThreshold=true org.xmlBlaster.util.admin.extern.LowMemoryDetector
    */
   public static void main(String[] args) throws java.io.IOException {
      
      LowMemoryDetector mem = new LowMemoryDetector((float)0.9);
      mem.register(new DefaultLowMemoryListener());

      ArrayList list = new ArrayList();
      System.out.println("Hit a key");
      System.in.read();
      int chunkSize = 100000;
      try {
         for (int i=0; i<1000; i++) {
            System.in.read();
            System.out.println("Adding another junk " + chunkSize);
            byte[] buffer = new byte[chunkSize];
            list.add(buffer);
         }
      }
      catch(java.lang.OutOfMemoryError e) {
         System.out.println("OOOOO: " + e.toString());
         System.in.read();
      }
      System.out.println("DONE, hit a key to finish");
      System.in.read();
   }
}

   /**
    * The default handler just logs the situation. 
    */
   class DefaultLowMemoryListener implements NotificationListener {
      boolean exitOnThreshold;

      public DefaultLowMemoryListener() {
         this.exitOnThreshold = Global.instance().getProperty().get("xmlBlaster/jmx/exitOnMemoryThreshold", this.exitOnThreshold);
      }

      public void handleNotification(Notification notification, Object handback)  {
         String notifType = notification.getType();
         if (notifType.equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED)) {
            MemoryPoolMXBean pool = (MemoryPoolMXBean)handback;
            Global.instance().getLog("jmx").error("DefaultLowMemoryListener",
               "Memory is low! maxJvmMemory=" + Global.byteString(LowMemoryDetector.maxJvmMemory()) +
               ", committed for heap=" + Global.byteString(pool.getUsage().getCommitted()) +
               ", max for heap=" + Global.byteString(pool.getUsage().getMax()) +
               ", threshold reached=" + Global.byteString(pool.getUsageThreshold()) +
               ", count=" + pool.getUsageThresholdCount());
            if (this.exitOnThreshold) {
               Global.instance().getLog("jmx").error("DefaultLowMemoryListener",
                  "Exiting now because of low memory (see '-xmlBlaster/jmx/exitOnMemoryThreshold true'");
               System.exit(-9);
            }
         }
      }
   }


