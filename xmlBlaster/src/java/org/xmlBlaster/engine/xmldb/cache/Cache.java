/*------------------------------------------------------------------------------
Name:      Cache.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Caches MessageUnits by LRU. 
Version:   $Id: Cache.java,v 1.5 2000/08/26 14:48:49 kron Exp $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xmldb.cache;

import java.io.*;
import java.util.*;
import java.lang.ref.*;

import org.jutils.JUtilsException;
import org.jutils.log.Log;
import org.jutils.init.Property;
import org.jutils.runtime.Memory;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.PMessageUnit;
import org.xmlBlaster.engine.xmldb.file.*;


public class Cache
{
   private static final String ME = "Cache";
   private long     _maxCacheSize = 0L;
   private long        _cacheSize = 0L;
   private long       _maxMsgSize = 0L;
   private String         _dbPath = "";

   private static RecordsFile _fileDb;
   private static RecordsFile _fileSwap;
   private          SortedMap _cacheMap;
   private                Lru _lru;

   private static int _overflowCount = 0;
   private static int      _msgToBig = 0;
   public  static int        msgInDb = 0;
   public static int      msgDurable = 0;

   public Cache()
   {
      _maxCacheSize = XmlBlasterProperty.get("xmldb.Cachesize", 20000000L);
      _maxMsgSize   = XmlBlasterProperty.get("xmldb.Messagesize",5000L);

      _lru = new Lru();

      /** Cache Map */
      _cacheMap = Collections.synchronizedSortedMap(new TreeMap());

      /** FileDb */
      try {
        /** read properties */
        _dbPath = XmlBlasterProperty.get("xmldb.Dbpath",System.getProperty("user.dir"));

        /** Test now if databasefile exists */
        File dbfile = new File(_dbPath+"/xmlBlaster.msg");
        File swapfile = new File(_dbPath+"/swap.msg");

        // Database-File
        if(!dbfile.exists())
        {
           /** Create a new dbfile */
           _fileDb    =  new RecordsFile(_dbPath+"/xmlBlaster.msg", 64);
        }else
        {
           /** Dbfile exists and open it with "RW" access. */
           _fileDb    =  new RecordsFile(_dbPath+"/xmlBlaster.msg","rw");
        }
      
        /** Create a Swap-File */
        _fileSwap    =  new RecordsFile(_dbPath+"/swap.msg", 64);

      }catch(XmlBlasterException e){
      }catch(IOException e){
         Log.error(ME,"Error while opening file from database."+e.toString());
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
         Log.warning(ME,"Sorry can't write to cache, because no oid was given.");
         return;
      }

      if(pmsgUnit.size > _maxMsgSize)
      {
         // No Copy-Back. Message can be durable or NOT durable
         writeThroughNoWA(pmsgUnit);
         if(pmsgUnit.isDurable){msgDurable++;}
         return;
      }

      // Max Cachesize is null. It's a filedatabse with no Caching 
      if(_maxCacheSize == 0)
      {
         writeThroughNoWA(pmsgUnit);
         if(pmsgUnit.isDurable){msgDurable++;}
         return;
      }

      if(_cacheSize < _maxCacheSize)
      {
         // Message is durable and present in filedb and cache
         if(pmsgUnit.isDurable){
            msgDurable++;
            writeThroughNoWA(pmsgUnit);
         }

         // Message is only present in cache
         copyBack(pmsgUnit);

         // Set new cachesize
         _cacheSize = _cacheSize + pmsgUnit.size;
         return;
      }


      /** Cache is full, make LRU and copy-back  */
      if(_cacheSize > _maxCacheSize)
      {
         _overflowCount++;

         // Swap Messages in swap.msg
         while(isFull()){
            // Get the oldest timestamp 
            String oldOid = _lru.removeOldest();
            if(oldOid == null)
               return;

            PMessageUnit oldPmu = getPmuByOid(oldOid);
            if(oldPmu == null)
               return;         
            writeBack(oldPmu);

            Object o = _cacheMap.remove(oldOid);
            o = null;
         } 
         return;
      }
   } //write


   private boolean isFull()
   {
      if(_cacheSize > _maxCacheSize)
         return true;
      else
         return false;
   }


   public PMessageUnit getPmuByOid(String oid)
   {
      if(!_cacheMap.containsKey(oid)){
         Log.warning(ME,"Sorry no key : "+oid+" found in Cache.");
         return null;
      }
      return (PMessageUnit)_cacheMap.get(oid);
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

      PMessageUnit pmu = (PMessageUnit)_cacheMap.get(oid);
  
      // Check Message in Cache and then delete it.
      if(pmu!=null){
         Object o = _cacheMap.remove(oid);
         o = null;
         String deletedOid = _lru.removeEntry(oid);
         if(deletedOid == null)
            Log.warning(ME,"Can't delete oid:"+deletedOid+" from LRU.");
      }

      // Check Message in filedb and then delete it.
      if(_fileDb.recordExists(oid))
      {
         try{
            _fileDb.deleteRecord(oid);
         }catch(XmlBlasterException e){
            Log.warning(ME,"Can't delete PMessageUnit from filedb : "+e.reason);
         }catch(IOException io){
            Log.warning(ME,io.toString());
         }
      }
   }


   public void copyBack(PMessageUnit pmu)
   {
//      SoftReference sr = new SoftReference(pmu);
//      _cacheMap.put(pmu.oid,sr);
      _cacheMap.put(pmu.oid,pmu);
      // Add Message to LRU.
      _lru.addEntry(pmu.oid);
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
         // TODO wenn pmu > maxPMU oder isDurable -> nicht write
         write(pmu);

      }else{
        /** PMU was in Cache present and set new LRU */
        _lru.removeEntry(oid);
        _lru.addEntry(oid);
      }

      return pmu;
   }


   private PMessageUnit readMiss(String oid)
   {
      PMessageUnit pmu = null;

      if(!_fileDb.recordExists(oid)){
         return null;
      }

      try{
         RecordReader rr = _fileDb.readRecord(oid);
         pmu = (PMessageUnit)rr.readObject();

         /** delete PMU from filedb and and write-back */
         // TODO not durable messages will be read from swap.msg in future
         _fileDb.deleteRecord(oid);

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

   public void setMaxMsgSize(long sizeInByte){
      _maxMsgSize = sizeInByte;
   }

   private int getPMUCount(){
      return _cacheMap.size();
   }

}
