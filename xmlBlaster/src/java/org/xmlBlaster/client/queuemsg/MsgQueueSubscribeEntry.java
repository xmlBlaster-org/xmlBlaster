/*------------------------------------------------------------------------------
Name:      MsgQueueSubscribeEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.queuemsg;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;


/**
 * Wraps an subscribe() message into an entry for a sorted queue.
 * @author xmlBlaster@marcelruff.info
 */
public final class MsgQueueSubscribeEntry extends MsgQueueEntry
{
   private final static String ME = "SubscribeQueueEntry";
   private final SubscribeQos subscribeQos;
   private final SubscribeKey subscribeKey;
   private SessionName receiver;
   private final long immutableSizeInBytes;

   /**
    * Use this constructor if a new message object is fed by method subscribe(). 
    * <p />
    */
   public MsgQueueSubscribeEntry(Global glob, StorageId storageId, 
                                 SubscribeKey subscribeKey, SubscribeQos subscribeQos)
         throws XmlBlasterException {
      super(glob, MethodName.SUBSCRIBE, PriorityEnum.NORM_PRIORITY, storageId,
            (subscribeQos == null) ? false : subscribeQos.getData().isPersistent());
      this.subscribeQos = (subscribeQos == null) ? new SubscribeQos(glob) : subscribeQos;
      this.subscribeKey = subscribeKey;
      this.immutableSizeInBytes = 567 + this.subscribeQos.getData().size() + this.subscribeKey.getData().size();
   }

   /**
    * For persistence recovery
    */
   public MsgQueueSubscribeEntry(Global glob, PriorityEnum priority, StorageId storageId,
                                Timestamp timestamp, long sizeInBytes,
                                SubscribeKey subscribeKey, SubscribeQos subscribeQos) {
      super(glob, MethodName.SUBSCRIBE.toString(), priority,
            timestamp, storageId, subscribeQos.getData().isPersistent());
      this.subscribeQos = subscribeQos;
      this.subscribeKey = subscribeKey;
      this.immutableSizeInBytes = sizeInBytes;
   }

   /**
    * @see MsgQueueEntry#isExpired
    */
   public boolean isExpired() {
      return false;
   }

   /**
    * @see MsgQueueEntry#isDestroyed
    */
   public boolean isDestroyed() {
      return false;
   }

   public SubscribeQos getSubscribeQos() {
      return this.subscribeQos;
   }

   public SubscribeKey getSubscribeKey() {
      return this.subscribeKey;
   }

   /**
    * Access the unique login name of the subscriber. 
    * @return loginName of the source
    * @see MsgQueueEntry#getSender()
    */
   public SessionName getSender() {
      return null;
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
      return this.receiver;
   }

   /**
    * @see MsgQueueEntry#getKeyOid()
    */
   public String getKeyOid() {
      return null;
   }

   /**
    * return null
    */
   public Timestamp getRcvTimestamp() {
      return null;
   }

   /**
    * The embeddded object for this implementing class is an Object[2] where
    * Object[0] = qos.toXml()
    * Object[1] = key.toXml()
    */
   public Object getEmbeddedObject() {
      Object[] obj = { this.subscribeQos.toXml(), this.subscribeKey.toXml() };
      return obj;
   }

   public long getSizeInBytes() {
      return this.immutableSizeInBytes;
   }

   /**
    * @return true
    */
   public boolean isInternal() {
      return true;
   }

   /**
    * Returns a shallow clone
    */
   public Object clone() {
      MsgQueueSubscribeEntry entry = null;
      entry = (MsgQueueSubscribeEntry)super.clone();
      return entry;
   }
}

