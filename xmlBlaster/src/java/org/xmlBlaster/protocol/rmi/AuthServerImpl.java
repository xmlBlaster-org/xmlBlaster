/*------------------------------------------------------------------------------
Name:      AuthServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Authentication access for RMI clients.
Version:   $Id: AuthServerImpl.java,v 1.7 2001/08/19 23:07:54 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.xmlBlaster.util.Log;
import org.jutils.time.StopWatch;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.engine.xml2java.LoginReturnQoS;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;


/**
 * Interface to login to xmlBlaster.
 * @author ruff@swand.lake.de
 */
public class AuthServerImpl extends UnicastRemoteObject implements org.xmlBlaster.protocol.rmi.I_AuthServer
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
   public AuthServerImpl(Authenticate authenticate, org.xmlBlaster.protocol.I_XmlBlaster blasterNative) throws RemoteException
   {
      if (Log.CALL) Log.call(ME, "Entering constructor ...");
      this.authenticate = authenticate;
   }


   /**
    * Does a login, returns a handle to xmlBlaster interface.
    * <p />
    * @param loginName The unique login name
    * @param password
    * @return sessionId The unique ID for this client
    * @exception XmlBlasterException If user is unknown
    * @deprecated
    */
   public String login(String loginName, String password, String qos_literal)
                        throws RemoteException, XmlBlasterException
   {
      String sessionId = null; // pass this in future with qos?
      if (Log.CALL) Log.call(ME, "Entering login(loginName=" + loginName/* + ", qos=" + qos_literal */ + ")");

      if (loginName==null || password==null || qos_literal==null) {
         Log.error(ME+"InvalidArguments", "login failed: please use no null arguments for login()");
         throw new XmlBlasterException("LoginFailed.InvalidArguments", "login failed: please use no null arguments for login()");
      }

      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();
      try {
         String tmpSessionId = authenticate.login(loginName, password, qos_literal, sessionId);
         if (tmpSessionId == null || (sessionId != null && sessionId.length() > 2 && !tmpSessionId.equals(sessionId))) {
            Log.warn(ME+".AccessDenied", "Login for " + loginName + " failed.");
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
    * Login to xmlBlaster.
    * @parameter qos_literal See LoginQosWrapper.java
    * @return The xml string from LoginReturnQoS.java<br />
    *         We could return the LoginReturnQoS object as well, but adding
    *         attributes to this object would force clients to install the new class
    *         declaration. In future we could use the Jini apporach here.
    * @see org.xmlBlaster.client.LoginQosWrapper
    * @see org.xmlBlaster.engine.xml2java.LoginReturnQoS
    */
   public String init(String qos_literal)
                        throws RemoteException, XmlBlasterException
   {
      String returnValue = null;
      String sessionId = null;
      if (Log.CALL) Log.call(ME, "Entering init(qos=" + qos_literal + ")");

      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();
      try {
         LoginReturnQoS qos = authenticate.init(qos_literal, sessionId);
         returnValue = qos.toXml();
         if (Log.TIME) Log.time(ME, "Elapsed time in login()" + stop.nice());
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }

      return returnValue;
   }

   public void disconnect(final String sessionId, String qos_literal)
                        throws RemoteException, XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering logout()");
      authenticate.disconnect(sessionId, qos_literal);
      if (Log.CALL) Log.call(ME, "Exiting logout()");
   }

   /**
    * Does a logout.
    * <p />
    * @param sessionId The client sessionId
    * @exception XmlBlasterException If sessionId is invalid
    * @deprecated
    */
   public void logout(final String sessionId)
                        throws RemoteException, XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering logout()");
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
