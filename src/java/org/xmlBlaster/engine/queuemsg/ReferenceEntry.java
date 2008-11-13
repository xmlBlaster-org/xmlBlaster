/*------------------------------------------------------------------------------
Name:      ReferenceEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.queuemsg;

import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.jdbc.XBMeat;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;

import java.lang.ref.WeakReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps an publish() message into an entry for a sorted queue.
 * @author michele@laghi.eu
 * @author xmlBlaster@marcelruff.info
 */
public class ReferenceEntry extends MsgQueueEntry
{
   private static Logger log = Logger.getLogger(ReferenceEntry.class.getName());
   private static final long serialVersionUID = 1L;
   private final String ME; // for logging
   protected final transient ServerScope glob; // engine.Global

   /** Weak reference on the MsgUnit with key/content/qos (raw struct) */
   private transient WeakReference weakMsgUnitWrapper;

   // The keyOid and the rcvTimestamp build the unique id in the msgUnitStore
   protected String keyOid;
   protected long msgUnitWrapperUniqueId;

   protected SessionName receiver;

   /*
      If true we don't store the meat in every UpdateEntry but just the reference
      and take care that the msgUnitStore referenceCounter on HD is always current
   */
   public static final boolean STRICT_REFERENCE_COUNTING = true;
   /*
      If true we are able to read the meat from database entries of older
      xmlBlaster versions
      Nice during transition to new behavior
   */
   public static final boolean STRICT_REFERENCE_COUNTING_COMPATIBLE = false;

   /**
    * A new message object is fed by method update(). 
    * @param msgUnit The raw data, we keep a weak reference only on this data so it can be garbage collected
    */
   public ReferenceEntry(String ME, ServerScope glob_, String entryType, MsgUnitWrapper msgUnitWrapper,
                         Timestamp timestamp, StorageId storageId, SessionName receiver) throws XmlBlasterException {
      super(glob_, entryType, 
            (msgUnitWrapper==null) ? PriorityEnum.NORM_PRIORITY : msgUnitWrapper.getMsgQosData().getPriority(),
            timestamp,
            storageId,
            (msgUnitWrapper==null) ? true : msgUnitWrapper.getMsgQosData().isPersistent()); // We may not use msgUnitWrapper.isPersistent() as is forced to transient in TopicHandler during initialization
      this.glob = glob_;
      this.ME = ME;
      setMsgUnitWrapper(msgUnitWrapper);
      //setSender(msgUnitWrapper.getMsgQosData().getSender());
      setReceiver(receiver);
      if (msgUnitWrapper != null) 
         super.wantReturnObj = msgUnitWrapper.getWantReturnObj();
      else super.wantReturnObj = false; 
   }


   /**
    * A new message object is fed by method publish(). 
    * @param msgUnit The raw data, we keep a weak reference only on this data so it can be garbage collected
    */
   public ReferenceEntry(String ME, ServerScope glob, String entryType, MsgUnitWrapper msgUnitWrapper, StorageId storageId) throws XmlBlasterException {
      this(ME, glob, entryType, msgUnitWrapper, (Timestamp)null, storageId, (SessionName)null);
   }

   /**
    * For persistence recovery
    * @param msgUnitWrapperUniqueId The unique timestamp of the MsgUnitWrapper instance (need to lookup MsgUnitWrapper)
    */
   public ReferenceEntry(String ME, ServerScope glob, String entryType, PriorityEnum priority, StorageId storageId, Timestamp entryTimestamp,
                        String keyOid, long msgUnitWrapperUniqueId, boolean persistent, SessionName receiver,
                        String qos, String key, byte[] content) throws XmlBlasterException {
      super(glob, entryType, priority, entryTimestamp, storageId, persistent);
      this.glob = glob;
      this.ME = ME;
      this.keyOid = keyOid;
      this.msgUnitWrapperUniqueId = msgUnitWrapperUniqueId;
      setReceiver(receiver);
      super.wantReturnObj = false;
      /*
      MsgUnitWrapper msgUnitWrapper = getMsgUnitWrapper();

      if ( STRICT_REFERENCE_COUNTING_COMPATIBLE ) {
         // We need to check it the original msgUnitWrapper still
         // exists, if not we create one (used by callback queue, not by history queue).
         if (msgUnitWrapper == null) {
            if (qos != null || key != null) {
               log.warn(ME, "Lookup of MsgUnitWrapper '" + msgUnitWrapperUniqueId + "' failed, we create a new instance");
               if (this.glob.getRequestBroker() != null) {
                  TopicHandler topicHandler = this.glob.getRequestBroker().getMessageHandlerFromOid(this.keyOid);
                  if (topicHandler != null) {
                     PublishQosServer publishQosServer = new PublishQosServer(glob, qos, true); // true marks from persistent store (prevents new timestamp)
                     MsgKeyData msgKeyData = glob.getMsgKeyFactory().readObject(key);
                     MsgUnit msgUnit = new MsgUnit(msgKeyData, content, publishQosServer.getData());
                     msgUnitWrapper = new MsgUnitWrapper(glob, msgUnit,
                                                topicHandler.getMsgUnitCache(),
                                                1, 0, -1);
                     // The topicHandler holds the real reference:
                     msgUnitWrapper = topicHandler.addMsgUnitWrapper(msgUnitWrapper, storageId);
                     // NOTE: The returned msgUnitWrapper is not always identical to the passed one
                     // if two threads do this simultaneously, the topic handler sync this situation
                     super.wantReturnObj = msgUnitWrapper.getWantReturnObj();
                     this.weakMsgUnitWrapper = new WeakReference(msgUnitWrapper);
                  }
               }
            }
            else {
               log.error(ME, "Can't recreate MsgUnitWrapper, got no information from persistency: " + toXml());
            }
         }
         else {
            // added() is not triggered when coming from persistency
            //msgUnitWrapper.incrementReferenceCounter(1, storageId);
         }
      }
      else {
         if (msgUnitWrapper == null) {
            log.warn(ME+"-"+getLogId(), "DEBUG ONLY: No 'meat' found in msgStore, we ignore this entry," +
                     " this is possible after a server crash for messages which were not acknowldeged to the publisher during the crash." +
                     " Usually the publisher will send it again");
            
            //log.error(ME, "No 'meat' found for MsgQueueEntry '" + getLogId() +
            //              "' in msgStore: " + Global.getStackTraceAsString());
            //
            //throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, 
            //          "No 'meat' found for MsgQueueEntry '" + getLogId() + "' in msgStore");
         }
      }
      */
   }

