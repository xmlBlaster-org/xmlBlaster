/*------------------------------------------------------------------------------
Name:      ClientEvent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Listens on clientRemove and clientAdded events
Version:   $Id: ClientEvent.java,v 1.2 1999/11/18 16:59:55 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.engine.ClientInfo;


/**
 * An event which indicates that a client did a login or logout. 
 * It carries the ClientInfo reference inside
 *
 * @version $Id: ClientEvent.java,v 1.2 1999/11/18 16:59:55 ruff Exp $
 * @author Marcel Ruff
 */
public class ClientEvent extends java.util.EventObject
{
   /**
    * Constructs a ClientEvent object.
    *
    * @param source the ClientInfo object
    */
   public ClientEvent(ClientInfo clientInfo)
   {
       super(clientInfo);
   }

   /**
    * Returns the originator of the event.
    *
    * @return the Authentication object that originated the event
    */
   public ClientInfo getClientInfo() {
       return (ClientInfo)source;
   }
}
