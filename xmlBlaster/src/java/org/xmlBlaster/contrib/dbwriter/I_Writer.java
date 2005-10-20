/*------------------------------------------------------------------------------
Name:      I_Storer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter;

import org.xmlBlaster.contrib.I_ContribPlugin;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfo;

public interface I_Writer extends I_ContribPlugin, I_Update {

   void store(DbUpdateInfo info) throws Exception;
   
}
