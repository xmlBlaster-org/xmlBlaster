/*------------------------------------------------------------------------------
Name:      MsgQueueUpdateEntry.java
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
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.queue.StorageId;

/**
 * Wraps an publish() message into an entry for a sorted queue.
 * @author laghi@swissinfo.org
 * @author xmlBlaster@marcelruff.info
 */
public final class MsgQueueUpdateEntry extends ReferenceEntry
{
   private final static String ME = "MsgQueueUpdateEntry";
   private final String subscriptionId;
   private final String state;

   /**
    * A new message object is fed by method publish(). 
    * @param msgUnit The raw data, we keep a weak reference only on this data so it can be garbage collected
    */
   public MsgQueueUpdateEntry(Global glob, MsgUnitWrapper msgUnitWrapper, StorageId storageId, SessionName receiver,
                              String subscriptionId, String state) throws XmlBlasterException {
      super(ME, glob, ServerEntryFactory.ENTRY_TYPE_UPDATE_REF, msgUnitWrapper, storageId, receiver);
      this.subscriptionId = subscriptionId;
      this.state = state;
   }

   /**
    * For persistence recovery
    */
   public MsgQueueUpdateEntry(Global glob, PriorityEnum priority, StorageId storageId, Timestamp updateEntryTimestamp,
                              String keyOid, long msgUnitWrapperUniqueId, boolean isDurable, SessionName receiver,
                              String subscriptionId, String state, int redeliverCount) {
      super(ME, glob, ServerEntryFactory.ENTRY_TYPE_UPDATE_REF, priority, storageId,
            updateEntryTimestamp, keyOid, msgUnitWrapperUniqueId, isDurable, receiver);
      this.subscriptionId = subscriptionId;
      this.state = state;
      super.redeliverCounter = redeliverCount;
   }

   public String getSubscriptionId() {
      return this.subscriptionId;
   }

   public String getState() {
      return this.state;
   }

   /**
    * The embeddded object for this implementing class is an Object[7]
    */
   public Object getEmbeddedObject() {
      Object[] obj = { this.getUniqueIdLong(),
                       this.keyOid,
                       new Long(this.msgUnitWrapperUniqueId),
                       this.receiver.getAbsoluteName(),
                       this.subscriptionId,
                       this.state,
                       new Integer(getRedeliverCounter())
                        };
      return obj;
   }

   /**
    * Returns a shallow clone
    */
   public Object clone() {
      MsgQueueUpdateEntry entry = null;
      entry = (MsgQueueUpdateEntry)super.clone();
      return entry;
   }
}

