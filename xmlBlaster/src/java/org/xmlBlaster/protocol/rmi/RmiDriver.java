/*------------------------------------------------------------------------------
Name:      RmiDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   RmiDriver class to invoke the xmlBlaster server using RMI.
Version:   $Id: RmiDriver.java,v 1.15 2000/10/27 12:28:35 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterSecurityManager;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.authentication.Authenticate;

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
 *     -rmi.registryPort   Specify a port number where rmiregistry listens.
 *                         Default is port 1099, the port 0 switches this feature off.
 *     -rmi.hostname       Specify a hostname where rmiregistry runs.
 *                         Default is the localhost.
 * </pre>
 * <p />
 * Invoke options: <br />
 * <pre>
 *   java -Djava.rmi.server.codebase=file:///${XMLBLASTER_HOME}/classes/  \
 *        -Djava.security.policy=${XMLBLASTER_HOME}/config/xmlBlaster.policy \
 *        -Djava.rmi.server.hostname=hostname.domainname
 *        MyApp -rmi.registryPort 2078
 * </pre>
 * Another option is to include the directory of xmlBlaster.policy into
 * your CLASSPATH.
 *
 * @see http://java.sun.com/products/jdk/1.2/docs/guide/rmi/faq.html
 * @see http://archives.java.sun.com/archives/rmi-users.html
 */
public class RmiDriver implements I_Driver
{
   private static final String ME = "RmiDriver";
   /** XmlBlaster RMI registry listen port is 1099, to access for bootstrapping */
   public static final int DEFAULT_REGISTRY_PORT = 1099;
   /** The singleton handle for this xmlBlaster server */
   private Authenticate authenticate = null;
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


   /** Get a human readable name of this driver */
   public String getName()
   {
      return ME;
   }


   /**
    * Start xmlBlaster RMI access.
    * @param args The command line parameters
    */
   public void init(String args[], Authenticate authenticate, I_XmlBlaster xmlBlasterImpl) throws XmlBlasterException
   {
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;

      XmlBlasterSecurityManager.createSecurityManager();

      try {
         authRmiServer = new AuthServerImpl(authenticate, xmlBlasterImpl);
         xmlBlasterRmiServer = new XmlBlasterImpl(xmlBlasterImpl);
      }
      catch (RemoteException e) {
         Log.error(ME, e.toString());
         throw new XmlBlasterException("RmiDriverFailed", e.toString());
      }

      bindToRegistry();

      Log.info(ME, "Started successfully RMI driver.");
   }


   /**
    *  Instructs RMI to shut down.
    */
   public void shutdown()
   {
      if (Log.TRACE) Log.trace(ME, "Shutting down RMI driver ...");

      try {
         if (authBindName != null) Naming.unbind(authBindName);
         if (xmlBlasterBindName != null) Naming.unbind(xmlBlasterBindName);
      } catch (Exception e) {
         ;
      }

      Log.info(ME, "RMI driver stopped, naming entries released.");
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
      if (Log.CALL) Log.call(ME, "bindToRegistry() ...");
      int registryPort = XmlBlasterProperty.get("rmi.registryPort", DEFAULT_REGISTRY_PORT); // default xmlBlaster RMI publishing port is 1099

      String hostname;
      try  {
         java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
         hostname = addr.getHostName();
      } catch (Exception e) {
         Log.warn(ME, "Can't determin your hostname");
         hostname = "localhost";
      }
      hostname = XmlBlasterProperty.get("rmi.hostname", hostname);

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
                               " we will use this one. You could change the port with e.g. '-rmi.registryPortCB 1122' to run your own rmiregistry.");
               }
               catch (RemoteException e2) {
                  String text = "Port " + DEFAULT_REGISTRY_PORT + " is already in use, but does not seem to be a rmiregistry. Please can change the port with e.g. -rmi.registryPortCB=1122 : " + e.toString();
                  Log.error(ME, text);
                  throw new XmlBlasterException(ME, text);
               }
            }
         }

         String prefix = "rmi://";
         authBindName = prefix + hostname + ":" + registryPort + "/I_AuthServer";
         xmlBlasterBindName = prefix + hostname + ":" + registryPort + "/I_XmlBlaster";

         // Publish RMI based xmlBlaster server ...
         try {
            Naming.bind(authBindName, authRmiServer);
            Log.info(ME, "Bound authentication RMI server to registry with name '" + authBindName + "'");
         } catch (AlreadyBoundException e) {
            try {
               Naming.rebind(authBindName, authRmiServer);
               Log.warn(ME, "Removed another entry while binding authentication RMI server to registry with name '" + authBindName + "'");
            }
            catch(Exception e2) {
               Log.error(ME+".RmiRegistryFailed", "RMI registry of '" + authBindName + "' failed: " + e2.toString());
               throw new XmlBlasterException(ME+".RmiRegistryFailed", "RMI registry of '" + authBindName + "' failed: " + e2.toString());
            }
         } catch (Exception e) {
            Log.error(ME+".RmiRegistryFailed", "RMI registry of '" + authBindName + "' failed: " + e.toString());
            throw new XmlBlasterException(ME+".RmiRegistryFailed", "RMI registry of '" + authBindName + "' failed: " + e.toString());
         }

         try {
            Naming.bind(xmlBlasterBindName, xmlBlasterRmiServer);
            Log.info(ME, "Bound xmlBlaster RMI server to registry with name '" + xmlBlasterBindName + "'");
         } catch (AlreadyBoundException e) {
            try {
               Naming.rebind(xmlBlasterBindName, xmlBlasterRmiServer);
               Log.warn(ME, "Removed another entry while binding xmlBlaster RMI server to registry with name '" + xmlBlasterBindName + "'");
            } catch (Exception e2) {
               Log.error(ME+".RmiRegistryFailed", "RMI registry of '" + xmlBlasterBindName + "' failed: " + e.toString());
               throw new XmlBlasterException(ME+".RmiRegistryFailed", "RMI registry of '" + xmlBlasterBindName + "' failed: " + e.toString());
            }
         } catch (Exception e) {
            Log.error(ME+".RmiRegistryFailed", "RMI registry of '" + xmlBlasterBindName + "' failed: " + e.toString());
            throw new XmlBlasterException(ME+".RmiRegistryFailed", "RMI registry of '" + xmlBlasterBindName + "' failed: " + e.toString());
         }
      } catch (Exception e) {
         e.printStackTrace();
         throw new XmlBlasterException("InitRmiFailed", "Could not initialize RMI registry: " + e.toString());
      }
   }


   /**
    * Command line usage.
    */
   public String usage()
   {
      String text = "\n";
      text += "RmiDriver options:\n";
      text += "   -rmi.registryPort   Specify a port number where rmiregistry listens.\n";
      text += "                       Default is port "+DEFAULT_REGISTRY_PORT+", the port 0 switches this feature off.\n";
      text += "   -rmi.hostname       Specify a hostname where rmiregistry runs.\n";
      text += "                       Default is the localhost.\n";
      text += "\n";
      return text;
   }
}
