/*------------------------------------------------------------------------------
Name:      RmiConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: RmiConnection.java,v 1.2 2000/10/21 20:54:00 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.rmi;


import org.xmlBlaster.protocol.rmi.I_AuthServer;
import org.xmlBlaster.protocol.rmi.I_XmlBlaster;
import org.xmlBlaster.protocol.rmi.I_XmlBlasterCallback;

import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;
import org.xmlBlaster.client.protocol.ConnectionException;
import org.xmlBlaster.client.LoginQosWrapper;
import org.xmlBlaster.client.protocol.I_CallbackExtended;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
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
 * @version $Revision: 1.2 $
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
   protected LoginQosWrapper loginQos = null;

   /** The RMI interface which we implement to allow callbacks */
   private I_XmlBlasterCallback callback = null;

   /** The name for the RMI registry */
   private String callbackRmiServerBindName = null;

   /** XmlBlaster RMI registry listen port is 1099, to access for bootstrapping */
   public static final int DEFAULT_REGISTRY_PORT = 1099; // org.xmlBlaster.protocol.rmi.RmiDriver.DEFAULT_REGISTRY_PORT;

   /**
    * RMI client access to xmlBlaster for <strong>normal client applications</strong>.
    * <p />
    * @param arg  parameters given on command line
    */
   public RmiConnection(String[] args) throws XmlBlasterException
   {
      createSecurityManager();
   }


   /**
    * RMI client access to xmlBlaster for <strong>applets</strong>.
    * <p />
    * @param ap  Applet handle
    */
   public RmiConnection(Applet ap) throws XmlBlasterException
   {
      createSecurityManager();
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
      hostname = XmlBlasterProperty.get("rmi.Hostname", hostname);

      // default xmlBlaster RMI publishing port is 1099
      int registryPort = XmlBlasterProperty.get("rmi.RegistryPort",
                         org.xmlBlaster.protocol.rmi.RmiDriver.DEFAULT_REGISTRY_PORT);
      String prefix = "rmi://" + hostname + ":" + registryPort + "/";


      String authServerUrl = prefix + "I_AuthServer";
      String addr = XmlBlasterProperty.get("rmi.AuthServer.url", authServerUrl);
      Remote rem = lookup(addr);
      if (rem instanceof org.xmlBlaster.protocol.rmi.I_AuthServer) {
         authServer = (I_AuthServer)rem;
         Log.info(ME, "Accessed xmlBlaster authentication reference with '" + addr + "'");
      }
      else {
         throw new XmlBlasterException("InvalidRmiCallback", "No to '" + addr + "' possible, class needs to implement interface I_AuthServer.");
      }


      String xmlBlasterUrl = prefix + "I_XmlBlaster";
      addr = XmlBlasterProperty.get("rmi.XmlBlaster.url", xmlBlasterUrl);
      rem = lookup(addr);
      if (rem instanceof org.xmlBlaster.protocol.rmi.I_XmlBlaster) {
         blasterServer = (I_XmlBlaster)rem;
         Log.info(ME, "Accessed xmlBlaster server reference with '" + addr + "'");
      }
      else {
         throw new XmlBlasterException("InvalidRmiCallback", "No to '" + addr + "' possible, class needs to implement interface I_XmlBlaster.");
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
         throw new XmlBlasterException("CallbackHandleInvalid", "Can't access address ='" + addr + "', no rmi registry running");
      }
      catch (NotBoundException e) {
         Log.error(ME, "The given address ='" + addr + "' is not bound to rmi registry: " + e.toString());
         throw new XmlBlasterException("CallbackHandleInvalid", "The given address '" + addr + "' is not bound to rmi registry: " + e.toString());
      }
      catch (MalformedURLException e) {
         Log.error(ME, "The given address ='" + addr + "' is invalid: " + e.toString());
         throw new XmlBlasterException("CallbackHandleInvalid", "The given address '" + addr + "' is invalid: " + e.toString());
      }
      catch (Throwable e) {
         Log.error(ME, "The given address ='" + addr + "' is invalid : " + e.toString());
         throw new XmlBlasterException("CallbackHandleInvalid", "The given address '" + addr + "' is invalid : " + e.toString());
      }
   }


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
   private I_XmlBlaster getXmlBlaster() throws Exception
   {
      if (blasterServer == null) {
         throw new Exception("The xmlBlaster handle is null, no connection available");
      }
      return blasterServer;
   }


   /**
    * Login to the server, specify your own callback in the qos if desired.
    * <p />
    * Note that no asynchronous subscribe() method is available if you don't
    * specify a callback in 'qos'.
    * @param loginName The login name for xmlBlaster
    * @param passwd    The login password for xmlBlaster
    * @param qos       The Quality of Service for this client, you may pass 'null' for default behavior
    * @exception       XmlBlasterException if login fails
    */
   public void login(String loginName, String passwd, LoginQosWrapper qos) throws XmlBlasterException, ConnectionException
   {
      login(loginName, passwd, qos, null);
   }


   /**
    * Login to the server, using the default BlasterCallback implementation.
    * <p />
    * If you do multiple logins with the same I_Callback implementation, the loginName
    * which is delivered with the update() method may be used to dispatch the message
    * to the correct client.
    * <p />
    * WARNING: <strong>The qos gets added a <pre>&lt;callback type='IOR'></pre> tag,
    *          so don't use it for a second login, otherwise a second callback is inserted !</strong>
    *
    * @param loginName The login name for xmlBlaster
    * @param passwd    The login password for xmlBlaster
    * @param qos       The Quality of Service for this client (the callback tag will be added automatically if client!=null)
    * @param client    Your implementation of I_CallbackExtended, or null if you don't want any updates.
    * @exception       XmlBlasterException if login fails
    */
   public void login(String loginName, String passwd, LoginQosWrapper qos, I_CallbackExtended client) throws XmlBlasterException, ConnectionException
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
         this.loginQos = new LoginQosWrapper();
      else
         this.loginQos = qos;

      if (client != null) {
         try {
            this.callback = new RmiCallbackServer(loginName, client);
         }
         catch (RemoteException e) {
            Log.error(ME, "Creation of RmiCallbackServer failed: " + e.toString());
            throw new XmlBlasterException("RmiDriverFailed", e.toString());
         }
         createCallbackServer(this.callback);

         CallbackAddress addr = new CallbackAddress("RMI");
         addr.setAddress(callbackRmiServerBindName);
         loginQos.addCallbackAddress(addr);
         Log.info(ME, "Success, exported RMI callback server for " + loginName);
      }

      loginRaw();
   }


   /**
    * Login to the server.
    * <p />
    * For internal use only.
    * The qos needs to be set up correctly if you wish a callback
    * @exception       XmlBlasterException if login fails
    */
   public void loginRaw() throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "loginRaw(" + loginName + ") ...");
      try {
         initRmiClient();
         sessionId = authServer.login(loginName, passwd, loginQos.toXml());
         if (Log.TRACE) Log.trace(ME, "Success, login for " + loginName);
         if (Log.DUMP) Log.dump(ME, loginQos.toXml());
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
    * The callback server is removed as well, releasing all RMI threads.
    * Note that this kills the server ping thread as well (if in fail save mode)
    * @return true successfully logged out
    *         false failure on logout
    */
   public boolean logout()
   {
      if (Log.CALL) Log.call(ME, "logout() ...");

      try {
         if (authServer != null) authServer.logout(sessionId);
         shutdownCallbackServer();
         init();
         return true;
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: [" + e.id + "]" + " " + e.reason);
      } catch(RemoteException e) {
         Log.warn(ME, e.toString());
         e.printStackTrace();
      }

      shutdownCallbackServer();
      init();
      return false;
   }


   /**
    * @return true if you are logged in
    */
   public boolean isLoggedIn()
   {
      return blasterServer != null;
   }


   /**
    * Building a Callback server, using the tie approach.
    *
    * @param the BlasterCallback server
    * @exception XmlBlasterException if the BlasterCallback server can't be created
    *            id="CallbackCreationError"
    */
   public void createCallbackServer(I_XmlBlasterCallback callbackRmiServer) throws XmlBlasterException
   {
      bindToRegistry(callbackRmiServer);
   }
   

   /**
    * Create and install a security manager, using xmlBlaster.policy
    * <p />
    * Note the similar method in org.xmlBlaster.protocol.rmi.RmiDriver;
    */
   private void createSecurityManager() throws XmlBlasterException
   {
      if (System.getSecurityManager() == null) {
         if (System.getProperty("java.security.policy") != null) {
            // use the given policy file (java -Djava.security.policy=...)
            Log.info(ME, "Setting security policy from file " + System.getProperty("java.security.policy"));
         }
         else {
            // try to find the policy file in the CLASSPATH
            ClassLoader loader = RmiConnection.class.getClassLoader();
            if (loader != null) {
               java.net.URL serverPolicyURL = loader.getResource("xmlBlaster.policy");
               if (serverPolicyURL != null ) {
                  String serverPolicy = serverPolicyURL.getFile();
                  System.setProperty("java.security.policy", serverPolicy);
                  Log.info(ME, "Setting security policy " + serverPolicy + ", found it in your CLASSPATH.");
               }
            }
         }

         // Check if there was any policy file found
         if (System.getProperty("java.security.policy") == null) {
            String text = "java.security.policy is not set, please include config/xmlBlaster.policy into your CLASSPATH or pass the file on startup like 'java -Djava.security.policy=<path>xmlBlaster.policy'...";
            throw new XmlBlasterException("RmiDriverFailed", text);
         }

         System.setSecurityManager(new RMISecurityManager());
         if (Log.TRACE) Log.trace(ME, "Started RMISecurityManager");
      }
      else
         Log.warn(ME, "Another security manager is running already, no config/xmlBlaster.policy bound");
   }


   /**
    * Publish the RMI xmlBlaster server to rmi registry.
    * <p />
    * The bind name is typically "rmi://localhost:1099/xmlBlaster"
    * @exception XmlBlasterException
    *                    RMI registry error handling
    */
   private void bindToRegistry(I_XmlBlasterCallback callbackRmiServer) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "bindToRegistry() ...");

      // Use the xmlBlaster-server rmiRegistry as a fallback:
      int registryPort = XmlBlasterProperty.get("rmi.RegistryPort", 
                                                DEFAULT_REGISTRY_PORT); // default xmlBlaster RMI publishing port is 1099
      // Use the given callback port if specified :
      registryPort = XmlBlasterProperty.get("rmi.RegistryPortCB", registryPort); 

      String hostname;
      try  {
         java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
         hostname = addr.getHostName();
      } catch (Exception e) {
         Log.info(ME, "Can't determin your hostname");
         // Use the xmlBlaster-server rmiRegistry as a fallback:
         hostname = XmlBlasterProperty.get("rmi.Hostname", "localhost");
      }
      // Use the given callback hostname if specified :
      hostname = XmlBlasterProperty.get("rmi.HostnameCB", hostname);

      try {
         if (registryPort > 0) {
            // Start a 'rmiregistry' if desired
            try {
               java.rmi.registry.LocateRegistry.createRegistry(registryPort);
               Log.info(ME, "Started RMI registry on port " + registryPort);
            } catch (java.rmi.server.ExportException e) {
               // Try to bind to an already running registry:
               try {
                  java.rmi.registry.LocateRegistry.getRegistry(hostname, registryPort);
                  Log.info(ME, "Another rmiregistry is running on port " + DEFAULT_REGISTRY_PORT +
                               " we will use this one. You could change the port with e.g. -rmi.RegistryPortCB=1122 to run your own rmiregistry.");
               }
               catch (RemoteException e2) {
                  String text = "Port " + DEFAULT_REGISTRY_PORT + " is already in use, but does not seem to be a rmiregistry. Please can change the port with e.g. -rmi.RegistryPortCB=1122 : " + e.toString();
                  Log.error(ME, text);
                  throw new XmlBlasterException(ME, text);
               }
            }
         }

         // e.g. "rmi://localhost:1099/I_XmlBlasterCallback"
         callbackRmiServerBindName = "rmi://" + hostname + ":" + registryPort + "/I_XmlBlasterCallback";

         // Publish RMI based xmlBlaster server ...
         try {
            Naming.rebind(callbackRmiServerBindName, callbackRmiServer);
            Log.info(ME, "Bound RMI callback server to registry with name '" + callbackRmiServerBindName + "'");
         } catch (Exception e) {
            Log.error(ME+".RmiRegistryFailed", "RMI registry of '" + callbackRmiServerBindName + "' failed: " + e.toString());
            throw new XmlBlasterException(ME+".RmiRegistryFailed", "RMI registry of '" + callbackRmiServerBindName + "' failed: " + e.toString());
         }
      } catch (Exception e) {
         e.printStackTrace();
         throw new XmlBlasterException("InitRmiFailed", "Could not initialize RMI registry: " + e.toString());
      }
   }


   /**
    * Shutdown the callback server.
    */
   public void shutdownCallbackServer()
   {
      try {
         if (callbackRmiServerBindName != null)
            Naming.unbind(callbackRmiServerBindName);
      } catch (Exception e) {
         ;
      }
      Log.info(ME, "The RMI callback server is shutdown.");
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
         throw new XmlBlasterException(e.id, e.reason); // transform Rmi exception to native exception
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
         throw new XmlBlasterException(e.id, e.reason); // transform Rmi exception to native exception
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
         throw new XmlBlasterException(e.id, e.reason); // transform Rmi exception to native exception
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
         throw new XmlBlasterException(e.id, e.reason); // transform Rmi exception to native exception
      } catch(Exception e) {
         throw new ConnectionException(ME+".InvokeError", e.toString());
      }
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
         throw new XmlBlasterException(e.id, e.reason); // transform Rmi exception to native exception
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
         throw new XmlBlasterException(e.id, e.reason); // transform Rmi exception to native exception
      } catch(Exception e) {
         e.printStackTrace();
         throw new ConnectionException(ME+".InvokeError", e.toString());
      }
   }


   /**
    * Check server.
    * @see xmlBlaster.idl
    */
   public void ping() throws ConnectionException
   {
      try {
         getXmlBlaster().ping();
         return;
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
      text += "RmiConnection options:\n";
      text += "   -rmi.RegistryPortCB Specify a port number where rmiregistry listens.\n";
      text += "                       Default is port "+DEFAULT_REGISTRY_PORT+", the port 0 switches this feature off.\n";
      text += "   -rmi.HostnameCB     Specify a hostname where rmiregistry runs.\n";
      text += "                       Default is the localhost.\n";
      text += "\n";
      return text;
   }
} // class RmiConnection


