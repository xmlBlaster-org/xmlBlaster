/*------------------------------------------------------------------------------
Name:      ClientUpdateQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Queue for client messages
Version:   $Id: ClientUpdateQueue.java,v 1.9 2000/06/13 13:03:59 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import java.util.List;
import java.util.LinkedList;
import java.util.Collections;
import java.util.NoSuchElementException;


/**
 * This message queue stores all messages
 * until they are delivered at the next login of this client.
 * <p />
 * This queue is based on the Producer-Consumer design pattern,
 * with the distinction that the consumer is not polling but
 * notified asynchronous.
 *
 * @version $Revision: 1.9 $
 * @author $Author: ruff $
 */
public class ClientUpdateQueue
{
   private String ME = "ClientUpdateQueue";
   private final long MAX_BYTES;
   private long currentBytes = 0L;

   /**
    * All MessageUnit which can't be delivered to the client (if he is not logged in)
    * are queued here and are delivered when the client comes on line.
    * <p>
    * Node objects = MessageUnit object
    */
   private LinkedList messageQueue = null;   // i believe ArrayList is more expensive in this case


   /**
    * Constructs an empty FIFO queue.
    */
   public ClientUpdateQueue()
   {
      MAX_BYTES = 100 * 1000L;  // 100 kByte is maximum queue size
      if (Log.CALLS) Log.calls(ME, "Creating new ClientUpdateQueue ...");
      init();
   }


   /**
    * Constructs an empty FIFO queue.
    * @param maxBytes The maximum size for this queue (client quota)
    */
   public ClientUpdateQueue(long maxBytes)
   {
      this.MAX_BYTES = maxBytes;
      if (Log.CALLS) Log.calls(ME, "Creating new ClientUpdateQueue(" + maxBytes + ") ...");
      init();
   }


   /**
    * Allocates a new LinkedList.
    */
   private void init()
   {
      // this.messageQueue = (List)Collections.synchronizedList(new LinkedList());
      this.messageQueue = new LinkedList();  // !!! thread save?
   }


   /**
    * Add a message unit to the queue.
    * <p />
    * @return true successfully stored message
    *         false no more space for this message
    */
   public final boolean push(MessageUnitWrapper msgUnitWrapper) throws XmlBlasterException
   {
      long size = msgUnitWrapper.getSizeInBytes();

      if (!queueHasPlace(size))
         return false;

      currentBytes += size;

      // we need to clone the message, if new updates of the SAME message arrive
      // we need to keep the content of the old message
      MessageUnitWrapper newWrapper = msgUnitWrapper.cloneContent();

      synchronized (messageQueue) {
         messageQueue.addFirst(newWrapper);
      }

      return true;
   }


   /**
    * pull the next message from the queue.
    * <p />
    * @return MessageUnitWrapper
    */
   public final MessageUnitWrapper pull() throws XmlBlasterException
   {
      try {
         synchronized (messageQueue) {
            MessageUnitWrapper msgUnitWrapper = (MessageUnitWrapper)messageQueue.removeLast();
            if (msgUnitWrapper == null) return null;
            currentBytes -= msgUnitWrapper.getSizeInBytes();
            return msgUnitWrapper;
         }
      }
      catch (NoSuchElementException e) {
         return null;
      }
   }


   /**
    * Check the available quotas for this client.
    * <p />
    * @param size in bytes
    * @return true enough memory available
    *         false quota exceeded
    */
   public final boolean queueHasPlace(long size) throws XmlBlasterException
   {
      if (size + currentBytes <  MAX_BYTES) {
         return true;
      }

      return false;
   }


   /**
    * The total amount of bytes consumed by all message - contents in the queue.
    * <p />
    * TODO: !!! how to calculate more exactly the overhead of the message objects?
    * <p />
    * @return the consumed bytes of the content of all messages in the queue
    */
   public final long getBytesUsed() throws XmlBlasterException
   {
      Log.warning(ME, "Sorry, getBytesUsed() not yet implemented");
      return currentBytes;
   }


   /**
    * How many messages are in the queue.
    * <p />
    * @return bytes used
    */
   public final int getSize()
   {
      return messageQueue.size();
   }
}
