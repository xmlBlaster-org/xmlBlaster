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
    * @return The unique ID as a string (cached for performance)
    */
   String getUniqueIdStr();
}
