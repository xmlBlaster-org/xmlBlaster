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
// import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.queue.I_StoragePlugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.enum.Constants;

import java.util.ArrayList;
import org.xmlBlaster.util.queue.QueuePluginManager;

// import org.xmlBlaster.util.queue.jdbc.I_ConnectionStateListener;
// currently only for a dump ...
import org.xmlBlaster.util.queue.ram.RamQueuePlugin;
import org.xmlBlaster.util.queue.I_StorageProblemListener;
// import org.xmlBlaster.util.queue.I_StorageProblemNotifier;


/**
 * @author laghi@swissinfo.org
 * @author xmlBlaster@marcelruff.info
 */
public class CacheQueueInterceptorPlugin implements I_Queue, I_StoragePlugin, I_StorageProblemListener
{
   private String ME;
   private LogChannel log;
   private QueuePropertyBase property;             // plugins via I_Queue
   private boolean notifiedAboutAddOrRemove = false;
   boolean isDown = true;
   private StorageId queueId;
   private I_QueuePutListener putListener;
//   private java.util.Properties pluginProperties;  // plugins via I_Plugin

   private I_Queue transientQueue;
   private I_Queue persistentQueue;
   private Global glob;
   private boolean isConnected = false;

   private Object deleteDeliveredMonitor = new Object();
   private Object storeNewPersistentRecoveryMonitor = new Object();
   private Object swappingPutMonitor = new Object();
   /**
    * this boolean is set only under the time a recovery afer having reconnected
    * to the DB. It is used to limit the synchronization
    */
   private boolean storeNewPersistentRecovery = false;
   /** object used to control the swapping performance */
   private CacheControlParam controlParam;
   private PluginInfo pluginInfo;

   private I_QueueEntry referenceEntry;

   /**
    * @see I_StorageProblemListener#storageUnavailable(int)
    */
   public void storageUnavailable(int oldStatus) {
      if (this.log.CALL) this.log.call(ME, "storageUnavailable");
      this.isConnected = false;
      // we could optimize this by providing a peekLast method to the I_Queue
      try {
         ArrayList lst = this.transientQueue.peek(-1, -1L);
         if (lst.size() < 1) this.referenceEntry = null;
         else this.referenceEntry = (I_QueueEntry)lst.get(lst.size()-1);
      }
      catch (XmlBlasterException ex) {
         this.log.error(ME, "storageUnavailable: exception occured when peeking the transient queue: " + ex.getMessage());
      }
   }


