/*------------------------------------------------------------------------------
Name:      AuthServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Authentication access for RMI clients.
Version:   $Id: AuthServerImpl.java,v 1.21 2002/12/18 16:15:22 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.jutils.time.StopWatch;

import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.util.DisconnectQos;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;


/**
 * Interface to login to xmlBlaster.
 * @author xmlBlaster@marcelruff.info
 */
public class AuthServerImpl extends UnicastRemoteObject implements org.xmlBlaster.protocol.rmi.I_AuthServer
{
   private String ME = "AuthServerImpl";
   private final Global glob;
   private final LogChannel log;
   private final I_Authenticate authenticate;


   /**
    * One instance implements an authentication server.
    * <p />
    * This server delegates all requests to xmlBlaster.authenticate package
    * @param authenticate The authentication service
    * @param blasterNative The interface to access xmlBlaster
    */
   public AuthServerImpl(Global glob, I_Authenticate authenticate, org.xmlBlaster.protocol.I_XmlBlaster blasterNative) throws RemoteException {
      this.glob = glob;
      this.log = glob.getLog("rmi");
      this.authenticate = authenticate;
   }

   /*
    * Does a login, returns a handle to xmlBlaster interface.
    * <p />
    * @param loginName The unique login name
    * @param passwd
    * @return sessionId The unique ID for this client
    * @exception XmlBlasterException If user is unknown
   public String login(String loginName, String passwd, String qos_literal)
                        throws RemoteException, XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering login(loginName=" + loginName + ")");

      if (loginName==null || passwd==null || qos_literal==null) {
         log.error(ME+"InvalidArguments", "login failed: please use no null arguments for login()");
         throw new XmlBlasterException("LoginFailed.InvalidArguments", "login failed: please use no null arguments for login()");
      }

      StopWatch stop=null; if (log.TIME) stop = new StopWatch();
      try {
         // Extend qos to contain security credentials ...
         ConnectQos connectQos = new ConnectQos(glob, qos_literal);
         connectQos.setSecurityPluginData(null, null, loginName, passwd);

         ConnectReturnQos returnQos = authenticate.connect(connectQos);
         if (log.TIME) log.time(ME, "Elapsed time in login()" + stop.nice());
         return returnQos.getSessionId();
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.getMessage()); // transform native exception to Corba exception
      }
   }
    */

   /**
    * Login to xmlBlaster.
    * @param qos_literal See ConnectQos.java
    * @return The xml string from ConnectReturnQos.java<br />
    *         We could return the ConnectReturnQos object as well, but adding
    *         attributes to this object would force clients to install the new class
    *         declaration. In future we could use the Jini apporach here.
    * @see org.xmlBlaster.util.ConnectQos
    * @see org.xmlBlaster.util.ConnectReturnQos
    */
   public String connect(String qos_literal)
                        throws RemoteException, XmlBlasterException
   {
      String returnValue = null;
      ConnectQos connectQos = new ConnectQos(glob, qos_literal);
      if (log.CALL) log.call(ME, "Entering connect(qos=" + qos_literal + ")");

      StopWatch stop=null; if (log.TIME) stop = new StopWatch();
      ConnectReturnQos returnQos = authenticate.connect(connectQos);
      returnValue = returnQos.toXml();
      if (log.TIME) log.time(ME, "Elapsed time in connect()" + stop.nice());

      return returnValue;
   }

   public void disconnect(final String sessionId, String qos_literal)
                        throws RemoteException, XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering disconnect()");
      authenticate.disconnect(sessionId, qos_literal);
      if (log.CALL) log.call(ME, "Exiting disconnect()");
   }

   /*
    * Does a logout.
    * <p />
    * @param sessionId The client sessionId
    * @exception XmlBlasterException If sessionId is invalid
   public void logout(final String sessionId)
                        throws RemoteException, XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering logout()");
      disconnect(sessionId, (new DisconnectQos()).toXml());
   }
    */

   /**
    * Ping to check if the authentication server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    */
   public String ping(String str) throws RemoteException
   {
      return "";
   }
}
