/*------------------------------------------------------------------------------
Name:      SubscribeEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine.queuemsg;

import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.queue.StorageId;

/**
 * SubscribeEntry
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class SubscribeEntry implements I_MapEntry {

   private final String key;
   private final String qos;
   private final String sessionId;
   private boolean stored;
   private final long uniqueId;
   private final String uniqueIdStr;
   private final long size; 


   /**
    * this constructor should be used by factories
    * @param qos
    * @param uniqueId
    * @param size
    */
   public SubscribeEntry(String key, String qos, String sessionId, long uniqueId, long size) {
      if (size < 1L) this.size = qos.length() + key.length() + sessionId.length();
      else this.size = size;
      this.key = key;
      this.qos = qos;
      this.sessionId = sessionId;
      if (uniqueId < 1L) this.uniqueId = new Timestamp().getTimestamp();  
      else this.uniqueId = uniqueId;
      this.uniqueIdStr = "" + this.uniqueId;
   }

   /**
    * this constructor should be used by common users
    * @param qos
    * @param uniqueId
    */
   public SubscribeEntry(String key, String qos, String sessionId) {
      this(key, qos, sessionId, 0L, 0L);
   }

   /**
    * @see org.xmlBlaster.engine.msgstore.I_MapEntry#getUniqueIdStr()
    */
   public String getUniqueIdStr() {
      return this.uniqueIdStr;
   }

   /**
    * @see org.xmlBlaster.util.queue.I_Entry#getPriority()
    */
   public int getPriority() {
      return PriorityEnum.NORM_PRIORITY.getInt();
      
   }

   /**
    * @see org.xmlBlaster.util.queue.I_Entry#isPersistent()
    */
   public boolean isPersistent() {
      return true;
   }

   /**
    * @see org.xmlBlaster.util.queue.I_Entry#getUniqueId()
    */
   public long getUniqueId() {
      return this.uniqueId;
   }

   /**
    * @see org.xmlBlaster.util.queue.I_Entry#getEmbeddedObject()
    */
   public Object getEmbeddedObject() {
      return new Object[] { this.key, this.qos, this.sessionId };
   }

   public String getQos() {
      return this.qos;
   }

   public String getKey() {
      return this.key;
   }

   public String getSessionId() {
      return this.sessionId;
   }

   /**
    * @see org.xmlBlaster.util.queue.I_Entry#getEmbeddedType()
    */
   public String getEmbeddedType() {
      return ServerEntryFactory.ENTRY_TYPE_SUBSCRIBE;
   }

   /**
    * @see org.xmlBlaster.util.queue.I_Entry#getLogId()
    */
   public String getLogId() {
      return getEmbeddedType() + "/" + this.uniqueIdStr;
   }

   /**
    * @see org.xmlBlaster.util.queue.I_Entry#getSizeInBytes()
    */
   public long getSizeInBytes() {
      return this.size;
   }

   /**
    * @see org.xmlBlaster.util.queue.I_Entry#added(org.xmlBlaster.util.queue.StorageId)
    */
   public void added(StorageId storageId) {
   }

   /**
    * @see org.xmlBlaster.util.queue.I_Entry#removed(org.xmlBlaster.util.queue.StorageId)
    */
   public void removed(StorageId storageId) {
   }

   /**
    * @see org.xmlBlaster.util.queue.I_Entry#setStored(boolean)
    */
   public void setStored(boolean stored) {
      this.stored = stored;
   }

   /**
    * @see org.xmlBlaster.util.queue.I_Entry#isStored()
    */
   public boolean isStored() {
      return this.stored;
   }

}
