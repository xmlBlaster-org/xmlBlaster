/*------------------------------------------------------------------------------
Name:      I_Queue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface for the queues (persistent and cache)
Version:   $Id: I_Queue.java,v 1.2 2002/09/30 10:02:30 ruff Exp $
Author:    ruff@swand.lake.de, laghi@swissinfo.org
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * The Interface which all queues (persistent queues and cache queues) must
 * implement.
 *
 * ?? All questions are marked with ??
 * - Should we provide methods which delete all msg with a given oid ?
 * - Should we provide a switch which activates/deactivates the possibility
 *   to store the history of a message (since in some cases this might be
 *   undesidered).
 */
public interface I_Queue
{
   /**
    * Gets the references of the messages in the queue. Note that the data
    * which is referenced here may be changed by other threads.
    */
	QueueEntryId[] getEntryReferences () throws XmlBlasterException;

   /**
    * Gets a copy of the entries (the messages) in the queue. If the queue
    * is modified, this copy will not be affected. This method is useful for
    * client browsing.
    */
	MsgQueueEntry[] getEntries ()throws XmlBlasterException;


   /**
    * Puts one message queue entry on top of the queue.
    * @param msgQueueEntry the message queue entry to put into the queue.
    * @throws XmlBlasterException in case an error occurs. Possible causes of
    *         error can be a communication exception of the underlying
    *         implementation (jdbc, file system etc).
    */
   void put (MsgQueueEntry msgQueueEntry) throws XmlBlasterException;

	/**
    * Takes a message out of the queue. The ordering is first priority and
    * secondly timestamp.
    * @return MsgQueueEntry the least element with respect to the given ordering
    * @throws XmlBlasterException in case the underlying implementation gets
    *         an exception while retrieving the element.
    */
   MsgQueueEntry take () throws XmlBlasterException;

   /**
    * Returns the first element in the queue but does not remove it from that
    * queue (leaves it untouched).
    * @return MsgQueueEntry the least element with respect to the given
    *         ordering or null if the queue is empty.
    * @throws XmlBlasterException if the underlying implementation gets an exception.
    */
   MsgQueueEntry peek () throws XmlBlasterException;

	/**
    * Returns the number of elements in this queue
    * @return int the number of elements currently in the queue
    */
   int size () throws XmlBlasterException;

	/**
    * Sets the maximum size of this queue
    * @param maxSize the maximum  number of entries allowed for this queue
    *
    * ?? What should be done if the new size is lower than the current
    * ?? number of elements in the queue ??
    */
   void capacity (int maxSize) throws XmlBlasterException;

	/**
    * returns the maximum number of elements for this queue
    * @return int the maximum number of elements in the queue
    */
   int capacity () throws XmlBlasterException;

   /**
    * Updates the given message queue entry with a new value. Note that this
    * can be used if an entry with the unique id already exists.
	 * ?? Does this really make sense here since we need to store history ??
    * ?? Should we define a switch which can deactivate storage of history ??
    */
   int update (MsgQueueEntry msgQueueEntry) throws XmlBlasterException;

	/**
    * Erases the given queue entry.
    * @param msgQueueEntry the message to erase.
    */
   int erase (MsgQueueEntry msgQueueEntry) throws XmlBlasterException;

}
