/*------------------------------------------------------------------------------
Name:      I_QueueEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

public interface I_QueueEntry extends java.io.Serializable
{
   /**
    * Allows to query the priority of this entry.
    * This is the highest order precedence in the sorted queue
    * @return The priority
    */
   int getPriority();

   /**
    * Returns true if the entry is durable (persistent), false otherwise.
    */
   boolean isDurable();

   /**
    * This is the second order criteria in the queue
    * @return The unique Id of this entry.
    */
   long getUniqueId();

   /**
    * Needed for sorting in queue
    */
   int compare(I_QueueEntry m2);

   /**
    * Needed for sorting in queue
    */
   boolean equals(I_QueueEntry m2);

   /**
    * gets the content of this queue entry (the embedded object). In
    * persistent queues this is the data which is stored as a blob.
    */
   Object getEmbeddedObject();


   /**
    * gets the type of the object embedded in this entry.
    * @return String the identifier which tells the I_EntryFactory how to
    *         deserialize this entry.
    */
   String getEmbeddedType();

   /**
    * Sets the queue to which this entry belongs.
    */
   void setQueue(I_Queue queue);

   /**
    * Return a human readable identifier for logging output.
    * <p>
    * See the derived class for a syntax description.
    * </p>
    */
   String getLogId();

   /**
    * returns the size in bytes of this entry.
    */
   long getSizeInBytes();
}
