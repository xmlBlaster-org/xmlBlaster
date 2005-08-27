/*------------------------------------------------------------------------------
Name:      I_Parser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter;

import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfo;

public interface I_Parser extends I_ContribPlugin {

   DbUpdateInfo parse(String data) throws Exception;
   
   
}
