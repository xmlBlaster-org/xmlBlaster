/*------------------------------------------------------------------------------
Name:      I_MapEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.msgstore;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.queue.I_Entry;

public interface I_MapEntry extends I_Entry, java.io.Serializable
{
   /**
    * @param > 0: increment, < 0 decrement
    */
   void incrementReferenceCounter(int count) throws XmlBlasterException;

   /**
    * @return The current value
    */
   int getReferenceCounter();

   /**
    * @return The unique ID as a string (cached for performance)
    */
   String getUniqueIdStr();
}
