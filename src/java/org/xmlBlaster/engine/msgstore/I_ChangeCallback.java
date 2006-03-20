/*------------------------------------------------------------------------------
Name:      I_ChangeCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.msgstore;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.msgstore.I_MapEntry;

public interface I_ChangeCallback
{
   /**
    * Callback invoked by I_Map.change inside the synchronization point.
    * @param entry the entry to modify.
    * @return I_MapEntry the modified entry.
    * @throws XmlBlasterException if something has gone wrong and the change must be rolled back.
    */                             
   I_MapEntry changeEntry(I_MapEntry entry) throws XmlBlasterException;
}
