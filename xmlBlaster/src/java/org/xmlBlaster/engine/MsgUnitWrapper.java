/*------------------------------------------------------------------------------
Name:      MsgUnitWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.engine.queuemsg.ServerEntryFactory;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.engine.qos.PublishQosServer; // for main only
import org.xmlBlaster.client.key.PublishKey;       // for main only




/**
 * Wraps a publish() message into an entry for a persistence cache. 
 * <p>
 * There are two options to make this object persistent (measure on a 2GHz Intel Linux laptop with Postgres):
 * </p>
 * <p>
 * 1. QoS and Key are stored as XML ASCII strings, the content as byte[]<br />
 * This variant takes about 50 microsec to serialize (toXml()) and 380 microsec to create the object again (SAX parse).
 * The size for an empty content is approx. 80 bytes for a medium sized key and QoS.
 * </p>
 * <p>
 * 2. The whole object is java.io.Serialized<br />
 * This variant takes about 160 microsec to serialize and 750 microsec to deserialize.
 * </p>
 * <p>
 * So we have chosen the XML variant as it is faster, has no versioning problems and has smaller size
 * </p>
 * @author xmlBlaster@marcelruff.info
 * @see org.xmlBlaster.engine.queuemsg.ServerEntryFactory#main(String[])
 */
public final class MsgUnitWrapper implements I_MapEntry, I_Timeout
{
   private transient final static String ME = "MsgUnitWrapper-";
   private transient final Global glob;
   private transient int historyReferenceCounter; // if is in historyQueue, is swapped to persistence as well
   private transient int referenceCounter;        // total number of references, is swapped to persistence as well
   private transient final long uniqueId;
   private transient final String uniqueIdStr;    // cache uniqueId as String
   private transient final StorageId storageId;   // the unique cache name
   private transient I_Map ownerCache;
   private transient final String embeddedType;
   /** used to tell to the MsgQueueEntry if a return value is desidered */
   private transient boolean wantReturnObj;
   private transient Object returnObj;

   /**
    * This topic is destroyed after given timeout
    * The timer is activated on state change to UNREFERENCED
    * and removed on change to ALIVE
    */
   private transient Timeout destroyTimer;
   private transient Timestamp timerKey = null;

   private final static int UNDEF = -1;
   private final static int ALIVE = 0;
   private final static int EXPIRED = 1;
   private final static int DESTROYED = 2;
   private transient int state = UNDEF;

   private MsgUnit msgUnit;
   private final long immutableSizeInBytes;

   private boolean stored = false;

   /**
    * Use this constructor if a new message object is fed by method publish(). 
    * <p />
    * @param msgUnit The raw data
    */
   public MsgUnitWrapper(Global glob, MsgUnit msgUnit, I_Map ownerCache) throws XmlBlasterException {
      this(glob, msgUnit, ownerCache.getStorageId(), 0, 0, (String)null, -1);
      this.ownerCache = ownerCache;
   }

   public MsgUnitWrapper(Global glob, MsgUnit msgUnit, StorageId storageId) throws XmlBlasterException {
      this(glob, msgUnit, storageId, 0, 0, (String)null, -1);
   }

   /**
    * Used when message comes from persistence, the owning I_Map is unknown
    */
   public MsgUnitWrapper(Global glob, MsgUnit msgUnit, StorageId storageId, int referenceCounter,
                        int historyReferenceCounter, long sizeInBytes) throws XmlBlasterException {
      this(glob, msgUnit, storageId, referenceCounter, historyReferenceCounter, (String)null, sizeInBytes);
   }

