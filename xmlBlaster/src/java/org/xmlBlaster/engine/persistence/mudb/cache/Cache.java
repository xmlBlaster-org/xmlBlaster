/*------------------------------------------------------------------------------
Name:      Cache.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Caches MessageUnits by LRU.
Version:   $Id: Cache.java,v 1.3 2000/12/26 14:56:41 ruff Exp $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence.mudb.cache;

import java.io.*;
import java.util.*;

import org.jutils.JUtilsException;
import org.xmlBlaster.util.Log;
import org.jutils.init.Property;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.persistence.PMessageUnit;
import org.xmlBlaster.engine.persistence.mudb.file.*;

/**
 * This is the Cache for the MessageUnits of the File-database.
 */
public class Cache
{
   private static final String ME = "Cache";
   // Cache parameters
   private long     _maxCacheSize = 0L;
   private long        _cacheSize = 0L;
   private long       _maxMsgSize = 0L;
   private String         _dbPath = "";

   // File descriptors
   private static RecordsFile _fileDb;
   private static RecordsFile _fileSwap;

   // Cache structure
   private          SortedMap _cacheMap;
   private                Lru _lru;

   // Variables for statistics
   private int _overflowCount = 0;
   private int      _msgToBig = 0;

   /**
    * Constructor of the Cache.
    */
   public Cache()
   {
      _maxCacheSize = XmlBlasterProperty.get("mudb.Cachesize", 20000000L);
      _maxMsgSize   = XmlBlasterProperty.get("mudb.Messagesize",5000L);

      _lru = new Lru();

      // Cache Map
      _cacheMap = Collections.synchronizedSortedMap(new TreeMap());

      // File-database
      try
      {
        // Read properties
        _dbPath = XmlBlasterProperty.get("Persistence.Path",System.getProperty("user.dir"));

        // Test database-/swapfile
        File dbfile = new File(_dbPath+"/xmlBlaster.msg");
        File swapfile = new File(_dbPath+"/swap.msg");

        // Database-file
        if(!dbfile.exists())
        {
           // Create database-file
           _fileDb    =  new RecordsFile(_dbPath+"/xmlBlaster.msg", 64);
        }else
        {
           // Database-File exists and open it with "RW" access.
           _fileDb    =  new RecordsFile(_dbPath+"/xmlBlaster.msg","rw");
        }

        // Create Swapfile
        if(!swapfile.exists())
        {
           // Create new dbfile
           _fileSwap = new RecordsFile(_dbPath+"/swap.msg", 64);
        }else
        {
           // Dbfile exists and open it with "RW" access.
           _fileSwap    =  new RecordsFile(_dbPath+"/swap.msg","rw");
        }

      }catch(XmlBlasterException e){
      }catch(IOException e){
         Log.error(ME,"Error while opening file from database."+e.toString());
      }
   }


   /**
    * Invoked by the MuDbDriver, to insert MessageUnits to MessageUnit-Database.
    * <p />
    * A MessageUnit can be written in Memory-Cache or filedb. If the
    * MessageUnit is durable or bigger the max. messagesize, then write the
    * MessageUnit to file.
    * <br />
    *
    * @param pmsgUnit An extended MessageUnit for storing the MessageUnit.
    */
   public void write(PMessageUnit pmsgUnit)
   {

      if(pmsgUnit.oid == null){
         Log.warn(ME,"Sorry can't write to cache, because no oid was given.");
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
         // --- SWAP ---
         _overflowCount++;

         checkCacheSize();

         copyBack(pmsgUnit);
      }

   }


   /**
    * Checks the cachesize. If the cachesize is max. then swap the oldest
    * used message for the memory to the swapfile.
    */
   private void checkCacheSize()
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


   /**
    * Is cache overflow?
    * <p />
    * @return true if the cache is overflow.
    */
   private boolean isSwap()
   {
      if(_cacheSize > _maxCacheSize)
         return true;
      else
         return false;
   }


