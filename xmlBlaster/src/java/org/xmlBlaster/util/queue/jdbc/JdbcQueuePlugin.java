/*------------------------------------------------------------------------------                              
Name:      JdbcQueuePlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue.jdbc;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_QueuePutListener;
import org.xmlBlaster.util.queue.ReturnDataHolder;
import org.xmlBlaster.util.queue.QueuePluginManager;
// import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.queue.I_StoragePlugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.util.queue.I_StorageProblemNotifier;
import org.xmlBlaster.util.queue.I_StorageProblemListener;

import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.Properties;


/**
 * Persistence queue implementation on a DB based on JDBC.
 * @author laghi@swissinfo.org
 * @author xmlBlaster@marcelruff.info
 */
public final class JdbcQueuePlugin implements I_Queue, I_StoragePlugin, I_Map
{
   private String ME;
   private StorageId storageId;
   private boolean notifiedAboutAddOrRemove = false;
   private String associatedTable = null;
   private Global glob;
   private LogChannel log;
   private QueuePropertyBase property;
   private JdbcManager manager = null;
   private I_QueuePutListener putListener;
   private long numOfEntries = -1L;
   private long numOfPersistentEntries = -1L;
   private long numOfPersistentBytes = -1L;
   boolean isDown = true;
   private long numOfBytes = -1L;

   /** Monitor object used to synchronize the count of sizes */
   private Object modificationMonitor = new Object();
   private PluginInfo pluginInfo = null;

   /**
    * Check is storage is big enough for entry
    * @param mapEntry may not be null
    * @return null There is space (otherwise the error text is returned)
    */
   private String spaceLeft(long numOfEntries, long sizeInBytes) {
      
      // allow one owerload only ...
      numOfEntries = 0L;
      sizeInBytes  = 0L;

      if (this.property == null) {
         return "Storage framework is down, current settings are" + toXml("");
      }

      if ((numOfEntries + getNumOfEntries()) > getMaxNumOfEntries())
         return "Queue overflow (number of entries), " + getNumOfEntries() +
                " entries are in queue, try increasing property '" +
                this.property.getPropName("maxMsg") + "' and '" +
                this.property.getPropName("maxMsgCache") + "', current settings are" + toXml("");

      if ((sizeInBytes + getNumOfBytes()) > getMaxNumOfBytes())
         return "Queue overflow, " + getMaxNumOfBytes() +
                " bytes are in queue, try increasing property '" + 
                this.property.getPropName("maxBytes") + "' and '" +
                this.property.getPropName("maxBytesCache") + "', current settings are" + toXml("");
      return null;
   }

   /**
    * Calculates the size in bytes of all entries in the array.
    */
/*
   private long calculateSizeInBytes(I_QueueEntry[] entries) {
      long sum = 0L;
      for (int i=0; i<entries.length; i++) {
         sum += entries[i].getSizeInBytes();
      }
      return sum;
   }
*/

