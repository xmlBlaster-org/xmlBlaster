/*------------------------------------------------------------------------------
Name:      I_Storer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter;

import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfo;

public interface I_Storer extends I_ContribPlugin {

   void store(DbUpdateInfo info) throws Exception;
   
}
