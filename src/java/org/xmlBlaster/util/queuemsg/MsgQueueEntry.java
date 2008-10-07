/*------------------------------------------------------------------------------
Name:      MsgQueueEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queuemsg;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.def.MethodName;

import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queue.jdbc.XBMeat;
import org.xmlBlaster.util.queue.jdbc.XBRef;


/**
 * Base class to enter xmlBlaster method invocations (messages) into an ordered queue.
 * @author xmlBlaster@marcelruff.info
 * @author michele@laghi.eu
 */
public abstract class MsgQueueEntry implements I_QueueEntry, Cloneable
{
   private final static String ME = "MsgQueueEntry";

   protected transient Global glob;
   private static Logger log = Logger.getLogger(MsgQueueEntry.class.getName());

   // Three helpers to transport the return value back to the caller
   protected transient boolean wantReturnObj = true;
   protected transient Object returnObj;
   protected transient MsgQueueEntry refToCloneOrigin;

   private String logId;

   /** The queue to which this entry belongs (set in the constructors) */
   protected final StorageId storageId;

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

   private transient boolean stored = false;
   //private transient boolean swapped = false;

   /** The queue to which this entry belongs (set in the constructors) */
   protected final String uniqueIdString;

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
      this.uniqueIdString = "" + this.uniqueIdTimestamp.getTimestamp();

      if (entryType == null || priority == null || glob == null || storageId ==null) {
         log.severe("Invalid constructor parameter");
         Thread.dumpStack();
         throw new IllegalArgumentException(ME + ": Invalid constructor parameter");
      }

      this.glob = glob;

      this.entryType = entryType;
      this.priority = priority;
      this.storageId = storageId;
      this.persistent = persistent;
   }

   public final void setGlobal(Global global) {
      this.glob = global;

   }


   public void finalize() {
      try {
         super.finalize();
         if (log != null && log.isLoggable(Level.FINE)) log.fine("finalize - garbage collect");
      }
      catch (Throwable e) {
         e.printStackTrace();
      }
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

   public void setPersistent(boolean persistent) {
      this.persistent = persistent;
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
    * Try to find out the approximate memory consumption of this message in RAM.
    * <p />
    * NOTE: The derived classes need to add their data amount to this value.
    * @return the approximate size in bytes of this object which contributes to a QueueEntry memory consumption
    */
   public long getSizeInBytes() {
      return 100; // a totally intuitive number for the object creation itself
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

      sb.append(offset).append("<MsgQueueEntry type='").append(entryType).append("'>");
      sb.append(offset).append("   <priority>").append(getPriority()).append("</priority>");
      sb.append(offset).append("   <persistent>").append(isPersistent()).append("</persistent>");
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
   //public Object getEmbeddedObject() {
   //   return this.getMsgUnit();
   //}


   /**
    * @return MethodName.xxx
    */
   public String getEmbeddedType() {
      return entryType;
   }

   /**
    * Notification if this entry is added to queue
    * @see org.xmlBlaster.util.queue.I_Entry#added(StorageId)
    */
   public void added(StorageId storageId) {
      log.info(getLogId() + " is added to queue: REFERENCE COUNTER IMPL MISSING");
   }

   /**
    * Notification if this entry is removed from queue
    * @see org.xmlBlaster.util.queue.I_Entry#removed(StorageId)
    */
   public void removed(StorageId storageId) {
      log.info(getLogId() + " is removed from queue: REFERENCE COUNTER IMPL MISSING");
   }

   /**
    * @return e.g. MethodName.PUBLISH
    * @see #getEmbeddedType()
    */
   public MethodName getMethodName() {
      return MethodName.toMethodName(this.entryType);
   }

   /**
    * @return true if the dispatcher framework shall provide a return object
    */
   public boolean wantReturnObj() {
      return this.wantReturnObj;
   }

   /**
    * sets the 'wantReturnObj' flag to what you specify (overwrites the default for
    * the implementing class).
    * @param wantReturnObj
    */
   public void setWantReturnObject(boolean wantReturnObj) {
      this.wantReturnObj = wantReturnObj;
   }

   /**
    * If this instance is a clone we can remember who cloned us. 
    */
   void setRefToCloneOrigin(MsgQueueEntry origEntry) {
      this.refToCloneOrigin = origEntry;
   }

   /**
    * @return returnObj The carried object used as return QoS in sync or async I_Queue.put() mode, can be null.
    */
   public Object getReturnObj() {
      return this.returnObj;
   }

   /**
    * Set the object to be carried as return value. 
    * NOTE: This can be used only once as the first call to this method
    * destroys the reference to the clone original instance.
    */
   public void setReturnObj(Object returnObj) {
      if (this.refToCloneOrigin != null) {
         this.refToCloneOrigin.setReturnObj(returnObj);
         this.refToCloneOrigin = null; // free reference so that GC can remove it if swapped
      }
      this.returnObj = returnObj;
   }

   /**
    * Returns a shallow clone. 
    * Is done by DispatchManager.prepareMsgsFromQueue() so that it can later encrypt
    * the message without touching the original
    */
   public Object clone() {
      MsgQueueEntry entry = null;
      try {
         entry = (MsgQueueEntry)super.clone();
         if (entry.wantReturnObj) {
            entry.setRefToCloneOrigin(this);
         }
      }
      catch(CloneNotSupportedException e) {
         log.severe("Internal clone problem: " + e.toString());
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
         sb.append("/").append(getKeyOid());
         this.logId = sb.toString();
      }
      return this.logId;
   }

   /**
    * @see org.xmlBlaster.util.queue.I_Entry#setStored(boolean)
    */
   final public void setStored(boolean stored) {
      this.stored = stored;
   }

   /**
    * @see org.xmlBlaster.util.queue.I_Entry#isStored()
    */
   final public boolean isStored() {
      return this.stored;
   }

   /**
    * 
    * @return null (always)
    */
   public MsgUnit getMsgUnit() throws XmlBlasterException {
      return null;
   }
   
   
   /**
    * For the new queues 
    */
   public XBMeat getMeat() throws XmlBlasterException {
      XBMeat meat = new XBMeat();
      meat.setByteSize(getSizeInBytes());
      meat.setDataType(getEmbeddedType());
      meat.setDurable(isPersistent());
      // meat.setFlag1(flag1);
      meat.setId(getUniqueId());
      MsgUnit unit = getMsgUnit();
      if (unit != null) {
         meat.setContent(unit.getContent());
         meat.setKey(unit.getKey());
         meat.setQos(unit.getQos());
      }
      meat.setRefCount(1);
      return meat;
   }

   /**
    * For the new queues 
    */
   public XBRef getRef() {
      XBRef ref = new XBRef();
      ref.setByteSize(getSizeInBytes());
      ref.setDurable(isPersistent());
      ref.setId(getUniqueId());
      ref.setMeatId(getUniqueId());
      ref.setPrio(getPriority());
      ref.setMethodName(entryType);
      return ref;
   }

   
   
}

