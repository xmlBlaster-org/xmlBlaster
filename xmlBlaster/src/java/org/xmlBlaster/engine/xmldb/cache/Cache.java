



package org.xmlBlaster.engine.xmldb.cache;


import java.lang.ref.*;
import org.jutils.JUtilsException;
import org.jutils.log.Log;
import org.jutils.init.Property;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.jutils.runtime.Memory;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.PMessageUnit;
import org.xmlBlaster.engine.xmldb.file.*;

import java.io.*;
import java.util.*;


public class Cache
{
   private static final String ME = "Cache";
   private long _maxCacheSize = 0L;
   private long _cacheSize = 0L;
   private long _maxMsgSize = 0L;
   private String _dbPath = "";

   private static RecordsFile _fileDb;
   private SortedMap _cacheMap;
   private LRU _lru;

   private static int _overflowCount = 0;
   private static int _msgToBig      = 0;
   public  static int  msgInDb       = 0;
   public static int  msgDurable    = 0;

   public Cache()
   {
      _maxCacheSize = XmlBlasterProperty.get("PDOM.Cachesize", 20000000L);
      _maxMsgSize   = XmlBlasterProperty.get("PDOM.Messagesize",5000L);

      _lru = new LRU(this);

      /** Cache Map */
      _cacheMap = Collections.synchronizedSortedMap(new TreeMap());

      /** FileDb */
      try {
        /** read properties */
        _dbPath = XmlBlasterProperty.get("PDOM.Dbpath",System.getProperty("user.dir"));

        /** Test now if databasefile exists */
        File dbfile = new File(_dbPath+"/xmlBlaster.msg");
        if(!dbfile.exists())
        {
           /** Create a new dbfile */
           _fileDb    =  new RecordsFile(_dbPath+"/xmlBlaster.msg", 64);
        }else
        {
           /** Dbfile exists and open it with "RW" access. */
           _fileDb    =  new RecordsFile(_dbPath+"/xmlBlaster.msg","rw");
        }
      }catch(XmlBlasterException e){
      }catch(IOException e){
         Log.error(ME,"Error while opening filedb."+e.toString());
      }
   }


   /**
    * Invoked by the engine (Message-Broker), to insert a MessageUnit to xmldb.
    * <p />
    * A MessageUnit can be written in the Memory-cache or in the filedb. If the
    * MessageUnit is durable or bigger the max. message-size, then we write the
    * MessageUnit to file else to Memory-Cache.
    * <p>
    *
    * @param pmsgUnit An extended MessageUnit for storing the MessageUnit.
    */
   public void write(PMessageUnit pmsgUnit)
   {

      if(pmsgUnit.oid == null){
         Log.error(ME,"Sorry can't write to cache, because no oid is spezified.");
         return;
      }

      /** write-through with no-write-allocation */
      if(pmsgUnit.size > _maxMsgSize)
      {
         _msgToBig++;
         msgInDb++;
         // No Copy-Back
         // Message can be durable or NOT durable
         writeThroughNoWA(pmsgUnit);

         if(pmsgUnit.isDurable){msgDurable++;}

         return;
      }

      // It's only a filedb
      if(_maxCacheSize == 0)
      {
         msgInDb++;
         writeThroughNoWA(pmsgUnit);

         if(pmsgUnit.isDurable){msgDurable++;}

         return;
      }

      /** copy-back  */
      if(_cacheSize < _maxCacheSize)
      {
         // Message is durable and present in filedb and cache
         if(pmsgUnit.isDurable)
         {
            msgInDb++;
            msgDurable++;
            writeThroughNoWA(pmsgUnit);
         }

         // Message is only present in cache
         copyBack(pmsgUnit);

         // Update LRU
         _lru.setTimestampToOid(pmsgUnit);

         // Set new cachesize
         _cacheSize = _cacheSize + pmsgUnit.size;
         return;
      }


      /** Cache is full, make LRU and copy-back  */
      if(_cacheSize > _maxCacheSize)
      {
         _overflowCount++;
         /** Cache is full, make LRU and copy-back  */
         Log.trace(ME,"Cachesize : "+ Memory.byteString(_cacheSize)+" is overflow. Use LRU and write-back for :"+ pmsgUnit.oid);
         _lru.removeCacheEntryAndReplace(pmsgUnit);
         return;
      }


   } //write

