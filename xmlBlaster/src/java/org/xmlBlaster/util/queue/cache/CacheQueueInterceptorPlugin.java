/*------------------------------------------------------------------------------
Name:      CacheQueueInterceptorPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue.cache;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queue.I_QueuePutListener;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.engine.helper.QueuePropertyBase;
import org.xmlBlaster.engine.helper.Constants;

import java.util.ArrayList;
import org.xmlBlaster.util.queue.QueuePluginManager;

import org.xmlBlaster.util.queue.jdbc.I_ConnectionListener;
// currently only for a dump ...
import org.xmlBlaster.util.queue.ram.RamQueuePlugin;

/**
 * @author laghi@swissinfo.org
 * @author xmlBlaster@marcelruff.info
 */
public class CacheQueueInterceptorPlugin implements I_Queue, I_Plugin, I_ConnectionListener
{
   private String ME;
   private LogChannel log;
   private QueuePropertyBase property;
   private boolean notifiedAboutAddOrRemove = false;
   boolean isDown = true;
   private StorageId queueId;
   private I_QueuePutListener putListener;

   private I_Queue transientQueue;
   private I_Queue persistentQueue;
   private boolean hasPersistentEntries = false;
   private boolean isSwapping = false;
   private Global glob;
   private boolean isConnected = false;

   private Object deleteDeliveredMonitor = new Object();
   private Object storeNewDurableRecoveryMonitor = new Object();
   private Object swappingPutMonitor = new Object();
   /**
    * this boolean is set only under the time a recovery afer having reconnected
    * to the DB. It is used to limit the synchronization
    */
   private boolean storeNewDurableRecovery = false;
   /** object used to control the swapping performance */
   private CacheControlParam controlParam;


   /**
    * @see I_ConnectionListener#disconnected()
    */
   public void disconnected() {
      this.log.call(ME, "disconnected");
      this.isConnected = false;
   }


   /**
    * @see I_ConnectionListener#disconnected()
    */
   public void reconnected() {
      this.log.call(ME, "reconnected");
     /* remove all obsolete messages from the persitence. Obsolete are the
      * entries which are lower (lower priority and older) than the lowest
      * entry in the transient storage.
      */

      if (this.persistentQueue == null) return; // should never happen

      try {
         synchronized(this.deleteDeliveredMonitor) {
            I_QueueEntry limitEntry = this.transientQueue.peek();
            ArrayList list = this.persistentQueue.peekWithLimitEntry(limitEntry);
            this.persistentQueue.removeRandom((I_Entry[])list.toArray(new I_Entry[list.size()]));
         }

         // add all new persistent entries to the persistent storage ...
         this.storeNewDurableRecovery = true;
         synchronized(this.storeNewDurableRecoveryMonitor) {
            I_QueueEntry limitEntry = this.persistentQueue.peek();
            ArrayList list = this.transientQueue.peekWithLimitEntry(limitEntry);
            this.persistentQueue.put((I_QueueEntry[])list.toArray(new I_QueueEntry[list.size()]), false);
         }
         this.storeNewDurableRecovery = false;

         this.isConnected = true;
      }
      catch (XmlBlasterException ex) {
         this.log.error(ME, "exception occured when reconnecting. " + ex.toString());
      }
   }


