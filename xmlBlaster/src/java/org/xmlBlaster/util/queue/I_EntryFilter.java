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
    * Note: Expect to receive same entry twice if the underlying implementation is a
    * CACHE. There the RAM and the JDBC store may independently deliver the same entry
    * if the entry is persistent and currently in the cache.
    * @param entry The current entry read, is never null
    * @param storage the method isTransient() allows to check the source of the event (RAM or JDBC)
    *         Is never null 
    * @return The entry to use, if null the given entry is filtered away.
    *         It can be a new, manipulated entry as well
    */
   public I_Entry intercept(I_Entry entry, I_Storage storage);
}
