/*------------------------------------------------------------------------------
Name:      AuthServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Authentication access for RMI clients.
Version:   $Id: AuthServerImpl.java,v 1.11 2001/09/05 10:05:32 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.jutils.time.StopWatch;

import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.engine.xml2java.LoginReturnQoS;
import org.xmlBlaster.client.LogoutQosWrapper;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;


/**
 * Interface to login to xmlBlaster.
 * @author ruff@swand.lake.de
 */
public class AuthServerImpl extends UnicastRemoteObject implements org.xmlBlaster.protocol.rmi.I_AuthServer
{
   private String ME = "AuthServerImpl";
   private I_Authenticate authenticate;


   /**
    * One instance implements an authentication server.
    * <p />
    * This server delegates all requests to xmlBlaster.authenticate package
    * @parma authenticate The authentication service
    * @param blasterNative The interface to access xmlBlaster
    */
   public AuthServerImpl(I_Authenticate authenticate, org.xmlBlaster.protocol.I_XmlBlaster blasterNative) throws RemoteException
   {
      if (Log.CALL) Log.call(ME, "Entering constructor ...");
      this.authenticate = authenticate;
   }


   /**
    * Does a login, returns a handle to xmlBlaster interface.
    * <p />
    * @param loginName The unique login name
    * @param passwd
    * @return sessionId The unique ID for this client
    * @exception XmlBlasterException If user is unknown
    * @deprecated
    */
   public String login(String loginName, String passwd, String qos_literal)
                        throws RemoteException, XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering login(loginName=" + loginName/* + ", qos=" + qos_literal */ + ")");

      if (loginName==null || passwd==null || qos_literal==null) {
         Log.error(ME+"InvalidArguments", "login failed: please use no null arguments for login()");
         throw new XmlBlasterException("LoginFailed.InvalidArguments", "login failed: please use no null arguments for login()");
      }

      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();
      try {
         // Extend qos to contain security credentials ...
         ConnectQos connectQos = new ConnectQos(qos_literal);
         connectQos.setSecurityPluginData("simple", "1.0", loginName, passwd);

         LoginReturnQoS returnQos = authenticate.connect(connectQos);
         if (Log.TIME) Log.time(ME, "Elapsed time in login()" + stop.nice());
         return returnQos.getSessionId();
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
   public String connect(String qos_literal)
                        throws RemoteException, XmlBlasterException
   {
      String returnValue = null;
      ConnectQos connectQos = new ConnectQos(qos_literal);
      if (Log.CALL) Log.call(ME, "Entering connect(qos=" + qos_literal + ")");

      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();
      try {
         LoginReturnQoS qos = authenticate.connect(connectQos);
         returnValue = qos.toXml();
         if (Log.TIME) Log.time(ME, "Elapsed time in connect()" + stop.nice());
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }

      return returnValue;
   }

   public void disconnect(final String sessionId, String qos_literal)
                        throws RemoteException, XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering disconnect()");
      authenticate.disconnect(sessionId, qos_literal);
      if (Log.CALL) Log.call(ME, "Exiting disconnect()");
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
      disconnect(sessionId, (new LogoutQosWrapper()).toXml());
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
