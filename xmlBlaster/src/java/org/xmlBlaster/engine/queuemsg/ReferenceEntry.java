/*------------------------------------------------------------------------------
Name:      ReferenceEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.queuemsg;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.TopicHandler;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Wraps an publish() message into an entry for a sorted queue.
 * @author laghi@swissinfo.org
 * @author xmlBlaster@marcelruff.info
 */
public class ReferenceEntry extends MsgQueueEntry
{
   private final String ME; // for logging
   private transient Global glob; // engine.Global

   /** Weak reference on the MsgUnit with key/content/qos (raw struct) */
   private transient WeakReference weakMsgUnitWrapper;

   // The keyOid and the rcvTimestamp build the unique id in the msgUnitStore
   protected String keyOid;
   protected long msgUnitWrapperUniqueId;

   protected SessionName receiver;

   /**
    * A new message object is fed by method update(). 
    * @param msgUnit The raw data, we keep a weak reference only on this data so it can be garbage collected
    */
   public ReferenceEntry(String ME, Global glob_, String entryType, MsgUnitWrapper msgUnitWrapper,
                         StorageId storageId, SessionName receiver) throws XmlBlasterException {
      super(glob_, entryType, msgUnitWrapper.getMsgQosData().getPriority(),
            storageId, msgUnitWrapper.getMsgQosData().isPersistent());
      this.glob = glob_;
      this.ME = ME;
      setMsgUnitWrapper(msgUnitWrapper);
      //setSender(msgUnitWrapper.getMsgQosData().getSender());
      setReceiver(receiver);
   }

   /**
    * A new message object is fed by method publish(). 
    * @param msgUnit The raw data, we keep a weak reference only on this data so it can be garbage collected
    */
   public ReferenceEntry(String ME, Global glob, String entryType, MsgUnitWrapper msgUnitWrapper, StorageId storageId) throws XmlBlasterException {
      this(ME, glob, entryType, msgUnitWrapper, storageId, (SessionName)null);
   }

   /**
    * For persistence recovery
    * @param msgUnitWrapperUniqueId The unique timestamp of the MsgUnitWrapper instance (need to lookup MsgUnitWrapper)
    */
   public ReferenceEntry(String ME, Global glob, String entryType, PriorityEnum priority, StorageId storageId, Timestamp entryTimestamp,
                        String keyOid, long msgUnitWrapperUniqueId, boolean persistent, SessionName receiver) {
      super(glob, entryType, priority, entryTimestamp, storageId, persistent);
      this.glob = glob;
      this.ME = ME;
      this.keyOid = keyOid;
      this.msgUnitWrapperUniqueId = msgUnitWrapperUniqueId;
      setReceiver(receiver);
   }

   /** @return the MsgUnitWrapper or null if not found */
   public MsgUnitWrapper getMsgUnitWrapper() {
      Object referent = null;
      if (this.weakMsgUnitWrapper != null)
         referent = this.weakMsgUnitWrapper.get();
      if (referent == null) {  // message was swapped away
         referent = lookup();
         if (referent == null)
            return null;
         this.weakMsgUnitWrapper = new WeakReference(referent);
      }
      return (MsgUnitWrapper)referent;
   }

   /**
    * Notification if this entry is added to queue
    * @see org.xmlBlaster.util.queue.I_Entry#added(StorageId)
    */
   public void added(StorageId storageId) throws XmlBlasterException {
      //if (!isInternal()) log.info(ME, getLogId() + " is added to queue");
      MsgUnitWrapper msgUnitWrapper = getMsgUnitWrapper();
      if (msgUnitWrapper != null) {
         msgUnitWrapper.incrementReferenceCounter(1, storageId);
      }
      else {
         log.error(ME, " Entry '" + getLogId() + "' added to queue but no meat found");
      }
   }

