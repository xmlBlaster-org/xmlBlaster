/*------------------------------------------------------------------------------
Name:      ClientEntryFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementation for the I_EntryFactory
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.queuemsg;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_EntryFactory;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.queuemsg.DummyEntry;
import org.xmlBlaster.util.qos.MsgQosData;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.util.queuemsg.MsgQueueConnectEntry;

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
public class ClientEntryFactory implements I_EntryFactory
{
   private final static String ME = "ClientEntryFactory";
   private Global glob = null;
   private String name = null;
   private LogChannel log = null;

   public static final String ENTRY_TYPE_CONNECT = MethodName.CONNECT.toString();
   public static final String ENTRY_TYPE_PUBLISH = MethodName.PUBLISH.toString();
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
   public I_Entry createEntry(int priority, long timestamp, String type, boolean persistent, byte[] blob, StorageId storageId)
      throws XmlBlasterException {

      if (ENTRY_TYPE_PUBLISH.equals(type)) {
         try {
            ByteArrayInputStream bais = new ByteArrayInputStream(blob);
            ObjectInputStream objStream = new ObjectInputStream(bais);
            Object[] obj = (Object[])objStream.readObject();
            if (obj.length != 2) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                         "Expected 2 entries in serialized object stream but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp);
            }
            String oid = (String)obj[0];
            String uniqueId = (String)obj[1];
            /*
            return new MsgQueuePublishEntry(this.glob,
                                           PriorityEnum.toPriorityEnum(priority), storageId, persistent);
            */
         }
         catch (Exception ex) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "createEntry-MsgQueueHistoryEntry", ex);
         }
      }
      else if (ENTRY_TYPE_CONNECT.equals(type)) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "Entry creation from storage of type " + type + " is not implemented");
         //return msgUnitWrapper;
      }
      else if (ENTRY_TYPE_DUMMY.equals(type)) {
         DummyEntry entry = new DummyEntry(glob, PriorityEnum.toPriorityEnum(priority), new Timestamp(timestamp), storageId, persistent);
         //entry.setUniqueId(timestamp);
         return entry;
      }

      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "Object '" + type + "' not implemented");
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
   public void initialize(Global glob, String name) {
      this.glob = glob;
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

}
