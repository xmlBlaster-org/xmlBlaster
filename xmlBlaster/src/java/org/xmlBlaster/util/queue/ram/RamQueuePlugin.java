/*------------------------------------------------------------------------------
Name:      RamQueuePlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue.ram;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.engine.helper.QueuePropertyBase;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.util.Global;

import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queue.I_QueuePutListener;
import org.xmlBlaster.util.queue.ReturnDataHolder;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;

import java.util.Comparator;
// import EDU.oswego.cs.dl.util.concurrent.BoundedPriorityQueue;
// import java.util.HashSet;
// import java.util.Iterator;
import java.util.ArrayList;
// import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;


/**
 * Queueing messages in RAM only, sorted after priority and timestamp
 * @see <a href="http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/intro.html">The concurrent library</a>
 * @author xmlBlaster@marcelruff.info
 */
public final class RamQueuePlugin implements I_Queue, I_Plugin
{
   private String ME = "RamQueuePlugin";
   private StorageId storageId;       // e.g. "history:/node/heron/12345"
   private boolean notifiedAboutAddOrRemove = false;
//   private BoundedPriorityQueue boundedPriorityQueue;
   private TreeSet storage;
   private QueuePropertyBase property;
   private Global glob;
   private LogChannel log;
   private I_QueuePutListener putListener;
   private boolean isShutdown = false;
   private final I_QueueEntry[] DUMMY_ARR = new I_QueueEntry[0];
   private MsgComparator comparator;
   private final int MAX_PRIO = 9; // see PriorityEnum.MAX_PRIORITY
   private long sizeInBytes = 0L;
   private long durableSizeInBytes = 0L;
   private long numOfDurableEntries = 0L;

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

      this.glob = this.property.getGlobal();
      this.log = glob.getLog("queue");

      this.storageId = uniqueQueueId;
      if (storageId == null || glob == null) {
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal arguments in RamQueuePlugin constructor: storageId=" + storageId);
      }

      this.ME = "RamQueuePlugin-" + storageId.getId();