   /**
    * Is called after the instance is created.
    * @param uniqueQueueId A unique name, allowing to create a unique name for a persistent store (e.g. file name)
    * @see I_Queue#initialize(StorageId, Object)
    */
   public void initialize(StorageId uniqueQueueId, Object userData)
      throws XmlBlasterException
   {
      if (this.isDown) {
         try {
            this.property = null;
            setProperties(userData);
            this.glob = this.property.getGlobal();
            this.log = this.glob.getLog("jdbc");
            this.ME = this.getClass().getName() + "-" + uniqueQueueId;
            this.storageId = uniqueQueueId;

//            this.manager = this.glob.getJdbcQueueManager(this.storageId);
            if (this.pluginInfo != null) {
               this.manager = this.glob.getJdbcQueueManager(this.pluginInfo);
            }
            else {
               log.warn(ME, "initialize. The pluginInfo is null (init has not been invoked. This is allowed when testing but be aware that settings will follow 'JDBC,1.0' configuration!!");
               QueuePluginManager pluginManager = new QueuePluginManager(this.glob);
               PluginInfo tmpPluginInfo = new PluginInfo(this.glob, pluginManager, "JDBC", "1.0");
               this.manager = this.glob.getJdbcQueueManager(tmpPluginInfo);
            }
            this.manager.setUp();

            this.associatedTable = this.manager.getTable(this.storageId.getStrippedId(), getMaxNumOfEntries());
            this.numOfEntries = this.manager.getNumOfEntries(this.associatedTable);
            this.numOfBytes = this.manager.getNumOfBytes(this.associatedTable);
            this.numOfPersistentEntries = this.manager.getNumOfPersistents(this.associatedTable);
            this.numOfPersistentBytes = this.manager.getSizeOfPersistents(this.associatedTable);

            this.isDown = false;
            if (log.TRACE) log.trace(ME, "Successful initialized");
         }
         catch (SQLException ex) {
            ex.printStackTrace();
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "sql exception in initialize(" + uniqueQueueId + ")", ex);
         }
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
         log.error(ME, "Can't configure queue, your properties are invalid: " + e.toString());
         return;
      }

      // sync necessary?

      /* Protect against shrinking ??
      if (this.property != null && this.property.getMaxMsg() > newProp.getMaxMsg()) {
         log.warn(ME, "Reconfigure of a RamQueuePlugin - getMaxNumOfEntries from " + this.property.getMaxMsg() +
                    " to " + newProp.getMaxMsg() + " is not supported, we ignore the new setting.");
         return;
      }
      */

