/*------------------------------------------------------------------------------
Name:      MapPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.msgstore.ram;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
// import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.queue.I_StoragePlugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.util.queue.I_StorageProblemNotifier;
import org.xmlBlaster.util.queue.I_StorageProblemListener;

import java.util.TreeMap;
import java.util.Map;


/**
 * Mapping messages in RAM only. 
 * Please refer to I_Map for Javadoc comments.
 * @see org.xmlBlaster.test.classtest.msgstore.I_MapTest 
 * @author xmlBlaster@marcelruff.info
 */
public final class MapPlugin implements I_Map, I_StoragePlugin
{
   private String ME = "MapPlugin";
   private StorageId mapId;
   private Map storage;
   private QueuePropertyBase property; // org.xmlBlaster.util.qos.storage.MsgUnitStoreProperty;
   private Global glob;
   private LogChannel log;
   private boolean isShutdown = false;
   private long sizeInBytes;
   private long persistentSizeInBytes;
   private long numOfPersistentEntries;
   private PluginInfo pluginInfo;

   /**
    * Is called after the instance is created.
    * @see org.xmlBlaster.engine.msgstore.I_Map#initialize(StorageId, Object)
    */
   public void initialize(StorageId uniqueMapId, Object userData) throws XmlBlasterException {
      setProperties(userData); // sets this.property
      this.glob = this.property.getGlobal();
      this.log = glob.getLog("persistence");


      this.mapId = uniqueMapId;
      if (mapId == null || glob == null) {
         Thread.currentThread().dumpStack();
         throw new IllegalArgumentException("Illegal arguments in MapPlugin constructor: mapId=" + mapId);
      }

      this.ME = "MapPlugin-" + mapId;

      if (property.getMaxMsg() > Integer.MAX_VALUE) throw new XmlBlasterException(ME, "initialize: The maximum number of messages is too big");
      
      this.storage = new TreeMap(); // Note: A HashMap works fine as well, but then there is no sorting with getAll() -> do we need it?
      this.isShutdown = false;
   }

   public void setProperties(Object userData) throws XmlBlasterException {
      if (userData == null) return;
      QueuePropertyBase newProp;
      try {
         newProp = (QueuePropertyBase)userData;
      }
      catch(Throwable e) { // this.log is still null
         throw XmlBlasterException.convert(this.glob, ME, "Can't configure topic cache, your properties are invalid", e); // glob is allowed to be null
      }

      this.property = newProp;
   }

   public Object getProperties() {
      return this.property;
   }

   public final StorageId getStorageId() {
      return mapId;
   }

   public void finalize() {
      if (log.TRACE) log.trace(ME, "finalize - garbage collected");
   }

   /**
    * @see I_Map#get(long)
    */
   public I_MapEntry get(final long uniqueId) throws XmlBlasterException {
      final String key = ""+uniqueId;
      if (log.CALL) log.call(ME, "get(" + key + ")");
      synchronized (this.storage) {
         return (I_MapEntry)this.storage.get(key);
      }
   }

