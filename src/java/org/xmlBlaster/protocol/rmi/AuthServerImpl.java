/*------------------------------------------------------------------------------
Name:      AuthServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Authentication access for RMI clients.
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;


/**
 * Interface to login to xmlBlaster.
 * @author xmlBlaster@marcelruff.info
 */
public class AuthServerImpl extends UnicastRemoteObject implements org.xmlBlaster.protocol.rmi.I_AuthServer
{
   
   private static final long serialVersionUID = 1L;
   private final Global glob;
   private static Logger log = Logger.getLogger(AuthServerImpl.class.getName());
   private final I_Authenticate authenticate;
   private final AddressServer addressServer;


   /**
    * One instance implements an authentication server.
    * <p />
    * This server delegates all requests to xmlBlaster.authenticate package
    * @param authenticate The authentication service
    * @param blasterNative The interface to access xmlBlaster
    */
   public AuthServerImpl(Global glob, AddressServer addressServer, 
                        I_Authenticate authenticate,
                        org.xmlBlaster.protocol.I_XmlBlaster blasterNative) throws RemoteException {
      this.glob = glob;

      this.authenticate = authenticate;
      this.addressServer = addressServer;
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
      if (log.isLoggable(Level.FINER)) log.call(ME, "Entering login(loginName=" + loginName + ")");

      if (loginName==null || passwd==null || qos_literal==null) {
         log.error(ME+"InvalidArguments", "login failed: please use no null arguments for login()");
         throw new XmlBlasterException("LoginFailed.InvalidArguments", "login failed: please use no null arguments for login()");
      }

      StopWatch stop=null; if (log.TIME) stop = new StopWatch();
      try {
         // Extend qos to contain security credentials ...
         ConnectQosServer connectQos = new ConnectQosServer(glob, qos_literal);
         connectQos.setSecurityPluginData(null, null, loginName, passwd);

         ConnectReturnQos returnQos = authenticate.connect(connectQos);
         if (log.isLoggable(Level.FINEST)) log.finest("Elapsed time in login()" + stop.nice());
         return returnQos.getSecretSessionId();
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.getMessage()); // transform native exception to Corba exception
      }
   }
    */

   /**
    * Login to xmlBlaster.
    * @param qos_literal See ConnectQosServer.java
    * @return The xml string from ConnectReturnQos.java<br />
    *         We could return the ConnectReturnQos object as well, but adding
    *         attributes to this object would force clients to install the new class
    *         declaration. In future we could use the Jini apporach here.
    * @see org.xmlBlaster.engine.qos.ConnectQosServer
    * @see org.xmlBlaster.engine.qos.ConnectReturnQosServer
    */
   public String connect(String qos_literal)
                        throws RemoteException, XmlBlasterException
   {
      String returnValue = null;
      ConnectQosServer connectQos = new ConnectQosServer(glob, qos_literal);
      if (log.isLoggable(Level.FINER)) log.finer("Entering connect(qos=" + qos_literal + ")");
      connectQos.setAddressServer(this.addressServer);
      ConnectReturnQosServer returnQos = authenticate.connect(connectQos);
      returnValue = returnQos.toXml();

      return returnValue;
   }

   public void disconnect(final String sessionId, String qos_literal)
                        throws RemoteException, XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering disconnect()");
      authenticate.disconnect(this.addressServer, sessionId, qos_literal);
      if (log.isLoggable(Level.FINER)) log.finer("Exiting disconnect()");
   }

   /*
    * Does a logout.
    * <p />
    * @param sessionId The client sessionId
    * @exception XmlBlasterException If sessionId is invalid
   public void logout(final String sessionId)
                        throws RemoteException, XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.call(ME, "Entering logout()");
      disconnect(sessionId, (new DisconnectQosServer(glob)).toXml());
   }
    */

   /**
    * Ping to check if the authentication server is alive. 
    * @see org.xmlBlaster.protocol.I_XmlBlaster#ping(String)
    */
   public String ping(String qos) throws RemoteException
   {
      return authenticate.ping(this.addressServer, qos);
   }
}
