/*------------------------------------------------------------------------------
Name:      PersistenceCachePlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.msgstore.cache;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.jdbc.I_ConnectionListener;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.engine.msgstore.StoragePluginManager;

import java.util.ArrayList;

/**
 * @author laghi@swissinfo.org
 * @author xmlBlaster@marcelruff.info
 * @see org.xmlBlaster.test.classtest.msgstore.I_MapTest 
 */
public class PersistenceCachePlugin implements I_Plugin, I_ConnectionListener, I_Map
{
   private String ME;
   private Global glob;
   private LogChannel log;

   private java.util.Properties pluginProperties; // properties via I_Plugin
   private QueuePropertyBase property;            // properties via I_Map
   boolean isDown = true;
   private StorageId queueId;

   private I_Map transientStore;
   private I_Map persistentStore;
   private boolean isConnected = false;

   /*
    * this boolean is set only under the time a recovery after having reconnected
    * to the DB. It is used to limit the synchronization
   private boolean storeNewPersistentRecovery = false;
    */
   
   /**
    * Triggered by persistent store (JDBC) on lost connection
    * @see I_ConnectionListener#disconnected()
    */
   public void disconnected() {
      this.log.call(ME, "disconnected");
      this.isConnected = false;
   }

   /**
    * Triggered by persistent store (JDBC) on reconnection
    * @see I_ConnectionListener#reconnected()
    */
   public void reconnected() {
      this.log.call(ME, "reconnected");
     /* remove all obsolete entries from the persistence. Obsolete are the
      * entries which are lower (lower priority and older) than the lowest
      * entry in the transient storage.
      */

      if (this.persistentStore == null) return; // should never happen

      //try {
         log.warn(ME, "Persistent store has reconnected, we may have a memory leak as send messsages are not cleaned up");
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

         log.warn(ME, "Persistent store has reconnected, current persistent messages are handled transient only, new ones will be handled persistent");
         /*
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
      //   this.log.error(ME, "exception occured when reconnecting. " + ex.getMessage());
      //}
   }

   /**
    * Is called after the instance is created.
    * @param uniqueQueueId A unique name, allowing to create a unique name for a persistent store (e.g. file name)
    * @see org.xmlBlaster.engine.msgstore.I_Map#initialize(StorageId, Object)
    */
   public void initialize(StorageId uniqueQueueId, Object userData) throws XmlBlasterException {
      if (this.isDown) {

         if (this.pluginProperties == null) {
            this.pluginProperties = new java.util.Properties(); // if loaded from testsuite without a PluginManager
         }

         this.property = null;
         this.glob = (org.xmlBlaster.engine.Global)((QueuePropertyBase)userData).getGlobal();
         this.log = glob.getLog("persistence");
         this.ME = this.getClass().getName() + "-" + uniqueQueueId;
         this.queueId = uniqueQueueId;
         try {
            this.property = (QueuePropertyBase)userData;
         }
         catch(Throwable e) {
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Can't configure queue, your properties are invalid", e);
         }
         if (log.CALL) log.call(ME, "Entering initialize(" + getType() + ", " + getVersion() + ")");

         StoragePluginManager pluginManager = glob.getStoragePluginManager();
         QueuePropertyBase queuePropertyBase = (QueuePropertyBase)userData;

         //instantiate and initialize the underlying queues

         String defaultTransient = this.pluginProperties.getProperty("transientMap", "RAM,1.0").trim();
         if (defaultTransient.startsWith(getType())) {
            log.error(ME,"Cache storage configured with transientMap=CACHE, to prevent recursion we set it to 'RAM,1.0'");
            defaultTransient = "RAM,1.0";
         }
         this.transientStore = pluginManager.getPlugin(defaultTransient, uniqueQueueId, createRamCopy(queuePropertyBase));
         if (log.TRACE) log.trace(ME, "Created transient part:" + this.transientStore.toXml(""));
         
         try {
            String defaultPersistent = this.pluginProperties.getProperty("persistentMap", "JDBC,1.0").trim();
            if (defaultPersistent.startsWith(getType())) {
               log.error(ME,"Cache storage configured with persistentMap=CACHE, to prevent recursion we set it to 'JDBC,1.0'");
               defaultPersistent = "JDBC,1.0";
            }
            this.persistentStore = pluginManager.getPlugin(defaultPersistent, uniqueQueueId, queuePropertyBase);

            this.isConnected = true;
            // to be notified about reconnections / disconnections
            this.glob.getJdbcQueueManager(this.queueId).registerListener(this);
            if (log.TRACE) log.trace(ME, "Created persistent part:" + this.persistentStore.toXml(""));
         }
         catch (XmlBlasterException ex) {
            this.log.error(ME, "could not initialize the persistent queue. Is the JDBC Driver jar file in the CLASSPATH ? Is the DB up and running ?" + ex.getMessage());
            // start a polling thread to see if the connection can be established later 

         }

         // do the queue specific stuff like delete all volatile entries in
         // the persistent queue
         if (this.persistentStore != null) {
            try {
               if (log.TRACE) log.trace(ME, "Initialize: Removing swapped entries from persistent store, numEntries=" + this.persistentStore.getNumOfEntries() + " numPersistentEntries=" + this.persistentStore.getNumOfPersistentEntries());
               this.persistentStore.removeTransient();
            }
            catch (XmlBlasterException ex) {
               this.log.error(ME, "could not remove transient entries (swapped entries) probably due to no connection to the DB, or the DB is down" + ex.getMessage());
            }

            // prefill cache (hack: works only for our JDBC queue which implements I_Queue as well)
            if (this.persistentStore instanceof org.xmlBlaster.util.queue.I_Queue) {
               if (log.TRACE) log.trace(ME, "Initialize: Prefilling cache storage with entries");
               if (this.persistentStore.getNumOfEntries() > 0) {
                  // initial fill of RAM queue ...
                  long maxBytes = this.transientStore.getMaxNumOfBytes();
                  // this.transientStore.getMaxNumOfEntries();
                  int maxEntries = -1;

                  ArrayList entries = null;
                  try {
                     entries = ((org.xmlBlaster.util.queue.I_Queue)this.persistentStore).peek(maxEntries, maxBytes);
                  }
                  catch (XmlBlasterException ex) {
                     this.log.error(ME, "could not reload data from persistence probably due to a broken connection to the DB or the DB is not up and running");
                  }
                  int n = entries.size();
                  log.info(ME, "Prefilling cache with " + n + " entries");
                  synchronized(this) {
                     for(int i=0; i<n; i++) {
                        I_MapEntry cleanEntry = (I_MapEntry)entries.get(i);
                        this.transientStore.put(cleanEntry);
                     }
                  }
               }
            }

         }
         this.isDown = false;
         if (log.TRACE) log.trace(ME, "Successful initialized: " + toXml(""));
      } // isDown?
   }

   /**
    * We set the cache props to the real props for RAM queue running under a cacheQueue
    */
   private QueuePropertyBase createRamCopy(QueuePropertyBase queuePropertyBase) {
      QueuePropertyBase ramCopy = (QueuePropertyBase)queuePropertyBase.clone();
      ramCopy.setMaxMsg(queuePropertyBase.getMaxMsgCache());
      ramCopy.setMaxBytes(queuePropertyBase.getMaxBytesCache());
      return ramCopy;
   }

   /**
    * @see I_Map#setProperties(Object)
    */
   public void setProperties(Object userData) throws XmlBlasterException {
      if (userData == null) return;
      if (log.CALL) log.call(ME, "Entering setProperties()");
      QueuePropertyBase newProp;
      try {
         newProp = (QueuePropertyBase)userData;
      }
      catch(Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Can't configure queue, your properties are invalid", e);
      }

      /* Do we need to protect against shrinking?
      if (this.property != null && this.property.getMaxMsg() > newProp.getMaxMsg()) {
         log.warn(ME, "Reconfigure of a RamQueuePlugin - getMaxNumOfEntries from " + this.property.getMaxMsg() +
                    " to " + newProp.getMaxMsg() + " is not supported, we ignore the new setting.");
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

      if (log.CALL) this.log.call(ME, "put(" + mapEntry.getLogId() + ")");
      XmlBlasterException e = null;
      int numPersistentPut = 0;
      int numTransientPut = 0;

      String errorText = spaceLeft(mapEntry, this);
      if (errorText != null) {
         if (log.TRACE) log.trace(ME, errorText);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES,
                   ME+"-put("+mapEntry.getLogId()+")", errorText);
      }

      synchronized(this) {

         // separate persistent from transient entries and store the persistents in persistence
         if (this.persistentStore != null && this.isConnected) {
            if (mapEntry.isPersistent()) {
               long spaceLeft = this.persistentStore.getMaxNumOfBytes() - this.persistentStore.getNumOfBytes();
               if (spaceLeft < mapEntry.getSizeInBytes()) {
                  String reason = "Persistent queue overflow, " + this.getNumOfBytes() +
                                  " bytes are in queue, try increasing property '" + 
                                  this.property.getPropName("maxBytes") + "'.";
                  this.log.warn(ME+"-put("+mapEntry.getLogId()+")", reason + this.toXml(""));
                  throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES,
                            ME+"-put("+mapEntry.getLogId()+")", reason);
               }
               try {
                  numPersistentPut = this.persistentStore.put(mapEntry);
               }
               catch (XmlBlasterException ex) {
                  this.log.error(ME, "put: an error occured when writing to the persistent queue, the persistent entry " + mapEntry.getLogId() +
                                " will temporarly be handled as transient. Is the DB up and running ? " + ex.getMessage() + "state "  + this.toXml(""));
                  // should an exception be rethrown here ?
               }
            }
         }

         if (spaceLeft(mapEntry, this.transientStore) == null) {
            numTransientPut = this.transientStore.put(mapEntry);
         }
         else if (numPersistentPut == 0) {  // if entry is marked as persistent it is already in persistentStore (see code above)
            // handle swapping (if any)
            if (this.log.TRACE) this.log.trace(ME+"-put("+mapEntry.getLogId()+")", "Swapping. Exceeding size state: " + toXml(""));
            if (this.persistentStore == null)
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME,
                     "put: no persistent queue configured, needed for swapping, entry " + mapEntry.getLogId() + " not handled");
            if (!this.isConnected)
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME,
                     "put: The DB is currently disconnected, entry " + mapEntry.getLogId() + " not handled");

            if (spaceLeft(mapEntry, this.persistentStore) == null) {
               try {
                  numPersistentPut = this.persistentStore.put(mapEntry);
               }
               catch (XmlBlasterException ex) {
                  this.log.error(ME, "put: an error occured when writing to the persistent queue, transient entry " +  mapEntry.getLogId() + 
                       " is not swapped and will be lost. Is the DB up and running ? " + ex.getMessage() + " state: " + toXml(""));
                  e = ex; // should an exception be rethrown here ?
               }
            }
            else
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_BYTES, ME,
                         "put: maximum size in bytes for the persistent queue exceeded when swapping, entry " + mapEntry.getLogId() + " not handled . State: " + toXml(""));
         }
      } // sync(this)

      if (e != null) throw e;
      if (numPersistentPut>0 || numTransientPut>0) {
         return 1;
      }
      // NOTE: It is possible that a persistent entry is not put to persistent storage
      // e.g. because of 'duplicate key' (entry existed already) and same with RAM queue
      // In this case the caller does get a 0
      return 0;
   }

   /**
    * Check is storage is big enough for entry
    * @param mapEntry may not be null
    * @return null There is space (otherwise the error text is returned)
    */
   private String spaceLeft(I_MapEntry mapEntry, I_Map map) {
      if (map == null || this.property == null) {
         return "Storage framework is down, current settings are" + toXml("");
      }

      if ((1 + map.getNumOfEntries()) > map.getMaxNumOfEntries())
         return "Queue overflow (number of entries), " + getNumOfEntries() +
                " entries are in queue, try increasing property '" +
                this.property.getPropName("maxMsg") + "' and '" +
                this.property.getPropName("maxMsgCache") + "', current settings are" + toXml("");

      if ((mapEntry.getSizeInBytes() + map.getNumOfBytes()) > map.getMaxNumOfBytes())
         return "Queue overflow, " + getMaxNumOfBytes() +
                " bytes are in queue, try increasing property '" + 
                this.property.getPropName("maxBytes") + "' and '" +
                this.property.getPropName("maxBytesCache") + "', current settings are" + toXml("");
      return null;
   }

   /**
    * Returns the unique ID of this queue
    */
   public StorageId getStorageId() {
      return this.queueId;
   }

   /**
    * @see I_Map#get(long)
    */
   public I_MapEntry get(final long uniqueId) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering get(" + uniqueId + ")");

      I_MapEntry mapEntry = this.transientStore.get(uniqueId);
      if (mapEntry != null) {
         return mapEntry;
      }

      if (this.persistentStore == null)
         return null;

      mapEntry = this.persistentStore.get(uniqueId);
      if (mapEntry == null) {
         return null;
      }

      // Ok, we need to swap transient entry back from persistence store
      synchronized(this) {
         if (!mapEntry.isPersistent()) {
            this.persistentStore.remove(mapEntry);
         }
         if (spaceLeft(mapEntry, this.transientStore) == null) {
            this.transientStore.put(mapEntry);
         }
         else {
            if (log.TRACE) log.trace(ME, "Performance tuning with LRU cache is missing");
         }
      }
      return mapEntry;
   }

   /**
    * @see I_Map[]#getAll()
    */
   public I_MapEntry[] getAll() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering getAll()");
      synchronized (this) {
         //log.error(ME, "getAll() DEBUG ONLY: numSwapped=" + numSwapped() + " transient=" + this.transientStore.getNumOfEntries() + " persistentStore=" + this.persistentStore.getNumOfEntries());
         //log.error(ME, "getAll() DEBUG ONLY: " + toXml(""));
         if (numSwapped() > 0) {
            java.util.Map map = new java.util.TreeMap(); // To suppress same entry twice and to be sorted (sorted is not yet specified to be necessary)

            I_MapEntry[] ramEntries = this.transientStore.getAll();
            for(int i=0; i<ramEntries.length; i++) {
               map.put(new Long(ramEntries[i].getUniqueId()), ramEntries[i]);
            }
            //log.error(ME, "getAll() DEBUG ONLY: map.size=" + map.size() + " numSwapped=" + numSwapped() + " transient=" + this.transientStore.getNumOfEntries());

            I_MapEntry[] persistEntries = this.persistentStore.getAll();
            for(int i=0; i<persistEntries.length; i++) {
               map.put(new Long(persistEntries[i].getUniqueId()), persistEntries[i]);
            }
            //log.error(ME, "getAll() DEBUG ONLY: map.size=" + map.size() + " numSwapped=" + numSwapped() + " persistentStore=" + this.persistentStore.getNumOfEntries());

            return (I_MapEntry[])map.values().toArray(new I_MapEntry[map.size()]);
         }
         else {
            return this.transientStore.getAll();
         }
      }
   }

