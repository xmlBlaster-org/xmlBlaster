/*------------------------------------------------------------------------------
Name:      ClientUpdateQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Queue for client messages
Version:   $Id: ClientUpdateQueue.java,v 1.1 1999/12/01 22:17:28 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import java.util.List;
import java.util.LinkedList;
import java.util.Collections;


/**
 * This message queue stores all messages
 * until they are delivered at the next login of this client. 
 * <p />
 * This queue is based on the Producer-Consumer design pattern,
 * with the distinction that the consumer is not polling but
 * notified asynchronous. 
 *
 * @version $Revision: 1.1 $
 * @author $Author: ruff $
 */
public class ClientUpdateQueue
{
   private String ME = "ClientUpdateQueue";
   private long MAX_SIZE = 100 * 1000;  // 100 kByte is maximum queue size

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
      if (Log.CALLS) Log.calls(ME, "Creating new ClientUpdateQueue ...");
      this.messageQueue = (LinkedList)Collections.synchronizedList(new LinkedList());
   }


   /**
    * Add a message unit to the queue. 
    * <p />
    */
   public final void push(MessageUnitWrapper messageUnitWrapper) throws XmlBlasterException
   {
      synchronized (messageQueue) {
         messageQueue.addFirst(messageUnitWrapper);
      }
   }


   /**
    * pull the next message from the queue. 
    * <p />
    * @return MessageUnitWrapper
    */
   public final MessageUnitWrapper pull() throws XmlBlasterException
   {
      synchronized (messageQueue) {
         return (MessageUnitWrapper)messageQueue.removeLast();
      }
   }


   /**
    * The total amount of bytes consumed by all messages in the queue. 
    * <p />
    * @return the number of MessageUnitWrapper object waiting in the queue
    */
   public final long getBytesUsed() throws XmlBlasterException
   {
      Log.error(ME, "Sorry, getBytesUsed() not yet implemented");
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
