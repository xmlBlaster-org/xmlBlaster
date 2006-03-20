/*------------------------------------------------------------------------------
Name:      ClientEntryFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementation for the I_EntryFactory
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.queuemsg;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_EntryFactory;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.queuemsg.DummyEntry;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.key.EraseKey;

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
 * @author laghi@swissinfo.org
 * @author xmlBlaster@marcelruff.info
 */
public class ClientEntryFactory implements I_EntryFactory
{
   private final static String ME = "ClientEntryFactory";
   private Global glob = null;
   private static Logger log = Logger.getLogger(ClientEntryFactory.class.getName());

   /**
    * Parses the specified entry to a byte array (serializing).
    */
   public byte[] toBlob(I_Entry entry) throws XmlBlasterException {
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

      MethodName methodName = MethodName.toMethodName(type);

      try {
         ObjectInputStream objStream = new ObjectInputStream(is);
         Object[] obj = (Object[])objStream.readObject();

         if (methodName == MethodName.PUBLISH_ONEWAY || methodName == MethodName.PUBLISH) {
            if (obj.length != 3) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                  "Expected 3 entries in serialized object '" + type + "' but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp + ". Could be a version incompatibility.");
            }
            String qos = (String)obj[0];
            String key = (String)obj[1];
            byte[] content = (byte[])obj[2];
            MsgQosData msgQosData = glob.getMsgQosFactory().readObject(qos);
            MsgKeyData msgKeyData = glob.getMsgKeyFactory().readObject(key);
            MsgUnit msgUnit = new MsgUnit(msgKeyData, content, msgQosData);
            return new MsgQueuePublishEntry(glob, methodName, PriorityEnum.toPriorityEnum(priority), storageId,
                                            new Timestamp(timestamp), sizeInBytes, msgUnit);
         }
         else if (methodName == MethodName.SUBSCRIBE) {
            if (obj.length != 2) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                  "Expected 2 entries in serialized object '" + type + "' but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp + ". Could be a version incompatibility.");
            }
            String qos = (String)obj[0];
            String key = (String)obj[1];
            return new MsgQueueSubscribeEntry(glob, PriorityEnum.toPriorityEnum(priority), storageId,
                       new Timestamp(timestamp), sizeInBytes,
                       glob.getQueryKeyFactory().readObject(key),
                       glob.getQueryQosFactory().readObject(qos));

         }
         else if (methodName == MethodName.UNSUBSCRIBE) {
            if (obj.length != 2) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                  "Expected 2 entries in serialized object '" + type + "' but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp + ". Could be a version incompatibility.");
            }
            String qos = (String)obj[0];
            String key = (String)obj[1];
            return new MsgQueueUnSubscribeEntry(glob, PriorityEnum.toPriorityEnum(priority), storageId,
                       new Timestamp(timestamp), sizeInBytes,
                       new UnSubscribeKey(glob, glob.getQueryKeyFactory().readObject(key)),
                       new UnSubscribeQos(glob, glob.getQueryQosFactory().readObject(qos)) );

         }
         else if (methodName == MethodName.ERASE) {
            if (obj.length != 2) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                  "Expected 2 entries in serialized object '" + type + "' but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp + ". Could be a version incompatibility.");
            }
            String qos = (String)obj[0];
            String key = (String)obj[1];
            return new MsgQueueEraseEntry(glob, PriorityEnum.toPriorityEnum(priority), storageId,
                       new Timestamp(timestamp), sizeInBytes,
                       new EraseKey(glob, glob.getQueryKeyFactory().readObject(key)),
                       new EraseQos(glob, glob.getQueryQosFactory().readObject(qos)) );

         }
         else if (methodName == MethodName.GET) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "Object '" + type + "' not implemented, you can't use synchronous GET requests in queues.");
         }
         else if (methodName == MethodName.CONNECT) {
            if (obj.length != 1) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                  "Expected 1 entries in serialized object '" + type + "' but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp + ". Could be a version incompatibility.");
            }
            String qos = (String)obj[0];
            ConnectQosData connectQosData = glob.getConnectQosFactory().readObject(qos);
            return new MsgQueueConnectEntry(glob, PriorityEnum.toPriorityEnum(priority), storageId,
                                            new Timestamp(timestamp), sizeInBytes, connectQosData);
         }
         else if (methodName == MethodName.DISCONNECT) {
            if (obj.length != 1) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                  "Expected 1 entries in serialized object '" + type + "' but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp + ". Could be a version incompatibility.");
            }
            String qos = (String)obj[0];
            DisconnectQos disconnectQos = new DisconnectQos(glob, glob.getDisconnectQosFactory().readObject(qos));
            return new MsgQueueDisconnectEntry(glob, PriorityEnum.toPriorityEnum(priority), storageId,
                                            new Timestamp(timestamp), sizeInBytes, disconnectQos);
         }
         else if (methodName == MethodName.DUMMY) { // for testsuite only
            byte[] bytes = (byte[])obj[0];
            DummyEntry entry = new DummyEntry(glob, PriorityEnum.toPriorityEnum(priority), new Timestamp(timestamp), storageId, bytes, persistent);
            //entry.setUniqueId(timestamp);
            return entry;
         }

      }
      catch (Exception ex) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-" + methodName, ex);
      }

      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "Object '" + type + "' not implemented");
   }

   /**
    * Is called after the instance is created.
    * @param name A name identifying this plugin.
    */
   public void initialize(Global glob) {
      this.glob = glob;

      log.info("successfully initialized");
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
}

