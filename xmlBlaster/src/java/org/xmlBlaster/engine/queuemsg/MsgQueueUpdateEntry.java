/*------------------------------------------------------------------------------
Name:      MsgQueueUpdateEntry.java
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
import org.xmlBlaster.util.enum.Constants;
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
                              String subscriptionId) throws XmlBlasterException {
      super(ME, glob, ServerEntryFactory.ENTRY_TYPE_UPDATE_REF, msgUnitWrapper, storageId, receiver);
      this.getMsgQosData().setSender(msgUnitWrapper.getMsgQosData().getSender());
      this.subscriptionId = subscriptionId;
      this.state = msgUnitWrapper.getMsgUnit().getQosData().getState();
      if (log.TRACE) log.trace(ME+"-/client/"+getStorageId(), "Created new MsgQueueUpdateEntry for published message, id=" + getUniqueId() + " prio=" + priority.toString());
   }

   /**
    * For persistence recovery
    */
   public MsgQueueUpdateEntry(Global glob, PriorityEnum priority, StorageId storageId, Timestamp updateEntryTimestamp,
                              String keyOid, long msgUnitWrapperUniqueId, boolean persistent, SessionName receiver,
                              String subscriptionId, String state, int redeliverCount) {
      super(ME, glob, ServerEntryFactory.ENTRY_TYPE_UPDATE_REF, priority, storageId,
            updateEntryTimestamp, keyOid, msgUnitWrapperUniqueId, persistent, receiver);
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

   public long getSizeInBytes() {
      return 179; // This is a guess only, we have only a reference on the real data
                  // The bytes consumed are a 'new Timestamp' and a 'new MsgQueueUpdateEntry'
      // IBM JDK 1.3.1 approx 172 bytes/entry
      // SUN JDK 1.4.1 approx 179 bytes/entry
   }

   /**
    * The embeddded object for this implementing class is an Object[6]
    */
   public Object getEmbeddedObject() {
      Object[] obj = { 
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

   /**
    * Dump state of this object into XML.
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into XML.
    * @param extraOffset indenting of tags
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(1000);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      Timestamp ts = new Timestamp(msgUnitWrapperUniqueId);

      sb.append(offset).append("<MsgQueueUpdateEntry");
      sb.append(" storageId='").append(getStorageId()).append("'");
      sb.append(" keyOid='").append(getKeyOid()).append("'");
      sb.append(" msgUnitRcvTimestamp='").append(ts.toString()).append("'");
      sb.append(" sender='").append(getSender()).append("'");
      sb.append(" receiver='").append(getReceiver().getAbsoluteName()).append("'");
      sb.append(" persistent='").append(isPersistent()).append("'");
      sb.append(" subscriptionId='").append(getSubscriptionId()).append("'");
      sb.append(" redeliverCounter='").append(getRedeliverCounter()).append("'");
      sb.append(" isExpired='").append(isExpired()).append("'");
      sb.append(" isDestroyed='").append(isDestroyed()).append("'");
      sb.append(" state='").append(getState()).append("'");
      sb.append("/>");
      return sb.toString();
   }
}

