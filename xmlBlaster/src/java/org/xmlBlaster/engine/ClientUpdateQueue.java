/*------------------------------------------------------------------------------
Name:      ClientUpdateQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Queue for client messages
Version:   $Id: ClientUpdateQueue.java,v 1.3 1999/12/08 12:16:17 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
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
 * @version $Revision: 1.3 $
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
      this.messageQueue = (LinkedList)Collections.synchronizedList(new LinkedList());
   }


   /**
    * Constructs an empty FIFO queue.
    * @param maxBytes The maximum size for this queue (client quota)
    */
   public ClientUpdateQueue(long maxBytes)
   {
      this.MAX_BYTES = maxBytes;
      if (Log.CALLS) Log.calls(ME, "Creating new ClientUpdateQueue(" + maxBytes + ") ...");
      this.messageQueue = (LinkedList)Collections.synchronizedList(new LinkedList());
   }


   /**
    * Add a message unit to the queue.
    * <p />
    * @return true successfully stored message
    *         false no more space for this message
    */
   public final boolean push(MessageUnitWrapper messageUnitWrapper) throws XmlBlasterException
   {
      long size = messageUnitWrapper.getSizeInBytes();

      if (!queueHasPlace(size))
         return false;

      currentBytes += size;

      // we need to clone the message, if new updates of the SAME message arrive
      // we need to keep the content of the old message
      MessageUnitWrapper newWrapper = messageUnitWrapper.cloneContent();

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
            MessageUnitWrapper messageUnitWrapper = (MessageUnitWrapper)messageQueue.removeLast();
            if (messageUnitWrapper == null) return null;
            currentBytes -= messageUnitWrapper.getSizeInBytes();
            return messageUnitWrapper;
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
    * The total amount of bytes consumed by all messages in the queue.
    * TODO: !!! how to calculate???
    * <p />
    * @return the number of MessageUnitWrapper object waiting in the queue
    */
   public final long getBytesUsed() throws XmlBlasterException
   {
      Log.warning(ME, "Sorry, getBytesUsed() not yet implemented");
      return 0;
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
