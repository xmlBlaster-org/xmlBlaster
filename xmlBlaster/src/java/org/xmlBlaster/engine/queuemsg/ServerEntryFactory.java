/*------------------------------------------------------------------------------
Name:      ServerEntryFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementation for the I_EntryFactory
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.queuemsg;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_EntryFactory;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.queuemsg.DummyEntry;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.queuemsg.MsgQueueHistoryEntry;
import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;

import org.xmlBlaster.engine.qos.PublishQosServer; // for main only
import org.xmlBlaster.client.key.PublishKey;       // for main only


import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;


/**
 * The implementation of the interface which can be used to convert an object
 * which implements the interface I_Entry to an Object and back. This is
 * useful for example if you want to store such entries in persitent storage
 * like a database or a file system. It might however be used even for other
 * purposes.
 * @author laghi@swissinfo.org
 * @author xmlBlaster@marcelruff.info
 */
public class ServerEntryFactory implements I_EntryFactory
{
   private final static String ME = "ServerEntryFactory";
   private Global glob = null;
   private String name = null;
   private LogChannel log = null;

   public static final String ENTRY_TYPE_MSG_SERIAL = "SER-MSG"; // msgUnit was serialized with java.io.Serializable
   public static final String ENTRY_TYPE_MSG_XML = "XML-MSG"; // msgUnit is dump as XML ASCII string
   public static final String ENTRY_TYPE_UPDATE_REF = "UPDATE_REF";
   public static final String ENTRY_TYPE_HISTORY_REF = "HISTORY_REF";
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
         this.log.error(ME, "toBlob: " + ex.getMessage());
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "toBlob()", ex);
      }
   }

   /**
    * Parses back the raw data to a I_Entry (deserializing)
    * @param type see ENTRY_TYPE_MSG etc.
    */
   public I_Entry createEntry(int priority, long timestamp, String type, boolean isDurable, byte[] blob, StorageId storageId)
      throws XmlBlasterException {

      if (ENTRY_TYPE_UPDATE_REF.equals(type)) {
         try {
            ByteArrayInputStream bais = new ByteArrayInputStream(blob);
            ObjectInputStream objStream = new ObjectInputStream(bais);
            Object[] obj = (Object[])objStream.readObject();
            if (obj.length != 7) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                         "Expected 7 entries in serialized object stream but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp);
            }
            Long uniqueId = (Long)obj[0];
            String keyOid = (String)obj[1];
            Long msgUnitWrapperUniqueId = (Long)obj[2];
            String receiverStr = (String)obj[3];
            String subscriptionId = (String)obj[4];
            String state = (String)obj[5];
            Integer redeliverCount = (Integer)obj[6];
            log.info(ME, "storageId=" + storageId + ": Read uniqueId=" + uniqueId + " topic keyOid=" + keyOid +
                         " msgUnitWrapperUniqueId=" + msgUnitWrapperUniqueId + " receiverStr=" + receiverStr +
                         " subscriptionId=" + subscriptionId + " state=" + state + " redeliverCount=" + redeliverCount);
            SessionName receiver = new SessionName(glob, receiverStr);
            Timestamp updateEntryTimestamp = new Timestamp(uniqueId.longValue());
            return new MsgQueueUpdateEntry(this.glob,
                                           PriorityEnum.toPriorityEnum(priority), storageId, updateEntryTimestamp,
                                           keyOid, msgUnitWrapperUniqueId.longValue(), isDurable, receiver,
                                           subscriptionId, state, redeliverCount.intValue());
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-MsgQueueUpdateEntry", ex);
         }
      }
      else if (ENTRY_TYPE_HISTORY_REF.equals(type)) {
         try {
            ByteArrayInputStream bais = new ByteArrayInputStream(blob);
            ObjectInputStream objStream = new ObjectInputStream(bais);
            Object[] obj = (Object[])objStream.readObject();
            if (obj.length != 3) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                         "Expected 3 entries in serialized object stream but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp);
            }
            Long uniqueId = (Long)obj[0];
            String keyOid = (String)obj[1];
            Long msgUnitWrapperUniqueId = (Long)obj[2];
            Timestamp updateEntryTimestamp = new Timestamp(uniqueId.longValue());
            return new MsgQueueHistoryEntry((org.xmlBlaster.engine.Global)this.glob,
                                           PriorityEnum.toPriorityEnum(priority), storageId, updateEntryTimestamp,
                                           keyOid, msgUnitWrapperUniqueId.longValue(), isDurable);
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-MsgQueueHistoryEntry", ex);
         }
      }
      else if (ENTRY_TYPE_MSG_XML.equals(type)) {
         try {
            ByteArrayInputStream bais = new ByteArrayInputStream(blob);
            ObjectInputStream objStream = new ObjectInputStream(bais);
            Object[] obj = (Object[])objStream.readObject();
            if (obj.length != 5) {
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
            MsgUnit msgUnit = new MsgUnit(glob, msgKeyData, content, publishQosServer.getData());
            MsgUnitWrapper msgUnitWrapper = new MsgUnitWrapper(glob, msgUnit, storageId,
                                      referenceCounter.intValue(), historyReferenceCounter.intValue());
            return msgUnitWrapper;
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-MsgUnitWrapper", ex);
         }
      }
      else if (ENTRY_TYPE_MSG_SERIAL.equals(type)) {
         try {
            ByteArrayInputStream bais = new ByteArrayInputStream(blob);
            ObjectInputStream objStream = new ObjectInputStream(bais);
            Object[] obj = (Object[])objStream.readObject();
            if (obj.length != 3) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                         "Expected 3 entries in serialized object stream but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp);
            }
            MsgUnit msgUnit = (MsgUnit)obj[0];
            Integer referenceCounter = (Integer)obj[1];
            Integer historyReferenceCounter = (Integer)obj[2];
            msgUnit.setGlobal(glob);
            MsgUnitWrapper msgUnitWrapper = new MsgUnitWrapper(glob, msgUnit, storageId,
                             referenceCounter.intValue(), historyReferenceCounter.intValue());
            return msgUnitWrapper;
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-MsgUnitWrapper", ex);
         }
      }
      else if (ENTRY_TYPE_DUMMY.equals(type)) {
         DummyEntry entry = new DummyEntry(glob, PriorityEnum.toPriorityEnum(priority), new Timestamp(timestamp), storageId, isDurable);
         //entry.setUniqueId(timestamp);
         return entry;
      }

      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "Persistent object '" + type + "' is not implemented");
   }

   /**
    * Returns the name of this plugin
    */
   public String getName() {
      return this.name;
   }


   /**
    * Is called after the instance is created.
    * @param name A name identifying this plugin.
    */
   public void initialize(org.xmlBlaster.util.Global glob, String name) {
      this.glob = (org.xmlBlaster.engine.Global)glob;
      this.name = name;
      this.log = glob.getLog("queue");
      this.log.info(ME, "successfully initialized");
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
      Global glob = new Global(args);
      LogChannel log = glob.getLog("test");
      try {
         String[] persistType = new String[] { ENTRY_TYPE_MSG_SERIAL, ENTRY_TYPE_MSG_XML };

         String ME;
         int numRuns = 4;
         for(int ii=0; ii<numRuns; ii++) {
            for(int jj=0; jj<persistType.length; jj++) {
               PublishKey publishKey = new PublishKey(glob, "HA");
               PublishQosServer publishQosServer = new PublishQosServer(glob, "<qos><isDurable/></qos>");
               publishQosServer.getData().setPriority(PriorityEnum.HIGH_PRIORITY);
               MsgUnit msgUnit = new MsgUnit(glob, publishKey.getData(), "HO".getBytes(), publishQosServer.getData());
               StorageId storageId = new StorageId("mystore", "someid");
               MsgUnitWrapper msgUnitWrapper = new MsgUnitWrapper(glob, msgUnit, storageId, 0, 0, persistType[jj]);

               I_EntryFactory factory = glob.getEntryFactory(storageId.getStrippedId());

               int priority = msgUnitWrapper.getPriority();
               long timestamp = msgUnitWrapper.getUniqueId();
               String type = msgUnitWrapper.getEmbeddedType();
               boolean isDurable = msgUnitWrapper.isDurable();

               ME = persistType[jj];

               int numTransform = 1000;
               org.jutils.time.StopWatch stopWatchToBlob = new org.jutils.time.StopWatch();
               for(int kk=0; kk<numTransform; kk++) {
                  byte[] blob = factory.toBlob(msgUnitWrapper);
               }
               double elapsed = stopWatchToBlob.elapsed();
               log.info(ME, "num toBlob=" + numTransform + " elapsed=" + elapsed + stopWatchToBlob.nice());

               byte[] blob = factory.toBlob(msgUnitWrapper);
               MsgUnitWrapper newWrapper = null;
               org.jutils.time.StopWatch stopWatchToObj = new org.jutils.time.StopWatch();
               for(int kk=0; kk<numTransform; kk++) {
                  newWrapper = (MsgUnitWrapper)factory.createEntry(priority,
                                              timestamp, type, isDurable, blob, storageId);
               }
               elapsed = stopWatchToObj.elapsed();
               log.info(ME, "num toObj=" + numTransform + " elapsed=" + elapsed + stopWatchToObj.nice());
       
               log.trace(ME, "SUCESS BEFORE: " + msgUnitWrapper.toXml());
               log.trace(ME, "SUCESS AFTER: " + newWrapper.toXml());
            }
         }
      }
      catch (XmlBlasterException e) {
         System.out.println("ERROR " + e.getMessage());
      }
   }
}
