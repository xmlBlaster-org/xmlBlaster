/*------------------------------------------------------------------------------
Name:      MsgQueueHistoryEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.queuemsg;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.TopicHandler;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.MsgUnit;
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
                              String keyOid, long msgUnitWrapperUniqueId, boolean isDurable) {
      super(ME, glob, ServerEntryFactory.ENTRY_TYPE_UPDATE_REF, priority, storageId,
            updateEntryTimestamp, keyOid, msgUnitWrapperUniqueId, isDurable, (SessionName)null);
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

