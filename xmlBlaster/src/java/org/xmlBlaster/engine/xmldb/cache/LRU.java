
package org.xmlBlaster.engine.xmldb.cache;

import java.lang.ref.*;
import java.util.*;
import org.jutils.log.*;
import org.jutils.runtime.Memory;
import org.xmlBlaster.engine.PMessageUnit;


public class LRU
{
   private static final String ME = "LRU";
   private SortedMap _lruTable;
   private Cache _cache;

   /**
   * Organize timestamp (sorted) to OID in a table
   * <p />
   * This cache manager implements a least recently used cache replacement strategy.
   * @param Cache-reference<br />
   */
   public LRU(Cache cache)
   {
      _lruTable = Collections.synchronizedSortedMap(new TreeMap());
      _cache = cache;
   }

   /** When a write-access to cache comes, must be set timestanp to OID */
   public void setTimestampToOid(PMessageUnit pmu)
   {
      SoftReference sr = new SoftReference(pmu.oid);
      _lruTable.put(String.valueOf(System.currentTimeMillis()), sr);
   }

   /**
    * <p />
    * Determines which entry in the cache should be removed,
    * It finds the least recently used cache entry.
    * @param <br />
    * @return the index of the cache entry to remove, or -1 if none should be removed
   */
   public void removeCacheEntryAndReplace(PMessageUnit newPMU)
   {
      long size = _cache.getCacheSize();
      while(size > _cache.getMaxCacheSize())
      {
         /** Get the first oid, its the oldest one .*/
         String timeStamp = (String)_lruTable.lastKey();
         SoftReference sr = null;
         sr = (SoftReference)_lruTable.get(timeStamp);
         String oid = "";
         if(sr != null)
         {
            oid = (String)sr.get();
         }
         Log.trace(ME,"Cache to big with OID : "+oid +" TIMESTAMP :"+timeStamp+ " Size : "+Memory.byteString(size));

         /** Get PMU from Cache */
         PMessageUnit pmu = _cache.getPmuByOid(oid);

         if(pmu == null){
            /** PMU was not present in Cache, PMU is mapped in filedb. */
            Object o = _lruTable.remove(timeStamp);
            o = null;
         }else{

            // WriteBack to filedb
            _cache.writeBack(pmu);
            _cache.msgInDb++;

            /** Delete references from Cache and LRU-Table */
            Log.trace(ME,"Remove OID: "+pmu.oid+" with size : "+ Memory.byteString(pmu.size)+" from Cache and LRU.");
            _cache.removeKeyObject(oid);
            Object o = _lruTable.remove(timeStamp);
            o = null;

            /** Calculate new Cachesize */
            size = size - pmu.size;
            _cache.setCacheSize(size);
         }

      }

      /** write new PMU to Cache and note it in the LRU-Table */
      setTimestampToOid(newPMU);
      _cache.copyBack(newPMU);
      if(newPMU.isDurable){
         _cache.writeThroughNoWA(newPMU);
         _cache.msgDurable++;
      }

      /** Calculate new Cachesize */
      size = size + newPMU.size;
      _cache.setCacheSize(size);
   }

   public void setNewLRU(PMessageUnit pmu)
   {
      /** optimize please */
      Set keys = _lruTable.keySet();
      Iterator it = keys.iterator();

      String key = "";
      while (it.hasNext())
      {

          key = (it.next()).toString();
          if(_lruTable.get(key).equals(pmu.oid)){
             break;
          }
      }

      // Delete key from LRU and set's it new
      Object o = _lruTable.remove(key);
      o = null;
      setTimestampToOid(pmu);
   }

   /**
    * <p />
    * Determines which entry in the cache should be removed to make room for the new
    * entry, if any; It finds the least recently used cache entry.
    * @param <br />
    * @return the index of the cache entry to remove, or -1 if none should be removed
   */
   private void removeKeyObject(String timeStamp)
   {
      Object o = _lruTable.remove(timeStamp);
      o = null;
   }

   public void removeKeyObjectByOid(String oid)
   {
      /** optimize please */
      Set keys = _lruTable.keySet();
      Iterator it = keys.iterator();

      String key = "";
      while (it.hasNext())
      {

          key = (it.next()).toString();
          if(_lruTable.get(key).equals(oid)){
             break;
          }
      }

      // Delete key/object from LRU
      Object o = _lruTable.remove(key);
      o = null;   
   }

}

