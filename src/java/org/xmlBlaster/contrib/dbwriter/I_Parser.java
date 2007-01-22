/*------------------------------------------------------------------------------
Name:      I_Parser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter;

import org.xml.sax.InputSource;
import org.xmlBlaster.contrib.I_ContribPlugin;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;

public interface I_Parser extends I_ContribPlugin {

   /**
    * 
    * @param data
    * @param encoding The encoding if you want to speciy one, if null, the default is taken.
    * @return
    * @throws Exception
    */
   SqlInfo parse(InputSource data, String encoding) throws Exception;
   
}
