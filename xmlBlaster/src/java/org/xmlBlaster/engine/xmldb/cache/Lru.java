
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
   */
   public void addEntry(String oid)
   {
      synchronized (_queue){
         _queue.addLast(oid); 
      }     
   }


   public String removeOldest()
   {
      synchronized (_queue){
         if(_queue.isEmpty())
            return null;
      }
         return (String)_queue.removeFirst();
   }

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

   public void clear()
   {
      synchronized (_queue){
         _queue.clear();
      }
   }

}