      this.property = newProp;
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
      try {
         return this.manager.getEntries(-1, -1L, this.associatedTable, getStorageId());
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "getEntries() caught sql exception, status is" + toXml(""), ex);
      }
   }

   /**
    * @see I_Queue#put(I_QueueEntry, boolean)
    */
   public void put(I_QueueEntry queueEntry, boolean ignorePutInterceptor)
      throws XmlBlasterException {
      if (queueEntry == null) return;

      if ((this.putListener != null) && (!ignorePutInterceptor)) {
         // Is an interceptor registered (and not bypassed) ?
         if (this.putListener.putPre(queueEntry) == false)
            return;
      }
      put((I_Entry)queueEntry);

      if (this.putListener != null && !ignorePutInterceptor) {
         this.putListener.putPost(queueEntry);
      }
   }

   /**
    * Internally used for I_MapEntry and I_QueueEntry
    * @return true on success
    */
   private boolean put(I_Entry entry) throws XmlBlasterException
   {
/*
      if (getNumOfEntries_() > getMaxNumOfEntries()) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, "put: the maximum number of entries reached." +
                   " Number of entries=" + getNumOfEntries_() + ", maxmimum number of entries=" + getMaxNumOfEntries() + " status: " + this.toXml(""));
      }
*/
      String exTxt = null;
      if ((exTxt=spaceLeft(1, entry.getSizeInBytes())) != null)
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, exTxt);

      synchronized (this.modificationMonitor) {
         if (getNumOfBytes_() > getMaxNumOfBytes()) {
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_BYTES, ME, "put: the maximum number of bytes reached." +
                   " Number of bytes=" + this.numOfBytes + " maxmimum number of bytes=" + getMaxNumOfBytes() + " status: " + this.toXml(""));
         }
         try {
            if (this.manager.addEntry(this.associatedTable, entry)) {
               this.numOfEntries++;
               this.numOfBytes += entry.getSizeInBytes();
               if (entry.isPersistent()) {
                  this.numOfPersistentEntries++;
                  this.numOfPersistentBytes += entry.getSizeInBytes();
               }
               return true;
            }
         }
         catch (SQLException ex) {
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "put(entry) caught sql exception, status is" + toXml(""), ex);
         }
      }
      return false;
   }

   /**
    * @see I_Queue#put(I_QueueEntry[], boolean)
    */
   public void put(I_QueueEntry[] queueEntries, boolean ignorePutInterceptor)
      throws XmlBlasterException {
      SQLException ex0 =  null;

      if (queueEntries == null) return;

      if ((this.putListener != null) &&(!ignorePutInterceptor)) {
         // Is an interceptor registered (and not bypassed) ?
         if (this.putListener.putPre(queueEntries) == false)
            return;
      }

/*
      if (getNumOfEntries_() > getMaxNumOfEntries()) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, "put[]: the maximum number of entries reached." +
                   " Number of entries=" + this.numOfEntries + " maxmimum number of entries=" + getMaxNumOfEntries() + " status: " + this.toXml(""));
      }
*/
      String exTxt = null;
      if ((exTxt=spaceLeft(queueEntries.length, /*calculateSizeInBytes(queueEntries)*/ 0L)) != null)
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, exTxt);

      synchronized (this.modificationMonitor) {
         if (getNumOfBytes_() > getMaxNumOfBytes()) {
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_BYTES, ME, "put[]: the maximum number of bytes reached." +
                  " Number of bytes=" + this.numOfBytes + " maxmimum number of bytes=" + getMaxNumOfBytes() + " status: " + this.toXml(""));
         }

         try {
            for (int i=0; i < queueEntries.length; i++) {
               boolean isProcessed = this.manager.addEntry(this.associatedTable, queueEntries[i]);
               if (isProcessed) {
                  this.numOfEntries++;
                  this.numOfBytes += queueEntries[i].getSizeInBytes();
                  if (queueEntries[i].isPersistent()) {
                     this.numOfPersistentEntries++;
                     this.numOfPersistentBytes += queueEntries[i].getSizeInBytes();
                  }
               }
            }
         }
         catch (SQLException ex) {
            this.log.error(ME, "put: sql exception: " + ex.getMessage() + " state: " + toXml(""));
            ex0 = ex;
         }
         if (ex0 != null)
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "put(entry[]) caught sql exception, status is" + toXml(""), ex0);
      }

      if (this.putListener != null && !ignorePutInterceptor) {
         this.putListener.putPost(queueEntries);
      }
   }


   /**
    * Returns the unique ID of this queue
    */
   public StorageId getStorageId() {
      return this.storageId;
   }

   /**
    * @see I_Queue#take()
    */
   public I_QueueEntry take() throws XmlBlasterException
   {
      // note that this method could be drastically improved
      // however it is unlikely to be used so I avoid that tuning now
      I_QueueEntry ret = this.peek();
      this.removeRandom(ret.getUniqueId());
      return ret;
   }

   /**
    * @see I_Queue#takeWithPriority(int,long,int,int)
    */
   public ArrayList takeWithPriority(int numOfEntries, long numOfBytes, int minPriority, int maxPriority)
      throws XmlBlasterException {
      if (numOfEntries == 0) return new ArrayList();
      ArrayList ret = this.peekWithPriority(numOfEntries, numOfBytes, minPriority, maxPriority);
      this.removeRandom( (I_QueueEntry[])ret.toArray(new I_QueueEntry[ret.size()]) );
      return ret;
   }


   /**
    * @see I_Queue#take(int, long)
    */
   public ArrayList take(int numOfEntries, long numOfBytes) throws XmlBlasterException {
      if (numOfEntries == 0) return new ArrayList();
      try {
         synchronized(this.modificationMonitor) {
            ArrayList ret = this.manager.getEntries(numOfEntries, numOfBytes, this.associatedTable, getStorageId());

            long ids[] = new long[ret.size()];
            for (int i=0; i < ids.length; i++)
               ids[i] = ((I_QueueEntry)ret.get(i)).getUniqueId();

            this.numOfEntries -= this.manager.deleteEntries(this.associatedTable, ids);

            this.numOfPersistentBytes = -1L;
            getNumOfPersistentBytes_(true);
            this.numOfPersistentEntries = -1L;
            getNumOfPersistentEntries_(true);

            // since this method should never be called, we choose the easiest way to
            // find out the sizeInBytes, that is by invoking the dB.
            this.numOfBytes = -1L;
            getNumOfBytes_();
            return ret;
         }
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "take(int,long) caught sql exception, status is" + toXml(""), ex);
      }
   }


   /**
    * @see I_Queue#takeLowest(int, long, I_QueueEntry, boolean)
    */
   public ArrayList takeLowest(int numOfEntries, long numOfBytes, I_QueueEntry limitEntry, boolean leaveOne)
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

      try {
         synchronized(this.modificationMonitor) {
            ReturnDataHolder ret = this.manager.getAndDeleteLowest(this.associatedTable, getStorageId(), numOfEntries, numOfBytes, maxPriority, minUniqueId, leaveOne);
            this.numOfBytes -= ret.countBytes;
            this.numOfEntries -= ret.countEntries;

            this.numOfPersistentBytes = -1L;
            getNumOfPersistentBytes_(true);

            this.numOfPersistentEntries = -1L;
            getNumOfPersistentEntries_(true);
            return ret.list;
         }
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "takeLowest() caught sql exception, status is" + toXml(""), ex);
      }
   }


   /**
    * @see I_Queue#peek()
    */
   public I_QueueEntry peek() throws XmlBlasterException
   {
      try {
         ArrayList ret = this.manager.getEntries(1, -1L, this.associatedTable, getStorageId());
         if (ret.size() < 1) return null;
         return (I_QueueEntry)ret.get(0);
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "peek() caught sql exception, status is" + toXml(""), ex);
      }
   }


   /**
    * @see I_Queue#peek(int,long)
    */
   public ArrayList peek(int numOfEntries, long numOfBytes) throws XmlBlasterException {
      if (numOfEntries == 0) return new ArrayList();
      try {
         return this.manager.getEntries(numOfEntries, numOfBytes, this.associatedTable, getStorageId());
      }
      catch (SQLException ex)  {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "peek(int,long) caught sql exception, status is" + toXml(""), ex);
      }
   }

   /**
    * @see I_Queue#peekSamePriority(int, long)
    */
   public ArrayList peekSamePriority(int numOfEntries, long numOfBytes) throws XmlBlasterException {
      if (numOfEntries == 0) return new ArrayList();
      try {
         ArrayList ret = this.manager.getEntriesBySamePriority(numOfEntries, numOfBytes, this.associatedTable, getStorageId());
         return ret;
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "peekSamePriority(int,long) caught sql exception, status is" + toXml(""), ex);
      }
   }

   /**
    * @see I_Queue#peekWithPriority(int, long, int, int)
    */
   public ArrayList peekWithPriority(int numOfEntries, long numOfBytes, int minPriority, int maxPriority) throws XmlBlasterException {
      if (numOfEntries == 0) return new ArrayList();
      try {
         return this.manager.getEntriesByPriority(numOfEntries, numOfBytes, minPriority, maxPriority, this.associatedTable, getStorageId());
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "peekWithPriority() caught sql exception, status is" + toXml(""), ex);
      }
   }


   /**
    * @see I_Queue#peekWithLimitEntry(I_QueueEntry)
    */
   public ArrayList peekWithLimitEntry(I_QueueEntry limitEntry) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "peekWithLimitEntry called");
      if (limitEntry == null) return new ArrayList();
      try {
         return this.manager.getEntriesWithLimit(this.associatedTable, getStorageId(), limitEntry);
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "peekWithLimitEntry() caught sql exception, status is" + toXml(""), ex);
      }
   }



   /**
    * Removes the first element in the queue
    * This method does not block.
    * @return Number of messages erased (0 or 1)
    * @throws XmlBlasterException if the underlying implementation gets an exception.
    */
   public int remove() throws XmlBlasterException {
      try {

         synchronized(this.modificationMonitor) {
            ReturnDataHolder ret = this.manager.deleteFirstEntries(this.associatedTable, 1, -1L);
            this.numOfEntries -= (int)ret.countEntries;
            this.numOfBytes -= ret.countBytes;

            this.numOfPersistentBytes = -1L;
            getNumOfPersistentBytes_(true);
            this.numOfPersistentEntries = -1L;
            getNumOfPersistentEntries_(true);

            return (int)ret.countEntries;
         }
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "remove() caught sql exception, status is" + toXml(""), ex);
      }
   }


   /**
    * Removes max num messages.
    * This method does not block.
    * @param num Erase num entries or less if less entries are available, -1 erases everything
    * @return Number of entries erased
    * @throws XmlBlasterException if the underlying implementation gets an exception.
    */
   public long remove(long numOfEntries, long numOfBytes) throws XmlBlasterException {
      if (numOfEntries == 0) return 0L;
      try {
         synchronized(this.modificationMonitor) {
            ReturnDataHolder ret = this.manager.deleteFirstEntries(this.associatedTable, numOfEntries, numOfBytes);
            this.numOfEntries -= (int)ret.countEntries;
            this.numOfBytes -= ret.countBytes;

            this.numOfPersistentBytes = -1L;
            getNumOfPersistentBytes_(true);
            this.numOfPersistentEntries = -1L;
            getNumOfPersistentEntries_(true);
            return ret.countEntries;
         }
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "remove(long,long) caught sql exception, status is" + toXml(""), ex);
      }
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
      return (int)removeRandom(args);
   }


   /**
    * Removes the given entries.
    * @param msgQueueEntry the entry to erase.
    */
   public long removeRandom(long[] dataIdArray) throws XmlBlasterException {
      try {
         ArrayList list = this.manager.getEntries(dataIdArray, this.associatedTable, this.getStorageId());
         return removeRandom((I_Entry[])list.toArray(new I_Entry[list.size()]));
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "removeRandom(long[]) caught sql exception, status is" + toXml(""), ex);
      }
   }


   /**
    * @see I_Queue#removeRandom(I_Entry)
    */
   public int removeRandom(I_Entry entry) throws XmlBlasterException {
      if (entry == null) return 0;
      I_Entry[] arr = new I_Entry[1];
      arr[0] = entry;
      return (int)removeRandom(arr);
   }

   /**
    * @see I_Queue#removeRandom(I_Entry[])
    */
   public long removeRandom(I_Entry[] queueEntries) throws XmlBlasterException {
      if (queueEntries == null) return 0;
      try {
         long[] ids = new long[queueEntries.length];

         long currentAmount = 0L;
         long currentPersistentSize = 0L;
         long currentPersistentEntries = 0L;
         for (int i=0; i < ids.length; i++) {
            ids[i] = queueEntries[i].getUniqueId();
            currentAmount += queueEntries[i].getSizeInBytes();
            if (queueEntries[i].isPersistent()) {
               currentPersistentSize += queueEntries[i].getSizeInBytes();
               currentPersistentEntries++;
            }
         }

         synchronized(this.modificationMonitor) {

            long ret = this.manager.deleteEntries(this.associatedTable, ids);
            this.numOfEntries -= ret;

            if ((int)ret != queueEntries.length) { // then we need to retrieve the values
               this.numOfPersistentBytes = -1L;
               getNumOfPersistentEntries_(true);
               this.numOfPersistentEntries = -1L;
               getNumOfPersistentEntries_(true);
               this.numOfBytes = -1L;
               getNumOfBytes_();
            }
            else {
               this.numOfBytes -= currentAmount;
               this.numOfPersistentBytes -= currentPersistentSize;
               this.numOfPersistentEntries -= currentPersistentEntries;
            }
            return ret;
         }
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "removeRandom(entry[]) caught sql exception, status is" + toXml(""), ex);
      }

   }


   /**
    * @see I_Queue#removeWithPriority(long, long, int, int)
    */
   public long removeWithPriority(long numOfEntries, long numOfBytes, int minPriority, int maxPriority)
      throws XmlBlasterException {
      ArrayList array = this.peekWithPriority((int)numOfEntries, numOfBytes, minPriority, maxPriority);
      return removeRandom((I_QueueEntry[])array.toArray(new I_QueueEntry[array.size()]));
   }

   /**
    * @see I_Queue#removeTransient()
    */
   public int removeTransient() throws XmlBlasterException {

      try {
         synchronized(this.modificationMonitor) {
            int ret = this.manager.deleteAllTransient(this.associatedTable);
            this.numOfEntries -= ret;
            // not so performant but only called on init
            this.numOfBytes = -1L;
            getNumOfBytes_();
            return ret;
         }
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "removeTransient() caught sql exception, status is" + toXml(""), ex);
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
    * @exception XmlBlasterException if number is not retrievable
    */
   private long getNumOfEntries_() throws XmlBlasterException {
      if (this.numOfEntries > -1L) return this.numOfEntries;
      synchronized (this.modificationMonitor) {
         try {
            this.numOfEntries = this.manager.getNumOfEntries(this.associatedTable);
            return this.numOfEntries;
         }
         catch (SQLException ex) {
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "getNumOfEntries_() caught sql exception, status is" + toXml(""), ex);
         }
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
         this.log.error(ME, "getNumOfEntries, exception: " + ex.getMessage());
         return this.numOfEntries;
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
      if (this.numOfPersistentEntries > -1L) return this.numOfPersistentEntries;
      synchronized (this.modificationMonitor) {
         try {
            this.numOfPersistentEntries = this.manager.getNumOfPersistents(this.associatedTable);
            return this.numOfPersistentEntries;
         }
         catch (SQLException ex) {
            if (verbose) { // If called from toXml() we need to suppress this exeption because we here call toXml() again
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "getNumOfPersistentEntries_() caught sql exception, status is" + toXml(""), ex);
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
         this.log.error(ME, "getNumOfEntries, exception: " + ex.getMessage());
         return this.numOfPersistentEntries;
      }
   }


   /**
    * @see I_Queue#getMaxNumOfEntries()
    */
   public long getMaxNumOfEntries() {
      return this.property.getMaxMsg();
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
      if (this.numOfBytes > -1L) return this.numOfBytes;
      synchronized (this.modificationMonitor) {
         try {
            this.numOfBytes = this.manager.getNumOfBytes(this.associatedTable);
            return this.numOfBytes;
         }
         catch (SQLException ex) {
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "getNumOfBytes_() caught sql exception, status is" + toXml(""), ex);
         }
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
         this.log.error(ME, "getNumOfBytes, exception: " + ex.getMessage());
         return this.numOfBytes;
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
      if (this.numOfPersistentBytes > -1L) return this.numOfPersistentBytes;
      synchronized (this.modificationMonitor) {
         try {
            this.numOfPersistentBytes = this.manager.getSizeOfPersistents(this.associatedTable);
            return this.numOfPersistentBytes;
         }
         catch (SQLException ex) {
            if (verbose) { // If called from toXml() we need to suppress this exeption because we here call toXml() again
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "getNumOfPersistentBytes_() caught sql exception, status is" + toXml(""), ex);
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
         this.log.error(ME, "getNumOfBytes, exception: " + ex.getMessage());
         return this.numOfPersistentBytes;
      }
   }


   /**
    * @see I_Queue#getMaxNumOfBytes()
    */
   public long getMaxNumOfBytes() {
      return this.property.getMaxBytes();
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

   public JdbcManager getManager() {
      return this.manager;
   }


   /**
    * Clears everything and removes the queue (i.e. frees the associated table)
    */
   public long clear() {
      try {
         ReturnDataHolder ret = this.manager.deleteFirstEntries(this.associatedTable, -1, -1L);
         this.numOfEntries = 0L;
         this.numOfBytes = 0L;
         this.numOfPersistentEntries = 0L;
         this.numOfPersistentBytes = 0L;
         return ret.countEntries;
      }
      catch (SQLException ex)
      {
         this.log.error(ME, "sql exception: " + ex.getMessage());
         return 0;
      }
      catch (XmlBlasterException ex)
      {
         this.log.error(ME, "exception: " + ex.getMessage());
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
      this.isDown = true;
      //      clear();
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

      // NOTE: Recursion problems when using getNumOfEntries() instead of this.numOfEntries
      // if an exception is thrown in getNumOfEntries() which uses toXml to dump the problem ...

      sb.append(offset).append("<JdbcQueuePlugin id='").append(getStorageId().getId());
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
         sb.append(offset).append(" <numOfPersistents>").append(getNumOfPersistentEntries_(false)).append("</numOfPersistents>");
         sb.append(offset).append(" <sizeOfPersistents>").append(getNumOfPersistentBytes_(false)).append("</sizeOfPersistents>");
      }
      catch (XmlBlasterException e) {
      }
      sb.append(offset).append(" <associatedTable>").append(this.associatedTable).append("</associatedTable>");
      sb.append(offset).append("</JdbcQueuePlugin>");
      return sb.toString();
   }

   /**
    * Enforced by I_Plugin
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) {
      this.pluginInfo = pluginInfo;
   }

   /**
    * Enforced by I_Plugin
    * @return "JDBC"
    */
   public String getType() { return "JDBC"; }

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
    * Cleans up the current queue (it deletes all the entries and frees the
    * table used and cleans up all tables in the database). This method should
    * never be called, only for testing purposes or to clean up the entire
    * database.
    */
   public void destroy() throws XmlBlasterException {
      try {
         this.clear();
         this.shutdown();
         long ret = this.manager.releaseTable(this.storageId.getStrippedId(), this.associatedTable);
         this.property = null;
         this.manager.cleanUp(this.storageId.getStrippedId());
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "destroy() caught sql exception, status is" + toXml(""), ex);
      }
   }


   /////////////////////////// I_Map implementation ///////////////////////
   public I_MapEntry get(final long uniqueId) throws XmlBlasterException {
      try {
         long[] idArr = new long[] { uniqueId };
         ArrayList list = this.manager.getEntries(idArr, this.associatedTable, getStorageId());
         if (list.size() < 1) {
            return null;
         }
         return (I_MapEntry)list.get(0);
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME, "sql exception in get(" + uniqueId + ")", ex);
      }
   }

   /**
    * @see I_Map#getAll()
    */
   public I_MapEntry[] getAll() throws XmlBlasterException {
      ArrayList list = peek(-1, -1);
      return (I_MapEntry[])list.toArray(new I_MapEntry[list.size()]);
   }

   /**
    * @see I_Map#put(I_MapEntry)
    */
   public int put(I_MapEntry mapEntry) throws XmlBlasterException {
      if (mapEntry == null) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "put(I_MapEntry="+mapEntry+")");
      }
      synchronized (this.modificationMonitor) {
         if (put((I_Entry)mapEntry))
            return 1;

         return 0;
      }
   }

   public int remove(final I_MapEntry mapEntry) throws XmlBlasterException {
      int num = removeRandom(mapEntry);
      synchronized (this.modificationMonitor) {
      }
      return num;
   }


   /**
    * @see org.xmlBlaster.util.queue.I_StorageProblemNotifier#registerStorageProblemListener(I_StorageProblemListener)
    */
   public boolean registerStorageProblemListener(I_StorageProblemListener listener) {
      if (this.manager == null) return false;
      return this.manager.registerStorageProblemListener(listener);
   }


   /**
    * @see org.xmlBlaster.util.queue.I_StorageProblemNotifier#unRegisterStorageProblemListener(I_StorageProblemListener)
    */
   public boolean unRegisterStorageProblemListener(I_StorageProblemListener listener) {
      if (this.manager == null) return false;
      return this.manager.unRegisterStorageProblemListener(listener);
   }

}

