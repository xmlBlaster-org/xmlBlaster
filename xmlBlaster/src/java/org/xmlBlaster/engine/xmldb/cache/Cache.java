/*------------------------------------------------------------------------------
Name:      Cache.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Caches MessageUnits by LRU.
Version:   $Id: Cache.java,v 1.6 2000/08/29 11:17:38 kron Exp $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xmldb.cache;

import java.io.*;
import java.util.*;

import org.jutils.JUtilsException;
import org.jutils.log.Log;
import org.jutils.init.Property;

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

   private int _overflowCount = 0;
   private int      _msgToBig = 0;

   public Cache()
   {
      _maxCacheSize = XmlBlasterProperty.get("xmldb.Cachesize", 20000000L);
      _maxMsgSize   = XmlBlasterProperty.get("xmldb.Messagesize",5000L);

      _lru = new Lru();

      /** Cache Map */
      _cacheMap = Collections.synchronizedSortedMap(new TreeMap());

      /** FileDb */
      try 
      {
        /** read properties */
        _dbPath = XmlBlasterProperty.get("xmldb.Dbpath",System.getProperty("user.dir"));

        /** Test if databasefile exists */
        File dbfile = new File(_dbPath+"/xmlBlaster.msg");
        File swapfile = new File(_dbPath+"/swap.msg");

        // Database-File
        if(!dbfile.exists())
        {
           /** Create new dbfile */
           _fileDb    =  new RecordsFile(_dbPath+"/xmlBlaster.msg", 64);
        }else
        {
           /** Dbfile exists and open it with "RW" access. */
           _fileDb    =  new RecordsFile(_dbPath+"/xmlBlaster.msg","rw");
        }

        /** Create Swap-File */
        if(!swapfile.exists())
        {
           /** Create new dbfile */
           _fileSwap    =  new RecordsFile(_dbPath+"/swap.msg", 64);
        }else
        {
           /** Dbfile exists and open it with "RW" access. */
           _fileSwap    =  new RecordsFile(_dbPath+"/swap.msg","rw");
        }

      }catch(XmlBlasterException e){
      }catch(IOException e){
         Log.error(ME,"Error while opening file from database."+e.toString());
      }
   }


   /**
    * Invoked by the engine (Message-Broker), to insert MessageUnits to xmldb.
    * <p />
    * A MessageUnit can be written in Memory-Cache or filedb. If the
    * MessageUnit is durable or bigger the max. messagesize, then we write the
    * MessageUnit to file.
    * <p />
    *
    * @param pmsgUnit An extended MessageUnit for storing the MessageUnit.
    */
   public void write(PMessageUnit pmsgUnit)
   {

      if(pmsgUnit.oid == null){
         Log.warning(ME,"Sorry can't write to cache, because no oid was given.");
         return;
      }

      // No Caching. Only a filedatabse.
      if(_maxCacheSize == 0)
      {
         writePersistent(pmsgUnit);
         return;
      }

      // MessageUnit is durable.
      if(pmsgUnit.isDurable)
      {
         writePersistent(pmsgUnit);
         checkCacheSize();
         copyBack(pmsgUnit);
         return;
      }

      // Big MessageUnits. No CopyBack
      if(pmsgUnit.size > _maxMsgSize)
      {
         writeSwap(pmsgUnit);
         return;
      }

      // Cachesize < MaxCacheSize
      if(!isSwap())
      {
         // Message is only present in cache
         copyBack(pmsgUnit);
         return;

      }else {
         /** ---SWAP--- */
         _overflowCount++;

         checkCacheSize();

         copyBack(pmsgUnit);
      }

   }


   public void checkCacheSize()
   {
      if(isSwap())
      {
         // Swap Messages in swap.msg
         while(isSwap())
         {
            // Get the oldest Message from Lru
            String oldOid = _lru.removeOldest();
            if(oldOid == null){
               return;
            }

            synchronized(_cacheMap)
            {
               // Is MessageUnit in Cache present, then write MessageUnit to filedb.
               if(_cacheMap.containsKey(oldOid))
               {
                  PMessageUnit oldPmu = (PMessageUnit)_cacheMap.get(oldOid);
                  if(!oldPmu.isDurable){
                     writeSwap(oldPmu);
                  }else{
                     // MessageUnit is persistent in filedb.
                  }
                  Object o = _cacheMap.remove(oldOid);
                  _cacheSize = _cacheSize - oldPmu.size;
               }
            }
         }
      }
   }


   private boolean isSwap()
   {
      if(_cacheSize > _maxCacheSize)
         return true;
      else
         return false;
   }


   public void writePersistent(PMessageUnit pmu)
   {
      RecordWriter rw = new RecordWriter(pmu.oid);
      try{
         rw.writeObject(pmu);

         if(!_fileDb.recordExists(pmu.oid)){
            _fileDb.insertRecord(rw);
         }
      }catch(XmlBlasterException e){
         Log.warning(ME,"Can't insert PMessageUnit to persistent-file : "+e.reason);
      }catch(IOException io){
         Log.error(ME,io.toString());
      }

   }

   public void writeSwap(PMessageUnit pmu)
   {
      RecordWriter rw = new RecordWriter(pmu.oid);
      try{
         rw.writeObject(pmu);

         if(!_fileSwap.recordExists(pmu.oid))
            _fileSwap.insertRecord(rw);

      }catch(XmlBlasterException e){
         Log.warning(ME,"Can't insert PMessageUnit to swapfile : "+e.reason);
      }catch(IOException io){
         Log.error(ME,io.toString());
      }
   }


   /**
    * Delete a MessageUnit from cache and xmldb.
    * <br>
    * @param oid The Key oid
    */
   public void delete(String oid)
   {
      synchronized(_cacheMap)
      {
         PMessageUnit pmu = (PMessageUnit)_cacheMap.get(oid);

         // Check MessageUnit in Cache and then delete it.
         if(pmu!=null)
         {
            // Remove MessageUnit from Cache
            Object o = _cacheMap.remove(oid);

            String deletedOid = _lru.removeEntry(oid);
            if(deletedOid == null)
               Log.warning(ME,"Can't delete oid:"+deletedOid+" from Lru.");

            // Calculate new cachesize.
            _cacheSize = _cacheSize - pmu.size;
         }
      }


      // Remove MessageUnit from swapfile.
      if(_fileSwap.recordExists(oid))
      {
         try{
               _fileSwap.deleteRecord(oid);
         }catch(XmlBlasterException e){
            Log.warning(ME,"Can't delete PMessageUnit from swapfile : "+e.reason);
         }catch(IOException io){
            Log.warning(ME,io.toString());
         }
      }


      // Remove MessageUnit from filedb.
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
      checkCacheSize();
      synchronized(_cacheMap){
         _cacheMap.put(pmu.oid,pmu);
      }

         // Add MessageUnit-Oid to Lru.
         _lru.addEntry(pmu.oid);
         _cacheSize = _cacheSize + pmu.size;
   }


   public PMessageUnit read(String oid)
   {
      // Message is durable and present in filedb and cache
      PMessageUnit pmu = (PMessageUnit)_cacheMap.get(oid);
      if(pmu==null)
      {
         // Read miss
         pmu = readMiss(oid);
         if(pmu==null){
            return pmu;
         }

         /** CopyBack to Cache  */
         if(!(pmu.size < _maxMsgSize)){
            checkCacheSize();
            copyBack(pmu);
         }

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

      // First read swapfile
      if(_fileSwap.recordExists(oid))
      {
         try
         {
            RecordReader rr = _fileSwap.readRecord(oid);
            pmu = (PMessageUnit)rr.readObject();

            if(!(pmu.size < _maxMsgSize))
               _fileSwap.deleteRecord(oid);

         }catch(XmlBlasterException e){
            Log.error(ME,"Can't read PMessageUnit from swapfile : "+e.reason);
         }catch(IOException io){
            Log.error(ME,io.toString());
         }catch(ClassNotFoundException cnfe){
            Log.error(ME,"Can't find PMessageUnit for TypeCast."+cnfe.toString());
         }
         return pmu;
      }


      // Durable MessageUnits
      if(_fileDb.recordExists(oid))
      {
         try
         {
            RecordReader rr = _fileDb.readRecord(oid);
            pmu = (PMessageUnit)rr.readObject();

         }catch(XmlBlasterException e){
            Log.error(ME,"Can't read PMessageUnit from database : "+e.reason);
         }catch(IOException io){
            Log.error(ME,io.toString());
         }catch(ClassNotFoundException cnfe){
            Log.error(ME,"Can't find PMessageUnit for TypeCast."+cnfe.toString());
         }
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

   public void setMaxCacheSize(long sizeInByte){
      _maxCacheSize = sizeInByte;
   }

   public void setMaxMsgSize(long sizeInByte){
      _maxMsgSize = sizeInByte;
   }

   public int getNumDurable()
   {
      return _fileDb.getNumRecords();
   }

   public int getNumSwapped()
   {
      return _fileSwap.getNumRecords();
   }



   //This Method is only for testing the cache.
   public void reset()
   {
      _overflowCount = 0;
   }


   public void statistic()
   {
      Log.info(ME,"----------------------------------------------------");
      Log.info(ME,"      Max. Cachesize               = "+_maxCacheSize);
      Log.info(ME,"      Max. Messagesize             = "+_maxMsgSize);
      Log.info(ME,"      Cacheoverflow                = "+_overflowCount);
      Log.info(ME,"      Number of Messages in Cache  = "+_cacheMap.size());
      Log.info(ME,"      Number of Messages isDurable = "+getNumDurable());
      Log.info(ME,"      Number of Messages swapped   = "+getNumSwapped());
      Log.info(ME,"      Cachesize                    = "+_cacheSize );
      Log.info(ME,"----------------------------------------------------");
   }

}