   /**
    * Is called after the instance is created.
    * @param uniqueQueueId A unique name, allowing to create a unique name for a persistent store (e.g. file name)
    * @see I_Queue#initialize(StorageId, Object)
    */
   public void initialize(StorageId uniqueQueueId, Object userData)
      throws XmlBlasterException
   {
      if (this.isDown) {

         this.property = null;
//         Global glob = this.property.getGlobal();
         glob = ((QueuePropertyBase)userData).getGlobal();
         this.log = glob.getLog("queue");
         this.ME = this.getClass().getName() + "-" + uniqueQueueId;
         this.queueId = uniqueQueueId;

         QueuePluginManager pluginManager = glob.getQueuePluginManager();
         QueuePropertyBase queuePropertyBase = (QueuePropertyBase)userData;

         //instantiate and initialize the underlying queues

         String defaultTransient = glob.getProperty().get("queue.cache.transientQueue", "RAM,1.0");
         this.transientQueue = pluginManager.getPlugin(defaultTransient, uniqueQueueId, createRamCopy(queuePropertyBase));
         //log.error(ME, "Debug only: " + this.transientQueue.toXml(""));
         
         try {
            String defaultPersistent = glob.getProperty().get("queue.cache.persistentQueue", "JDBC,1.0");
            this.persistentQueue = pluginManager.getPlugin(defaultPersistent, uniqueQueueId, queuePropertyBase);

            this.isConnected = true;
            // to be notified about reconnections / disconnections
            this.glob.getJdbcQueueManager(this.queueId).registerListener(this);
         }
         catch (XmlBlasterException ex) {
            this.log.error(ME, "could not initialize the persistent queue. Is the JDBC Driver jar file in the CLASSPATH ? Is the DB up and running ?");
            // start a polling thread to see if the connection can be established later 

         }

         // do the queue specific stuff like delete all volatile entries in
         // the persistent queue
         if (this.persistentQueue != null) {
            try {
               this.persistentQueue.removeTransient();
            }
            catch (XmlBlasterException ex) {
               this.log.error(ME, "could not remove transient entries (swapped entries) probably due to no connection to the DB, or the DB is down");
            }

            setProperties(userData);
            // not used yet
            this.controlParam = new CacheControlParam((QueuePropertyBase)getProperties());

            if (this.persistentQueue.getNumOfEntries() > 0) {
               this.hasPersistentEntries = true;

               // initial fill of RAM queue ...
               long maxSize = this.transientQueue.getMaxNumOfBytes();
               // this.transientQueue.getMaxNumOfEntries();
               int maxEntries = -1;

               ArrayList entries = null;
               try {
                  entries = this.persistentQueue.peek(maxEntries, maxSize);
               }
               catch (XmlBlasterException ex) {
                  this.log.error(ME, "could not reload data from persistence probably due to a broken connection to the DB or the DB is not up and running");
               }
               I_QueueEntry[] cleanEntries = (I_QueueEntry[])entries.toArray(new I_QueueEntry[entries.size()]);
               this.transientQueue.put(cleanEntries, false);
            }
         } // persistentQueue!=null
         this.isDown = false;
         log.info(ME, "Successful initialized");
      } // isDown?
   }

   /**
    * We set the cache props to the real props for RAM queue running under a cacheQueue
    */
   private QueuePropertyBase createRamCopy(QueuePropertyBase queuePropertyBase) {
      QueuePropertyBase ramCopy = (QueuePropertyBase)queuePropertyBase.clone();
      ramCopy.setMaxMsg(queuePropertyBase.getMaxMsgCache());
      ramCopy.setMaxBytes(queuePropertyBase.getMaxBytesCache());
      ramCopy.setCacheQueue(true);
      return ramCopy;
   }

   /**
    * @see I_Queue#setProperties(Object)
    */
   public void setProperties(Object userData) throws XmlBlasterException {
      if (userData == null) return;
      QueuePropertyBase newProp;
      try {
         newProp = (QueuePropertyBase)userData;
      }
      catch(Throwable e) {
         log.error(ME, "Can't configure queue, your properties are invalid: " + e.toString());
         return;
      }

      /* Do we need to protect against shrinking?
      if (this.property != null && this.property.getMaxMsg() > newProp.getMaxMsg()) {
         log.warn(ME, "Reconfigure of a RamQueuePlugin - getMaxNumOfEntries from " + this.property.getMaxMsg() +
                    " to " + newProp.getMaxMsg() + " is not supported, we ignore the new setting.");
         return;
      }
      */

      this.property = newProp;
      this.transientQueue.setProperties(createRamCopy((QueuePropertyBase)userData));
      if (this.persistentQueue != null) this.persistentQueue.setProperties(userData);
   }

   /**
    * Access the current queue configuration
    */
   public Object getProperties() {
      return this.property;
   }

   public void setNotifiedAboutAddOrRemove(boolean notify) {
      this.notifiedAboutAddOrRemove = notify;
   }

