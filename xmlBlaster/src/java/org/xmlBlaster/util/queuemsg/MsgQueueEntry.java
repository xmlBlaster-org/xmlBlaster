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

import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queue.I_Queue;


/**
 * Base class to enter xmlBlaster method invocations (messages) into an ordered queue.
 * @author ruff@swand.lake.de
 * @author laghi@swissinfo.org
 */
public abstract class MsgQueueEntry implements I_QueueEntry, Cloneable
{
   private final static String ME = "MsgQueueEntry";

   protected transient Global glob;
   protected transient LogChannel log;

   /** The queue to which this entry belongs (set in the constructors) */
   private transient I_Queue ownerQueue;

   /** How often the entry was tried to send but failed */
   private int redeliverCounter = 0;

   /** The unique creation timestamp (unique in a Global of a virtual machine) */
   private final Timestamp uniqueIdTimestamp;

   /** The message priority, see Constants.java */
   protected final PriorityEnum priority;

   /** the flag telling if a message is durable (opposite to transient) */
   protected boolean durable;

   /** Which method we invoke, e.g. "update" or "publish" */
   protected final MethodName methodName;

   /**
    * Creates a new queue entry object. 
    * @param priority The message priority
    * @param queue The queue i belong to
    * @see org.xmlBlaster.util.Timestamp
    */
   public MsgQueueEntry(Global glob, MethodName methodName, PriorityEnum priority, I_Queue queue, boolean durable) {
      this(glob, methodName, priority, null, queue, durable);
   }

   /**
    * This constructor is for internal creation from persistence only. 
    *
    * @param timestamp The unique nano timestamp as from org.xmlBlaster.util.Timestamp or null to create one now
    */
   public MsgQueueEntry(Global glob, MethodName methodName, PriorityEnum priority, Timestamp timestamp, I_Queue queue, boolean durable) {
      this.uniqueIdTimestamp = (timestamp == null) ? new Timestamp() : timestamp;

      if (methodName == null || priority == null || glob == null || queue ==null) {
         glob.getLog("dispatch").error(ME, "Invalid constructor parameter");
         Thread.currentThread().dumpStack();
         throw new IllegalArgumentException(ME + ": Invalid constructor parameter");
      }

      this.glob = glob;
      this.log = glob.getLog("dispatch");
      this.methodName = methodName;
      this.priority = priority;
      this.ownerQueue = queue;
      this.durable = durable;
      if (log.TRACE) log.trace(ME+"-/client/"+this.ownerQueue.getQueueId(), "Creating new MsgQueueEntry for published message, id=" + getUniqueId());
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
   public final int getPriority() {
      return this.priority.getInt();
   }

   /**
    * @return The priority of this message
    */
   public final PriorityEnum getPriorityEnum() {
      return this.priority;
   }

   /**
    * @return The isDurable flag of this message
    */
   public boolean isDurable() {
      return this.durable;
   }

   /**
    * The unique creation timestamp (unique in a Global of a virtual machine)
    * Enforced by I_QueueEntry
    * @param nano seconds
    */
   public final long getUniqueId() {
      return this.uniqueIdTimestamp.getTimestamp();
   }

   /**
    * Flag which marks the entry as outdated
    */
   public abstract boolean isExpired();

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
    /*
   public final I_Queue getMsgQueue() {
      return this.ownerQueue;
   }
      */
   /**
    * Increment the counter if message delivery fails (exception during sending)
    * We don't know if other side has processed it completely or not
    */
   public final void incrRedeliver() {
      this.redeliverCounter++;
   }

   /**
    * How often we tried to redeliver the message.
    * <p>
    * Note: This information is lost on server crash (the redeliver counter
    * is not persistent).
    * </p>
    * @return How often delivery of this message is not known
    */
   public final int getRedeliver() {
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
//      return this.getMessageUnit();
//   }


   /**
    * @return e.g. "publish" from MethodName.PUBLISH
    * @see I_QueueEntry#getEmbeddedType()
    */
   public String getEmbeddedType() {
      return methodName.toString();
   }

   /**
    * @return e.g. MethodName.PUBLISH
    * @see #getEmbeddedType()
    */
   public MethodName getMethodName() {
      return methodName;
   }

   /**
    * @return Constants.UPDATE = "update" etc in the derived classes
    * @see I_QueueEntry.getEmbeddedType()
    */
//   public String getEmbeddedType() {
//      return ME;
//   }

   /**
    * Set the queue, usually called if recovered from persistent store.
    */
   public void setQueue(I_Queue queue) {
      this.ownerQueue = queue;
   }

   /**
    * Returns a shallow clone
    */
   public Object clone() {
      MsgQueueEntry entry = null;
      try {
         entry = (MsgQueueEntry)super.clone();
      }
      catch(CloneNotSupportedException e) {
         log.error(ME, "Internal problem: " + e.toString());
      }
      return entry;
   }

   /**
    * Return a human readable identifier for logging output.
    * @return e.g. "callback:/node/heron/client/joe/2/17/HIGH/23455969/TheMessageOid"
    */
   public String getLogId() {
      StringBuffer sb = new StringBuffer(80);
      sb.append(ownerQueue.getQueueId());
      sb.append("/").append(priority);
      //sb.append("/").append(priority.toString());
      sb.append("/").append(getUniqueId());
      sb.append("/").append(getKeyOid());
      return sb.toString();
   }
}

