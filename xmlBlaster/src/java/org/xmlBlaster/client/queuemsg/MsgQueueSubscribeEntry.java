/*------------------------------------------------------------------------------
Name:      MsgQueueSubscribeEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.queuemsg;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;


/**
 * Wraps an subscribe() message into an entry for a sorted queue.
 * @author xmlBlaster@marcelruff.info
 */
public final class MsgQueueSubscribeEntry extends MsgQueueEntry
{
   private final static String ME = "SubscribeQueueEntry";
   private final QueryQosData subscribeQosData;
   private final QueryKeyData subscribeKeyData;
   private SessionName receiver;
   private final long immutableSizeInBytes;

   /**
    * Use this constructor if a new message object is fed by method subscribe(). 
    * <p />
    */
   public MsgQueueSubscribeEntry(Global glob, StorageId storageId, 
                                 QueryKeyData subscribeKeyData, QueryQosData subscribeQosData)
         throws XmlBlasterException {
      super(glob, MethodName.SUBSCRIBE, PriorityEnum.NORM_PRIORITY, storageId,
            (subscribeQosData == null) ? false : subscribeQosData.isPersistent());
      this.subscribeQosData = (subscribeQosData == null) ? new QueryQosData(glob, MethodName.SUBSCRIBE) : subscribeQosData;
      this.subscribeKeyData = subscribeKeyData;
      this.immutableSizeInBytes = 567 + this.subscribeQosData.size() + this.subscribeKeyData.size();
   }

   /**
    * For persistence recovery
    */
   public MsgQueueSubscribeEntry(Global glob, PriorityEnum priority, StorageId storageId,
                                Timestamp timestamp, long sizeInBytes,
                                QueryKeyData subscribeKeyData, QueryQosData subscribeQosData) {
      super(glob, MethodName.SUBSCRIBE.toString(), priority,
            timestamp, storageId, subscribeQosData.isPersistent());
      this.subscribeQosData = subscribeQosData;
      this.subscribeKeyData = subscribeKeyData;
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

   public QueryQosData getSubscribeQosData() {
      return this.subscribeQosData;
   }

   public QueryKeyData getSubscribeKeyData() {
      return this.subscribeKeyData;
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
      Object[] obj = { this.subscribeQosData.toXml(), this.subscribeKeyData.toXml() };
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

