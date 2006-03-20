/*------------------------------------------------------------------------------
Name:      I_SessionPersitencePlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine;

import org.xmlBlaster.authentication.I_ClientListener;
import org.xmlBlaster.util.plugin.I_Plugin;

/**
 * I_SessionPersitencePlugin provides the interface for the storage for both sessions
 * and subscriptions. 
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public interface I_SessionPersistencePlugin extends I_Plugin, I_ClientListener, I_SubscriptionListener {

   /** this is the string idenitfying this plugin: it is the one specified in the plugin configuration */
   public final static String ID="sessionPersistence";

}