   /**
    * @see I_StorageProblemListener#storageAvailable(int)
    */
   public void storageAvailable(int oldStatus) {
      if (oldStatus == I_StorageProblemListener.UNDEF) return;
      if (this.log.CALL) this.log.call(ME, "storageAvailable");
     /* remove all obsolete messages from the persitence. Obsolete are the
      * entries which are lower (lower priority and older) than the lowest
      * entry in the transient storage.
      */

      if (this.persistentQueue == null) return; // should never happen

      try {
         synchronized(this.deleteDeliveredMonitor) {
            boolean isInclusive = true; // if the reference is the original one then it is inclusive, if it is a new one then it is exclusive
            I_QueueEntry limitEntry = this.referenceEntry;
            if (this.log.TRACE) {
               if (limitEntry == null) this.log.trace(ME, "storageAvailable: the reference entry is null");
               else this.log.trace(ME, "storageAvailable: the reference entry is '" + limitEntry.getUniqueId() + "' and its flag 'stored' is '" + limitEntry.isStored() + "'");
            }
            ArrayList list = null;
            this.referenceEntry = null;

            if (limitEntry == null || limitEntry.isStored()) {
               isInclusive = false;
               limitEntry = this.transientQueue.peek(); // get the first entry in the RAM queue as ref
               if (this.log.TRACE) {
                  if (limitEntry == null) this.log.trace(ME, "storageAvailable: the new reference entry is null");
                  else this.log.trace(ME, "storageAvailable: the new reference entry is '" + limitEntry.getUniqueId() + "'");
               }
            }
            if (limitEntry == null) { // then ram queue was empty when it lost connection and is empty now
               isInclusive = false;
//               list = this.persistentQueue.peek(-1, -1L);
               this.persistentQueue.clear(); 
            }
//            else list = this.persistentQueue.peekWithLimitEntry(limitEntry);

            else this.persistentQueue.removeWithLimitEntry(limitEntry, isInclusive);
/*
            if (this.log.TRACE) this.log.trace(ME, "storageAvailable: '" + list.size() + "' entries removed from persistence");
            this.persistentQueue.removeRandom((I_Entry[])list.toArray(new I_Entry[list.size()]));
            if (isInclusive) {
               this.persistentQueue.take();
               if (this.log.TRACE) this.log.trace(ME, "storageAvailable: the limit was inclusive: removing one further entry from the persitent storage");
            }
*/
         }

         // add all new persistent entries to the persistent storage ...
         this.storeNewPersistentRecovery = true;
         synchronized(this.storeNewPersistentRecoveryMonitor) {
            I_QueueEntry limitEntry = this.persistentQueue.peek();
            ArrayList list = this.transientQueue.peekWithLimitEntry(limitEntry);
            this.persistentQueue.put((I_QueueEntry[])list.toArray(new I_QueueEntry[list.size()]), false);
         }
         this.storeNewPersistentRecovery = false;

         this.isConnected = true;
      }
      catch (XmlBlasterException ex) {
         this.log.error(ME, "exception occured when reconnecting. " + ex.toString());
      }
      finally {
         try {
            loadFromPersistence();
         }
         catch (XmlBlasterException ex) {
            this.log.error(ME, "storageAvailable: exception when loading from persistence: " + ex.getMessage());
         }
      }
   }


   /**
    * Is called after the instance is created.
    * @param uniqueQueueId A unique name, allowing to create a unique name for a persistent store (e.g. file name)
    * @see I_Queue#initialize(StorageId, Object)
    */
   synchronized public void initialize(StorageId uniqueQueueId, Object userData)
      throws XmlBlasterException
   {
      if (this.isDown) {

         java.util.Properties pluginProperties = null;
         if (this.pluginInfo != null) pluginProperties = this.pluginInfo.getParameters();

         if (pluginProperties == null) 
            pluginProperties = new java.util.Properties(); // if loaded from testsuite without a PluginManager

         this.property = null;
         glob = ((QueuePropertyBase)userData).getGlobal();
         this.log = glob.getLog("queue");
         this.ME = this.getClass().getName() + "-" + uniqueQueueId;
         if (this.log.CALL) this.log.call(ME, "initialized");
         this.queueId = uniqueQueueId;

         QueuePluginManager pluginManager = glob.getQueuePluginManager();
         QueuePropertyBase queuePropertyBase = (QueuePropertyBase)userData;

         //instantiate and initialize the underlying queues
         String defaultTransient = pluginProperties.getProperty("transientQueue", "RAM,1.0").trim();
         if (defaultTransient.startsWith(getType())) {
            log.error(ME,"Cache queue configured with transientQueue=CACHE, to prevent recursion we set it to 'RAM,1.0'");
            defaultTransient = "RAM,1.0";
         }
         this.transientQueue = pluginManager.getPlugin(defaultTransient, uniqueQueueId, createRamCopy(queuePropertyBase));
         //log.error(ME, "Debug only: " + this.transientQueue.toXml(""));
         
         try {
            String defaultPersistent = pluginProperties.getProperty("persistentQueue", "JDBC,1.0").trim();
            if (defaultPersistent.startsWith(getType())) {
               log.error(ME,"Cache queue configured with persistentQueue=CACHE, to prevent recursion we set it to 'JDBC,1.0'");
               defaultPersistent = "JDBC,1.0";
            }
            this.persistentQueue = pluginManager.getPlugin(defaultPersistent, uniqueQueueId, queuePropertyBase);

            this.isConnected = true;
            // to be notified about reconnections / disconnections
//            this.glob.getJdbcQueueManager(this.queueId).registerListener(this);
            this.persistentQueue.registerStorageProblemListener(this);
         }
         catch (XmlBlasterException ex) {
            this.log.error(ME, "Could not initialize the persistent queue '" + uniqueQueueId + "'. Is the JDBC Driver jar file in the CLASSPATH ?" +
                " Is the DB up and running ? We continue RAM based ..." + ex.getMessage() +
                " The propery settings are:" + queuePropertyBase.toXml());
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

            loadFromPersistence();

         } // persistentQueue!=null
         this.isDown = false;
         if (log.TRACE) log.trace(ME, "Successful initialized");
      } // isDown?
   }

