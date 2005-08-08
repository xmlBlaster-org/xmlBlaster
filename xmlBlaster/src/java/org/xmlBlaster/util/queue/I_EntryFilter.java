/*------------------------------------------------------------------------------
Name:      I_EntryFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

/**
 * Callback when entries are accessed from storage. 
 */
public interface I_EntryFilter
{
   /**
    * Invoked by the I_Map or I_Queue implementation when entries are read from the store. 
    * Your implementation may not throw any exception, the behavior in such a case is undefined.
    * @param entry The current entry read
    * @return The entry to use, if null the given entry is filtered away.
    *         It can be a new, manipulated entry as well
    */
   public I_Entry intercept(I_Entry entry);
}
