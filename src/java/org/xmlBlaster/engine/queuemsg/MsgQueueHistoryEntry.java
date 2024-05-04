/*------------------------------------------------------------------------------
Name:      MsgQueueHistoryEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.queuemsg;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.jdbc.XBRef;

/**
 * Wraps an publish() message into an entry for a sorted queue.
 * @author michele@laghi.eu
 * @author xmlBlaster@marcelruff.info
 */
public final class MsgQueueHistoryEntry extends ReferenceEntry
{
   private static Logger log = Logger.getLogger(MsgQueueHistoryEntry.class.getName());
   private static final long serialVersionUID = -2967395648378724198L;
   private final static String ME = "MsgQueueHistoryEntry";
   private final boolean forceDestroy;

   /**
    * A new message object is fed by method publish(). 
    * @param msgUnit The raw data, we keep a weak reference only on this data so it can be garbage collected
    */
   public MsgQueueHistoryEntry(ServerScope glob, MsgUnitWrapper msgUnitWrapper, StorageId storageId) throws XmlBlasterException {
      super(ME, glob, ServerEntryFactory.ENTRY_TYPE_HISTORY_REF, msgUnitWrapper, (Timestamp)null, storageId, (SessionName)null);
      this.forceDestroy = msgUnitWrapper.getMsgQosData().getForceDestroyProp().getValue();
      if (log.isLoggable(Level.FINE)) log.fine("Created new MsgQueueHistoryEntry for published message, id=" + getUniqueId() + " prio=" + priority.toString());
   }

   /**
    * For persistence recovery
    */
   public MsgQueueHistoryEntry(ServerScope glob, PriorityEnum priority, StorageId storageId, Timestamp entryTimestamp,
                              String keyOid, long msgUnitWrapperUniqueId, boolean persistent, long sizeInBytes)
                              throws XmlBlasterException {
      super(ME, glob, ServerEntryFactory.ENTRY_TYPE_HISTORY_REF, priority, storageId,
            entryTimestamp, keyOid, msgUnitWrapperUniqueId, persistent, (SessionName)null,
            (String)null, (String)null, (byte[])null);
      this.forceDestroy = true; // TODO: Make flag persistent, assuming true will prevent log.error() for 'no meat found'
      if (sizeInBytes != getSizeInBytes()) {
         log.severe("Internal problem: From persistence sizeInBytes=" + sizeInBytes + " but expected " + getSizeInBytes());
      }
   }

   /**
    * TODO: Save this state in persistency and recover it
    * similar to MsgQueueUpdateEntry
    * Is needed to know if missing meat is an error
    */
   protected boolean isForceDestroy() {
      return this.forceDestroy;
   }

   /**
    * Enforced by I_QueueEntry
    * @return Allways the same int as the history queue is strictly chronologic
    */
   public int getPriority() {
      return PriorityEnum.NORM_PRIORITY.getInt();
   }

   /**
    * The negative unique creation timestamp (unique in a Global of a virtual machine)
    * Enforced by I_QueueEntry
    * @param negative nano seconds to enforce LIFO behavior (the newest message is at the front)
    */
   public long getUniqueId() {
      return (this.uniqueIdTimestamp.getTimestamp()<0L) ? this.uniqueIdTimestamp.getTimestamp() : (-1)*this.uniqueIdTimestamp.getTimestamp();
   }

   /**
    * The negative unique creation timestamp (unique in a Global of a virtual machine)
    * @param negative nano seconds to enforce LIFO behavior (the newest message is at the front)
    */
   public Long getUniqueIdLong() {
      return Long.valueOf(getUniqueId());
   }

   public long getSizeInBytes() {
      return 176; // This is a guess only, we have only a reference on the real data
                  // The bytes consumed are a 'new Timestamp' and a 'new MsgQueueHistoryEntry'
      // IBM JDK 1.3.1 approx 164 bytes/entry
      // SUN JDK 1.4.1 approx 176 bytes/entry
   }

   /**
    * Returns a shallow clone
    */
   public Object clone() {
      MsgQueueHistoryEntry entry = null;
      entry = (MsgQueueHistoryEntry)super.clone();
      return entry;
   }

   public XBRef getRef() {
      XBRef ref = super.getRef();
      Map map = new HashMap/*<String,String>*/();
      map.put(XBRef.KEY_OID, keyOid);
      map.put(XBRef.MSG_WRAPPER_ID, "" + msgUnitWrapperUniqueId);
      ref.setMetaInfo(StringPairTokenizer.mapToCSV(map));
      ref.setMeatId(msgUnitWrapperUniqueId);
      return ref;
   }
   
   
   
}

