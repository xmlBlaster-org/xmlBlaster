/*------------------------------------------------------------------------------
Name:      I_MapEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.msgstore;

import org.xmlBlaster.util.queue.I_Entry;

public interface I_MapEntry extends I_Entry, java.io.Serializable
{
   /**
    * The cache sets it to true when the entry is swapped
    * away. 
    * You should not write on a swapped away entry as those
    * changes are lost.
    * For 'ram' or 'jdbc' implementation this flag is not used
    * and remains the initial value (set it to false in your implementation
    * of I_MapEntry).
    */
   boolean isSwapped();

   /**
    * Used by the cache implementation to mark entries which will
    * be swapped to the persistent store. 
    */
   void isSwapped(boolean isSwapped);

   /**
    * @return The unique ID as a string (cached for performance)
    */
   String getUniqueIdStr();
}