   /**
    * Used when message comes from persistence, the owning I_Map is unknown
    * @param embeddedType Allows you to control how to make this object persistent:<br />
    *         ServerEntryFactory.ENTRY_TYPE_MSG_XML Dump strings as XML ASCII (which is smaller, faster, portable -> and therefor default)<br />
    *         ServerEntryFactory.ENTRY_TYPE_MSG_SERIAL Dump object with java.io.Serializable
    * @param sizeInBytes The estimated size of this entry in RAM, if -1 we estimate it for you
    */
   public MsgUnitWrapper(Global glob, MsgUnit msgUnit, StorageId storageId, int referenceCounter,
                         int historyReferenceCounter, String embeddedType, long sizeInBytes) throws XmlBlasterException {
      this.glob = glob;
      if (msgUnit == null) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, "MsgUnitWrapper", "Invalid constructor parameter msgUnit==null");
      }
      this.msgUnit = msgUnit;
      this.storageId = storageId;
      this.referenceCounter = referenceCounter;
      this.historyReferenceCounter = historyReferenceCounter;
      this.embeddedType = (embeddedType == null) ? ServerEntryFactory.ENTRY_TYPE_MSG_XML : embeddedType;
      //this.uniqueId = getKeyOid()+getMsgQosData().getRcvTimestamp();
      if (getMsgQosData().getRcvTimestamp() == null) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, "MsgUnitWrapper", "Missing timestamp, try to create publish QoS with PublishQosServer.java");
      }
      this.uniqueId = getMsgQosData().getRcvTimestamp().getTimestamp();
      this.uniqueIdStr = ""+this.uniqueId;
      // this.ME = "MsgUnitWrapper-" + getLogId();
      this.destroyTimer = this.glob.getMessageTimer();  // holds weak references only

      // Estimated calculation of used memory by one MsgUnitWrapper instance
      // = Object memory + payload
      // Where following objects need to be created:
      // 5 PropBoolean
      // 1 PropLong
      // 1 RcvTimestamp
      // 1 MsgQosData
      // 1 MsgKeyData
      // 1 MsgUnit
      // 1 MsgUnitWrapper
      this.immutableSizeInBytes = (sizeInBytes >= 0) ? sizeInBytes : (3200 + this.msgUnit.size());

      toAlive();
      //this.glob.getLog("core").info(ME, "Created message" + toXml());
      if (this.historyReferenceCounter > this.referenceCounter) { // assert
         this.glob.getLog("core").error(ME + getLogId(), "PANIC: historyReferenceCounter=" + this.historyReferenceCounter + " is bigger than referenceCounter=" + this.referenceCounter + toXml());
      }
   }

   /*
   public void finalize() {
      this.glob.getLog("core").info(ME, "finalize: " + toXml());
   }
   */

   /*
   private I_Map getOwnerCache() throws XmlBlasterException {
      if (this.ownerCache == null) {
         this.ownerCache = getTopicHandler().getMsgUnitCache();
      }
      return this.ownerCache;
   }
   */

   /**
    * @return The owning TopicHandler, never null
    */
   public TopicHandler getTopicHandler() throws XmlBlasterException {
      TopicHandler topicHandler = glob.getRequestBroker().getMessageHandlerFromOid(getKeyOid());
      if (topicHandler == null) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME + getLogId(), "getTopicHandler() - storage lookup of topic '" + getKeyOid() + "' failed");
      }
      return topicHandler;
   }

   /**
    * Invoked by ReferenceEntry.java to support reference counting
    * @param storageId
    */
   public void incrementReferenceCounter(int count, StorageId storageId) {
      
      //glob.getLog("core").error(ME, "DEBUG ONLY " + getSizeInBytes() + " \n" + toXml());

      boolean isHistoryReference = (storageId != null && storageId.getPrefix().equals("history"));
      synchronized (uniqueIdStr) { // use an arbitrary local attribute as monitor
         if (isHistoryReference) {
            this.historyReferenceCounter += count;
         }
         this.referenceCounter += count;
      }
      // TODO: Remove the logging
      if (glob.getLog("core").TRACE && !isInternal()) {
         // this is just to send the stack trace to the log file (stderr does not go there)
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         PrintStream pstr = new PrintStream(baos);
         new Exception().printStackTrace(pstr);
         
         glob.getLog("core").trace(ME, "Reference count of '" + getLogId() + "' changed from " +
         (this.referenceCounter-((isHistoryReference)?2*count:count)) + " to " + this.referenceCounter + 
         ", new historyEntries=" + this.historyReferenceCounter + " this='" + this + "' storageId='" + storageId + "' stack=" + new String(baos.toByteArray()));
         
      }
      if (this.referenceCounter <= 0L) {
         toDestroyed();
      }
   }

   /**
    * @return The number or references on myself (history, callback queue and plugin queues)
    */
   public int getReferenceCounter() {
      return this.referenceCounter;
   }

   /**
    * @return 1: Is referenced one time from history queue, else 0
    */
   public int getHistoryReferenceCounter() {
      return this.historyReferenceCounter;
   }

   /** Returns a dummy only as sorting is not important in this context. */
   public int getPriority() {
      return PriorityEnum.NORM_PRIORITY.getInt();
   }

   /*
   public boolean isExpired() {
      return getMsgQosData().isExpired();
   }
   */

   public MsgQosData getMsgQosData() {
      return (MsgQosData)this.msgUnit.getQosData();
   }

   public boolean isPersistent() {
      return getMsgQosData().isPersistent();
   }

   public MsgKeyData getMsgKeyData() {
      return (MsgKeyData)this.msgUnit.getKeyData();
   }

   public MsgUnit getMsgUnit() {
      return this.msgUnit;
   }

   public final String getKeyOid() {
      return getMsgKeyData().getOid();
   }

   public String getContentMime() {
      return getMsgKeyData().getContentMime();
   }

   public String getContentMimeExtended() {
      return getMsgKeyData().getContentMimeExtended();
   }

   public String getDomain() {
      return getMsgKeyData().getDomain();
   }

   public void setMsgUnit(MsgUnit msg) {
      this.msgUnit = msg;
   }

   public long getSizeInBytes() {
      return this.immutableSizeInBytes;
   }

   /**
    * The unique ID for this entry = getMsgQosData().getRcvTimestamp().getTimestamp()
    */
   public long getUniqueId() {
      return this.uniqueId;
   }

   public String getUniqueIdStr() {
      return this.uniqueIdStr;
   }

   public final String getLogId() {
      return getKeyOid() + "/" + getMsgQosData().getRcvTimestamp();
   }

   public final boolean isInternal() {
      return getMsgKeyData().isInternal();
   }

   /**
    * @return ServerEntryFactory.ENTRY_TYPE_MSG_XML or ServerEntryFactory.ENTRY_TYPE_MSG_SERIAL
    */
   public String getEmbeddedType() {
      return this.embeddedType;
   }

   /**
    * The embedded object. 
    * Object[] = { this.msgUnit, new Integer(this.referenceCounter) }  or<br />
    * qos.toXml, key.toXml, contentBytes
    * <p>
    * IMPORTANT NOTE:
    * If you change the data here you need to change MsgQueueUpdateEntry#getEmbeddedObject() as well!
    * Check ServerEntryFactory as well.
    * </p>
    */
   public Object getEmbeddedObject() {
      if (this.embeddedType.equals(ServerEntryFactory.ENTRY_TYPE_MSG_SERIAL)) {
         Object[] obj = { this.msgUnit, new Integer(this.referenceCounter),
                          new Integer(this.historyReferenceCounter) };
         return obj;
      }
      else {
         Object[] obj = { this.msgUnit.getQosData().toXml(), this.msgUnit.getKeyData().toXml(),
                          this.msgUnit.getContent(), new Integer(this.referenceCounter),
                          new Integer(this.historyReferenceCounter) };
         return obj;
      }
   }

   /**
    * Returns a shallow clone
    */
   public Object clone() {
      try {
         return super.clone();
      }
      catch (CloneNotSupportedException e) {
         return null;
      }
   }

   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dumps the message. 
    * NOTE: max 80 bytes of the content are displayed
    */
   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(1024);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      int maxContentDumpSize = 80;
      sb.append(offset).append("<MsgUnitWrapper id='").append(getLogId());
      sb.append("' referenceCount='").append(getReferenceCounter());
      sb.append("' state='").append(getStateStr()).append("'>");
      sb.append(this.msgUnit.toXml(Constants.INDENT + extraOffset, maxContentDumpSize));
      sb.append(offset).append("</MsgUnitWrapper>");
      return sb.toString();
   }

   /**
    * Notification if this entry is added to storage
    * @see org.xmlBlaster.util.queue.I_Entry#added(StorageId)
    */
   public void added(StorageId storageId) {
      this.glob.getLog("core").error(ME + getLogId(), "added("+storageId.getId()+") invocation not expected");
   }

   /**
    * Notification if this entry is removed from storage
    * @see org.xmlBlaster.util.queue.I_Entry#removed(StorageId)
    */
   public void removed(StorageId storageId) {
      this.glob.getLog("core").error(ME + getLogId(), "removed("+storageId.getId()+") invocation not expected");
   }

   /**
    */
   public boolean isAlive() {
      return this.state == ALIVE;
   }

   private void toAlive() {
      synchronized (this) {
         if (isAlive()) {
            return;
         }
         this.state = ALIVE;
         if (this.timerKey != null) {
            this.destroyTimer.removeTimeoutListener(this.timerKey);
            this.timerKey = null;
            this.glob.getLog("core").error(ME + getLogId(), "Unexpected expiry timer in state " + getStateStr());
         }

         long lifeTime = getMsgQosData().getLifeTime();
         if (lifeTime > -1) {
            long timeout = (getMsgQosData().getRcvTimestamp().getMillis() + lifeTime) - System.currentTimeMillis();
            if (timeout <= 0L) {
               timeout(null);  // switch to EXPIRED or DESTROYED
            }
            else {
               this.timerKey = this.destroyTimer.addTimeoutListener(this, timeout, null);
               //this.glob.getLog("core").info(ME, "Register msg for expiration in " + org.jutils.time.TimeHelper.millisToNice(timeout));
            }
         }
      }
   }

   /**
    */
   public boolean isExpired() {
      return this.state == EXPIRED;
   }

   private void toExpired() throws XmlBlasterException {
      //this.glob.getLog("core").info(ME, "Entering toExpired(oldState=" + getStateStr() + ")");
      synchronized (this) {
         if (this.timerKey != null) {
            this.destroyTimer.removeTimeoutListener(this.timerKey);
            this.timerKey = null;
         }
         if (isExpired()) {
            return;
         }
         this.state = EXPIRED;
         if (this.referenceCounter <= 0L) {
            toDestroyed();
            return;
         }
      }

      TopicHandler topicHandler = glob.getRequestBroker().getMessageHandlerFromOid(getKeyOid());
      if (topicHandler != null) // Topic could be erased in the mean time with forceDestroy=true
         topicHandler.entryExpired(this);
   }

   /**
    */
   public boolean isDestroyed() {
      return this.state == DESTROYED;
   }

   private void toDestroyed() {
      //this.glob.getLog("core").info(ME, "Entering toDestroyed(oldState=" + getStateStr() + ")");
      synchronized (this) {
         if (this.timerKey != null) {
            this.destroyTimer.removeTimeoutListener(this.timerKey);
            this.timerKey = null;
         }
         if (isDestroyed()) {
            return;
         }
         this.state = DESTROYED;
      }

      TopicHandler topicHandler = glob.getRequestBroker().getMessageHandlerFromOid(getKeyOid());
      if (topicHandler != null) // Topic could be erased in the mean time with forceDestroy=true
         topicHandler.entryDestroyed(this);
   }

   /**
    * This timeout occurs after a configured expiration delay
    */
   public final void timeout(Object userData) {
      //this.glob.getLog("core").info(ME, "Expiration timeout occurred after " + getMsgQosData().getLifeTime() + " millis");
      synchronized (this) {
         if (this.timerKey != null) {
            this.destroyTimer.removeTimeoutListener(this.timerKey);
            this.timerKey = null;
         }
         if (getMsgQosData().isForceDestroy()) {
            toDestroyed();
         }
         else {
            try {
               toExpired();
            }
            catch (XmlBlasterException e) {
               this.glob.getLog("core").error(ME + getLogId(), "Unexpected exception from toExpired() which we can't handle: " + e.getMessage());
            }
         }
      }
   }

   public String getStateStr() {
      if (isAlive()) {
         return "ALIVE";
      }
      else if (isExpired()) {
         return "EXPIRED";
      }
      else if (isDestroyed()) {
         return "DESTROYED";
      }
      return "UNDEF";
   }

   /**
    * @see org.xmlBlaster.util.queue.I_Entry#setStored(boolean)
    */
   public final void setStored(boolean stored) {
      this.stored = stored;
   }

   /**
    * @see org.xmlBlaster.util.queue.I_Entry#isStored()
    */
   public final boolean isStored() {
      return this.stored;
   }
   
   /**
    * Sets this flag to true/false. This flag can be passed to the MsgQueueEntry
    * @param wantReturnObj
    */
   public void setWantReturnObj(boolean wantReturnObj) {
      this.wantReturnObj = wantReturnObj;
   }
   
   /**
    * 
    * @return
    */   
   public boolean getWantReturnObj() {
      return this.wantReturnObj;
   }
   
   /**
    * @return returnObj The carried object used as return QoS in sync or async I_Queue.put() mode, can be null.
    */
   public Object getReturnObj() {
      return this.returnObj;
   }

   /**
    * Set the object to be carried as return value. 
    * NOTE: This can be used only once as the first call to this method
    * destroys the reference to the clone original instance.
    */
   public void setReturnObj(Object returnObj) {
      this.returnObj = returnObj;
   }


   /**
    * Measure size for XML-ASCII versus java.io.Serializable persistence. 
    * <pre> 
    * java org.xmlBlaster.engine.MsgUnitWrapper
    * </pre> 
    * Result:
    * <p>
    * java.io.Serialized file 'MsgUnitWrapper.ser' size=1407 bytes versus XML dump=123 bytes
    * </p>
    */
   public static void main(String[] args) {
      Global glob = new Global(args);
      String fileName = "MsgUnitWrapper.ser";
      try {
         PublishKey publishKey = new PublishKey(glob, "HA");
         PublishQosServer publishQosServer = new PublishQosServer(glob, "<qos><persistent/></qos>");
         publishQosServer.getData().setPriority(PriorityEnum.HIGH_PRIORITY);
         MsgUnit msgUnit = new MsgUnit(publishKey.getData(), "HO".getBytes(), publishQosServer.getData());
         StorageId storageId = new StorageId("mystore", "someid");
         MsgUnitWrapper msgUnitWrapper = new MsgUnitWrapper(glob, msgUnit, storageId);
         try {
            java.io.FileOutputStream f = new java.io.FileOutputStream(fileName);
            java.io.ObjectOutputStream objStream = new java.io.ObjectOutputStream(f);
            objStream.writeObject(msgUnitWrapper);
            objStream.flush();
            java.io.File file = new java.io.File(fileName);
            System.out.println("SUCCESS written java.io.Serialized file '" + fileName + "' size=" + file.length() +
                               " versus XML dump=" + msgUnitWrapper.getSizeInBytes());
         }
         catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
         }
      }
      catch (XmlBlasterException e) {
         System.err.println("ERROR: " + e.getMessage());
      }
   }
}

