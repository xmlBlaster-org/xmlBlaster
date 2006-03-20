/*------------------------------------------------------------------------------
Name:      Queue.java
Project:   jutils.org
Copyright: jutils.org, see jutils-LICENSE file
Comment:   Queue for client messages
Version:   $Id: Queue.java 5103 2002-05-27 15:22:42Z ruff $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import org.xmlBlaster.util.def.ErrorCode;


/**
 * This queue (FIFO) implementation may serve as your base class
 * or as a member variable. <br />
 * It is based on a linked list.
 * <p />
 * Example:
 * <pre>
 *   int size = 3;
 *   Queue queue = new Queue("Test", size);
 *   queue.setModeToDiscardOldest(); // if queue is full, oldest entry is removed
 *   try {
 *       queue.push("Hello ");
 *       queue.push("world.");
 *   }
 *   catch (XmlBlasterException e) {
 *   }
 *   System.out.println(queue.pull()); // prints "Hello "
 *   System.out.println(queue.pull()); // prints "world."
 * </pre>
 *
 * TODO: Allow persistence store e.g. via JDBC bridge into Oracle with some smart caching
 * @version $Revision: 1.8 $
 * @author ruff@swand.lake.de
 */
public class SimpleXbQueue
{
   private String name = "";
   private final int MAX_ENTRIES;
   /** Default is false, and you get an Exception if queue is full */
   private boolean discardOldest = false;
   /** Throw the message away if queue is full - the message is silently lost! */
   private boolean discard = false;
   private long numLost = 0;


   /**
    * The queue is implemented with a linked list.
    * I believe ArrayList is more expensive in this case
    */
   private LinkedList queueList = null;


   /**
    * Constructs an empty FIFO queue.<p />
    * 
    * @param      name
    *             A nice name, for error reporting only.
    * @param      maxEntries
    *             The maximum number of nodes for this queue.
    */
   public SimpleXbQueue(String name, int maxEntries)
   {
      this.name = name;
      this.MAX_ENTRIES = maxEntries;
      init();
    }


   /**
    * Allocates a new LinkedList.
    */
   private void init()
   {
      // this.queueList = (List)Collections.synchronizedList(new LinkedList());
      this.queueList = new LinkedList(); // !!! thread save?
   }


   /**
    * Check if the queue is filled up.<p />
    * 
    * @return     'true' if space for at least on more entry does exist, 
    *             'false' if quota is exceeded.
    */
   public final boolean isFull() 
   {
      if (queueList.size() >= MAX_ENTRIES)
         return true;
      return false;
   }


   /**
    * Pull the next unit from the end of the queue.<p />
    * 
    * @return     The last object of the queue or null if no element is in 
    *             the queue.
    */
   public final Object pull()
   {
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
    * Add a unit to the beginning of the queue.<p />
    * 
    * @param      obj
    *             The object which should be added to the beginning of 
    *             the queue.
    * @exception  JUtilsException
    *             This exception is thrown of no more space is available.
    */
   public final void push(Object obj) 
      throws XmlBlasterException
   {
      synchronized (queueList) {
         if (queueList.size() >= MAX_ENTRIES) {
            if (discardOldest) {
               pull(); // Discard oldest
               numLost++;
            }
            else if (discard) {
               // message is silently lost
               numLost++;
               return;
            }
            else {
               throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_EXHAUST, "SimpleQueue["+this.name+"].MaxSize", "Maximun size=" + MAX_ENTRIES + " of queue '" + name + "' reached");
            }
         }
         queueList.addFirst(obj);
      }
    }


   /**
    * Returns a list-iterator of the elements in this list (in proper 
    * sequence), starting at the specified position in the list.
    * <p />
    * TODO: Discuss thread safety when using this list.<p />
    * 
    * @param      index 
    *             Position of the first element to be returned from the 
    *             list-iterator (by a call to next).
    * @return     A ListIterator of the elements in this list (in proper 
    *             sequence), starting at the specified position in the list.
    * @exception  IndexOutOfBoundsException
    *             if index is out of range (index < 0 || index > size()).
    */
   public final ListIterator queueIterator(int index) 
      throws IndexOutOfBoundsException
   {
      return queueList.listIterator(index);
   }


   /**
    * Default is that an Exception is thrown if the queue is full.
    * Calling this method changes the behavior that if the queue is
    * filled, the supernumerary entries are falling out at the end and are lost.
    */
   public void setModeToDiscardOldest()
   {
      this.discard = false;
      this.discardOldest = true;
   }


   /**
    * Default is that an Exception is thrown if the queue is full.
    * Calling this method changes the behavior that if the queue is
    * filled, the supernumerary entries are falling out at the end and are lost.
    */
   public void setModeToDiscard()
   {
      this.discardOldest = false;
      this.discard = true;
   }


   /**
    * Counter for lost messages in 'discard' or 'discardOldest' mode
    */
   public long getNumLost()
   {
      return this.numLost;
   }


   /**
    * How many objects are in the queue.<p />
    * 
    * @return     The number of objects in this queue.
    */
   public final int size()
   {
      return queueList.size();
   }


   /**
    * This method is for testing only.<p />
    * 
    * To start this test type:<br />
    *   <code>java org.xmlBlaster.util.SimpleXbQueue</code>
    */
   public static void main(String args[]) throws Exception
   {
      int size = 3;
      SimpleXbQueue queue = new SimpleXbQueue("Test", size);
      queue.setModeToDiscardOldest();
      try {
         queue.push("Hello ");
         queue.push("world ");
         queue.push("my ");
         queue.push("dear ");
         queue.push("friend!");
      }
      catch (XmlBlasterException e) {
      }
      StringBuffer result = new StringBuffer("");
      while (queue.size() > 0) {
         result.append(queue.pull());
      }
      System.out.println("For a queue with max size=" + size + " the result is: " + result.toString());
   }
}
