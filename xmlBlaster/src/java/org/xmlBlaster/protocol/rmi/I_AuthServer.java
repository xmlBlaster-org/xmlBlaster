/*------------------------------------------------------------------------------
Name:      I_AuthServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Authentication access for RMI clients.
Version:   $Id: I_AuthServer.java,v 1.1 2000/06/13 15:14:45 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.xmlBlaster.util.XmlBlasterException;
import java.rmi.RemoteException;


/**
 * Interface to login to xmlBlaster.
 * @author ruff@swand.lake.de
 */
public interface I_AuthServer extends java.rmi.Remote
{
   /**
    * Does a login, returns a handle to xmlBlaster interface. 
    * <p />
    * TODO: Allow passing an external sessionId in qos?
    * @param loginName The unique login name
    * @param password
    * @return sessionId The unique ID for this client
    * @exception XmlBlasterException If user is unknown
    */
   public String login(String loginName, String password, String qos_literal)
                        throws RemoteException, XmlBlasterException;


   /**
    * Does a logout.
    * <p />
    * @param sessionId The client sessionId
    * @exception XmlBlasterException If sessionId is invalid
    */
   public void logout(final String sessionId)
                        throws RemoteException, XmlBlasterException;


   /**
    * Test RMI connection.
    */
   /**
    * Check the server.
    * <p />
    */
   public boolean ping() throws RemoteException, XmlBlasterException;
}
