/*------------------------------------------------------------------------------
Name:      Queue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Queue for client messages
Version:   $Id: Queue.java,v 1.5 2000/06/13 13:04:02 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.util.List;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Collections;
import java.util.NoSuchElementException;


/**
 * This queue (FIFO) implementation may serve as your base class
 * or as a member variable. <br />
 * It is based on a linked list.
 * <p />
 * TODO: Allow persistence store e.g. via JDBC bridge into Orcale with some smart caching
 * @version $Revision: 1.5 $
 * @author $Author: ruff $
 */
public class Queue
{
   private String ME = "Queue";
   private String name = "";
   private final int MAX_ENTRIES;
   /** Default is false, and you get an Exception if queue is full */
   private boolean discardOldest = false;

   /**
    * The queue is implemented with a linked list.
    * I believe ArrayList is more expensive in this case
    */
   private LinkedList queueList = null;


   /**
    * Constructs an empty FIFO queue.
    * @param name  A nice name, for error reporting only
    * @param maxEtnries The maximum number of nodes for this queue
    */
   public Queue(String name, int maxEntries)
   {
      this.name = name;
      this.MAX_ENTRIES = maxEntries;
      if (Log.CALLS) Log.calls(ME, "Creating new Queue(" + MAX_ENTRIES + ") ...");
      init();
   }


   /**
    * Default is that an Exception is thrown if the queue is full.
    * Calling this method changes the behavior that if the queue is
    * filled, the supernumerary entries are falling out at the end and are lost
    */
   public void setModeToDiscardOldest()
   {
      this.discardOldest = true;
   }


   /**
    * Allocates a new LinkedList.
    */
   private void init()
   {
      // this.queueList = (List)Collections.synchronizedList(new LinkedList());
      this.queueList = new LinkedList();  // !!! thread save?
   }


   /**
    * Add a unit to the beginning of the queue.
    * <p />
    * @exception no more space
    */
   public final void push(Object obj) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering push() ...");
      synchronized (queueList) {
         if (queueList.size() >= MAX_ENTRIES) {
            if (discardOldest)
               pull();  // Discard oldest
            else {
               Log.error(ME+".MaxSize", "Maximun size=" + MAX_ENTRIES + " of queue '" + name + "' reached");
               throw new  XmlBlasterException(ME+".MaxSize", "Maximun size=" + MAX_ENTRIES + " of queue '" + name + "' reached");
            }
         }
         queueList.addFirst(obj);
      }
   }


   /**
    * pull the next unit from the end of the queue.
    * <p />
    * @return The object
    */
   public final Object pull()
   {
      if (Log.CALLS) Log.calls(ME, "Entering pull() ...");
      try {
         synchronized (queueList) {
            return queueList.removeLast();
         }
      }
      catch (NoSuchElementException e) {
         return null;
      }
   }


   /**
    * Returns a list-iterator of the elements in this list (in proper sequence), starting at the specified position in the list.
    * <p />
    * TODO: Discuss thread safety when using this list
    * @parameter index of first element to be returned from the list-iterator (by a call to next).
    * @return a ListIterator of the elements in this list (in proper sequence), starting at the specified position in the list.
    * @exception IndexOutOfBoundsException - if index is out of range (index < 0 || index > size()).
    */
   public final ListIterator queueIterator(int index) throws IndexOutOfBoundsException
   {
      return queueList.listIterator(index);
   }


   /**
    * Check if the queue is filled up.
    * <p />
    * @return true space for at least on more entry
    *         false quota exceeded
    */
   public final boolean isFull() throws XmlBlasterException
   {
      if (queueList.size() >= MAX_ENTRIES)
         return true;
      return false;
   }


   /**
    * How many objects are in the queue.
    * <p />
    * @return number of objects
    */
   public final int size()
   {
      return queueList.size();
   }


   /**
    * Only for testing
    *    java org.xmlBlaster.util.Queue
    */
   public static void main(String args[]) throws Exception
   {
      String me = "Queue-Tester";
      int size = 3;
      Queue queue = new Queue("Test", size);
      queue.setModeToDiscardOldest();
      try {
         queue.push("Hello ");
         queue.push("world ");
         queue.push("my ");
         queue.push("dear ");
         queue.push("friend!");
      } catch(XmlBlasterException e) {}
      StringBuffer result = new StringBuffer("");
      while(queue.size() > 0) {
         result.append(queue.pull());
      }
      Log.info(me, "For a queue with max size=" + size + " the result is: " + result.toString());
   }
}
