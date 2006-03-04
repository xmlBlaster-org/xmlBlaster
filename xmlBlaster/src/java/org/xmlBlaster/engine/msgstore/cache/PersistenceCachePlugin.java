/*------------------------------------------------------------------------------
Name:      PersistenceCachePlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.msgstore.cache;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_EntryFilter;
import org.xmlBlaster.util.queue.I_StoragePlugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.engine.msgstore.I_ChangeCallback;
import org.xmlBlaster.engine.msgstore.StoragePluginManager;
import org.xmlBlaster.util.queue.I_StorageProblemListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Properties;

/**
 * Implements a random access message storage.
 * <p/>
 * The implementation uses internally a RAM and a JDBC map and handles the caching between those two.
 * @author laghi@swissinfo.org
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.persistence.html">The engine.persistence requirement</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see org.xmlBlaster.test.classtest.msgstore.I_MapTest 
 */
public class PersistenceCachePlugin implements I_StoragePlugin, I_StorageProblemListener, I_Map, PersistenceCachePluginMBean
{
   private String ME;
   private ContextNode contextNode;
   private Global glob;
   private static Logger log = Logger.getLogger(PersistenceCachePlugin.class.getName());

//   private java.util.Properties pluginProperties; // properties via I_Plugin
   private QueuePropertyBase property;            // properties via I_Map
   boolean isDown = true;
   private StorageId storageId;

   private I_Map transientStore;
   private I_Map persistentStore;
   private boolean isConnected = false;
   private PluginInfo pluginInfo = null;

   /** My JMX registration */
   private Object mbeanHandle;

   /*
    * this boolean is set only under the time a recovery after having reconnected
    * to the DB. It is used to limit the synchronization
   private boolean storeNewPersistentRecovery = false;
    */
   
   /**
    * Triggered by persistent store (JDBC) on lost connection
    * @see org.xmlBlaster.util.queue.jdbc.I_ConnectionListener#disconnected()
    */
   public void storageUnavailable(int oldStatus) {
      log.finer("storageUnavailable");
      this.isConnected = false;
   }

   public boolean isTransient() {
      return this.transientStore.isTransient() && (this.persistentStore == null || this.persistentStore.isTransient());
   }

   /**
    * Triggered by persistent store (JDBC) on reconnection
    * @see I_StorageProblemListener#storageAvailable(int)
    */
   public void storageAvailable(int oldStatus) {
      if (oldStatus == I_StorageProblemListener.UNDEF) return;
      log.finer("storageAvailable");
     /* remove all obsolete entries from the persistence. Obsolete are the
      * entries which are lower (lower priority and older) than the lowest
      * entry in the transient storage.
      */

      if (this.persistentStore == null) return; // should never happen

      //try {
         log.warning("Persistent store has reconnected, we may have a memory leak as send messsages are not cleaned up. Current persistent messages are handled transient only, new ones will be handled persistent");
         /*
         // TODO: Implement an arraylist to remember the sent messages and destroy them
         // Happens for persistent messages and swapped messages (if JDBC connection lost)
         // For swapped entries the callback thread could block (poll) until the swap is available again.
         synchronized(this.deleteDeliveredMonitor) {
            I_MapEntry limitEntry = this.transientStore.peek();
            ArrayList list = this.persistentStore.peekWithLimitEntry(limitEntry);
            this.persistentStore.removeRandom((I_Entry[])list.toArray(new I_Entry[list.size()]));
         }
         */

         /*
         log.warn(ME, "Persistent store has reconnected, current persistent messages are handled transient only, new ones will be handled persistent");
         // add all new persistent entries to the persistent storage ...
         this.storeNewPersistentRecovery = true;
         synchronized(this.storeNewPersistentRecoveryMonitor) {
            I_MapEntry limitEntry = this.persistentStore.peek();
            ArrayList list = this.transientStore.peekWithLimitEntry(limitEntry);
            this.persistentStore.put((I_MapEntry[])list.toArray(new I_MapEntry[list.size()]), false);
         }
         this.storeNewPersistentRecovery = false;
         */
         this.isConnected = true;
      //}
      //catch (XmlBlasterException ex) {
      //   log.error(ME, "exception occured when reconnecting. " + ex.getMessage());
      //}
   }

