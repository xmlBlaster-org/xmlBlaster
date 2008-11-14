/*------------------------------------------------------------------------------
Name:      MsgQueueConnectEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.queuemsg;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;


/**
 * Wraps an connect() message into an entry for a sorted queue.
 * @author michele@laghi.eu
 * @author xmlBlaster@marcelruff.info
 */
public final class MsgQueueConnectEntry extends MsgQueueEntry
{
   private static final long serialVersionUID = -2955028300581264869L;
   private final ConnectQosData connectQosData;
   private SessionName receiver;
   private final long immutableSizeInBytes;

   /**
    * Use this constructor if a new message object is fed by method connect(). 
    * <p />
    */
   public MsgQueueConnectEntry(Global glob, StorageId storageId, ConnectQosData connectQosData)
         throws XmlBlasterException {
      super(glob, MethodName.CONNECT, PriorityEnum.MAX_PRIORITY, storageId, connectQosData.isPersistent());
      this.connectQosData = connectQosData;
      this.immutableSizeInBytes = 2400; // 126 + this.connectQos.getData().size();
   }

   /**
    * For persistence recovery
    */
   public MsgQueueConnectEntry(Global glob, PriorityEnum priority, StorageId storageId,
                               Timestamp timestamp, long sizeInBytes, ConnectQosData connectQosData) {
      super(glob, MethodName.CONNECT.toString(), priority,
            timestamp, storageId, connectQosData.isPersistent());
      this.connectQosData = connectQosData;
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

   public final ConnectQosData getConnectQosData() {
      return this.connectQosData;
   }
   
   public MsgUnit getMsgUnit() {
      return new MsgUnit(null, null, getConnectQosData());
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
      Object[] obj = { this.connectQosData.toXml() };
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
      //TODO final boolean noSecurity = (props!=null) && props.containsKey("noSecurity");//Constants.TOXML_FLAG_NOSECURITY)
      if (this.connectQosData != null)
         out.write(this.connectQosData.toXml((String)null, props).getBytes());
   }
   
   /**
    * Returns a shallow clone
    */
   public Object clone() {
      MsgQueueConnectEntry entry = null;
      entry = (MsgQueueConnectEntry)super.clone();
      return entry;
   }
}

