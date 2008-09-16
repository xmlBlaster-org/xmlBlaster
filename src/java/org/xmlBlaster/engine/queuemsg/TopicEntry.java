/*------------------------------------------------------------------------------
Name:      TopicEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.queuemsg;

import java.util.logging.Logger;

import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.jdbc.XBMeat;
import org.xmlBlaster.util.queue.jdbc.XBRef;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.engine.queuemsg.ServerEntryFactory;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.engine.msgstore.I_MapEntry;


/**
 * Wraps an publish() message into an entry for a persistence cache. 
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
public final class TopicEntry implements I_MapEntry
{
   private static Logger log = Logger.getLogger(TopicEntry.class.getName());
   private static final long serialVersionUID = 1L;
   private transient final String ME;
   private transient final long uniqueId;
   private transient final String uniqueIdStr;    // cache uniqueId as String
   private transient final String embeddedType;

   private MsgUnit msgUnit;
   private final long immutableSizeInBytes;

   private transient boolean stored = false;
   private transient boolean swapped = false;
   private transient Timestamp sortTimestamp;

   /**
    * Use this constructor if a new message object is fed by method publish(). 
    * <p />
    * @param msgUnit The raw data
    */
   public TopicEntry(ServerScope glob, MsgUnit msgUnit) throws XmlBlasterException {
      this(glob, msgUnit, (String)null, -1L);
   }

   /**
    * Used when message comes from persistence, the owning I_Map is unknown
    * @param embeddedType Allows you to control how to make this object persistent:<br />
    *         ServerEntryFactory.ENTRY_TYPE_TOPIC_XML Dump strings as XML ASCII (which is smaller, faster, portable -> and therefor default)<br />
    *         ServerEntryFactory.ENTRY_TYPE_TOPIC_SERIAL Dump object with java.io.Serializable
    * @param sizeInByte The estimated size of the entry in RAM (can be totally different on HD). 
    *                   If -1L it is estimated for you
    */
   public TopicEntry(ServerScope glob, MsgUnit msgUnit, String embeddedType, long sizeInBytes) throws XmlBlasterException {
      if (msgUnit == null) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, "TopicEntry", "Invalid constructor parameter msgUnit==null");
      }
      this.msgUnit = msgUnit;
      this.embeddedType = (embeddedType == null) ? ServerEntryFactory.ENTRY_TYPE_TOPIC_XML : embeddedType;
      //this.uniqueId = getKeyOid()+getMsgQosData().getRcvTimestamp();
      if (getMsgQosData().getRcvTimestamp() == null) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, "TopicEntry", "Missing timestamp, try to create publish QoS with PublishQosServer.java");
      }
      this.uniqueId = getMsgQosData().getRcvTimestamp().getTimestamp();
      this.uniqueIdStr = ""+this.uniqueId;
      this.ME = "TopicEntry-" + getLogId();
      this.immutableSizeInBytes = (sizeInBytes >= 0L) ? sizeInBytes : this.msgUnit.size();
      //this.glob.getLog("core").info(ME, "Created message" + toXml());
   }

   /*
   public void finalize() {
      this.glob.getLog("core").info(ME, "finalize: " + toXml());
      super.finalize();
   }
   */

   /**
    * @return The owning TopicHandler, never null
   public TopicHandler getTopicHandler() throws XmlBlasterException {
      TopicHandler topicHandler = glob.getRequestBroker().getMessageHandlerFromOid(getKeyOid());
      if (topicHandler == null) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "getTopicHandler() - storage lookup of topic '" + getKeyOid() + "' failed");
      }
      return topicHandler;
   }
    */

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

   public String getKeyOid() {
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

   public String getLogId() {
      return getKeyOid() + "/" + getMsgQosData().getRcvTimestamp();
   }

   public final boolean isInternal() {
      return getMsgKeyData().isInternal();
   }

   /**
    * @return ServerEntryFactory.ENTRY_TYPE_TOPIC_XML or ServerEntryFactory.ENTRY_TYPE_TOPIC_SERIAL
    */
   public String getEmbeddedType() {
      return this.embeddedType;
   }

   /**
    * The embedded object. 
    * Object[] = { this.msgUnit }  or<br />
    * qos.toXml, key.toXml, contentBytes
    */
   public Object getEmbeddedObject() {
      if (this.embeddedType.equals(ServerEntryFactory.ENTRY_TYPE_TOPIC_SERIAL)) {
         Object[] obj = { this.msgUnit };
         return obj;
      }
      else {
         Object[] obj = { this.msgUnit.getQosData().toXml(), this.msgUnit.getKeyData().toXml() };
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

   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(2000);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<TopicEntry id='").append(getLogId()).append("'>");
      sb.append(this.msgUnit.toXml(Constants.INDENT + extraOffset));
      sb.append(offset).append("</TopicEntry>");
      return sb.toString();
   }

   /**
    * Notification if this entry is added to storage
    * @see org.xmlBlaster.util.queue.I_Entry#added(StorageId)
    */
   public void added(StorageId storageId) {
      log.severe(ME + "added("+storageId.getId()+") invocation not expected (internal illegal argument)");
   }

   /**
    * Notification if this entry is removed from storage
    * @see org.xmlBlaster.util.queue.I_Entry#removed(StorageId)
    */
   public void removed(StorageId storageId) {
      log.severe(ME + "removed("+storageId.getId()+") invocation not expected (internal illegal argument)");
   }

   /**
    */
   public boolean isExpired() {
      return false;
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
    * Enforced by I_Map
    * @see I_Map#isSwapped()
    */
   public boolean isSwapped() {
      return this.swapped;
   }

   /**
    * Enforced by I_Map
    * @see I_Map#isSwapped(boolean)
    */
   public void isSwapped(boolean swapped) {
      this.swapped = swapped;
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

   public final void embeddedObjectToXml(java.io.OutputStream out, java.util.Properties props) throws java.io.IOException {
      MsgUnit msgUnit = getMsgUnit();
      if (msgUnit != null)
         msgUnit.toXml(out, props);
   }
   
   /**
    * Measure size for XML-ASCII versus java.io.Serializable persistence. 
    * <pre> 
    * java org.xmlBlaster.engine.TopicEntry
    * </pre> 
    * Result:
    * <p>
    * java.io.Serialized file 'TopicEntry.ser' size=1407 bytes versus XML dump=123 bytes
    * </p>
    */
   public static void main(String[] args) {
      ServerScope glob = new ServerScope(args);
      String fileName = "TopicEntry.ser";
      try {
         org.xmlBlaster.client.key.PublishKey publishKey = new org.xmlBlaster.client.key.PublishKey(glob, "HA");
         org.xmlBlaster.engine.qos.PublishQosServer publishQosServer = new org.xmlBlaster.engine.qos.PublishQosServer(glob, "<qos><persistent/></qos>");
         publishQosServer.getData().setPriority(PriorityEnum.HIGH_PRIORITY);
         MsgUnit msgUnit = new MsgUnit(publishKey.getData(), "HO".getBytes(), publishQosServer.getData());
         TopicEntry msgUnitWrapper = new TopicEntry(glob, msgUnit);
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
   
   /**
    * For the new queues 
    */
   public XBMeat getMeat() {
      XBMeat meat = new XBMeat();
      meat.setByteSize(getSizeInBytes());
      meat.setContent(msgUnit.getContent());
      meat.setDataType(getEmbeddedType());
      meat.setDurable(isPersistent());
      // meat.setFlag1(flag1);
      meat.setId(uniqueId);
      meat.setKey(msgUnit.getKey());
      meat.setQos(msgUnit.getQos());
      meat.setRefCount(1);
      return meat;
   }

   /**
    * For the new queues 
    */
   public XBRef getRef() {
      XBRef ref = new XBRef();
      ref.setByteSize(getSizeInBytes());
      ref.setDurable(isPersistent());
      // ref.setFlag1();
      ref.setId(getUniqueId());
      ref.setMeatId(getUniqueId());
      ref.setMetaInfo(null);
      ref.setPrio(getPriority());
      return ref;
   }

}

