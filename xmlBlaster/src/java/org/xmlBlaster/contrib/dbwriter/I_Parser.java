/*------------------------------------------------------------------------------
Name:      I_Parser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter;

import org.xmlBlaster.contrib.I_ContribPlugin;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;

public interface I_Parser extends I_ContribPlugin {

   SqlInfo parse(String data) throws Exception;
   
   
}
