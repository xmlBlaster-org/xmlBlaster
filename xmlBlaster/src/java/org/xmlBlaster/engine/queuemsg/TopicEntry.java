/*------------------------------------------------------------------------------
Name:      TopicEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.queuemsg;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.engine.queuemsg.ServerEntryFactory;
import org.xmlBlaster.util.enum.Constants;
//import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
//import org.xmlBlaster.engine.TopicHandler;


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
   private transient final String ME;
   private transient final Global glob;
   private transient final long uniqueId;
   private transient final String uniqueIdStr;    // cache uniqueId as String
   private transient final String embeddedType;

   private MsgUnit msgUnit;
   private final long immutableSizeInBytes;

   /**
    * Use this constructor if a new message object is fed by method publish(). 
    * <p />
    * @param msgUnit The raw data
    */
   public TopicEntry(Global glob, MsgUnit msgUnit) throws XmlBlasterException {
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
   public TopicEntry(Global glob, MsgUnit msgUnit, String embeddedType, long sizeInBytes) throws XmlBlasterException {
      this.glob = glob;
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
   public void added(StorageId storageId) throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "added("+storageId.getId()+") invocation not expected");
   }

   /**
    * Notification if this entry is removed from storage
    * @see org.xmlBlaster.util.queue.I_Entry#removed(StorageId)
    */
   public void removed(StorageId storageId) throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "removed("+storageId.getId()+") invocation not expected");
   }

   /**
    */
   public boolean isExpired() {
      return false;
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
      Global glob = new Global(args);
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
}

