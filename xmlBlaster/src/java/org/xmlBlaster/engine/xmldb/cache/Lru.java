/*------------------------------------------------------------------------------
Name:      Lru.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Caches MessageUnits by LRU.
Version:   $Id: Lru.java,v 1.2 2000/08/29 11:17:38 kron Exp $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xmldb.cache;

import java.lang.ref.*;
import java.util.*;

import java.util.List;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Collections;
import java.util.NoSuchElementException;

import org.jutils.log.*;
import org.jutils.runtime.Memory;
import org.xmlBlaster.engine.PMessageUnit;


/**
 * This class caches MessageUnits by LRU-strategy ("least recently used").
 * If the cache has reached the max. Cachesize, then the cache will be 
 * swapped each MessageUnit with LRU to the file-database.
 */
public class Lru
{
   private static final String     ME = "LRU";
   private          LinkedList _queue = null;

   /**
   * This LRU implements a least recently used cache replacement strategy.
   */
   public Lru()
   {
      _queue = new LinkedList();
   }

   /**
   * Add a new oid at the end of the queue. It's the newest one.
   * <br />
   * @param oid The oid of the MessageUnit, which is currently present in cache.
   */
   public void addEntry(String oid)
   {
      synchronized (_queue){
         _queue.addLast(oid);
      }
   }


   /**
    * Removes the oldest oid for swapping.
    * @param oid : The oldest oid for swapping in the swapfile.
    */
   public String removeOldest()
   {
      synchronized (_queue){
         if(_queue.isEmpty())
            return null;
      }
         return (String)_queue.removeFirst();
   }

   /**
    * Removes an entry from Lru-List. This method invoked by the cache, when delete
    * a MessageUnit from the Cache and file.
    * @param oid The oid of the MessageUnit.
    */
   public String removeEntry(String oid)
   {
      synchronized (_queue){
         int index = _queue.indexOf(oid);
         if(index > -1){
            return (String)_queue.get(index);
         }else{
            return null;
         }
      }
   }
 
   /**
    * Deletes all oid's, which are currently present in cache.
    */
   public void clear()
   {
      synchronized (_queue){
         _queue.clear();
      }
   }

}

