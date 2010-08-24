/*------------------------------------------------------------------------------
Name:      RamQueuePlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue.ram;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

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
 * Queueing messages in RAM only, sorted after priority and timestamp
 * @author xmlBlaster@marcelruff.info
 */
public final class RamQueuePlugin implements I_Queue, I_StoragePlugin
{
   private String ME = "RamQueuePlugin";
   private StorageId storageId;       // e.g. "history:/node/heron/12345"
   private boolean notifiedAboutAddOrRemove = false;
//   private BoundedPriorityQueue boundedPriorityQueue;
   private TreeSet storage;
   private QueuePropertyBase property;
   private Global glob;
   private static Logger log = Logger.getLogger(RamQueuePlugin.class.getName());
   private I_QueuePutListener putListener;
   private boolean isShutdown = false;
   private MsgComparator comparator;
   private final int MAX_PRIO = 9; // see PriorityEnum.MAX_PRIORITY
   private long sizeInBytes = 0L;
   private long persistentSizeInBytes = 0L;
   private long numOfPersistentEntries = 0L;
   private PluginInfo pluginInfo;
   private StorageSizeListenerHelper storageSizeListenerHelper;
   
   public RamQueuePlugin() {
      this.storageSizeListenerHelper = new StorageSizeListenerHelper(this);
   }
   
