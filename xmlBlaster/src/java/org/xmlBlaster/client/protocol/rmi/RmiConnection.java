/*------------------------------------------------------------------------------
Name:      RmiConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: RmiConnection.java,v 1.19 2002/04/15 13:10:32 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.rmi;


import org.xmlBlaster.protocol.rmi.I_AuthServer;
import org.xmlBlaster.protocol.rmi.I_XmlBlaster;

import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;
import org.xmlBlaster.client.protocol.ConnectionException;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterSecurityManager;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;

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
 * @version $Revision: 1.19 $
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public class RmiConnection implements I_XmlBlasterConnection
{
   private String ME = "RmiConnection";

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
      XmlBlasterSecurityManager.createSecurityManager();
   }


   /**
    * RMI client access to xmlBlaster for <strong>applets</strong>.
    * <p />
    * @param ap  Applet handle
    */
   public RmiConnection(Applet ap) throws XmlBlasterException
   {
      XmlBlasterSecurityManager.createSecurityManager();
   }


   /**
    * Connect to RMI server.
    */
   private void initRmiClient() throws XmlBlasterException
   {
      String hostname;
      try  {
         java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
         hostname = addr.getHostName();
      } catch (Exception e) {
         Log.warn(ME, "Can't determin your hostname");
         hostname = "localhost";
      }
      hostname = XmlBlasterProperty.get("rmi.hostname", hostname);

      // default xmlBlaster RMI publishing port is 1099
      int registryPort = XmlBlasterProperty.get("rmi.registryPort", DEFAULT_REGISTRY_PORT);
      String prefix = "rmi://" + hostname + ":" + registryPort + "/";


      String authServerUrl = prefix + "I_AuthServer";
      String addr = XmlBlasterProperty.get("rmi.AuthServer.url", authServerUrl);
      Remote rem = lookup(addr);
      if (rem instanceof org.xmlBlaster.protocol.rmi.I_AuthServer) {
         authServer = (I_AuthServer)rem;
         Log.info(ME, "Accessed xmlBlaster authentication reference with '" + addr + "'");
      }
      else {
         throw new XmlBlasterException("InvalidRmi", "No connect to '" + addr + "' possible, class needs to implement interface I_AuthServer.");
      }


      String xmlBlasterUrl = prefix + "I_XmlBlaster";
      addr = XmlBlasterProperty.get("rmi.XmlBlaster.url", xmlBlasterUrl);
      rem = lookup(addr);
      if (rem instanceof org.xmlBlaster.protocol.rmi.I_XmlBlaster) {
         blasterServer = (I_XmlBlaster)rem;
         Log.info(ME, "Accessed xmlBlaster server reference with '" + addr + "'");
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
         Log.error(ME, "Can't access address ='" + addr + "', no rmi registry running");
         throw new XmlBlasterException(ME, "Can't access address ='" + addr + "', no rmi registry running");
      }
      catch (NotBoundException e) {
         Log.error(ME, "The given address ='" + addr + "' is not bound to rmi registry: " + e.toString());
         throw new XmlBlasterException(ME, "The given address '" + addr + "' is not bound to rmi registry: " + e.toString());
      }
      catch (MalformedURLException e) {
         Log.error(ME, "The given address ='" + addr + "' is invalid: " + e.toString());
         throw new XmlBlasterException(ME, "The given address '" + addr + "' is invalid: " + e.toString());
      }
      catch (Throwable e) {
         Log.error(ME, "The given address ='" + addr + "' is invalid : " + e.toString());
         throw new XmlBlasterException(ME, "The given address '" + addr + "' is invalid : " + e.toString());
      }
   }

   /**
    * Reset
    */
   public void init()
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
   private I_XmlBlaster getXmlBlaster() throws ConnectionException
   {
      if (blasterServer == null) {
         if (Log.TRACE) Log.trace(ME, "No RMI connection available.");
         throw new ConnectionException(ME+".init", "The RMI xmlBlaster handle is null, no connection available");
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
   public ConnectReturnQos connect(ConnectQos qos) throws XmlBlasterException, ConnectionException
   {
      if (qos == null)
         throw new XmlBlasterException(ME+".connect()", "Please specify a valid QoS");

      this.loginQos = qos;
      this.loginName = qos.getUserId();
      this.passwd = null;

      this.ME = "RmiConnection-" + loginName;
      if (Log.CALL) Log.call(ME, "connect() ...");
      if (blasterServer != null) {
         Log.warn(ME, "You are already logged in.");
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
   public void login(String loginName, String passwd, ConnectQos qos) throws XmlBlasterException, ConnectionException
   {
      this.ME = "RmiConnection-" + loginName;
      if (Log.CALL) Log.call(ME, "login() ...");
      if (blasterServer != null) {
         Log.warn(ME, "You are already logged in.");
         return;
      }

      this.loginName = loginName;
      this.passwd = passwd;
      if (qos == null)
         this.loginQos = new ConnectQos();
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
   public ConnectReturnQos loginRaw() throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "loginRaw(" + loginName + ") ...");
      try {
         initRmiClient();
         if (passwd == null) {
            String tmp = authServer.connect(loginQos.toXml());
            this.connectReturnQos = new ConnectReturnQos(tmp);
            this.sessionId = this.connectReturnQos.getSessionId();
         }
         else {
            this.sessionId = authServer.login(loginName, passwd, loginQos.toXml());
         }
         if (Log.TRACE) Log.trace(ME, "Success, login for " + loginName);
         if (Log.DUMP) Log.dump(ME, loginQos.toXml());
         return this.connectReturnQos;
      } catch(RemoteException e) {
         if (Log.TRACE) Log.trace(ME, "Login failed for " + loginName);
         throw new ConnectionException("LogingFailed", e.toString());
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
   public boolean logout()
   {
      if (Log.CALL) Log.call(ME, "logout() ...");

      try {
         if (authServer != null) {
            if(passwd==null) {
               authServer.disconnect(sessionId, "");
            }
            else {
               authServer.logout(sessionId);
            }
         }
         shutdown();
         init();
         return true;
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: [" + e.id + "]" + " " + e.reason);
      } catch(RemoteException e) {
         Log.warn(ME, e.toString());
         e.printStackTrace();
      }

      shutdown();
      init();
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
    * @see xmlBlaster.idl
    */
   public final String subscribe(String xmlKey, String qos) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "subscribe() ...");
      try {
         return getXmlBlaster().subscribe(sessionId, xmlKey, qos);
      } catch(XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason);
      } catch(Exception e) {
         throw new ConnectionException(ME+".InvokeError", e.toString());
      }
   }


   /**
    * Enforced by I_XmlBlasterConnection interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public final void unSubscribe(String xmlKey, String qos) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "unSubscribe() ...");
      try {
         getXmlBlaster().unSubscribe(sessionId, xmlKey, qos);
      } catch(XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason);
      } catch(Exception e) {
         throw new ConnectionException(ME+".InvokeError", e.toString());
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
    * @see xmlBlaster.idl
    */
   public final String publish(MessageUnit msgUnit) throws XmlBlasterException, ConnectionException
   {
      if (Log.TRACE) Log.trace(ME, "Publishing ...");
      try {
         return getXmlBlaster().publish(sessionId, msgUnit);
      } catch(XmlBlasterException e) {
         if (Log.TRACE) Log.trace(ME, "XmlBlasterException: " + e.reason);
         throw new XmlBlasterException(e.id, e.reason);
      } catch(Exception e) {
         throw new ConnectionException(ME+".InvokeError", e.toString());
      }
   }


   /**
    * @see xmlBlaster.idl
    */
   public String[] publishArr(MessageUnit [] msgUnitArr) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "publishArr() ...");
      try {
         return getXmlBlaster().publishArr(sessionId, msgUnitArr);
      } catch(XmlBlasterException e) {
         if (Log.TRACE) Log.trace(ME, "XmlBlasterException: " + e.reason);
         throw new XmlBlasterException(e.id, e.reason);
      } catch(Exception e) {
         throw new ConnectionException(ME+".InvokeError", e.toString());
      }
   }

   /**
    * RMI does not support oneway messages. 
    * @see xmlBlaster.idl
    */
   public void publishOneway(MessageUnit [] msgUnitArr) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "publishOneway(), RMI does not support oneway, we switch to publishArr() ...");
      publishArr(msgUnitArr);
   }

   /**
    * @see xmlBlaster.idl
    */
   public final String[] erase(String xmlKey, String qos) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "erase() ...");
      try {
         return getXmlBlaster().erase(sessionId, xmlKey, qos);
      } catch(XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason);
      } catch(Exception e) {
         throw new ConnectionException(ME+".InvokeError", e.toString());
      }
   }


   /**
    * @see xmlBlaster.idl
    */
   public final MessageUnit[] get(String xmlKey, String qos) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "get() ...");
      try {
         return getXmlBlaster().get(sessionId, xmlKey, qos);
      } catch(XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason);
      } catch(Exception e) {
         e.printStackTrace();
         throw new ConnectionException(ME+".InvokeError", e.toString());
      }
   }


   /**
    * Check server.
    * @see xmlBlaster.idl
    */
   public String ping(String str) throws ConnectionException
   {
      try {
         return getXmlBlaster().ping(str);
      } catch(Exception e) {
         throw new ConnectionException(ME+".InvokeError", e.toString());
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