   protected boolean isForceDestroy() {
      return false;
   }

   public final ServerScope getGlobal() {
      return this.glob;
   }

   /**
    * The caller needs to synchronize over msgCache
    *  @return the MsgUnitWrapper or null if not found
    */
   public MsgUnitWrapper getMsgUnitWrapper() {
      Object referent = null;
      if (this.weakMsgUnitWrapper != null) {
         referent = this.weakMsgUnitWrapper.get();
      } 
      if (referent == null || ((MsgUnitWrapper)referent).isSwapped()) {  // message was swapped away
         this.weakMsgUnitWrapper = null;
         referent = lookup();
         if (referent == null) return null;
         this.weakMsgUnitWrapper = new WeakReference(referent);
      }
      MsgUnitWrapper msgUnitWrapper = (MsgUnitWrapper)referent;
      if (msgUnitWrapper == null) { 
         if (!isForceDestroy()) {
            log.severe("No meat found but forceDestroy=false");
         }
      }
      return msgUnitWrapper;
   }

   private void incrementReferenceCounter(int incr, StorageId storageId) {
      try {
         MsgUnitWrapper msgUnitWrapper = getMsgUnitWrapper();
         if (msgUnitWrapper != null) {
            msgUnitWrapper.incrementReferenceCounter(incr, storageId);
         }
         else { // Log situation:
            boolean isHistory = (storageId == null) ? false : Constants.RELATING_HISTORY.equals(storageId.getRelatingType());
            String id = (storageId == null) ? "" : storageId.getId();
            if (isForceDestroy()) {
               if (log.isLoggable(Level.FINE)) log.fine("No meat found, incr=" + incr + " storageId=" + id + " isExpired=" + isExpired());
            }
            else if (isHistory) { // Is OK when expired history entries are cleared!
               if (log.isLoggable(Level.FINE)) log.fine("No meat found, incr=" + incr + " storageId=" + id + " isExpired=" + isExpired());
            }
            else if (isExpired()) {
               if (log.isLoggable(Level.FINE)) log.fine("No meat found, incr=" + incr + " storageId=" + id + " isExpired=" + isExpired());
            }
            else { // Analyse it
               log.severe("No meat found, incr=" + incr + " storageId=" + id + " isExpired=" + isExpired());
               Thread.dumpStack();
            }
         }
      }
      catch (Throwable ex) {
         log.severe("incr="+incr+" to '" + storageId + "' raised an exception: " + ex.toString());
      }
   }

   /**
    * Notification if this entry is added to queue. 
    * It can be added to several queues simultaneously, the reference counter will be incremented each time
    * @see org.xmlBlaster.util.queue.I_Entry#added(StorageId)
    */
   public void added(StorageId storageId) {
      incrementReferenceCounter(1, storageId);
   }

   /**
    * Notification if this entry is removed from queue
    * @see org.xmlBlaster.util.queue.I_Entry#removed(StorageId)
    */
   public void removed(StorageId storageId) {
      incrementReferenceCounter(-1, storageId);
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

   /**
    * Gets the message unit but is for read only (dirty read) 
    * @return null if expired->destroyed
    * @throws XmlBlasterException
    */
   public MsgUnit getMsgUnitOrNull() {
      MsgUnitWrapper msgUnitWrapper = getMsgUnitWrapper();
      if (msgUnitWrapper == null) {
         return null;
      }
      return msgUnitWrapper.getMsgUnit();
   }

   /**
    * Gets the message unit but is for read only (dirty read) 
    * @return
    * @throws XmlBlasterException if not found (e.g. forceDestroy and expired)
    */
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

   /**
    * TODO? make sender persistent?
    * @return can be null
    */
   public final SessionName getSender() {
      MsgUnit msgUnit = getMsgUnitOrNull();
      if (msgUnit == null) return null;
      return msgUnit.getQosData().getSender();
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
   private MsgUnitWrapper lookup() {
      try {
         return this.glob.getTopicAccessor().lookupDirtyRead(this.keyOid, this.msgUnitWrapperUniqueId);
      }
      catch (XmlBlasterException e) {
         log.warning("lookup failed: " + e.getMessage());
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

   public final void embeddedObjectToXml(java.io.OutputStream out, java.util.Properties props) throws java.io.IOException {
      MsgUnit msgUnit = getMsgUnitOrNull();
      if (msgUnit != null) // TODO: Messages with no meat can't be loaded again by ServerEntryFactory 
         msgUnit.toXml(out, props);
   }
   
   /**
    * Returns a shallow clone
    */
   public Object clone() {
      ReferenceEntry entry = null;
      entry = (ReferenceEntry)super.clone();
      return entry;
   }


   /**
    * Shall return null since it is a reference
    */
   public XBMeat getMeat() {
      return null;
   }
   
}

