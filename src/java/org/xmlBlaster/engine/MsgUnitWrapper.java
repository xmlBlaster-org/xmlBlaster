/*------------------------------------------------------------------------------
Name:      MsgUnitWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.engine.queuemsg.ServerEntryFactory;
import org.xmlBlaster.util.def.Constants;
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
   private static final long serialVersionUID = -3883804885824516337L;
   private transient final ServerScope glob;
   private static Logger log = Logger.getLogger(MsgUnitWrapper.class.getName());
   private transient int historyReferenceCounter; // if is in historyQueue, is swapped to persistence as well
   private transient int referenceCounter;        // total number of references, is swapped to persistence as well
   private transient final long uniqueId;
   private transient final String uniqueIdStr;    // cache uniqueId as String
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

   private final static int ALIVE = 0;
   private final static int PRE_EXPIRED = 4;
   private final static int EXPIRED = 1;
   private final static int DESTROYED = 2;
   private transient int state = ALIVE;

   private MsgUnit msgUnit;
   private final long immutableSizeInBytes;

   private boolean stored = false;
   private transient boolean swapped = false;
   private transient Timestamp sortTimestamp;


   /**
    * Testsuite
    */
   public MsgUnitWrapper(ServerScope glob, MsgUnit msgUnit, StorageId storageId) throws XmlBlasterException {
      this(glob, msgUnit, (I_Map)null, storageId, 0, 0, (String)null, -1);
   }

   /**
    * Used when message is created from TopicHandler.publish
    */
   public MsgUnitWrapper(ServerScope glob, MsgUnit msgUnit, I_Map ownerCache, int referenceCounter,
                        int historyReferenceCounter, long sizeInBytes) throws XmlBlasterException {
      this(glob, msgUnit, ownerCache, ownerCache.getStorageId(), referenceCounter, historyReferenceCounter, (String)null, sizeInBytes);
   }

   /**
    * Used when message comes from persistence, the owning I_Map is unknown
    */
   public MsgUnitWrapper(ServerScope glob, MsgUnit msgUnit, StorageId storageId, int referenceCounter,
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
   public MsgUnitWrapper(ServerScope glob, MsgUnit msgUnit, I_Map ownerCache, StorageId storageId, int referenceCounter,
                         int historyReferenceCounter, String embeddedType, long sizeInBytes) throws XmlBlasterException {
      this.glob = glob;

      if (msgUnit == null) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, "MsgUnitWrapper", "Invalid constructor parameter msgUnit==null");
      }
      this.msgUnit = msgUnit;
      this.ownerCache = ownerCache;
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

      if (log.isLoggable(Level.FINE)) log.fine("Created new MsgUnitWrapper instance '" + this + "' " + ((this.ownerCache==null) ? " from persistence store" : ""));

      //this.glob.getLog("core").info(ME, "Created message" + toXml());
      if (this.historyReferenceCounter > this.referenceCounter) { // assert
         log.severe("PANIC: historyReferenceCounter=" + this.historyReferenceCounter + " is bigger than referenceCounter=" + this.referenceCounter + toXml());
      }
   }
   
   public final ServerScope getServerScope() {
      return this.glob;
   }

   /**
    * Cleanup timer, it is a weak reference on us therefor it is a 'nice to have'. 
    */
   public void finalize() {
      if (this.destroyTimer != null && this.timerKey != null) {
         if (log.isLoggable(Level.FINE)) log.fine("finalize timerKey=" + this.timerKey);
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

   /**
    * Invoked by ReferenceEntry.java and TopicHandler.java to support reference counting
    * @param count The number of ref-counts to add/subtract
    * @param storageId
    * @return false if the entry is not pre destroyed, true if it is
    *         pre destroyed. NOTE1: the caller must ensure to invoke toDestroyed() in cases
    *         'true' is returned. NOTE2: The invocation toDestroyed() must be done
    *         outside from any sync on the cache.
    */
   public void incrementReferenceCounter(int count, StorageId storageId) throws XmlBlasterException {
      if (isSwapped()) {
         if (log.isLoggable(Level.FINE)) 
            log.fine("incrementReferenceCounter: unexpected swapped message");
         return;
      }
      boolean isHistoryReference = (storageId != null && storageId.getPrefix().equals("history"));
      synchronized (uniqueIdStr) { // use an arbitrary local attribute as monitor
         if (isHistoryReference) {
            this.historyReferenceCounter += count;
         }
         this.referenceCounter += count;
      }

      if (log.isLoggable(Level.FINE) && !isInternal()) {
         log.fine("Reference count changed from " +
             (this.referenceCounter-count) + " to " + this.referenceCounter + 
             ", new historyEntries=" + this.historyReferenceCounter + " this='" + this + "' storageId='" + storageId + "'");
      }

      if (this.referenceCounter > 0L) {
         if (ReferenceEntry.STRICT_REFERENCE_COUNTING) {
            if (count != 0) this.glob.getTopicAccessor().changeDirtyRead(this);
         }
      }
      else {
         if (!isDestroyed()) {
            toDestroyed();
         }
      }
   }

   /**
    * Internal use for TopicHandler
    */
   void setReferenceCounter(int count) {
      synchronized (uniqueIdStr) { // use an arbitrary local attribute as monitor
         this.referenceCounter += count;
      }

      if (log.isLoggable(Level.FINE) && !isInternal()) {
         log.fine("Reference count changed from " +
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
      if (log.isLoggable(Level.FINE)) log.fine("Entring changeEntry(), referecenceCounter=" + this.referenceCounter + 
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
      return toXml((String)null, false);
   }
   
   public final void embeddedObjectToXml(java.io.OutputStream out, java.util.Properties props) throws java.io.IOException {
      /*
      boolean forceReadable = (props!=null) && props.contains("forceReadable"); Constants.TOXML_FORCEREADABLE
      int maxContentLen = -1;
      if (props!=null && props.contains("maxContentLen")) { Constants.TOXML_MAXCONTENTLEN
         try { maxContentLen = new Integer((String)props.get("maxContentLen")).intValue(); } catch(NumberFormatException e) {}
      }
      */
      MsgUnit msgUnit = getMsgUnit();
      if (msgUnit != null)
         msgUnit.toXml(out, props);
   }

   /**
    * Dumps the message. 
    * NOTE: max 80 bytes of the content are displayed
    */
   public String toXml(String extraOffset, boolean forceReadable) {
      StringBuffer sb = new StringBuffer(1024);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      int maxContentDumpSize = 80;
      sb.append(offset).append("<MsgUnitWrapper id='").append(getLogId());
      sb.append("' referenceCount='").append(getReferenceCounter());
      sb.append("' state='").append(getStateStr()).append("'>");
      sb.append(this.msgUnit.toXml(Constants.INDENT + extraOffset, maxContentDumpSize, forceReadable));
      sb.append(offset).append("</MsgUnitWrapper>");
      return sb.toString();
   }

   /**
    * Notification if this entry is added to storage
    * @see org.xmlBlaster.util.queue.I_Entry#added(StorageId)
    */
   public void added(StorageId storageId) {
      log.severe("added("+storageId.getId()+") invocation not expected");
   }

   /**
    * Notification if this entry is removed from storage
    * @see org.xmlBlaster.util.queue.I_Entry#removed(StorageId)
    */
   public void removed(StorageId storageId) {
      log.severe("removed("+storageId.getId()+") invocation not expected");
   }

   /**
    */
   private boolean isAlive() {
      return this.state == ALIVE;
   }
   
   /**
    * The state may still be alive. 
    * @return true is the configured life span is elapsed
    */
   public boolean hasRemainingLife() {
      long lifeTime = getMsgQosData().getLifeTime();
      if (lifeTime > -1) {
         long timeout = getMsgQosData().getRemainingLife();
         if (timeout <= 0L) {
            return false;
         }
      }
      return true;
   }

   public void startExpiryTimer() {
      synchronized (this) {
         if (this.state != ALIVE) {
            log.severe("Unexpected startExpiryTimer in state " + getStateStr());
            return;
         }
         if (this.timerKey != null) {
            log.severe("Unexpected expiry timer in state " + getStateStr());
            return;
            //this.destroyTimer.removeTimeoutListener(this.timerKey);
            //this.timerKey = null;
            //log.error(ME + getLogId(), "Unexpected expiry timer in state " + getStateStr());
         }

         long lifeTime = getMsgQosData().getLifeTime();
         if (lifeTime > -1) {
            long timeout = getMsgQosData().getRemainingLife();
            if (timeout <= 0L) {
               this.state = PRE_EXPIRED;
               timeout = 0L;
               //timeout(null); // Will deadlock if called by constructor // switch to EXPIRED or DESTROYED
               // We span the timer to fire later and destroy us from another thread 
            }
            this.timerKey = this.destroyTimer.addTimeoutListener(this, timeout, null);
         }
      }
   }

   /**
    */
   public boolean isExpired() {
      return this.state == EXPIRED || this.state == PRE_EXPIRED;
   }

   private void toExpired() throws XmlBlasterException {
      synchronized (this) {
         if (this.timerKey != null) {
            this.destroyTimer.removeTimeoutListener(this.timerKey);
            this.timerKey = null;
         }
         if (this.state == EXPIRED) {
            return;
         }
         this.state = EXPIRED;
      }
      
      if (this.referenceCounter <= 0L) {
         toDestroyed();
         return;
      }
      
      if (this.historyReferenceCounter > 0) {
         StorageId st = new StorageId(Constants.RELATING_HISTORY, "dummy");
         incrementReferenceCounter((-1)*this.historyReferenceCounter, st);
      }
   }

   /**
    */
   public boolean isDestroyed() {
      return this.state == DESTROYED;
   }

   /**
    * Called by TopicHandler.java or ReferenceEntry.java
    */
   public void toDestroyed() {
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
      
      if (log.isLoggable(Level.FINEST)) {
         log.finest("toDestroyed: " + toXml());
         Thread.dumpStack();
      }

      boolean async = false;
      
      if (async)
         this.glob.getTopicAccessor().entryDestroyed_scheduleForExecution(this);
      else {
         TopicHandler topicHandler = this.glob.getTopicAccessor().access(getKeyOid());
         if (topicHandler != null) { // Topic could be erased in the mean time with forceDestroy=true
            try {
               topicHandler.entryDestroyed(this);
            }
            finally {
               this.glob.getTopicAccessor().release(topicHandler);
            }
         }
      }
   }

   /**
    * This timeout occurs after a configured expiration delay
    */
   public final void timeout(Object userData) {
      if (getMsgQosData().isForceDestroy()) {
         toDestroyed();
      }
      else {
         try {
            toExpired();
         }
         catch (XmlBlasterException e) {
            log.severe("Unexpected exception from toExpired() which we can't handle: " + e.getMessage());
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
    * Can be used by cache implementation to implement LRU
    * @return null if not previously set by setSortTimestamp()
    */
   public final Timestamp getSortTimestamp() {
      return this.sortTimestamp;
   }

   /**
    * Can be used by cache implementation to implement LRU
    * @return timestamp This is chosen by the cache algorithm
    */
   public final void setSortTimestamp(Timestamp timestamp) {
      this.sortTimestamp = timestamp;
   }

   /*
    * Measure size for XML-ASCII versus java.io.Serializable persistence. 
    * <pre> 
    * java org.xmlBlaster.engine.MsgUnitWrapper
    * </pre> 
    * Result:
    * <p>
    * java.io.Serialized file 'MsgUnitWrapper.ser' size=1407 bytes versus XML dump=123 bytes
    * </p>
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
   */
}

