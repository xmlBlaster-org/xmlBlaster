/*------------------------------------------------------------------------------
Name:      MsgUnitWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
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
import org.xmlBlaster.engine.msgstore.I_ChangeCallback;
import org.xmlBlaster.engine.queuemsg.ReferenceEntry;


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
public final class MsgUnitWrapper implements I_MapEntry, I_Timeout, I_ChangeCallback
{
   private transient final static String ME = "MsgUnitWrapper-";
   private transient final Global glob;
   private transient final LogChannel log;
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
   private final static int PRE_DESTROYED = 3;
   private transient int state = UNDEF;

   private MsgUnit msgUnit;
   private final long immutableSizeInBytes;

   private boolean stored = false;
   private transient boolean swapped = false;


   /**
    * Testsuite
    */
   public MsgUnitWrapper(Global glob, MsgUnit msgUnit, StorageId storageId) throws XmlBlasterException {
      this(glob, msgUnit, (I_Map)null, storageId, 0, 0, (String)null, -1);
   }

   /**
    * Used when message comes from persistence, the owning I_Map is unknown
    */
   public MsgUnitWrapper(Global glob, MsgUnit msgUnit, I_Map ownerCache, int referenceCounter,
                        int historyReferenceCounter, long sizeInBytes) throws XmlBlasterException {
      this(glob, msgUnit, ownerCache, ownerCache.getStorageId(), referenceCounter, historyReferenceCounter, (String)null, sizeInBytes);
   }

   /**
    * Used when message comes from persistence, the owning I_Map is unknown
    */
   public MsgUnitWrapper(Global glob, MsgUnit msgUnit, StorageId storageId, int referenceCounter,
                        int historyReferenceCounter, long sizeInBytes) throws XmlBlasterException {
      this(glob, msgUnit, (I_Map)null, storageId, referenceCounter, historyReferenceCounter, (String)null, sizeInBytes);
   }

   /**
    * Used when message comes from persistence, the owning I_Map is unknown
    * @param embeddedType Allows you to control how to make this object persistent:<br />
    *         ServerEntryFactory.ENTRY_TYPE_MSG_XML Dump strings as XML ASCII (which is smaller, faster, portable -> and therefor default)<br />
    *         ServerEntryFactory.ENTRY_TYPE_MSG_SERIAL Dump object with java.io.Serializable
    * @param sizeInBytes The estimated size of this entry in RAM, if -1 we estimate it for you
    */
   public MsgUnitWrapper(Global glob, MsgUnit msgUnit, I_Map ownerCache, StorageId storageId, int referenceCounter,
                         int historyReferenceCounter, String embeddedType, long sizeInBytes) throws XmlBlasterException {
      this.glob = glob;
      this.log = glob.getLog("core");
      if (msgUnit == null) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, "MsgUnitWrapper", "Invalid constructor parameter msgUnit==null");
      }
      this.msgUnit = msgUnit;
      this.ownerCache = ownerCache;
      this.storageId = (storageId!=null) ? storageId : ((this.ownerCache!=null) ? this.ownerCache.getStorageId() : null);
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

         /*
            Estimation in database (here postgres(oracle):

            1. the columns
               JdbcDriver.mapping[Oracle]=string=VARCHAR(128),longint=NUMBER(19),int=NUMBER(10),blob=BLOB,boolean=CHAR(1)

                             Postg      Oracle
               -----------------------------
               dataid    int   8          19
               nodeid    text  variable  128
               queuename text  variable  128
               prio      int   4          10
               flag      text  1           1
               durable   text  1           1
               bytesize  int   8          19
               -----------------------------
               SUM                       306
               +
               blob      blob  variable   variable

             2) blob = MsgUnit + Integer + Integer
                     =  38 + this.qosData.size() + this.keyData.size() + this.content.length + 38 + 38

                Postgres example:
                  1077011447218000001     xmlBlaster_192_168_1_4_3412     msgUnitStore_xmlBlaster_192_168_1_4_3412myMessage       5       MSG_XML T       3833    ��\\000\\005ur\\000\\023[Ljava.lang.Object;\\220�X\\237\\020s)l\\002\\000\\000xp\\000\\000\\000\\005t\\002\\026\\012 <qos>\\012  <subscribable>false</subscribable>\\012  <destination forceQueuing='true'>/node/xmlBlaster_192_168_1_4_3412/client/Subscriber</destination>\\012  <sender>/node/xmlBlaster_192_168_1_4_3412/client/Publisher/1</sender>\\012  <priority>MAX</priority>\\012  <expiration lifeTime='360000' remainingLife='271805' forceDestroy='false'/>\\012  <rcvTimestamp nanos='1077011447218000001'/>\\012  <persistent/>\\012  <route>\\012   <node id='xmlBlaster_192_168_1_4_3412' stratum='0' timestamp='1077011447218000001' dirtyRead='false'/>\\012  </route>\\012  <isPublish/>\\012 </qos>t\\000.\\012 <key oid='myMessage' contentMime='txt/xml'/>ur\\000\\002[B��\\027�\\006\\010T�\\002\\000\\000xp\\000\\000\\0005I'm message B-376 of type myMessage sent in a PtP waysr\\000\\021java.lang.Integer\\022⠤�\\201\\2078\\002\\000\\001I\\000\\005valuexr\\000\\020java.lang.Number\\206�\\225\\035\\013\\224�\\213\\002\\000\\000xp\\000\\000\\000\\001sq\\000~\\000\\006\\000\\000\\000\\000
             
             => 382 + msgUnit.size()

         In RAM:
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
      */
      this.immutableSizeInBytes = (sizeInBytes >= 0) ? sizeInBytes : (3200 + this.msgUnit.size());

      if (log.TRACE) log.trace(ME+getLogId(), "Created new MsgUnitWrapper instance '" + this + "' " + ((this.ownerCache==null) ? " from persistence store" : ""));

      toAlive();
      //this.glob.getLog("core").info(ME, "Created message" + toXml());
      if (this.historyReferenceCounter > this.referenceCounter) { // assert
         log.error(ME + getLogId(), "PANIC: historyReferenceCounter=" + this.historyReferenceCounter + " is bigger than referenceCounter=" + this.referenceCounter + toXml());
      }
   }

   /**
    * Cleanup timer, it is a weak reference on us therefor it is a 'nice to have'. 
    */
   public void finalize() {
      if (this.destroyTimer != null && this.timerKey != null) {
         this.destroyTimer.removeTimeoutListener(this.timerKey);
      }
   }

   /**
    * The cache sets it to true when the entry is swapped
    * away. 
    * You should not write on a swapped away entry as those
    * changes are lost.
    * Enforced by I_Map
    * @see I_Map#isSwapped()
    */
   public boolean isSwapped() {
      return this.swapped;
   }

   /**
    * Used by the cache implementation to mark entries which will
    * be swapped to the persistent store. 
    * Enforced by I_Map
    */
   public void isSwapped(boolean swapped) {
      this.swapped = swapped;
   }

   private I_Map getOwnerCache() throws XmlBlasterException {
      if (this.ownerCache == null) {
         if (log.TRACE) log.trace(ME+getLogId(), "Creating ownerCache from topicHandler");
         this.ownerCache = getTopicHandler().getMsgUnitCache();
      }
      return this.ownerCache;
   }

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
    * @return false if the entry is outdated (is swapped away)
    */
   public boolean incrementReferenceCounter(int count, StorageId storageId) throws XmlBlasterException {
      
      I_Map cache = getOwnerCache();
      synchronized (cache) {
         if (isSwapped()) {
            return false;
         }
         boolean isHistoryReference = (storageId != null && storageId.getPrefix().equals("history"));
         synchronized (uniqueIdStr) { // use an arbitrary local attribute as monitor
            if (isHistoryReference) {
               this.historyReferenceCounter += count;
            }
            this.referenceCounter += count;
         }

         // TODO: Remove the logging
         if (log.TRACE && !isInternal()) {
            log.trace(ME+getLogId(), "Reference count changed from " +
                (this.referenceCounter-count) + " to " + this.referenceCounter + 
                ", new historyEntries=" + this.historyReferenceCounter + " this='" + this + "' storageId='" + storageId + "'");
         }

         if (this.referenceCounter > 0L) {
            if (ReferenceEntry.STRICT_REFERENCE_COUNTING) {
               // Update persistence store
               if (count != 0) {
                  I_MapEntry ret = cache.change(this, null);
                  //I_MapEntry ret = getOwnerCache().change(this.getUniqueId(), this);  // I_ChangeCallback
                  if (ret != this) {
                     log.error(ME+getLogId(), "Expected to be identical in change(): old=" + this + " new=" + ret);
                  }
               }
            }
         }
         else {
            if (!isDestroyed()) {
               this.state = PRE_DESTROYED; // Invalidate inside synchronize
            }
         }
      } // sync cache                               isDestroyed()
      if (this.state == PRE_DESTROYED) //this.referenceCounter <= 0L)
         toDestroyed();
      return true;
   }

   /**
    * Internal use for TopicHandler
    */
   void setReferenceCounter(int count) {
      synchronized (uniqueIdStr) { // use an arbitrary local attribute as monitor
         this.referenceCounter += count;
      }

      if (log.TRACE && !isInternal()) {
         log.trace(ME+getLogId(), "Reference count changed from " +
             (this.referenceCounter-count) + " to " + this.referenceCounter + ", this='" + this + "'");
      }

      if (this.referenceCounter <= 0L)
         toDestroyed();
   }

   /**
    * Callback invoked by I_Map.change inside the synchronization point. 
    * Enforced by I_ChangeCallback
    * @param entry the entry to modify.
    * @return I_MapEntry the modified entry.
    * @throws XmlBlasterException if something has gone wrong and the change must be rolled back.
    */                             
   public I_MapEntry changeEntry(I_MapEntry entry) throws XmlBlasterException {
      if (log.TRACE) log.trace(ME+getLogId(), "Entring changeEntry(), referecenceCounter=" + this.referenceCounter + 
                               ", historyReferenceCounter=" + this.historyReferenceCounter );
      return this;
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
      return this.state == DESTROYED || this.state == PRE_DESTROYED;
   }

   private void toDestroyed() {
      //this.glob.getLog("core").info(ME, "Entering toDestroyed(oldState=" + getStateStr() + ")");
      synchronized (this) {
         if (this.timerKey != null) {
            this.destroyTimer.removeTimeoutListener(this.timerKey);
            this.timerKey = null;
         }
         if (this.state == DESTROYED) {
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
   /*
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
   */
   }
}

