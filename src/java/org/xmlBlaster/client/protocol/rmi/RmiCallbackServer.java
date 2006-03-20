/*------------------------------------------------------------------------------
Name:      RmiCallbackServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.rmi;


import org.xmlBlaster.protocol.rmi.I_XmlBlasterCallback;
import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.protocol.rmi.RmiUrl;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.MsgUnitRaw;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.Naming;
import java.rmi.AlreadyBoundException;


/**
 * Example for a RMI callback implementation.
 * <p />
 * You can use this default callback handling with your clients,
 * but if you need other handling of callbacks, take a copy
 * of this Callback implementation and add your own code.
 * <p />
 * A rmi-registry server is created automatically, if there is running already one, that is used.<br />
 * You can specify another port or host to create/use a rmi-registry server:
 * <pre>
 *     -dispatch/callback/plugin/rmi/registryPort Specify a port number where rmiregistry listens.
 *                         Default is port 1099, the port 0 switches this feature off.
 *     -dispatch/callback/plugin/rmi/hostname     Specify a hostname where rmiregistry runs.
 *                         Default is the localhost.
 * </pre>
 * <p />
 * Note: The security manager must be initialized properly before you use an instance of this class.<br />
 * RmiConnection does it in its constructor if you use this class, or you could use<br />
 *     XmlBlasterSecurityManager.createSecurityManager();<br />
 * to do so yourself.
 * <p />
 * Invoke options: <br />
 * <pre>
 *   java -Djava.rmi.server.codebase=file:///${XMLBLASTER_HOME}/classes/  \
 *        -Djava.security.policy=${XMLBLASTER_HOME}/config/xmlBlaster.policy \
 *        -Djava.rmi.server.hostname=hostname.domainname
 *        MyApp -dispatch/connection/plugin/rmi/registryPort 2079
 * </pre>
 */
public class RmiCallbackServer extends UnicastRemoteObject implements I_XmlBlasterCallback, I_CallbackServer
{
   private String ME;
   private Global glob;
   private static Logger log = Logger.getLogger(RmiCallbackServer.class.getName());
   private I_CallbackExtended client;
   private String loginName;
   /** The name for the RMI registry */
   private String callbackRmiServerBindName = null;
   /** XmlBlaster RMI registry listen port is 1099, to register callback server */
   public static final int DEFAULT_REGISTRY_PORT = 1099; // org.xmlBlaster.protocol.rmi.RmiDriver.DEFAULT_REGISTRY_PORT;
   private RmiUrl rmiUrl;
   private CallbackAddress callbackAddress;


   public RmiCallbackServer() throws java.rmi.RemoteException {
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getCbProtocol();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return "1.0";
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
   }

