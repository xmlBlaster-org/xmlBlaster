/*------------------------------------------------------------------------------
Name:      MapPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.msgstore.ram;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.msgstore.I_ChangeCallback;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.queue.I_EntryFilter;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_Storage;
import org.xmlBlaster.util.queue.I_StoragePlugin;
import org.xmlBlaster.util.queue.I_StorageProblemListener;
import org.xmlBlaster.util.queue.I_StorageSizeListener;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.StorageSizeListenerHelper;

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
   private Set lruSet;                 // We could use a LinkedList for LRU but random access is slow
   private QueuePropertyBase property; // org.xmlBlaster.util.qos.storage.MsgUnitStoreProperty;
   private Global glob;
   private static Logger log = Logger.getLogger(MapPlugin.class.getName());
   private boolean isShutdown = false;
   private long sizeInBytes;
   private long persistentSizeInBytes;
   private long numOfPersistentEntries;
   private PluginInfo pluginInfo;

   private StorageSizeListenerHelper storageSizeListenerHelper;
   
   public MapPlugin() {
      this.storageSizeListenerHelper = new StorageSizeListenerHelper(this);
   }
   
   /**
    * Is called after the instance is created.
    * @see org.xmlBlaster.engine.msgstore.I_Map#initialize(StorageId, Object)
    */
   public void initialize(StorageId uniqueMapId, Object userData) throws XmlBlasterException {
      setProperties(userData); // sets this.property

      this.mapId = uniqueMapId;
      if (mapId == null || glob == null) {
         Thread.dumpStack();
         throw new IllegalArgumentException("Illegal arguments in MapPlugin constructor: mapId=" + mapId);
      }

      this.ME = "MapPlugin-" + mapId;

      if (this.property != null && this.glob.isServerSide() != this.property.getGlobal().isServerSide()) {
         log.severe("Incompatible globals this.property.getGlobal().isServerSide()=" + this.property.getGlobal().isServerSide() + ": " + Global.getStackTraceAsString(null));
      }
      this.glob = this.property.getGlobal();
      
      if (property.getMaxEntries() > Integer.MAX_VALUE) throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME + ".initialize: The maximum number of messages is too big");
      
      this.storage = new TreeMap();
      this.lruSet = new TreeSet(new LruComparator());

      this.isShutdown = false;
   }

   public void setProperties(Object userData) throws XmlBlasterException {
      if (userData == null) return;
      QueuePropertyBase newProp;
      try {
         newProp = (QueuePropertyBase)userData;
      }
      catch(Throwable e) { // this.log is still null
         throw XmlBlasterException.convert(this.glob, ME, "Can't configure RAM map, your properties are invalid", e); // glob is allowed to be null
      }

      this.property = newProp;
   }

   public Object getProperties() {
      return this.property;
   }

   public final StorageId getStorageId() {
      return mapId;
   }

   public boolean isTransient() {
      return true;
   }

   /**
    * @see I_Map#get(long)
    */
   public I_MapEntry get(final long uniqueId) throws XmlBlasterException {
      final String key = ""+uniqueId;
      if (log.isLoggable(Level.FINER)) log.finer("get(" + key + ")");
      synchronized (this.storage) {
         I_MapEntry entry = (I_MapEntry)this.storage.get(key);
         touch(entry);
         return entry;
      }
   }

   private void touch(I_MapEntry entry) {
      if (entry == null) return;
      if (entry.getSortTimestamp() != null) // assert: All entries in the set must have a sortTimestamp else the Comparator fails
         this.lruSet.remove(entry);
      entry.setSortTimestamp(new Timestamp());
      this.lruSet.add(entry);
   }

   /**
    * @see I_Map#getAll()
    */
   public I_MapEntry[] getAll(I_EntryFilter entryFilter) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("getAll()");
      I_MapEntry[] entries = null;
      synchronized (this.storage) {
         // sortTimestamp remains as all entries are touched
                 entries = (I_MapEntry[])this.storage.values().toArray(new I_MapEntry[this.storage.size()]);
      }
      if (entryFilter == null)
         return entries;
          
      ArrayList list = new ArrayList();
      for (int i=0; i<entries.length; i++) {
         I_MapEntry entry = (I_MapEntry)entryFilter.intercept(entries[i], this);
         if (entry != null)
                list.add(entry);
      }
      return (I_MapEntry[])list.toArray(new I_MapEntry[list.size()]);
   }

   /**
    * @see I_Map#put(I_MapEntry)
    */
   public int put(I_MapEntry entry) throws XmlBlasterException {
      if (entry == null) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "put(I_MapEntry="+entry+")");
      }
      if (log.isLoggable(Level.FINER)) log.finer("put(" + entry.getLogId() + ")");

      if (getNumOfEntries() > property.getMaxEntries()) { // Allow superload one time only
         String reason = "Message store overflow, number of entries=" + property.getMaxEntries() +
                         ", try increasing '" + this.property.getPropName("maxEntries") + "' on client login.";
         if (log.isLoggable(Level.FINE)) log.fine(reason);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
      }
      if (this.getNumOfBytes() > property.getMaxBytes()) { // Allow superload one time only
         String reason = "Message store overflow with " + this.getNumOfBytes() +
                         " bytes, try increasing '" + this.property.getPropName("maxBytes") + "' on client login.";
         if (log.isLoggable(Level.FINE)) log.fine(reason);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, reason);
      }

      String key = entry.getUniqueIdStr();
      int ret = 0;
      synchronized (this.storage) {
         Object old = this.storage.put(key, entry);

         if (old != null) { // I_Map#put(I_MapEntry) spec says that the old entry is not updated!
            this.storage.put(key, old);
            touch((I_MapEntry)old);
            ret = 0;
            /*
            this.sizeInBytes -= old.getSizeInBytes();
            if (old.isPersistent()) {
               this.numOfPersistentEntries--;
               this.persistentSizeInBytes -= old.getSizeInBytes();
            }
            */
         }
         else {
            entry.setSortTimestamp(new Timestamp());
            this.lruSet.add(entry);
            
            entry.setStored(true);
            this.sizeInBytes += entry.getSizeInBytes();
            if (entry.isPersistent()) {
               this.numOfPersistentEntries++;
               this.persistentSizeInBytes += entry.getSizeInBytes();
            }
            ret = (old != null) ? 0 : 1;
         }
      }
      this.storageSizeListenerHelper.invokeStorageSizeListener();
      return ret;
   }

   /**
    * @see I_Map#remove(I_MapEntry)
    */
   public int remove(final I_MapEntry mapEntry) throws XmlBlasterException {
      if (mapEntry == null) return 0;
      if (log.isLoggable(Level.FINER)) log.finer("remove(" + mapEntry.getLogId() + ")");
      try {
         synchronized (this.storage) {
            if (mapEntry.getSortTimestamp() != null)
               this.lruSet.remove(mapEntry);
            I_MapEntry entry = (I_MapEntry)this.storage.remove(mapEntry.getUniqueIdStr());
            if (entry == null)
               return 0;

            if (entry.isPersistent()) {
               this.numOfPersistentEntries--;
               this.persistentSizeInBytes -= entry.getSizeInBytes();
            }
            entry.setStored(false);
            this.sizeInBytes -= entry.getSizeInBytes();
            return 1;
         }
      }
      finally { // since it should be outside the sync
         this.storageSizeListenerHelper.invokeStorageSizeListener();
      }
   }

   /**
    * @see I_Map#remove(long)
    */
   public int remove(final long uniqueId) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("remove(" + uniqueId + ")");
      int ret = 0;
      synchronized (this.storage) {
         I_MapEntry mapEntry = get(uniqueId);
         if (mapEntry != null)
            ret = remove(mapEntry);
      }
      this.storageSizeListenerHelper.invokeStorageSizeListener();
      return ret;
   }

   /**
    * @see I_Map#removeTransient()
    */
   public int removeTransient() throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "removeTransient() is not implemented");
   }

   /**
    * @see I_Map#removeOldest()
    */
   public I_MapEntry removeOldest() throws XmlBlasterException {
      try {
         synchronized (this.storage) {
            I_MapEntry oldest = null;
            Iterator it = this.lruSet.iterator();
            if (it.hasNext()) {
               oldest = (I_MapEntry)it.next();
            }
            if (oldest != null) {
               remove(oldest);
               return oldest;
            }

            if (this.storage.size() > 0) {
               log.severe("LRU set has no entries, we remove an arbitrary entry from RAM map");
               it = this.storage.values().iterator();
               if (it.hasNext()) {
                  I_MapEntry entry = (I_MapEntry)it.next();
                  remove(entry);
                  return entry;
               }
            }

            return null;
         }
      }
      finally {
         this.storageSizeListenerHelper.invokeStorageSizeListener();
      }
   }

   public long clear() {
      if (log.isLoggable(Level.FINER)) log.finer("clear()");
      synchronized(this.storage) {
         long ret = this.storage.size();

         Iterator iter = this.storage.values().iterator();
         while (iter.hasNext()) {
            ((I_MapEntry)iter.next()).setStored(false);
         }

         this.lruSet.clear();
         this.storage.clear();
         this.sizeInBytes = 0L;
         this.persistentSizeInBytes = 0L;
         this.numOfPersistentEntries = 0L;
         this.storageSizeListenerHelper.invokeStorageSizeListener();
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
      return property.getMaxEntries();
   }

   /**i
    * @see I_Map#getNumOfPersistentEntries()
    */
   public long getNumOfPersistentEntries() {
      synchronized(this.storage) {
         return this.numOfPersistentEntries;
      }
   }

   /**
    * @see I_Map#getNumOfBytes()
    */
   public long getNumOfBytes() {
      synchronized(this.storage) {
         return this.sizeInBytes;
      }
   }

   /**
    * @see I_Map#getNumOfPersistentBytes()
    */
   public long getNumOfPersistentBytes() {
      synchronized(this.storage) {
         return this.persistentSizeInBytes;
      }
   }

   /**
    * @see I_Map#getMaxNumOfBytes()
    */
   public final long getMaxNumOfBytes() {
      return this.property.getMaxBytes();
   }

   public final void shutdown() {
      if (log.isLoggable(Level.FINER)) log.finer("Entering shutdown(" + this.storage.size() + ")");
      //Thread.currentThread().dumpStack();
      synchronized (this.storage) {
         if (this.storage.size() > 0) {
            if (log.isLoggable(Level.FINE)) log.fine("Shutting down RAM map which contains " + this.storage.size() + " messages");
         }
         this.lruSet.clear();
         isShutdown = true;
      }
      if (log.isLoggable(Level.FINER)) log.finer("shutdown() of RAM map " + this.getStorageId());
      this.storageSizeListenerHelper.invokeStorageSizeListener();
      removeStorageSizeListener(null);
      // glob.getQueuePluginManager().cleanup(this);
      if (glob instanceof ServerScope) {
         ((ServerScope)glob).getStoragePluginManager().cleanup(this);
      }
      else
         log.warning("The global is not a ServerScope: we can not clean up this entry from the storage plugin manager");
      
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
      this.glob = glob;
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
    * @see I_Map#change(I_MapEntry, I_ChangeCallback)
    */
   public I_MapEntry change(I_MapEntry entry, I_ChangeCallback callback) throws XmlBlasterException {
      synchronized(this.storage) {
         entry.getSizeInBytes(); // must be here since newEntry could reference same obj.
         I_MapEntry newEntry = entry; 
         if (callback != null) newEntry = callback.changeEntry(entry);
         if (newEntry == null) return entry;
         if (newEntry.isPersistent() != entry.isPersistent()) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".change",
                  "changing  oldEntry.isPersistent=" + entry.isPersistent() + " to newEntry.isPersistent=" + newEntry.isPersistent() + "differs. This is not allowed");
         } 
         if (entry != newEntry) { // then they are not the same reference ...
            int tmp = remove(entry);
            if (tmp < 1) throw new XmlBlasterException(this.glob,  ErrorCode.INTERNAL_UNKNOWN, ME + ".change", "the size of the entry '" + entry.getUniqueId() + "' has not been found on this map");
            put(newEntry);
         }
         return newEntry;
      }
   }


   /**
    * @see I_Map#change(I_MapEntry, I_ChangeCallback)
    */
   public void updateCounters(I_MapEntry entry) throws XmlBlasterException {
      change(entry, null);
   }


   /**
    * @see I_Map#change(long, I_ChangeCallback)
    */
   public I_MapEntry change(long uniqueId, I_ChangeCallback callback) throws XmlBlasterException {
      synchronized(this.storage) {
         I_MapEntry oldEntry = get(uniqueId);
         return change(oldEntry, callback);
      }
   }

   /**
    * @see I_Map#embeddedObjectsToXml(OutputStream, Properties)
    */
   public long embeddedObjectsToXml(OutputStream out, Properties props) {
      log.warning("Sorry, dumping transient entries is not implemented");
      return 0;
   }

   /**
    * @see I_Queue#addStorageSizeListener(I_StorageSizeListener)
    */
   public void addStorageSizeListener(I_StorageSizeListener listener) {
      this.storageSizeListenerHelper.addStorageSizeListener(listener);
   }
   
   /**
    * @see I_Queue#removeStorageSizeListener(I_StorageSizeListener)
    */
   public void removeStorageSizeListener(I_StorageSizeListener listener) {
      this.storageSizeListenerHelper.removeStorageSizeListener(listener);
   }
   
   /**
    * @see I_Queue#hasStorageSizeListener(I_StorageSizeListener)
    */
   public boolean hasStorageSizeListener(I_StorageSizeListener listener) {
      return this.storageSizeListenerHelper.hasStorageSizeListener(listener);
   }

   /**
    * @see I_Storage#getStorageSizeListeners()
    */
   public I_StorageSizeListener[] getStorageSizeListeners() {
      return storageSizeListenerHelper.getStorageSizeListeners();
   }

   /**
    * Sorts the entries in the the last recent added order (no real LRU). 
    */
   class LruComparator implements Comparator, java.io.Serializable
   {
      private static final long serialVersionUID = -8286998211709086682L;

      // We compare the MsgUnitWrapper by its cache entry timestamp
      public final int compare(Object o1, Object o2) {
         I_MapEntry id1 = (I_MapEntry)o1;
         I_MapEntry id2 = (I_MapEntry)o2;

         if (id1.getSortTimestamp() == null) id1.setSortTimestamp(new Timestamp()); // assert != null
         if (id2.getSortTimestamp() == null) id2.setSortTimestamp(new Timestamp()); // assert != null
         
         if (id1.getSortTimestamp().getTimestamp() > id2.getSortTimestamp().getTimestamp()) {
            return 1;
         }
         else if (id1.getSortTimestamp().getTimestamp() < id2.getSortTimestamp().getTimestamp()) {
            return -1;
         }
         return 0;
      }
   }

   /**
    * java org.xmlBlaster.engine.msgstore.ram.MapPlugin
    */
   public static void main(String[] args) {
      try {
         Global glob = new Global(args);
         MapPlugin pl = new MapPlugin();
         StorageId mapId = new StorageId(glob, "/node/unknown", "msgUnitStore", "hello");
         pl.initialize(mapId, new org.xmlBlaster.util.qos.storage.MsgUnitStoreProperty(glob, glob.getId()));
      }
      catch (XmlBlasterException e) {
         System.out.println("ERROR: " + e.getMessage());
      }
   }
}
