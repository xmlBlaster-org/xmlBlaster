/*------------------------------------------------------------------------------
Name:      MsgQueueUpdateEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queuemsg;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.engine.helper.Constants;

/**
 * Wraps an update() message into an entry for a sorted queue.
 * @author laghi@swissinfo.org
 * @author ruff@swand.lake.de
 */
public final class MsgQueueUpdateEntry extends MsgQueueEntry
{
   private final static String ME = "UpdateQueueEntry";
   private final String oid;
   private final SessionName receiver;
   private final MsgQosData msgQosData;
   /** The MessageUnit with key/content/qos (raw struct) */
   private MessageUnit msgUnit;

   /**
    * Use this constructor if a new PtP message object is fed by method publish() and
    * the sessionInfo is not yet known (possibly user is not logged in).
    * <p />
    * @param receiverSubjectInfo Destinationsubject
    * @param msgUnitWrapper contains the parsed message
    */
   public MsgQueueUpdateEntry(Global glob, MessageUnit msgUnit, I_Queue queue, String oid, MsgQosData msgQosData, SessionName receiver)
         throws XmlBlasterException {
      this(glob, (Timestamp)null, msgUnit, queue, oid, msgQosData, receiver);
   }

   /**
    * Use this constructor to deserialize (since it must not create a new
    * timestamp.
    * <p />
    * @param receiverSubjectInfo Destinationsubject
    * @param msgUnitWrapper contains the parsed message
    */
   public MsgQueueUpdateEntry(Global glob, Timestamp timestamp, MessageUnit msgUnit, I_Queue queue, String oid, MsgQosData msgQosData, SessionName receiver)
         throws XmlBlasterException {
      super(glob, MethodName.UPDATE, msgQosData.getPriority(), timestamp, queue, msgQosData.isDurable());
      this.oid = oid;
      this.msgUnit = msgUnit;
      this.msgQosData = msgQosData;
      this.receiver = receiver;
   }


   /**
    * @see MsgQueueEntry#isExpired
    */
   public final boolean isExpired() {
      return this.msgQosData.isExpired();
   }


   public final MsgQosData getMsgQosData() {
      return this.msgQosData;
   }

   /**
    * Get the message unit, you must call getUpdateQos(int,int,int) before to generate the update QoS.
    * <p />
    * See private getUpdateQos(int,int,int)
    */
   public final MessageUnit getMessageUnit() {
      return this.msgUnit;
   }

   /**
    * Get the message unit.
    */
   public final void setMessageUnit(MessageUnit msg) {
      this.msgUnit = msg;
   }

   /**
    * Access the unique login name of the (last) publisher.
    * <p />
    * The sender of this message.
    * @return loginName of the data source which last updated this message
    *         or null
    * @see MsgQueueEntry#getSender()
    */
   public final SessionName getSender() {
      return this.msgQosData.getSender();
   }

   /**
    * @return The name of the receiver (data sink) or null,
    *         this is the callback queue owner which subscribed or got a PtP message
    * @see MsgQueueEntry#getReceiver()
    */
   public final SessionName getReceiver() {
      return this.receiver;
   }

   /**
    * @see MsgQueueEntry#getKeyOid()
    */
   public final String getKeyOid() {
      return this.oid;
   }

   /**
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In nanoseconds elapsed since midnight, January 1, 1970 UTC
    */
   public final Timestamp getRcvTimestamp() {
      return this.msgQosData.getRcvTimestamp();
   }

   /**
    * The embeddded object for this implementing class is an Object[3] where
    * Object[2] = Boolean (embedds boolean pubSub)
    */
   public Object getEmbeddedObject() {
      Object[] obj = new Object[4];
      obj[0] = this.oid;
      obj[1] = this.getMessageUnit();
      obj[2] = this.msgQosData;
      obj[3] = this.receiver;

      return obj;
   }

   /**
    * Try to find out the approximate memory consumption of this message.
    * <p />
    * The update entry is a reference to the publish entry only and does
    * not transport the message itself.
    *
    * @return the approximate size in bytes of this object which contributes to a UpdateQueueEntry memory consumption
    */
   public final long getSizeInBytes() {
      long size = super.getSizeInBytes();
      size += 8; // correct it !!!
      return size;
   }

   /**
    * @return If it is an internal message (oid starting with "_"). 
    */
   public boolean isInternal() {
      return (getMessageUnit().getMsgKeyData().isInternal() || getMessageUnit().getMsgKeyData().isPluginInternal());
   }

   /**
    * Returns a shallow clone
    */
   public Object clone() {
      MsgQueueUpdateEntry entry = null;
      entry = (MsgQueueUpdateEntry)super.clone();
      return entry;
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the message QoS as a XML ASCII string
    */
   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(1024);
      String offset = (extraOffset != null) ? "\n " + extraOffset : "\n ";
      extraOffset = (extraOffset != null) ? extraOffset + " "  : " ";
      sb.append(offset).append("<MsgQueueUpdateEntry id='").append(getLogId()).append("'>");
      sb.append(offset).append(getMessageUnit().toXml(extraOffset+" "));
      sb.append(offset).append(" <MsgQosData>").append(msgQosData.toXml(extraOffset+"  ")).append("</MsgQosData>");
      sb.append(offset).append(" <receiver>").append(getReceiver()).append("</receiver>");
      sb.append(offset).append("</MsgQueueUpdateEntry>");
      return sb.toString();
   }
}

