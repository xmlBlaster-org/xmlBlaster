/*------------------------------------------------------------------------------
Name:      PersistentMap.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.engine.msgstore.StoragePluginManager;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.queue.StorageId;

/**
 * PersistentMap. You can use this as a common java.util.Map. It persists over different JVM. Two methods of the interface are currently not implemented:
 * <ul>
 *   <li>Collection values()</li>
 *   <li>Set entrySet()</li>
 * <ul>
 * When invoked, these will throw the runtime exception IllegalStateException.
 * 
 * All keys of the map are cached on memory and normally not reloaded. This means that when an other process or an other instance of this class (having the same
 * Id) is inserting entries on the map, then the additions are not detected instantly (you can invoke reload() for that).
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class PersistentMap implements Map {

   class PersistentProperty extends QueuePropertyBase {
      
      PersistentProperty(Global global, String nodeId) {
         super(global, nodeId);
      }
      
   }
   
   private I_Map cache;
   /** Stores the key as the key and the long as the value. The value is then used to retrieve the persistent value */
   private Map keyMap;
   
   public PersistentMap(String Id) throws XmlBlasterException {
      this(null, Id, 0L, 0L);
   }
   
   /**
    * All entries are kept in cache.
    * @param global can be null. If null one is created. If it is an engine global it is used directly, otherwise a new
    * engine global is done out of the util global (i.e. by using its properties).
    * @param nodeId The nodeId identifying this persistent properties. This way it can be reused over different JVM.
    * @param maxEntries the maximum number of entries If less than one, then no maximum is specified.
    * @param maxBytes the maximum number of bytes If less than one, then no maximum is specified.
    */
   public PersistentMap(org.xmlBlaster.util.Global global, String nodeId, long maxEntries, long maxBytes) throws XmlBlasterException {
      org.xmlBlaster.engine.Global engineGlobal = null;
      if (global == null)
         global = new org.xmlBlaster.engine.Global();
      if (global instanceof org.xmlBlaster.engine.Global) {
         engineGlobal = (org.xmlBlaster.engine.Global)global;
      }
      else {
         boolean loadPropFile = true;
         engineGlobal = new org.xmlBlaster.engine.Global(global.getProperty().getProperties(), loadPropFile);
      }
      
      StoragePluginManager pluginManager = engineGlobal.getStoragePluginManager();
      PluginInfo pluginInfo = new PluginInfo(engineGlobal, pluginManager, "CACHE", "1.0");

      QueuePropertyBase props = new PersistentProperty(engineGlobal, nodeId);
      if (maxBytes > 0) {
         props.setMaxBytes(maxBytes);
         props.setMaxBytesCache(maxBytes);
      }
      if (maxEntries > 0) {
         props.setMaxEntries(maxEntries);
         props.setMaxEntriesCache(maxEntries);
      }
      StorageId storageId = new StorageId("properties", nodeId);
      this.cache = pluginManager.getPlugin(pluginInfo, storageId, props);
      this.keyMap = new HashMap();
      reload();
   }
   
   public final synchronized void reload() throws XmlBlasterException {
      I_MapEntry[] entries = this.cache.getAll(null);
      for (int i=0; i < entries.length; i++) {
         // key is the they of the entry and value is the Long of the timestamp
         PersistentEntry entry = (PersistentEntry)entries[i];
         long id = entry.getUniqueId();
         this.keyMap.put(entry.getKey(), new Long(id));
      }
   }
   
   
   public synchronized boolean containsValue(Object value) {
      try {
         I_MapEntry[] entries = this.cache.getAll(null);
         for (int i=0; i < entries.length; i++) {
            PersistentEntry entry = (PersistentEntry)entries[i];
            entry.getValue().equals(value);
            return true;
         }
         return false;
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         return false;
      }
   }


   public int size() {
      return (int)this.cache.getNumOfEntries();
   }

   /**
    * @see java.util.Map#isEmpty()
    */
   public boolean isEmpty() {
      return size() == 0;
   }

   /**
    * @see java.util.Map#containsKey(java.lang.Object)
    */
   public boolean containsKey(Object key) {
      return get(key) != null;
   }

   /**
    * @see java.util.Map#get(java.lang.Object)
    */
   public synchronized Object get(Object key) {
      try {
         Long id = (Long)this.keyMap.get(key);
         if (id == null)
            return null;
         PersistentEntry entry = (PersistentEntry)this.cache.get(id.longValue());
         if (entry == null) { // could have been deleted by somebody else on another instance.
            this.keyMap.remove(key);
            return null; 
         }
         return entry.getValue();
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         return null;
      }
   }

   /**
    * @see java.util.Map#put(java.lang.Object, java.lang.Object)
    */
   public synchronized Object put(Object key, Object value) {
      try {
         Long id = (Long)this.keyMap.get(key);
         PersistentEntry entry = null;
         if (id != null) {
            entry = (PersistentEntry)this.cache.get(id.longValue());
            if (entry == null) {
               entry = new PersistentEntry(id.longValue(), key, value);
            }
            else {
               entry.assign(key, value);
               this.cache.remove(id.longValue()); // must first be removed
            }
         }
         else {
            entry = new PersistentEntry(key, value);
            id = new Long(entry.getUniqueId());
            this.keyMap.put(key, id);
         }
         this.cache.put(entry);
         return value;
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         return null;
      }
   }

   /**
    * @see java.util.Map#remove(java.lang.Object)
    */
   public synchronized Object remove(Object key) {
      try {
         Long id = (Long)this.keyMap.get(key);
         if (id == null)
            return null;
         PersistentEntry entry = (PersistentEntry)this.cache.get(id.longValue());
         this.cache.remove(id.longValue());
         return entry.getValue();
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         return null;
      }
   }

   /**
    * @see java.util.Map#putAll(java.util.Map)
    */
   public synchronized void putAll(Map map) {
      if (map == null)
         return;
      synchronized (map) {
         Iterator iter = map.keySet().iterator();
         while (iter.hasNext()) {
            Object key = iter.next();
            Object val = map.get(key);
            put(key, val);
         }
      }
   }

   /**
    * @see java.util.Map#clear()
    */
   public synchronized void clear() {
      try {
         this.keyMap.clear();
         this.cache.clear();
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
      }
   }

   /**
    * @see java.util.Map#keySet()
    */
   public Set keySet() {
      return this.keyMap.entrySet();
   }

   /**
    * @see java.util.Map#values()
    */
   public Collection values() {
      throw new IllegalStateException("The method values() is not implemented in PersistenMap");
   }

   /**
    * @see java.util.Map#entrySet()
    */
   public Set entrySet() {
      throw new IllegalStateException("The method values() is not implemented in PersistenMap");
   }

}