   /**
    * Is called after the instance is created.
    * @param uniqueQueueId A unique name, allowing to create a unique name for a persistent store (e.g. file name)
    * @see org.xmlBlaster.engine.msgstore.I_Map#initialize(StorageId, Object)
    */
   public void initialize(StorageId uniqueQueueId, Object userData) throws XmlBlasterException {
      if (this.isDown) {
         java.util.Properties pluginProperties = null;
         if (this.pluginInfo != null) pluginProperties = this.pluginInfo.getParameters();
         if (pluginProperties == null)
            pluginProperties = new java.util.Properties(); // if loaded from testsuite without a PluginManager

         this.property = null;
         this.ME = this.getClass().getName() + "-" + uniqueQueueId;
         this.storageId = uniqueQueueId;
         try {
            this.property = (QueuePropertyBase)userData;
         }
         catch(Throwable e) {
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Can't configure queue, your properties are invalid", e);
         }
         if (log.isLoggable(Level.FINER)) log.finer("Entering initialize(" + getType() + ", " + getVersion() + ")");

         // For JMX instanceName may not contain ","
         String instanceName = this.glob.validateJmxValue(this.storageId.getId());
         this.contextNode = new ContextNode(ContextNode.MAP_MARKER_TAG, instanceName, 
                             this.glob.getContextNode()); // TODO: pass from real parent like TopicInfo
         this.mbeanHandle = this.glob.registerMBean(this.contextNode, this);

         // StoragePluginManager pluginManager = (StoragePluginManager)this.glob.getObjectEntry("org.xmlBlaster.engine.msgstore.StoragePluginManager");
         StoragePluginManager pluginManager = glob.getStoragePluginManager();
         QueuePropertyBase queuePropertyBase = (QueuePropertyBase)userData;

         //instantiate and initialize the underlying queues

         String defaultTransient = pluginProperties.getProperty("transientMap", "RAM,1.0").trim();
         if (defaultTransient.startsWith(getType())) {
            log.severe("Cache storage configured with transientMap=CACHE, to prevent recursion we set it to 'RAM,1.0'");
            defaultTransient = "RAM,1.0";
         }
         this.transientStore = pluginManager.getPlugin(defaultTransient, uniqueQueueId, createRamCopy(queuePropertyBase));
         if (log.isLoggable(Level.FINE)) log.fine("Created transient part:" + this.transientStore.toXml(""));
         
         try {
            String defaultPersistent = pluginProperties.getProperty("persistentMap", "JDBC,1.0").trim();
            if (defaultPersistent.startsWith(getType())) {
               log.severe("Cache storage configured with persistentMap=CACHE, to prevent recursion we set it to 'JDBC,1.0'");
               defaultPersistent = "JDBC,1.0";
            }
            this.persistentStore = pluginManager.getPlugin(defaultPersistent, uniqueQueueId, queuePropertyBase);

            this.isConnected = true;
            // to be notified about reconnections / disconnections
//            this.glob.getJdbcQueueManager(this.storageId).registerStorageProblemListener(this);
            this.persistentStore.registerStorageProblemListener(this);

            if (log.isLoggable(Level.FINE)) log.fine("Created persistent part:" + this.persistentStore.toXml(""));
         }
         catch (XmlBlasterException ex) {
            log.severe("could not initialize the persistent queue. Is the JDBC Driver jar file in the CLASSPATH ? Is the DB up and running ?" + ex.getMessage());
            // start a polling thread to see if the connection can be established later 

         }

         // do the queue specific stuff like delete all volatile entries in
         // the persistent queue
         if (this.persistentStore != null) {
            try {
               if (log.isLoggable(Level.FINE)) log.fine("Initialize: Removing swapped entries from persistent store, numEntries=" + this.persistentStore.getNumOfEntries() + " numPersistentEntries=" + this.persistentStore.getNumOfPersistentEntries());
               this.persistentStore.removeTransient();
            }
            catch (XmlBlasterException ex) {
               log.severe("could not remove transient entries (swapped entries) probably due to no connection to the DB, or the DB is down" + ex.getMessage());
            }

            // prefill cache (hack: works only for our JDBC queue which implements I_Queue as well)
            if (this.persistentStore instanceof org.xmlBlaster.util.queue.I_Queue) {
               if (log.isLoggable(Level.FINE)) log.fine("Initialize: Prefilling cache storage with entries");
               if (this.persistentStore.getNumOfEntries() > 0) {
                  // initial fill of RAM queue ...
                  long maxBytes = this.transientStore.getMaxNumOfBytes();
                  // this.transientStore.getMaxNumOfEntries();
                  long maxEntries = this.transientStore.getMaxNumOfEntries();

                  ArrayList entries = null;
                  try {
                     entries = ((org.xmlBlaster.util.queue.I_Queue)this.persistentStore).peek((int)maxEntries, maxBytes);
                     int n = entries.size();
                     log.info("Prefilling cache with " + n + " entries");
                     synchronized(this) {
                        for(int i=0; i<n; i++) {
                           I_MapEntry cleanEntry = (I_MapEntry)entries.get(i);
                           this.transientStore.put(cleanEntry);
                        }
                     }
                  }
                  catch (XmlBlasterException ex) {
                     log.severe("could not reload data from persistence probably due to a broken connection to the DB or the DB is not up and running: " + ex.getMessage());
                  }
               }
            }

         }
         this.isDown = false;
         if (log.isLoggable(Level.FINE)) log.fine("Successful initialized: " + toXml(""));
      } // isDown?
   }

