/*------------------------------------------------------------------------------
Name:      RmiConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: RmiCallbackServer.java,v 1.1 2000/10/22 12:35:29 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.rmi;


import org.xmlBlaster.protocol.rmi.I_XmlBlasterCallback;
import org.xmlBlaster.client.protocol.I_CallbackExtended;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.Naming;


/**
 * Example for a RMI callback implementation.
 * <p />
 * You can use this default callback handling with your clients,
 * but if you need other handling of callbacks, take a copy
 * of this Callback implementation and add your own code.
 * <p />
 * Note: The security manager must be initialized properly before you use an instance of this class.<br />
 * RmiConnection does it in its constructor if you use this class, or you could use RmiConnection.createSecurityManager() to do this.
 * <p />
 * Note: The rmi-registry must be running before you use an instance of this class.
 */
class RmiCallbackServer extends UnicastRemoteObject implements I_XmlBlasterCallback
{
   private final String ME;
   private final I_CallbackExtended boss;
   private final String loginName;
   /** The name for the RMI registry */
   private String callbackRmiServerBindName = null;
   /** XmlBlaster RMI registry listen port is 1099, to register callback server */
   public static final int DEFAULT_REGISTRY_PORT = 1099; // org.xmlBlaster.protocol.rmi.RmiDriver.DEFAULT_REGISTRY_PORT;


   /**
    * Construct a persistently named object.
    * @param client    Your implementation of I_CallbackExtended, or null if you don't want any updates.
    */
   public RmiCallbackServer(String name, I_CallbackExtended boss) throws RemoteException, XmlBlasterException
   {
      this.ME = "RmiCallbackServer-" + name;
      this.boss = boss;
      this.loginName = name;
      createCallbackServer(this);
      Log.info(ME, "Success, created RMI callback server for " + loginName);
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
                               " we will use this one. You could change the port with e.g. '-rmi.RegistryPortCB 1122' to run your own rmiregistry.");
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
    * @return The RMI registry entry of this server, which can be used for the loginQoS
    */
   public CallbackAddress getCallbackHandle()
   {
      CallbackAddress addr = new CallbackAddress("RMI");
      addr.setAddress(callbackRmiServerBindName);
      return addr;
   }


   /**
    * Shutdown the callback server.
    */
   public void shutdown()
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
   public void update(MessageUnit[] msgUnitArr) throws RemoteException, XmlBlasterException
   {
      if (msgUnitArr == null) return;
      boss.update(loginName, msgUnitArr);
   }

} // class RmiCallbackServer

