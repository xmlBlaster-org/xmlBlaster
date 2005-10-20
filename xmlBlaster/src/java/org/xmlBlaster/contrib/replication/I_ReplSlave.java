/*------------------------------------------------------------------------------
Name:      I_ReplSlave.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import org.xmlBlaster.contrib.I_ContribPlugin;
import org.xmlBlaster.contrib.I_Update;

/**
 * I_ReplSlave
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public interface I_ReplSlave extends I_ContribPlugin, I_Update {
   
   /**
    * 3
    *
    */
   void prepareForRequest() throws Exception;
   
   /**
    * 4
    *
    */
   void requestInitialData() throws Exception;
   
   /**
    * 6
    *
    */
   void reactivateDestination() throws Exception;
   
   
   boolean checkForDestroy(String replKey) throws Exception;
   
}
