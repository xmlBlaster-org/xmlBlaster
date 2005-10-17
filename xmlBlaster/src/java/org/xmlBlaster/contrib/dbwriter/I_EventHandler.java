/*------------------------------------------------------------------------------
Name:      I_EventHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter;

import java.util.Map;


public interface I_EventHandler {

   /**
    * The received message, either from the MoM or from somewhat else. 
    * @param topic The topic name
    * @param content The message content
    * @param attrMap A map with attribute, can be null. The values of the pairs are ClientProperty objects. 
    */
   void update(String topic, byte[] content, Map attrMap) throws Exception;
   
}
