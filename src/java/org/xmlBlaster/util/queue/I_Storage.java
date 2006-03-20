/*------------------------------------------------------------------------------
Name:      I_Storage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

public interface I_Storage
{
   /**
    * @return true for RAM based queue, false for other types like CACHE and JDBC queues
    */
   boolean isTransient();
}
