/*------------------------------------------------------------------------------
Name:      I_ContribPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib;

import java.util.Set;


public interface I_ContribPlugin {
   
   void init(I_Info info) throws Exception;
   
   /**
    * @see I_Plugin
    * @throws Exception
    */
   void shutdown() throws Exception;
   
   /**
    * Gets all property keys which may be used by this object.
    * @return
    */
   Set getUsedPropertyKeys();
   
}
