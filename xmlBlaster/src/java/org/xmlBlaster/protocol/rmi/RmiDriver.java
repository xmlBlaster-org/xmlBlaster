/*------------------------------------------------------------------------------
Name:      RmiDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   RmiDriver class to invoke the xmlBlaster server using RMI.
Version:   $Id: RmiDriver.java,v 1.7 2000/09/15 17:16:20 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.authentication.Authenticate;

import java.rmi.RemoteException;
import java.rmi.RMISecurityManager;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.Naming;


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
 * Invoke options: <br />
 * <pre>
 *   java -Djava.rmi.server.codebase=file:///${XMLBLASTER_HOME}/classes/  \
 *        -Djava.security.policy=${XMLBLASTER_HOME}/config/xmlBlaster.policy \
 *        -Djava.rmi.server.hostname=hostname.domainname
 *        ...
 * </pre>
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

      // Create and install a security manager
      if (System.getSecurityManager() == null) {
         System.setSecurityManager(new RMISecurityManager());
         if (Log.TRACE) Log.trace(ME, "Started RMISecurityManager");
      }

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
      Log.info(ME, "Shutting down RMI driver ...");

      try {
         if (authBindName != null) Naming.unbind(authBindName);
         if (xmlBlasterBindName != null) Naming.unbind(xmlBlasterBindName);
      } catch (Exception e) {
         ;
      }
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
      int registryPort = XmlBlasterProperty.get("rmi.RegistryPort", DEFAULT_REGISTRY_PORT); // default xmlBlaster RMI publishing port is 1099
      try {
         if (registryPort > 0) {
            // Start a 'rmiregistry' if desired
            java.rmi.registry.LocateRegistry.createRegistry(registryPort);
            Log.info(ME, "Started RMI registry on port " + registryPort);
         }

         String hostname;
         try  {
            java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
            hostname = addr.getHostName();
         } catch (Exception e) {
            Log.warn(ME, "Can't determin your hostname");
            hostname = "localhost";
         }
         String prefix = "rmi://";
         authBindName = prefix + hostname + ":" + registryPort + "/I_AuthServer";
         xmlBlasterBindName = prefix + hostname + ":" + registryPort + "/I_XmlBlaster";

         // Publish RMI based xmlBlaster server ...
         try {
            Naming.rebind(authBindName, authRmiServer);
            Log.info(ME, "Bound authentication RMI server to registry with name '" + authBindName + "'");
         } catch (Exception e) {
            Log.error(ME+".RmiRegistryFailed", "RMI registry of '" + authBindName + "' failed: " + e.toString());
            throw new XmlBlasterException(ME+".RmiRegistryFailed", "RMI registry of '" + authBindName + "' failed: " + e.toString());
         }
         try {
            Naming.rebind(xmlBlasterBindName, xmlBlasterRmiServer);
            Log.info(ME, "Bound xmlBlaster RMI server to registry with name '" + xmlBlasterBindName + "'");
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
      text += "   -rmi.RegistryPort   Specify a port number where rmiregistry listens.\n";
      text += "                       Default is port "+DEFAULT_REGISTRY_PORT+", the port 0 switches this feature off.\n";
      text += "\n";
      return text;
   }
}
