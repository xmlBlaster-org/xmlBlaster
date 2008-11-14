/*------------------------------------------------------------------------------
Name:      MsgQueueEraseEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.queuemsg;

import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;


/**
 * Wraps an erase() message into an entry for a sorted queue.
 * @author xmlBlaster@marcelruff.info
 */
public final class MsgQueueEraseEntry extends MsgQueueEntry
{
   private static final long serialVersionUID = 3720551167058323026L;
   private final EraseQos eraseQos;
   private final EraseKey eraseKey;
   private SessionName receiver;
   private final long immutableSizeInBytes;

   /**
    * Use this constructor if a new message object is fed by method erase(). 
    * <p />
    */
   public MsgQueueEraseEntry(Global glob, StorageId storageId, 
                                 EraseKey eraseKey, EraseQos eraseQos)
         throws XmlBlasterException {
      super(glob, MethodName.ERASE, PriorityEnum.MIN_PRIORITY, storageId, eraseQos.getData().isPersistent());
      this.eraseQos = eraseQos;
      this.eraseKey = eraseKey;
      /*  TODO: This check to be done only when in POLLING mode !!!!
      if (!eraseKey.isQuery()) {
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Only erase request with exact topic oid are supported in fail safe mode");
      }
      */
      this.immutableSizeInBytes = 567 + this.eraseQos.getData().size() + this.eraseKey.getData().size();
   }

   /**
    * For persistence recovery
    */
   public MsgQueueEraseEntry(Global glob, PriorityEnum priority, StorageId storageId,
                                Timestamp timestamp, long sizeInBytes,
                                EraseKey eraseKey, EraseQos eraseQos) {
      super(glob, MethodName.ERASE.toString(), priority,
            timestamp, storageId,
            (eraseQos == null) ? false : eraseQos.getData().isPersistent());
      this.eraseQos = (eraseQos == null) ? new EraseQos(glob) : eraseQos;
      this.eraseKey = eraseKey;
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

   public EraseQos getEraseQos() {
      return this.eraseQos;
   }

   public EraseKey getEraseKey() {
      return this.eraseKey;
   }

   public MsgUnit getMsgUnit() {
      return new MsgUnit(getEraseKey().getData(), null, getEraseQos().getData());
   }

   /**
    * Access the unique login name of the eraser. 
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
      Object[] obj = { this.eraseQos.toXml(), this.eraseKey.toXml() };
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
      out.write(this.eraseKey.toXml().getBytes());
      out.write(this.eraseQos.toXml(props).getBytes());
   }

   /**
    * Returns a shallow clone
    */
   public Object clone() {
      MsgQueueEraseEntry entry = null;
      entry = (MsgQueueEraseEntry)super.clone();
      return entry;
   }
}

