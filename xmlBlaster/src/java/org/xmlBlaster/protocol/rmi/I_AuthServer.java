/*------------------------------------------------------------------------------
Name:      I_AuthServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Authentication access for RMI clients.
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.xmlBlaster.util.XmlBlasterException;
import java.rmi.RemoteException;


/**
 * Interface to login to xmlBlaster.
 * @author xmlBlaster@marcelruff.info
 */
public interface I_AuthServer extends java.rmi.Remote
{
   public String connect(String qos_literal)
                        throws RemoteException, XmlBlasterException;


   public void disconnect(final String sessionId, String qos_literal)
                        throws RemoteException, XmlBlasterException;

   /**
    * Ping to check if the authentication server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    */
   public String ping(String str) throws RemoteException;
}