   // JMX
   public String getQueueName() {
      return getStorageId().getStrippedId();
   }

   // JMX
   public int removeById(long uniqueId) throws Exception {
      try {
         return remove(uniqueId);
      }
      catch (XmlBlasterException e) {
         throw new Exception(e.toString());
      }
   }

   // JMX
   public String removeOldestEntry() throws Exception {
      try {
         I_MapEntry entry = removeOldest();
         return (entry==null) ? null : entry.toString();
      }
      catch (XmlBlasterException e) {
         throw new Exception(e.toString());
      }
   }

   // JMX
   public int removeTransientEntries() throws Exception {
      try {
         return removeTransient();
      }
      catch (XmlBlasterException e) {
         throw new Exception(e.toString());
      }
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
    * @see I_Map#setProperties(Object)
    */
   public synchronized void setProperties(Object userData) throws XmlBlasterException {
      if (userData == null) return;
      if (log.isLoggable(Level.FINER)) log.finer("Entering setProperties()");
      QueuePropertyBase newProp;
      try {
         newProp = (QueuePropertyBase)userData;
      }
      catch(Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Can't configure queue, your properties are invalid", e);
      }

      /* Do we need to protect against shrinking?
      if (this.property != null && this.property.getMaxEntries() > newProp.getMaxEntries()) {
         log.warn(ME, "Reconfigure of a RamQueuePlugin - getMaxNumOfEntries from " + this.property.getMaxEntries() +
                    " to " + newProp.getMaxEntries() + " is not supported, we ignore the new setting.");
         return;
      }
      */

      this.property = newProp;
      this.transientStore.setProperties(createRamCopy((QueuePropertyBase)userData));
      if (this.persistentStore != null) this.persistentStore.setProperties(userData);
   }

   /**
    * Access the current queue configuration
    */
   public Object getProperties() {
      return this.property;
   }

   // JMX
   public String getPropertyStr() {
      return (this.property == null) ? "" : this.property.toXml();
   }

   /**
    * All entries are stored into the transient queue. All persistent entries are
    * stored also in the persistent queue. The exceeding size in the transient
    * queue is calculated. If it is positive it means we need to swap. The
    * overflowing entries are taken from the ram queue. The volatile between
    * them are stored in the persistent storage (since the persistent ones have
    * been previously stored).
    * @see I_Map#put(I_MapEntry)
    */
   public int put(I_MapEntry mapEntry) throws XmlBlasterException {
      if (mapEntry == null)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "put(I_MapEntry="+mapEntry+")");

      if (log.isLoggable(Level.FINER)) log.finer("put(" + mapEntry.getLogId() + ")");
      int numPersistentPut = 0;
      int numTransientPut = 0;

      synchronized(this) {

         XmlBlasterException exceptionReturned = spaceLeftException(mapEntry, this);
         if (exceptionReturned != null) {
            if (log.isLoggable(Level.FINE)) log.fine(exceptionReturned.getMessage());
            exceptionReturned.setLocation(ME+"-put("+mapEntry.getLogId()+")");
            throw exceptionReturned;
         }

         // separate persistent from transient entries and store the persistents in persistence
         if (this.persistentStore != null && this.isConnected) {
            if (mapEntry.isPersistent()) {
               XmlBlasterException exceptionReturned2 = spaceLeftException(mapEntry, this.persistentStore);
               if (exceptionReturned2 != null) {
                  if (log.isLoggable(Level.FINE)) log.fine(exceptionReturned2.getMessage());
                  exceptionReturned2.setLocation(ME+"-put("+mapEntry.getLogId()+")");
                  throw exceptionReturned2;
               }
               try {
                  numPersistentPut = this.persistentStore.put(mapEntry);
               }
               catch (XmlBlasterException ex) {
                  log.severe("put: an error occurred when writing to the persistent queue, the persistent entry " + mapEntry.getLogId() +
                                " will temporarily be handled as transient. Is the DB up and running ? " + ex.getMessage() + "state "  + this.toXml(""));
               }
            }
         }

         assureTransientSpace(mapEntry);
         
         numTransientPut = this.transientStore.put(mapEntry);
      } // sync(this)

      if (numPersistentPut>0 || numTransientPut>0) {
         return 1;
      }
      // NOTE: It is possible that a persistent entry is not put to persistent storage
      // e.g. because of 'duplicate key' (entry existed already) and same with RAM queue
      // In this case the caller does get a 0
      return 0;
   }