   /**
    * @see I_Map#remove(I_MapEntry)
    */
   public int remove(final I_MapEntry mapEntry) throws XmlBlasterException {
      if (log.CALL) this.log.call(ME, "remove(" + mapEntry.getLogId() + ")");
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
    * @see I_Map#removeTransient()
    */
   public int removeTransient() throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "removeTransient not implemented");
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
      long ret = 0L;
      if (this.persistentStore != null && this.isConnected) {
         ret = this.persistentStore.getNumOfPersistentEntries();
         if (ret < 0L) return this.transientStore.getNumOfEntries();
         return this.persistentStore.getNumOfPersistentEntries();
      }
      return this.transientStore.getNumOfPersistentEntries();
   }

   /**
    * @see I_Map#getMaxNumOfEntries()
    */
   public long getMaxNumOfEntries() {
      long ret = 0L;
      if (this.persistentStore != null && this.isConnected)
         return this.persistentStore.getMaxNumOfEntries();
      return this.transientStore.getMaxNumOfEntries();
   }

   /**
    * @see I_Map#getNumOfBytes()
    */
   public long getNumOfBytes() {
      long ret = 0L;
      if (this.persistentStore != null && this.isConnected) {
         ret = this.persistentStore.getNumOfBytes();
         if (ret < 0L) return this.transientStore.getNumOfBytes();
         ret += this.transientStore.getNumOfBytes() - this.transientStore.getNumOfPersistentBytes();
      }
      return this.transientStore.getNumOfBytes();
   }

   /**
    * @see I_Map#getNumOfPersistentBytes()
    */
   public long getNumOfPersistentBytes() {
      long ret = 0L;
      if (this.persistentStore != null && this.isConnected) {
         ret = this.persistentStore.getNumOfPersistentBytes();
         if (ret < 0L) return this.transientStore.getNumOfPersistentBytes();
         return this.persistentStore.getNumOfPersistentBytes();
      }
      return this.transientStore.getNumOfPersistentBytes();
   }

   /**
    * @see I_Map#getMaxNumOfBytes()
    */
   public long getMaxNumOfBytes() {
      long ret = 0L;
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
         this.log.error(ME, "could not clear transient storage. Cause: " + ex.getMessage());
      }

      try {
         if (this.persistentStore != null && this.isConnected)
            ret += this.persistentStore.clear();
      }
      catch (Exception ex) {
         this.log.error(ME, "could not clear persistent storage. Cause: " + ex.getMessage());
      }

      /*
      try {
         // to be notified about reconnections / disconnections
         this.glob.getJdbcQueueManager(this.queueId).unregisterListener(this);
      }
      catch (Exception ex) {
         this.log.error(ME, "could not unregister listener. Cause: " + ex.getMessage());
      }
      */

      //this.numOfBytes = 0L;
      //this.numOfEntries = 0L;

      return ret;
   }

   /**
    * Shutdown the implementation, sync with data store
    * @param true: force shutdown, don't flush everything
    */
   public void shutdown(boolean force) {
      if (log.CALL) log.call(ME, "shutdown()");
      this.isDown = true;
      this.transientStore.shutdown(force);
      if (this.persistentStore != null && this.isConnected)
         this.persistentStore.shutdown(force);

      try {
         this.glob.getJdbcQueueManager(this.queueId).unregisterListener(this);
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

      sb.append(offset).append("<PersistenceCachePlugin id='").append(getStorageId().getId());
      sb.append("' type='").append(getType());
      sb.append("' version='").append(getVersion());
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
      this.pluginProperties = pluginInfo.getParameters();
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
    * destroys all the resources associated to this queue. Even the persistent
    * data is destroyed.
    */
   public void destroy() throws XmlBlasterException {
      XmlBlasterException e = null;
      this.isDown = true;
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
}
