/*------------------------------------------------------------------------------
Name:      I_ClientListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   sessionRemoved and sessionAdded events
Version:   $Id: I_ClientListener.java,v 1.3 2002/03/13 16:41:07 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;


/**
 * Listens on sessionRemoved and sessionAdded events.
 * <p>
 * The events are fired by the Authenticate object.
 *
 * @version $Revision: 1.3 $
 * @author $Author: ruff $
 */
public interface I_ClientListener extends java.util.EventListener {
    /**
     * Invoked on successful client login
     */
    public void sessionAdded(ClientEvent e) throws org.xmlBlaster.util.XmlBlasterException;

    /**
     * Invoked on first successful client login, when SubjectInfo is created
     */
    public void subjectAdded(ClientEvent e) throws org.xmlBlaster.util.XmlBlasterException;

    /**
     * Invoked when client does a logout
     */
    public void sessionRemoved(ClientEvent e) throws org.xmlBlaster.util.XmlBlasterException;

    /**
     * Invoked when client does its last logout
     */
    public void subjectRemoved(ClientEvent e) throws org.xmlBlaster.util.XmlBlasterException;
}
