/*------------------------------------------------------------------------------
Name:      I_ClientListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   sessionRemoved and sessionAdded events
Version:   $Id: I_ClientListener.java,v 1.5 2004/02/04 20:47:51 laghi Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * Listens on sessionRemoved and sessionAdded events.
 * <p>
 * The events are fired by the Authenticate object.
 *
 * @version $Revision: 1.5 $
 * @author $Author: laghi $
 */
public interface I_ClientListener extends java.util.EventListener {
    /**
     * Invoked on successful client login
     */
    public void sessionAdded(ClientEvent e) throws XmlBlasterException;

    /**
     * Invoked on first successful client login, when SubjectInfo is created
     */
    public void subjectAdded(ClientEvent e) throws XmlBlasterException;

   /**
    * Invoked before a client does a logout
    */
   public void sessionPreRemoved(ClientEvent e) throws XmlBlasterException;

   /**
    * Invoked when client does a logout
    */
   public void sessionRemoved(ClientEvent e) throws XmlBlasterException;

   /**
    * Invoked when client does its last logout
    */
   public void subjectRemoved(ClientEvent e) throws XmlBlasterException;
       
}
