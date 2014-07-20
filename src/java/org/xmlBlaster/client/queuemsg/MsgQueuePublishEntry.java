/*------------------------------------------------------------------------------
Name:      MsgQueuePublishEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.queuemsg;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;

/**
 * Wraps an publish() message into an entry for a sorted queue.
 * @author michele@laghi.eu
 * @author xmlBlaster@marcelruff.info
 */
public final class MsgQueuePublishEntry extends MsgQueueEntry
{
   /**
    * 
    */
   private static final long serialVersionUID = 1L;
   private static Logger log = Logger.getLogger(MsgQueuePublishEntry.class.getName());
   private final static String ME = "PublishQueueEntry";
   private final MsgQosData msgQosData;
   private SessionName receiver;
   /** The MsgUnit with key/content/qos (raw struct) */
   private MsgUnit msgUnit;
   private final long immutableSizeInBytes;

   /**
    * Use this constructor if a new message object is fed by method publish() (not oneway). 
    * <p />
    * @param msgUnit The raw data
    */
   public MsgQueuePublishEntry(Global glob, MsgUnit msgUnit, StorageId storageId) throws XmlBlasterException {
      this(glob, msgUnit, storageId, false);
   }

   public MsgQueuePublishEntry(Global glob, MsgUnit msgUnit, StorageId storageId, boolean oneway)
         throws XmlBlasterException {
      super(glob, oneway ? MethodName.PUBLISH_ONEWAY : MethodName.PUBLISH,
            ((MsgQosData)msgUnit.getQosData()).getPriority(), storageId, ((MsgQosData)msgUnit.getQosData()).isPersistent());
      if (msgUnit == null) {
         log.severe("Invalid constructor parameter");
         Thread.dumpStack();
         throw new IllegalArgumentException(ME + ": Invalid constructor parameter");
      }
      if (log.isLoggable(Level.FINER)) log.finer("Created: " + getUniqueId());
      this.msgUnit = msgUnit;
      this.msgQosData = (MsgQosData)msgUnit.getQosData();

      // Estimated calculation of used memory by one MsgUnitWrapper instance
      // = Object memory + payload
      // Where following objects need to be created (approx. 660 bytes RAM):
      // 6 PropBoolean
      // 1 PropLong
      // 1 Timestamp
      // 1 MsgQosData
      // 1 MsgKeyData
      // 1 MsgUnit
      // 1 MsgQueuePublishEntry
      this.immutableSizeInBytes = 660 + this.msgUnit.size();
   }

   /**
    * For persistence recovery
    * @param priority PriorityEnum.MIN1_PRIORITY etc does not work, the priority from qos is used (remove this parameter)
    * @param sizeInByte The estimated size of the entry in RAM (can be totally different on HD). 
    */
   public MsgQueuePublishEntry(Global glob, MethodName entryType, PriorityEnum priority, StorageId storageId,
                               Timestamp publishEntryTimestamp, long sizeInBytes,
                               MsgUnit msgUnit) {
      super(glob, entryType.toString(), ((MsgQosData)msgUnit.getQosData()).getPriority(),
            publishEntryTimestamp, storageId, ((MsgQosData)msgUnit.getQosData()).isPersistent());
//      if (msgUnit == null) {
//         log.severe("Invalid constructor parameter");
//         Thread.dumpStack();
//         throw new IllegalArgumentException(ME + ": Invalid constructor parameter");
//      }
      this.msgUnit = msgUnit;
      this.msgQosData = (MsgQosData)msgUnit.getQosData();
      this.immutableSizeInBytes = sizeInBytes;
      if (log.isLoggable(Level.FINER)) log.finer("Created from persistence: " + getUniqueId());
   }

   /**
    * @see MsgQueueEntry#isExpired
    */
   public boolean isExpired() {
      return this.msgQosData.isExpired();
   }

   /**
    * @see MsgQueueEntry#isDestroyed
    */
   public boolean isDestroyed() {
      return false;
   }

   /**
    * Get the message unit, you must call getUpdateQos(int,int,int) before to generate the update QoS.
    * <p />
    * See private getUpdateQos(int,int,int)
    */
   public MsgUnit getMsgUnit() {
      return this.msgUnit;
   }

   /**
    * Try to find out the approximate memory consumption of this message.
    * <p />
    * @return The size in bytes
    */
   public long getSizeInBytes() {
      return this.immutableSizeInBytes;
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
   public SessionName getSender() {
      return this.msgQosData.getSender();
   }

   /**
    * @return The name of the receiver (data sink) or null
    * @see MsgQueueEntry#getReceiver()
    */
   public void setReceiver(SessionName receiver) {
      this.receiver = receiver;
   }

   /**
    * @return The name of the receiver (data sink) or null
    * @see MsgQueueEntry#getReceiver()
    */
   public SessionName getReceiver() {
      if (this.receiver == null) {
         ArrayList list = this.msgQosData.getDestinations();
         if (list != null && list.size() >0) {
            Destination d = (Destination) list.get(0);
            this.receiver = d.getDestination();
            if (list.size() > 1)
               log.warning("Ignoring other receivers with getReceiver()");
         }
      }
      return this.receiver;
   }

   public MsgKeyData getMsgKeyData() {
      return (MsgKeyData)getMsgUnit().getKeyData();
   }

   /**
    * @see MsgQueueEntry#getKeyOid()
    */
   public String getKeyOid() {
      return getMsgKeyData().getOid();
   }

   /**
    * The embedded object. 
    * @return qos.toXml, key.toXml, contentBytes
    */
   public Object getEmbeddedObject() {
      Object[] obj = { this.msgUnit.getQosData().toXml(),
                       this.msgUnit.getKeyData().toXml(),
                       this.msgUnit.getContent() };
      return obj;
   }

   public final void embeddedObjectToXml(java.io.OutputStream out, java.util.Properties props) throws java.io.IOException {
      MsgUnit msgUnit = this.msgUnit;
      if (msgUnit != null)
         msgUnit.toXml(out, props);
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

