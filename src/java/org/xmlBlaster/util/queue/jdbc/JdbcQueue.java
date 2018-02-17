/*------------------------------------------------------------------------------                              
Name:      JdbcQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue.jdbc;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.engine.msgstore.I_ChangeCallback;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_EntryFilter;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queue.I_QueuePutListener;
import org.xmlBlaster.util.queue.I_Storage;
import org.xmlBlaster.util.queue.I_StoragePlugin;
import org.xmlBlaster.util.queue.I_StorageProblemListener;
import org.xmlBlaster.util.queue.I_StorageSizeListener;
import org.xmlBlaster.util.queue.ReturnDataHolder;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.StorageSizeListenerHelper;

/**
 * Persistence queue implementation on a DB based on JDBC.
 * <p>
 * Loaded via xmlBlaster.properties, there are three implementation
 * (the first, using a table per topic, was dropped long ago).
 * <p>
 * org.xmlBlaster.util.queue.jdbc.JdbcQueueCommonTablePlugin<br />
 * was the operational variant up to v1.6.2+ (2008-10) using one XB_ENTRIES table
 * </p>
 * <p>
 * org.xmlBlaster.util.queue.jdbc.JdbcQueue<br />
 * is the new implementation since v1.6.3 using three tables XBSTORE, XBREF, XBMEAT
 * </p>
 * Example:
 * <pre>
 * JdbcStorage[postgres]=org.xmlBlaster.util.queue.jdbc.JdbcQueue,\
                      url=jdbc:postgresql://localhost:5432/test,\
                      user=postgres,\
                      password=,\
                      connectionPoolSize=1,\
                      connectionBusyTimeout=90000,\
                      maxWaitingThreads=300,\
                      tableNamePrefix=XB_,\
                      entriesTableName=ENTRIES,\
                      dbAdmin=true
 * </pre>
 * @author michele@laghi.eu
 * @author xmlBlaster@marcelruff.info
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/queue.jdbc.commontable.html">The queue.jdbc.commontable requirement</a>
 * @see JdbcQueueCommonTablePlugin
 */
public final class JdbcQueue implements I_Queue, I_StoragePlugin, I_Map {
   private String ME;
   private boolean notifiedAboutAddOrRemove = false;
   private Global glob;
   private static Logger log = Logger.getLogger(JdbcQueue.class.getName());
   private QueuePropertyBase property;
   private XBDatabaseAccessor databaseAccessor = null;
   private I_QueuePutListener putListener;
   // to set it to -999L makes it easier to identify than -1L
   private long numOfEntries = -999L;
   private long numOfPersistentEntries = -999L;
   private long numOfPersistentBytes = -999L;
   private long numOfBytes = -999L;
   boolean isDown = true;
   private static boolean isWarned;

   /** Monitor object used to synchronize the count of sizes */
   private Object modificationMonitor = new Object();
   private PluginInfo pluginInfo = null;

   private boolean debug = false;

   private int entryCounter;
   
   private StorageSizeListenerHelper storageSizeListenerHelper;
   private XBStore xbStore;
   private StorageId storageId;
   
   public JdbcQueue() {
      storageSizeListenerHelper = new StorageSizeListenerHelper(this);
   }
   
   public boolean isTransient() {
      return false;
   }

   /**
    * This method resets all cached sizes and counters. While testing it
    * could be invoked before each public invocation to see which method fails ...
    */
   private final void resetCounters() {
      try {
         numOfPersistentBytes = -999L;
         numOfPersistentEntries = -999L;
         numOfBytes = -999L;
         numOfEntries = -999L;
         numOfPersistentBytes = getNumOfPersistentBytes_(true);
         numOfPersistentEntries = getNumOfPersistentEntries_(true);
         numOfBytes = getNumOfBytes_();
         numOfEntries = getNumOfEntries_();
      }
      catch (XmlBlasterException ex) {
         if (log.isLoggable(Level.FINE)) log.fine("resetCounters exception occured: " + ex.getMessage());
      }
   }


   /**
    * Check is storage is big enough for entry
    * @param mapEntry may not be null
    * @return null There is space (otherwise the error text is returned)
    */
   private String spaceLeft(long numOfEntries, long sizeInBytes) {
      
      // allow one owerload only ...
      numOfEntries = 0L;
      sizeInBytes  = 0L;

      if (property == null) {
         return "Storage framework is down, current settings are" + toXml("");
      }

      if ((numOfEntries + getNumOfEntries()) > getMaxNumOfEntries())
         return "Queue overflow (number of entries), " + getNumOfEntries() +
                " entries are in queue, try increasing property '" +
                property.getPropName("maxEntries") + "' and '" +
                property.getPropName("maxEntriesCache") + "', current settings are" + toXml("");

      if ((sizeInBytes + getNumOfBytes()) > getMaxNumOfBytes())
         return "Queue overflow, " + getMaxNumOfBytes() +
                " bytes are in queue, try increasing property '" + 
                property.getPropName("maxBytes") + "' and '" +
                property.getPropName("maxBytesCache") + "', current settings are" + toXml("");
      return null;
   }

   /**
    * Returns the factory for a specific queue. It strips the queueId to
    * find out to which manager it belongs. If such a manager does not exist
    * yet, it is created and initialized.
    * A queueId must be of the kind: cb:some/id/or/someother
    * where the important requirement here is that it contains a ':' character.
    * text on the left side of the separator (in this case 'cb') tells which
    * kind of queue it is: for example a callback queue (cb) or a client queue.
    */
   protected XBDatabaseAccessor getFactory(PluginInfo  plugInfo) throws XmlBlasterException {
      String location = ME + "/type '" + plugInfo.getType() + "' version '" + plugInfo.getVersion() + "'";
      String queueFactoryName = plugInfo.toString(); //  + "-" + pluginInfo.getTypeVersion();
      Object obj = glob.getObjectEntry(queueFactoryName);              
      XBDatabaseAccessor qFactory = null;
      try {
         if (obj == null) {
           synchronized (XBDatabaseAccessor.class) {
              obj = glob.getObjectEntry(queueFactoryName); // could have been initialized meanwhile              
              if ( obj == null) {
                 
                 boolean useXBDatabaseAccessorDelegate = glob.get("xmlBlaster/useXBDatabaseAccessorDelegate", true,
                        null, pluginInfo);
                  qFactory = (useXBDatabaseAccessorDelegate) ? new XBDatabaseAccessorDelegate()
                        : new XBDatabaseAccessor();
                 if (log.isLoggable(Level.FINE)) 
                    log.fine("Created JdbcManagerCommonTable instance for storage plugin configuration '" + queueFactoryName + "'");
                 
                 glob.addObjectEntry(queueFactoryName, qFactory);
              }
              else
                 qFactory = (XBDatabaseAccessor)obj;
           }
         }
         else 
            qFactory = (XBDatabaseAccessor)obj;
         qFactory.initFactory(glob, plugInfo);
      }
      catch (Throwable ex) {
         if (log.isLoggable(Level.FINE)) {
            log.fine("getFactory internal exception: " + ex.toString());
            ex.printStackTrace();
         }
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, location, "getFactory: throwable when initializing the connection pool", ex);
      }

