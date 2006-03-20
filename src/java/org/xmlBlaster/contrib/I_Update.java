/*------------------------------------------------------------------------------
Name:      I_Update.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib;

import java.util.Map;

/**
 * Helper to return subscribed messages.  
 * @author Marcel Ruff
 */
public interface I_Update {
   /**
    * The message received from the MoM. 
    * @param topic The topic name
    * @param content The message content
    * @param attrMap A map with attribute, can be null 
    */
   void update(String topic, byte[] content, Map attrMap) throws Exception;
}
