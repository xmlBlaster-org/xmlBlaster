/*------------------------------------------------------------------------------
Name:      RmiConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: RmiConnection.java,v 1.30 2003/01/18 16:57:21 ruff Exp $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.rmi;


import org.xmlBlaster.protocol.rmi.I_AuthServer;
import org.xmlBlaster.protocol.rmi.I_XmlBlaster;

import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.XmlBlasterSecurityManager;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;

import java.rmi.RemoteException;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.net.MalformedURLException;

import java.applet.Applet;


/**
 * This is a helper class, helping a Java client to connect to xmlBlaster
 * using RMI.
 * <p>
 * Please note that you don't need to use this wrapper, you can use the raw RMI
 * interface as well. You can also hack your own little wrapper, which does exactly
 * what you want.
 * <p>
 * There is a constructor for applets, and standalone Java clients.
 * <p />
 * If you need a fail save client, you can invoke the xmlBlaster RMI methods
 * through this class as well (for example use rmiConnection.publish() instead of the direct
 * RMI server.publish()).
 * <p />
 * If you want to connect from a servlet, please use the framework in xmlBlaster/src/java/org/xmlBlaster/protocol/http
 *
 * @version $Revision: 1.30 $
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class RmiConnection implements I_XmlBlasterConnection
{
   private String ME = "RmiConnection";
   private final Global glob;
   private final LogChannel log;

   private I_AuthServer authServer = null;
   private I_XmlBlaster blasterServer = null;
   private String sessionId = null;

   protected String loginName = null;
   private String passwd = null;
   protected ConnectQos loginQos = null;
   protected ConnectReturnQos connectReturnQos = null;

   /** XmlBlaster RMI registry listen port is 1099, to access for bootstrapping */
   public static final int DEFAULT_REGISTRY_PORT = 1099; // org.xmlBlaster.protocol.rmi.RmiDriver.DEFAULT_REGISTRY_PORT;

   /**
    * RMI client access to xmlBlaster for <strong>normal client applications</strong>.
    * <p />
    * @param arg  parameters given on command line
    */
   public RmiConnection(Global glob) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = glob.getLog("rmi");
      XmlBlasterSecurityManager.createSecurityManager(glob);
   }


   /**
    * RMI client access to xmlBlaster for <strong>applets</strong>.
    * <p />
    * @param ap  Applet handle
    */
   public RmiConnection(Global glob, Applet ap) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = glob.getLog("rmi");
       XmlBlasterSecurityManager.createSecurityManager(glob);
   }


   /**
    * Connect to RMI server.
    */
   private void initRmiClient() throws XmlBlasterException
   {
      String hostname = glob.getLocalIP();
      hostname = glob.getProperty().get("rmi.hostname", hostname);

      // default xmlBlaster RMI publishing port is 1099
      int registryPort = glob.getProperty().get("rmi.registryPort", DEFAULT_REGISTRY_PORT);
      String prefix = "rmi://" + hostname + ":" + registryPort + "/";


      String authServerUrl = prefix + "I_AuthServer";
      String addr = glob.getProperty().get("rmi.AuthServer.url", authServerUrl);
      Remote rem = lookup(addr);
      if (rem instanceof org.xmlBlaster.protocol.rmi.I_AuthServer) {
         authServer = (I_AuthServer)rem;
         log.info(ME, "Accessed xmlBlaster authentication reference with '" + addr + "'");
      }
      else {
         throw new XmlBlasterException("InvalidRmi", "No connect to '" + addr + "' possible, class needs to implement interface I_AuthServer.");
      }


      String xmlBlasterUrl = prefix + "I_XmlBlaster";
      addr = glob.getProperty().get("rmi.XmlBlaster.url", xmlBlasterUrl);
      rem = lookup(addr);
      if (rem instanceof org.xmlBlaster.protocol.rmi.I_XmlBlaster) {
         blasterServer = (I_XmlBlaster)rem;
         log.info(ME, "Accessed xmlBlaster server reference with '" + addr + "'");
      }
      else {
         throw new XmlBlasterException("InvalidRmi", "No connect to '" + addr + "' possible, class needs to implement interface I_XmlBlaster.");
      }
   }


   /**
    * Connect to RMI server.
    */
   private Remote lookup(String addr) throws XmlBlasterException
   {
      try {
         return Naming.lookup(addr);
      }
      catch (RemoteException e) {
         log.error(ME, "Can't access address ='" + addr + "', no rmi registry running");
         throw new XmlBlasterException(ME, "Can't access address ='" + addr + "', no rmi registry running");
      }
      catch (NotBoundException e) {
         log.error(ME, "The given address ='" + addr + "' is not bound to rmi registry: " + e.toString());
         throw new XmlBlasterException(ME, "The given address '" + addr + "' is not bound to rmi registry: " + e.toString());
      }
      catch (MalformedURLException e) {
         log.error(ME, "The given address ='" + addr + "' is invalid: " + e.toString());
         throw new XmlBlasterException(ME, "The given address '" + addr + "' is invalid: " + e.toString());
      }
      catch (Throwable e) {
         log.error(ME, "The given address ='" + addr + "' is invalid : " + e.toString());
         throw new XmlBlasterException(ME, "The given address '" + addr + "' is invalid : " + e.toString());
      }
   }

   /**
    * Reset
    */
   public void resetConnection()
   {
      authServer = null;
      blasterServer = null;
      sessionId = null;
   }

   /**
    * Accessing the xmlBlaster handle.
    * For internal use, throws an ordinary Exception if xmlBlaster==null
    * We use this for similar handling as org.omg exceptions.
    * @return Server
    */
   private I_XmlBlaster getXmlBlaster() throws XmlBlasterException {
      if (blasterServer == null) {
         if (log.TRACE) log.trace(ME, "No RMI connection available.");
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                                       "The RMI xmlBlaster handle is null, no connection available");
      }
      return blasterServer;
   }

   /**
    * @return The connection protocol name "RMI"
    */
   public final String getProtocol()
   {
      return "RMI";
   }

   /**
    * @param qos Has all credentials
    */
   public ConnectReturnQos connect(ConnectQos qos) throws XmlBlasterException
   {
      if (qos == null)
         throw new XmlBlasterException(ME+".connect()", "Please specify a valid QoS");

      this.loginQos = qos;
      this.loginName = qos.getUserId();
      this.passwd = null;

      this.ME = "RmiConnection-" + loginName;
      if (log.CALL) log.call(ME, "connect() ...");
      if (blasterServer != null) {
         log.warn(ME, "You are already logged in.");
         return this.connectReturnQos;
      }

      return loginRaw();
   }

   /**
    * Login to the server. 
    * <p />
    * @param loginName The login name for xmlBlaster
    * @param passwd    The login password for xmlBlaster
    * @param qos       The Quality of Service for this client
    * @exception       XmlBlasterException if login fails
    */
   public void login(String loginName, String passwd, ConnectQos qos) throws XmlBlasterException
   {
      this.ME = "RmiConnection-" + loginName;
      if (log.CALL) log.call(ME, "login() ...");
      if (blasterServer != null) {
         log.warn(ME, "You are already logged in.");
         return;
      }

      this.loginName = loginName;
      this.passwd = passwd;
      if (qos == null)
         this.loginQos = new ConnectQos(glob);
      else
         this.loginQos = qos;

      loginRaw();
   }

   /**
    * Login to the server.
    * <p />
    * For internal use only.
    * The qos needs to be set up correctly if you wish a callback
    * @return The returned qos, containing the sessionId
    * @exception       XmlBlasterException if login fails
    */
   public ConnectReturnQos loginRaw() throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "loginRaw(" + loginName + ") ...");
      try {
         initRmiClient();
         if (passwd == null) {
            String tmp = authServer.connect(loginQos.toXml());
            this.connectReturnQos = new ConnectReturnQos(glob, tmp);
            this.sessionId = this.connectReturnQos.getSecretSessionId();
         }
         else {
            log.error(ME, "NOT IMPLEMENTED");
            //this.sessionId = authServer.login(loginName, passwd, loginQos.toXml());
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "NOT IMPLEMENTED");
         }
         if (log.TRACE) log.trace(ME, "Success, login for " + loginName);
         if (log.DUMP) log.dump(ME, loginQos.toXml());
         return this.connectReturnQos;
      } catch(RemoteException e) {
         if (log.TRACE) log.trace(ME, "Login failed for " + loginName);
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "Login failed for " + loginName);
      }
   }


   /**
    * Access the login name.
    * @return your login name or null if you are not logged in
    */
   public String getLoginName()
   {
      return loginName;
   }


   /**
    * Logout from the server.
    * <p />
    * The callback server is removed as well, releasing all RMI threads.
    * Note that this kills the server ping thread as well (if in fail save mode)
    * @return true successfully logged out
    *         false failure on gout
    */
   public boolean disconnect(DisconnectQos qos)
   {
      if (log.CALL) log.call(ME, "logout() ...");

      try {
         if (authServer != null) {
            authServer.disconnect(sessionId, (qos==null)?"":qos.toXml());
         }
         shutdown();
         resetConnection();
         return true;
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
      } catch(RemoteException e) {
         log.warn(ME, e.toString());
         e.printStackTrace();
      }

      shutdown();
      resetConnection();
      return false;
   }


   /**
    * Shut down.
    * Is called by logout()
    */
   public boolean shutdown()
   {
      return true;
   }


   /**
    * @return true if you are logged in
    */
   public boolean isLoggedIn()
   {
      return blasterServer != null;
   }


   /**
    * Enforced by I_XmlBlasterConnection interface (fail save mode).
    * see explanations of publish() method.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public final String subscribe(String xmlKey, String qos) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "subscribe() ...");
      try {
         return getXmlBlaster().subscribe(sessionId, xmlKey, qos);
      } catch(XmlBlasterException e) {
         throw e;
      } catch(Exception e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "subscribe", e);
      }
   }


   /**
    * Enforced by I_XmlBlasterConnection interface (fail save mode)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public final String[] unSubscribe(String xmlKey, String qos) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "unSubscribe() ...");
      try {
         return getXmlBlaster().unSubscribe(sessionId, xmlKey, qos);
      } catch(XmlBlasterException e) {
         throw e;
      } catch(Exception e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "unSubscribe", e);
      }
   }


   /**
    * Publish fault-tolerant the given message.
    * <p />
    * This is a wrapper around the raw RMI publish() method
    * If the server disappears you get an exception.
    * This call will not block.
    * <p />
    * Enforced by I_XmlBlasterConnection interface (fail save mode)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public final String publish(MsgUnitRaw msgUnit) throws XmlBlasterException
   {
      if (log.TRACE) log.trace(ME, "Publishing ...");
      try {
         return getXmlBlaster().publish(sessionId, msgUnit);
      } catch(XmlBlasterException e) {
         if (log.TRACE) log.trace(ME, "XmlBlasterException: " + e.getMessage());
         throw e;
      } catch(Exception e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "publish", e);
      }
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] publishArr(MsgUnitRaw [] msgUnitArr) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "publishArr() ...");
      try {
         return getXmlBlaster().publishArr(sessionId, msgUnitArr);
      } catch(XmlBlasterException e) {
         if (log.TRACE) log.trace(ME, "XmlBlasterException: " + e.getMessage());
         throw e;
      } catch(Exception e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "publishArr", e);
      }
   }

   /**
    * RMI does not support oneway messages. 
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public void publishOneway(MsgUnitRaw [] msgUnitArr) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "publishOneway(), RMI does not support oneway, we switch to publishArr() ...");
      publishArr(msgUnitArr);
   }

   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public final String[] erase(String xmlKey, String qos) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "erase() ...");
      try {
         return getXmlBlaster().erase(sessionId, xmlKey, qos);
      } catch(XmlBlasterException e) {
         throw e;
      } catch(Exception e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "erase", e);
      }
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public final MsgUnitRaw[] get(String xmlKey, String qos) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "get() ...");
      try {
         return getXmlBlaster().get(sessionId, xmlKey, qos);
      } catch(XmlBlasterException e) {
         throw e;
      } catch(Exception e) {
         e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "get", e);
      }
   }


   /**
    * Check server.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String ping(String str) throws XmlBlasterException {
      try {
         return getXmlBlaster().ping(str);
      } catch(Exception e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "ping", e);
      }
   }


   /**
    * Command line usage.
    * <p />
    * These variables may be set in xmlBlaster.properties as well.
    * Don't use the "-" prefix there.
    */
   public static String usage()
   {
      String text = "\n";
      text += "RmiConnection 'RMI' options:\n";
      text += "   -rmi.registryPort   Specify a port number where rmiregistry of the xmlBlaster server listens.\n";
      text += "                       Default is port "+DEFAULT_REGISTRY_PORT+", the port 0 switches this feature off.\n";
      text += "   -rmi.hostname       Specify a hostname where rmiregistry of the xmlBlaster server runs.\n";
      text += "                       Default is the localhost.\n";
      text += "   -rmi.registryPortCB Specify a port number where rmiregistry for the callback server listens.\n";
      text += "                       Default is port "+DEFAULT_REGISTRY_PORT+", the port 0 switches this feature off.\n";
      text += "   -rmi.hostnameCB     Specify a hostname where rmiregistry for the callback server runs.\n";
      text += "                       Default is the localhost (useful for multi homed hosts).\n";
      text += "\n";
      return text;
   }
} // class RmiConnection

