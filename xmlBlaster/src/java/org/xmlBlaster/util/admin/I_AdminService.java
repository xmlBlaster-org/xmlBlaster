/*------------------------------------------------------------------------------
Name:      I_AdminQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.admin;

/**
 * Declares available methods to control a xmlBlaster service plugin.   
 *
 * @author xmlBlaster@marcelruff.info
 * @since 1.0.8
 */
public interface I_AdminService extends I_AdminPlugin {
   /**
    * Activate this service
    */
   public void activate() throws Exception;

   /**
    * Deactivate the service to standby. 
    * A call to activate() fires the service up again
    */
   public void deActivate();
}