      long maxMsg = property.getMaxMsg();
      if (maxMsg > Integer.MAX_VALUE)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "initialize: The maximum number of messages is too big");
      this.comparator = new MsgComparator();
      this.storage = new TreeSet(this.comparator);
      this.numOfDurableEntries = 0L;
      this.durableSizeInBytes = 0L;
      this.isShutdown = false;
   }

   /**
    * Allows to overwrite properties which where passed on initialize()
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
   public final long[] getEntryReferences() throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "getEntryReferences() is not implemented");
   }

   /**
    * Gets a copy of the entries (the messages) in the queue. If the queue
    * is modified, this copy will not be affected. This method is useful for client browsing.
    */
   public final ArrayList getEntries() throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "getEntries() is not implemented");
   }

   public void finalize() {
      if (log.TRACE) log.trace(ME, "finalize - garbage collected");
   }

   /** For verbose logging */
   public final StorageId getStorageId() {
      return storageId;
   }

   public final void shutdown(boolean force) {
      if (log.TRACE) log.trace(ME, "Entering shutdown(" + this.storage.size() + ")");
      //Thread.currentThread().dumpStack();
      synchronized (this) {
         if (this.storage.size() > 0) {
            if (force) {
               log.warn(ME, "Shutting down queue forced which contains " + this.storage.size() + " messages, destroying entries");
               clear();
            }
            else {
               String reason = "Shutting down queue which contains " + this.storage.size() + " messages";
               log.warn(ME, reason);
               //throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, reason);
               //handleFailure !!!
            }
         }
         isShutdown = true;
      }
      if (log.CALL) log.call(ME, "shutdown() of queue " + this.getStorageId());
   }

   public final boolean isShutdown() {
      return this.isShutdown;
   }

   /**
    * Flush the queue
    * @return The number of messages erased
    */
   public final long clear() {
      synchronized(this) {
         long ret = (long)this.storage.size();
         if (this.notifiedAboutAddOrRemove) {
            Iterator iter = this.storage.iterator();
            while (iter.hasNext()) {
               I_QueueEntry entry = (I_QueueEntry)iter.next();
               try {
                  entry.removed(this.storageId);
               }
               catch (XmlBlasterException e) {
                  log.error(ME, "Unexpected exception: " + e.getMessage());
               }
            }
         }
         this.storage.clear();
         this.sizeInBytes = 0L;
         this.durableSizeInBytes = 0L;
         this.numOfDurableEntries = 0L;
         return ret;
      }
   }

   /**
    * @see I_Queue#remove()
    */
   public final int remove() throws XmlBlasterException {
      return (int)remove(1, -1L);
   }

   /**
    * @see I_Queue#remove(long, long)
    */
   public final long remove(long numOfEntries, long numOfBytes)
      throws XmlBlasterException
   {
      if (numOfEntries > Integer.MAX_VALUE)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "remove: too many entries to remove " + numOfEntries);
      synchronized(this) {
         ReturnDataHolder ret = this.genericPeek((int)numOfEntries, numOfBytes, 0, 9);
         ArrayList elementsToDelete = ret.list;

         // count the durable entries (and the durable sizes)
         for (int i=0; i < elementsToDelete.size(); i++) {
            I_QueueEntry entry = (I_QueueEntry)elementsToDelete.get(i);
            if (this.notifiedAboutAddOrRemove) {
               entry.removed(this.storageId);
            }
            if (entry.isDurable()) {
               this.numOfDurableEntries--;
               this.durableSizeInBytes -= entry.getSizeInBytes();
            }
         }

         this.storage.removeAll(elementsToDelete);
         this.sizeInBytes -= ret.countBytes;
         return elementsToDelete.size();
      }
   }


   /**
    */
   public final long removeWithPriority(long numOfEntries, long numOfBytes, int minPriority, int maxPriority)
      throws XmlBlasterException {
      if (numOfEntries > Integer.MAX_VALUE)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "remove: too many entries to remove " + numOfEntries);
      ArrayList elementsToRemove = peekWithPriority((int)numOfEntries, numOfBytes, minPriority, maxPriority);
      return removeRandom((I_Entry[])elementsToRemove.toArray(new I_Entry[elementsToRemove.size()]));
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
   public final I_QueueEntry peek() {
      if (this.storage.size() < 1) return null;
      return (I_QueueEntry)this.storage.first();
      //return (I_QueueEntry)this.storage.iterator().next();
   }


   /**
    * @param minPrio Extension to I_Queue:  if -1 then only entries with similar priority as the first one are taken (= peekSamePriority())
    * @see I_Queue#peek(int, long)
    */
   public final ArrayList peekWithPriority(int numOfEntries, long numOfBytes, int minPrio, int maxPrio)
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
   private final ReturnDataHolder genericPeek(int numOfEntries, long numOfBytes, int minPrio, int maxPrio)
      throws XmlBlasterException {
      ReturnDataHolder ret = new ReturnDataHolder();
//      long numOfBytes = bytes.longValue();
//      long count = 0L;
      long currentSizeInBytes = 0L;
//      long totalSizeInBytes = 0L;
//      ArrayList ret = new ArrayList();
      if (this.storage.size() < 1) return ret;

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
   public final ArrayList peek(int numOfEntries, long numOfBytes)
      throws XmlBlasterException {
      return genericPeek(numOfEntries, numOfBytes, 0, MAX_PRIO).list;
   }


   /**
    * @see I_Queue#peekSamePriority(int, long)
    */
   public ArrayList peekSamePriority(int numOfEntries, long numOfBytes) throws XmlBlasterException {
      return genericPeek(numOfEntries, numOfBytes, -1, -1).list;
   }


   /**
    * @see I_Queue#peekWithLimitEntry(I_QueueEntry)
    */
   public ArrayList peekWithLimitEntry(I_QueueEntry limitEntry) throws XmlBlasterException {
      if (limitEntry == null) return new ArrayList();
      return new ArrayList(this.storage.headSet(limitEntry));
   }



   /**
    * @see I_Queue#getNumOfEntries()
    */
   public long getNumOfEntries() {
      return this.storage.size();
   }

   /**i
    * @see I_Queue#getNumOfDurableEntries()
    */
   public long getNumOfDurableEntries() {
      return this.numOfDurableEntries;
   }

   /**
    * @see I_Queue#getMaxNumOfEntries()
    */
   public final long getMaxNumOfEntries() {
      return property.getMaxMsg();
   }

   /**
    * @see I_Queue#getNumOfBytes()
    */
   public long getNumOfBytes() {
      return this.sizeInBytes;
   }

   /**
    * @see I_Queue#getNumOfDurableBytes()
    */
   public long getNumOfDurableBytes() {
      return this.durableSizeInBytes;
   }


   /**
    * Gets the number of bytes by really reading (i.e. by scanning the whole
    * queue contents) the number of bytes of each single entry
    * @see I_Queue#getNumOfBytes()
    */
   public long getSynchronizedNumOfBytes() {
      Iterator iter = this.storage.iterator();
      long sum = 0L;
      while (iter.hasNext()) {
         sum += ((I_QueueEntry)(iter.next())).getSizeInBytes();
      }
      return sum;
   }

   /**
    * @see I_Queue#getMaxNumOfBytes()
    */
   public final long getMaxNumOfBytes() {
      return this.property.getMaxBytes();
   }

   /**
    * @see I_Queue#removeRandom(I_Entry)
    */
   public int removeRandom(I_Entry entry) throws XmlBlasterException {
      I_Entry[] arr = new I_Entry[1];
      arr[0] = entry;
      return (int)removeRandom(arr);
   }

   /**
    * @see I_Queue#removeRandom(I_Entry[])
    */
   public long removeRandom(I_Entry[] queueEntries) throws XmlBlasterException {
      long ret = 0L;
      if ((queueEntries == null) || (queueEntries.length == 0))
         return 0;
      synchronized(this) {
         ret = this.storage.size();
         if (ret == 0) return 0;

         /* Did not work with all virtual machines ...
         this.storage.removeAll(java.util.Arrays.asList(queueEntries));
         */
         for (int j=0; j<queueEntries.length; j++) {
            if (queueEntries[j] == null) continue;
            if (this.notifiedAboutAddOrRemove) {
               queueEntries[j].removed(this.storageId);
            }
            if (this.storage.remove(queueEntries[j])) {
               I_Entry entry = queueEntries[j];
               this.sizeInBytes -= entry.getSizeInBytes();
               if (entry.isDurable()) {
                  this.durableSizeInBytes -= entry.getSizeInBytes();
                  this.numOfDurableEntries--;
               }
            }
         }
         ret -= this.storage.size();
      }
      return ret;
   }

   /**
    * @see I_Queue#take()
    */
   public final I_QueueEntry take() throws XmlBlasterException {
      ArrayList list = take(1, -1L);
      if (list == null || list.size() < 1) return null;
      return (I_QueueEntry)list.get(0);
   }

   /**
    * @see I_Queue#take(int, long)
    */
   public final ArrayList take(int numOfEntries, long numOfBytes) throws XmlBlasterException {
      return takeWithPriority(numOfEntries, numOfBytes, 0, MAX_PRIO);
   }

   /**
    */
   public ArrayList takeSamePriority(int numOfEntries, long numOfBytes) throws XmlBlasterException {
      return takeWithPriority(numOfEntries, numOfBytes, -1, -1);
   }

   /**
    * @see I_Queue#take(int, long)
    */
   public final ArrayList takeWithPriority(int numOfEntries, long numOfBytes, int minPriority, int maxPriority) throws XmlBlasterException {
      if (isShutdown) {
         log.warn(ME, "The queue is shutdown, no message access is possible.");
         if (log.TRACE) Thread.currentThread().dumpStack();
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "The queue is shutdown, no message access is possible.");
      }
      ArrayList ret = null;
      synchronized (this) {
         ret = genericPeek(numOfEntries, numOfBytes, minPriority, maxPriority).list;
         for (int i=0; i < ret.size(); i++) {
            I_QueueEntry entry = (I_QueueEntry)ret.get(i);
            if (this.notifiedAboutAddOrRemove) {
               entry.removed(this.storageId);
            }
            if (this.storage.remove(entry)) {
               this.sizeInBytes -= entry.getSizeInBytes();
               if (entry.isDurable()) {
                  this.numOfDurableEntries--;
                  this.durableSizeInBytes -= entry.getSizeInBytes();
               }
            }
         }
      }
      return ret;
   }


   /**
    * @see I_Queue#takeLowest(int, long, I_QueueEntry, boolean)
    */
   public ArrayList takeLowest(int numOfEntries, long numOfBytes, I_QueueEntry limitEntry, boolean leaveOne)
      throws XmlBlasterException {

      synchronized(this) {
         LinkedList list = new LinkedList(this.storage);
         ListIterator iter = list.listIterator(list.size());
         long count = 0L;
         long currentSizeInBytes = 0L;
         long totalSizeInBytes = 0L;
         ArrayList ret = new ArrayList();

         // it leaves at least one entry in the list
         while (iter.hasPrevious() && (count<numOfEntries||numOfEntries<0) && (totalSizeInBytes < numOfBytes || numOfBytes <0)) {
            I_QueueEntry entry = (I_QueueEntry)iter.previous();
            currentSizeInBytes = entry.getSizeInBytes();
            totalSizeInBytes += entry.getSizeInBytes();

            if (limitEntry != null && this.comparator.compare(limitEntry, entry) >= 0) break;
            ret.add(entry);
            count++;
         }
         if (leaveOne && this.storage.size() == ret.size()) ret.remove(ret.size()-1);
         for (int i=0; i < ret.size(); i++) {
            // this.storage.removeAll(ret);
            I_QueueEntry entry =  (I_QueueEntry)ret.get(i);
            if (this.notifiedAboutAddOrRemove) {
               entry.removed(this.storageId);
            }
            if (this.storage.remove(entry)) {
               this.sizeInBytes -= entry.getSizeInBytes();
               if (entry.isDurable()) {
                  this.numOfDurableEntries--;
                  this.durableSizeInBytes -= entry.getSizeInBytes();
               }
            }
         }
         // this.sizeInBytes -= totalSizeInBytes;
         return ret;
      }
   }

   /**
    * Put a message into the queue, blocks if take thread blocks synchronize
    */
   public final Object put(I_QueueEntry entry, boolean ignorePutInterceptor)
      throws XmlBlasterException {
      if (entry == null) return null;

      if (isShutdown) {
         if (log.TRACE) log.trace(ME, "The queue is shutdown, put() of message " + entry.getUniqueId() + " failed");
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "The queue is shutdown, put() of message " + entry.getUniqueId() + " failed");
      }

      if ((this.putListener != null) && (!ignorePutInterceptor)) {
         // Is an interceptor registered?
         return this.putListener.put(entry);
      }

      if (getNumOfEntries() > property.getMaxMsg()) { // Allow superload one time only
         String reason = "Queue overflow (number of entries), " + property.getMaxMsg() +
                         " messages are in queue, try increasing '" +
                         this.property.getPropName("maxMsg") + "' on client login.";
         if (log.TRACE) log.trace(ME, reason);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
      }
      if (this.getNumOfBytes() > property.getMaxBytes()) { // Allow superload one time only
         String reason = "Queue overflow, " + this.getNumOfBytes() +
                         " bytes are in queue, try increasing '" + 
                         this.property.getPropName("maxBytes") + "' on client login.";
         if (log.TRACE) log.trace(ME, reason);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
      }

      synchronized(this) {
         if (!this.storage.contains(entry)) {
            if (this.storage.add(entry)) {
               this.sizeInBytes += entry.getSizeInBytes();
               if (entry.isDurable()) {
                  this.numOfDurableEntries++;
                  this.durableSizeInBytes += entry.getSizeInBytes();
               }
               if (this.notifiedAboutAddOrRemove) {
                  entry.added(this.storageId);
               }
            }
         }
         else {
            log.error(ME, "Ignoring IDENTICAL uniqueId=" + entry.getUniqueId());
            Thread.currentThread().dumpStack();
         }
      }
      return null;
   }

   /**
    * Put messages into the queue, blocks if take thread blocks synchronize
    */
   public final Object[] put(I_QueueEntry[] msgArr, boolean ignorePutInterceptor)
      throws XmlBlasterException {
      if (msgArr == null) return null;

      //if (log.CALL) log.call(ME, "Entering put(" + msgArr.length + ")");

      if (isShutdown) {
         if (log.TRACE) log.trace(ME, "The queue is shutdown, put() of " + msgArr.length + " messages failed");
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "The queue is shutdown, put() of " + msgArr.length + " messages failed");
      }

      // delegate put?
      if ((this.putListener != null) && (!ignorePutInterceptor)) {
         // Is an interceptor registered and it is not bypassed
         return this.putListener.put(msgArr);
      }

      if (getNumOfEntries() > property.getMaxMsg()) { // Allow superload one time only
         String reason = "Queue overflow (num of entries), " + property.getMaxMsg() +
                  " messages are in queue, try increasing '" + this.property.getPropName("maxMsg") + "' on client login.";
         if (log.TRACE) log.trace(ME, reason);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
      }
      if (this.getNumOfBytes() > property.getMaxBytes()) { // Allow superload one time only
         String reason = "Queue overflow, " + this.getNumOfBytes() + " bytes are in queue, try increasing '" +
                         this.property.getPropName("maxBytes") + "' on client login.";
         if (log.TRACE) log.trace(ME, reason);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
      }

      synchronized(this) {
//         this.storage.addAll(java.util.Arrays.asList(msgArr));
         for (int i=0; i < msgArr.length; i++) {
            I_QueueEntry entry = msgArr[i];
            if (!this.storage.contains(entry)) {
               if (this.storage.add(entry)) {
                  this.sizeInBytes += entry.getSizeInBytes();
                  if (entry.isDurable()) {
                     this.numOfDurableEntries++;
                     this.durableSizeInBytes += entry.getSizeInBytes();
                  }
                  if (this.notifiedAboutAddOrRemove) {
                     entry.added(this.storageId);
                  }
               }
            }
         }
      }
      return null;
   }
   
   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of RamQueuePlugin as a XML ASCII string
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<RamQueuePlugin id='").append(getStorageId());
      sb.append("' currMsgs='").append(getNumOfEntries()).append("' maxNumOfEntries='").append(getMaxNumOfEntries()).append("'>");
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
    * destroys all the resources associated to this queue.
    */
   public void destroy() throws XmlBlasterException {
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
      java.util.Properties props = pluginInfo.getParameters();
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
}


/**
 * Sorts the messages
 * <ol>
 *   <li>Priority</li>
 *   <li>Timestamp</li>
 * </ol>
 * @see org.xmlBlaster.util.queuemsg.MsgQueueEntry#compare(I_QueueEntry)
 */
class MsgComparator implements Comparator
{
   /**
    * Comparing the longs directly is 20% faster than having a
    * String compound key
    */
   public final int compare(Object o1, Object o2) {
      I_QueueEntry d1 = (I_QueueEntry)o1;
      I_QueueEntry d2 = (I_QueueEntry)o2;
      return d1.compare(d2);
   }
}

