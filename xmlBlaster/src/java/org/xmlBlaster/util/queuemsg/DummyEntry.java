package org.xmlBlaster.util.queuemsg;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.enum.PriorityEnum;

/**
 * @testcase test.org.xmlBlaster.util.queuemsg.TestDummyEntry 
 */
public class DummyEntry extends MsgQueueEntry {

   /**
    * This constructor is for internal creation from persistence only (passing the original timestamp). 
    */
   public DummyEntry(Global glob, PriorityEnum priority, Timestamp timestamp, I_Queue queue, boolean durable) {
      super(glob, MethodName.DUMMY_ENTRY, priority, timestamp, queue, durable);
   }

   public DummyEntry(Global glob, PriorityEnum priority, I_Queue queue, boolean durable) {
      super(glob, MethodName.DUMMY_ENTRY, priority, queue, durable);
   }

   /**
    * This constructor is for internal creation from persistence only (passing the original timestamp). 
    */
   public DummyEntry(Global glob, PriorityEnum priority, Timestamp timestamp, I_Queue queue, int size, boolean durable) {
      super(glob, MethodName.DUMMY_ENTRY, priority, timestamp, queue, durable);
   }

   public DummyEntry(Global glob, PriorityEnum priority, I_Queue queue, int size, boolean durable) {
      super(glob, MethodName.DUMMY_ENTRY, priority, queue, durable);
   }

   public Object getEmbeddedObject() {
      return null;
   }

   public final boolean isExpired() {
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
      return "DummyEntry";
   }

   public boolean isInternal() {
      return false;
   }

   public final long getSizeInBytes() {
      return super.getSizeInBytes() + 34;
   }
}
