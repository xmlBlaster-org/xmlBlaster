/*------------------------------------------------------------------------------
Name:      I_EventHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter;

import java.util.Map;


public interface I_EventHandler extends I_ContribPlugin {

   /**
    * The received message, either from the MoM or from somewhat else. 
    * @param topic The topic name
    * @param content The message content
    * @param attrMap A map with attribute, can be null 
    */
   void update(String topic, String content, Map attrMap) throws Exception;
   
}
