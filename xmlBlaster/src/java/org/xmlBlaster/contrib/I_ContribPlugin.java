/*------------------------------------------------------------------------------
Name:      I_ContribPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib;


public interface I_ContribPlugin {
   
   void init(I_Info info) throws Exception;
   
   /**
    * @see I_Plugin
    * @throws Exception
    */
   void shutdown() throws Exception;
   
   
}
