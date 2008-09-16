/*------------------------------------------------------------------------------
Name:      ServerEntryFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementation for the I_EntryFactory
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.queuemsg;

import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_EntryFactory;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.jdbc.XBMeat;
import org.xmlBlaster.util.queue.jdbc.XBRef;
import org.xmlBlaster.util.queue.jdbc.XBStore;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.queuemsg.DummyEntry;
import org.xmlBlaster.util.key.MsgKeyData;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.engine.MsgUnitWrapper;

import org.xmlBlaster.engine.qos.PublishQosServer; // for main only
import org.xmlBlaster.client.key.PublishKey;       // for main only


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;


/**
 * The implementation of the interface which can be used to convert an object
 * which implements the interface I_Entry to an Object and back. This is
 * useful for example if you want to store such entries in persistent storage
 * like a database or a file system. It might however be used even for other
 * purposes.
 * @author michele@laghi.eu
 * @author xmlBlaster@marcelruff.info
 */
public class ServerEntryFactory implements I_EntryFactory
{
   private final static String ME = "ServerEntryFactory";
   private ServerScope glob = null;
   private static Logger log = Logger.getLogger(ServerEntryFactory.class.getName());

   public static final String ENTRY_TYPE_MSG_SERIAL = "MSG_SER"; // msgUnit was serialized with java.io.Serializable
   public static final String ENTRY_TYPE_MSG_XML = "MSG_XML"; // msgUnit is dumped as XML ASCII string
   public static final String ENTRY_TYPE_MSG_RAW = "MSG_RAW"; // msgUnit is dumped as specified in the protocol.socket requirement (see C persistent queue)
   public static final String ENTRY_TYPE_UPDATE_REF = "UPDATE_REF";
   public static final String ENTRY_TYPE_HISTORY_REF = "HISTORY_REF";
   public static final String ENTRY_TYPE_TOPIC_SERIAL = "TOPIC_SER";
   public static final String ENTRY_TYPE_TOPIC_XML = "TOPIC_XML";
   public static final String ENTRY_TYPE_SESSION = "SESSION";
   public static final String ENTRY_TYPE_SUBSCRIBE = "SUBSCRIBE";
   public static final String ENTRY_TYPE_DUMMY = DummyEntry.ENTRY_TYPE;

