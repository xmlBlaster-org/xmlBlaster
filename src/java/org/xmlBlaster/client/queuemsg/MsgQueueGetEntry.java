/*------------------------------------------------------------------------------
Name:      MsgQueueGetEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.queuemsg;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;


/**
 * Wraps an get() message into an entry for a sorted queue.
 * @author xmlBlaster@marcelruff.info
 */
public final class MsgQueueGetEntry extends MsgQueueEntry
{
   private final static String ME = "GetQueueEntry";
   private final GetQos getQos;
   private final GetKey getKey;
   private SessionName receiver;
   private final long immutableSizeInBytes;

   /**
    * Use this constructor for a get() request. 
    * <p />
    */
   public MsgQueueGetEntry(Global glob, StorageId storageId, 
                                 GetKey getKey, GetQos getQos)
         throws XmlBlasterException {
      super(glob, MethodName.GET, PriorityEnum.NORM_PRIORITY, storageId, getQos.getData().isPersistent());
      this.getQos = getQos;
      this.getKey = getKey;
      this.immutableSizeInBytes = 500 + this.getQos.getData().size() + this.getKey.getData().size();
   }

   /**
    * For persistence recovery
    */
   public MsgQueueGetEntry(Global glob, PriorityEnum priority, StorageId storageId,
                                Timestamp timestamp, long sizeInBytes,
                                GetKey getKey, GetQos getQos) {
      super(glob, MethodName.GET.toString(), priority,
            timestamp, storageId,
            (getQos == null) ? false : getQos.getData().isPersistent());
      this.getQos = (getQos == null) ? new GetQos(glob) : getQos;
      this.getKey = getKey;
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

   public GetQos getGetQos() {
      return this.getQos;
   }

   public GetKey getGetKey() {
      return this.getKey;
   }

   /**
    * Access the unique login name of the getr. 
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
      Object[] obj = { this.getQos.toXml(), this.getKey.toXml() };
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
      out.write(this.getKey.toXml().getBytes());
      out.write(this.getQos.toXml(props).getBytes());
   }

   /**
    * Returns a shallow clone
    */
   public Object clone() {
      MsgQueueGetEntry entry = null;
      entry = (MsgQueueGetEntry)super.clone();
      return entry;
   }
}

