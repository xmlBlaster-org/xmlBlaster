/*------------------------------------------------------------------------------
Name:      MsgQueueEntryFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementation for the I_QueueEntryFactory
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queuemsg;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_QueueEntryFactory;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.util.qos.MsgQosData;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;


/**
 * The implementation of the interface which can be used to convert an object
 * which implements the interface I_QueueEntry to an Object and back. This is
 * useful for example if you want to store such entries in persitent storage
 * like a database or a file system. It might however be used even for other
 * purposes.
 * @author laghi@swissinfo.org
 * @author ruff@swand.lake.de
 */
public class MsgQueueEntryFactory implements I_QueueEntryFactory
{
   private final static String ME = "MsgQueueEntryFactory";
   private Global glob = null;
   private String name = null;
//   private I_Queue queue = null;
   private LogChannel log = null;


   /**
    * Parses the specified entry to a byte array (serializing).
    */
   public byte[] toBlob(I_QueueEntry entry) throws XmlBlasterException {
      // this way we don't need to make instanceof checks, so every
      //implementation of I_QueueEntry is responsible of returning an object
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
         throw new XmlBlasterException(ME, "toBlob: " + ex.getMessage());
      }
   }

   /**
    * Parses back the raw data to a I_QueueEntry (deserializing)
    */
   public I_QueueEntry createEntry(int priority, long timestamp, String type, boolean durable, byte[] blob, I_Queue queue)
      throws XmlBlasterException {

      MethodName methodName = MethodName.toMethodName(type);
      if (methodName == MethodName.UPDATE) {
         try {
            ByteArrayInputStream bais = new ByteArrayInputStream(blob);
            ObjectInputStream objStream = new ObjectInputStream(bais);
            Object[] obj = (Object[])objStream.readObject();
            if (obj.length != 4) {
               throw new XmlBlasterException(ME, "Expected 4 entries in serialized object stream but got " + obj.length + " for priority=" + priority + " timestamp=" + timestamp);
            }
            String oid = (String)obj[0];
            MessageUnit msgUnit = (MessageUnit)obj[1];
            msgUnit.setGlobal(this.glob);
            MsgQosData msgQosData = (MsgQosData)obj[2];
            msgQosData.setGlobal(this.glob);
            SessionName receiver = new SessionName(glob, (String)obj[3]);
            // the engine.Global needs to be fixed: it is a fast hack ...
            return new MsgQueueUpdateEntry(this.glob, new Timestamp(timestamp), msgUnit, queue, oid, msgQosData, receiver);
         }
         catch (Exception ex) {
            throw new XmlBlasterException(ME, "createEntry: MsgQueueUpdateEntry. Exception " + ex.getMessage());
         }
      }
      else if (methodName == MethodName.DUMMY_ENTRY) {
         DummyEntry entry = new DummyEntry(glob, PriorityEnum.toPriorityEnum(priority), new Timestamp(timestamp), queue, durable);
         //entry.setUniqueId(timestamp);
         return entry;
      }

      throw new XmlBlasterException(ME, "Object '" + type + "' not implemented");
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
