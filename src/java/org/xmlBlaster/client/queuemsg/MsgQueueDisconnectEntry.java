/*------------------------------------------------------------------------------
Name:      MsgQueueDisconnectEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.queuemsg;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;


/**
 * Wraps an disconnect() message into an entry for a sorted queue.
 * @author laghi@swissinfo.org
 * @author xmlBlaster@marcelruff.info
 */
public final class MsgQueueDisconnectEntry extends MsgQueueEntry
{
   private static final long serialVersionUID = 2227605254463221335L;
   private final DisconnectQos disconnectQos;
   private SessionName receiver;
   private final long immutableSizeInBytes;

   /**
    * Use this constructor if a new message object is fed by method disconnect(). 
    * <p />
    * @param disconnectQos May not be null
    */
   public MsgQueueDisconnectEntry(Global glob, StorageId storageId, DisconnectQos disconnectQos)
         throws XmlBlasterException {
      super(glob, MethodName.DISCONNECT, PriorityEnum.MIN_PRIORITY, storageId, disconnectQos.getData().isPersistent());
      this.disconnectQos = disconnectQos;
      this.immutableSizeInBytes = 500 + this.disconnectQos.getData().size();
   }

   /**
    * For persistence recovery
    */
   public MsgQueueDisconnectEntry(Global glob, PriorityEnum priority, StorageId storageId,
                               Timestamp timestamp, long sizeInBytes, DisconnectQos disconnectQos) {
      super(glob, MethodName.DISCONNECT.toString(), priority,
            timestamp, storageId,
            (disconnectQos == null) ? false : disconnectQos.getData().isPersistent());
      this.disconnectQos = (disconnectQos == null) ? new DisconnectQos(glob) : disconnectQos;
      this.immutableSizeInBytes = sizeInBytes;
   }

   /**
    * @see MsgQueueEntry#isExpired
    */
   public final boolean isExpired() {
      return false;
   }

   /**
    * @see MsgQueueEntry#isDestroyed
    */
   public final boolean isDestroyed() {
      return false;
   }

   public final DisconnectQos getDisconnectQos() {
      return this.disconnectQos;
   }

   /**
    * Access the unique login name of the sender. 
    * @return loginName of the data source
    * @see MsgQueueEntry#getSender()
    */
   public final SessionName getSender() {
      return null;
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
      return this.receiver;
   }

   /**
    * @see MsgQueueEntry#getKeyOid()
    */
   public final String getKeyOid() {
      return null;
   }

   /**
    * return null
    */
   public final Timestamp getRcvTimestamp() {
      return null;
   }

   /**
    * The embeddded object for this implementing class is an Object[1] where
    * Object[0] = qos.toXml()
    */
   public Object getEmbeddedObject() {
      Object[] obj = { this.disconnectQos.toXml() };
      return obj;
   }

   public final long getSizeInBytes() {
      return this.immutableSizeInBytes;
   }

   /**
    * @return true
    */
   public final boolean isInternal() {
      return true;
   }

   public final void embeddedObjectToXml(java.io.OutputStream out, java.util.Properties props) throws java.io.IOException {
      if (this.disconnectQos != null)
         out.write(this.disconnectQos.toXml((String)null, props).getBytes());
   }

   /**
    * Returns a shallow clone
    */
   public Object clone() {
      MsgQueueDisconnectEntry entry = null;
      entry = (MsgQueueDisconnectEntry)super.clone();
      return entry;
   }
}