   /**
    * Notification if this entry is removed from queue
    * @see org.xmlBlaster.util.queue.I_Entry#removed(StorageId)
    */
   public void removed(StorageId storageId) throws XmlBlasterException {
      //if (!isInternal()) log.info(ME, getLogId() + " is removed from queue");
      MsgUnitWrapper msgUnitWrapper = getMsgUnitWrapper();
      /* I couldn't force garbage collect of messageUnitWrapper here, why?
      {  // TEST ONLY
         if (msgUnitWrapper != null) {
            msgUnitWrapper = null;
            System.gc();
            System.gc();
            System.gc();
            System.gc();
         }
         msgUnitWrapper = getMsgUnitWrapper();
         log.error(ME, "REMOVE WEAK REF TEST AGAIN msgUnitWrapper=" + msgUnitWrapper);
      }
      */
      if (msgUnitWrapper != null) {
         msgUnitWrapper.incrementReferenceCounter(-1, storageId);
         msgUnitWrapper = null;
      }
      else {
         if (log.TRACE) log.trace(ME, " Entry '" + getLogId() + "' removed from queue but no meat found");
      }
   }

   /**
    * @return true for EXPIRED messages
    */
   public boolean isExpired() {
      MsgUnitWrapper msgUnitWrapper = getMsgUnitWrapper();
      if (msgUnitWrapper == null)
         return true;
      return msgUnitWrapper.isExpired(); //getMsgQosData().isExpired();
   }

   /**
    * @return true if no MsgUnitWrapper is found (the 'meat' is not available anymore)<br />
    *         or for msgUnitWrapper in state=DESTROYED
    */
   public boolean isDestroyed() {
      MsgUnitWrapper msgUnitWrapper = getMsgUnitWrapper();
      if (msgUnitWrapper == null)
         return true;
      return msgUnitWrapper.isDestroyed();
   }

   public MsgQosData getMsgQosData() throws XmlBlasterException {
      return (MsgQosData)getMsgUnit().getQosData();
   }

   public MsgUnit getMsgUnit() throws XmlBlasterException {
      MsgUnitWrapper msgUnitWrapper = getMsgUnitWrapper();
      if (msgUnitWrapper == null) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "Message " + getUniqueId() + " not found");
      }
      return msgUnitWrapper.getMsgUnit();
   }

   public void setMsgUnitWrapper(MsgUnitWrapper msgUnitWrapper) throws XmlBlasterException {
      if (msgUnitWrapper == null)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Given msgUnitWrapper is null");
      this.weakMsgUnitWrapper = new WeakReference(msgUnitWrapper);
      this.keyOid = msgUnitWrapper.getMsgUnit().getKeyData().getOid();
      this.msgUnitWrapperUniqueId = msgUnitWrapper.getUniqueId();
   }

   public long getMsgUnitWrapperUniqueId() {
      return this.msgUnitWrapperUniqueId;
   }

   //public long getSizeInBytes() {
   //   return 180; // This is a guess only, we have only a reference on the real data
   //}

   public String getKeyOid() {
      return this.keyOid;
   }

   public long getRcvTimestamp() {
      return 0L;
   }

   public final MsgKeyData getMsgKeyData() throws XmlBlasterException {
      return (MsgKeyData)getMsgUnit().getKeyData();
   }

   /**
    * @return If it is an internal message (oid starting with "_"). 
    */
   public boolean isInternal() {
      try {
         return (getMsgKeyData().isInternal() || getMsgKeyData().isPluginInternal());
      }
      catch (XmlBlasterException e) {
         return false;
      }
   }

   // TODO? make sender persistent?
   public final SessionName getSender() {
      try {
         return getMsgQosData().getSender();
      }
      catch (XmlBlasterException e) {
         return null;
      }
   }

   /**
    * @return null
    */
   public final SessionName getReceiver() {
      return this.receiver;
   }

   public final void setReceiver(SessionName receiver) {
      this.receiver = receiver;
   }

   /** @return the MsgUnitWrapper or null if not found */
   public MsgUnitWrapper lookup() {
      TopicHandler topicHandler = this.glob.getRequestBroker().getMessageHandlerFromOid(keyOid);
      if (topicHandler == null) {
         return null;
      }
      try {
         return topicHandler.getMsgUnitWrapper(this.msgUnitWrapperUniqueId);
      }
      catch (XmlBlasterException e) {
         log.warn(ME, "lookup failed: " + e.getMessage());
         return null;
      }
   }

   /**
    * The embedded object for this implementing class is an Object[2]
    */
   public Object getEmbeddedObject() {
      Object[] obj = { this.keyOid, new Long(this.msgUnitWrapperUniqueId) };
      return obj;
   }

   /**
    * Returns a shallow clone
    */
   public Object clone() {
      ReferenceEntry entry = null;
      entry = (ReferenceEntry)super.clone();
      return entry;
   }
}

