/*------------------------------------------------------------------------------
Name:      I_QueueEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

public interface I_QueueEntry extends I_Entry, java.io.Serializable
{
   /**
    * Needed for sorting in queue
    */
   int compare(I_QueueEntry m2);

   /**
    * Needed for sorting in queue
    */
   boolean equals(I_QueueEntry m2);
}
