/*------------------------------------------------------------------------------
Name:      MsgQueueConnectEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queuemsg;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.engine.helper.Destination;

import java.util.ArrayList;

/**
 * Wraps an publish() message into an entry for a sorted queue.
 * @author laghi@swissinfo.org
 * @author xmlBlaster@marcelruff.info
 */
public final class MsgQueueConnectEntry extends MsgQueueEntry
{
   private final static String ME = "ConnectQueueEntry";
   private final ConnectQos connectQos;
   private SessionName receiver;

   /**
    * Use this constructor if a new message object is fed by method publish(). 
    * <p />
    */
   public MsgQueueConnectEntry(Global glob, StorageId storageId, ConnectQos connectQos)
         throws XmlBlasterException {
      super(glob, MethodName.CONNECT, PriorityEnum.MAX_PRIORITY, storageId, /*isDurable*/true);
      this.connectQos = connectQos;
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

   public final ConnectQos getConnectQos() {
      return this.connectQos;
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
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In nanoseconds elapsed since midnight, January 1, 1970 UTC
    */
   public final Timestamp getRcvTimestamp() {
      return null;
   }

   /**
    * The embeddded object for this implementing class is an Object[3] where
    * Object[2] = Boolean (embedds boolean pubSub)
    */
   public Object getEmbeddedObject() {
//      Object[] obj = { new Boolean(pubSub) };
//      return obj;
      log.error(ME, "getEmbeddedObject() IMPLEMENT");
      throw new IllegalArgumentException("getEmbeddedObject() IMPLEMENT");
   }

   public final long getSizeInBytes() {
      long size = super.getSizeInBytes();
      size += 8; // correct it !!!
      return size;
   }

   /**
    * @return If it is an internal message (oid starting with "_"). 
    */
   public final boolean isInternal() {
      return true;
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