      if (glob.getWipeOutDB()) {
         synchronized (glob) {
            if (glob.getWipeOutDB()) {
               qFactory.wipeOutDB(true);
               glob.setWipeOutDB(false);
            }
         }
      }
      return qFactory;
   }

   /**
    * Is called after the instance is created.
    * @param uniqueQueueId A unique name, allowing to create a unique name for a persistent store (e.g. file name)
    * @see I_Queue#initialize(StorageId, Object)
    */
   synchronized public void initialize(StorageId uniqueQueueId, Object userData) throws XmlBlasterException {
      if (isDown) {
         this.storageId = uniqueQueueId;
         property = null;
         setProperties(userData);
         // TODO pass the XBStore object here (somehow)
         ME = getClass().getName() + "-" + uniqueQueueId;
         // storageId = uniqueQueueId;

         if (property != null && glob.isServerSide() != property.getGlobal().isServerSide()) {
            log.severe("Incompatible globals this.property.getGlobal().isServerSide()=" + property.getGlobal().isServerSide() + ": " + Global.getStackTraceAsString(null));
         }
         glob = property.getGlobal();

         databaseAccessor = getFactory(pluginInfo);
         xbStore = databaseAccessor.getXBStore(uniqueQueueId);
         EntryCount entryCount = databaseAccessor.getNumOfAll(xbStore);
         setEntryCount(entryCount);
         isDown = false;
         databaseAccessor.registerQueue(this);
         if (log.isLoggable(Level.FINE)) 
            log.fine("Successful initialized");
      }

      boolean dbg = glob.getProperty().get("queue/debug", false);
      if (dbg == true) 
         property.setDebug(true);
      debug = property.getDebug();
      if (debug) {
         log.warning("initialize: debugging is enabled");
      }
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
         log.severe("Can't configure queue, your properties are invalid: " + e.toString());
         e.printStackTrace();
         return;
      }

      property = newProp;
   }

   /**
    * Access the current queue configuration
    */
   public Object getProperties() {
      return property;
   }

   public void setNotifiedAboutAddOrRemove(boolean notify) {
      notifiedAboutAddOrRemove = notify;
   }

   public boolean isNotifiedAboutAddOrRemove() {
      return notifiedAboutAddOrRemove;
   }

   /**
    * @see I_Queue#addPutListener(I_QueuePutListener)
    */
   public void addPutListener(I_QueuePutListener l) {
      if (l == null)
         throw new IllegalArgumentException(ME + ": addPustListener(null) is not allowed");
      if (putListener != null)
         throw new IllegalArgumentException(ME + ": addPustListener() failed, there is a listener registered already");
      putListener = l;
   }

   /**
    * @see I_Queue#removePutListener(I_QueuePutListener)
    */
   public void removePutListener(I_QueuePutListener l) {
      putListener = null;
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
      final boolean isRef = true;
      return (ArrayList)databaseAccessor.getEntries(xbStore, -1, -1L, entryFilter, isRef, this);
   }

   /**
    * @see I_Queue#put(I_QueueEntry, boolean)
    */
   public void put(I_QueueEntry queueEntry, boolean ignorePutInterceptor)
      throws XmlBlasterException
   {
      if (queueEntry == null) 
         return;

      if ((putListener != null) && (!ignorePutInterceptor)) {
         // Is an interceptor registered (and not bypassed) ?
         if (putListener.putPre(queueEntry) == false)
            return;
      }
      put(queueEntry);

      if (putListener != null && !ignorePutInterceptor) {
         putListener.putPost(queueEntry);
      }
   }

   /**
    * Internally used for I_MapEntry and I_QueueEntry
    * @return true on success
    */
   private boolean put(I_Entry entry) throws XmlBlasterException {
      boolean ret = false;
      synchronized (modificationMonitor) {
         String exTxt = null;
         if ((exTxt=spaceLeft(1, entry.getSizeInBytes())) != null)
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, exTxt);
         if (getNumOfBytes_() > getMaxNumOfBytes()) {
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_BYTES, ME, "put: the maximum number of bytes reached." +
                   " Number of bytes=" + numOfBytes + " maxmimum number of bytes=" + getMaxNumOfBytes() + " status: " + toXml(""));
         }
         try {
            if (databaseAccessor.addEntry(xbStore, entry)) {
               numOfEntries++;
               numOfBytes += entry.getSizeInBytes();
               if (entry.isPersistent()) {
                  numOfPersistentEntries++;
                  numOfPersistentBytes += entry.getSizeInBytes();
               }
               ret = true;
            }
         }
         catch (XmlBlasterException ex) {
            resetCounters();
            throw ex;
         }
      }
      storageSizeListenerHelper.invokeStorageSizeListener();
      return ret;
   }

   /**
    * @see I_Queue#put(I_QueueEntry[], boolean)
    */
   public void put(I_QueueEntry[] queueEntries, boolean ignorePutInterceptor)
      throws XmlBlasterException {
      XmlBlasterException ex0 =  null;

      if (queueEntries == null) 
         return;

      if ((putListener != null) &&(!ignorePutInterceptor)) {
         // Is an interceptor registered (and not bypassed) ?
         if (putListener.putPre(queueEntries) == false)
            return;
      }

      synchronized (modificationMonitor) {
         String exTxt = null;
         if ((exTxt=spaceLeft(queueEntries.length, /*calculateSizeInBytes(queueEntries)*/ 0L)) != null)
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, exTxt);

         if (getNumOfBytes_() > getMaxNumOfBytes()) {
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_BYTES, ME, "put[]: the maximum number of bytes reached." +
                  " Number of bytes=" + numOfBytes + " maxmimum number of bytes=" + getMaxNumOfBytes() + " status: " + toXml(""));
         }

         try {

            int[] help = this.databaseAccessor.addEntries(xbStore, queueEntries);
            for (int i=0; i < queueEntries.length; i++) {
            // boolean isProcessed = this.manager.addEntry(this.storageId.getStrippedId(), queueEntries[i]);
               boolean isProcessed = help[i] > 0 || help[i] == -2; // !!! JDK 1.4 only: Statement.SUCCESS_NO_INFO = -2;
               if (log.isLoggable(Level.FINE)) {
                  log.fine("put(I_Entry[]) the entry nr. " + i + " returned '" + help[i] + "'");
               }
               if (isProcessed) {
                  numOfEntries++;
                  numOfBytes += queueEntries[i].getSizeInBytes();
                  if (queueEntries[i].isPersistent()) {
                     numOfPersistentEntries++;
                     numOfPersistentBytes += queueEntries[i].getSizeInBytes();
                  }
               }
            }
         }
         catch (XmlBlasterException ex) {
            ex0 = ex;
            resetCounters();
         }
         if (ex0 != null)
            throw ex0;
      }

      if (putListener != null && !ignorePutInterceptor) {
         putListener.putPost(queueEntries);
      }
      storageSizeListenerHelper.invokeStorageSizeListener();
   }


   /**
    * Returns the unique ID of this queue
    */
   public StorageId getStorageId() {
      return storageId;
   }

   /**
    * Currently not supported by I_Queue. 
    */
   public I_QueueEntry take() throws XmlBlasterException
   {
      // note that this method could be drastically improved
      // however it is unlikely to be used so I avoid that tuning now
      synchronized (modificationMonitor) {
         I_QueueEntry ret = peek();
         removeRandom(ret.getUniqueId());
         return ret;
      }
   }

   /**
    * @see I_Queue#takeWithPriority(int,long,int,int)
    */
   public ArrayList takeWithPriority(int numOfEntries, long numOfBytes, int minPriority, int maxPriority)
      throws XmlBlasterException {
      if (numOfEntries == 0) return new ArrayList();
      synchronized (modificationMonitor) {
         ArrayList ret = peekWithPriority(numOfEntries, numOfBytes, minPriority, maxPriority);
         removeRandom( (I_QueueEntry[])ret.toArray(new I_QueueEntry[ret.size()]) );
         return ret;
      }
   }


   /**
    * Currently not supported by I_Queue. 
    */
   public ArrayList take(int numEntries, long numBytes) throws XmlBlasterException {
      if (numEntries == 0) return new ArrayList();
      ArrayList ret = null;
      I_EntryFilter entryFilter = null;
      synchronized(this.modificationMonitor) {
         try {
            final boolean isRef = true;
            ret = (ArrayList)databaseAccessor.getEntries(xbStore, numEntries, numBytes, entryFilter, isRef, this);

            XBRef[] entries =(XBRef[])ret.toArray(new XBRef[ret.size()]);
            long deleted = databaseAccessor.deleteEntries(xbStore, entries, null);
            this.numOfEntries -= deleted;
         }
         finally {
            resetCounters();
         }
      }
      this.storageSizeListenerHelper.invokeStorageSizeListener();
      return ret;
   }


   /**
    * @see I_Queue#takeLowest(int, long, I_QueueEntry, boolean)
    */
   public ArrayList takeLowest(int numEntries, long numBytes, I_QueueEntry limitEntry, boolean leaveOne)
      throws XmlBlasterException {

      // I could change the concept here by just checking if an entry with the
      // given uniqueId is found the search algorithm should break. This would
      // increase performance. However this method is probably never called on
      // this particular implementation.

      long minUniqueId = 0L;
      int maxPriority = Integer.MAX_VALUE;
      if (limitEntry != null) {
         minUniqueId = limitEntry.getUniqueId();
         maxPriority = limitEntry.getPriority();
      }

      ReturnDataHolder ret = null;
      synchronized(this.modificationMonitor) {
         try {
            ret = databaseAccessor.getAndDeleteLowest(xbStore, numEntries, numBytes, maxPriority, minUniqueId, leaveOne, true, this);
            numOfBytes -= ret.countBytes;
            numOfEntries -= ret.countEntries;
            numOfPersistentBytes -= ret.countPersistentBytes;
            numOfPersistentEntries -= ret.countPersistentEntries;
         }
         catch (XmlBlasterException ex) {
            resetCounters();
            throw ex;
         }
      }
      storageSizeListenerHelper.invokeStorageSizeListener();
      if (ret == null) 
         return null;
      return ret.list;
   }


   /**
    * @see I_Queue#peekLowest(int, long, I_QueueEntry, boolean)
    */
   public ArrayList peekLowest(int numEntries, long numBytes, I_QueueEntry limitEntry, boolean leaveOne)
      throws XmlBlasterException {

      long minUniqueId = 0L;
      int maxPriority = Integer.MAX_VALUE;
      if (limitEntry != null) {
         minUniqueId = limitEntry.getUniqueId();
         maxPriority = limitEntry.getPriority();
      }

      ReturnDataHolder ret = databaseAccessor.getAndDeleteLowest(xbStore, numEntries, numBytes, maxPriority, minUniqueId, leaveOne, false, this);
      return ret.list;
   }


   /**
    * @see I_Queue#peek()
    */
   public I_QueueEntry peek() throws XmlBlasterException
   {
      I_EntryFilter entryFilter = null;
      final boolean isRef = true;
      List/*<I_Entry>*/ ret = databaseAccessor.getEntries(xbStore, 1, -1L, entryFilter, isRef, this);
      if (ret.size() < 1) return null;
      return (I_QueueEntry)ret.get(0);
   }


   /**
    * @see I_Queue#peek(int,long)
    */
   public ArrayList peek(int numEntries, long numBytes) throws XmlBlasterException {
      if (numEntries == 0) return new ArrayList();
      I_EntryFilter entryFilter = null;
      final boolean isRef = true;
      ArrayList ret = (ArrayList)databaseAccessor.getEntries(xbStore, numEntries, numBytes, entryFilter, isRef, this);
      return ret;
   }
   
   /**
    * @see I_Queue#peekStartAt(int,long,I_QueueEntry)
    */
   public ArrayList peekStartAt(int numOfEntries, long numOfBytes, I_QueueEntry firstEntryExlusive) throws XmlBlasterException {
      if (firstEntryExlusive == null)
         return peek(numOfEntries, numOfBytes);
      if (numOfEntries == 0) return new ArrayList();
      I_EntryFilter entryFilter = null;
      ArrayList ret = (ArrayList)databaseAccessor.getRefEntries(xbStore, numOfEntries, numOfBytes, entryFilter, this, firstEntryExlusive);
      return ret;
   }

   /**
    * @see I_Queue#peekSamePriority(int, long)
    */
   public ArrayList peekSamePriority(int numEntries, long numBytes) throws XmlBlasterException {
      if (numEntries == 0) 
         return new ArrayList();
      ArrayList ret = (ArrayList)databaseAccessor.getEntriesBySamePriority(xbStore, numEntries, numBytes);
      return ret;
   }

   /**
    * @see I_Queue#peekWithPriority(int, long, int, int)
    */
   public ArrayList peekWithPriority(int numEntries, long numBytes, int minPriority, int maxPriority) throws XmlBlasterException {
      if (numEntries == 0) 
         return new ArrayList();
      ArrayList ret = (ArrayList)databaseAccessor.getEntriesByPriority(xbStore, numEntries, numBytes, minPriority, maxPriority);
      return ret;
   }


   /**
    * @see I_Queue#peekWithLimitEntry(I_QueueEntry)
    * @deprecated
    */
   public ArrayList peekWithLimitEntry(I_QueueEntry limitEntry) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("peekWithLimitEntry called");
      if (limitEntry == null) 
         return new ArrayList();
      return (ArrayList)databaseAccessor.getEntriesWithLimit(xbStore, limitEntry, this);
   }

   /**
    * @see I_Queue#removeWithLimitEntry(I_QueueEntry, boolean)
    */
   public long removeWithLimitEntry(I_QueueEntry limitEntry, boolean inclusive) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("removeWithLimitEntry called");
      if (limitEntry == null) return 0L;
      long ret = 0L;
      synchronized(modificationMonitor) {
         try {
            XBRef xbRef = limitEntry.getRef(); 
            ret = databaseAccessor.removeEntriesWithLimit(xbStore, xbRef, inclusive);
            if (ret != 0) { // since we are not able to calculate the size in the cache we have to recalculate it
               resetCounters();
            }
         }
         catch (XmlBlasterException ex) {
            resetCounters();
            throw ex;
         }
      }
      storageSizeListenerHelper.invokeStorageSizeListener();
      return ret;
   }


   /**
    * Removes the first element in the queue
    * This method does not block.
    * @return Number of messages erased (0 or 1)
    * @throws XmlBlasterException if the underlying implementation gets an exception.
    */
   public int remove() throws XmlBlasterException {
      return (int)removeNum(1L);
   }


   /**
    * Removes max numOfEntries messages.
    * This method does not block.
    * @param numEntries Erase num entries or less if less entries are available, -1 erases everything
    * @return Number of entries erased
    * @throws XmlBlasterException if the underlying implementation gets an exception.
    */
   public long removeNum(long numEntries) throws XmlBlasterException {
      if (numEntries == 0) 
         return 0L;
      long ret = 0L;
      synchronized(this.modificationMonitor) {
         try {
            ret = databaseAccessor.deleteFirstRefs(xbStore, numEntries);
            EntryCount entryCount = databaseAccessor.getNumOfAll(xbStore);
            setEntryCount(entryCount);
         }
         catch (XmlBlasterException ex) {
            resetCounters();
            throw ex;
         }
      }
      this.storageSizeListenerHelper.invokeStorageSizeListener();
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
      long[] args = new long[1];
      args[0] = dataId;
      if (removeRandom(args)[0]) 
         return 1;
      else return 0;
   }


   /**
    * Removes the given entries.
    * @param msgQueueEntry the entry to erase.
    */
   public boolean[] removeRandom(long[] dataIdArray) throws XmlBlasterException {
      XBMeat[] meats = new XBMeat[dataIdArray.length];
      for (int i=0; i < meats.length; i++)
         meats[i] = new XBMeat(dataIdArray[i]);
      ArrayList list = (ArrayList)databaseAccessor.getEntries(xbStore, null, meats);
      return removeRandom((I_Entry[])list.toArray(new I_Entry[list.size()]));
   }


   /**
    * @see I_Queue#removeRandom(I_Entry)
    */
   public int removeRandom(I_Entry entry) throws XmlBlasterException {
      if (entry == null) 
         return 0;
      long id = entry.getUniqueId();
      long currentAmount = entry.getSizeInBytes();
      long currentPersistentSize = 0L;
      long currentPersistentEntries = 0L;

      if (entry.isPersistent()) {
         currentPersistentSize += currentAmount;
         currentPersistentEntries = 1L;
      }
      int ret = 0;
      synchronized(modificationMonitor) {
         XBRef ref = entry.getRef();
         long refId = -1L;
         long meatId = -1L;
         if (ref != null) {
            refId = ref.getId();
            meatId = ref.getMeatId();
         }
         else if (entry.getMeat() != null)
            meatId = entry.getMeat().getId();
         ret = databaseAccessor.deleteEntry(xbStore, refId, meatId);
         if (ret > 0) { // then we need to retrieve the values
            numOfEntries--;
            numOfBytes -= currentAmount;
            numOfPersistentBytes -= currentPersistentSize;
            numOfPersistentEntries -= currentPersistentEntries;
         }
      }
      storageSizeListenerHelper.invokeStorageSizeListener();
      return ret;
   }

   /**
    * @see I_Queue#removeRandom(I_Entry[])
    */
   public boolean[] removeRandom(I_Entry[] queueEntries) throws XmlBlasterException {
      if (queueEntries == null || queueEntries.length == 0) 
         return new boolean[0];
      boolean ret[];
      long currentAmount = 0L;
      long currentPersistentSize = 0L;
      long currentPersistentEntries = 0L;
      
      
      XBRef[] refs = null;
      XBMeat[] meats = null;
      boolean hasRef = queueEntries[0].getRef() != null;
      boolean hasMeat = !hasRef; // queueEntries[0].getMeat() != null;
      long oldNumberOfEntries = numOfEntries;
      if (hasRef)
         refs = new XBRef[queueEntries.length];
      if (hasMeat)
         meats = new XBMeat[queueEntries.length];
      
      for (int i=0; i < queueEntries.length; i++) {
         currentAmount += queueEntries[i].getSizeInBytes();
         if (queueEntries[i].isPersistent()) {
            currentPersistentSize += queueEntries[i].getSizeInBytes();
            currentPersistentEntries++;
         }
         if (hasRef)
            refs[i] = queueEntries[i].getRef();
         if (hasMeat)
            meats[i] = queueEntries[i].getMeat();
      }

      synchronized(modificationMonitor) {
         ret = new boolean[queueEntries.length];
         try {
            long sum = databaseAccessor.deleteEntries(xbStore, refs, meats);
            if (log.isLoggable(Level.FINE)) 
               log.fine("randomRemove: the number of removed entries is '" + sum + "'");
            this.numOfEntries -= sum;

            if ((int)sum != queueEntries.length) { // then we need to retrieve the values
               resetCounters();  // now it can be optimized since boolean[] is given back
               // we know in caseof an error the successful ones are the first ones
               long nmax = oldNumberOfEntries - numOfEntries;
               for (int i=0; i < (int)nmax; i++)
                  ret[i] = true;
            }
            else {
               this.numOfBytes -= currentAmount;
               this.numOfPersistentBytes -= currentPersistentSize;
               this.numOfPersistentEntries -= currentPersistentEntries;
               for (int i=0; i < ret.length; i++)
                  ret[i] = true;
            }
         }
         catch (XmlBlasterException ex) {
            resetCounters();
            throw ex;
         }
      }
      storageSizeListenerHelper.invokeStorageSizeListener();
      return ret;
   }


   /**
    * @see I_Queue#removeWithPriority(long, long, int, int)
    */
   public long removeWithPriority(long numEntries, long numBytes, int minPriority, int maxPriority)
      throws XmlBlasterException {
      ArrayList array = this.peekWithPriority((int)numEntries, numBytes, minPriority, maxPriority);
      boolean ret[] = removeRandom((I_QueueEntry[])array.toArray(new I_QueueEntry[array.size()]));
      long count = 0L;
      for (int i=0; i < ret.length; i++) if (ret[i]) count++;
      return count;
   }

   private void setEntryCount(EntryCount entryCount) {
      this.numOfEntries = entryCount.numOfEntries;
      this.numOfPersistentEntries = entryCount.numOfPersistentEntries;
      this.numOfBytes = entryCount.numOfBytes;
      this.numOfPersistentBytes = entryCount.numOfPersistentBytes;
   }

   /**
    * It returns the size of the queue. Note that this call will return the size
    * stored in cache.
    * In case this value is negative (which means a previous attempt to read from the
    * DB failed) it will synchronize against the DB by making a call to the DB.
    * If that fails it will return -1L.
    * If the log DUMP is set to true, then a refresh of the cache is done by every invocation
    * and if the cached value is different from the real value an error is written.
    *
    * @see I_Queue#getNumOfEntries()
    * @exception XmlBlasterException if number is not retrievable
    */
   private final long getNumOfEntries_() throws XmlBlasterException {
      if (numOfEntries > -1L && !debug) 
         return numOfEntries;
      synchronized (modificationMonitor) {
         long oldValue = numOfEntries;
         EntryCount entryCount = databaseAccessor.getNumOfAll(xbStore);
         setEntryCount(entryCount);
         //this.numOfEntries = this.manager.getNumOfEntries(getStorageId().getStrippedId());
         if (debug) {
            if (oldValue != numOfEntries && oldValue != -999L) {  // don't log if explicitly set the oldValue
               String txt = "getNumOfEntries: an inconsistency occured between the cached value and the real value of 'numOfEntries': it was '" + oldValue + "' but should have been '" + this.numOfEntries + "'";
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, txt + toXml(""));
            }
         }
         else if (log.isLoggable(Level.FINE)) 
            log.fine("getNumOfEntries_ old (cached) value: '" + oldValue + "' new (real) value: '" + numOfEntries + "'");
         return numOfEntries;
      }
   }


   /**
    * It returns the size of the queue. Note that this call will return the size
    * stored in cache.
    * In case this value is -1L (which means a previous attempt to read from the
    * DB failed) it will synchronize against the DB by making a call to the DB.
    * If that fails it will return -1L.
    *
    * @see I_Queue#getNumOfEntries()
    */
   public long getNumOfEntries() {
      try {
         return getNumOfEntries_();
      }
      catch (XmlBlasterException ex) {
         log.severe("getNumOfEntries, exception: " + ex.getMessage());
         return numOfEntries;
      }
   }


   /**
    * It returns the number of persistent entries in the queue.
    * In case this value is -1L (which means a previous attempt to read from the
    * DB failed) it will synchronize against the DB by making a call to the DB.
    * If that fails it will return -1L.
    *
    * @param verbose If true we throw an exception on errors, if false we ignore the error silently
    * @see I_Queue#getNumOfPersistentEntries()
    */
   private long getNumOfPersistentEntries_(boolean verbose) throws XmlBlasterException {
      if (numOfPersistentEntries > -1L && !debug) 
         return numOfPersistentEntries;
      synchronized (modificationMonitor) {
         try {
            long oldValue = numOfPersistentEntries;
            EntryCount entryCount = databaseAccessor.getNumOfAll(xbStore);
            setEntryCount(entryCount);
            if (debug) {
               if (oldValue != numOfPersistentEntries && oldValue != -999L) {  // don't log if explicitly set the oldValue
                  String txt = "getNumOfPersistentEntries: an inconsistency occured between the cached value and the real value of 'numOfPersistentEntries': it was '" + oldValue + "' but should have been '" + this.numOfPersistentEntries + "'";
                  throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, txt + toXml(""));
               }
            }
            else if (log.isLoggable(Level.FINE))
               log.fine("getNumOfPersistentEntries_ old (cached) value: '" + oldValue + "' new (real) value: '" + numOfPersistentEntries + "'");
            return numOfPersistentEntries;
         }
         catch (XmlBlasterException ex) {
            if (verbose) { // If called from toXml() we need to suppress this exeption because we here call toXml() again
               throw ex;   // the verbose flag is probably not needed anymore ...
            }
            return -1L;
         }
      }
   }


   /**
    * It returns the number of persistent entries in the queue.
    * In case this value is -1L (which means a previous attempt to read from the
    * DB failed) it will synchronize against the DB by making a call to the DB.
    * If that fails it will return -1L.
    *
    * @see I_Queue#getNumOfPersistentEntries()
    */
   public long getNumOfPersistentEntries() {
      try {
         return getNumOfPersistentEntries_(true);
      }
      catch (XmlBlasterException ex) {
         log.severe("getNumOfEntries, exception: " + ex.getMessage());
         return numOfPersistentEntries;
      }
   }


   /**
    * @see I_Queue#getMaxNumOfEntries()
    */
   public long getMaxNumOfEntries() {
      return property.getMaxEntries();
   }


   /**
    * It returns the size of the queue. Note that this call will return the size
    * stored in cache.
    * In case this value is -1L (which means a previous attempt to read from the
    * DB failed) it will synchronize against the DB by making a call to the DB.
    * If that fails it will return -1L.
    *
    * @see I_Queue#getNumOfBytes()
    */
   private long getNumOfBytes_() throws XmlBlasterException {
      if (numOfBytes > -1L && !debug) 
         return numOfBytes;
      synchronized (modificationMonitor) {
         long oldValue = numOfBytes;
         EntryCount entryCount = databaseAccessor.getNumOfAll(xbStore);
         setEntryCount(entryCount);
         if (debug) {
            if (oldValue != numOfBytes && oldValue != -999L) {  // don't log if explicitly set the oldValue
               String txt = "getNumOfBytes: an inconsistency occured between the cached value and the real value of 'numOfPersistentBytes': it was '" + oldValue + "' but should have been '" + this.numOfBytes + "'";
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, txt + toXml(""));
            }
         }
         else if (log.isLoggable(Level.FINE)) 
            log.fine("getNumOfBytes_ old (cached) value: '" + oldValue + "' new (real) value: '" + numOfBytes + "'");
         return numOfBytes;
      }
   }


   /**
    * It returns the size of the queue. Note that this call will return the size
    * stored in cache.
    * In case this value is -1L (which means a previous attempt to read from the
    * DB failed) it will synchronize against the DB by making a call to the DB.
    * If that fails it will return -1L.
    *
    * @see I_Queue#getNumOfBytes()
    */
   public long getNumOfBytes() {
      try {
         return getNumOfBytes_();
      }
      catch (XmlBlasterException ex) {
         log.fine("getNumOfBytes, exception: " + ex.getMessage());
         return numOfBytes;
      }
   }


   /**
    * It returns the number of persistent entries in the queue.
    * In case this value is -1L (which means a previous attempt to read from the
    * DB failed) it will synchronize against the DB by making a call to the DB.
    * If that fails it will return -1L.
    *
    * @param verbose If true we throw an exception on errors, if false we ignore the error silently
    * @see I_Queue#getNumOfPersistentBytes()
    */
   private long getNumOfPersistentBytes_(boolean verbose) throws XmlBlasterException {
      if (numOfPersistentBytes > -1L && !debug) 
         return numOfPersistentBytes;
      synchronized (modificationMonitor) {
         try {
            long oldValue = numOfPersistentBytes;
            EntryCount entryCount = databaseAccessor.getNumOfAll(xbStore);
            setEntryCount(entryCount);
            if (debug) {
               if (oldValue != numOfPersistentBytes && oldValue != -999L) {  // don't log if explicitly set the oldValue
                  String txt = "getNumOfPersistentBytes: an inconsistency occured between the cached value and the real value of 'numOfPersistentBytes': it was '" + oldValue + "' but should have been '" + numOfPersistentBytes + "'";
                  throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, txt + toXml(""));
               }
            }
            else if (log.isLoggable(Level.FINE)) 
               log.warning("getNumOfPersistentBytes_ old (cached) value: '" + oldValue + "' new (real) value: '" + numOfPersistentBytes + "'");
            return numOfPersistentBytes;
         }
         catch (XmlBlasterException ex) {
            if (verbose) { // If called from toXml() we need to suppress this exeption because we here call toXml() again
               throw ex; // probably verbose is not needed anymore ...
            }
            return -1L;
         }
      }
   }


   /**
    * It returns the number of persistent entries in the queue.
    * In case this value is -1L (which means a previous attempt to read from the
    * DB failed) it will synchronize against the DB by making a call to the DB.
    * If that fails it will return -1L.
    *
    * @see I_Queue#getNumOfPersistentBytes()
    */
   public long getNumOfPersistentBytes() {
      try {
         return getNumOfPersistentBytes_(true);
      }
      catch (XmlBlasterException ex) {
         log.severe("getNumOfPersistentBytes, exception: " + ex.getMessage());
         return numOfPersistentBytes;
      }
   }


   /**
    * @see I_Queue#getMaxNumOfBytes()
    */
   public long getMaxNumOfBytes() {
      return property.getMaxBytes();
   }


   /**
    * Updates the given message queue entry with a new value. Note that this
    * can be used if an entry with the unique id already exists.
    * ?? Does this really make sense here since we need to store history ??
    * ?? Should we define a switch which can deactivate storage of history ??
    */
   public int update(I_QueueEntry queueEntry) throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "update not implemented");
   }

   /**
    * Clears everything and removes the queue (i.e. frees the associated table)
    */
   public long clear() {
      try {
         final boolean delMeat = true;
         // long ret = queueFactory.deleteFirstEntries(xbStore, -1, -1L, delMeat);
         long ret = databaseAccessor.clearQueue(xbStore);
         numOfEntries = 0L;
         numOfBytes = 0L;
         numOfPersistentEntries = 0L;
         numOfPersistentBytes = 0L;
         storageSizeListenerHelper.invokeStorageSizeListener();
         return ret;
      }
      catch (XmlBlasterException ex) {
         log.severe("exception: " + ex.getMessage());
         return 0;
      }
   }

   /**
    * @see I_Queue#removeHead(I_QueueEntry)
    */
   public long removeHead(I_QueueEntry toEntry) throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "removeHead not implemented yet");
   }

   /**
    * Shutdown the implementation, sync with data store
    */
   public void shutdown() {
      synchronized (this) {
         if (log.isLoggable(Level.FINER)) 
            log.finer("shutdown '" + xbStore.toString() + "' (currently the value of 'isDown' is '" + isDown + "'");
         if (isDown) 
            return;
         isDown = true;
      }
      
      storageSizeListenerHelper.invokeStorageSizeListener();
      removeStorageSizeListener(null);
      
      try {
         if (getNumOfEntries() == 0) {
            databaseAccessor.deleteStore(xbStore.getId());
         }
      }
      catch (XmlBlasterException ex) {
         log.severe("An exception occured when trying to remove the storage " + xbStore.toString());
         ex.printStackTrace();
      }

      synchronized (this) {
         databaseAccessor.unregisterQueue(this); // Closes JDBC connection
      }
      
      glob.getQueuePluginManager().cleanup(this);

      glob.doStorageCleanup(this);
   }

   public boolean isShutdown() {
      return isDown;
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

      // NOTE: Recursion problems when using getNumOfEntries() instead of this.numOfEntries
      // if an exception is thrown in getNumOfEntries() which uses toXml to dump the problem ...

      sb.append(offset).append("<JdbcQueue id='").append(getStorageId().getId());
      sb.append("' type='").append(getType());
      sb.append("' version='").append(getVersion());
      sb.append("' numOfEntries='").append(this.numOfEntries);
      sb.append("' numOfBytes='").append(this.numOfBytes);
      sb.append("'>");
      if (this.property != null) {
         sb.append(this.property.toXml(extraOffset+Constants.INDENT));
      }
      else {
         sb.append(offset).append("<isDown>").append(this.isDown).append("</isDown>");
      
      }
      try {
         sb.append(offset).append(" <numOfPersistentsCached>").append(this.numOfPersistentEntries).append("</numOfPersistentsCached>");
         sb.append(offset).append(" <sizeOfPersistentsCached>").append(getNumOfPersistentBytes()).append("</sizeOfPersistentsCached>");

         sb.append(offset).append(" <numOfEntriesCached>").append(this.numOfEntries).append("</numOfEntriesCached>");
         sb.append(offset).append(" <numOfBytesCached>").append(getNumOfBytes()).append("</numOfBytesCached>");

         sb.append(offset).append(" <numOfEntries>").append(getNumOfEntries_()).append("</numOfEntries>");
         sb.append(offset).append(" <numOfBytes>").append(getNumOfBytes_()).append("</numOfBytes>");


         sb.append(offset).append(" <numOfPersistents>").append(getNumOfPersistentEntries_(false)).append("</numOfPersistents>");
         sb.append(offset).append(" <sizeOfPersistents>").append(getNumOfPersistentBytes_(false)).append("</sizeOfPersistents>");
      }
      catch (XmlBlasterException e) {
      }

      sb.append(offset).append("</JdbcQueue>");
      return sb.toString();
   }

   /**
    * Enforced by I_Plugin
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) {
      this.glob = glob;
      this.pluginInfo = pluginInfo;
      // Example to switch off via xmlBlaster.properties:
      // QueuePlugin[CACHE][1.0]=org.xmlBlaster.util.queue.cache.CacheQueueInterceptorPlugin,persistentQueue=JDBC,transientQueue=RAM,xmlBlaster/warnNewJdbcQueue=false
      if (this.pluginInfo.getParameters().getProperty("xmlBlaster/warnNewJdbcQueue", "true").equals("true")) {
         if (!isWarned) {
            isWarned = true;
            log.info("Be aware: You are using the new JdbcQueue database persistence layer with three tables 'xbstore', 'xbref', 'xbmeat'. " +
                  "If you have data in the old schema 'xb_entries' they are NOT used. In case you need the old data please read http://www.xmlblaster.org/xmlBlaster/doc/requirements/queue.jdbc.html");
         }
      }
   }

   /**
    * Enforced by I_Plugin
    * @return "JDBC"
    */
   public String getType() { 
      return "JDBC"; 
   }

   /**
    * Enforced by I_Plugin
    * @return "1.0"
    */
   public String getVersion() { 
      return "1.0"; 
   }

   /**
    * Enforced by I_StoragePlugin
    * @return the pluginInfo object.
    */
   public PluginInfo getInfo() { 
      return pluginInfo; 
   }

   /**
    * Cleans up the current queue (it deletes all the entries and frees the
    * table used and cleans up all tables in the database). This method should
    * never be called, only for testing purposes or to clean up the entire
    * database.
    */
   public void destroy() throws XmlBlasterException {
      databaseAccessor.cleanUp(xbStore);
      this.clear();
      this.shutdown();
      property = null;
   }


   /////////////////////////// I_Map implementation ///////////////////////
   public I_MapEntry get(final long uniqueId) throws XmlBlasterException {
      XBMeat[] meats = new XBMeat[] { new XBMeat(uniqueId) };
      List list = (ArrayList)databaseAccessor.getEntries(xbStore, null, meats);
      if (list.size() < 1) {
         return null;
      }
      return (I_MapEntry)list.get(0);
   }

   /**
    * @see I_Map#getAll()
    */
   public I_MapEntry[] getAll(I_EntryFilter entryFilter) throws XmlBlasterException {
      final boolean isRef = false;
      List list = databaseAccessor.getEntries(xbStore, -1, -1L, entryFilter, isRef, this);
      return (I_MapEntry[])list.toArray(new I_MapEntry[list.size()]);
   }

   /**
    * @see I_Map#put(I_MapEntry)
    */
   public int put(I_MapEntry mapEntry) throws XmlBlasterException {
      if (mapEntry == null) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "put(I_MapEntry="+mapEntry+")");
      }
      synchronized (modificationMonitor) {
         if (put((I_Entry)mapEntry))
            return 1;

         return 0;
      }
   }

   public int remove(final I_MapEntry mapEntry) throws XmlBlasterException {
      int num = removeRandom(mapEntry);
      synchronized (modificationMonitor) {
      }
      return num;
   }

   /**
    * This method is always invoked with the I_Map interface
    */
   public int remove(final long uniqueId) throws XmlBlasterException {
      int num = removeRandom(uniqueId);
      synchronized (modificationMonitor) {
      }
      return num;
   }

   /**
    * @see I_Map#removeOldest()
    */
   public I_MapEntry removeOldest() throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "removeOldest is not implemented");
   }

   /**
    * @see org.xmlBlaster.util.queue.I_StorageProblemNotifier#registerStorageProblemListener(I_StorageProblemListener)
    */
   public boolean registerStorageProblemListener(I_StorageProblemListener listener) {
      if (databaseAccessor == null) 
         return false;
      return databaseAccessor.registerStorageProblemListener(listener);
   }


   /**
    * @see org.xmlBlaster.util.queue.I_StorageProblemNotifier#unRegisterStorageProblemListener(I_StorageProblemListener)
    */
   public boolean unRegisterStorageProblemListener(I_StorageProblemListener listener) {
      if (databaseAccessor == null || listener == null) 
         return false;
      return databaseAccessor.unRegisterStorageProblemListener(listener);
   }


   /**
    * @see I_Map#change(I_MapEntry, I_ChangeCallback)
    */
   public I_MapEntry change(I_MapEntry entry, I_ChangeCallback callback) throws XmlBlasterException {
      synchronized(this) { // is this the correct synchronization ??
         entry.getSizeInBytes(); // must be here since newEntry could reference same obj.
         I_MapEntry newEntry = entry;
         if (callback != null) 
            newEntry = callback.changeEntry(entry);
         if (newEntry == null) 
            return entry;
         if (newEntry.isPersistent() != entry.isPersistent()) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".change",
                  "changing  oldEntry.isPersistent=" + entry.isPersistent() + " to newEntry.isPersistent=" + newEntry.isPersistent() + "differs. This is not allowed");
         } 

         final boolean onlyRefCounter = false; // TODO Check if this can be optimized if only used for reference counting
         
         XBMeat newMeat = newEntry.getMeat();
         XBMeat oldMeat = entry.getMeat();
         long diffSize = databaseAccessor.modifyEntry(xbStore, newMeat, oldMeat, onlyRefCounter);
         this.numOfBytes += diffSize;
         if (entry.isPersistent())
            this.numOfPersistentBytes += diffSize;
         return newEntry;
      }
   }


   /**
    * @see I_Map#change(I_MapEntry, I_ChangeCallback)
    */
   public void updateCounters(I_MapEntry entry) throws XmlBlasterException {
      if (entry == null)
         return;
      synchronized(this) { // is this the correct synchronization ??
         entry.getSizeInBytes(); // must be here since newEntry could reference same obj.
         final boolean onlyRefCounter = true;
         XBMeat newMeat = entry.getMeat();
         // long diffSize = databaseAccessor.modifyEntry(xbStore, newMeat, null, onlyRefCounter);
         long diffSize = databaseAccessor.modifyEntry(xbStore, newMeat, newMeat, onlyRefCounter);
         this.numOfBytes += diffSize;
         if (entry.isPersistent())
            this.numOfPersistentBytes += diffSize;
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
    * @see I_Queue#addStorageSizeListener(I_StorageSizeListener)
    */
   public void addStorageSizeListener(I_StorageSizeListener listener) {
      this.storageSizeListenerHelper.addStorageSizeListener(listener);
   }
   
   /**
    * @see I_Queue#removeStorageSizeListener(I_StorageSizeListener)
    */
   public void removeStorageSizeListener(I_StorageSizeListener listener) {
      this.storageSizeListenerHelper.removeStorageSizeListener(listener);
   }
   
   /**
    * @see I_Queue#hasStorageSizeListener(I_StorageSizeListener)
    */
   public boolean hasStorageSizeListener(I_StorageSizeListener listener) {
      return this.storageSizeListenerHelper.hasStorageSizeListener(listener);
   }

   /**
    * @see I_Storage#getStorageSizeListeners()
    */
   public I_StorageSizeListener[] getStorageSizeListeners() {
      return storageSizeListenerHelper.getStorageSizeListeners();
   }

   public long embeddedQueueObjectsToXml(OutputStream out, Properties props) throws Exception {
      return embeddedObjectsToXml(out, props); // Hack, use Map implementation as accessor (which looses priority info)
   }
   
   /**
    * Note: Is currently implemented assuming I_MapEntry and NOT I_QueueEntry (but I_Queue offers this method which is wrong)
    * @see I_Queue#embeddedObjectsToXml(OutputStream, Properties)
    * @see I_Map#embeddedObjectsToXml(OutputStream, Properties)
    */
   public long embeddedObjectsToXml(final OutputStream out, final Properties props) throws Exception {
      if (out == null) return 0;
      entryCounter = 0;
      /*I_Entry[] results = */getAll(new I_EntryFilter() {
         public I_Entry intercept(I_Entry entry, I_Storage storage) {
            entryCounter++;
            try {
               entry.embeddedObjectToXml(out, props);
            }
            catch (IOException e) {
               log.warning("Ignoring dumpToFile() problem: "+e.toString());
            }
            return null;
         }
      });
      
      return entryCounter;
   }
}

