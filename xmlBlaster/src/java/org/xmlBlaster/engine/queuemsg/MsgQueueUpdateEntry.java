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
      this(glob, msgUnitWrapper, (Timestamp)null, storageId, receiver, subscriptionId);                           
   }   
   
   /**
    * convenience constructor to allow passing an already given uniqueId (the timestamp)
    * @param glob
    * @param msgUnitWrapper
    * @param timestamp
    * @param storageId
    * @param receiver
    * @param subscriptionId
    * @throws XmlBlasterException
    */
   public MsgQueueUpdateEntry(Global glob, MsgUnitWrapper msgUnitWrapper, Timestamp timestamp, StorageId storageId, SessionName receiver,
                              String subscriptionId) throws XmlBlasterException {
      super(ME, glob, ServerEntryFactory.ENTRY_TYPE_UPDATE_REF, msgUnitWrapper, timestamp, storageId, receiver);
      this.getMsgQosData().setSender(msgUnitWrapper.getMsgQosData().getSender());
      this.subscriptionId = subscriptionId;
      this.state = msgUnitWrapper.getMsgUnit().getQosData().getState();
      if (log.TRACE) log.trace(ME+"-/client/"+getStorageId(), "Created new MsgQueueUpdateEntry for published message '" + msgUnitWrapper.getLogId() + "', id=" + getUniqueId() + " prio=" + priority.toString());
   }

   /**
    * For persistence recovery
    * The params qos, key, content can be null (the unparsed raw message)
    */
   public MsgQueueUpdateEntry(Global glob, PriorityEnum priority, StorageId storageId, Timestamp updateEntryTimestamp,
                              String keyOid, long msgUnitWrapperUniqueId, boolean persistent, long sizeInBytes,
                              SessionName receiver, String subscriptionId, String state,
                              int redeliverCount,
                              String qos, String key, byte[] content) throws XmlBlasterException {
      super(ME, glob, ServerEntryFactory.ENTRY_TYPE_UPDATE_REF, priority, storageId,
            updateEntryTimestamp, keyOid, msgUnitWrapperUniqueId, persistent, receiver,
            qos, key, content);
      this.subscriptionId = subscriptionId;
      this.state = state;
      super.redeliverCounter = redeliverCount;
      if (sizeInBytes != getSizeInBytes()) {
         log.error(ME, "Internal problem: From persistence sizeInBytes=" + sizeInBytes + " but expected " + getSizeInBytes());
      }
   }

   /**
    * Copy constructor, used to get a shallow clone, we still reference the original MsgUnitWrapper. 
    */
   public MsgQueueUpdateEntry(MsgQueueUpdateEntry entry, StorageId storageId) throws XmlBlasterException {
      this(entry.getGlobal(), entry.getMsgUnitWrapper(), entry.uniqueIdTimestamp, storageId, entry.getReceiver(), entry.getSubscriptionId());
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
    * The embeddded object for this implementing class is an Object[6] for transient
    * messages and Object[9] for persistent messages.
    * <p>
    * We need to store the 'meat' for persistent messages as well
    * After a recovery the 'meat's reference counter is not valid so
    * we need to have all informations duplicated in each callback queue.
    * </p>
    */
   public Object getEmbeddedObject() {
      MsgUnitWrapper w = null;
      if (isPersistent() && ((w = getMsgUnitWrapper()) != null)) {
         Object[] meat = (Object[])w.getEmbeddedObject();
         Object[] obj = { 
                          this.keyOid,
                          new Long(this.msgUnitWrapperUniqueId),
                          this.receiver.getAbsoluteName(),
                          this.subscriptionId,
                          this.state,
                          new Integer(getRedeliverCounter()),
                          meat[0],   // QoS
                          meat[1],   // key
                          meat[2]    // content
                           };
         return obj;
      }
      else {
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
      sb.append(" id='").append(uniqueIdTimestamp.getTimestamp()).append("'");
      sb.append(" storageId='").append(getStorageId()).append("'");
      sb.append(offset).append(" keyOid='").append(getKeyOid()).append("'");
      sb.append(" msgUnitRcvTimestamp='").append(msgUnitWrapperUniqueId).append("'");
      sb.append(" msgUnitRcvTimestampStr='").append(ts.toString()).append("'");
      sb.append(offset).append(" sender='").append(getSender()).append("'");
      sb.append(" receiver='").append(getReceiver().getAbsoluteName()).append("'");
      sb.append(offset).append(" persistent='").append(isPersistent()).append("'");
      sb.append(" subscriptionId='").append(getSubscriptionId()).append("'");
      sb.append(" redeliverCounter='").append(getRedeliverCounter()).append("'");
      sb.append(offset).append(" isExpired='").append(isExpired()).append("'");
      sb.append(" isDestroyed='").append(isDestroyed()).append("'");
      sb.append(" state='").append(getState()).append("'");
      {
         MsgUnitWrapper msgUnitWrapper = getMsgUnitWrapper();
            if (msgUnitWrapper != null)
               sb.append(offset).append(msgUnitWrapper.toXml(extraOffset+Constants.INDENT));
      }
      sb.append("/>");
      return sb.toString();
   }
}

