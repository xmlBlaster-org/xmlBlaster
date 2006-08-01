/*------------------------------------------------------------------------------
Name:      CacheQueueInterceptorPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue.cache;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.queue.I_EntryFilter;
import org.xmlBlaster.util.queue.I_QueueSizeListener;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queue.I_QueuePutListener;
import org.xmlBlaster.util.queue.I_StoragePlugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.def.Constants;

import java.util.ArrayList;
import org.xmlBlaster.util.queue.QueuePluginManager;

// import org.xmlBlaster.util.queue.jdbc.I_ConnectionStateListener;
// currently only for a dump ...
import org.xmlBlaster.util.queue.ram.RamQueuePlugin;
import org.xmlBlaster.util.queue.I_StorageProblemListener;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;
import java.io.OutputStream;

/**
 * Implements a queue cache. 
 * Internally it utilizes a RAM queue and a JDBC queue and manages the cache logic. 
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/queue.cache.html">The queue.cache requirement</a>
 * @author laghi@swissinfo.org
 * @author xmlBlaster@marcelruff.info
 */
public class CacheQueueInterceptorPlugin implements I_Queue, I_StoragePlugin, I_StorageProblemListener, CacheQueueInterceptorPluginMBean
{
   private static Logger log = Logger.getLogger(CacheQueueInterceptorPlugin.class.getName());
   private String ME;
   private ContextNode contextNode;
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

   /** object used to control the swapping performance */
   //private CacheControlParam controlParam;
   private PluginInfo pluginInfo;

   private ArrayList queueSizeListeners;
   private Object queueSizeListenersSync = new Object();
   
   /** this is the sync between the peaks and the swapping: no peak should be allowed while swapping */
   private Object peekSync = new Object();

   /** My JMX registration */
   private Object mbeanHandle;

   public boolean isTransient() {
      return this.transientQueue.isTransient() && this.persistentQueue.isTransient();
   }

   /**
    * Helper method to check the space left on a given queue.
    * @param  queue the queue on which to calculate the space left.
    * @param  valueToCheckAgainst the amount of bytes which are subtracted (needed in the queue) in this 
              check.
    * @param  ifFullThrowException if 'true' this method will throw an exception if the return value would
              be negative
    * @return long the space left on the specified queue after having occupied the queue with what is 
    *         specified in 'valueToCheckAgainst'
    * @throws XmlBlasterException if the 'ifFullThrowException' flag has been set to 'true' and the 
    *         return value would be negative.
    */
   private final long checkSpaceAvailable(I_Queue queue, long valueToCheckAgainst, boolean ifFullThrowException, String extraTxt) 
      throws XmlBlasterException {
      long spaceLeft = queue.getMaxNumOfBytes() - queue.getNumOfBytes() - valueToCheckAgainst;
      if (log.isLoggable(Level.FINE)) log.fine(ME+"maxNumOfBytes=" + queue.getMaxNumOfBytes() + "' numOfBytes='" + queue.getNumOfBytes() + "'. Occured at " + extraTxt);
      if (spaceLeft < 0L && (log.isLoggable(Level.FINE) || ifFullThrowException)) {
         String maxBytes = "maxBytes";
         String queueName = "Cache";
         if (queue == this.transientQueue) {
            maxBytes = "maxBytesCache";
            queueName = "Transient";
         }
         else if (queue == this.persistentQueue) {
            queueName = "Persistent";
         }
         String reason = queueName + " queue overflow, " + queue.getNumOfBytes() +
                         " bytes are in queue, try increasing '" + 
                         this.property.getPropName(maxBytes) + "' on client login.";
         if (log.isLoggable(Level.FINE)) log.fine(reason + this.toXml(""));
         if (ifFullThrowException)
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
      }
      return spaceLeft;
   }

   private final long checkEntriesAvailable(I_Queue queue, long valueToCheckAgainst, boolean ifFullThrowException, String extraTxt) 
      throws XmlBlasterException {
      long entriesLeft = queue.getMaxNumOfEntries() - queue.getNumOfEntries() - valueToCheckAgainst;
      if (entriesLeft < 0L && (log.isLoggable(Level.FINE) || ifFullThrowException)) {
         String maxEntries = "maxEntries";
         String queueName = "Cache";
         if (queue == this.transientQueue) {
            maxEntries = "maxEntriesCache";
            queueName = "Transient";
         }
         else if (queue == this.persistentQueue) {
            queueName = "Persistent";
         }
         String reason = queueName + " queue overflow, " + queue.getNumOfEntries() +
                         " entries are in queue, try increasing '" + 
                         this.property.getPropName(maxEntries) + "' on client login.";
         if (log.isLoggable(Level.FINE)) log.fine(reason + this.toXml(""));
         if (ifFullThrowException)
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
      }
      return entriesLeft;
   }


   /**
    * @see I_StorageProblemListener#storageUnavailable(int)
    */
   synchronized public void storageUnavailable(int oldStatus) {
      if (log.isLoggable(Level.FINER)) 
         log.finer(ME+"entering storageUnavailable");
      this.isConnected = false;
      // we could optimize this by providing a peekLast method to the I_Queue
      try {
         this.transientQueue.peek(-1, -1L);
      }
      catch (XmlBlasterException ex) {
         log.severe(ME+"storageUnavailable: exception occured when peeking the transient queue: " + ex.getMessage());
         ex.printStackTrace();
      }
   }


