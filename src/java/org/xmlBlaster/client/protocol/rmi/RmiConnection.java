/*------------------------------------------------------------------------------
Name:      RmiConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using RMI
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.rmi;


import org.xmlBlaster.protocol.rmi.I_AuthServer;
import org.xmlBlaster.protocol.rmi.I_XmlBlaster;
import org.xmlBlaster.protocol.rmi.RmiUrl;

import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.XmlBlasterSecurityManager;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.xbformat.I_ProgressListener;

import java.rmi.RemoteException;
import java.rmi.Naming;
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
 * If you need a failsafe client, you can invoke the xmlBlaster RMI methods
 * through this class as well (for example use rmiConnection.publish() instead of the direct
 * RMI server.publish()).
 * <p />
 * If you want to connect from a servlet, please use the framework in xmlBlaster/src/java/org/xmlBlaster/protocol/http
 * <pre>
 *  # Configure RMI plugin to load:
 *  ClientProtocolPlugin[RMI][1.0]=org.xmlBlaster.client.protocol.rmi.RmiConnection
 * </pre>
 *
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.rmi.html">The RMI requirement</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class RmiConnection implements I_XmlBlasterConnection
{
   private String ME = "RmiConnection";
   private Global glob;
   private static Logger log = Logger.getLogger(RmiConnection.class.getName());

   private I_AuthServer authServer = null;
   private I_XmlBlaster blasterServer = null;
   private String sessionId = null;
   protected Address clientAddress;
   private RmiUrl rmiUrl;

   /** XmlBlaster RMI registry listen port is 1099, to access for bootstrapping */
   public static final int DEFAULT_REGISTRY_PORT = 1099; // org.xmlBlaster.protocol.rmi.RmiDriver.DEFAULT_REGISTRY_PORT;
   private boolean verbose = true;

   /**
    * Called by plugin loader which calls init(Global, PluginInfo) thereafter. 
    */
   public RmiConnection() {
   }

   /**
    * RMI client access to xmlBlaster for <strong>normal client applications</strong>.
    * <p />
    * @param arg  parameters given on command line
    */
   public RmiConnection(Global glob) throws XmlBlasterException {
      init(glob, null);
   }

   /**
    * RMI client access to xmlBlaster for <strong>applets</strong>.
    * <p />
    * @param ap  Applet handle
    */
   public RmiConnection(Global glob, Applet ap) throws XmlBlasterException {
      init(glob, null);
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getProtocol();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return "1.0";
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) throws XmlBlasterException {
      this.glob = (glob == null) ? Global.instance() : glob;

      XmlBlasterSecurityManager.createSecurityManager(this.glob);
      log.info("Created '" + getProtocol() + "' protocol plugin to connect to xmlBlaster server");
   }

   /**
    * Connect to RMI server.
    */
   public void connectLowlevel(Address address) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("connectLowlevel() ...");

      if (this.authServer != null) {
         return;
      }

      this.clientAddress = address;

      // default xmlBlaster RMI publishing registryPort is 1099
      this.rmiUrl = new RmiUrl(glob, this.clientAddress);

      String authServerUrl = this.rmiUrl.getUrl() + "I_AuthServer";
      String addr = this.clientAddress.getEnv("AuthServerUrl", authServerUrl).getValue();
      Remote rem = lookup(addr);
      if (rem instanceof org.xmlBlaster.protocol.rmi.I_AuthServer) {
         this.authServer = (I_AuthServer)rem;
         this.clientAddress.setRawAddress(addr);
         log.info("Accessed xmlBlaster authentication reference with '" + addr + "'");
      }
      else {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "No connect to '" + addr + "' possible, class needs to implement interface I_AuthServer.");
      }

      String xmlBlasterUrl = this.rmiUrl.getUrl() + "I_XmlBlaster";
      addr = this.clientAddress.getEnv("XmlBlasterUrl", xmlBlasterUrl).getValue();
      rem = lookup(addr);
      if (rem instanceof org.xmlBlaster.protocol.rmi.I_XmlBlaster) {
         this.blasterServer = (I_XmlBlaster)rem;
         log.info("Accessed xmlBlaster server reference with '" + addr + "'");
      }
      else {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "No connect to '" + addr + "' possible, class needs to implement interface I_XmlBlaster.");
      }
   }


   /**
    * Connect to RMI server.
    * @see I_XmlBlasterConnection#connectLowlevel(Address)
    */
   private Remote lookup(String addr) throws XmlBlasterException {
      try {
         return Naming.lookup(addr);
      }
      catch (RemoteException e) {
         if (this.verbose) log.warning("Can't access address ='" + addr + "', no rmi registry running");
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "Can't access address ='" + addr + "', no rmi registry running");
      }
      catch (NotBoundException e) {
         if (this.verbose) log.warning("The given address ='" + addr + "' is not bound to rmi registry: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "The given address '" + addr + "' is not bound to rmi registry: " + e.toString());
      }
      catch (MalformedURLException e) {
         log.severe("The given address ='" + addr + "' is invalid: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "The given address '" + addr + "' is invalid: " + e.toString());
      }
      catch (Throwable e) {
         log.severe("The given address ='" + addr + "' is invalid : " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "The given address '" + addr + "' is invalid : " + e.toString());
      }
      finally {
         this.verbose = false;
      }
   }

   /**
    * Reset
    */
   public void resetConnection(){
      this.authServer = null;
      this.blasterServer = null;
      this.sessionId = null;
   }

   /**
    * Accessing the xmlBlaster handle.
    * For internal use, throws an ordinary Exception if xmlBlaster==null
    * We use this for similar handling as org.omg exceptions.
    * @return Server
    */
   private I_XmlBlaster getXmlBlaster() throws XmlBlasterException {
      if (this.blasterServer == null) {
         if (log.isLoggable(Level.FINE)) log.fine("No RMI connection available.");
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                                       "The RMI xmlBlaster handle is null, no connection available");
      }
      return this.blasterServer;
   }

   /**
    * @return The connection protocol name "RMI"
    */
   public final String getProtocol() {
      return "RMI";
   }

   /**
    * Login to the server. 
    * <p />
    * @param connectQos The encrypted connect QoS 
    * @exception   XmlBlasterException if login fails
    */
   public String connect(String connectQos) throws XmlBlasterException {
      if (connectQos == null)
         throw new XmlBlasterException(ME+".connect()", "Please specify a valid QoS");
      if (log.isLoggable(Level.FINER)) log.finer("connect() ...");

      if (this.sessionId != null) {
         log.warning("You are already logged in.");
         return "";
      }

      connectLowlevel(this.clientAddress);

      try {
         return authServer.connect(connectQos);
      } catch(RemoteException e) {
         if (log.isLoggable(Level.FINE)) log.fine("Login failed");
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "Login failed");
      }
   }

   /**
    * @see I_XmlBlasterConnection#setConnectReturnQos(ConnectReturnQos)
    */
   public void setConnectReturnQos(ConnectReturnQos connectReturnQos) {
      this.sessionId = connectReturnQos.getSecretSessionId();
      this.ME = "RmiConnection-"+connectReturnQos.getSessionName().toString();
   }

   /**
    * Logout from the server.
    * <p />
    * The callback server is removed as well, releasing all RMI threads.
    * Note that this kills the server ping thread as well (if in failsafe mode)
    * @return true successfully logged out
    *         false failure on gout
    */
   public boolean disconnect(String disconnectQos) {
      if (log.isLoggable(Level.FINER)) log.finer("logout() ...");

      try {
         if (authServer != null) {
            authServer.disconnect(this.sessionId, (disconnectQos==null)?"":disconnectQos);
         }
         shutdown();
         resetConnection();
         return true;
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
      } catch(RemoteException e) {
         log.warning(e.toString());
         e.printStackTrace();
      }

      try {
         shutdown();
      }
      catch (XmlBlasterException ex) {
         log.severe("disconnect: could not shutdown properly. " + ex.getMessage());
      }
      resetConnection();
      return false;
   }


   /**
    * Shut down.
    * Is called by logout()
    */
   public void shutdown() throws XmlBlasterException {
   }

   /**
    * @return true if you are logged in
    */
   public boolean isLoggedIn() {
      return this.blasterServer != null;
   }

   /**
    * Enforced by I_XmlBlasterConnection interface (failsafe mode).
    * see explanations of publish() method.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public final String subscribe(String xmlKey, String qos) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("subscribe() ...");
      try {
         return getXmlBlaster().subscribe(this.sessionId, xmlKey, qos);
      } catch(XmlBlasterException e) {
         throw e;
      } catch(Exception e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "subscribe", e);
      }
   }

   /**
    * Enforced by I_XmlBlasterConnection interface (failsafe mode)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public final String[] unSubscribe(String xmlKey, String qos) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("unSubscribe() ...");
      try {
         return getXmlBlaster().unSubscribe(this.sessionId, xmlKey, qos);
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
    * Enforced by I_XmlBlasterConnection interface (failsafe mode)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public final String publish(MsgUnitRaw msgUnit) throws XmlBlasterException {
      if (log.isLoggable(Level.FINE)) log.fine("Publishing ...");
      try {
         return getXmlBlaster().publish(this.sessionId, msgUnit);
      } catch(XmlBlasterException e) {
         if (log.isLoggable(Level.FINE)) log.fine("XmlBlasterException: " + e.getMessage());
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
      if (log.isLoggable(Level.FINER)) log.finer("publishArr() ...");
      try {
         return getXmlBlaster().publishArr(this.sessionId, msgUnitArr);
      } catch(XmlBlasterException e) {
         if (log.isLoggable(Level.FINE)) log.fine("XmlBlasterException: " + e.getMessage());
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
      if (log.isLoggable(Level.FINER)) log.finer("publishOneway(), RMI does not support oneway, we switch to publishArr() ...");
      publishArr(msgUnitArr);
   }

   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public final String[] erase(String xmlKey, String qos) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("erase() ...");
      try {
         return getXmlBlaster().erase(this.sessionId, xmlKey, qos);
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
      if (log.isLoggable(Level.FINER)) log.finer("get() ...");
      try {
         return getXmlBlaster().get(this.sessionId, xmlKey, qos);
      } catch(XmlBlasterException e) {
         throw e;
      } catch(Exception e) {
         e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "get", e);
      }
   }

   /**
    * Register a listener for to receive information about the progress of incoming data. 
    * Only one listener is supported, the last call overwrites older calls. This implementation
    * does nothing here, it just returns null.
    * 
    * @param listener Your listener, pass 0 to unregister.
    * @return The previously registered listener or 0
    */
   public I_ProgressListener registerProgressListener(I_ProgressListener listener) {
      log.fine("This method is currently not implemeented.");
      return null;
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
      text += "   -dispatch/connection/plugin/rmi/registryPort\n";
      text += "                       Specify a port number where rmiregistry of the xmlBlaster server listens.\n";
      text += "                       Default is port "+DEFAULT_REGISTRY_PORT+", the port 0 switches this feature off.\n";
      text += "   -dispatch/connection/plugin/rmi/hostname\n";
      text += "                       Specify a hostname where rmiregistry of the xmlBlaster server runs.\n";
      text += "                       Default is the localhost.\n";
      text += "   -dispatch/callback/plugin/rmi/registryPort\n";
      text += "                       Specify a port number where rmiregistry for the callback server listens.\n";
      text += "                       Default is port "+DEFAULT_REGISTRY_PORT+", the port 0 switches this feature off.\n";
      text += "   -dispatch/callback/plugin/rmi/hostname\n";
      text += "                       Specify a hostname where rmiregistry for the callback server runs.\n";
      text += "                       Default is the localhost (useful for multi homed hosts).\n";
      text += "\n";
      return text;
   }
} // class RmiConnection

