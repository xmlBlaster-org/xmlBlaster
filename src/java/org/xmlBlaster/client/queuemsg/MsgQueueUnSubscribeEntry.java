/*------------------------------------------------------------------------------
Name:      MsgQueueUnSubscribeEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.queuemsg;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;


/**
 * Wraps an unSubscribe() message into an entry for a sorted queue.
 * @author xmlBlaster@marcelruff.info
 */
public final class MsgQueueUnSubscribeEntry extends MsgQueueEntry
{
   private static final long serialVersionUID = 1L;
   private final static String ME = "UnSubscribeQueueEntry";
   private final UnSubscribeQos unSubscribeQos;
   private final UnSubscribeKey unSubscribeKey;
   private SessionName receiver;
   private final long immutableSizeInBytes;

   /**
    * Use this constructor if a new message object is fed by method unSubscribe(). 
    * <p />
    * @param unSubscribeQos
    */
   public MsgQueueUnSubscribeEntry(Global glob, StorageId storageId, 
                                 UnSubscribeKey unSubscribeKey, UnSubscribeQos unSubscribeQos)
         throws XmlBlasterException {
      super(glob, MethodName.UNSUBSCRIBE, PriorityEnum.NORM_PRIORITY, storageId,
            (unSubscribeQos == null) ? false : unSubscribeQos.getData().isPersistent());
      this.unSubscribeQos = (unSubscribeQos == null) ? new UnSubscribeQos(glob) : unSubscribeQos;
      if (unSubscribeKey == null) throw new IllegalArgumentException(ME + " UnSubscribeKey is null");
      this.unSubscribeKey = unSubscribeKey;
      this.immutableSizeInBytes = 567 + this.unSubscribeQos.getData().size() + this.unSubscribeKey.getData().size();
   }

   /**
    * For persistence recovery
    */
   public MsgQueueUnSubscribeEntry(Global glob, PriorityEnum priority, StorageId storageId,
                                Timestamp timestamp, long sizeInBytes,
                                UnSubscribeKey unSubscribeKey, UnSubscribeQos unSubscribeQos) {
      super(glob, MethodName.UNSUBSCRIBE.toString(), priority,
            timestamp, storageId, unSubscribeQos.getData().isPersistent());
      this.unSubscribeQos = unSubscribeQos;
      this.unSubscribeKey = unSubscribeKey;
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

   public UnSubscribeQos getUnSubscribeQos() {
      return this.unSubscribeQos;
   }

   public UnSubscribeKey getUnSubscribeKey() {
      return this.unSubscribeKey;
   }

   /**
    * Access the unique login name of the unSubscriber. 
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
      Object[] obj = { this.unSubscribeQos.toXml(), this.unSubscribeKey.toXml() };
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

   public final void embeddedObjectToXml(java.io.OutputStream out, java.util.Properties props) throws java.io.IOException {
      out.write(this.unSubscribeKey.toXml().getBytes());
      out.write(this.unSubscribeQos.toXml(props).getBytes());
   }
	   
   /**
    * Returns a shallow clone
    */
   public Object clone() {
      MsgQueueUnSubscribeEntry entry = null;
      entry = (MsgQueueUnSubscribeEntry)super.clone();
      return entry;
   }
}

