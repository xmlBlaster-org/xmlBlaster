/*------------------------------------------------------------------------------
Name:      AuthServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Authentication access for RMI clients.
Version:   $Id: AuthServerImpl.java,v 1.1 2000/06/13 15:14:45 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.xmlBlaster.util.*;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnitContainer;
import org.xmlBlaster.authentication.Authenticate;

import java.rmi.RemoteException;


/**
 * Interface to login to xmlBlaster.
 * @author ruff@swand.lake.de
 */
public class AuthServerImpl implements I_AuthServer
{
   private String ME = "AuthServerImpl";
   private Authenticate authenticate;


   /**
    * One instance implements an authentication server. 
    * <p />
    * This server delegates all requests to xmlBlaster.authenticate package
    * @parma authenticate The authentication service
    * @param blasterNative The interface to access xmlBlaster
    */
   public AuthServerImpl(Authenticate authenticate, org.xmlBlaster.protocol.I_XmlBlaster blasterNative)
   {
      if (Log.CALLS) Log.calls(ME, "Entering constructor ...");
      this.authenticate = authenticate;
   }


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
                        throws RemoteException, XmlBlasterException
   {
      String sessionId = null; // pass this in future with qos?
      if (Log.CALLS) Log.calls(ME, "Entering login(loginName=" + loginName/* + ", qos=" + qos_literal */ + ")");

      if (loginName==null || password==null || qos_literal==null) {
         Log.error(ME+"InvalidArguments", "login failed: please use no null arguments for login()");
         throw new XmlBlasterException("LoginFailed.InvalidArguments", "login failed: please use no null arguments for login()");
      }

      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();
      try {
         String tmpSessionId = authenticate.login(loginName, password, qos_literal, sessionId);
         if (tmpSessionId == null || !tmpSessionId.equals(sessionId)) {
            Log.warning(ME+".AccessDenied", "Login for " + loginName + " failed.");
            throw new XmlBlasterException("LoginFailed.AccessDenied", "Sorry, access denied");
         }
         if (Log.TIME) Log.time(ME, "Elapsed time in login()" + stop.nice());
         return tmpSessionId;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }


   /**
    * Does a logout.
    * <p />
    * @param sessionId The client sessionId
    * @exception XmlBlasterException If sessionId is invalid
    */
   public void logout(final String sessionId)
                        throws RemoteException, XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering logout()");
      authenticate.logout(sessionId);
   }


   /**
    * Test RMI connection.
    * @return true
    */
   public boolean ping() throws RemoteException, XmlBlasterException
   {
      return true;
   }
}
