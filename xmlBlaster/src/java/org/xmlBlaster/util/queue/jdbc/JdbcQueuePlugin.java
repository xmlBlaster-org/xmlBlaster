/*------------------------------------------------------------------------------
Name:      JdbcQueuePlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue.jdbc;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queue.I_QueueEntryFactory;
import org.xmlBlaster.util.queue.I_QueuePutListener;
import org.xmlBlaster.util.queue.ReturnDataHolder;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.engine.helper.QueuePropertyBase;

import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;


/**
 * Persistence queue implementation on a DB based on JDBC.
 * @author laghi@swissinfo.org
 * @author ruff@swand.lake.de
 */
public final class JdbcQueuePlugin implements I_Queue, I_Plugin
{
   private String ME;
   private String queueId;
   private String associatedTable = null;
   private LogChannel log;
   private QueuePropertyBase property;
   private JdbcManager manager = null;
   private I_QueuePutListener putListener;
   private long numOfEntries = -1L;
   private long numOfDurableEntries = -1L;
   private long numOfDurableBytes = -1L;
   boolean isDown = true;
   private long numOfBytes = -1L;
   private String strippedQueueId = null;

   /** Monitor object used to synchronize the count of sizes */
   private Object modificationMonitor = new Object();

   /**
    * Is called after the instance is created.
    * @param uniqueQueueId A unique name, allowing to create a unique name for a persistent store (e.g. file name)
    * @see I_Queue#initialize(Global, String, Object)
    */
   public void initialize(String uniqueQueueId, Object userData)
      throws XmlBlasterException
   {
      if (this.isDown) {
         try {
            this.property = null;
            setProperties(userData);
            Global glob = this.property.getGlobal();
            this.log = glob.getLog("queue");
            this.ME = this.getClass().getName() + "-" + uniqueQueueId;
            this.queueId = uniqueQueueId;
            this.strippedQueueId = Global.getStrippedString(this.queueId);

            this.manager = glob.getJdbcQueueManager(this.queueId);
            this.manager.setUp();

            this.associatedTable = this.manager.getTable(this.strippedQueueId, getMaxNumOfEntries());
            this.numOfEntries = this.manager.getNumOfEntries(this.associatedTable);
            this.numOfBytes = this.manager.getNumOfBytes(this.associatedTable);

            this.isDown = false;
            log.info(ME, "Successful initialized");
            this.numOfDurableBytes = 0L;
            this.numOfDurableEntries = 0L;
         }
         catch (SQLException ex) {
            ex.printStackTrace();
            throw new XmlBlasterException(ME, "initialize, SQLException: " + ex.getMessage());
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

      if (this.property != null && this.property.getMaxMsg() != newProp.getMaxMsg()) {
         log.warn(ME, "Reconfigure of a RamQueuePlugin - getMaxNumOfEntries from " + this.property.getMaxMsg() +
                    " to " + newProp.getMaxMsg() + " is not supported, we ignore the new setting.");
         return;
      }

      this.property = newProp;
   }

   /**
    * Access the current queue configuration
    */
   public Object getProperties() {
      return this.property;
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
      throw new XmlBlasterException(ME, "getEntryReferences not implemented");
   }

   /**
    * @see I_Queue#getEntries()
    */
   public ArrayList getEntries() throws XmlBlasterException {
      try {
         return this.manager.getEntries(-1, -1L, this.associatedTable, this);
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(ME, "sql exception: " + ex.getMessage());
      }
   }

   /**
    * @see I_Queue#put(I_QueueEntry)
    */
   public Object put(I_QueueEntry queueEntry, boolean ignorePutInterceptor)
      throws XmlBlasterException
   {
      if (queueEntry == null) return null;

      if ((this.putListener != null) && (!ignorePutInterceptor)) {
         // Is an interceptor registered (and not bypassed) ?
         return this.putListener.put(queueEntry);
      }

      if (getNumOfEntries_() >= getMaxNumOfEntries()) {
         throw new XmlBlasterException(ME, "put: the maximum number of entries reached. Number of entries: " + this.numOfEntries + " maxmimum number of entries: " + getMaxNumOfEntries() + " status: " + this.toXml(""));
      }

      synchronized (this.modificationMonitor) {
         if (getNumOfBytes_() >= getMaxNumOfBytes()) {
            throw new XmlBlasterException(ME, "put: the maximum number of bytes reached. Number of bytes: " + this.numOfBytes + " maxmimum number of bytes: " + getMaxNumOfBytes() + " status: " + this.toXml(""));
         }
         try {
            if (this.manager.addEntry(this.associatedTable, queueEntry)) {
               this.numOfEntries++;
               this.numOfBytes += queueEntry.getSizeInBytes();
               if (queueEntry.isDurable()) {
                  this.numOfDurableEntries++;
                  this.numOfDurableBytes += queueEntry.getSizeInBytes();
               }
            }
         }
         catch (SQLException ex) {
            throw new XmlBlasterException(ME, "put: sql exception: " + ex.getMessage() + " state " + toXml(""));
         }
         return null;
      }
   }


   /**
    * @see I_Queue#put(I_QueueEntry[])
    */
   public Object[] put(I_QueueEntry[] queueEntries, boolean ignorePutInterceptor)
      throws XmlBlasterException {
      SQLException ex0 =  null;

      if (queueEntries == null) return null;

      if ((this.putListener != null) &&(!ignorePutInterceptor)) {
         // Is an interceptor registered (and not bypassed) ?
         return this.putListener.put(queueEntries);
      }

      if (getNumOfEntries_() >= getMaxNumOfEntries()) {
         throw new XmlBlasterException(ME, "put: the maximum number of entries reached. Number of entries: " + this.numOfEntries + " maxmimum number of entries: " + getMaxNumOfEntries() + " status: " + this.toXml(""));
      }

      synchronized (this.modificationMonitor) {
         if (getNumOfBytes_() >= getMaxNumOfBytes()) {
            throw new XmlBlasterException(ME, "put: the maximum number of bytes reached. Number of bytes: " + this.numOfBytes + " maxmimum number of bytes: " + getMaxNumOfBytes() + " status: " + this.toXml(""));
         }

         try {
            for (int i=0; i < queueEntries.length; i++) {
               boolean isProcessed = this.manager.addEntry(this.associatedTable, queueEntries[i]);
               if (isProcessed) {
                  this.numOfEntries++;
                  this.numOfBytes += queueEntries[i].getSizeInBytes();
                  if (queueEntries[i].isDurable()) {
                     this.numOfDurableEntries++;
                     this.numOfDurableBytes += queueEntries[i].getSizeInBytes();
                  }
               }
            }
         }
         catch (SQLException ex) {
            this.log.error(ME, "put: sql exception: " + ex.getMessage() + " state: " + toXml(""));
            ex0 = ex;
         }
         if (ex0 != null)
            throw new XmlBlasterException(ME, "put: sql exception: " + ex0.getMessage() + " state: " + toXml(""));

         return null;
      }
   }


   /**
    * Returns the unique ID of this queue
    */
   public String getQueueId()
   {
      return this.queueId;
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
            ArrayList ret = this.manager.getEntries(numOfEntries, numOfBytes, this.associatedTable, this);

            long ids[] = new long[ret.size()];
            for (int i=0; i < ids.length; i++)
               ids[i] = ((I_QueueEntry)ret.get(i)).getUniqueId();

            this.numOfEntries -= this.manager.deleteEntries(this.associatedTable, ids);

            this.numOfDurableBytes = -1L;
            getNumOfDurableBytes_();
            this.numOfDurableEntries = -1L;
            getNumOfDurableEntries_();

            // since this method should never be called, we choose the easiest way to
            // find out the sizeInBytes, that is by invoking the dB.
            this.numOfBytes = -1L;
            getNumOfBytes_();
            return ret;
         }
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(ME, "sql exception: " + ex.getMessage());
      }
   }


