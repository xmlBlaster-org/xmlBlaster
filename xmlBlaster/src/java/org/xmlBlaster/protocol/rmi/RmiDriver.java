/*------------------------------------------------------------------------------
Name:      RmiDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   RmiDriver class to invoke the xmlBlaster server using RMI.
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.XmlBlasterSecurityManager;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;

import java.rmi.RemoteException;
import java.rmi.RMISecurityManager;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.Naming;
import java.rmi.AlreadyBoundException;

/**
 * RmiDriver class to invoke the xmlBlaster server using RMI.
 * <p />
 * Design issues:
 * <p />
 * How to identify the calling client.
 * <p />
 * How does RMI handle incoming requests?<br />
 * Is it a worker thread model, or a 1 client/one thread
 * model, or 1 worker thread per RMI call?
 * When a call comes in a random thread is taken from a pool.<br />
 * According to the rmi specs "A method dispatched by the RMI runtime to a
 * remote object implementation (a server) may or may not execute in a
 * separate thread. Some calls originating from the same client virtual
 * machine will execute in the same thread; some will execute in different
 * threads. Calls originating from different client virtual machines will
 * execute in different threads. Other than this last case of different
 * client virtual machines, the RMI runtime makes no guarantees with
 * respect to mapping remote object invocations to threads. "<br />
 * <p />
 * Possible soultions:
 * <ul>
 *    <li>Give each client its own remote object to call<br />
 *        We can implicitly identiofy the client, but we
 *        will have problems with many connecting clients
 *        because of the 1-1 mapping between threads and sockets.
 *    </li>
 *    <li>Pass the sessionId with each method call<br />
 *        Implicitly this will be one server object serving all clients
 *        It is not very smart
 *    </li>
 *    <li>Hack rmic to automatically pass a client id</li>
 * </ul>
 * We will choose the second solution (pass the sessionId).
 * <p />
 * RMI has not fine controlled policy like the CORBA POA!
 * <p />
 * A rmi-registry server is created automatically, if there is running already one, that is used.<br />
 * You can specify another port or host to create/use a rmi-registry server:
 * <pre>
 *     -plugin/rmi/registryPort   Specify a port number where rmiregistry listens.
 *                         Default is port 1099, the port 0 switches this feature off.
 *     -plugin/rmi/hostname Specify a hostname where rmiregistry runs.
 *                         Default is the localhost.
 * </pre>
 * <p />
 * Invoke options: <br />
 * <pre>
 *   java -Djava.rmi.server.codebase=file:///${XMLBLASTER_HOME}/classes/  \
 *        -Djava.security.policy=${XMLBLASTER_HOME}/config/xmlBlaster.policy \
 *        -Djava.rmi.server.hostname=hostname.domainname
 *        MyApp -plugin/rmi/registryPort 2078
 * </pre>
 * Another option is to include the directory of xmlBlaster.policy into
 * your CLASSPATH.
 *
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.rmi.html">The RMI requirement</a>
 * @see <a href="http://java.sun.com/products/jdk/1.2/docs/guide/rmi/faq.html" target="others">RMI FAQ</a>
 * @see <a href="http://archives.java.sun.com/archives/rmi-users.html" target="others">RMI USERS</a>
 */
public class RmiDriver implements I_Driver
{
   private String ME = "RmiDriver";
   private Global glob = null;
   private static Logger log = Logger.getLogger(RmiDriver.class.getName());
   /** XmlBlaster RMI registry listen port is 1099, to access for bootstrapping */
   public static final int DEFAULT_REGISTRY_PORT = 1099;
   /** The singleton handle for this xmlBlaster server */
   private I_Authenticate authenticate = null;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl = null;
   /** The RMI implementation, which delegates to authenticate */
   private AuthServerImpl authRmiServer = null;
   /** The name for the RMI registry */
   private String authBindName = null;
   /** The RMI implementation, which delegates to xmlBlaster server */
   private XmlBlasterImpl xmlBlasterRmiServer = null;
   /** The name for the RMI registry */
   private String xmlBlasterBindName = null;
   private boolean isActive = false;
   /** The protocol plugin configuration */
   private AddressServer addressServer;


