/*------------------------------------------------------------------------------
Name:      MsgQueueEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queuemsg;

import org.jutils.log.LogChannel;
// import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.enum.MethodName;

import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_QueueEntry;


/**
 * Base class to enter xmlBlaster method invocations (messages) into an ordered queue.
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */
public abstract class MsgQueueEntry implements I_QueueEntry, Cloneable
{
   private final static String ME = "MsgQueueEntry";

   protected transient Global glob;
   protected transient LogChannel log;

   private String logId;

   /** The queue to which this entry belongs (set in the constructors) */
   private final StorageId storageId;

   /** How often the entry was tried to send but failed */
   protected int redeliverCounter = 0;

   /** The unique creation timestamp (unique in a Global of a virtual machine) */
   protected final Timestamp uniqueIdTimestamp;

   /** The message priority, see Constants.java */
   protected final PriorityEnum priority;

   /** the flag telling if a message is persistent (opposite to transient) */
   protected boolean persistent;

   /** Which method we invoke, e.g. "update" or "publish" */
   protected final String entryType;

   /**
    * @param methodName use methodName as entryType
    */
   public MsgQueueEntry(Global glob, MethodName methodName, PriorityEnum priority, StorageId storageId, boolean persistent) {
      this(glob, methodName.toString(), priority, (Timestamp)null, storageId, persistent);
   }

   /**
    * Creates a new queue entry object. 
    * @param priority The message priority
    * @param storageId The queue i belong to
    * @see org.xmlBlaster.util.Timestamp
    */
   public MsgQueueEntry(Global glob, String entryType, PriorityEnum priority, StorageId storageId, boolean persistent) {
      this(glob, entryType, priority, (Timestamp)null, storageId, persistent);
   }

   /**
    * This constructor is for internal creation from persistence only. 
    *
    * @param timestamp The unique nano timestamp as from org.xmlBlaster.util.Timestamp or null to create one now
    */
   public MsgQueueEntry(Global glob, String entryType, PriorityEnum priority, Timestamp timestamp, StorageId storageId, boolean persistent) {
      this.uniqueIdTimestamp = (timestamp == null) ? new Timestamp() : timestamp;

      if (entryType == null || priority == null || glob == null || storageId ==null) {
         glob.getLog("dispatch").error(ME, "Invalid constructor parameter");
         Thread.currentThread().dumpStack();
         throw new IllegalArgumentException(ME + ": Invalid constructor parameter");
      }

      this.glob = glob;
      this.log = glob.getLog("dispatch");
      this.entryType = entryType;
      this.priority = priority;
      this.storageId = storageId;
      this.persistent = persistent;
   }

   public final void setGlobal(Global global) {
      this.glob = global;
      this.log = glob.getLog("dispatch");
   }


   public void finalize() {
      if (log.TRACE) log.trace(ME, "finalize - garbage collect");
   }

   /**
    * Enforced by I_QueueEntry
    * @return The priority of this message as int (see PriorityEnum.java)
    */
   public int getPriority() {
      return this.priority.getInt();
   }

   /**
    * @return The priority of this message
    */
   public final PriorityEnum getPriorityEnum() {
      return this.priority;
   }

   /**
    * @return The persistent flag of this message
    */
   public boolean isPersistent() {
      return this.persistent;
   }

   /**
    * The unique creation timestamp (unique in a Global of a virtual machine)
    * Enforced by I_QueueEntry
    * @param nano seconds
    */
   public long getUniqueId() {
      return this.uniqueIdTimestamp.getTimestamp();
   }

   /**
    * The unique creation timestamp (unique in a Global of a virtual machine)
    * @param nano seconds
    */
   public Long getUniqueIdLong() {
      return this.uniqueIdTimestamp.getTimestampLong();
   }

   /**
    * Flag which marks the entry as outdated
    */
   public abstract boolean isExpired();

   /**
    * Flag which marks the entry as destroyed, you should take it from queue and ignore/discard it
    */
   public abstract boolean isDestroyed();

   /**
    * @return If it is an internal message (oid starting with "_"). 
    */
   public abstract boolean isInternal();

   /**
    * @return The name of the sender (data source) or null
    */
   public abstract SessionName getSender();

   /**
    * @return The name of the receiver (data sink) or null
    */
   public abstract SessionName getReceiver();

   /**
    * @return The message key oid or null
    */
   public abstract String getKeyOid();

   /**
    * To which queue do i belong
    */
   public final StorageId getStorageId() {
      return this.storageId;
   }

   /**
    * Increment the counter if message delivery fails (exception during sending)
    * We don't know if other side has processed it completely or not
    */
   public final void incrRedeliverCounter() {
      this.redeliverCounter++;
   }

   /**
    * How often we tried to redeliver the message.
    * <p>
    * Note: Depending on the derived class implementation this information
    * is lost on server crash (the redeliver counter
    * is not persistent). Only MsgQueueUpdateEntry persists it.
    * </p>
    * @return Number of failed tries
    */
   public final int getRedeliverCounter() {
      return this.redeliverCounter;
   }

   /**
    * Try to find out the approximate memory consumption of this message.
    * <p />
    * NOTE: The derived classes need to add their data amount to this value.
    * @return the approximate size in bytes of this object which contributes to a QueueEntry memory consumption
    */
   public long getSizeInBytes() {
      return 20; // a totally intuitive number for the object creation itself
   }