   public PMessageUnit getPmuByOid(String oid)
   {
      if(!_cacheMap.containsKey(oid)){
         Log.warning(ME,"Sorry no key : "+oid+" found in Cache.");
         return null;
      }
/*      PMessageUnit pmu = null;
      SoftReference sr = null;
      sr = (SoftReference)_cacheMap.get(oid);

      if(sr != null)
      {
         pmu = (PMessageUnit)sr.get();
      }
      if(pmu == null){
         Log.warning(ME,"PMU was null by oid : "+oid+" PMU :"+pmu);
      }
      return pmu;*/
      return (PMessageUnit)_cacheMap.get(oid);
   }

   private void dumpCache()
   {
      Log.calls(ME,"Enter dumpCache...");
      Set keys = _cacheMap.keySet();
      Iterator it = keys.iterator();

      String key = "";
      SoftReference mysr = null;
      PMessageUnit mypmu = null;
      Log.dump(ME,"-------- DUMP CACHE --------");
      while (it.hasNext())
      {
          Log.calls(ME,"Enter dumpCache...while");
          key = (it.next()).toString();
          mysr = (SoftReference)_cacheMap.get(key);
          mypmu = (PMessageUnit)mysr.get();
          Log.dump(ME,"OID : "+key+" PMU : "+mypmu.oid);
      }
      Log.dump(ME,"----------------------------");
   }

   public boolean keyExists(String oid)
   {
      boolean exists = false;

      //First look up in cache
      PMessageUnit pmu = null;
      pmu = (PMessageUnit)_cacheMap.get(oid);
      if(pmu==null)
      {
         exists = false;
      }else{
         return true;
      }

      //Second look up in filedb
      if(!_fileDb.recordExists(oid)){
         exists = false;
      }else{
         return true;
      }

      return exists;
   }


   public void removeKeyObject(String key)
   {
      Object o = _cacheMap.remove(key);
      o = null;
   }

   public void writeThroughNoWA(PMessageUnit pmu)
   {
      RecordWriter rw = new RecordWriter(pmu.oid);
      try{
         rw.writeObject(pmu);

         if(!_fileDb.recordExists(pmu.oid)){
            _fileDb.insertRecord(rw);
            Log.trace(ME,"write-through with no-write-allocation."+" OID : "+pmu.oid);
         }
      }catch(XmlBlasterException e){
         Log.trace(ME,"Can't insert PMessageUnit to database : "+e.reason);
      }catch(IOException io){
         Log.error(ME,io.toString());
      }
   }


   /**
    * Delete a MessageUnit from the xmldb.
    * <br>
    * @param oid       The Key oid
    */
   public void delete(String oid)
   {
      if(keyExists(oid))
      {
         try{
            PMessageUnit pmu = null;
            pmu = (PMessageUnit)_cacheMap.get(oid);
            if(pmu==null)
            {
               //Msg is durable
               msgDurable--;

               //Msg was maybe to big
               try{
                  RecordReader rr = _fileDb.readRecord(oid);
                  PMessageUnit p = (PMessageUnit)rr.readObject();
                  if(p!=null){
                     if(p.size > _maxMsgSize){
                        _msgToBig--;
                     } 
                  }
              }catch(XmlBlasterException e){
                 Log.error(ME,"Can't read PMessageUnit from database : "+e.reason);
              }catch(IOException io){
                 Log.error(ME,io.toString());
              }catch(ClassNotFoundException cnfe){
                 Log.error(ME,"Can't find PMessageUnit for TypeCast."+cnfe.toString());
              }
            }

            _fileDb.deleteRecord(oid);
            removeKeyObject(oid);
            msgInDb--;
            _lru.removeKeyObjectByOid(oid);
       
            Log.trace(ME,"Delete PMU with oid : "+oid+" from cache and filedb.");
         }catch(XmlBlasterException e){
            Log.warning(ME,"Can't delete PMessageUnit from database : "+e.reason);
         }catch(IOException io){
            Log.warning(ME,io.toString());
         }
      }else{
         Log.warning(ME,"Can't delete, because key doesn't exists.");
      }

   }