   /** Get a human readable name of this driver */
   public String getName() {
      return ME;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "RMI"
    */
   public String getProtocolId() {
      return "RMI";
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getProtocolId();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return "1.0";
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) 
      throws XmlBlasterException {

      this.glob = glob;
      this.ME = "RmiDriver" + this.glob.getLogPrefixDashed();


      org.xmlBlaster.engine.ServerScope engineGlob = (org.xmlBlaster.engine.ServerScope)glob.getObjectEntry("ServerNodeScope");
      if (engineGlob == null)
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "could not retreive the ServerNodeScope. Am I really on the server side ?");
      try {
         this.authenticate = engineGlob.getAuthenticate();
         if (this.authenticate == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "authenticate object is null");
         }
         I_XmlBlaster xmlBlasterImpl = this.authenticate.getXmlBlaster();
         if (xmlBlasterImpl == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "xmlBlasterImpl object is null");
         }

         init(glob, new AddressServer(glob, getType(), glob.getId(), pluginInfo.getParameters()), this.authenticate, xmlBlasterImpl);
         
         activate();
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "init. Could'nt initialize the driver.", ex);
      }
   }

   /**
    * Get the address how to access this driver. 
    * @return "rmi://www.mars.universe:1099/I_AuthServer"
    */
   public String getRawAddress() {
      return authBindName;
   }

   /**
    * Start xmlBlaster RMI access.
    * @param glob Global handle to access logging, property and commandline args
    */
   private synchronized void init(Global glob, AddressServer addressServer, I_Authenticate authenticate, I_XmlBlaster xmlBlasterImpl) throws XmlBlasterException
   {
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;
      this.addressServer = addressServer;

      XmlBlasterSecurityManager.createSecurityManager(glob);

      // plugin/rmi/registryPort
      int registryPort = addressServer.getEnv("registryPort", DEFAULT_REGISTRY_PORT).getValue(); // default xmlBlaster RMI publishing port is 1099
      String hostname = addressServer.getEnv("hostname", glob.getLocalIP()).getValue();

      try {
         if (registryPort > 0) {
            // Start a 'rmiregistry' if desired
            try {
               java.rmi.registry.LocateRegistry.createRegistry(registryPort);
               log.info("Started RMI registry on port " + registryPort);
            } catch (java.rmi.server.ExportException e) {
               // Try to bind to an already running registry:
               try {
                  java.rmi.registry.LocateRegistry.getRegistry(hostname, registryPort);
                  log.info("Another rmiregistry is running on port " + DEFAULT_REGISTRY_PORT +
                               " we will use this one. You could change the port with e.g. '-plugin/rmi/registryPort 1122' to run your own rmiregistry.");
               }
               catch (RemoteException e2) {
                  String text = "Port " + DEFAULT_REGISTRY_PORT + " is already in use, but does not seem to be a rmiregistry. Please can change the port with e.g. -plugin/rmi/registryPortCB=1122 : " + e.toString();
                  log.severe(text);
                  throw new XmlBlasterException(ME, text);
               }
            }
         }

         String prefix = "rmi://";
         authBindName = prefix + hostname + ":" + registryPort + "/I_AuthServer";
         authBindName = addressServer.getEnv("AuthServerUrl", authBindName).getValue();

         xmlBlasterBindName = prefix + hostname + ":" + registryPort + "/I_XmlBlaster";
         xmlBlasterBindName = addressServer.getEnv("XmlBlasterUrl", xmlBlasterBindName).getValue();
      } catch (Exception e) {
         e.printStackTrace();
         throw new XmlBlasterException("InitRmiFailed", "Could not initialize RMI registry: " + e.toString());
      }

      if (log.isLoggable(Level.FINE)) log.fine("Initialized RMI server");
   }

   /**
    * Activate xmlBlaster access through this protocol.
    */
   public synchronized void activate() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering activate");
      try {
         authRmiServer = new AuthServerImpl(glob, this.addressServer, this.authenticate, xmlBlasterImpl);
         xmlBlasterRmiServer = new XmlBlasterImpl(glob, this.addressServer, xmlBlasterImpl);
      }
      catch (RemoteException e) {
         log.severe(e.toString());
         throw new XmlBlasterException("RmiDriverFailed", e.toString());
      }

      bindToRegistry();

      isActive = true;

      log.info("Started successfully RMI driver.");
   }

   /**
    * Deactivate xmlBlaster access (standby), no clients can connect. 
    */
   public synchronized void deActivate() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering deActivate");
      try {
         if (authBindName != null) {
            Naming.unbind(authBindName);
         }
         // force shutdown, even if we still have calls in progress:
         java.rmi.server.UnicastRemoteObject.unexportObject(authRmiServer, true);
      } catch (Exception e) {
         log.warning("Can't shutdown authentication server: " + e.toString());
      }

      try {
         if (xmlBlasterBindName != null) Naming.unbind(xmlBlasterBindName);
         // force shutdown, even if we still have calls in progress:
         java.rmi.server.UnicastRemoteObject.unexportObject(xmlBlasterRmiServer, true);
      } catch (Exception e) {
         log.warning("Can't shutdown xmlBlaster server: " + e.toString());
      }

      isActive = false;

      log.info("RMI deactivated, no client access possible.");
   }

   /**
    *  Instructs RMI to shut down.
    */
   public void shutdown() throws XmlBlasterException {
      if (log.isLoggable(Level.FINE)) log.fine("Shutting down RMI driver ...");

      if (isActive) {
         try {
            deActivate();
         } catch (XmlBlasterException e) {
            log.severe(e.toString());
         }
      }

      authBindName = null;
      log.info("RMI driver stopped, naming entries released.");
   }


   /**
    * Publish the RMI xmlBlaster server to rmi registry.
    * <p />
    * The bind name is typically "rmi://localhost:1099/xmlBlaster"
    * @exception XmlBlasterException
    *                    RMI registry error handling
    */
   private void bindToRegistry() throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("bindToRegistry() ...");

      // Publish RMI based xmlBlaster server ...
      try {
         Naming.bind(authBindName, authRmiServer);
         log.info("Bound authentication RMI server to registry with name '" + authBindName + "'");
      } catch (AlreadyBoundException e) {
         try {
            Naming.rebind(authBindName, authRmiServer);
            log.warning("Removed another entry while binding authentication RMI server to registry with name '" + authBindName + "'");
         }
         catch(Exception e2) {
            if (log.isLoggable(Level.FINE)) log.fine("RMI registry of '" + authBindName + "' failed: " + e2.toString());
            throw new XmlBlasterException(ME+".RmiRegistryFailed", "RMI registry of '" + authBindName + "' failed: " + e2.toString());
         }
      } catch (java.rmi.NoSuchObjectException e) { // 'rmi://noty:7904/I_AuthServer': authRmiServer -> no such object in table
         if (log.isLoggable(Level.FINE)) log.fine("RMI registry of '" + authBindName + "' authRmiServer=" + authRmiServer + " failed: " + e.getMessage());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME+".RmiRegistryFailed",
                                       "RMI registry of '" + authBindName + "' failed, probably another server instance is running already (implementation to handle this is missing): " + e.toString());
      } catch (Throwable e) {
         if (log.isLoggable(Level.FINE)) log.fine("RMI registry of '" + authBindName + "' failed: " + e.getMessage());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME+".RmiRegistryFailed",
                                       "RMI registry of '" + authBindName + "' failed: ", e);
      }

      try {
         Naming.bind(xmlBlasterBindName, xmlBlasterRmiServer);
         log.info("Bound xmlBlaster RMI server to registry with name '" + xmlBlasterBindName + "'");
      } catch (AlreadyBoundException e) {
         try {
            Naming.rebind(xmlBlasterBindName, xmlBlasterRmiServer);
            log.warning("Removed another entry while binding xmlBlaster RMI server to registry with name '" + xmlBlasterBindName + "'");
         } catch (Exception e2) {
            log.severe("RMI registry of '" + xmlBlasterBindName + "' failed: " + e.toString());
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME+".RmiRegistryFailed", "RMI registry of '" + xmlBlasterBindName + "' failed: " + e.toString());
         }
      } catch (Throwable e) {
         log.severe("RMI registry of '" + xmlBlasterBindName + "' failed: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME+".RmiRegistryFailed", "RMI registry of '" + xmlBlasterBindName + "' failed", e);
      }
   }


   /**
    * Command line usage.
    */
   public String usage()
   {
      String text = "\n";
      text += "RmiDriver options:\n";
      text += "   -plugin/rmi/registryPort\n";
      text += "                       Specify a port number where rmiregistry listens.\n";
      text += "                       Default is port "+DEFAULT_REGISTRY_PORT+", the port 0 switches this feature off.\n";
      text += "   -plugin/rmi/hostname\n";
      text += "                       Specify a hostname where rmiregistry runs.\n";
      text += "                       Default is the localhost.\n";
      text += "\n";
      return text;
   }
}