   /**
    * Needed for sorting the queue.
    * Comparing the longs directly is 20% faster than having a
    * String compound key
    * <br /><br />
    * Sorts the messages
    * <ol>
    *   <li>Priority</li>
    *   <li>Timestamp</li>
    * </ol>
    * <p>
    * The sorting order is priority,timestamp:
    * </p>
    * <pre>
    *   ->    5,100 - 5,98 - 5,50 - 9,3000 - 9,2500   ->
    * </pre>
    * <p>
    * As 9 is highest priority it is the first to be taken out.<br />
    * As we need to maintain the timely sequence and
    * id is a timestamp in (more or less) nano seconds elapsed since 1970)
    * the id 2500 (it is older) has precedence to the id 3000
    * </p>
    */
   public final int compare(I_QueueEntry m2) {
      //log.info(ME, "Entering compare A=" + getUniqueId() + " B=" + m2.getUniqueId());
      int diff = m2.getPriority() - getPriority();
      if (diff != 0) // The higher prio wins
         return diff;

      long dif = m2.getUniqueId() - getUniqueId();
      if (dif < 0L)
         return 1;  // The older message wins
      else if (dif > 0L)
         return -1;
      return 0;

      //System.out.println("Comparing " + getSortKey() + " : " + d2.getSortKey());
      // return getSortKey().compareTo(d2.getSortKey());
   }

   /**
    * Needed for sorting in queue
    */
   public final boolean equals(I_QueueEntry m2) {
      return (getPriority() == m2.getPriority() && getUniqueId() == m2.getUniqueId());
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of MsgQueueEntry
    */
   public String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of MsgQueueEntry
    */
   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(200);
      String offset = "\n   ";
      if (extraOffset != null) offset += extraOffset;

      sb.append(offset).append("<MsgQueueEntry>");
      sb.append(offset).append("   <priority>").append(getPriority()).append("</priority>");
      sb.append(offset).append("   <uniqueId>").append(getUniqueId()).append("</uniqueId>");
      sb.append(offset).append("   <sender>").append(getSender()).append("</sender>");
      sb.append(offset).append("   <receiver>").append(getReceiver()).append("</receiver>");
      sb.append(offset).append("   <expired>").append(isExpired()).append("</expired>");
      sb.append(offset).append("   <isInternal>").append(isInternal()).append("</isInternal>");
      sb.append(offset).append("</MsgQueueEntry>\n");
      return sb.toString();
   }

   /**
    * Nice for logging
    * @see #getLogId()
    */
   public String toString() {
      return getLogId();
      /*
      StringBuffer sb = new StringBuffer(200);
      sb.append("MsgQueueEntry sender=").append(getSender());
      sb.append(" receiver=").append(getReceiver());
      sb.append(" priority=").append(getPriority());
      sb.append(" uniqueId=").append(getUniqueId());
      sb.append(" expired=").append(isExpired());
      return sb.toString();
      */
   }

   /**
    * @see I_QueueEntry.getEmbeddedObject()
    */
//   public Object getEmbeddedObject() {
//      return this.getMsgUnit();
//   }


   /**
    * @return e.g. "publish"
    * @see I_QueueEntry#getEmbeddedType()
    */
   public String getEmbeddedType() {
      return entryType;
   }

   /**
    * Notification if this entry is added to queue
    * @see org.xmlBlaster.util.queue.I_Entry#added(StorageId)
    */
   public void added(StorageId storageId) throws XmlBlasterException {
      log.info(ME, getLogId() + " is added to queue: REFERENCE COUNTER IMPL MISSING");
   }

   /**
    * Notification if this entry is removed from queue
    * @see org.xmlBlaster.util.queue.I_Entry#removed(StorageId)
    */
   public void removed(StorageId storageId) throws XmlBlasterException {
      log.info(ME, getLogId() + " is removed from queue: REFERENCE COUNTER IMPL MISSING");
   }

   /**
    * @return e.g. MethodName.PUBLISH
    * @see #getEmbeddedType()
    */
   public MethodName getMethodName() {
      return MethodName.toMethodName(this.entryType);
   }

   /**
    * @return Constants.UPDATE = "update" etc in the derived classes
    * @see I_QueueEntry.getEmbeddedType()
    */
//   public String getEmbeddedType() {
//      return ME;
//   }

   /**
    * Returns a shallow clone
    */
   public Object clone() {
      MsgQueueEntry entry = null;
      try {
         entry = (MsgQueueEntry)super.clone();
      }
      catch(CloneNotSupportedException e) {
         log.error(ME, "Internal clone problem: " + e.toString());
      }
      return entry;
   }

   /**
    * Return a human readable identifier for logging output.
    * @return e.g. "callback:/node/heron/client/joe/2/17/HIGH/23455969/TheMessageOid"
    */
   public final String getLogId() {
      if (this.logId == null) {
         StringBuffer sb = new StringBuffer(80);
         sb.append(getStorageId());
         sb.append("/").append(priority);
         //sb.append("/").append(priority.toString());
         sb.append("/").append(getUniqueId());
         //sb.append("/").append(getKeyOid());
         this.logId = sb.toString();
      }
      return this.logId;
   }
}

