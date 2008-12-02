package org.xmlBlaster.util.queuemsg;

import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.queue.StorageId;

/**
 */
public class DummyEntry extends MsgQueueEntry {

   private static final long serialVersionUID = 1L;
   private static Logger log = Logger.getLogger(DummyEntry.class.getName());
   public static final String ME = "DummyEntry";
   public static final String ENTRY_TYPE = "DUMMY";
   private long sizeOfMsg = 0;
   private byte[] content;
   
   /**
    * This constructor is for internal creation from persistence only (passing the original timestamp). 
    */
/*
   public DummyEntry(Global glob, PriorityEnum priority, Timestamp timestamp, StorageId storageId, boolean persistent) {
      super(glob, ENTRY_TYPE, priority, timestamp, storageId, persistent);
   }
*/
   public DummyEntry(Global glob, PriorityEnum priority, Timestamp timestamp, StorageId storageId, long sizeOfMsg, boolean persistent) {
      super(glob, ENTRY_TYPE, priority, timestamp, storageId, persistent);
      this.sizeOfMsg = sizeOfMsg;
      if (this.sizeOfMsg != 0) {
         if (this.sizeOfMsg >= Integer.MAX_VALUE)
            log.warning("the size of the content has been reduced because it did not fit inside a byte[]");
         this.content = new byte[(int)this.sizeOfMsg];
      }
   }

   /**
    * Called by ClientEntryFactory and ServerEntryFactory only
    */
   public DummyEntry(Global glob, PriorityEnum priority, Timestamp timestamp, StorageId storageId, long sizeInBytes,
         byte[] content, boolean persistent) {
      super(glob, ENTRY_TYPE, priority, timestamp, storageId, persistent);
      this.content = content;
      this.sizeOfMsg = sizeInBytes;
      // if (this.content != null) this.sizeOfMsg = (long)this.content.length;
   }

   public DummyEntry(Global glob, PriorityEnum priority, StorageId storageId, boolean persistent) {
      super(glob, ENTRY_TYPE, priority, storageId, persistent);
      //log.error("DummyEntry", ""+getUniqueId());
   }

   public DummyEntry(Global glob, PriorityEnum priority, StorageId storageId, long sizeOfMsg, boolean persistent) {
      super(glob, ENTRY_TYPE, priority, storageId, persistent);
      this.sizeOfMsg = sizeOfMsg;
      if (this.sizeOfMsg != 0) {
         if (this.sizeOfMsg >= Integer.MAX_VALUE)
            log.warning("the size of the content has been reduced because it did not fit inside a byte[]");
         this.content = new byte[(int)this.sizeOfMsg];
      }
   }

   /**
    * This constructor is for internal creation from persistence only (passing the original timestamp). 
    */
/*
   public DummyEntry(Global glob, PriorityEnum priority, Timestamp timestamp, StorageId storageId, int size, boolean persistent) {
      super(glob, ENTRY_TYPE, priority, timestamp, storageId, persistent);
   }

   public DummyEntry(Global glob, PriorityEnum priority, StorageId storageId, int size, boolean persistent) {
      super(glob, ENTRY_TYPE, priority, storageId, persistent);
   }
*/
   public Object getEmbeddedObject() {
      return new Object[] {this.content};
   }

   public final boolean isExpired() {
      return false;
   }

   /**
    * @see MsgQueueEntry#isDestroyed
    */
   public final boolean isDestroyed() {
      return false;
   }

   public final SessionName getSender() {
      return new SessionName(glob, "theSender");
   }

   public final SessionName getReceiver() {
      return new SessionName(glob, "theReceiver");
   }

   public final String getKeyOid() {
      return "theOid";
   }
   
   public MsgUnit getMsgUnit() throws XmlBlasterException {
      return new MsgUnit(glob, (String) null, (byte[]) null, "<qos/>");
   }

   public String getEmbeddedType() {
      return ENTRY_TYPE;
   }

   public boolean isInternal() {
      return false;
   }

   public final long getSizeInBytes() {
      if (this.sizeOfMsg == 0)
         return super.getSizeInBytes() + 34;
      else return this.sizeOfMsg;
   }

   public final void setPersistent(boolean persistent) {
      this.persistent = persistent;
   }

   public final void embeddedObjectToXml(java.io.OutputStream out, java.util.Properties props) throws java.io.IOException {
      if (this.content != null)
         out.write(this.content);
   }

}