/**
 * Example for a callback implementation.
 * <p />
 * You can use this default callback handling with your clients,
 * but if you need other handling of callbacks, take a copy
 * of this Callback implementation and add your own code.
 * <p />
 */
class RmiCallbackServer extends java.rmi.server.UnicastRemoteObject implements I_XmlBlasterCallback
{
   private final String ME;
   private final I_CallbackExtended boss;
   private final String loginName;

   /**
    * Construct a persistently named object.
    * @param client    Your implementation of I_CallbackExtended, or null if you don't want any updates.
    */
   public RmiCallbackServer(String name, I_CallbackExtended boss) throws RemoteException
   {
      this.ME = "RmiCallbackServer-" + name;
      this.boss = boss;
      this.loginName = name;
      if (Log.CALL) Log.call(ME, "Entering constructort");
   }


   /**
    * This is the callback method invoked from xmlBlaster
    * informing the client in an asynchronous mode about new messages.
    * <p />
    * It implements the interface I_XmlBlasterCallback
    * <p />
    * The call is converted to the native MessageUnit, and the other update()
    * method of this class is invoked.
    *
    * @param msgUnitArr Contains a MessageUnit structs (your message) for CORBA
    * @see xmlBlaster.idl
    */
   public void update(org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr) throws RemoteException, XmlBlasterException
   {
      if (msgUnitArr == null) return;
      boss.update(loginName, msgUnitArr);
   }

} // class RmiCallbackServer