   public void copyBack(PMessageUnit pmu)
   {
//      SoftReference sr = new SoftReference(pmu);
//      _cacheMap.put(pmu.oid,sr);
      _cacheMap.put(pmu.oid,pmu);
      Log.trace(ME,"COPY-BACK "+" OID : "+pmu.oid);
   }


   public void writeBack(PMessageUnit pmu)
   {
      // Write back to fileDb
      writeThroughNoWA(pmu);
      Log.trace(ME,"WRITE-BACK "+" OID : "+pmu.oid);
   }

   public PMessageUnit read(String oid)
   {
      PMessageUnit pmu = null;
/*      SoftReference sr = null;
      sr = (SoftReference)_cacheMap.get(oid);
      if(sr != null)
      {
         pmu = (PMessageUnit)sr.get();
      }*/
      pmu = (PMessageUnit)_cacheMap.get(oid);
      if(pmu==null)
      {
         // Read miss
         pmu = readMiss(oid);

         if(pmu==null){
            return pmu;
         }

         /** Write-Back to Cache and set LRU */
         write(pmu);

         Log.trace(ME,"READ OID from FILEDB: "+pmu.oid);
      }else{
        /** PMU was in Cache present and set new LRU */
        _lru.setNewLRU(pmu);
      }

      return pmu;
   }


   private PMessageUnit readMiss(String oid)
   {
      PMessageUnit pmu = null;

      if(!_fileDb.recordExists(oid)){
         Log.warning(ME,"Can't read pmu with oid : "+oid+" from filedb by read-miss.");
         return null;
      }

      try{
         RecordReader rr = _fileDb.readRecord(oid);
         pmu = (PMessageUnit)rr.readObject();
         /** delete PMU from file and and write-back */
         if(!pmu.isDurable){
            _fileDb.deleteRecord(oid);
            Log.trace(ME,"Delete OID : "+oid+ " from filedb.");
         }
      }catch(XmlBlasterException e){
         Log.error(ME,"Can't read PMessageUnit from database : "+e.reason);
      }catch(IOException io){
         Log.error(ME,io.toString());
      }catch(ClassNotFoundException cnfe){
         Log.error(ME,"Can't find PMessageUnit for TypeCast."+cnfe.toString());
      }
      return pmu;
   }

   public void setCacheSize(long newSize){
      _cacheSize = newSize;
   }

   public long getCacheSize(){
      return _cacheSize;
   }

   public void clearCache(){
   }

   public long getMaxCacheSize(){
      return _maxCacheSize;
   }
   public void setMaxCacheSize(long sizeInByte){
      _maxCacheSize = sizeInByte;
   }

   private int getPMUCount()
   {
      return _cacheMap.size();
   }


   // Gets a vector of the current cachestate.
   public Vector getCacheState()
   {
      /* Format : Current CacheSize
                  Max-CacheSize
                  Max-MsgSize
                  PMU's in Cache
                  Cacheoverflow
                  PMU's in db
                  Durable PMU's
                  Big PMU's
      */
      Vector state = new Vector();
      state.addElement(Memory.byteString(getCacheSize()));
      state.addElement(Memory.byteString(_maxCacheSize));
      state.addElement(Memory.byteString(_maxMsgSize));
      state.addElement(String.valueOf(getPMUCount()));
      state.addElement(String.valueOf(_overflowCount));
      state.addElement(String.valueOf(msgInDb));
      state.addElement(String.valueOf(msgDurable));
      state.addElement(String.valueOf(_msgToBig));
      return state;
   }

}
