/*------------------------------------------------------------------------------
Name:      I_RemotePropertiesListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Listens on remote properties send from clients
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import java.util.Map;

import org.xmlBlaster.authentication.SessionInfo;

/**
 * Listens on remote properties send from clients. 
 * <p>
 * The events are fired by the RequestBroker instance.
 * Is triggered for arriving topic __sys__remoteProperties.
 *
 * @author Marcel Ruff
 */
public interface I_RemotePropertiesListener extends java.util.EventListener {
   /**
    * Invoked when RemoteProperties have arrived.
    * @param sessionInfo The client sending the properties,
    *                     the remoteProperties are added to this sessionInfo already
    * @param remoteProperties Key is a string, value is of type ClientProperties 
    */
   public void update(SessionInfo sessionInfo, Map remoteProperties);
}