   /**
    * @see I_StorageProblemListener#storageAvailable(int)
    */
   synchronized public void storageAvailable(int oldStatus) {
      if (oldStatus == I_StorageProblemListener.UNDEF) return;
      if (log.isLoggable(Level.FINER)) log.finer(ME+"entering storageAvailable");
     /* remove all obsolete messages from the persitence. */

      if (this.persistentQueue == null) return; // should never happen

      try {
         boolean isInclusive = true; // if the reference is the original one then it is inclusive, if it is a new one then it is exclusive
         I_QueueEntry limitEntry = null; // this.referenceEntry;
         if (log.isLoggable(Level.FINE)) {
            if (limitEntry == null) log.fine(ME+"The reference entry is null");
            else log.fine(ME+"The reference entry is '" + limitEntry.getUniqueId() + "' and its flag 'stored' is '" + limitEntry.isStored() + "'");
         }
         ArrayList list = null;

         if (limitEntry == null || limitEntry.isStored()) {
            isInclusive = false;
            limitEntry = this.transientQueue.peek(); // get the first entry in the RAM queue as ref
            if (log.isLoggable(Level.FINE)) {
               if (limitEntry == null) log.fine(ME+"The new reference entry is null");
               else log.fine(ME+"The new reference entry is '" + limitEntry.getUniqueId() + "'");
            }
         }
         if (limitEntry == null) { // then ram queue was empty when it lost connection and is empty now
            isInclusive = false;
            this.persistentQueue.clear(); 
         }


         // remove all old msg which are higher than the reference entry all more important msg were sent already         
         else this.persistentQueue.removeWithLimitEntry(limitEntry, isInclusive); 

         limitEntry = this.persistentQueue.peek();
         if (limitEntry != null) {
            list = this.transientQueue.peekWithLimitEntry(limitEntry);
            if (list.size() > 0) {
               // TAKE AWAY ALL TRANSIENTS !!!!!!
               long countToPut = this.persistentQueue.getMaxNumOfEntries() - this.persistentQueue.getNumOfEntries();
               long bytesToPut = this.persistentQueue.getMaxNumOfBytes() - this.persistentQueue.getNumOfBytes();
               long currBytes = 0L;
               ArrayList list2 = new ArrayList();
               for (int i=list.size()-1; i >= 0; i--) {
                  I_Entry entry = (I_Entry)list.get(i);
                  if (entry.isPersistent()) {
                     if (currBytes >= bytesToPut || list2.size() >= countToPut) {
                        break;
                     }
                     list2.add(entry);
                     currBytes += entry.getSizeInBytes();
                  }
               }
               if (list2.size() > 0) {
                  this.persistentQueue.put((I_QueueEntry[])list2.toArray(new I_QueueEntry[list2.size()]), false);
               }
           }
         }
         this.isConnected = true;
      }
      catch (XmlBlasterException ex) {
         log.severe(ME+"exception occured when reconnecting. " + ex.getMessage());
         ex.printStackTrace();
      }
      finally {
         try {
            loadFromPersistence();
         }
         catch (XmlBlasterException ex) {
            log.severe(ME+"storageAvailable: exception when loading from persistence: " + ex.getMessage());
            ex.printStackTrace();
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
         this.glob = ((QueuePropertyBase)userData).getGlobal();

         this.ME = uniqueQueueId.toString() + ": ";
         if (log.isLoggable(Level.FINER)) log.finer(ME+"initialized");
         this.queueId = uniqueQueueId;

         // For JMX instanceName may not contain ","
         String instanceName = this.glob.validateJmxValue(this.queueId.getId());
         this.contextNode = new ContextNode(ContextNode.QUEUE_MARKER_TAG, instanceName, 
                             this.glob.getContextNode()); // TODO: pass from real parent like SubjectInfo
         this.mbeanHandle = this.glob.registerMBean(this.contextNode, this);

         QueuePluginManager pluginManager = glob.getQueuePluginManager();
         QueuePropertyBase queuePropertyBase = (QueuePropertyBase)userData;

         //instantiate and initialize the underlying queues
         String defaultTransient = pluginProperties.getProperty("transientQueue", "RAM,1.0").trim();
         if (defaultTransient.startsWith(getType())) {
            log.severe(ME+"Cache queue configured with transientQueue=CACHE, to prevent recursion we set it to 'RAM,1.0'");
            defaultTransient = "RAM,1.0";
         }
         this.transientQueue = pluginManager.getPlugin(defaultTransient, uniqueQueueId, createRamCopy(queuePropertyBase));
         //log.error(ME, "Debug only: " + this.transientQueue.toXml(""));
         
         try {
            String defaultPersistent = pluginProperties.getProperty("persistentQueue", "JDBC,1.0").trim();
            if (defaultPersistent.startsWith(getType())) {
               log.severe(ME+"Cache queue configured with persistentQueue=CACHE, to prevent recursion we set it to 'JDBC,1.0'");
               defaultPersistent = "JDBC,1.0";
            }
            this.persistentQueue = pluginManager.getPlugin(defaultPersistent, uniqueQueueId, queuePropertyBase);

            this.isConnected = true;
            // to be notified about reconnections / disconnections
//            this.glob.getJdbcQueueManager(this.queueId).registerListener(this);
            this.persistentQueue.registerStorageProblemListener(this);
         }
         catch (XmlBlasterException ex) {
            log.severe(ME+"Could not initialize the persistent queue '" + uniqueQueueId + "'. Is the JDBC Driver jar file in the CLASSPATH ?" +
                " Is the DB up and running ? We continue RAM based ..." + ex.getMessage() +
                " The propery settings are:" + queuePropertyBase.toXml());
            // start a polling thread to see if the connection can be established later 
            ex.printStackTrace();
         }

         // do the queue specific stuff like delete all volatile entries in
         // the persistent queue
         if (isPersistenceAvailable()) {
            try {
               this.persistentQueue.removeTransient();
            }
            catch (XmlBlasterException ex) {
               log.severe(ME+"could not remove transient entries (swapped entries) probably due to no connection to the DB, or the DB is down");
               ex.printStackTrace();
            }

            setProperties(userData);
            // not used yet
            //this.controlParam = new CacheControlParam((QueuePropertyBase)getProperties());

            loadFromPersistence();

            // on restart the added() event is not triggered!

         } // persistentQueue!=null
         this.isDown = false;
         if (log.isLoggable(Level.FINE)) log.fine(ME+"Successful initialized");
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
   synchronized public void setProperties(Object userData) throws XmlBlasterException {
      if (userData == null) return;
      QueuePropertyBase newProp;
      try {
         newProp = (QueuePropertyBase)userData;
      }
      catch(Throwable e) {
         log.severe(ME+"Can't configure queue, your properties are invalid: " + e.toString());
         e.printStackTrace();
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

   // JMX
   public String getPropertyStr() {
      return (this.property == null) ? "" : this.property.toXml();
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
   synchronized public void addPutListener(I_QueuePutListener l) {
      if (l == null)
         throw new IllegalArgumentException(ME + ": addPustListener(null) is not allowed");
      if (this.putListener != null)
         throw new IllegalArgumentException(ME + ": addPustListener() failed, there is a listener registered already");
      this.putListener = l;
   }

   /**
    * @see I_Queue#removePutListener(I_QueuePutListener)
    */
   synchronized public void removePutListener(I_QueuePutListener l) {
      this.putListener = null;
   }

   /**
    * returns the persistent queue (null if no one defined)
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
    * @see I_Queue#getEntries(I_EntryFilter)
    */
   public ArrayList getEntries(I_EntryFilter entryFilter) throws XmlBlasterException {
           if (this.persistentQueue == null) return new ArrayList();
           return this.persistentQueue.getEntries(entryFilter);
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

      if ((this.putListener != null) && (!ignorePutInterceptor)) {
         // Is an interceptor registered (and not bypassed) ?
         if (this.putListener.putPre(queueEntries) == false)
            return; // supress adding entries to queue (bypasses the queue)
      }

      synchronized (this) {
         checkEntriesAvailable(this, 0L, true, "first check in put"); // throws XmlBlasterException if no space left
         checkSpaceAvailable(this, 0L, true,  "first check in put"); // throws XmlBlasterException if no space left
        
         long sizeOfEntries = 0L;
         // separate persistent from transient messages and store the persistents in persistence
         if (isPersistenceAvailable()) {
            ArrayList persistentsFromEntries = new ArrayList();
            long sizeOfPersistents = 0L;
            long numOfPersistents = 0L;
        
            for (int i=0; i < queueEntries.length; i++) {
               if (queueEntries[i].isPersistent()) {
                  persistentsFromEntries.add(queueEntries[i]);
                  sizeOfPersistents += queueEntries[i].getSizeInBytes();
                  numOfPersistents++;
               }
               else sizeOfEntries += queueEntries[i].getSizeInBytes();
            }
            sizeOfEntries += sizeOfPersistents;
        
            if (persistentsFromEntries.size() > 0) {
               try {
                  this.persistentQueue.put((I_QueueEntry[])persistentsFromEntries.toArray(new I_QueueEntry[persistentsFromEntries.size()]), ignorePutInterceptor);
               }
               catch (XmlBlasterException ex) {
                  log.severe(ME+"put: an error occured when writing to the persistent queue: " + persistentsFromEntries.size() + " persistent entries will temporarly be handled as transient. Is the DB up and running ? " + ex.getMessage() + "state "  + this.toXml(""));
                  // should an exception be rethrown here ? No because it should be possible to work even if no persistence available
               }
               catch (Throwable ex) {
                  log.severe(ME+"put: an error occured when writing to the persistent queue: " + persistentsFromEntries.size() + " persistent entries will temporarly be handled as transient. Is the DB up and running ? " + ex.toString() + "state "  + this.toXml(""));
                  ex.printStackTrace();
               }
            }
         }
        
        
         synchronized(this.peekSync) {        
            // put all messages on transient queue
            this.transientQueue.put(queueEntries, ignorePutInterceptor);

            if (isPersistenceAvailable()) { // if no persistence available let RAM overflow one time

               // handle swapping (if any)
               // TODO swap only if bigger than max entries (not same as max entries)
               long exceedingSize = -checkSpaceAvailable(this.transientQueue, 0L, false, "");
               long exceedingEntries = -checkEntriesAvailable(this.transientQueue, 0L, false, "");
               if ( (exceedingSize >= 0L && this.persistentQueue.getMaxNumOfBytes() > this.transientQueue.getMaxNumOfBytes()) || 
                    (exceedingEntries >= 0L && this.persistentQueue.getMaxNumOfEntries() > this.transientQueue.getMaxNumOfEntries())) {
                  if (log.isLoggable(Level.FINE)) log.fine(ME+"Swapping. Exceeding size (in bytes): " + exceedingSize + " exceeding entries: " + exceedingEntries + " state: " + toXml(""));
            
                  ArrayList transients = null;
                  try {
                     ArrayList swaps = null;
                     boolean needsLoading = false;
                     if (this.transientQueue.getNumOfEntries() == 0)
                        swaps = this.transientQueue.takeLowest((int)exceedingEntries, exceedingSize, null, true);
                     else {
                        swaps = this.transientQueue.takeLowest(queueEntries.length, sizeOfEntries, null, true);
                        needsLoading = true;
                     }
                     if (log.isLoggable(Level.FINE)) {
                        log.fine(ME+"Swapping: moving '" + swaps.size() + "' entries from transient queue to persistent queue: exceedingEntries='" + exceedingEntries + "' and exceedingSize='" + exceedingSize + "'");
                     }
                     // get the transients
                     transients = new ArrayList();
                     for (int i=0; i < swaps.size(); i++) {
                        I_QueueEntry entry = (I_QueueEntry)swaps.get(i);
                        if (!entry.isPersistent()) {
                           transients.add(entry);
                        }
                     }
                     if (transients.size() > 0)
                        this.persistentQueue.put((I_QueueEntry[])transients.toArray(new I_QueueEntry[transients.size()]), ignorePutInterceptor);
                     if (needsLoading) loadFromPersistence();
                  }
                  catch (XmlBlasterException ex) {
                     log.severe(ME+"put: an error occured when swapping: " +  transients.size() + ". Is the DB up and running ? " + ex.getMessage() + " state: " + toXml(""));
                     ex.printStackTrace();
                  }
                  catch (Throwable ex) {
                     log.severe(ME+"put: an error occured when swapping: " +  transients.size() + ". Is the DB up and running ? " + ex.toString());
                     ex.printStackTrace();
                  }
               }
            }
         } // end of peekSync here ...
      } // end of synchronized here ...

      // these must be outside the synchronized ...
      if (this.notifiedAboutAddOrRemove) {
         for(int i=0; i<queueEntries.length; i++)
            try {
               queueEntries[i].added(this.queueId);
            }
            catch (Throwable ex) {
               log.severe(ME+"put: an error occured when notifying : " + ex.toString());
               ex.printStackTrace();
            }
      }
      if (this.queueSizeListeners != null) this.invokeQueueSizeListener();
      if ((this.putListener != null) && (!ignorePutInterceptor)) {
         this.putListener.putPost(queueEntries);
      }
   }

   // JMX
   public String getQueueName() {
      return getStorageId().getStrippedId();
   }

   /**
    * Returns the unique ID of this queue
    */
   public StorageId getStorageId() {
      return this.queueId;
   }

   /**
    * @see I_Queue#takeWithPriority(int,long,int,int)
    */
   public ArrayList takeWithPriority(int numOfEntries, long numOfBytes, int minPriority, int maxPriority)
      throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "takeWithPriority not implemented");
      // if (this.notifiedAboutAddOrRemove) {}
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
    * Aware: peekLowest is not implemented!!
    * @see I_Queue#peekLowest(int, long, I_QueueEntry, boolean)
    */
   public ArrayList peekLowest(int numOfEntries, long numOfBytes, I_QueueEntry limitEntry, boolean leaveOne)
      throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "peekLowest is not implemented");
   }


   /**
    * Aware: takeLowest for more than one entry is not implemented!!
    * @see I_Queue#takeLowest(int, long, I_QueueEntry, boolean)
    */
   public ArrayList takeLowest(int numOfEntries, long numOfBytes, I_QueueEntry limitEntry, boolean leaveOne)
      throws XmlBlasterException {
      ArrayList list = null;
      boolean doNotify = false;
      try {
         synchronized(this) {
            boolean handlePersistents = isPersistenceAvailable() && hasUncachedEntries();
            if ( handlePersistents ) { 
               // swapping
               try {
                  list = this.persistentQueue.takeLowest(numOfEntries, numOfBytes, limitEntry, leaveOne);
                  doNotify = true;
               }
               catch (Throwable ex) {
                  handlePersistents = false;
                  log.severe(ME+"takeLowest: exception occured when taking the lowest entry from the persistent queue: " + ex.toString());
                  ex.printStackTrace();
               }
           
               if (handlePersistents) {
                  if (list.size() > 1) {
                     throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME,
                              "takeLowest for more than one entry is not implemented");
                  }
           
                  long num = 0L;
                  boolean[] hlp = this.transientQueue.removeRandom((I_Entry[])list.toArray(new I_Entry[list.size()]));
                  for (int i=0; i < hlp.length; i++) if (hlp[i]) num++;
                  if (num > 0L) {
                     if (log.isLoggable(Level.FINE)) log.fine(ME+"Didn't expect message " + ((I_Entry)list.get(0)).getLogId() + " in transient store." +
                                " If the database was temporary unavailable this is possible " + this.toXml(""));
                  }
               }
            }
            // 'else' is no good here since it could have changed due to the exception ...
            if ( !handlePersistents) {
               list = this.transientQueue.takeLowest(numOfEntries, numOfBytes, limitEntry, leaveOne);
               doNotify = true;
               if (isPersistenceAvailable() && list.size() > 0 && this.persistentQueue.getNumOfEntries() > 0) {
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
        
         } // end of syncrhonized 
      }
      finally {
         if (doNotify) {
            if (this.notifiedAboutAddOrRemove) {
               for(int i=0; i<list.size(); i++) ((I_Entry)list.get(i)).removed(this.queueId);
            }
         }
         if (this.queueSizeListeners != null) invokeQueueSizeListener();                  
      }
      return list;
   }

   // JMX
   public String peekStr() throws Exception {
      try {
         I_QueueEntry entry = peek();
         return (entry == null) ? "No entry found" :
                     entry.getLogId() + " - " + entry.getSizeInBytes() + " bytes - " +
                     ((entry.isPersistent()) ? "persistent" : "transient") +
                     " prio=" + entry.getPriority() + 
                     " " + entry.getEmbeddedType(); // no toXml() available ?? 
      }
      catch(XmlBlasterException e) {
         throw new Exception(e);
      }
   }

   // JMX
   public String[] peekEntries(int numOfEntries) throws Exception {
      if (numOfEntries == 0)
         return new String[] { "Please pass number of messages to peak" };
      try {
         ArrayList list = peek(numOfEntries, -1L);
         if (list == null || list.size()<1)
            return new String[] { "No entry found" };
         String[] ret = new String[list.size()];
         for (int i=0; i<list.size(); i++) {
            I_QueueEntry entry = (I_QueueEntry)list.get(i);
            // no toXml() available ?? 
            ret[i] = entry.getLogId() + " - " + entry.getSizeInBytes() + " bytes - " +
                     ((entry.isPersistent()) ? "persistent" : "transient") +
                     " prio=" + entry.getPriority() + 
                     " " + entry.getEmbeddedType(); // no toXml() available ?? 
         }
         return ret;
      }
      catch (XmlBlasterException e) {
         throw new Exception(e);
      }
   }

   /**
    * @see I_Queue#peek()
    */
   public I_QueueEntry peek() throws XmlBlasterException {
      synchronized(this.peekSync) {
         return this.transientQueue.peek();
      }
   }

   /**
    * @see I_Queue#peek(int,long)
    */
   public ArrayList peek(int numOfEntries, long numOfBytes) throws XmlBlasterException {
      synchronized(this.peekSync) {
         return this.transientQueue.peek(numOfEntries, numOfBytes);
      }
   }

   /**
    * @see I_Queue#peekSamePriority(int, long)
    */
   public ArrayList peekSamePriority(int numOfEntries, long numOfBytes) throws XmlBlasterException {
      synchronized(this.peekSync) {
         return this.transientQueue.peekSamePriority(numOfEntries, numOfBytes);
      }
   }

   /**
    * @see I_Queue#peekWithPriority(int, long, int, int)
    */
   public ArrayList peekWithPriority(int numOfEntries, long numOfBytes, int minPriority, int maxPriority) throws XmlBlasterException {
      synchronized(this.peekSync) {
         return this.transientQueue.peekWithPriority(numOfEntries, numOfBytes, minPriority, maxPriority);
      }
   }

   /**
    * @see I_Queue#peekWithLimitEntry(I_QueueEntry)
    * @deprecated
    */
   public ArrayList peekWithLimitEntry(I_QueueEntry limitEntry) throws XmlBlasterException {
      synchronized(this.peekSync) {
         return this.transientQueue.peekWithLimitEntry(limitEntry);
      }
   }


   /**
    * @see I_Queue#removeWithLimitEntry(I_QueueEntry, boolean)
    */
   synchronized public long removeWithLimitEntry(I_QueueEntry limitEntry, boolean inclusive) throws XmlBlasterException {
      long ret = this.transientQueue.removeWithLimitEntry(limitEntry, inclusive);

      if (isPersistenceAvailable()) {
         try {
            ret = this.persistentQueue.removeWithLimitEntry(limitEntry, inclusive);
         }
         catch (XmlBlasterException ex) {
            log.severe(ME+"removeWithLimitEntry: exception occured when removing from persistence. reason: " + ex.getMessage());
            ex.printStackTrace();
         }
         catch (Throwable ex) {
            log.severe(ME+"removeWithLimitEntry: exception occured when removing from persistence. reason: " + ex.toString());
            ex.printStackTrace();
         }
      }
      return ret;
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

      I_Entry[] entries = null;
      ArrayList  list = null;
      boolean[] tmp = null;
      try {
         synchronized(this) {
            while ((nmax > 0) && (numOfBytes > 0L)) {
               list = peek(nmax, numOfBytes);
               if ((list == null) || (list.size() < 1)) break;
               long delta = this.transientQueue.getNumOfBytes();
               removedEntries = 0;
               entries = (I_Entry[])list.toArray(new I_Entry[list.size()]);
               tmp = removeRandomNoNotify(entries);
               for (int i=0; i < tmp.length; i++) if (tmp[i]) removedEntries++;
           
               delta -= this.transientQueue.getNumOfBytes();
               nmax -= removedEntries;
               ret += removedEntries;
               numOfBytes -= delta;
            }
         }
      }
      finally {
         if (this.notifiedAboutAddOrRemove && tmp!=null && entries!=null) {
            for(int i=0; i<tmp.length; i++)
               if (tmp[i])
                  entries[i].removed(this.queueId);
         }
      }
      if (this.queueSizeListeners != null) invokeQueueSizeListener();                  
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
      if (removeRandom(entries)[0]) return 1;
      else return 0;
   }

   /**
    * The given ret array will be updated with the result of the removing from the persistent
    * queue.
    */
   private final boolean[] removePossibleSwappedEntries(boolean[] ret, I_Entry[] queueEntries) {
      if (log.isLoggable(Level.FINER)) log.finer(ME+"removePossibleSwappedEntries");

      // prepare the entries array
      if (!isPersistenceAvailable()) return ret;
      int numUnremoved = 0;
      for (int i=0; i < ret.length; i++) if (!ret[i]) numUnremoved++;
      if (numUnremoved == 0) return ret;
      if (!hasTransientsSwapped()) return ret;

      if (log.isLoggable(Level.FINE)) log.fine(ME+"There were entries '" + numUnremoved + "' to delete on persistence");
      if (queueEntries == null || queueEntries.length < 1) return ret;
      
      I_Entry[] unremovedEntries = new I_Entry[numUnremoved];
      int count = 0;
      for (int i=0; i < ret.length; i++) {
         if (!ret[i]) {
            unremovedEntries[count] = queueEntries[i];
            count++;
         }
      }

      try {
         boolean[] ret1 = this.persistentQueue.removeRandom(unremovedEntries);
         count = 0;
         for (int i=0; i < ret.length; i++) {
            if (!ret[i]) {
               ret[i] = ret1[count];
               count++;
               if (log.isLoggable(Level.FINEST)) log.finest(ME+"Entry '" + unremovedEntries[count].getUniqueId() + "' has been deleted ? : " + ret1[count]);
            }
         }
      }
      catch (XmlBlasterException ex) {
         log.severe(ME+"exception occured when trying to remove entries which have supposely been swapped since the last peek. reason: " + ex.getMessage());
         ex.printStackTrace();
         return ret;
      }
      return ret;
   }

   /**
    * @see I_Queue#removeRandom(I_Entry[])
    */
   private final boolean[] removeRandomNoNotify(I_Entry[] queueEntries) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer(ME+"removeRandom(I_QueueEntry[])");
      if (queueEntries == null || queueEntries.length < 1) return new boolean[0];
      boolean[] ret = null;

      synchronized (this) {
         try {
           if (isPersistenceAvailable()) {
               ArrayList persistents = new ArrayList();
               for (int i=0; i < queueEntries.length; i++) {
                  if (queueEntries[i].isPersistent()) persistents.add(queueEntries[i]);
               }
               if (log.isLoggable(Level.FINE)) log.fine(ME+"Remove " + persistents.size() + " persistent entries from persistent storage");
               try {
                  this.persistentQueue.removeRandom((I_Entry[])persistents.toArray(new I_Entry[persistents.size()]));
               }
               catch (XmlBlasterException ex) {
                  log.severe(ME+"could not remove " + persistents.size() + " entries from the persistent queue. Probably due to failed connection to the DB exception: " +  ex.getMessage());
                  ex.printStackTrace();
               }
            }
           
            // and now the transient queue (the ram queue)
            if (log.isLoggable(Level.FINE)) log.fine(ME+"Removing from transient queue " + queueEntries.length + " entries");
            try {
               ret = this.transientQueue.removeRandom(queueEntries);
               ret = removePossibleSwappedEntries(ret, queueEntries);
            }
            catch (XmlBlasterException ex) {
               log.severe(ME+"could not remove " + queueEntries.length + " entries from the transient queue.: " + ex.getMessage());
               ex.printStackTrace();
            }
         }
         finally {
            try {
               loadFromPersistence();
            }
            catch (XmlBlasterException ex1) {
               log.severe(ME+"removeRandom exception occured when loading from persistence: " + ex1.getMessage());
               ex1.printStackTrace();
            }
         }
      }
      return ret;
   }


   /**
    * @see I_Queue#removeRandom(I_Entry[])
    */
   public final boolean[] removeRandom(I_Entry[] queueEntries) throws XmlBlasterException {
      boolean[] ret = removeRandomNoNotify(queueEntries);
      if (this.notifiedAboutAddOrRemove) {
         for(int i=0; i<ret.length; i++) {
            if (ret[i]) {
              try {
                 queueEntries[i].removed(this.queueId);
              }
              catch (Throwable ex) {
                 log.severe(ME+"removeRandom: exception when notifying about removal: " + ex.toString());
                 ex.printStackTrace();
              }
            }
         }
      }
      if (this.queueSizeListeners != null) invokeQueueSizeListener();                  
      return ret;
   }


   /**
    * Loads from the persistence so much data as it fits into the transient
    * queue.
    */
   synchronized private final int loadFromPersistence() throws XmlBlasterException {
      if (!isPersistenceAvailable()) return 0;

      // load further entries from persistence into transient queue
      if(hasUncachedEntries()) {
         //or should it only fill a certain amount (percent) of the queue size ?
         long freeEntries = this.transientQueue.getMaxNumOfEntries() - this.transientQueue.getNumOfEntries();
         long freeBytes = this.transientQueue.getMaxNumOfBytes() - this.transientQueue.getNumOfBytes();

         if (freeEntries <= 0L || freeBytes <= 0L) {
            if (log.isLoggable(Level.FINE))
               log.fine(ME+"The transient queue is already full." +
                          " numOfBytes=" + this.transientQueue.getNumOfBytes() +
                          " maxNumOfBytes=" + this.transientQueue.getMaxNumOfBytes() +
                          " numOfEntries=" + this.transientQueue.getNumOfEntries() +
                          " maxNumOfEntries=" + this.transientQueue.getMaxNumOfBytes());
            if (log.isLoggable(Level.FINEST)) log.finest(ME+"The real current size in bytes of transient queue is: " + ((RamQueuePlugin)this.transientQueue).getSynchronizedNumOfBytes());
            return 0;
         }
         if (log.isLoggable(Level.FINE)) log.fine(ME+"Swapping: reloading from persistence for a length of " + freeBytes);

         // 1. Look into persistent store ...
         ArrayList list = null;
         try {
            list = this.persistentQueue.peek((int)freeEntries, freeBytes);
         }
         catch (XmlBlasterException ex) {
            log.severe(ME+"Could not read back data from persistence: " + ex.getMessage());
            ex.printStackTrace();
         }

         if (list == null || list.size() < 1) {
            return 0;
         }

         // 2. Put it into RAM ...
         try {
            this.transientQueue.put((I_QueueEntry[])list.toArray(new I_QueueEntry[list.size()]), false);
         }
         catch (XmlBlasterException ex) {
            log.severe(ME+"loadFromPeristence: no space left on transient queue: " + ex.getMessage());
            ex.printStackTrace();
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
               this.persistentQueue.removeRandom((I_Entry[])transients.toArray(new I_Entry[transients.size()]));
         }
         catch (XmlBlasterException ex) {
            log.severe(ME+"loadFromPeristence: Memory leak: problems removing " + transients.size() + " swapped transient entries form persistent store: " + ex.getMessage());
            return list.size();
         }

         return transients.size();
      }

      return 0;
   }


   /**
    * @see I_Queue#removeWithPriority(long, long, int, int)
    */
   public long removeWithPriority(long numOfEntries, long numOfBytes, int minPriority, int maxPriority)
      throws XmlBlasterException {
      ArrayList list = null;
      boolean[] tmp = null;
      synchronized(this) {
         if (numOfEntries > Integer.MAX_VALUE)
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "remove: too many entries to remove " + numOfEntries);
         list = peekWithPriority((int)numOfEntries, numOfBytes, minPriority, maxPriority);
         if (list == null || list.size() < 1) return 0L;
         tmp = removeRandomNoNotify((I_QueueEntry[])list.toArray(new I_QueueEntry[list.size()]));

      }
      if (this.notifiedAboutAddOrRemove) {
         for(int i=0; i<tmp.length; i++) {
            if (tmp[i]) {
               try {
                  ((I_Entry)list.get(i)).removed(this.queueId);
               }
               catch (Throwable ex) {
                  log.severe(ME+"removeWithPriority exception occured when notifying about removal. Reason: " + ex.toString());
                  ex.printStackTrace();  
               }
            }
         }
      }
      if (this.queueSizeListeners != null) invokeQueueSizeListener();                  
      long ret = 0L;
      for (int i=0; i < tmp.length; i++) if (tmp[i]) ret++;
      return ret;
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
   synchronized public long getNumOfEntries() {
      long ret = 0L;
      if (isPersistenceAvailable()) {
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
   synchronized public long getNumOfPersistentEntries() {
      long ret = 0L;
      if (isPersistenceAvailable()) {
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
      if (isPersistenceAvailable())
         return this.persistentQueue.getMaxNumOfEntries();
      return this.transientQueue.getMaxNumOfEntries();
   }

   /**
    * @see I_Queue#getNumOfBytes()
    */
   synchronized public long getNumOfBytes() {
      long ret = 0L;
      if (isPersistenceAvailable()) {
         ret = this.persistentQueue.getNumOfBytes();
         if (ret < 0L) return this.transientQueue.getNumOfBytes();
         ret += this.transientQueue.getNumOfBytes() - this.transientQueue.getNumOfPersistentBytes();
         return ret;
      }
      return this.transientQueue.getNumOfBytes();
   }

   /**
    * @see I_Queue#getNumOfPersistentBytes()
    */
   synchronized public long getNumOfPersistentBytes() {
      long ret = 0L;
      if (isPersistenceAvailable()) {
         ret = this.persistentQueue.getNumOfPersistentBytes();
         // if a persistent queue return -1L it means it was not able to get the correct size
         if (ret < 0L) return this.transientQueue.getNumOfPersistentBytes();
         return ret;
      }
      return this.transientQueue.getNumOfPersistentBytes();
   }


   /**
    * @see I_Queue#getMaxNumOfBytes()
    */
   synchronized public long getMaxNumOfBytes() {
      if (isPersistenceAvailable())
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
      try {
         return remove(-1, -1);
      }
      catch (XmlBlasterException e) {
         log.severe(ME+"Ignoring exception in clear(): " + e.toString());
         return 0;
      }
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
   synchronized public void shutdown() {
      if (log.isLoggable(Level.FINER)) log.finer(ME+"shutdown(isDown="+this.isDown+")");
      if (this.isDown) {
         return;
      }
      this.isDown = true;
      this.glob.unregisterMBean(this.mbeanHandle);
      long numTransients = getNumOfEntries() - getNumOfPersistentEntries();
      if (numTransients > 0) {
         log.warning(ME+"Shutting down cache queue which contains " + numTransients + " transient messages");
      }

      try {
         this.transientQueue.shutdown();
      }
      catch (Throwable ex) {
         log.severe(ME+"shutdown: exception when processing transient queue. Reason: " + ex.toString());
         ex.printStackTrace();
      }

      try {
         if (this.persistentQueue != null) this.persistentQueue.shutdown();
      }
      catch (Throwable ex) {
         log.severe(ME+"shutdown: exception when processing transient queue. Reason: " + ex.toString());
         ex.printStackTrace();
      }
      try {
//         this.glob.getJdbcQueueManager(this.queueId).unregisterListener(this);
         if (this.persistentQueue != null) this.persistentQueue.unRegisterStorageProblemListener(this);
      }
      catch (Exception ex) {
         log.severe(ME+"could not unregister listener. Cause: " + ex.getMessage());
         ex.printStackTrace();
      }
      removeQueueSizeListener(null);
   }

   public boolean isShutdown() {
      return this.isDown;
   }

   /**
    * JMX help
    * @return a human readable usage help string
    */
   public java.lang.String usage() {
      return "Manipulating the queue directly will most certainly destroy your data."
      +Global.getJmxUsageLinkInfo(this.getClass().getName(), null);
   }

   /**
    * @return A link for JMX usage
    */
   public java.lang.String getUsageUrl() {
      return Global.getJavadocUrl(this.getClass().getName(), null);
   }
   
   /* dummy to have a copy/paste functionality in jconsole */
   public void setUsageUrl(java.lang.String url) {}

   // JMX
   public final String toXml() {
      return toXml("");
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
      sb.append("' maxEntriesCache='").append(this.transientQueue.getMaxNumOfEntries());
      sb.append("' maxBytesCache='").append(this.transientQueue.getMaxNumOfBytes());
      sb.append("' maxEntries='").append(getMaxNumOfEntries());
      sb.append("' maxBytes='").append(getMaxNumOfBytes());
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
    * @see org.xmlBlaster.util.queue.I_StorageProblemNotifier#registerStorageProblemListener(I_StorageProblemListener)
    */
   synchronized public boolean registerStorageProblemListener(I_StorageProblemListener listener) {
      if (this.persistentQueue == null) return false;
      return this.persistentQueue.registerStorageProblemListener(listener);
   }


   /**
    * @see org.xmlBlaster.util.queue.I_StorageProblemNotifier#unRegisterStorageProblemListener(I_StorageProblemListener)
    */
   synchronized public boolean unRegisterStorageProblemListener(I_StorageProblemListener listener) {
      if (this.persistentQueue == null) return false;
      return this.persistentQueue.unRegisterStorageProblemListener(listener);
   }

   /**
    * @see I_Queue#addQueueSizeListener(I_QueueSizeListener)
    */
   public void addQueueSizeListener(I_QueueSizeListener listener) {
      if (listener == null) 
         throw new IllegalArgumentException(ME + ": addQueueSizeListener(null) is not allowed");
      synchronized(this.queueSizeListenersSync) {
         if (this.queueSizeListeners == null)
            this.queueSizeListeners = new ArrayList();
         this.queueSizeListeners.add(listener);
      }
   }
   
   /**
    * @see I_Queue#removeQueueSizeListener(I_QueueSizeListener)
    */
   public void removeQueueSizeListener(I_QueueSizeListener listener) {
      synchronized(this.queueSizeListenersSync) {
         if (listener == null) this.queueSizeListeners = null;
         else {
            if (!this.queueSizeListeners.remove(listener))
               log.warning(ME+"removeQueueSizeListener: could not remove listener '" + listener.toString() + "' since not registered");
            if (this.queueSizeListeners.size() == 0) this.queueSizeListeners = null;
         }
      }
   }
   
   private final void invokeQueueSizeListener() {
      if (this.queueSizeListeners != null) {
         I_QueueSizeListener[] listeners = null;
         synchronized(this.queueSizeListenersSync) {
             listeners = (I_QueueSizeListener[])this.queueSizeListeners.toArray(new I_QueueSizeListener[this.queueSizeListeners.size()]);
         }
         for (int i=0; i < listeners.length; i++) {
            try {
               listeners[i].changed(this, this.getNumOfEntries(), this.getNumOfBytes());
            }
            catch (NullPointerException e) {
               if (log.isLoggable(Level.FINE)) log.fine(ME+"Call is not possible as another thread has removed queueSizeListeners, this is OK to prevent a synchronize.");
            }
         }
      }
   }

   /**
    * @see I_Queue#hasQueueSizeListener(I_QueueSizeListener)
    */
   public boolean hasQueueSizeListener(I_QueueSizeListener listener) {
      if (listener == null)
         return this.queueSizeListeners != null;
      else
         return this.queueSizeListeners.contains(listener);
   }

   /**
    * @see I_Queue#embeddedObjectsToXml(OutputStream, Properties)
    */
   public long embeddedObjectsToXml(OutputStream out, Properties props) throws Exception {
      I_Queue queue = this.persistentQueue;
      if (queue != null) {
         return queue.embeddedObjectsToXml(out, null);
      }
      log.warning(ME+"Sorry, dumping transient entries to '" + out + "' is not implemented");
      return 0;
   }
   
   /**
    * @see I_AdminQueue#dumpEmbeddedObjectsToFile(String)
    */
   public String dumpEmbeddedObjectsToFile(String fileName) throws Exception {
      if (fileName == null || fileName.equalsIgnoreCase("String")) {
         fileName = this.queueId.getStrippedId() + ".xml";
      }
      File to_file = new File(fileName);
      if (to_file.getParent() != null) {
         to_file.getParentFile().mkdirs();
      }
      FileOutputStream out = new FileOutputStream(to_file);
      out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>".getBytes());
      out.write(("\n<"+this.queueId.getPrefix()+">").getBytes());
      Properties props = new Properties();
      props.put(Constants.TOXML_FORCEREADABLE, ""+true);  // to be human readable (minimize base64)
      props.put(Constants.TOXML_ENCLOSINGTAG, "publish"); // to look similar to XmlScript
      long count = embeddedObjectsToXml(out, props);
      out.write(("\n</"+this.queueId.getPrefix()+">").getBytes());
      out.close();
      return "Dumped " + count + " entries to '" + to_file.toString() + "'";
   }
}
