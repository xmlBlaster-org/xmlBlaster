/*------------------------------------------------------------------------------
Name:      Queue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Queue for client messages
Version:   $Id: Queue.java,v 1.2 2000/02/25 13:51:01 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
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
 * @version $Revision: 1.2 $
 * @author $Author: ruff $
 */
public class Queue
{
   private String ME = "Queue";
   private final int MAX_ENTRIES;

   /**
    * The queue is implemented with a linked list.
    * I believe ArrayList is more expensive in this case
    */
   private LinkedList queueList = null;


   /**
    * Constructs an empty FIFO queue.
    * @param maxEtnries The maximum number of nodes for this queue
    */
   public Queue(int maxEntries)
   {
      this.MAX_ENTRIES = maxEntries;
      if (Log.CALLS) Log.calls(ME, "Creating new Queue(" + MAX_ENTRIES + ") ...");
      init();
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
    * Add a unit to the queue.
    * <p />
    * @exception no more space
    */
   public final void push(Object obj) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering push() ...");
      synchronized (queueList) {
         if (queueList.size() >= MAX_ENTRIES)
            throw new  XmlBlasterException(ME+".MaxSize", "Maximun size of queue reached");
         queueList.addFirst(obj);
      }
   }


   /**
    * pull the next unit from the queue.
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
      Queue queue = new Queue(size);
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
      Log.info(me, "The for a queue with max size=" + size + " the result is: " + result.toString());
   }
}