   /**
    * Parses the specified entry to a byte array (serializing).
    */
   public byte[] toBlob(I_Entry entry) throws XmlBlasterException {
      // this way we don't need to make instanceof checks, so every
      //implementation of I_Entry is responsible of returning an object
      // it wants to store in the db
//      return entry.getEmbeddedObject();
      try {
         Object obj = entry.getEmbeddedObject();
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ObjectOutputStream objStream = new ObjectOutputStream(baos);
         objStream.writeObject(obj);
         return baos.toByteArray();
      }
      catch (IOException ex) {
         log.severe("toBlob: " + ex.getMessage());
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "toBlob()", ex);
      }
   }

   /**
    * Parses back the raw data to a I_Entry (deserializing)
    * @param type see ENTRY_TYPE_MSG etc.
    */
   public I_Entry createEntry(int priority, long timestamp, String type,
                              boolean persistent, long sizeInBytes, InputStream is, StorageId storageId)
      throws XmlBlasterException {

      if (is == null) {
         String txt = "Entry with data prio='" + priority + "' timestamp='" + timestamp + "' type='" + type + "' persitent='" + persistent + "' size='" + sizeInBytes + "' storageId='" + storageId + "' has a null stream";
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-MsgQueueUpdateEntry: " + txt);
      }

      if (ENTRY_TYPE_UPDATE_REF.equalsIgnoreCase(type)) { // still used
         try {
            ObjectInputStream objStream = new ObjectInputStream(is);
            Object[] obj = (Object[])objStream.readObject();
            if (obj.length < 6) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                  "Expected 6 entries in serialized object '" + type + "' but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp + ". Could be a version incompatibility.");
            }
            String keyOid = (String)obj[0];
            Long msgUnitWrapperUniqueId = (Long)obj[1];
            String receiverStr = (String)obj[2];
            String subscriptionId = (String)obj[3];
            String flag = (String)obj[4]; // was state in older release
            Integer redeliverCount = (Integer)obj[5];

            // We read the message content as well but don't parse it yet:
            String qos = null;
            String key = null;
            byte[] content = null;
            if ( ReferenceEntry.STRICT_REFERENCE_COUNTING_COMPATIBLE ) {
               if (obj.length >= 9) {
                  // deprecated, remove this code in future
                  // see ENTRY_TYPE_MSG_XML !
                  qos = (String)obj[6];
                  key = (String)obj[7];
                  content = (byte[])obj[8];
                  //Integer referenceCounter = (Integer)obj[9];
                  //Integer historyReferenceCounter = (Integer)obj[10];
               }
            }

            if (log.isLoggable(Level.FINE)) log.fine("storageId=" + storageId + ": Read timestamp=" + timestamp + " topic keyOid=" + keyOid +
                         " msgUnitWrapperUniqueId=" + msgUnitWrapperUniqueId + " receiverStr=" + receiverStr +
                         " subscriptionId=" + subscriptionId + " flag=" + flag + " redeliverCount=" + redeliverCount);
            SessionName receiver = new SessionName(glob, receiverStr);
            Timestamp updateEntryTimestamp = new Timestamp(timestamp);
            return new MsgQueueUpdateEntry(this.glob,
                                           PriorityEnum.toPriorityEnum(priority), storageId, updateEntryTimestamp,
                                           keyOid, msgUnitWrapperUniqueId.longValue(), persistent, sizeInBytes,
                                           receiver, subscriptionId, flag, redeliverCount.intValue(),
                                           qos, key, content);
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-MsgQueueUpdateEntry", ex);
         }
      }
      else if (ENTRY_TYPE_HISTORY_REF.equalsIgnoreCase(type)) { // still used
         try {
            ObjectInputStream objStream = new ObjectInputStream(is);
            Object[] obj = (Object[])objStream.readObject();
            if (obj.length < 2) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                  "Expected 2 entries in serialized object '" + type + "' but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp + ". Could be a version incompatibility.");
            }
            String keyOid = (String)obj[0];
            Long msgUnitWrapperUniqueId = (Long)obj[1];
            Timestamp updateEntryTimestamp = new Timestamp(timestamp);
            return new MsgQueueHistoryEntry(this.glob,
                                           PriorityEnum.toPriorityEnum(priority), storageId, updateEntryTimestamp,
                                           keyOid, msgUnitWrapperUniqueId.longValue(), persistent, sizeInBytes);
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-MsgQueueHistoryEntry", ex);
         }
      }
      else if (ENTRY_TYPE_MSG_XML.equalsIgnoreCase(type)) { // still used
         try {
            ObjectInputStream objStream = new ObjectInputStream(is);
            Object[] obj = (Object[])objStream.readObject();
            if (obj.length < 5) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                         "Expected 5 entries in serialized object stream but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp);
            }
            String qos = (String)obj[0];
            String key = (String)obj[1];
            byte[] content = (byte[])obj[2];
            Integer referenceCounter = (Integer)obj[3];
            Integer historyReferenceCounter = (Integer)obj[4];
            PublishQosServer publishQosServer = new PublishQosServer(glob, qos, true); // true marks from persistent store (prevents new timestamp)
            MsgKeyData msgKeyData = glob.getMsgKeyFactory().readObject(key);
            MsgUnit msgUnit = new MsgUnit(msgKeyData, content, publishQosServer.getData());
            MsgUnitWrapper msgUnitWrapper = new MsgUnitWrapper(glob, msgUnit, storageId,
                                      referenceCounter.intValue(), historyReferenceCounter.intValue(), sizeInBytes);
            msgUnitWrapper.startExpiryTimer();
            return msgUnitWrapper;
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-MsgUnitWrapper", ex);
         }
      }
      else if (ENTRY_TYPE_MSG_SERIAL.equalsIgnoreCase(type)) {  // probably unused (not found in my tests)
         try {
            ObjectInputStream objStream = new ObjectInputStream(is);
            Object[] obj = (Object[])objStream.readObject();
            if (obj.length < 3) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                         "Expected 3 entries in serialized object stream but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp);
            }
            MsgUnit msgUnit = (MsgUnit)obj[0];
            Integer referenceCounter = (Integer)obj[1];
            Integer historyReferenceCounter = (Integer)obj[2];
            msgUnit.setGlobal(glob);
            MsgUnitWrapper msgUnitWrapper = new MsgUnitWrapper(glob, msgUnit, storageId,
                             referenceCounter.intValue(), historyReferenceCounter.intValue(), sizeInBytes);
            msgUnitWrapper.startExpiryTimer();
            return msgUnitWrapper;
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-MsgUnitWrapper", ex);
         }
      }

      else if (ENTRY_TYPE_TOPIC_XML.equalsIgnoreCase(type)) { // still used
         try {
            ObjectInputStream objStream = new ObjectInputStream(is);
            Object[] obj = (Object[])objStream.readObject();
            if (obj.length < 2) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                         "Expected 2 entries in serialized object stream but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp);
            }
            String qos = (String)obj[0];
            String key = (String)obj[1];
            byte[] content = null;
            PublishQosServer publishQosServer = new PublishQosServer(glob, qos, true); // true marks from persistent store (prevents new timestamp)
            MsgKeyData msgKeyData = glob.getMsgKeyFactory().readObject(key);
            MsgUnit msgUnit = new MsgUnit(msgKeyData, content, publishQosServer.getData());
            TopicEntry topicEntry = new TopicEntry(glob, msgUnit, type, sizeInBytes);
            return topicEntry;
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-TopicEntry", ex);
         }
      }
      else if (ENTRY_TYPE_TOPIC_SERIAL.equalsIgnoreCase(type)) { // probably unused (not found in my tests)
         try {
            ObjectInputStream objStream = new ObjectInputStream(is);
            Object[] obj = (Object[])objStream.readObject();
            if (obj.length < 1) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                         "Expected 1 entry in serialized object stream but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp);
            }
            MsgUnit msgUnit = (MsgUnit)obj[0];
            msgUnit.setGlobal(glob);
            TopicEntry topicEntry = new TopicEntry(glob, msgUnit, type, sizeInBytes);
            return topicEntry;
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-TopicEntry", ex);
         }
      }

      else if (ENTRY_TYPE_SESSION.equalsIgnoreCase(type)) {  // still used
         try {
            ObjectInputStream objStream = new ObjectInputStream(is);
            Object[] obj = (Object[])objStream.readObject();
            if (obj.length < 1) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                         "Expected 1 entry in serialized object stream but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp);
            }
            String xmlLiteral = (String)obj[0];
            SessionEntry sessionEntry = new SessionEntry(xmlLiteral, timestamp, sizeInBytes);
            return sessionEntry;
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-TopicEntry", ex);
         }
      }
      else if (ENTRY_TYPE_SUBSCRIBE.equalsIgnoreCase(type)) {  // still used
         try {
            ObjectInputStream objStream = new ObjectInputStream(is);
            Object[] obj = (Object[])objStream.readObject();
            if (obj.length < 3) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                         "Expected 3 entries in serialized object stream but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp);
            }
            String keyLiteral = (String)obj[0];
            String qosLiteral = (String)obj[1];
            String sessionName = (String)obj[2];
            SubscribeEntry subscribeEntry = new SubscribeEntry(keyLiteral, qosLiteral, sessionName, timestamp, sizeInBytes);
            return subscribeEntry;
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-TopicEntry", ex);
         }
      }

      else if (ENTRY_TYPE_DUMMY.equalsIgnoreCase(type)) { // still used (for testing)
         DummyEntry entry = new DummyEntry(glob, PriorityEnum.toPriorityEnum(priority), new Timestamp(timestamp), storageId, sizeInBytes, persistent);
         //entry.setUniqueId(timestamp);
         return entry;
      }

      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "Persistent object '" + type + "' is not implemented");
   }

   /**
    * Is called after the instance is created.
    * @param name A name identifying this plugin.
    */
   public void initialize(org.xmlBlaster.util.Global glob) {
      this.glob = (org.xmlBlaster.engine.ServerScope)glob;

      if (log.isLoggable(Level.FINE)) log.fine("Successfully initialized");
   }

   /**
    * Allows to overwrite properties which where passed on initialize()
    * The properties which support hot configuration are depending on the used implementation
    */
   public void setProperties(Object userData) {
   }

   /**
    * Access the current Parser configuration
    */
   public Object getProperties() {
      return null;
   }

   /**
    * Measure performance for XML-ASCII versus java.io.Serializable persistence.
    * <pre>
    * java org.xmlBlaster.engine.queuemsg.ServerEntryFactory
    * </pre>
    */
   public static void main(String[] args) {
      ServerScope glob = new ServerScope(args);

      try {
         String[] persistType = new String[] { ENTRY_TYPE_MSG_SERIAL, ENTRY_TYPE_MSG_XML };

         int numRuns = 4;
         for(int ii=0; ii<numRuns; ii++) {
            for(int jj=0; jj<persistType.length; jj++) {
               PublishKey publishKey = new PublishKey(glob, "HA");
               PublishQosServer publishQosServer = new PublishQosServer(glob, "<qos><persistent/></qos>");
               publishQosServer.getData().setPriority(PriorityEnum.HIGH_PRIORITY);
               MsgUnit msgUnit = new MsgUnit(publishKey.getData(), "HO".getBytes(), publishQosServer.getData());
               StorageId storageId = new StorageId("mystore", "someid");
               MsgUnitWrapper msgUnitWrapper = new MsgUnitWrapper(glob, msgUnit, null, storageId, 0, 0, persistType[jj], -1);
               msgUnitWrapper.startExpiryTimer();
               I_EntryFactory factory = glob.getEntryFactory();

               int priority = msgUnitWrapper.getPriority();
               long timestamp = msgUnitWrapper.getUniqueId();
               String type = msgUnitWrapper.getEmbeddedType();
               boolean persistent = msgUnitWrapper.isPersistent();
               long sizeInBytes = msgUnitWrapper.getSizeInBytes();

               int numTransform = 1000;
               org.xmlBlaster.util.StopWatch stopWatchToBlob = new org.xmlBlaster.util.StopWatch();
               for(int kk=0; kk<numTransform; kk++) {
                  /*byte[] blob =*/ factory.toBlob(msgUnitWrapper);
               }
               double elapsed = stopWatchToBlob.elapsed();
               log.info("num toBlob=" + numTransform + " elapsed=" + elapsed + stopWatchToBlob.nice());

               byte[] blob = factory.toBlob(msgUnitWrapper);
               MsgUnitWrapper newWrapper = null;
               org.xmlBlaster.util.StopWatch stopWatchToObj = new org.xmlBlaster.util.StopWatch();
               for(int kk=0; kk<numTransform; kk++) {
                  newWrapper = (MsgUnitWrapper)factory.createEntry(priority,
                                              timestamp, type, persistent, sizeInBytes, new ByteArrayInputStream(blob), storageId);
               }
               elapsed = stopWatchToObj.elapsed();
               log.info("num toObj=" + numTransform + " elapsed=" + elapsed + stopWatchToObj.nice());

               log.fine("SUCESS BEFORE: " + msgUnitWrapper.toXml());
               log.fine("SUCESS AFTER: " + newWrapper.toXml());
            }
         }
      }
      catch (XmlBlasterException e) {
         System.out.println("ERROR " + e.getMessage());
      }
   }
   

   private Map getCSV(String csv) {
      Map map = null;
      if (csv != null)
         map = StringPairTokenizer.CSVToMap(csv);
      else
         map = new HashMap/*<String,String>*/();
      return map;
   }

   private StorageId getStorageId(XBStore store) {
      return new StorageId(store.getType(), store.getType() + store.getPostfix());
   }
   
   
   public I_Entry createEntry(XBStore store, XBMeat meat, XBRef ref) throws XmlBlasterException {
      // throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-MsgQueueUpdateEntry: " + txt);
      
      StorageId storageId = getStorageId(store);
      String type = store.getType();
      long timestamp = ref.getId();
      Map map = null;
      if (ref != null)
         map = getCSV(ref.getMetaInfo());
      else
         map = new HashMap/*<String,String>*/();
      if (ENTRY_TYPE_UPDATE_REF.equalsIgnoreCase(type)) { // still used
         try {
            String keyOid = (String)map.get(XBRef.KEY_OID);
            long msgUnitWrapperUniqueId = Long.parseLong((String)map.get(XBRef.MSG_WRAPPER_ID));
            String receiverStr = (String)map.get(XBRef.RECEIVER_STR);
            String subscriptionId = (String)map.get(XBRef.SUB_ID);
            String flag = (String)map.get(XBRef.FLAG);
            int redeliverCount = Integer.parseInt((String)map.get(XBRef.REDELIVER_COUNTER));
            
            // We read the message content as well but don't parse it yet:
            String qos = null;
            String key = null;
            byte[] content = null;
            if (meat != null) {
               content = meat.getContent();
               key = meat.getKey();
               qos = meat.getQos();
            }
               
            if (log.isLoggable(Level.FINE)) log.fine("storageId=" + store.toString() + ": Read timestamp=" + timestamp + " topic keyOid=" + keyOid +
                         " msgUnitWrapperUniqueId=" + msgUnitWrapperUniqueId + " receiverStr=" + receiverStr +
                         " subscriptionId=" + subscriptionId + " flag=" + flag + " redeliverCount=" + redeliverCount);
            SessionName receiver = new SessionName(glob, receiverStr);
            Timestamp updateEntryTimestamp = new Timestamp(timestamp);
            
            return new MsgQueueUpdateEntry(glob,
                                           PriorityEnum.toPriorityEnum(ref.getPrio()), storageId, updateEntryTimestamp,
                                           keyOid, msgUnitWrapperUniqueId, ref.isDurable(), ref.getByteSize(),
                                           receiver, subscriptionId, flag, redeliverCount,
                                           qos, key, content);
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-MsgQueueUpdateEntry", ex);
         }
      }
      else if (ENTRY_TYPE_HISTORY_REF.equalsIgnoreCase(type)) { // still used
         try {
            String keyOid = (String)map.get(XBRef.KEY_OID);
            long msgUnitWrapperUniqueId = Long.parseLong((String)map.get(XBRef.MSG_WRAPPER_ID));
            Timestamp updateEntryTimestamp = new Timestamp(timestamp);
            return new MsgQueueHistoryEntry(glob,
                                           PriorityEnum.toPriorityEnum(ref.getPrio()), storageId, updateEntryTimestamp,
                                           keyOid, msgUnitWrapperUniqueId, ref.isDurable(), ref.getByteSize());
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-MsgQueueHistoryEntry", ex);
         }
      }
      else if (ENTRY_TYPE_MSG_XML.equalsIgnoreCase(type)) { // still used
         try {
            String qos = meat.getQos();
            String key = meat.getKey();
            byte[] content = meat.getContent();
            long referenceCounter = meat.getRefCount();
            int historyReferenceCounter = Integer.parseInt((String)meat.getDataType());
            PublishQosServer publishQosServer = new PublishQosServer(glob, qos, true); // true marks from persistent store (prevents new timestamp)
            MsgKeyData msgKeyData = glob.getMsgKeyFactory().readObject(key);
            MsgUnit msgUnit = new MsgUnit(msgKeyData, content, publishQosServer.getData());
            MsgUnitWrapper msgUnitWrapper = new MsgUnitWrapper(glob, msgUnit, storageId,
                                      (int)referenceCounter, historyReferenceCounter, meat.getByteSize());
            msgUnitWrapper.startExpiryTimer();
            return msgUnitWrapper;
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-MsgUnitWrapper", ex);
         }
      }
      else if (ENTRY_TYPE_TOPIC_XML.equalsIgnoreCase(type)) { // still used
         try {
            String qos = meat.getQos();
            String key = meat.getKey();
            byte[] content = null;
            PublishQosServer publishQosServer = new PublishQosServer(glob, qos, true); // true marks from persistent store (prevents new timestamp)
            MsgKeyData msgKeyData = glob.getMsgKeyFactory().readObject(key);
            MsgUnit msgUnit = new MsgUnit(msgKeyData, content, publishQosServer.getData());
            TopicEntry topicEntry = new TopicEntry(glob, msgUnit, type, meat.getByteSize());
            return topicEntry;
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-TopicEntry", ex);
         }
      }
      else if (ENTRY_TYPE_SESSION.equalsIgnoreCase(type)) {  // still used
         try {
            String xmlLiteral = meat.getQos();
            SessionEntry sessionEntry = new SessionEntry(xmlLiteral, timestamp, meat.getByteSize());
            return sessionEntry;
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-TopicEntry", ex);
         }
      }
      else if (ENTRY_TYPE_SUBSCRIBE.equalsIgnoreCase(type)) {  // still used
         try {
            String keyLiteral = meat.getKey();
            String qosLiteral = meat.getQos();
            String sessionName = meat.getDataType();
            SubscribeEntry subscribeEntry = new SubscribeEntry(keyLiteral, qosLiteral, sessionName, timestamp, meat.getByteSize());
            return subscribeEntry;
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-TopicEntry", ex);
         }
      }

      else if (ENTRY_TYPE_DUMMY.equalsIgnoreCase(type)) { // still used (for testing)
         DummyEntry entry = new DummyEntry(glob, PriorityEnum.toPriorityEnum(ref.getPrio()), new Timestamp(timestamp), storageId, meat.getByteSize(), ref.isDurable());
         //entry.setUniqueId(timestamp);
         return entry;
      }

      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "Persistent object '" + type + "' is not implemented");
   }
   
   
}
