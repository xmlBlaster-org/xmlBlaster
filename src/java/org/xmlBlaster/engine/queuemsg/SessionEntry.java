/*------------------------------------------------------------------------------
Name:      SessionEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine.queuemsg;

import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.jdbc.XBMeat;
import org.xmlBlaster.util.queue.jdbc.XBRef;

/**
 * SessionEntry
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class SessionEntry implements I_MapEntry {
   private static final long serialVersionUID = 1L;
   private String qos;
   private transient boolean swapped = false;
   private boolean stored;
   private long uniqueId;
   private String uniqueIdStr;
   private long size; 
   private transient Timestamp sortTimestamp;
   private transient StorageId storageId;


   /**
    * this constructor should be used by factories
    * @param qos
    * @param uniqueId
    * @param size
    * @param storageId TODO
    */
   public SessionEntry(String qos, long uniqueId, long size, StorageId storageId) {
      if (size < 1L) this.size = qos.length();
      else this.size = size;
      if (uniqueId < 1L) this.uniqueId = new Timestamp().getTimestamp();  
      this.qos = qos;
      this.uniqueId = uniqueId;
      this.uniqueIdStr = "" + this.uniqueId;
      this.storageId = storageId;
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
      return new Object[] { this.qos };
   }

   public String getQos() {
      return this.qos;
   }

   /**
    * @see org.xmlBlaster.util.queue.I_Entry#getEmbeddedType()
    */
   public String getEmbeddedType() {
      return ServerEntryFactory.ENTRY_TYPE_SESSION;
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

   /**
    * Enforced by I_Map
    * @see I_Map#isSwapped()
    */
   public boolean isSwapped() {
      return this.swapped;
   }

   /**
    * Enforced by I_Map
    * @see I_Map#isSwapped(boolean)
    */
   public void isSwapped(boolean swapped) {
      this.swapped = swapped;
   }

   /**
    * Can be used by cache implementation to implement LRU
    * @return null if not previously set by setSortTimestamp()
    */
   public final Timestamp getSortTimestamp() {
      return this.sortTimestamp;
   }

   /**
    * Can be used by cache implementation to implement LRU
    * @return timestamp This is chosen by the cache algorithm
    */
   public final void setSortTimestamp(Timestamp timestamp) {
      this.sortTimestamp = timestamp;
   }

   public final void embeddedObjectToXml(java.io.OutputStream out, java.util.Properties props) throws java.io.IOException {
      if (this.qos == null) return;
      out.write(this.qos.getBytes());
   }
   
   /**
    * For the new queues 
    */
   public XBMeat getMeat() {
      XBMeat meat = new XBMeat();
      meat.setByteSize(getSizeInBytes());
      meat.setContent(null);
      meat.setDataType(getEmbeddedType());
      meat.setDurable(isPersistent());
      meat.setId(uniqueId);
      meat.setQos(qos);
      meat.setRefCount(1);
      return meat;
   }

   /**
    * For the new queues 
    */
   public XBRef getRef() {
      XBRef ref = new XBRef();
      ref.setByteSize(getSizeInBytes());
      ref.setDurable(isPersistent());
      // ref.setFlag1();
      ref.setId(getUniqueId());
      ref.setMeatId(getUniqueId());
      ref.setMetaInfo(null);
      ref.setPrio(getPriority());
      return ref;
   }

   public StorageId getStorageId() {
      return storageId;
   }
}
