/*------------------------------------------------------------------------------
Name:      I_MsgDistributor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.distributor;

import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.I_SubscriptionListener;
import org.xmlBlaster.util.plugin.I_Plugin;
// import org.xmlBlaster.util.I_ResponseListener;


/**
 * I_MsgDistributor
 *
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * 
 */
public interface I_MsgDistributor extends I_SubscriptionListener, I_Plugin {

   /**
    * This method should not throw any exception so it is responsability of
    * the plugin developer to catch Throwable and make the necessary
    * error handling.
    * 
    * @param msgUnitWrapper the entry to distribute
    */
   void distribute(MsgUnitWrapper msgUnitWrapper);

}
