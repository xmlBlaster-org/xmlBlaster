/*------------------------------------------------------------------------------
Name:      I_ClientListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Listens on clientRemove and clientAdded events
Version:   $Id: I_ClientListener.java,v 1.2 2000/06/13 13:03:58 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;


/**
 * Listens on clientRemove and clientAdded events.
 * <p>
 * The events are fired by the Authenticate object.
 *
 * @version $Revision: 1.2 $
 * @author $Author: ruff $
 */
public interface I_ClientListener extends java.util.EventListener {
    /**
     * Invoked on successfull client login
     */
    public void clientAdded(ClientEvent e) throws org.xmlBlaster.util.XmlBlasterException;

    /**
     * Invoked when client does a logout
     */
    public void clientRemove(ClientEvent e) throws org.xmlBlaster.util.XmlBlasterException;
}