   public boolean isNotifiedAboutAddOrRemove() {
      return this.notifiedAboutAddOrRemove;
   }

   /**
    * @see I_Queue#addPutListener(I_QueuePutListener)
    */
   public void addPutListener(I_QueuePutListener l) {
      if (l == null)
         throw new IllegalArgumentException(ME + ": addPustListener(null) is not allowed");
      if (this.putListener != null)
         throw new IllegalArgumentException(ME + ": addPustListener() failed, there is a listener registered already");
      this.putListener = l;
   }

   /**
    * @see I_Queue#removePutListener(I_QueuePutListener)
    */
   public void removePutListener(I_QueuePutListener l) {
      this.putListener = null;
   }

   /**
    * returns the persitent queue (null if no one defined)
    */
   public I_Queue getPersistentQueue() {
      return this.persistentQueue;
   }

   /**
    * returns the transient queue (null if no one defined)
    */
   public I_Queue getTransientQueue() {
      return this.transientQueue;
   }


   /**
    * Gets the references of the entries in the queue. Note that the data
    * which is referenced here may be changed by other threads.
    */
   public long[] getEntryReferences() throws XmlBlasterException {
      // currently not implemented
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "getEntryReferences not implemented");
   }

   /**
    * @see I_Queue#getEntries()
    */
   public ArrayList getEntries() throws XmlBlasterException {
      // currently not implemented
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "getEntries not implemented");
   }


   /**
    * @see I_Queue#put(I_QueueEntry, boolean)
    */
   public Object put(I_QueueEntry queueEntry, boolean ignorePutInterceptor)
      throws XmlBlasterException {
      I_QueueEntry[] entries = new I_QueueEntry[1];
      entries[0] = queueEntry;
      return put(entries, ignorePutInterceptor);
   }

    
   /**
    * All entries are stored into the transient queue. All durable messages are
    * stored also in the persistent queue. The exceeding size in the transient
    * queue is calculated. If it is positive it means we need to swap. The
    * overflowing messages are taken from the ram queue. The volatile between
    * them are stored in the persistent storage (since the durable ones have
    * been previously stored).
    * 
    * @see I_Queue#put(I_QueueEntry[], boolean)
    */
   public Object[] put(I_QueueEntry[] queueEntries, boolean ignorePutInterceptor)
      throws XmlBlasterException {
      if (queueEntries == null || queueEntries.length < 1) return null;
      XmlBlasterException e = null;
      if ((this.putListener != null) && (!ignorePutInterceptor)) {
         // Is an interceptor registered (and not bypassed) ?
         return this.putListener.put(queueEntries);
      }

      if (getNumOfEntries() > getMaxNumOfEntries()) { // Allow superload one time only
         String reason = "Queue overflow (number of entries), " + getNumOfEntries() +
                         " messages are in queue, try increasing '" +
                         this.property.getPropName("maxMsg") + "' on client login.";
         if (log.TRACE) log.trace(ME, reason);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
      }
      if (this.getNumOfBytes() > getMaxNumOfBytes()) { // Allow superload one time only
         String reason = "Queue overflow, " + getMaxNumOfBytes() +
                         " bytes are in queue, try increasing '" + 
                         this.property.getPropName("maxBytes") + "' on client login.";
         if (log.TRACE) log.trace(ME, reason);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
      }

      long sizeOfEntries = 0L;

      synchronized(this.deleteDeliveredMonitor) {

         // separate durable from transient messages and store the durables in persistence
         if (this.persistentQueue != null && this.isConnected) {
            ArrayList durablesFromEntries = new ArrayList();
            long sizeOfDurables = 0L;
            for (int i=0; i < queueEntries.length; i++) {
               if (queueEntries[i].isDurable()) {
                  durablesFromEntries.add(queueEntries[i]);
                  sizeOfDurables += ((I_QueueEntry)queueEntries[i]).getSizeInBytes();
               }
               else sizeOfEntries += queueEntries[i].getSizeInBytes();
            }
            sizeOfEntries += sizeOfDurables;

            if (durablesFromEntries.size() > 0) {
               this.hasPersistentEntries = true;

               long spaceLeft = this.persistentQueue.getMaxNumOfBytes() - this.persistentQueue.getNumOfBytes();
               if (spaceLeft < sizeOfDurables) {
                  String reason = "Durable queue overflow, " + this.getNumOfBytes() +
                                  " bytes are in queue, try increasing '" + 
                                  this.property.getPropName("maxBytes") + "' on client login.";
                  this.log.warn(ME, reason + this.toXml(""));
                  throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
               }
               try {
                  this.persistentQueue.put((I_QueueEntry[])durablesFromEntries.toArray(new I_QueueEntry[durablesFromEntries.size()]), ignorePutInterceptor);
               }
               catch (XmlBlasterException ex) {
                  this.log.error(ME, "put: an error occured when writing to the persistent queue: " + durablesFromEntries.size() + " durable entries will temporarly be handled as transient. Is the DB up and running ? " + ex.toString() + "state "  + this.toXml(""));
                  // should an exception be rethrown here ?
               }
            }
         }

         // check if swapping before putting data into ram queue allows to avoid
         // synchronizing in case it is not swapping (better performance)
         if (sizeOfEntries + this.transientQueue.getNumOfBytes() < this.transientQueue.getMaxNumOfBytes()) {
            this.transientQueue.put(queueEntries, ignorePutInterceptor);
         }

         else {
            synchronized (this.swappingPutMonitor) {
               // put all messages on transient queue
               this.transientQueue.put(queueEntries, ignorePutInterceptor);

               // handle swapping (if any)
               long exceedingSize = this.transientQueue.getNumOfBytes() - this.transientQueue.getMaxNumOfBytes();
               if (exceedingSize >= 0L) {
                  if (this.log.TRACE) this.log.trace(ME, "put: swapping. Exceeding size (in bytes): " + exceedingSize + " state: " + toXml(""));
                  this.isSwapping = true;
                  if (this.persistentQueue == null)
                     throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "put: no durable queue configured, needed for swapping");

                  if (!this.isConnected)
                     throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "put: The DB is currently disconnected: swapped messages are lost");

                  ArrayList swaps = this.transientQueue.takeLowest(-1, exceedingSize, null, true);
                  // get the transients
                  ArrayList transients = new ArrayList();
                  long sizeOfTransients = 0L;
                  for (int i=0; i < swaps.size(); i++) {
                     I_QueueEntry entry = (I_QueueEntry)swaps.get(i);
                     if (!entry.isDurable()) {
                        transients.add(entry);
                        sizeOfTransients += entry.getSizeInBytes();
                     }
                  }
                  this.hasPersistentEntries = true;
                  long spaceLeft = this.persistentQueue.getMaxNumOfBytes() - this.persistentQueue.getNumOfBytes();
                  if (spaceLeft < sizeOfTransients)
                     throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_BYTES, ME, "put: maximum size in bytes for the durable queue exceeded when swapping. State: " + toXml(""));
                  try {
                     this.persistentQueue.put((I_QueueEntry[])transients.toArray(new I_QueueEntry[transients.size()]), ignorePutInterceptor);
                  }
                  catch (XmlBlasterException ex) {
                     this.log.error(ME, "put: an error occured when writing to the persistent queue: " +  transients.size() + " transient swapped messages will be lost. Is the DB up and running ? " + ex.getMessage() + " state: " + toXml(""));
                     // should an exception be rethrown here ?
                     e = ex;
                  }
               } // end of swapPutMonitor
            }
         }
      }

      if (e != null) throw e;
      return null;
   }


   /**
    * Returns the unique ID of this queue
    */
   public StorageId getStorageId() {
      return this.queueId;
   }

   /**
    * @see I_Queue#take()
    */
   public I_QueueEntry take() throws XmlBlasterException
   {
      return (I_QueueEntry)take(-1,-1L).get(0);
   }

   /**
    * @see I_Queue#takeWithPriority(int,long,int,int)
    */
   public ArrayList takeWithPriority(int numOfEntries, long numOfBytes, int minPriority, int maxPriority)
      throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "takeWithPriority not implemented");
   }


   /**
    * @see I_Queue#take(int, long)
    */
   public ArrayList take(int numOfEntries, long numOfBytes) throws XmlBlasterException {
      this.log.call(ME, "take ");
      ArrayList list = peek(numOfEntries, numOfBytes);
      I_QueueEntry[] entries = (I_QueueEntry[])list.toArray(new I_QueueEntry[list.size()]);
      removeRandom(entries);
      return list;
   }


   /**
    * @see I_Queue#takeLowest(int, long, I_QueueEntry, boolean)
    */
   public ArrayList takeLowest(int numOfEntries, long numOfBytes, I_QueueEntry limitEntry, boolean leaveOne)
      throws XmlBlasterException {
      synchronized (this.swappingPutMonitor) {
         if (isSwapping) {
            ArrayList list = this.persistentQueue.takeLowest(numOfEntries, numOfBytes, limitEntry, leaveOne);
            if (list.size() > 1) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "takeLowest for more than one entry is not implemented");
            }
            long num = this.transientQueue.removeRandom((I_Entry[])list.toArray(new I_Entry[list.size()]));
            if (num > 0L) {
               log.error(ME, "Didn't expect message " + ((I_Entry)list.get(0)).getLogId() + " in transient store");
            }
            return list;
         }
         else {
            ArrayList list = this.transientQueue.takeLowest(numOfEntries, numOfBytes, limitEntry, leaveOne);
            if (list.size() > 0) {
               this.persistentQueue.removeRandom((I_Entry[])list.toArray(new I_Entry[list.size()]));
            }
            return list;
         }
      }
   }


   /**
    * @see I_Queue#peek()
    */
   public I_QueueEntry peek() throws XmlBlasterException
   {
      return this.transientQueue.peek();
   }


   /**
    * @see I_Queue#peek(int,long)
    */
   public ArrayList peek(int numOfEntries, long numOfBytes) throws XmlBlasterException {
      synchronized(this.swappingPutMonitor) {
         return this.transientQueue.peek(numOfEntries, numOfBytes);
      }
   }

   /**
    * @see I_Queue#peekSamePriority(int, long)
    */
   public ArrayList peekSamePriority(int numOfEntries, long numOfBytes) throws XmlBlasterException {
      synchronized(this.swappingPutMonitor) {
         return this.transientQueue.peekSamePriority(numOfEntries, numOfBytes);
      }
   }

   /**
    * @see I_Queue#peekWithPriority(int, long, int, int)
    */
   public ArrayList peekWithPriority(int numOfEntries, long numOfBytes, int minPriority, int maxPriority) throws XmlBlasterException {
      synchronized(this.swappingPutMonitor) {
         return this.transientQueue.peekWithPriority(numOfEntries, numOfBytes, minPriority, maxPriority);
      }
   }


   /**
    * @see I_Queue#peekWithLimitEntry(I_QueueEntry)
    */
   public ArrayList peekWithLimitEntry(I_QueueEntry limitEntry) throws XmlBlasterException {
      synchronized(this.swappingPutMonitor) {
         return this.transientQueue.peekWithLimitEntry(limitEntry);
      }
   }


   /**
    * Removes the first element in the queue
    * This method does not block.
    * @return Number of messages erased (0 or 1)
    * @throws XmlBlasterException if the underlying implementation gets an exception.
    */
   public int remove() throws XmlBlasterException {
      return (int)remove(1, -1L);
   }


   /**
    * Removes max num messages.
    * This method does not block.
    * @param num Erase num entries or less if less entries are available, -1 erases everything
    * @return Number of entries erased
    * @throws XmlBlasterException if the underlying implementation gets an exception.
    */
   public long remove(long numOfEntries, long numOfBytes) throws XmlBlasterException {

      long ret = 0L;
      int removedEntries = 0;
      if (numOfEntries > Integer.MAX_VALUE)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "remove: too many entries to remove " + numOfEntries);
      int nmax = (int)numOfEntries;

      if (nmax < 0) nmax = Integer.MAX_VALUE;
      if (numOfBytes < 0L) numOfBytes = Long.MAX_VALUE;

      while ((nmax > 0) && (numOfBytes > 0L)) {
         ArrayList list = peek(nmax, numOfBytes);
         if ((list == null) || (list.size() < 1)) break;
         long delta = this.transientQueue.getNumOfBytes();
         removedEntries = (int)removeRandom((I_Entry[])list.toArray(new I_Entry[list.size()]));
         delta -= this.transientQueue.getNumOfBytes();
         nmax -= removedEntries;
         ret += removedEntries;
         numOfBytes -= delta;
      }
      return ret;
   }


   /**
    * Removes the given entry.
    * @param dataId the unique id. It must be unique within the storage area
    *        of the implementing queue. In other words, if the underlying
    *        implementation is on RAM, then the storage area is the JVM, that
    *        is the queue must be unique in the same JVM. If the queue is a
    *        jdbc, the dataId is unique in the DB used.
    */
   public int removeRandom(long dataId) throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "removeRandom(long) not implemented");
   }


   /**
    * Removes the given entries.
    * @param msgQueueEntry the entry to erase.
    */
   public long removeRandom(long[] dataIdArray) throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "removeRandom(long[]) not implemented");
   }


   /**
    * @see I_Queue#removeRandom(I_Entry)
    */
   public int removeRandom(I_Entry entry) throws XmlBlasterException {
      I_Entry[] entries = new I_Entry[1];
      entries[0] = entry;
      return (int)removeRandom(entries);
   }

   /**
    * @see I_Queue#removeRandom(I_Entry[])
    */
   public long removeRandom(I_Entry[] queueEntries) throws XmlBlasterException {

      if (this.log.CALL) this.log.call(ME,"removeRandom(I_QueueEntry[])");
      if (queueEntries == null || queueEntries.length < 1) return 0L;
      if (this.storeNewDurableRecovery) {
         synchronized(this.storeNewDurableRecoveryMonitor) {
            return this.removeRandomUnsync(queueEntries);
         }
      }
      return this.removeRandomUnsync(queueEntries);
   }


   /**
    * @see I_Queue#removeRandom(I_Entry[])
    */
   private final long removeRandomUnsync(I_Entry[] queueEntries) throws XmlBlasterException {

      long ret = 0L;
      if ((this.persistentQueue != null) && (this.hasPersistentEntries)) {
         if (!this.isSwapping) { // cleanup all transient entries
            ArrayList durables = new ArrayList();
            for (int i=0; i < queueEntries.length; i++) {
               if (queueEntries[i].isDurable()) durables.add(queueEntries[i]);
            }
            if (this.log.TRACE) this.log.trace(ME, "removeRandom (swapping mode): remove " + durables.size() + " durable entries from persistent storage");
            if (this.persistentQueue != null && this.isConnected) {
               try {
                  this.persistentQueue.removeRandom((I_Entry[])durables.toArray(new I_Entry[durables.size()]));
               }
               catch (XmlBlasterException ex) {
                  this.log.error(ME, "could not remove " + durables.size() + " entries from the persitent queue. Probably due to failed connection to the DB");
               }
            }
         }
         else {
            if (this.log.TRACE) this.log.trace(ME, "removeRandom (non-swapping mode): remove " + queueEntries.length + " entries from persistent storage");
            try {
               this.persistentQueue.removeRandom(queueEntries);
            }
            catch (XmlBlasterException ex) {
               this.log.error(ME, "could not remove " + queueEntries.length + " entries from the persitent queue. Probably due to failed connection to the DB");
            }
         }
         if (this.persistentQueue.getNumOfEntries() < 1) {
            if (this.log.TRACE) this.log.trace(ME, "removeRandom: persitent storage is now empty (no swapping anymore)");
            this.hasPersistentEntries = false;
            this.isSwapping = false;
         }
      }
      // and now the transient queue (the ram queue)
      if (this.log.TRACE) this.log.trace(ME, "removeRandom: removing from transient queue " + queueEntries.length + " entries");
      ret = this.transientQueue.removeRandom(queueEntries);
      loadFromPersistence();
      return ret;
   }


   /**
    * Loads from the persistence so much data as it fits into the transient
    * queue.
    */
   private final int loadFromPersistence() throws XmlBlasterException {
      // load further entries from persistence into transient queue
      if (this.isSwapping) {
         if (this.persistentQueue == null || !this.isConnected) return 0;

         //or should it only fill a certain amount (percent) of the queue size ?
         long bytes = this.transientQueue.getMaxNumOfBytes() - this.transientQueue.getNumOfBytes();
         if (bytes < 0L) {
            this.log.warn(ME, "loadFromPersistence: the transient queue is already full. max size: " + this.transientQueue.getMaxNumOfBytes() + " , currently: " + this.transientQueue.getNumOfBytes());
            if (this.log.DUMP) this.log.dump(ME, "loadFromPersitence: the real current size in bytes of transient queue is: " + ((RamQueuePlugin)this.transientQueue).getSynchronizedNumOfBytes());
            return 0;
         }
         if (this.log.TRACE) this.log.trace(ME, "removeRandom: swapping: reloading from persistence for a length of " + bytes);

         ArrayList list = null;
         try {
            list = this.persistentQueue.peek(-1, bytes);
            if (this.persistentQueue.getNumOfEntries() < 1)
               this.isSwapping = false;
         }
         catch (XmlBlasterException ex) {
            this.log.error(ME, "could not read back data from persistence: Problably the DB is down or lost connection.");
         }
         try {
            if (list != null) this.transientQueue.put((I_QueueEntry[])list.toArray(new I_QueueEntry[list.size()]), false);
            return list.size();
         }
         catch (XmlBlasterException ex) {
            this.log.error(ME, "loadFromPeristence: no space left on transient queue: " + ex.getMessage());
            return 0;
         }

      }
      return 0;
   }


   /**
    * @see I_Queue#removeWithPriority(long, long, int, int)
    */
   public long removeWithPriority(long numOfEntries, long numOfBytes, int minPriority, int maxPriority)
      throws XmlBlasterException {

      synchronized(this) {
         if (numOfEntries > Integer.MAX_VALUE)
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "remove: too many entries to remove " + numOfEntries);
         ArrayList list = peekWithPriority((int)numOfEntries, numOfBytes, minPriority, maxPriority);
         if (list == null || list.size() < 1) return 0L;
         return removeRandom((I_QueueEntry[])list.toArray(new I_QueueEntry[list.size()]));
      }
   }

   /**
    * @see I_Queue#removeTransient()
    */
   public int removeTransient() throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "removeTransient not implemented");
   }


   /**
    * It returns the size of the queue. Note that this call will return the size
    * stored in cache, i.e. it will NOT make a call to the underlying DB.
    *
    * @see I_Queue#getNumOfEntries()
    */
   public long getNumOfEntries() {
      long ret = 0L;
      if (this.persistentQueue != null && this.isConnected) {
         ret = this.persistentQueue.getNumOfEntries();
         if (ret < 0L) return this.transientQueue.getNumOfEntries();
         ret += this.transientQueue.getNumOfEntries() - this.transientQueue.getNumOfDurableEntries();
         return ret;
      }
      return this.transientQueue.getNumOfEntries();
   }


   /**
    * It returns the size of durable entries in the queue. Note that this call will return the size
    * stored in cache, i.e. it will NOT make a call to the underlying DB.
    *
    * @see I_Queue#getNumOfDurableEntries()
    */
   public long getNumOfDurableEntries() {
      long ret = 0L;
      if (this.persistentQueue != null && this.isConnected) {
         ret = this.persistentQueue.getNumOfDurableEntries();
         if (ret < 0L) return this.transientQueue.getNumOfEntries();
         return this.persistentQueue.getNumOfDurableEntries();
      }
      return this.transientQueue.getNumOfDurableEntries();
   }

   /**
    * @see I_Queue#getMaxNumOfEntries()
    */
   public long getMaxNumOfEntries() {
      long ret = 0L;
      if (this.persistentQueue != null && this.isConnected)
         return this.persistentQueue.getMaxNumOfEntries();
      return this.transientQueue.getMaxNumOfEntries();
   }

   /**
    * @see I_Queue#getNumOfBytes()
    */
   public long getNumOfBytes() {
      long ret = 0L;
      if (this.persistentQueue != null && this.isConnected) {
         ret = this.persistentQueue.getNumOfBytes();
         if (ret < 0L) return this.transientQueue.getNumOfBytes();
         ret += this.transientQueue.getNumOfBytes() - this.transientQueue.getNumOfDurableBytes();
      }
      return this.transientQueue.getNumOfBytes();
   }

   /**
    * @see I_Queue#getNumOfDurableBytes()
    */
   public long getNumOfDurableBytes() {
      long ret = 0L;
      if (this.persistentQueue != null && this.isConnected) {
         ret = this.persistentQueue.getNumOfDurableBytes();
         if (ret < 0L) return this.transientQueue.getNumOfDurableBytes();
         return this.persistentQueue.getNumOfDurableBytes();
      }
      return this.transientQueue.getNumOfDurableBytes();
   }


   /**
    * @see I_Queue#getMaxNumOfBytes()
    */
   public long getMaxNumOfBytes() {
      long ret = 0L;
      if (this.persistentQueue != null && this.isConnected)
         return this.persistentQueue.getMaxNumOfBytes();
      return this.transientQueue.getMaxNumOfBytes();
   }



   /**
    * Updates the given message queue entry with a new value. Note that this
    * can be used if an entry with the unique id already exists.
    * ?? Does this really make sense here since we need to store history ??
    * ?? Should we define a switch which can deactivate storage of history ??
    */
   public int update(I_QueueEntry queueEntry) throws XmlBlasterException
   {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "update not implemented");
   }


   /**
    * Clears everything and removes the queue (i.e. frees the associated table)
    */
   public long clear() {
      long ret = this.transientQueue.clear();
      if (this.persistentQueue != null && this.isConnected)
         ret += this.persistentQueue.clear();
//      this.numOfBytes = 0L;
//      this.numOfEntries = 0L;

      try {
         // to be notified about reconnections / disconnections
         this.glob.getJdbcQueueManager(this.queueId).unregisterListener(this);
      }
      catch (Exception ex) {
         this.log.error(ME, "could not unregister listener. Cause: " + ex.getMessage());
      }

      return ret;
   }

   /**
    * @see I_Queue#removeHead(I_QueueEntry)
    */
   public long removeHead(I_QueueEntry toEntry) throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "removeHead not implemented");
   }



   /**
    * Shutdown the implementation, sync with data store
    * @param true: force shutdown, don't flush everything
    */
   public void shutdown(boolean force) {
      this.isDown = true;
      this.transientQueue.shutdown(force);
      if (this.persistentQueue != null && this.isConnected)
         this.persistentQueue.shutdown(force);
   }



   public boolean isShutdown() {
      return this.isDown;
   }

   /**
    * @return a human readable usage help string
    */
   public String usage() {
      return "no usage";
   }

   /**
    * @return Internal state as an XML ASCII string
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(1000);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<CacheQueueInterceptorPlugin id='").append(getStorageId().getId()).append("'>");
      sb.append(this.transientQueue.toXml(extraOffset+Constants.INDENT));
      if (this.persistentQueue != null)
         sb.append(this.persistentQueue.toXml(extraOffset+Constants.INDENT));
      sb.append(offset).append("</CacheQueueInterceptorPlugin>");
      return sb.toString();
   }

   /**
    * Enforced by I_Plugin
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) {
      java.util.Properties props = pluginInfo.getParameters();
   }

   /**
    * Enforced by I_Plugin
    * @return "JDBC"
    */
   public String getType() { return "CACHE"; }

   /**
    * Enforced by I_Plugin
    * @return "1.0"
    */
   public String getVersion() { return "1.0"; }

   /**
    * destroys all the resources associated to this queue. Even the persistent
    * data is destroyed.
    */
   public void destroy() throws XmlBlasterException {
      XmlBlasterException e = null;
      this.isDown = true;
      try {
         if (this.persistentQueue != null && this.isConnected)
            this.persistentQueue.destroy();
      }
      catch (XmlBlasterException ex) {
         e = ex;
      }
      this.transientQueue.destroy();
      if (e != null) throw e;
   }
}