   /**
    * Is called after the instance is created.
    * @param uniqueQueueId A unique name, allowing to create a unique name for a persistent store (e.g. file name)
    *                "update:/node/heron/client/joe/2", "history:<oid>", "client:joe/2"
    * @param userData For example a Properties object or a String[] args object passing the configuration data
    *                 Here we expect a QueuePropertyBase instance
    */
   public void initialize(StorageId uniqueQueueId, Object userData) throws XmlBlasterException {
      this.property = null;
      setProperties(userData);
      
      if (this.property != null && this.glob != null && this.glob.isServerSide() != this.property.getGlobal().isServerSide()) {
         log.severe("Incompatible globals this.property.getGlobal().isServerSide()=" + this.property.getGlobal().isServerSide() + ": " + Global.getStackTraceAsString(null));
      }
      this.glob = this.property.getGlobal();

      this.storageId = uniqueQueueId;
      if (storageId == null || glob == null) {
         Thread.dumpStack();
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal arguments in RamQueuePlugin constructor: storageId=" + storageId);
      }

      this.ME = "RamQueuePlugin-" + storageId.getId();

      long maxEntries = property.getMaxEntries();
      if (maxEntries > Integer.MAX_VALUE)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "initialize: The maximum number of messages is too big");
      this.comparator = new MsgComparator();
      this.storage = new TreeSet(this.comparator);
      this.numOfPersistentEntries = 0L;
      this.persistentSizeInBytes = 0L;
      this.isShutdown = false;
   }

   /**
    * Allows to overwrite properties which where passed on initialize(). 
    * The properties which support hot configuration are depending on the used implementation
    * <p>
    * capacity is immutable, if you try to change a warning is logged
    * </p>
    */
   public void setProperties(Object userData) throws XmlBlasterException {
      if (userData == null) return;
      QueuePropertyBase newProp;
      try {
         newProp = (QueuePropertyBase)userData;
      }
      catch(Throwable e) { // this.log is still null
         throw XmlBlasterException.convert(this.glob, ME, "Can't configure queue, your properties are invalid", e); // glob is allowed to be null
      }

      this.property = newProp;
   }

   /**
    * Access the current queue configuration
    */
   public Object getProperties() {
      return this.property;
   }

   public boolean isTransient() {
      return true;
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
    * Gets the references of the messages in the queue. Note that the data
    * which is referenced here may be changed by other threads.
    * @see I_Queue#getEntryReferences()
    */
   public long[] getEntryReferences() throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "getEntryReferences() is not implemented");
   }

   /**
    * Gets a copy of the entries (the messages) in the queue. If the queue
    * is modified, this copy will not be affected. This method is useful for client browsing.
    * THIS METHOD IS NOT IMPLEMENTED
    * @throws XmlBlasterException always
    */
   public ArrayList getEntries(I_EntryFilter entryFilter) throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "getEntries() is not implemented");
   }

   /** For verbose logging */
   public StorageId getStorageId() {
      return storageId;
   }

   public void shutdown() {
      int size = 0;
      synchronized (this) {
         if (log.isLoggable(Level.FINE)) log.fine("Entering shutdown(" + this.storage.size() + ")");
         if (this.isShutdown) return;
         this.isShutdown = true;
         size = this.storage.size();
      }

      this.storageSizeListenerHelper.invokeStorageSizeListener();
      this.removeStorageSizeListener(null);

      if (size > 0) {
         String reason = "Shutting down RAM queue which contains " + size + " messages";
         if (log.isLoggable(Level.FINE)) log.fine(reason);
         //throw new XmlBlasterException(ME, reason);
         //handleFailure !!!
      }

      if (log.isLoggable(Level.FINER)) {
         log.finer("shutdown() of queue " + this.getStorageId() + " which contains " + size + "messages");
      }
      
      glob.getQueuePluginManager().cleanup(this);
   }

   public boolean isShutdown() {
      return this.isShutdown;
   }

   /**
    * Flush the queue
    * @return The number of messages erased
    */
   public long clear() {
      long ret = 0L;
      I_QueueEntry[] entries = null;
      try {
         synchronized(this) {
            ret = this.storage.size();
   
            // Take a copy to avoid java.util.ConcurrentModificationException
            entries = (I_QueueEntry[])this.storage.toArray(new I_QueueEntry[this.storage.size()]);
   
            for (int ii=0; ii<entries.length; ii++) {
               entries[ii].setStored(false);
            }
   
            this.storage.clear();
            this.sizeInBytes = 0L;
            this.persistentSizeInBytes = 0L;
            this.numOfPersistentEntries = 0L;
         }
      }
      finally {
         if (this.notifiedAboutAddOrRemove) {
            for (int ii=0; ii<entries.length; ii++) {
               entries[ii].removed(this.storageId);
            }
         }
      }

      this.storageSizeListenerHelper.invokeStorageSizeListener();
      return ret;
   }

   /**
    * @see I_Queue#remove()
    */
   public int remove() throws XmlBlasterException {
      return (int)removeNum(1);
   }

   /**
    * @see I_Queue#remove(long, long)
    */
   public long removeNum(long numOfEntries)
      throws XmlBlasterException
   {
      if (numOfEntries > Integer.MAX_VALUE)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "remove: too many entries to remove " + numOfEntries);
      long size = 0;
      ArrayList elementsToDelete = null;
      try {
         synchronized(this) {
            ReturnDataHolder ret = this.genericPeek((int)numOfEntries, -1L, 0, 9);
            elementsToDelete = ret.list;

            // count the persistent entries (and the persistent sizes)
            for (int i=0; i < elementsToDelete.size(); i++) {
               I_QueueEntry entry = (I_QueueEntry)elementsToDelete.get(i);
               if (entry.isPersistent()) {
                  this.numOfPersistentEntries--;
                  this.persistentSizeInBytes -= entry.getSizeInBytes();
               }
               entry.setStored(false); // tell the entry it has been removed from the storage ...
            }

            this.storage.removeAll(elementsToDelete);
            this.sizeInBytes -= ret.countBytes;
            size = elementsToDelete.size();
         }
      }
      finally {
         if (this.notifiedAboutAddOrRemove && elementsToDelete != null) {
            for (int i=0; i < elementsToDelete.size(); i++) {
               ((I_Entry)elementsToDelete.get(i)).removed(this.storageId);
            }
         }
      }
     this.storageSizeListenerHelper.invokeStorageSizeListener();
     return size;
   }


   /**
    */
   public long removeWithPriority(long numOfEntries, long numOfBytes, int minPriority, int maxPriority)
      throws XmlBlasterException {
      if (numOfEntries > Integer.MAX_VALUE)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "remove: too many entries to remove " + numOfEntries);
      ArrayList elementsToRemove = peekWithPriority((int)numOfEntries, numOfBytes, minPriority, maxPriority);
      boolean[] ret = removeRandom((I_Entry[])elementsToRemove.toArray(new I_Entry[elementsToRemove.size()]));
      long count = 0L;
      for (int i=0; i < ret.length;i++) if (ret[i]) count++;
      return count;
   }

   /**
    * @see I_Queue#removeTransient()
    */
   public int removeTransient() throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "removeTransient() is not implemented");
   }


   /**
    * @see I_Queue#peek()
    */
   public I_QueueEntry peek() {
      if (getNumOfEntries() < 1) return null;
      synchronized (this) {
         return (I_QueueEntry)this.storage.first();
      }
      //return (I_QueueEntry)this.storage.iterator().next();
   }


   /**
    * @param minPrio Extension to I_Queue:  if -1 then only entries with similar priority as the first one are taken (= peekSamePriority())
    * @see I_Queue#peek(int, long)
    */
   public ArrayList peekWithPriority(int numOfEntries, long numOfBytes, int minPrio, int maxPrio)
      throws XmlBlasterException {
      return genericPeek(numOfEntries, numOfBytes, minPrio, maxPrio).list;
   }


   /**
    * 
    * @param   numOfEntries the number of entries to peek. If -1 then all entries
    *          found are peeked.
    * @param   numOfBytes as input it is the size in bytes to retrieve.
    * @param   minPrio the minimum priority to return. If a negative number, then
    *          all entries which have the same priority as the first entry are returned.
    *          this value is inclusive.
    * @param   maxPrio the maximum priority to return (inclusive).
    */
   private ReturnDataHolder genericPeek(int numOfEntries, long numOfBytes, int minPrio, int maxPrio)
      throws XmlBlasterException {
      ReturnDataHolder ret = new ReturnDataHolder();
//      long numOfBytes = bytes.longValue();
//      long count = 0L;
      long currentSizeInBytes = 0L;
//      long totalSizeInBytes = 0L;
//      ArrayList ret = new ArrayList();
      if (getNumOfEntries() < 1) return ret;

      synchronized (this) {
         Iterator iter = this.storage.iterator();
         // find all elements to delete ...
         while (iter.hasNext() && (ret.countEntries<numOfEntries||numOfEntries<0)) {
            I_QueueEntry entry = (I_QueueEntry)iter.next();
            currentSizeInBytes = entry.getSizeInBytes();

            if ((ret.countBytes+currentSizeInBytes>=numOfBytes) && ret.countEntries>0L && numOfBytes>-1) break;
            // further specific breaks ...
            int prio = entry.getPriority();
            if (minPrio < 0) {
               minPrio = prio;
               maxPrio = minPrio;
            }
            if (prio < minPrio) break;
            if (prio <= maxPrio) {
               ret.countBytes += currentSizeInBytes;
               ret.list.add(entry);
               ret.countEntries++;
            }
         }
      }
      return ret;
   }


   /**
    * @see I_Queue#peek(int, long)
    */
   public ArrayList peek(int numOfEntries, long numOfBytes)
      throws XmlBlasterException {
      return genericPeek(numOfEntries, numOfBytes, 0, MAX_PRIO).list;
   }

   public ArrayList peekStartAt(int numOfEntries, long numOfBytes, I_QueueEntry firstEntryExlusive) throws XmlBlasterException {
      throw new IllegalAccessError("RamQueuePlugin has peekStartAt not implemented");
   }

   /**
    * @see I_Queue#peekSamePriority(int, long)
    */
   public ArrayList peekSamePriority(int numOfEntries, long numOfBytes) throws XmlBlasterException {
      return genericPeek(numOfEntries, numOfBytes, -1, -1).list;
   }


   /**
    * @see I_Queue#peekWithLimitEntry(I_QueueEntry)
    * @deprecated
    */
   public ArrayList peekWithLimitEntry(I_QueueEntry limitEntry) throws XmlBlasterException {
      if (limitEntry == null) return new ArrayList();
      synchronized (this) {
         return new ArrayList(this.storage.headSet(limitEntry));
      }
   }

   /**
    * @see I_Queue#removeWithLimitEntry(I_QueueEntry, boolean)
    */
   public long removeWithLimitEntry(I_QueueEntry limitEntry, boolean inclusive) throws XmlBlasterException {
      long ret = 0L;
      if (limitEntry == null) 
         return ret;
      
      synchronized (this) {
         SortedSet set = this.storage.headSet(limitEntry);
         ret = set.size();
         this.storage.removeAll(set);
         if (inclusive) {
            if (this.storage.remove(limitEntry)) ret++;
         }
      }
      this.storageSizeListenerHelper.invokeStorageSizeListener();
      
      return ret;
   }

   /**
    * @see I_Queue#getNumOfEntries()
    */
   public long getNumOfEntries() {
      synchronized (this) {
         return this.storage.size();
      }
   }

   /**i
    * @see I_Queue#getNumOfPersistentEntries()
    */
   public long getNumOfPersistentEntries() {
      return this.numOfPersistentEntries;
   }

   /**
    * @see I_Queue#getMaxNumOfEntries()
    */
   public long getMaxNumOfEntries() {
      return property.getMaxEntries();
   }

   /**
    * @see I_Queue#getNumOfBytes()
    */
   public long getNumOfBytes() {
      return this.sizeInBytes;
   }

   /**
    * @see I_Queue#getNumOfPersistentBytes()
    */
   public long getNumOfPersistentBytes() {
      return this.persistentSizeInBytes;
   }


   /**
    * Gets the number of bytes by really reading (i.e. by scanning the whole
    * queue contents) the number of bytes of each single entry
    * @see I_Queue#getNumOfBytes()
    */
   public long getSynchronizedNumOfBytes() {
      synchronized (this) {
         Iterator iter = this.storage.iterator();
         long sum = 0L;
         while (iter.hasNext()) {
            sum += ((I_QueueEntry)(iter.next())).getSizeInBytes();
         }
         return sum;
      }
   }

   /**
    * @see I_Queue#getMaxNumOfBytes()
    */
   public long getMaxNumOfBytes() {
      return this.property.getMaxBytes();
   }

   /**
    * @see I_Queue#removeRandom(I_Entry)
    */
   public int removeRandom(I_Entry entry) throws XmlBlasterException {
      I_Entry[] arr = new I_Entry[1];
      arr[0] = entry;
      if (removeRandom(arr)[0]) return 1; 
      else return 0;
   }

   /**
    * @see I_Queue#removeRandom(I_Entry[])
    */
   public boolean[] removeRandom(I_Entry[] queueEntries) throws XmlBlasterException {
      if ((queueEntries == null) || (queueEntries.length == 0)) return new boolean[0];
      boolean ret[] = new boolean[queueEntries.length];

      ArrayList entriesToRemove = new ArrayList();
      try {
         synchronized(this) {
            if (this.storage.size() == 0) return ret; // all entries are false 

            /* Did not work with all virtual machines ...
            this.storage.removeAll(java.util.Arrays.asList(queueEntries));
            */
            for (int j=0; j<queueEntries.length; j++) {
               if (queueEntries[j] == null) continue;
               if (this.notifiedAboutAddOrRemove) {
                  entriesToRemove.add(queueEntries[j]);
               }
               queueEntries[j].setStored(false); // tell the entry it has been removed from the storage ...
               if (this.storage.remove(queueEntries[j])) {
                  ret[j] = true;
                  I_Entry entry = queueEntries[j];
                  this.sizeInBytes -= entry.getSizeInBytes();
                  if (entry.isPersistent()) {
                     this.persistentSizeInBytes -= entry.getSizeInBytes();
                     this.numOfPersistentEntries--;
                  }
               }
            }
         }
      }
      finally {
         for (int i=0; i < entriesToRemove.size(); i++) {
            ((I_Entry)entriesToRemove.get(i)).removed(this.storageId);
         }
      }
      this.storageSizeListenerHelper.invokeStorageSizeListener();
      return ret;
   }

   /**
    * Currently NOT supported by I_Queue. 
    */
   public I_QueueEntry take() throws XmlBlasterException {
      ArrayList list = take(1, -1L);
      if (list == null || list.size() < 1) return null;
      return (I_QueueEntry)list.get(0);
   }

   /**
    * Currently NOT supported by I_Queue. 
    */
   public ArrayList take(int numOfEntries, long numOfBytes) throws XmlBlasterException {
      return takeWithPriority(numOfEntries, numOfBytes, 0, MAX_PRIO);
   }

   /**
    */
   public ArrayList takeSamePriority(int numOfEntries, long numOfBytes) throws XmlBlasterException {
      return takeWithPriority(numOfEntries, numOfBytes, -1, -1);
   }

   /**
    * @see I_Queue
    */
   public ArrayList takeWithPriority(int numOfEntries, long numOfBytes, int minPriority, int maxPriority) throws XmlBlasterException {
      if (isShutdown) {
         log.warning("The queue is shutdown, no message access is possible.");
         if (log.isLoggable(Level.FINE)) Thread.dumpStack();
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "The queue is shutdown, no message access is possible.");
      }
      ArrayList ret = null;

      ArrayList entriesToRemove = new ArrayList();
      try {
         synchronized (this) {
            ret = genericPeek(numOfEntries, numOfBytes, minPriority, maxPriority).list;
            for (int i=0; i < ret.size(); i++) {
               I_QueueEntry entry = (I_QueueEntry)ret.get(i);
               if (this.notifiedAboutAddOrRemove) {
                  entriesToRemove.add(entry);
               }
               entry.setStored(false); // tell the entry it has been removed from the storage ...
               if (this.storage.remove(entry)) {
                  this.sizeInBytes -= entry.getSizeInBytes();
                  if (entry.isPersistent()) {
                     this.numOfPersistentEntries--;
                     this.persistentSizeInBytes -= entry.getSizeInBytes();
                  }
               }
            }
         }
      }
      finally {
         for (int i=0; i < entriesToRemove.size(); i++) {
            ((I_Entry)entriesToRemove.get(i)).removed(this.storageId);
         }
      }
      
      this.storageSizeListenerHelper.invokeStorageSizeListener();
      return ret;
   }


   /**
    * Helper method to find out if still to retrieve entries in getAndDeleteLowest or not. 
    */
   private final boolean isInsideRange(int numEntries, int maxNumEntries, long numBytes, long maxNumBytes) {
      if (maxNumEntries < 0) {
         if (maxNumBytes <0L) return true;
         return numBytes < maxNumBytes;
      }
      // then maxNumEntries >= 0
      if (maxNumBytes <0L) return numEntries < maxNumEntries;
      // then the less restrictive of both is used (since none is negative)
      return numEntries < maxNumEntries || numBytes < maxNumBytes;
   }


   /**
    * @see I_Queue#takeLowest(int, long, I_QueueEntry, boolean)
    */
   public ArrayList takeLowest(int numOfEntries, long numOfBytes, I_QueueEntry limitEntry, boolean leaveOne)
      throws XmlBlasterException {
      return takeOrPeekLowest(numOfEntries, numOfBytes, limitEntry, leaveOne, true);
   }

   /**
    * @see I_Queue#peekLowest(int, long, I_QueueEntry, boolean)
    */
   public ArrayList peekLowest(int numOfEntries, long numOfBytes, I_QueueEntry limitEntry, boolean leaveOne)
      throws XmlBlasterException {
      return takeOrPeekLowest(numOfEntries, numOfBytes, limitEntry, leaveOne, false);
   }

   /**
    * @see I_Queue#takeLowest(int, long, I_QueueEntry, boolean)
    */
   private ArrayList takeOrPeekLowest(int numOfEntries, long numOfBytes, I_QueueEntry limitEntry, boolean leaveOne, boolean doDelete)
      throws XmlBlasterException {

      ArrayList ret = null; 
      ArrayList entriesToRemove = new ArrayList();
      try {
         synchronized(this) {
            LinkedList list = new LinkedList(this.storage);
            ListIterator iter = list.listIterator(list.size());
            int count = 0;
            long currentSizeInBytes = 0L;
            long totalSizeInBytes = 0L;
            ret = new ArrayList();

            // it leaves at least one entry in the list
            while (iter.hasPrevious()) {
               I_QueueEntry entry = (I_QueueEntry)iter.previous();
               currentSizeInBytes = entry.getSizeInBytes();
               if (!isInsideRange(count, numOfEntries, totalSizeInBytes, numOfBytes)) break;
               totalSizeInBytes += currentSizeInBytes;

               if (limitEntry != null && this.comparator.compare(limitEntry, entry) >= 0) break;
               ret.add(entry);
               count++;
            }
            if (leaveOne && this.storage.size() == ret.size()) ret.remove(ret.size()-1);

            if (doDelete) {
               for (int i=0; i < ret.size(); i++) {
                  // this.storage.removeAll(ret);
                  I_QueueEntry entry =  (I_QueueEntry)ret.get(i);
                  if (this.notifiedAboutAddOrRemove) {
                     entriesToRemove.add(entry);
                  }
                  entry.setStored(false); // tell the entry it has been removed from the storage ...
                  if (this.storage.remove(entry)) {
                     this.sizeInBytes -= entry.getSizeInBytes();
                     if (entry.isPersistent()) {
                        this.numOfPersistentEntries--;
                        this.persistentSizeInBytes -= entry.getSizeInBytes();
                     }
                  }
               }
            }
            // this.sizeInBytes -= totalSizeInBytes;
         }
      }
      finally {
         for (int i=0; i < entriesToRemove.size(); i++) {
            ((I_Entry)entriesToRemove.get(i)).removed(this.storageId);
         }
      }
      
      this.storageSizeListenerHelper.invokeStorageSizeListener();
      return ret;
   }

   /**
    * Put a message into the queue, blocks if take thread blocks synchronize
    */
   public void put(I_QueueEntry entry, boolean ignorePutInterceptor)
      throws XmlBlasterException {
      if (entry == null) return;

      if (isShutdown) {
         if (log.isLoggable(Level.FINE)) log.fine("The queue is shutdown, put() of message " + entry.getUniqueId() + " failed");
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "The queue is shutdown, put() of message " + entry.getUniqueId() + " failed");
      }

      if ((this.putListener != null) && (!ignorePutInterceptor)) {
         // Is an interceptor registered?
         if (this.putListener.putPre(entry) == false)
            return;
      }

      if (getNumOfEntries() > property.getMaxEntries()) { // Allow superload one time only
         String reason = "Queue overflow (number of entries), " + property.getMaxEntries() +
                         " messages are in queue, try increasing '" +
                         this.property.getPropName("maxEntries") + "' on client login.";
         if (log.isLoggable(Level.FINE)) log.fine(reason);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
      }
      if (this.getNumOfBytes() > property.getMaxBytes()) { // Allow superload one time only
         String reason = "Queue overflow, " + this.getNumOfBytes() +
                         " bytes are in queue, try increasing '" + 
                         this.property.getPropName("maxBytes") + "' on client login.";
         if (log.isLoggable(Level.FINE)) log.fine(reason);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
      }

      synchronized(this) {
         if (!this.storage.contains(entry)) {
            if (this.storage.add(entry)) {
               entry.setStored(true);
               this.sizeInBytes += entry.getSizeInBytes();
               if (entry.isPersistent()) {
                  this.numOfPersistentEntries++;
                  this.persistentSizeInBytes += entry.getSizeInBytes();
               }
               if (this.notifiedAboutAddOrRemove) {
                  entry.added(this.storageId);
               }
            }
         }
         else {
            log.severe("Ignoring IDENTICAL uniqueId=" + entry.getUniqueId());
            Thread.dumpStack();
         }
      }

      this.storageSizeListenerHelper.invokeStorageSizeListener();
      if (this.putListener != null && !ignorePutInterceptor) {
         this.putListener.putPost(entry);
      }
   }

   /**
    * Put messages into the queue, blocks if take thread blocks synchronize
    */
   public void put(I_QueueEntry[] msgArr, boolean ignorePutInterceptor)
      throws XmlBlasterException {
      if (msgArr == null) return;

      //if (log.isLoggable(Level.FINER)) log.call(ME, "Entering put(" + msgArr.length + ")");

      if (isShutdown) {
         if (log.isLoggable(Level.FINE)) log.fine("The queue is shutdown, put() of " + msgArr.length + " messages failed");
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "The queue is shutdown, put() of " + msgArr.length + " messages failed");
      }

      // delegate put?
      if ((this.putListener != null) && (!ignorePutInterceptor)) {
         // Is an interceptor registered and it is not bypassed
         if (this.putListener.putPre(msgArr) == false)
            return;
      }

      if (getNumOfEntries() > property.getMaxEntries()) { // Allow superload one time only
         String reason = "RAM Queue overflow (num of entries), " + property.getMaxEntries()
               +
                  " messages are in queue, try increasing '" + this.property.getPropName("maxEntries") + "' on client login.";
         if (log.isLoggable(Level.FINE)) log.fine(reason+toXml());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
      }
      if (this.getNumOfBytes() > property.getMaxBytes()) { // Allow superload one time only
         String reason = "RAM Queue overflow, " + this.getNumOfBytes() + " bytes are in queue, try increasing '"
               +
                         this.property.getPropName("maxBytes") + "' on client login.";
         if (log.isLoggable(Level.FINE)) log.fine(reason+toXml());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
      }

      synchronized(this) {
//         this.storage.addAll(java.util.Arrays.asList(msgArr));
         for (int i=0; i < msgArr.length; i++) {
            I_QueueEntry entry = msgArr[i];
            if (!this.storage.contains(entry)) {
               if (this.storage.add(entry)) {
                  entry.setStored(true);
                  this.sizeInBytes += entry.getSizeInBytes();
                  if (entry.isPersistent()) {
                     this.numOfPersistentEntries++;
                     this.persistentSizeInBytes += entry.getSizeInBytes();
                  }
                  if (this.notifiedAboutAddOrRemove) {
                     entry.added(this.storageId);
                  }
               }
            }
         }
      }

      this.storageSizeListenerHelper.invokeStorageSizeListener();
      if (this.putListener != null && !ignorePutInterceptor) {
         this.putListener.putPost(msgArr);
      }
   }
   
   /**
    * Dump state of this object into a XML ASCII string.
    */
   public String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of RamQueuePlugin as a XML ASCII string
    */
   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<RamQueuePlugin id='").append(getStorageId().getId());
      sb.append("' type='").append(getType());
      sb.append("' version='").append(getVersion());
      sb.append("' numOfEntries='").append(getNumOfEntries());
      sb.append("' numOfBytes='").append(getNumOfBytes());

      sb.append("' numOfPersistentEntries='").append(getNumOfPersistentEntries());
      sb.append("' numOfPersistentBytes='").append(getNumOfPersistentBytes());
      sb.append("'>");
      sb.append(property.toXml(extraOffset+Constants.INDENT));
      sb.append(offset).append("</RamQueuePlugin>");

      return sb.toString();
   }


   /**
    * @see I_Queue#removeHead(I_QueueEntry)
    */
   public long removeHead(I_QueueEntry toEntry) throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "removeHead() is not implemented");
   }

   /**
    * destroys silently all the resources associated to this queue.
    */
   public void destroy() throws XmlBlasterException {
      synchronized (this) {
         this.storage.clear();
      }
      this.shutdown();
      this.property = null;
   }

   /**
    * @return a human readable usage help string
    */
   public String usage() {
      return "no usage";
   }

   /**
    * Enforced by I_Plugin
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) {
      this.glob = glob;
      this.pluginInfo = pluginInfo;
   }

   /**
    * Enforced by I_Plugin
    * @return "RAM"
    */
   public String getType() { return "RAM"; }

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
   public boolean registerStorageProblemListener(I_StorageProblemListener listener) {
      return false;
   }


   /**
    * @see org.xmlBlaster.util.queue.I_StorageProblemNotifier#unRegisterStorageProblemListener(I_StorageProblemListener)
    */
   public boolean unRegisterStorageProblemListener(I_StorageProblemListener listener) {
      return false;
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

   /**
    * @see I_Queue#embeddedObjectsToXml(OutputStream, Properties)
    */
   public long embeddedObjectsToXml(OutputStream out, Properties props) {
      log.warning("Sorry, dumping transient entries is not implemented");
      return 0;
   }
}

/**
 * Sorts the messages
 * <ol>
 *   <li>Priority</li>
 *   <li>Timestamp</li>
 * </ol>
 * @see org.xmlBlaster.util.queuemsg.MsgQueueEntry#compare(I_QueueEntry)
 */
final class MsgComparator implements Comparator
{
   /**
    * Comparing the longs directly is 20% faster than having a
    * String compound key
    */
   public int compare(Object o1, Object o2) {
      I_QueueEntry d1 = (I_QueueEntry)o1;
      I_QueueEntry d2 = (I_QueueEntry)o2;
      return d1.compare(d2);
   }

}

