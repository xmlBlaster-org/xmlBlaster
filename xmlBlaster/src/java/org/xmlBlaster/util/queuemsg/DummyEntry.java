package org.xmlBlaster.util.queuemsg;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.enum.PriorityEnum;

/**
 */
public class DummyEntry extends MsgQueueEntry {

   public static final String ENTRY_TYPE = "DUMMY";

   /**
    * This constructor is for internal creation from persistence only (passing the original timestamp). 
    */
   public DummyEntry(Global glob, PriorityEnum priority, Timestamp timestamp, StorageId storageId, boolean persistent) {
      super(glob, ENTRY_TYPE, priority, timestamp, storageId, persistent);
   }

   public DummyEntry(Global glob, PriorityEnum priority, StorageId storageId, boolean persistent) {
      super(glob, ENTRY_TYPE, priority, storageId, persistent);
      //log.error("DummyEntry", ""+getUniqueId());
   }

   /**
    * This constructor is for internal creation from persistence only (passing the original timestamp). 
    */
   public DummyEntry(Global glob, PriorityEnum priority, Timestamp timestamp, StorageId storageId, int size, boolean persistent) {
      super(glob, ENTRY_TYPE, priority, timestamp, storageId, persistent);
   }

   public DummyEntry(Global glob, PriorityEnum priority, StorageId storageId, int size, boolean persistent) {
      super(glob, ENTRY_TYPE, priority, storageId, persistent);
   }

   public Object getEmbeddedObject() {
      return null;
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

   public String getEmbeddedType() {
      return ENTRY_TYPE;
   }

   public boolean isInternal() {
      return false;
   }

   public final long getSizeInBytes() {
      return super.getSizeInBytes() + 34;
   }
}