   /**
    * @see I_Map#getAll()
    */
   public I_MapEntry[] getAll() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "getAll()");
      synchronized (this.storage) {
         return (I_MapEntry[])this.storage.values().toArray(new I_MapEntry[this.storage.size()]);
      }
   }

   /**
    * @see I_Map#put(I_MapEntry)
    */
   public int put(I_MapEntry entry) throws XmlBlasterException {
      if (entry == null) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "put(I_MapEntry="+entry+")");
      }
      if (log.CALL) log.call(ME, "put(" + entry.getLogId() + ")");

      if (getNumOfEntries() > property.getMaxMsg()) { // Allow superload one time only
         String reason = "Message store overflow, number of entries=" + property.getMaxMsg() +
                         ", try increasing '" + this.property.getPropName("maxMsg") + "' on client login.";
         if (log.TRACE) log.trace(ME, reason);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
      }
      if (this.getNumOfBytes() > property.getMaxBytes()) { // Allow superload one time only
         String reason = "Message store overflow with " + this.getNumOfBytes() +
                         " bytes, try increasing '" + this.property.getPropName("maxBytes") + "' on client login.";
         if (log.TRACE) log.trace(ME, reason);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
      }

      String key = entry.getUniqueIdStr();
      synchronized (this.storage) {
         Object old = this.storage.put(key, entry);

         if (old != null) { // I_Map#put(I_MapEntry) spec says that the old entry is not updated!
            this.storage.put(key, old);
            return 0;
            /*
            this.sizeInBytes -= old.getSizeInBytes();
            if (old.isPersistent()) {
               this.numOfPersistentEntries--;
               this.persistentSizeInBytes -= old.getSizeInBytes();
            }
            */
         }
         
         this.sizeInBytes += entry.getSizeInBytes();
         if (entry.isPersistent()) {
            this.numOfPersistentEntries++;
            this.persistentSizeInBytes += entry.getSizeInBytes();
         }
         return (old != null) ? 0 : 1;
      }
   }

   public int remove(final I_MapEntry mapEntry) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "remove(" + mapEntry.getLogId() + ")");
      synchronized (this.storage) {
         I_MapEntry entry = (I_MapEntry)this.storage.remove(mapEntry.getUniqueIdStr());
         if (entry == null)
            return 0;

         if (entry.isPersistent()) {
            this.numOfPersistentEntries--;
            this.persistentSizeInBytes -= entry.getSizeInBytes();
         }
         this.sizeInBytes -= entry.getSizeInBytes();
         return 1;
      }
   }

   /**
    * @see I_Map#removeTransient()
    */
   public int removeTransient() throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "removeTransient() is not implemented");
   }

   public long clear() {
      if (log.CALL) log.call(ME, "clear()");
      synchronized(this.storage) {
         long ret = (long)this.storage.size();
         this.storage.clear();
         this.sizeInBytes = 0L;
         this.persistentSizeInBytes = 0L;
         this.numOfPersistentEntries = 0L;
         return ret;
      }
   }

   /**
    * @see I_Map#getNumOfEntries()
    */
   public long getNumOfEntries() {
      synchronized(this.storage) {
         return this.storage.size();
      }
   }

   public long getMaxNumOfEntries() {
      return property.getMaxMsg();
   }

   /**i
    * @see I_Map#getNumOfPersistentEntries()
    */
   public long getNumOfPersistentEntries() {
      return this.numOfPersistentEntries;
   }

   /**
    * @see I_Map#getNumOfBytes()
    */
   public long getNumOfBytes() {
      return this.sizeInBytes;
   }

   /**
    * @see I_Map#getNumOfPersistentBytes()
    */
   public long getNumOfPersistentBytes() {
      return this.persistentSizeInBytes;
   }

   /**
    * @see I_Map#getMaxNumOfBytes()
    */
   public final long getMaxNumOfBytes() {
      return this.property.getMaxBytes();
   }

   public final void shutdown() {
      if (log.CALL) log.call(ME, "Entering shutdown(" + this.storage.size() + ")");
      //Thread.currentThread().dumpStack();
      synchronized (this.storage) {
         if (this.storage.size() > 0) {
            String reason = "Shutting down topic cache which contains " + this.storage.size() + " messages";
            log.warn(ME, reason);
            //throw new XmlBlasterException(ME, reason);
            //handleFailure !!!
         }
         isShutdown = true;
      }
      if (log.CALL) log.call(ME, "shutdown() of topic cache " + this.getStorageId());

   }

   public final boolean isShutdown() {
      return this.isShutdown;
   }

   public void destroy() throws XmlBlasterException {
      this.property = null;
   }

   public String usage() {
      return "no usage";
   }

   public final String toXml() {
      return toXml((String)null);
   }

   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<MapPlugin id='").append(getStorageId().getId());
      sb.append("' type='").append(getType());
      sb.append("' version='").append(getVersion());
      sb.append("' numOfEntries='").append(getNumOfEntries());
      sb.append("' numOfBytes='").append(getNumOfBytes());
      sb.append("'>");
      sb.append(property.toXml(extraOffset+Constants.INDENT));
      sb.append(offset).append("</MapPlugin>");

      return sb.toString();
   }

   /**
    * Enforced by I_Plugin
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) {
//      java.util.Properties props = pluginInfo.getParameters();
      this.pluginInfo = pluginInfo;
   }

   /**
    * Enforced by I_Plugin
    * @return "RAM"
    */
   public String getType() { return "RAM"; }

   /**
    * Enforced by I_Plugin
    * @return "1.0"
    */
   public String getVersion() { return "1.0"; }

   /**
    * Enforced by I_StoragePlugin
    * @return the pluginInfo object.
    */
   public PluginInfo getInfo() { return this.pluginInfo; }

   /**
    * @see org.xmlBlaster.util.queue.I_StorageProblemNotifier#registerStorageProblemListener(I_StorageProblemListener)
    */
   public boolean registerStorageProblemListener(I_StorageProblemListener listener) {
      return false;
   }

   /**
    * @see org.xmlBlaster.util.queue.I_StorageProblemNotifier#unRegisterStorageProblemListener(I_StorageProblemListener)
    */
   public boolean unRegisterStorageProblemListener(I_StorageProblemListener listener) {
      return false;
   }

   /**
    * java org.xmlBlaster.engine.msgstore.ram.MapPlugin
    */
   public static void main(String[] args) {
      try {
         Global glob = new Global(args);
         MapPlugin pl = new MapPlugin();
         StorageId mapId = new StorageId("msgUnitStore", "/node/unknown");
         pl.initialize(mapId, new org.xmlBlaster.util.qos.storage.MsgUnitStoreProperty(glob, glob.getId()));
      }
      catch (XmlBlasterException e) {
         System.out.println("ERROR: " + e.getMessage());
      }
   }
}