   /**
    * Swap an entry away to hard disk. 
    * Call this method from synchronized code only.
    * @param mapEntry The new entry which needs space for itself. 
    */
   private void assureTransientSpace(I_MapEntry mapEntry) throws XmlBlasterException {

      while (!spaceLeft(mapEntry, this.transientStore)) {

         /* Protect against infinite looping */
         if (this.transientStore == null || this.property == null ||
             this.transientStore.getNumOfEntries() < 1)
            break;

         I_MapEntry oldest = this.transientStore.removeOldest();
         if (oldest == null) {
            if (log.isLoggable(Level.FINE)) log.fine("The RAM queue is full, new entry '" + mapEntry.getUniqueId() + "' seems to be the first and only one, so we accept it");
            break;
         }
         if (log.isLoggable(Level.FINER)) log.finer("Swapping '" + oldest.getLogId() + "' to HD ...");
         try {
            if (!oldest.isPersistent()) { // if entry is marked as persistent it is already in persistentStore (see code above)
               // swap away the oldest cache entry to harddisk ...
               if (log.isLoggable(Level.FINE)) this.log.fine("Swapping '" + oldest.getLogId() + " size=" + oldest.getSizeInBytes() + "'. Exceeding size state after removing from transient before entering persistent: " + toXml(""));
               if (this.persistentStore == null)
                  throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME,
                        "assureTransientSpace: no persistent queue configured, needed for swapping, entry " + mapEntry.getLogId() + " is not handled");
               if (!this.isConnected)
                  throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME,
                        "assureTransientSpace: The DB is currently disconnected, entry " + mapEntry.getLogId() + " is not handled");

               if (spaceLeft(oldest, this.persistentStore)) {
                  try {
                     this.persistentStore.put(oldest);
                  }
                  catch (XmlBlasterException ex) {
                     log.severe("assureTransientSpace: an error occured when writing to the persistent queue, transient entry " +  oldest.getLogId() + 
                           " is not swapped, new entry '" + mapEntry.getLogId() + "' is rejected. Is the DB up and running ? " + ex.getMessage() + " state: " + toXml(""));
                     throw ex;
                  }
               }
               else
                  throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_BYTES, ME,
                              "assureTransientSpace: maximum size in bytes for the persistent queue exceeded when swapping, entry " + mapEntry.getLogId() + " not handled . State: " + toXml(""));
            }
            oldest.isSwapped(true);
         }
         catch(XmlBlasterException ex2) {
            this.transientStore.put(oldest); // undo on error
            throw ex2;  // swapping failed, we won't accept the new entry
         }
      }
   }

   /**
    * Check is storage is big enough for entry
    * @param mapEntry may not be null
    * @return null There is space (otherwise the error text is returned)
    */
   private XmlBlasterException spaceLeftException(I_MapEntry mapEntry, I_Map map) {
      if (map == null || this.property == null) {
         return new XmlBlasterException(glob, ErrorCode.RESOURCE_UNAVAILABLE, ME,
                "Storage framework is down, current settings are" + toXml(""));
      }

      if ((1 + map.getNumOfEntries()) > map.getMaxNumOfEntries())
         return new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME,
                "Queue overflow (number of entries), " + getNumOfEntries() +
                " entries are in queue, try increasing property '" +
                this.property.getPropName("maxEntries") + "' and '" +
                this.property.getPropName("maxEntriesCache") + "', current settings are" + toXml(""));

      if ((mapEntry.getSizeInBytes() + map.getNumOfBytes()) > map.getMaxNumOfBytes())
         return new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_BYTES, ME,
                "Queue overflow, " + getMaxNumOfBytes() +
                " bytes are in queue, try increasing property '" + 
                this.property.getPropName("maxBytes") + "' and '" +
                this.property.getPropName("maxBytesCache") + "', current settings are" + toXml(""));

      return null;
   }

   /**
    * Check is storage is big enough for entry
    * @param mapEntry may not be null
    * @return true Space enough
    */
   private boolean spaceLeft(I_MapEntry mapEntry, I_Map map) {
      if (map == null || this.property == null)
         return false;

      if ((1 + map.getNumOfEntries()) > map.getMaxNumOfEntries())
         return false;

      if ((mapEntry.getSizeInBytes() + map.getNumOfBytes()) > map.getMaxNumOfBytes())
         return false;

      return true;
   }

   /**
    * Returns the unique ID of this queue
    */
   public StorageId getStorageId() {
      return this.storageId;
   }

   /**
    * @see I_Map#get(long)
    */
   public I_MapEntry get(final long uniqueId) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering get(" + uniqueId + ")");

      I_MapEntry mapEntry = null;
      synchronized(this) {
         mapEntry = this.transientStore.get(uniqueId);
         if (mapEntry != null) {
            mapEntry.isSwapped(false);
            return mapEntry;
         }

         if (this.persistentStore == null)
            return null;

         mapEntry = this.persistentStore.get(uniqueId);
         if (mapEntry == null) {
            return null;
         }

         // Ok, we need to swap transient entry back from persistence store
         if (!mapEntry.isPersistent()) {
            this.persistentStore.remove(mapEntry);
         }

         assureTransientSpace(mapEntry);
         
         this.transientStore.put(mapEntry);
         mapEntry.isSwapped(false);
      } // synchronized(this)
      return mapEntry;
   }

   /**
    * Access all entries. 
    * <p />
    * TODO !!!: This method should be changed to an iterator approach
    * as if we have swapped messages they won't fit to memory. 
    * @see I_Map#getAll()
    */
   public I_MapEntry[] getAll(I_EntryFilter entryFilter) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering getAll()");
      synchronized (this) {
         //log.error(ME, "getAll() DEBUG ONLY: numSwapped=" + numSwapped() + " transient=" + this.transientStore.getNumOfEntries() + " persistentStore=" + this.persistentStore.getNumOfEntries());
         //log.error(ME, "getAll() DEBUG ONLY: " + toXml(""));

         /* !!!!
           I'm not shure if this conditions is enough for all cases
           so we do the save way if (true)
           For topicStore this is OK 
         */
         //if (numSwapped() > 0 || this.persistentStore.getNumOfEntries() > this.transientStore.getNumOfEntries() ) {
         if (true) {
            java.util.Map map = new java.util.TreeMap(); // To suppress same entry twice and to be sorted (sorted is not yet specified to be necessary)

            I_MapEntry[] ramEntries = this.transientStore.getAll(entryFilter);
            for(int i=0; i<ramEntries.length; i++) {
               map.put(new Long(ramEntries[i].getUniqueId()), ramEntries[i]);
            }
            //log.error(ME, "getAll() DEBUG ONLY: map.size=" + map.size() + " numSwapped=" + numSwapped() + " transient=" + this.transientStore.getNumOfEntries());

            if (this.persistentStore != null) {
               I_MapEntry[] persistEntries = this.persistentStore.getAll(entryFilter);
               if (persistEntries != null) {
                  for(int i=0; i<persistEntries.length; i++) {
                     if (persistEntries[i] == null) continue;
                     map.put(new Long(persistEntries[i].getUniqueId()), persistEntries[i]);
                  }
               }
               //log.error(ME, "getAll() DEBUG ONLY: map.size=" + map.size() + " numSwapped=" + numSwapped() + " persistentStore=" + this.persistentStore.getNumOfEntries());
            }

            return (I_MapEntry[])map.values().toArray(new I_MapEntry[map.size()]);
         }
         else {
            return this.transientStore.getAll(entryFilter);
         }
      }
   }

   /**
    * @see I_Map#remove(I_MapEntry)
    */
   public int remove(final I_MapEntry mapEntry) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("remove(" + mapEntry.getLogId() + ")");
      synchronized (this) {
         // search in RAM storage
         int num = this.transientStore.remove(mapEntry);
         int num2 = 0;
         if (mapEntry.isPersistent() || (num == 0 && numSwapped() > 0)) {
            if (this.persistentStore != null)
               num2 = this.persistentStore.remove(mapEntry);
         }
         return Math.max(num, num2);
      }
   }

   /**
    * @see I_Map#remove(long)
    */
   public int remove(final long uniqueId) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("remove(" + uniqueId + ")");
      synchronized (this) {
         I_MapEntry mapEntry = get(uniqueId);
         if (mapEntry == null) {
            return 0;
         }
         return remove(mapEntry);
      }
   }

   /**
    * @see I_Map#removeOldest()
    */
   public I_MapEntry removeOldest() throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "removeOldest is not implemented");
   }

   /**
    * @see I_Map#removeTransient()
    */
   public int removeTransient() throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "removeTransient is not implemented");
   }

   private long numSwapped() {
      if (this.persistentStore == null) {
         return 0L;
      }
      return this.persistentStore.getNumOfEntries() - this.persistentStore.getNumOfPersistentEntries();
   }

   /**
    * It returns the size of the queue. Note that this call will return the size
    * stored in cache, i.e. it will NOT make a call to the underlying DB.
    *
    * @see I_Map#getNumOfEntries()
    */
   public long getNumOfEntries() {
      long ret = 0L;
      if (this.persistentStore != null && this.isConnected) {
         ret = this.persistentStore.getNumOfEntries();
         if (ret < 0L) return this.transientStore.getNumOfEntries();
         ret += this.transientStore.getNumOfEntries() - this.transientStore.getNumOfPersistentEntries();
         return ret;
      }
      return this.transientStore.getNumOfEntries();
   }

   /**
    * It returns the size of persistent entries in the queue. Note that this call will return the size
    * stored in cache, i.e. it will NOT make a call to the underlying DB.
    *
    * @see I_Map#getNumOfPersistentEntries()
    */
   public long getNumOfPersistentEntries() {
      if (this.persistentStore != null && this.isConnected) {
         final long ret = this.persistentStore.getNumOfPersistentEntries();
         if (ret < 0L) return this.transientStore.getNumOfEntries();
         return ret;
      }
      return this.transientStore.getNumOfPersistentEntries();
   }

   /**
    * @see I_Map#getMaxNumOfEntries()
    */
   public long getMaxNumOfEntries() {
      if (this.persistentStore != null && this.isConnected)
         return this.persistentStore.getMaxNumOfEntries();
      return this.transientStore.getMaxNumOfEntries();
   }

   /**
    * @see I_Map#getNumOfBytes()
    */
   public long getNumOfBytes() {
      if (this.persistentStore != null && this.isConnected) {
         long ret = this.persistentStore.getNumOfBytes();
         if (ret < 0L) return this.transientStore.getNumOfBytes();
         ret += this.transientStore.getNumOfBytes() - this.transientStore.getNumOfPersistentBytes();
         return ret;
      }
      return this.transientStore.getNumOfBytes();
   }

   /**
    * @see I_Map#getNumOfPersistentBytes()
    */
   public long getNumOfPersistentBytes() {
      if (this.persistentStore != null && this.isConnected) {
         final long ret = this.persistentStore.getNumOfPersistentBytes();
         if (ret < 0L) return this.transientStore.getNumOfPersistentBytes();
         return ret;
      }
      return this.transientStore.getNumOfPersistentBytes();
   }

   /**
    * @see I_Map#getMaxNumOfBytes()
    */
   public long getMaxNumOfBytes() {
      if (this.persistentStore != null && this.isConnected)
         return this.persistentStore.getMaxNumOfBytes();
      return this.transientStore.getMaxNumOfBytes();
   }

   /**
    * Clears everything and removes the queue (i.e. frees the associated table)
    */
   public long clear() {
      long ret = 0L;
      try {
         ret = this.transientStore.clear();
      }
      catch (Exception ex) {
         log.severe("could not clear transient storage. Cause: " + ex.getMessage());
      }

      try {
         if (this.persistentStore != null && this.isConnected)
            ret += this.persistentStore.clear();
      }
      catch (Exception ex) {
         log.severe("could not clear persistent storage. Cause: " + ex.getMessage());
      }

      /*
      try {
         // to be notified about reconnections / disconnections
         this.glob.getJdbcQueueManager(this.storageId).unregisterListener(this);
      }
      catch (Exception ex) {
         log.error(ME, "could not unregister listener. Cause: " + ex.getMessage());
      }
      */

      //this.numOfBytes = 0L;
      //this.numOfEntries = 0L;

      return ret;
   }

   /**
    * Shutdown the implementation, sync with data store
    */
   public void shutdown() {
      if (log.isLoggable(Level.FINER)) log.finer("shutdown()");
      this.isDown = true;
      this.glob.unregisterMBean(this.mbeanHandle);
      long numTransients = getNumOfEntries() - getNumOfPersistentEntries();
      if (numTransients > 0) {
         log.warning("Shutting down persistence cache which contains " + numTransients + " transient messages");
      }
      this.transientStore.shutdown();
      if (this.persistentStore != null && this.isConnected)
         this.persistentStore.shutdown();

      try {
//         this.glob.getJdbcQueueManager(this.storageId).unregisterListener(this);
         if (this.persistentStore != null)
            this.persistentStore.unRegisterStorageProblemListener(this);
      }
      catch (Exception ex) {
         log.severe("could not unregister listener. Cause: " + ex.toString());
      }
   }

   public boolean isShutdown() {
      return this.isDown;
   }

   /**
    * JMX help
    * @return a human readable usage help string
    */
   public java.lang.String usage() {
      return "Manipulating the storage directly will most certainly destroy your data."
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

   /**
    * @return Internal state as an XML ASCII string
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(1000);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<PersistenceCachePlugin id='").append(getStorageId().getId());
      sb.append("' type='").append(getType());
      sb.append("' version='").append(getVersion());
      sb.append("' maxEntriesCache='").append(this.transientStore.getMaxNumOfEntries());
      sb.append("' maxBytesCache='").append(this.transientStore.getMaxNumOfBytes());
      sb.append("' maxEntries='").append(getMaxNumOfEntries());
      sb.append("' maxBytes='").append(getMaxNumOfBytes());
      sb.append("' numOfEntries='").append(getNumOfEntries());
      sb.append("' numOfBytes='").append(getNumOfBytes());
      sb.append("'>");
      sb.append(this.transientStore.toXml(extraOffset+Constants.INDENT));
      if (this.persistentStore != null)
         sb.append(this.persistentStore.toXml(extraOffset+Constants.INDENT));
      sb.append(offset).append("</PersistenceCachePlugin>");
      return sb.toString();
   }

   /**
    * Enforced by I_Plugin
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) {
//      this.pluginProperties = pluginInfo.getParameters();
      this.glob = (org.xmlBlaster.engine.Global)glob;

      this.pluginInfo = pluginInfo;
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

      try {
//         this.glob.getJdbcQueueManager(this.storageId).unregisterListener(this);
         if (this.persistentStore != null)
            this.persistentStore.unRegisterStorageProblemListener(this);
      }
      catch (Exception ex) {
         log.severe("could not unregister listener. Cause: " + ex.toString());
      }

      try {
         if (this.persistentStore != null && this.isConnected)
            this.persistentStore.destroy();
      }
      catch (XmlBlasterException ex) {
         e = ex;
      }

      this.transientStore.destroy();
      if (e != null) throw e;
   }

   /**
    * @see org.xmlBlaster.util.queue.I_StorageProblemNotifier#registerStorageProblemListener(I_StorageProblemListener)
    */
   public boolean registerStorageProblemListener(I_StorageProblemListener listener) {
      if (this.persistentStore == null) return false;
      return this.persistentStore.registerStorageProblemListener(listener);
   }


   /**
    * @see org.xmlBlaster.util.queue.I_StorageProblemNotifier#unRegisterStorageProblemListener(I_StorageProblemListener)
    */
   public boolean unRegisterStorageProblemListener(I_StorageProblemListener listener) {
      if (this.persistentStore == null || listener == null) return false;
      return this.persistentStore.unRegisterStorageProblemListener(listener);
   }


   /**
    * @see I_Map#change(I_MapEntry, I_ChangeCallback)
    */
   public I_MapEntry change(I_MapEntry entry, I_ChangeCallback callback) throws XmlBlasterException {
      if (entry == null) return null;
      synchronized(this) { // is this the correct synchronization ??
         long oldSizeInBytes = entry.getSizeInBytes(); // must be here since newEntry could reference same obj.
         I_MapEntry newEntry = entry;
         boolean oldIsPersistent = entry.isPersistent();
         if (callback != null) newEntry = callback.changeEntry(entry);
         if (newEntry == null) {
            return entry;
         }
         if (oldSizeInBytes != newEntry.getSizeInBytes()) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".change", "the size of the entry '" + entry.getUniqueId() + "' has changed from '" + oldSizeInBytes + "' to '" + newEntry.getSizeInBytes() +"'. This is not allowed");
         } 

         I_MapEntry retEntry = this.transientStore.change(newEntry, null);
         
         if (oldIsPersistent != retEntry.isPersistent()) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "Changing of persistence flag of '" + entry.getLogId() + "' to persistent=" + retEntry.isPersistent() + " is not implemented");
            // TODO: In case we changed the entry flag from persistent to transient it should be removed from the persistence.
         }
         
         if (newEntry.isPersistent()) {
            if (this.persistentStore != null && this.isConnected) {
               retEntry = this.persistentStore.change(newEntry, null);
            }
            else {
               if (log.isLoggable(Level.FINE)) log.fine("Can't update entry '" + entry.getLogId() + "' on persistence");
               //throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "Can't update entry '" + entry.getLogId() + "' on persistence");
            }
         }
         return retEntry;
      }
   }


   /**
    * @see I_Map#change(long, I_ChangeCallback)
    */
   public I_MapEntry change(long uniqueId, I_ChangeCallback callback) throws XmlBlasterException {
      synchronized (this) {
         I_MapEntry oldEntry = get(uniqueId);
         return change(oldEntry, callback);
      }
   }

   /**
    * @see I_Map#embeddedObjectsToXml(OutputStream, Properties)
    */
   public long embeddedObjectsToXml(OutputStream out, Properties props) throws Exception {
      I_Map ps = this.persistentStore;
      if (ps != null) {
         return ps.embeddedObjectsToXml(out, null);
      }
      log.warning("Sorry, dumping transient entries to '" + out + "' is not implemented");
      return 0;
   }
   
   /**
    * @see I_AdminMap#dumpEmbeddedObjectsToFile(String)
    */
   public String dumpEmbeddedObjectsToFile(String fileName) throws Exception {
      if (fileName == null || fileName.equalsIgnoreCase("String")) {
         fileName = this.storageId.getStrippedId() + ".xml";
      }
      File to_file = new File(fileName);
      if (to_file.getParent() != null) {
         to_file.getParentFile().mkdirs();
      }
      FileOutputStream out = new FileOutputStream(to_file);
      out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>".getBytes());
      out.write(("\n<"+this.storageId.getPrefix()+">").getBytes());
      long count = embeddedObjectsToXml(out, null);
      out.write(("\n</"+this.storageId.getPrefix()+">").getBytes());
      return "Dumped " + count + " entries to '" + to_file.toString() + "'";
   }

}