   /**
    * Construct the callback server. 
    */
   public void initialize(Global glob, String name, CallbackAddress callbackAddress,
                          I_CallbackExtended client) throws XmlBlasterException
   {
      this.ME = "RmiCallbackServer-" + name;
      this.glob = glob;

      this.client = client;
      this.loginName = name;
      this.callbackAddress = callbackAddress;
      createCallbackServer(this);
      if (log.isLoggable(Level.FINE)) log.fine("Success, created RMI callback server for " + loginName);
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
    * Publish the RMI xmlBlaster server to rmi registry.
    * <p />
    * The bind name is typically "rmi://localhost:1099/xmlBlaster"
    * @exception XmlBlasterException
    *                    RMI registry error handling
    */
   private void bindToRegistry(I_XmlBlasterCallback callbackRmiServer) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("bindToRegistry() ...");

      // -dispatch/callback/plugin/rmi/registryPort 1099
      this.rmiUrl = new RmiUrl(glob, this.callbackAddress);

      try {
         if (this.rmiUrl.getRegistryPort() > 0) {
            // Start a 'rmiregistry' if desired
            try {
               java.rmi.registry.LocateRegistry.createRegistry(this.rmiUrl.getRegistryPort());
               log.info("Started RMI registry on port " + this.rmiUrl.getRegistryPort());
            } catch (java.rmi.server.ExportException e) {
               // Try to bind to an already running registry:
               try {
                  java.rmi.registry.LocateRegistry.getRegistry(this.rmiUrl.getHostname(), this.rmiUrl.getRegistryPort());
                  log.info("Another rmiregistry is running on port " + DEFAULT_REGISTRY_PORT +
                               " we will use this one. You could change the port with e.g. '-dispatch/callback/plugin/rmi/registryPort 1122' to run your own rmiregistry.");
               }
               catch (RemoteException e2) {
                  String text = "Port " + DEFAULT_REGISTRY_PORT + " is already in use, but does not seem to be a rmiregistry. Please can change the port with e.g. -dispatch/callback/plugin/rmi/registryPort 1122 : " + e.toString();
                  log.severe(text);
                  throw new XmlBlasterException(ME, text);
               }
            }
         }

         // e.g. "rmi://localhost:1099/I_XmlBlasterCallback/Tim"
         callbackRmiServerBindName = this.rmiUrl.getUrl() + "I_XmlBlasterCallback/" + loginName;

         // Publish RMI based xmlBlaster server ...
         try {
            Naming.bind(callbackRmiServerBindName, callbackRmiServer);
            log.info("Bound RMI callback server to registry with name '" + callbackRmiServerBindName + "'");
            this.callbackAddress.setRawAddress(callbackRmiServerBindName);
         } catch (AlreadyBoundException e) {
            try {
               Naming.rebind(callbackRmiServerBindName, callbackRmiServer);
               log.warning("Removed another entry while binding authentication RMI callback server to registry with name '" + callbackRmiServerBindName + "'");
            } catch (Exception e2) {
               log.severe("RMI registry of '" + callbackRmiServerBindName + "' failed: " + e2.toString());
               throw new XmlBlasterException(ME+".RmiRegistryFailed", "RMI registry of '" + callbackRmiServerBindName + "' failed: " + e2.toString());
            }
         }
      } catch (java.net.MalformedURLException e) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Could not initialize RMI registry", e);
      } catch (RemoteException e) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Could not initialize RMI registry", e);
      }
   }

   /**
    * @return The protocol name "RMI"
    */
   public final String getCbProtocol()
   {
      return "RMI";
   }

   /**
    * @return The RMI address of this server, which can be used for the connectQos
    */
   public String getCbAddress() throws XmlBlasterException
   {
      return callbackRmiServerBindName;
   }

   /**
    * Shutdown the callback server.
    */
   public void shutdown()
   {
      try {
         if (callbackRmiServerBindName != null)
            Naming.unbind(callbackRmiServerBindName);
            // force shutdown, even if we still have calls in progress:
            java.rmi.server.UnicastRemoteObject.unexportObject(this, true);
      } catch (Exception e) {
         ;
      }
      log.info("The RMI callback server is shutdown.");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * informing the client in an asynchronous mode about new messages.
    * <p />
    * It implements the interface I_XmlBlasterCallback
    * <p />
    * The call is converted to the native MsgUnitRaw, and the other update()
    * method of this class is invoked.
    *
    * @param msgUnitArr Contains a MsgUnitRaw structs (your message) for CORBA
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] update(String cbSessionId, MsgUnitRaw[] msgUnitArr) throws RemoteException, XmlBlasterException
   {
      if (msgUnitArr == null) throw new XmlBlasterException(ME, "Received update of null message");
      if (log.isLoggable(Level.FINER)) log.finer("Entering update(" + cbSessionId + ") of " + msgUnitArr.length + " messages");

      return client.update(cbSessionId, msgUnitArr);
   }

   /**
    * The oneway variant for better performance. 
    */
   public void updateOneway(String cbSessionId, org.xmlBlaster.util.MsgUnitRaw[] msgUnitArr) throws RemoteException
   {
      if (msgUnitArr == null) return;
      if (log.isLoggable(Level.FINER)) log.finer("Entering updateOneway(" + cbSessionId + ") of " + msgUnitArr.length + " messages");
      try {
         client.updateOneway(cbSessionId, msgUnitArr);
      }
      catch (Throwable e) {
         log.severe("Caught exception which can't be delivered to xmlBlaster because of 'oneway' mode: " + e.toString());
      }
   }

   /**
    * Ping to check if the xmlBlaster server is alive. 
    * @see org.xmlBlaster.protocol.I_CallbackDriver#ping(String)
    */
   public String ping(String qos) throws RemoteException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering ping("+qos+") ...");
      return Constants.RET_OK;
   }
} // class RmiCallbackServer

