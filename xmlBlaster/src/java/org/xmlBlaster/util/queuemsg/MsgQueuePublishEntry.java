/*------------------------------------------------------------------------------
Name:      MsgQueuePublishEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queuemsg;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.queue.StorageId;

import java.util.ArrayList;

/**
 * Wraps an publish() message into an entry for a sorted queue.
 * @author laghi@swissinfo.org
 * @author xmlBlaster@marcelruff.info
 */
public final class MsgQueuePublishEntry extends MsgQueueEntry
{
   private final static String ME = "PublishQueueEntry";
   private final MsgQosData msgQosData;
   private SessionName receiver;
   /** The MsgUnit with key/content/qos (raw struct) */
   private MsgUnit msgUnit;

   /**
    * Use this constructor if a new message object is fed by method publish(). 
    * <p />
    * @param msgUnit The raw data
    */
   public MsgQueuePublishEntry(Global glob, MsgUnit msgUnit, StorageId storageId)
         throws XmlBlasterException {
      super(glob, MethodName.PUBLISH, ((MsgQosData)msgUnit.getQosData()).getPriority(), storageId, ((MsgQosData)msgUnit.getQosData()).isDurable());
      if (msgUnit == null) {
         glob.getLog("dispatch").error(ME, "Invalid constructor parameter");
         Thread.currentThread().dumpStack();
         throw new IllegalArgumentException(ME + ": Invalid constructor parameter");
      }
      this.msgUnit = msgUnit;
      this.msgQosData = (MsgQosData)msgUnit.getQosData();
   }

   /**
    * @see MsgQueueEntry#isExpired
    */
   public final boolean isExpired() {
      return this.msgQosData.isExpired();
   }

   /**
    * @see MsgQueueEntry#isDestroyed
    */
   public final boolean isDestroyed() {
      return false;
   }

   public final MsgQosData getMsgQosData() {
      return this.msgQosData;
   }

   /**
    * Get the message unit, you must call getUpdateQos(int,int,int) before to generate the update QoS.
    * <p />
    * See private getUpdateQos(int,int,int)
    */
   public final MsgUnit getMsgUnit() {
      return this.msgUnit;
   }

   /**
    * Get the message unit.
    */
   public final void setMsgUnit(MsgUnit msg) {
      this.msgUnit = msg;
   }

   /**
    * Try to find out the approximate memory consumption of this message.
    * <p />
    * It counts the message content bytes but NOT the xmlKey, xmlQoS etc. bytes<br />
    * This is because its used for the MessageQueue to figure out
    * how many bytes the client consumes in his queue.<p />
    *
    * As the key and qos will usually not change with follow up messages
    * (with same oid), the message only need to clone the content, and
    * that is quoted for the client.<p />
    *
    * Note that when the same message is queued for many clients, the
    * server load is duplicated for each client (needs to be optimized).<p />
    *
    * @return the approximate size in bytes of this object which contributes to a ClientUpdateQueue memory consumption
    */
   public final long getSizeInBytes() {
      long size = super.getSizeInBytes();
      if (this.msgUnit != null) {
         size += this.msgUnit.getContent().length;
         //size += this.msgUnit.size();
         // These are references on the original MsgQueueEntry and consume almost no memory:
         // size += xmlKey;
         // size += uniqueKey.size() + objectHandlingBytes;
      }
      return size;
   }

   /**
    * @return If it is an internal message (oid starting with "_"). 
    */
   public boolean isInternal() {
      return (getMsgKeyData().isInternal() || getMsgKeyData().isPluginInternal());
   }

   /**
    * Access the unique login name of the (last) publisher.
    * <p />
    * The sender of this message.
    * @return loginName of the data source which last publishd this message
    *         or null
    * @see MsgQueueEntry#getSender()
    */
   public final SessionName getSender() {
      return this.msgQosData.getSender();
   }

   /**
    * @return The name of the receiver (data sink) or null
    * @see MsgQueueEntry#getReceiver()
    */
   public final void setReceiver(SessionName receiver) {
      this.receiver = receiver;
   }

   /**
    * @return The name of the receiver (data sink) or null
    * @see MsgQueueEntry#getReceiver()
    */
   public final SessionName getReceiver() {
      if (this.receiver == null) {
         ArrayList list = this.msgQosData.getDestinations();
         if (list != null && list.size() >0) {
            this.receiver = (SessionName)list.get(0);
            if (list.size() > 1)
               log.warn(ME, "Ignoring other receivers with getReceiver()");
         }
      }
      return this.receiver;
   }

   public final MsgKeyData getMsgKeyData() {
      return (MsgKeyData)getMsgUnit().getKeyData();
   }

   /**
    * @see MsgQueueEntry#getKeyOid()
    */
   public final String getKeyOid() {
      return getMsgKeyData().getOid();
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
    */
   public Object getEmbeddedObject() {
//      Object[] obj = { new Boolean(subscribeable) };
//      return obj;
      log.error(ME, "getEmbeddedObject() IMPLEMENT");
      throw new IllegalArgumentException("getEmbeddedObject() IMPLEMENT");
   }

   /**
    * Returns a shallow clone
    */
   public Object clone() {
      MsgQueuePublishEntry entry = null;
      entry = (MsgQueuePublishEntry)super.clone();
      return entry;
   }
}

