/*------------------------------------------------------------------------------
Name:      CacheControlParam.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue.cache;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;


/**
 * Class used to configure and control the swapping behaviour (and performance)
 * of the cache queue.
 * 
 * @author michele@laghi.eu
 * @author xmlBlaster@marcelruff.info
 */
public class CacheControlParam
{

   /**
    * The maximum size of the queue. It is used as a reference for the other
    * parameter. The levels and the sizes should never exceed this value.
    */
   public long maxBytes;

   /**
    * The storeSwapLevel is the limit size in bytes over which the cache queue
    * should start to take entries from the transient store (commonly the
    * RAM queue) to store it on the persistent storage (commonly the Jdbc queue).
    */
   public long storeSwapLevel;

   /**
    * The storeSwapBytes is the size in bytes to take each time data is swapped
    * from the transient queue to the persistent queue.
    */
   public long storeSwapBytes;

   /**
    * The reloadSwapLevel is the limit size in bytes under which the cache queue
    * should start to take entries from the persistent store (commonly the
    * JDBC queue) to reload it into transient storage (commonly the RAM queue).
    */
   public long reloadSwapLevel;

   /**
    * The reloadSwapBytes is the size in bytes to take each time data is swapped
    * from the persistent queue to reload the transient queue.
    */
   public long reloadSwapBytes;

   /**
    * The number of milliseconds to wait during a put action. This would delay
    * entries in such a way that some swap cycles could be avoided, increasing
    * the overall throughput of the queue, limiting the CPU consumption and the
    * expensive disk i/o accesses.
    */
   public long putDamper =0L; // currently not used


   /**
    * Constructs a CacheControlParam by using the queue properties.
    */
   public CacheControlParam(QueuePropertyBase property) {
      this.storeSwapLevel = property.getStoreSwapLevel();
      this.storeSwapBytes = property.getStoreSwapBytes();
      this.reloadSwapLevel = property.getReloadSwapLevel();
      this.reloadSwapBytes = property.getReloadSwapBytes();
   }


}
