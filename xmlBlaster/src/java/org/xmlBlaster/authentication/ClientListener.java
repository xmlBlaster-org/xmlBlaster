/*------------------------------------------------------------------------------
Name:      ClientListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Listens on clientRemove and clientAdded events
Version:   $Id: ClientListener.java,v 1.1 1999/11/17 23:38:47 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;


/**
 * Listens on clientRemove and clientAdded events. 
 * <p>
 * The events are fired by the Authenticate object.
 *
 * @version $Id: ClientListener.java,v 1.1 1999/11/17 23:38:47 ruff Exp $
 */
public interface ClientListener extends java.util.EventListener {
    /**
     * Invoked on successfull client login
     */
    public void clientAdded(ClientEvent e) throws org.xmlBlaster.serverIdl.XmlBlasterException;

    /**
     * Invoked when client does a logout
     */    
    public void clientRemove(ClientEvent e) throws org.xmlBlaster.serverIdl.XmlBlasterException;
}
