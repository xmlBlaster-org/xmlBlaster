/*------------------------------------------------------------------------------
Name:      MsgQueueHistoryEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.queuemsg;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.queue.StorageId;

/**
 * Wraps an publish() message into an entry for a sorted queue.
 * @author laghi@swissinfo.org
 * @author xmlBlaster@marcelruff.info
 */
public final class MsgQueueHistoryEntry extends ReferenceEntry
{
   private final static String ME = "MsgQueueHistoryEntry";

   /**
    * A new message object is fed by method publish(). 
    * @param msgUnit The raw data, we keep a weak reference only on this data so it can be garbage collected
    */
   public MsgQueueHistoryEntry(Global glob, MsgUnitWrapper msgUnitWrapper, StorageId storageId) throws XmlBlasterException {
      super(ME, glob, ServerEntryFactory.ENTRY_TYPE_HISTORY_REF, msgUnitWrapper, storageId, (SessionName)null);
      if (log.TRACE) log.trace(ME+"-/client/"+getStorageId(), "Created new MsgQueueHistoryEntry for published message, id=" + getUniqueId() + " prio=" + priority.toString());
   }

   /**
    * For persistence recovery
    */
   public MsgQueueHistoryEntry(Global glob, PriorityEnum priority, StorageId storageId, Timestamp updateEntryTimestamp,
                              String keyOid, long msgUnitWrapperUniqueId, boolean persistent) {
      super(ME, glob, ServerEntryFactory.ENTRY_TYPE_UPDATE_REF, priority, storageId,
            updateEntryTimestamp, keyOid, msgUnitWrapperUniqueId, persistent, (SessionName)null);
   }

   /**
    * Enforced by I_QueueEntry
    * @return Allways the same int as the history queue is strictly chronologic
    */
   public final int getPriority() {
      return PriorityEnum.NORM_PRIORITY.getInt();
   }

   /**
    * The negative unique creation timestamp (unique in a Global of a virtual machine)
    * Enforced by I_QueueEntry
    * @param negative nano seconds to enforce LIFO behavior (the newest message is at the front)
    */
   public final long getUniqueId() {
      return (this.uniqueIdTimestamp.getTimestamp()<0L) ? this.uniqueIdTimestamp.getTimestamp() : (-1)*this.uniqueIdTimestamp.getTimestamp();
   }

   /**
    * The negative unique creation timestamp (unique in a Global of a virtual machine)
    * @param negative nano seconds to enforce LIFO behavior (the newest message is at the front)
    */
   public final Long getUniqueIdLong() {
      return new Long(getUniqueId());
   }

   /**
    * Returns a shallow clone
    */
   public Object clone() {
      MsgQueueHistoryEntry entry = null;
      entry = (MsgQueueHistoryEntry)super.clone();
      return entry;
   }
}