   /**
    * We set the cache props to the real props for RAM queue running under a cacheQueue
    */
   private QueuePropertyBase createRamCopy(QueuePropertyBase queuePropertyBase) {
      QueuePropertyBase ramCopy = (QueuePropertyBase)queuePropertyBase.clone();
      ramCopy.setMaxEntries(queuePropertyBase.getMaxEntriesCache());
      ramCopy.setMaxBytes(queuePropertyBase.getMaxBytesCache());
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
      if (this.property != null && this.property.getMaxEntries() > newProp.getMaxEntries()) {
         log.warn(ME, "Reconfigure of a RamQueuePlugin - getMaxNumOfEntries from " + this.property.getMaxEntries() +
                    " to " + newProp.getMaxEntries() + " is not supported, we ignore the new setting.");
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
   public void put(I_QueueEntry queueEntry, boolean ignorePutInterceptor)
      throws XmlBlasterException {
      I_QueueEntry[] entries = new I_QueueEntry[1];
      entries[0] = queueEntry;
      put(entries, ignorePutInterceptor);
   }
    
   /**
    * All entries are stored into the transient queue. All persistent messages are
    * stored also in the persistent queue. The exceeding size in the transient
    * queue is calculated. If it is positive it means we need to swap. The
    * overflowing messages are taken from the ram queue. The volatile between
    * them are stored in the persistent storage (since the persistent ones have
    * been previously stored).
    * 
    * @see I_Queue#put(I_QueueEntry[], boolean)
    */
   public void put(I_QueueEntry[] queueEntries, boolean ignorePutInterceptor)
      throws XmlBlasterException {
      if (queueEntries == null || queueEntries.length < 1) return;
      XmlBlasterException e = null;
      if ((this.putListener != null) && (!ignorePutInterceptor)) {
         // Is an interceptor registered (and not bypassed) ?
         if (this.putListener.putPre(queueEntries) == false)
            return;
      }

      if (getNumOfEntries() > getMaxNumOfEntries()) { // Allow superload one time only
         String reason = "Queue overflow (number of entries), " + getNumOfEntries() +
                         " messages are in queue, try increasing '" +
                         this.property.getPropName("maxEntries") + "' on client login.";
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

         // separate persistent from transient messages and store the persistents in persistence
         if (this.persistentQueue != null && this.isConnected) {
            ArrayList persistentsFromEntries = new ArrayList();
            long sizeOfPersistents = 0L;
            for (int i=0; i < queueEntries.length; i++) {
               if (queueEntries[i].isPersistent()) {
                  persistentsFromEntries.add(queueEntries[i]);
                  sizeOfPersistents += ((I_QueueEntry)queueEntries[i]).getSizeInBytes();
               }
               else sizeOfEntries += queueEntries[i].getSizeInBytes();
            }
            sizeOfEntries += sizeOfPersistents;

            if (persistentsFromEntries.size() > 0) {

               long spaceLeft = this.persistentQueue.getMaxNumOfBytes() - this.persistentQueue.getNumOfBytes();
               if (spaceLeft < sizeOfPersistents) {
                  String reason = "Persistent queue overflow, " + this.getNumOfBytes() +
                                  " bytes are in queue, try increasing '" + 
                                  this.property.getPropName("maxBytes") + "' on client login.";
                  this.log.warn(ME, reason + this.toXml(""));
                  throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
               }
               try {
                  this.persistentQueue.put((I_QueueEntry[])persistentsFromEntries.toArray(new I_QueueEntry[persistentsFromEntries.size()]), ignorePutInterceptor);
               }
               catch (XmlBlasterException ex) {
                  this.log.error(ME, "put: an error occured when writing to the persistent queue: " + persistentsFromEntries.size() + " persistent entries will temporarly be handled as transient. Is the DB up and running ? " + ex.toString() + "state "  + this.toXml(""));
                  // should an exception be rethrown here ?
               }
            }
         }

         // check if swapping before putting data into ram queue allows to avoid
         // synchronizing in case it is not swapping (better performance)
         if (sizeOfEntries + this.transientQueue.getNumOfBytes() < this.transientQueue.getMaxNumOfBytes()) {
            this.transientQueue.put(queueEntries, ignorePutInterceptor);
            if (this.notifiedAboutAddOrRemove) {
               for(int i=0; i<queueEntries.length; i++)
                  queueEntries[i].added(this.queueId);
            }
         }

         else {
            synchronized (this.swappingPutMonitor) {
               // put all messages on transient queue
               this.transientQueue.put(queueEntries, ignorePutInterceptor);
               if (this.notifiedAboutAddOrRemove) {
                  for(int i=0; i<queueEntries.length; i++)
                     queueEntries[i].added(this.queueId);
               }

               // handle swapping (if any)
               long exceedingSize = this.transientQueue.getNumOfBytes() - this.transientQueue.getMaxNumOfBytes();
               if (exceedingSize >= 0L) {
                  if (this.log.TRACE) this.log.trace(ME, "put: swapping. Exceeding size (in bytes): " + exceedingSize + " state: " + toXml(""));
                  if (this.persistentQueue == null)
                     throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "put: no persistent queue configured, needed for swapping");

                  if (!this.isConnected)
                     throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "put: The DB is currently disconnected: swapped messages are lost");

                  ArrayList swaps = this.transientQueue.takeLowest(-1, exceedingSize, null, true);
                  // get the transients
                  ArrayList transients = new ArrayList();
                  long sizeOfTransients = 0L;
                  for (int i=0; i < swaps.size(); i++) {
                     I_QueueEntry entry = (I_QueueEntry)swaps.get(i);
                     if (!entry.isPersistent()) {
                        transients.add(entry);
                        sizeOfTransients += entry.getSizeInBytes();
                     }
                  }
                  long spaceLeft = this.persistentQueue.getMaxNumOfBytes() - this.persistentQueue.getNumOfBytes();
                  if (spaceLeft < sizeOfTransients)
                     throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_BYTES, ME, "put: maximum size in bytes for the persistent queue exceeded when swapping. State: " + toXml(""));
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

      if ((this.putListener != null) && (!ignorePutInterceptor)) {
         this.putListener.putPost(queueEntries);
      }
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
      return (I_QueueEntry)take(1,-1L).get(0);
   }

   /**
    * @see I_Queue#takeWithPriority(int,long,int,int)
    */
   public ArrayList takeWithPriority(int numOfEntries, long numOfBytes, int minPriority, int maxPriority)
      throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "takeWithPriority not implemented");
      // if (this.notifiedAboutAddOrRemove) {}
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


   private final boolean hasTransientsSwapped() {
      return this.persistentQueue.getNumOfPersistentEntries() != this.persistentQueue.getNumOfEntries();
   }

   private final boolean isPersistenceAvailable() {
      return this.persistentQueue != null && this.isConnected;
   }
      
   private final boolean hasUncachedEntries() {
      return hasTransientsSwapped() || 
             this.persistentQueue.getNumOfPersistentEntries() != this.transientQueue.getNumOfPersistentEntries();
   }

   /**
    * Aware: takeLowest for more than one entry is not implemented!!
    * @see I_Queue#takeLowest(int, long, I_QueueEntry, boolean)
    */
   public ArrayList takeLowest(int numOfEntries, long numOfBytes, I_QueueEntry limitEntry, boolean leaveOne)
      throws XmlBlasterException {
      ArrayList list = null;
      synchronized (this.swappingPutMonitor) {
         boolean handlePersistents = isPersistenceAvailable() && hasUncachedEntries();
         if ( handlePersistents ) { 
            // swapping
            try {
               list = this.persistentQueue.takeLowest(numOfEntries, numOfBytes, limitEntry, leaveOne);
            }
            catch (Throwable ex) {
               handlePersistents = false;
               this.log.error(ME, "takeLowest: exception occured when taking the lowest entry from the persitent queue: " + ex.toString());
            }

            if (handlePersistents) {
               if (this.notifiedAboutAddOrRemove) {
                  for(int i=0; i<list.size(); i++)
                     ((I_Entry)list.get(i)).removed(this.queueId);
               }
               if (list.size() > 1) {
                  throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME,
                           "takeLowest for more than one entry is not implemented");
               }
               long num = this.transientQueue.removeRandom((I_Entry[])list.toArray(new I_Entry[list.size()]));
               if (num > 0L) {
                  log.error(ME, "Didn't expect message " + ((I_Entry)list.get(0)).getLogId() + " in transient store");
               }
            }
         }
//         'else' is no good here since it could have changed due to the exception ...
         if ( !handlePersistents) {
            list = this.transientQueue.takeLowest(numOfEntries, numOfBytes, limitEntry, leaveOne);
            if (this.notifiedAboutAddOrRemove) {
               for(int i=0; i<list.size(); i++)
                  ((I_Entry)list.get(i)).removed(this.queueId);
            }

            if (this.persistentQueue!=null && list.size() > 0 && this.persistentQueue.getNumOfEntries() > 0) {
               boolean durableFound = false;
               for(int i=0; i<list.size(); i++) {
                  if (((I_Entry)list.get(i)).isPersistent()) {
                     durableFound = true;
                     break;
                  }
               }
               if (durableFound) {
                  this.persistentQueue.removeRandom((I_Entry[])list.toArray(new I_Entry[list.size()]));
               }
            }
         }
      }
      return list;
   }

   /**
    * @see I_Queue#peek()
    */
   public I_QueueEntry peek() throws XmlBlasterException {
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
    * @deprecated
    */
   public ArrayList peekWithLimitEntry(I_QueueEntry limitEntry) throws XmlBlasterException {
      synchronized(this.swappingPutMonitor) {
         return this.transientQueue.peekWithLimitEntry(limitEntry);
      }
   }


   /**
    * @see I_Queue#removeWithLimitEntry(I_QueueEntry, boolean)
    */
   public long removeWithLimitEntry(I_QueueEntry limitEntry, boolean inclusive) throws XmlBlasterException {
      synchronized(this.swappingPutMonitor) {
         long ret = this.transientQueue.removeWithLimitEntry(limitEntry, inclusive);
         if (this.persistentQueue != null && this.isConnected) 
            ret = this.persistentQueue.removeWithLimitEntry(limitEntry, inclusive);
         return ret;
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
      if (this.storeNewPersistentRecovery) {
         synchronized(this.storeNewPersistentRecoveryMonitor) {
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
      if (this.persistentQueue != null) {
         ArrayList persistents = new ArrayList();
         for (int i=0; i < queueEntries.length; i++) {
            if (queueEntries[i].isPersistent()) persistents.add(queueEntries[i]);
         }
         if (this.log.TRACE) this.log.trace(ME, "removeRandom: remove " + persistents.size() + " persistent entries from persistent storage");
         if (this.persistentQueue != null && this.isConnected) {
            try {
               this.persistentQueue.removeRandom((I_Entry[])persistents.toArray(new I_Entry[persistents.size()]));
            }
            catch (XmlBlasterException ex) {
               this.log.error(ME, "could not remove " + persistents.size() + " entries from the persistent queue. Probably due to failed connection to the DB");
            }
         }
      }

      // and now the transient queue (the ram queue)
      if (this.log.TRACE) this.log.trace(ME, "removeRandom: removing from transient queue " + queueEntries.length + " entries");
      ret = this.transientQueue.removeRandom(queueEntries);

      if (this.notifiedAboutAddOrRemove) {
         for(int i=0; i<queueEntries.length; i++)
            queueEntries[i].removed(this.queueId);
      }

      loadFromPersistence();
      return ret;
   }


   /**
    * Loads from the persistence so much data as it fits into the transient
    * queue.
    */
   private final int loadFromPersistence() throws XmlBlasterException {
      if (this.persistentQueue == null || !this.isConnected) return 0;

      // load further entries from persistence into transient queue
      synchronized (this.swappingPutMonitor) {
         if (this.persistentQueue.getNumOfEntries() > this.transientQueue.getNumOfEntries()) {
            //or should it only fill a certain amount (percent) of the queue size ?
            long freeEntries = this.transientQueue.getMaxNumOfEntries() - this.transientQueue.getNumOfEntries();
            long freeBytes = this.transientQueue.getMaxNumOfBytes() - this.transientQueue.getNumOfBytes();

            if (freeEntries < 0L || freeBytes < 0L) {
               this.log.warn(ME, "loadFromPersistence: the transient queue is already full." +
                             " numOfBytes=" + this.transientQueue.getNumOfBytes() +
                             " maxNumOfBytes=" + this.transientQueue.getMaxNumOfBytes() +
                             " numOfEntries=" + this.transientQueue.getNumOfEntries() +
                             " maxNumOfEntries=" + this.transientQueue.getMaxNumOfBytes());
               if (this.log.DUMP) this.log.dump(ME, "loadFromPersitence: the real current size in bytes of transient queue is: " + ((RamQueuePlugin)this.transientQueue).getSynchronizedNumOfBytes());
               return 0;
            }
            if (this.log.TRACE) this.log.trace(ME, "removeRandom: swapping: reloading from persistence for a length of " + freeBytes);

            // 1. Look into persistent store ...
            ArrayList list = null;
            try {
               list = this.persistentQueue.peek((int)freeEntries, freeBytes);
            }
            catch (XmlBlasterException ex) {
               this.log.error(ME, "could not read back data from persistence: " + ex.getMessage());
            }

            if (list == null || list.size() < 1) {
               return 0;
            }

            // 2. Put it into RAM ...
            try {
               this.transientQueue.put((I_QueueEntry[])list.toArray(new I_QueueEntry[list.size()]), false);
            }
            catch (XmlBlasterException ex) {
               this.log.error(ME, "loadFromPeristence: no space left on transient queue: " + ex.getMessage());
               return 0;
            }

            // 3. Erase the swapped and transient entries from persistence ...
            ArrayList transients = new ArrayList();
            int n = list.size();
            for(int i=0; i<n; i++) {
               if (!((I_Entry)list.get(i)).isPersistent()) {
                  transients.add(list.get(i));
               }
            }
            try {
               if (transients.size() > 0)
                  this.persistentQueue.removeRandom((I_Entry[])list.toArray(new I_Entry[list.size()]));
            }
            catch (XmlBlasterException ex) {
               this.log.error(ME, "loadFromPeristence: Memory leak: problems removing " + transients.size() + " swapped transient entries form persistent store: " + ex.getMessage());
               return list.size();
            }

            return list.size();
         }
      } // sync

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
         ret += this.transientQueue.getNumOfEntries() - this.transientQueue.getNumOfPersistentEntries();
         return ret;
      }
      return this.transientQueue.getNumOfEntries();
   }


   /**
    * It returns the size of persistent entries in the queue. Note that this call will return the size
    * stored in cache, i.e. it will NOT make a call to the underlying DB.
    *
    * @see I_Queue#getNumOfPersistentEntries()
    */
   public long getNumOfPersistentEntries() {
      long ret = 0L;
      if (this.persistentQueue != null && this.isConnected) {
         ret = this.persistentQueue.getNumOfPersistentEntries();
         if (ret < 0L) return this.transientQueue.getNumOfEntries();
         return this.persistentQueue.getNumOfPersistentEntries();
      }
      return this.transientQueue.getNumOfPersistentEntries();
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
         ret += this.transientQueue.getNumOfBytes() - this.transientQueue.getNumOfPersistentBytes();
      }
      return this.transientQueue.getNumOfBytes();
   }

   /**
    * @see I_Queue#getNumOfPersistentBytes()
    */
   public long getNumOfPersistentBytes() {
      long ret = 0L;
      if (this.persistentQueue != null && this.isConnected) {
         ret = this.persistentQueue.getNumOfPersistentBytes();
         if (ret < 0L) return this.transientQueue.getNumOfPersistentBytes();
         return this.persistentQueue.getNumOfPersistentBytes();
      }
      return this.transientQueue.getNumOfPersistentBytes();
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
      long ret = 0;

      synchronized(this) {
         // Activate reference decrement temporary ... entry.removed()
         if (this.notifiedAboutAddOrRemove) this.transientQueue.setNotifiedAboutAddOrRemove(true);
         ret = this.transientQueue.clear();
         if (this.notifiedAboutAddOrRemove) this.transientQueue.setNotifiedAboutAddOrRemove(false);
      }

      if (this.persistentQueue != null && this.isConnected)
         ret += this.persistentQueue.clear();
//      this.numOfBytes = 0L;
//      this.numOfEntries = 0L;

      /*
      try {
         // to be notified about reconnections / disconnections
         this.glob.getJdbcQueueManager(this.queueId).unregisterListener(this);
      }
      catch (Exception ex) {
         this.log.error(ME, "could not unregister listener. Cause: " + ex.getMessage());
      }
      */

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
    */
   public void shutdown() {
      if (log.CALL) log.call(ME, "shutdown()");
      this.isDown = true;
      this.transientQueue.shutdown();
      if (this.persistentQueue != null && this.isConnected)
         this.persistentQueue.shutdown();
      try {
//         this.glob.getJdbcQueueManager(this.queueId).unregisterListener(this);
         this.persistentQueue.unRegisterStorageProblemListener(this);
      }
      catch (Exception ex) {
         this.log.error(ME, "could not unregister listener. Cause: " + ex.getMessage());
      }
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

      sb.append(offset).append("<CacheQueueInterceptorPlugin id='").append(getStorageId().getId());
      sb.append("' type='").append(getType());
      sb.append("' version='").append(getVersion());
      sb.append("' numOfEntries='").append(getNumOfEntries());
      sb.append("' numOfBytes='").append(getNumOfBytes());
      sb.append("'>");
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
      this.pluginInfo = pluginInfo;
//      this.pluginProperties = pluginInfo.getParameters();
   }

   /**
    * Enforced by I_Plugin
    * @return "CACHE"
    */
   public String getType() { return "CACHE"; }

   /**
    * Enforced by I_Plugin
    * @return "1.0"
    */
   public String getVersion() { return "1.0"; }

   /**
    * Enforced by I_StoragePlugin
    * @return the pluginInfo object.
    */
   public PluginInfo getInfo() { return this.pluginInfo; }

   /**
    * destroys all the resources associated to this queue. Even the persistent
    * data is destroyed.
    */
   public void destroy() throws XmlBlasterException {
      XmlBlasterException e = null;
      this.isDown = true;
//      this.glob.getJdbcQueueManager(this.queueId).unregisterListener(this);
      if (this.persistentQueue != null) this.persistentQueue.unRegisterStorageProblemListener(this);
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

   /**
    * @see org.xmlBlaster.util.queue.I_StorageProblemNotifier#registerStorageProblemListener(I_StorageProblemListener)
    */
   public boolean registerStorageProblemListener(I_StorageProblemListener listener) {
      if (this.persistentQueue == null) return false;
      return this.persistentQueue.registerStorageProblemListener(listener);
   }


   /**
    * @see org.xmlBlaster.util.queue.I_StorageProblemNotifier#unRegisterStorageProblemListener(I_StorageProblemListener)
    */
   public boolean unRegisterStorageProblemListener(I_StorageProblemListener listener) {
      if (this.persistentQueue == null) return false;
      return this.persistentQueue.unRegisterStorageProblemListener(listener);
   }

}
