/*------------------------------------------------------------------------------
Name:      RmiCallbackServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: RmiCallbackServer.java,v 1.19 2002/12/18 12:34:45 ruff Exp $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.rmi;


import org.xmlBlaster.protocol.rmi.I_XmlBlasterCallback;
import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.CallbackAddress;
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
 *     -rmi.registryPortCB Specify a port number where rmiregistry listens.
 *                         Default is port 1099, the port 0 switches this feature off.
 *     -rmi.hostnameCB     Specify a hostname where rmiregistry runs.
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
 *        MyApp -rmi.registryPort 2079
 * </pre>
 */
public class RmiCallbackServer extends UnicastRemoteObject implements I_XmlBlasterCallback, I_CallbackServer
{
   private String ME;
   private Global glob;
   private LogChannel log;
   private I_CallbackExtended client;
   private String loginName;
   /** The name for the RMI registry */
   private String callbackRmiServerBindName = null;
   /** XmlBlaster RMI registry listen port is 1099, to register callback server */
   public static final int DEFAULT_REGISTRY_PORT = 1099; // org.xmlBlaster.protocol.rmi.RmiDriver.DEFAULT_REGISTRY_PORT;


   public RmiCallbackServer() throws java.rmi.RemoteException
   {
   }

   /**
    * Construct the callback server. 
    */
   public void initialize(Global glob, String name, I_CallbackExtended client) throws XmlBlasterException
   {
      this.ME = "RmiCallbackServer-" + name;
      this.glob = glob;
      this.log = glob.getLog("rmi");
      this.client = client;
      this.loginName = name;
      createCallbackServer(this);
      log.info(ME, "Success, created RMI callback server for " + loginName);
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
      if (log.CALL) log.call(ME, "bindToRegistry() ...");

      // TODO: Use ConnectQos to allow hardcoded hostname/socket

      // Use the xmlBlaster-server rmiRegistry as a fallback:
      int registryPort = glob.getProperty().get("rmi.registryPort",
                                                DEFAULT_REGISTRY_PORT); // default xmlBlaster RMI publishing port is 1099
      // Use the given callback port if specified :
      registryPort = glob.getProperty().get("rmi.registryPortCB", registryPort);

      String hostname = glob.getCbHostname("rmi.hostnameCB");

      try {
         if (registryPort > 0) {
            // Start a 'rmiregistry' if desired
            try {
               java.rmi.registry.LocateRegistry.createRegistry(registryPort);
               log.info(ME, "Started RMI registry on port " + registryPort);
            } catch (java.rmi.server.ExportException e) {
               // Try to bind to an already running registry:
               try {
                  java.rmi.registry.LocateRegistry.getRegistry(hostname, registryPort);
                  log.info(ME, "Another rmiregistry is running on port " + DEFAULT_REGISTRY_PORT +
                               " we will use this one. You could change the port with e.g. '-rmi.registryPortCB 1122' to run your own rmiregistry.");
               }
               catch (RemoteException e2) {
                  String text = "Port " + DEFAULT_REGISTRY_PORT + " is already in use, but does not seem to be a rmiregistry. Please can change the port with e.g. -rmi.registryPortCB=1122 : " + e.toString();
                  log.error(ME, text);
                  throw new XmlBlasterException(ME, text);
               }
            }
         }

         // e.g. "rmi://localhost:1099/I_XmlBlasterCallback/Tim"
         callbackRmiServerBindName = "rmi://" + hostname + ":" + registryPort + "/I_XmlBlasterCallback/" + loginName;

         // Publish RMI based xmlBlaster server ...
         try {
            Naming.bind(callbackRmiServerBindName, callbackRmiServer);
            log.info(ME, "Bound RMI callback server to registry with name '" + callbackRmiServerBindName + "'");
         } catch (AlreadyBoundException e) {
            try {
               Naming.rebind(callbackRmiServerBindName, callbackRmiServer);
               log.warn(ME, "Removed another entry while binding authentication RMI callback server to registry with name '" + callbackRmiServerBindName + "'");
            } catch (Exception e2) {
               log.error(ME+".RmiRegistryFailed", "RMI registry of '" + callbackRmiServerBindName + "' failed: " + e2.toString());
               throw new XmlBlasterException(ME+".RmiRegistryFailed", "RMI registry of '" + callbackRmiServerBindName + "' failed: " + e2.toString());
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
         throw new XmlBlasterException("InitRmiFailed", "Could not initialize RMI registry: " + e.toString());
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
   public boolean shutdownCb()
   {
      try {
         if (callbackRmiServerBindName != null)
            Naming.unbind(callbackRmiServerBindName);
            // force shutdown, even if we still have calls in progress:
            java.rmi.server.UnicastRemoteObject.unexportObject(this, true);
      } catch (Exception e) {
         ;
      }
      log.info(ME, "The RMI callback server is shutdown.");
      return true;
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
      if (log.CALL) log.call(ME, "Entering update(" + cbSessionId + ") of " + msgUnitArr.length + " messages");

      return client.update(cbSessionId, msgUnitArr);
   }

   /**
    * The oneway variant for better performance. 
    */
   public void updateOneway(String cbSessionId, org.xmlBlaster.util.MsgUnitRaw[] msgUnitArr) throws RemoteException
   {
      if (msgUnitArr == null) return;
      if (log.CALL) log.call(ME, "Entering updateOneway(" + cbSessionId + ") of " + msgUnitArr.length + " messages");
      try {
         client.updateOneway(cbSessionId, msgUnitArr);
      }
      catch (Throwable e) {
         log.error(ME, "Caught exception which can't be delivered to xmlBlaster because of 'oneway' mode: " + e.toString());
      }
   }

   /**
    * Ping to check if the xmlBlaster server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    */
   public String ping(String str) throws RemoteException
   {
      return "";
   }
} // class RmiCallbackServer