   /**
    * Writes a MessageUnit to the persistent-file.
    * <p />
    * @param pmu is an extented MessageUnit for storing durable.
    */
   private void writePersistent(PMessageUnit pmu)
   {
      RecordWriter rw = new RecordWriter(pmu.oid);
      try{
         rw.writeObject(pmu);

         if(!_fileDb.recordExists(pmu.oid)){
            _fileDb.insertRecord(rw);
         }
      }catch(XmlBlasterException e){
         Log.warn(ME,"Can't insert PMessageUnit to persistent-file : "+e.reason);
      }catch(IOException io){
         Log.error(ME,io.toString());
      }

   }

   /**
    * Write MessageUnits to the swapfile, if the cache has an overflow.
    * <p />
    * @param pmu is an extended MessageUnit for swaping in the swapfile.
    */
   private void writeSwap(PMessageUnit pmu)
   {
      RecordWriter rw = new RecordWriter(pmu.oid);
      try{
         rw.writeObject(pmu);

         if(!_fileSwap.recordExists(pmu.oid))
            _fileSwap.insertRecord(rw);

      }catch(XmlBlasterException e){
         Log.warn(ME,"Can't insert PMessageUnit to swapfile : "+e.reason);
      }catch(IOException io){
         Log.error(ME,io.toString());
      }
   }


   /**
    * Delete a MessageUnit from cache and MessageUnit-database.
    * <p />
    * @param oid The Key oid (oid="...")
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
               Log.warn(ME,"Can't delete oid:"+deletedOid+" from Lru.");

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
            Log.warn(ME,"Can't delete PMessageUnit from swapfile : "+e.reason);
         }catch(IOException io){
            Log.warn(ME,io.toString());
         }
      }


      // Remove MessageUnit from filedb.
      if(_fileDb.recordExists(oid))
      {
         try{
            _fileDb.deleteRecord(oid);
         }catch(XmlBlasterException e){
            Log.warn(ME,"Can't delete PMessageUnit from filedb : "+e.reason);
         }catch(IOException io){
            Log.warn(ME,io.toString());
         }
      }

   }


   /**
    * Copys the MessageUnit back to memory-cache.
    * <p />
    * Is a cache-operation.
    * @param pmu is the extended MessageUnit.
    */
   private void copyBack(PMessageUnit pmu)
   {
      checkCacheSize();
      synchronized(_cacheMap){
         _cacheMap.put(pmu.oid,pmu);
      }

         // Add MessageUnit-Oid to Lru.
         _lru.addEntry(pmu.oid);
         _cacheSize = _cacheSize + pmu.size;
   }

   /**
    * Read a MessageUnit by oid from the cache.
    * <p />
    * @param  oid          is the key-oid (oid="...")
    * @return the extended MessageUnit.
    */
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


   /**
    * Readmiss is invoked by the read-operation.
    * <p />
    * @param  oid Gets the MessageUnit by oid from file.
    * @return the extended MessageUnit.
    */
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


   /**
    * Sets the max. cachesize.
    * <p />
    * @param newSize is the new cachesize.
    */
   public void setCacheSize(long newSize){
      _cacheSize = newSize;
   }

   /**
    * Gets the current cachsize
    */
   public long getCacheSize(){
      return _cacheSize;
   }

   /**
    * Removes all cache-entries and also swapfile-entries.
    */
   public void clearCache(){
     // TODO
   }

   /**
    * Sets the max. cachsize. This method is only for tests.
    * The max. cachesize is configured by the xmlBlaster.properties-file
    */
   public void setMaxCacheSize(long sizeInByte){
      _maxCacheSize = sizeInByte;
   }

   /**
    * Sets the max. messagesize. It's only for tests.
    * The max. cachesize is configured by the xmlBlaster.properties-file
    */
   public void setMaxMsgSize(long sizeInByte){
      _maxMsgSize = sizeInByte;
   }

   /**
    * Gets the number of MessageUnits, which are stored in the file-database.
    * <p />
    * @return the number of MessageUnits
    */
   public int getNumDurable()
   {
      return _fileDb.getNumRecords();
   }

   /**
    * Gets the number of MessageUnits which are currently swapped by the cache.
    * <p />
    * @return the number of swapped MessageUnits.
    */
   public int getNumSwapped()
   {
      return _fileSwap.getNumRecords();
   }



   /**
    * This method is only for testing the cache.
    */
   public void reset()
   {
      _overflowCount = 0;
   }

   /**
     * Prints the current state of the cache to console.
     */
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
