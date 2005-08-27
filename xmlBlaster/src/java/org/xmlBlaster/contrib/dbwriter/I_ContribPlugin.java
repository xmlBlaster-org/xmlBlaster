/*------------------------------------------------------------------------------
Name:      I_ContribPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter;

import org.xmlBlaster.contrib.I_Info;

public interface I_ContribPlugin {

   
   void init(I_Info info) throws Exception;
   
   void shutdown() throws Exception;
   
   
}
