/*------------------------------------------------------------------------------
Name:      ClientEvent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Listens on clientRemove and clientAdded events
Version:   $Id: ClientEvent.java,v 1.3 1999/12/09 13:28:36 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.engine.ClientInfo;


/**
 * An event which indicates that a client did a login or logout.
 * It carries the ClientInfo reference inside
 *
 * @version $Id: ClientEvent.java,v 1.3 1999/12/09 13:28:36 ruff Exp $
 * @author Marcel Ruff
 */
public class ClientEvent extends java.util.EventObject
{
   /**
    * Constructs a ClientEvent object.
    *
    * @param the client which does the login or logout
    */
   public ClientEvent(ClientInfo clientInfo)
   {
       super(clientInfo);
   }

   /**
    * Returns the originator of the event.
    *
    * @return the client which does the login or logout
    */
   public ClientInfo getClientInfo() {
       return (ClientInfo)source;
   }
}
