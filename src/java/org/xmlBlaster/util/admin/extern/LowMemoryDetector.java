/*------------------------------------------------------------------------------
Name:      LowMemoryDetector.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Usage:     java -Xms1M -Xmx2M -Dcom.sun.management.jmxremote -Djava.util.logging.config.file=testlog.properties org.xmlBlaster.util.admin.extern.LowMemoryDetector
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.admin.extern;

// JDK 1.5
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;

import org.xmlBlaster.util.Global;
// Needed only for DefaultLowMemoryListener

/**
 * Get notification when heap memory usage exceeds 90%. 
 * Configuration:
 * <pre>
-xmlBlaster/jmx/observeLowMemory       Write a log error when 90% of the JVM memory is used (JDK >= 1.5) [true]
-xmlBlaster/jmx/memoryThresholdFactor  Configure the log error memory threshhold (defaults to 90%) (JDK >= 1.5) [0.9]
-xmlBlaster/jmx/exitOnMemoryThreshold  If true xmlBlaster stops if the memoryThresholdFactor is reached (JDK >= 1.5) [false]
 * </pre>
 * @since JDK 1.5 and xmlBlaster 1.0.7
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class LowMemoryDetector {
   private final static Logger log = Logger.getLogger(LowMemoryDetector.class.getName());
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

   public MBeanServer getMBeanServer() {
      return this.mbeanServer;
   }

   /**
    * Default ctor for 90% threshold and registered DefaultLowMemoryListener. 
    */
   public LowMemoryDetector() {
      this((float)0.9);
      register(new DefaultLowMemoryListener(this));
   }

   /**
    * @param thresholdFactor Use typically 0.9, if 90% of heap is used up the
    * listener is triggered
    */
   public LowMemoryDetector(float thresholdFactor) {
      this.thresholdFactor = thresholdFactor;
      // http://java.sun.com/j2se/1.5.0/docs/api/java/lang/management/MemoryPoolMXBean.html
      this.mbeanServer = MBeanServerFactory.createMBeanServer();//("org.xmlBlaster");

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

      register(new DefaultLowMemoryListener(this));
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
    * Tester: java -Xms2M -Xmx3M -Dcom.sun.management.jmxremote -DxmlBlaster/jmx/exitOnMemoryThreshold=true org.xmlBlaster.util.admin.extern.LowMemoryDetector
    */
   public static void main(String[] args) throws java.io.IOException {
      
      LowMemoryDetector mem = new LowMemoryDetector((float)0.9);
      mem.register(new DefaultLowMemoryListener(mem));

      ArrayList list = new ArrayList();
      System.out.println("Hit a key to start");
      System.in.read();
      int chunkSize = 100000;
      try {
         for (int i=0; i<1000; i++) {
            System.out.println("Hit a key to allocate next " + chunkSize + " bytes");
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
    * The default handler just logs the situation or exits if configured.  
    */
   class DefaultLowMemoryListener implements NotificationListener {
      private final static Logger log = Logger.getLogger(DefaultLowMemoryListener.class.getName());
      boolean exitOnThreshold;
      MBeanServer mbeanServer;

      public DefaultLowMemoryListener(LowMemoryDetector lowMemoryDetector) {
         this.exitOnThreshold = Global.instance().getProperty().get("xmlBlaster/jmx/exitOnMemoryThreshold", this.exitOnThreshold);
         this.mbeanServer = lowMemoryDetector.getMBeanServer();
      }

      /**
       * Called when memory threshold is reached. 
       */
      public void handleNotification(Notification notification, Object handback)  {
         try {
            String notifType = notification.getType();
            if (!notifType.equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED))
               return;

            MemoryPoolMXBean pool = (MemoryPoolMXBean)handback;

            int numTries = 5;
            for (int i=0; i<numTries; i++) {
               // Memory is low! maxJvmMemory=8.323 MBytes, max for heap=7.340 MBytes, otherMem=983040, threshold reached=6.606 MBytes,
               // Runtime.totalMemory=8323072, Runtime.freeMemory=1461904, usedMem=6.861 MBytes
               if (log.isLoggable(Level.FINE))
                  log.fine(
                  "Memory is low! maxJvmMemory=" + Global.byteString(LowMemoryDetector.maxJvmMemory()) +
                  ", max for heap=" + Global.byteString(pool.getUsage().getMax()) +
                  ", otherMem=" + Global.byteString(LowMemoryDetector.maxJvmMemory() - pool.getUsage().getMax()) +  // 8.323-7.340=0.983
                  ", threshold reached=" + Global.byteString(pool.getUsageThreshold()) +
                  ", Runtime.totalMemory=" + Global.byteString(Runtime.getRuntime().totalMemory()) +
                  ", Runtime.freeMemory=" + Global.byteString(Runtime.getRuntime().freeMemory()) +
                  ", usedMem=" + Global.byteString(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));

               System.gc();
               try { Thread.sleep(1); } catch (Exception e) {}

               long usedMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
               if (usedMem < pool.getUsageThreshold()) {
                  if (log.isLoggable(Level.FINE))
                     log.fine("Low memory: Nothing to do, the garbage collector has handled it usedMem=" + Global.byteString(usedMem) + " threshold=" + Global.byteString(pool.getUsageThreshold()));
                  return;  // Nothing to do, the garbage collector has handled it
               }
            }

            long usedMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());

            //Memory is low! maxJvmMemory=8.323 MBytes, committed for heap=7.340 MBytes, max for heap=7.340 MBytes,
            //threshold reached=6.606 MBytes, currently used=7.595 MBytes, count=2.
            //Physical RAM size is 1.060 GBytes, this JVM may use max 8.323 MBytes and max 1024 file descriptors
            log.severe("Memory is low! maxJvmMemory=" + Global.byteString(LowMemoryDetector.maxJvmMemory()) +
               //", committed for heap=" + Global.byteString(pool.getUsage().getCommitted()) +
               ", max for heap=" + Global.byteString(pool.getUsage().getMax()) +
               ", threshold reached=" + Global.byteString(pool.getUsageThreshold()) +
               ", currently used=" + Global.byteString(usedMem) +
               ", count=" + pool.getUsageThresholdCount() +
               ". Physical RAM size is " + Global.byteString(Global.totalPhysicalMemorySize) + "," +
               " this JVM may use max " + Global.byteString(Global.heapMemoryUsage) +
               " and max " + Global.maxFileDescriptorCount + " file descriptors");
            if (this.exitOnThreshold) {
               System.gc();
               try { Thread.sleep(1); } catch (Exception e) {}
               System.gc();
               try { Thread.sleep(1); } catch (Exception e) {}
               usedMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
               if (usedMem > pool.getUsageThreshold()) {
                  log.severe("Exiting now because of low memory (see '-xmlBlaster/jmx/exitOnMemoryThreshold true'");
                  System.exit(-9);
               }
               log.info("Garbage collected to usedMem=" + Global.byteString(usedMem) + ", we continue");
            }
         }
         catch (Throwable e) {
            e.printStackTrace();
         }
      }
   } // class DefaultLowMemoryListener