   /**
    * @see I_Queue#takeLowest(int, long, I_QueueEntry)
    */
   public ArrayList takeLowest(int numOfEntries, long numOfBytes, I_QueueEntry limitEntry)
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
            ReturnDataHolder ret = this.manager.getAndDeleteLowest(this.associatedTable, this, numOfEntries, numOfBytes, maxPriority, minUniqueId);
            this.numOfBytes -= ret.countBytes;
            this.numOfEntries -= ret.countEntries;

            this.numOfDurableBytes = -1L;
            getNumOfDurableBytes_();

            this.numOfDurableEntries = -1L;
            getNumOfDurableEntries_();
            return ret.list;
         }
      }
      catch (SQLException ex) {
         this.log.dump(ME, "takeLowest: SQL exception: " + ex.getMessage());
         throw new XmlBlasterException(ME, "takeLowest: SQL exception: " + ex.getMessage());
      }
   }


   /**
    * @see I_Queue#peek()
    */
   public I_QueueEntry peek() throws XmlBlasterException
   {
      try {
         ArrayList ret = this.manager.getEntries(1, -1L, this.associatedTable, this);
         if (ret.size() < 1) return null;
         return (I_QueueEntry)ret.get(0);
      }
      catch (SQLException ex)
      {
         throw new XmlBlasterException(ME, "sql exception: " + ex.getMessage());
      }
   }


   /**
    * @see I_Queue#peek(int,long)
    */
   public ArrayList peek(int numOfEntries, long numOfBytes) throws XmlBlasterException {
      if (numOfEntries == 0) return new ArrayList();
      try {
         return this.manager.getEntries(numOfEntries, numOfBytes, this.associatedTable, this);
      }
      catch (SQLException ex)
      {
         throw new XmlBlasterException(ME, "sql exception: " + ex.getMessage());
      }
   }

   /**
    * @see I_Queue#peekSamePriority(int, long)
    */
   public ArrayList peekSamePriority(int numOfEntries, long numOfBytes) throws XmlBlasterException {
      if (numOfEntries == 0) return new ArrayList();
      try {
         ArrayList ret = this.manager.getEntriesBySamePriority(numOfEntries, numOfBytes, this.associatedTable, this);
         return ret;
      }
      catch (SQLException ex)
      {
         throw new XmlBlasterException(ME, "sql exception: " + ex.getMessage());
      }
   }

   /**
    * @see I_Queue#peek(int, long, int, int)
    */
   public ArrayList peekWithPriority(int numOfEntries, long numOfBytes, int minPriority, int maxPriority) throws XmlBlasterException {
      if (numOfEntries == 0) return new ArrayList();
      try {
         return this.manager.getEntriesByPriority(numOfEntries, numOfBytes, minPriority, maxPriority, this.associatedTable, this);
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(ME, "peekWithPriority, sql exception: " + ex.getMessage());
      }
   }


   /**
    * @see I_Queue#peekWithLimitEntry(I_QueueEntry)
    */
   public ArrayList peekWithLimitEntry(I_QueueEntry limitEntry) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "peekWithLimitEntry called");
      if (limitEntry == null) return new ArrayList();
      try {
         return this.manager.getEntriesWithLimit(this.associatedTable, this, limitEntry);
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(ME, "peekWithLimitEntry. SQLException: " + ex.getMessage());
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

            this.numOfDurableBytes = -1L;
            getNumOfDurableBytes_();
            this.numOfDurableEntries = -1L;
            getNumOfDurableEntries_();

            return (int)ret.countEntries;
         }
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(ME, "remove, sql exception: " + ex.getMessage());
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

            this.numOfDurableBytes = -1L;
            getNumOfDurableBytes_();
            this.numOfDurableEntries = -1L;
            getNumOfDurableEntries_();
            return ret.countEntries;
         }
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(ME, "remove, sql exception: " + ex.getMessage());
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
         ArrayList list = this.manager.getEntries(dataIdArray, this.associatedTable, this);
         return removeRandom((I_QueueEntry[])list.toArray(new I_QueueEntry[list.size()]));
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(ME, "removeRandom, sql exception: " + ex.getMessage());
      }
   }


   /**
    * @see I_Queue#removeRandom(I_QueueEntry)
    */
   public int removeRandom(I_QueueEntry entry) throws XmlBlasterException {
      if (entry == null) return 0;
      I_QueueEntry[] arr = new I_QueueEntry[1];
      arr[0] = entry;
      return (int)removeRandom(arr);
   }

   /**
    * @see I_Queue#removeRandom(I_QueueEntry[])
    */
   public long removeRandom(I_QueueEntry[] queueEntries) throws XmlBlasterException {
      if (queueEntries == null) return 0;
      try {
         long[] ids = new long[queueEntries.length];

         long currentAmount = 0L;
         long currentDurableSize = 0L;
         long currentDurableEntries = 0L;
         for (int i=0; i < ids.length; i++) {
            ids[i] = queueEntries[i].getUniqueId();
            currentAmount += queueEntries[i].getSizeInBytes();
            if (queueEntries[i].isDurable()) {
               currentDurableSize += queueEntries[i].getSizeInBytes();
               currentDurableEntries++;
            }
         }

         synchronized(this.modificationMonitor) {

            long ret = this.manager.deleteEntries(this.associatedTable, ids);
            this.numOfEntries -= ret;

            if ((int)ret != queueEntries.length) { // then we need to retrieve the values
               this.numOfDurableBytes = -1L;
               getNumOfDurableEntries_();
               this.numOfDurableEntries = -1L;
               getNumOfDurableEntries_();
               this.numOfBytes = -1L;
               getNumOfBytes_();
            }
            else {
               this.numOfBytes -= currentAmount;
               this.numOfDurableBytes -= currentDurableSize;
               this.numOfDurableEntries -= currentDurableEntries;
            }
            return ret;
         }
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(ME, "removeRandom, sql exception: " + ex.getMessage());
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
         if (this.log.DUMP)
            this.log.dump(ME, "conditionalRemove: SQLException: " + ex.getMessage());
         throw new XmlBlasterException(ME, "conditionalRemove: SQLException: " + ex.getMessage());
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
   private long getNumOfEntries_() throws XmlBlasterException {
      if (this.numOfEntries > -1L) return this.numOfEntries;
      synchronized (this.modificationMonitor) {
         try {
            this.numOfEntries = this.manager.getNumOfEntries(this.associatedTable);
            return this.numOfEntries;
         }
         catch (SQLException ex) {
            throw new XmlBlasterException(ME, "getNumOfEntries_ SQLException: " + ex.getMessage());
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
    * It returns the number of durable entries in the queue.
    * In case this value is -1L (which means a previous attempt to read from the
    * DB failed) it will synchronize against the DB by making a call to the DB.
    * If that fails it will return -1L.
    *
    * @see I_Queue#getNumOfDurableEntries()
    */
   private long getNumOfDurableEntries_() throws XmlBlasterException {
      if (this.numOfDurableEntries > -1L) return this.numOfDurableEntries;
      synchronized (this.modificationMonitor) {
         try {
            this.numOfDurableEntries = this.manager.getNumOfDurables(this.associatedTable);
            return this.numOfDurableEntries;
         }
         catch (SQLException ex) {
            throw new XmlBlasterException(ME, "getNumOfDurableEntries_ SQLException: " + ex.getMessage());
         }
      }
   }


   /**
    * It returns the number of durable entries in the queue.
    * In case this value is -1L (which means a previous attempt to read from the
    * DB failed) it will synchronize against the DB by making a call to the DB.
    * If that fails it will return -1L.
    *
    * @see I_Queue#getNumOfDurableEntries()
    */
   public long getNumOfDurableEntries() {
      try {
         return getNumOfDurableEntries_();
      }
      catch (XmlBlasterException ex) {
         this.log.error(ME, "getNumOfEntries, exception: " + ex.getMessage());
         return this.numOfDurableEntries;
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
            throw new XmlBlasterException(ME, "getNumOfBytes_ SQLException: " + ex.getMessage());
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
    * It returns the number of durable entries in the queue.
    * In case this value is -1L (which means a previous attempt to read from the
    * DB failed) it will synchronize against the DB by making a call to the DB.
    * If that fails it will return -1L.
    *
    * @see I_Queue#getNumOfDurableBytes()
    */
   private long getNumOfDurableBytes_() throws XmlBlasterException {
      if (this.numOfDurableBytes > -1L) return this.numOfDurableBytes;
      synchronized (this.modificationMonitor) {
         try {
            this.numOfDurableBytes = this.manager.getSizeOfDurables(this.associatedTable);
            return this.numOfDurableBytes;
         }
         catch (SQLException ex) {
            throw new XmlBlasterException(ME, "getNumOfDurableBytes_ SQLException: " + ex.getMessage());
         }
      }
   }


   /**
    * It returns the number of durable entries in the queue.
    * In case this value is -1L (which means a previous attempt to read from the
    * DB failed) it will synchronize against the DB by making a call to the DB.
    * If that fails it will return -1L.
    *
    * @see I_Queue#getNumOfDurableBytes()
    */
   public long getNumOfDurableBytes() {
      try {
         return getNumOfDurableBytes_();
      }
      catch (XmlBlasterException ex) {
         this.log.error(ME, "getNumOfBytes, exception: " + ex.getMessage());
         return this.numOfDurableBytes;
      }
   }


   /**
    * @see I_Queue#getMaxNumOfBytes()
    */
   public long getMaxNumOfBytes() {
      return this.property.getMaxSize();
   }


   /**
    * Updates the given message queue entry with a new value. Note that this
    * can be used if an entry with the unique id already exists.
    * ?? Does this really make sense here since we need to store history ??
    * ?? Should we define a switch which can deactivate storage of history ??
    */
   public int update(I_QueueEntry queueEntry) throws XmlBlasterException
   {
      throw new XmlBlasterException(ME, "update not implemented");
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
         this.numOfDurableEntries = 0L;
         this.numOfDurableBytes = 0L;
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
      throw new XmlBlasterException(ME, "removeHead not implemented yet");
   }



   /**
    * Shutdown the implementation, sync with data store
    * @param true: force shutdown, don't flush everything
    */
   public void shutdown(boolean force) {
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
      StringBuffer sb = new StringBuffer(256);
      String offset = (extraOffset == null) ? "\n   " : ("\n   "+extraOffset);

      sb.append(offset).append("<JdbcQueuePlugin id='" + getQueueId() + "'>");
      sb.append(offset).append("<type>" + getType() + "</type>");
      sb.append(offset).append("  <maxNumOfEntries>" + getMaxNumOfEntries() + "</maxNumOfEntries>");
      sb.append(offset).append("  <maxNumOfBytes>" + getMaxNumOfBytes() + "</maxNumOfBytes>");
      sb.append(offset).append("  <numOfEntries>" + getNumOfEntries() + "</numOfEntries>");
      sb.append(offset).append("  <numOfBytes>" + getNumOfBytes() + "</numOfBytes>");
      sb.append(offset).append("  <numOfDurables>" + getNumOfDurableEntries() + "</numOfDurables>");
      sb.append(offset).append("  <sizeOfDurables>" + getNumOfDurableBytes() + "</sizeOfDurables>");
      sb.append(offset).append("  <sizeOfDurables>" + getNumOfDurableBytes() + "</sizeOfDurables>");
      sb.append(offset).append("  <associatedTable>" + this.associatedTable + "</associatedTable>");
      sb.append(offset).append("</JdbcQueuePlugin>");
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
   public String getType() { return "JDBC"; }

   /**
    * Enforced by I_Plugin
    * @return "1.0"
    */
   public String getVersion() { return "1.0"; }

   /**
    * Cleans up the current queue (it deletes all the entries and frees the
    * table used and cleans up all tables in the database). This method should
    * never be called, only for testing purposes or to clean up the entire
    * database.
    */
   public void destroy() throws XmlBlasterException {
      try {
         this.clear();
         this.shutdown(true);
         long ret = this.manager.releaseTable(this.strippedQueueId, this.associatedTable);
         this.property = null;
         this.manager.cleanUp(this.queueId);
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(ME, "SQLException: " + ex.getMessage());
      }
   }

}
