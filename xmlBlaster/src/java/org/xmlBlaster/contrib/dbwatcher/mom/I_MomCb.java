/*------------------------------------------------------------------------------
Name:      I_MomCb.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher.mom;

import java.util.Map;

/**
 * Helper to return subscribed messages.  
 * @author Marcel Ruff
 */
public interface I_MomCb {
   /**
    * The message received from the MoM. 
    * @param topic The topic name
    * @param content The message content
    * @param attrMap A map with attribute, can be null 
    */
   void update(String topic, String content, Map attrMap);
}
