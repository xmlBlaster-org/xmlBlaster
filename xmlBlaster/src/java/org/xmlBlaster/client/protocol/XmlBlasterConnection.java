/*------------------------------------------------------------------------------
Name:      XmlBlasterConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP/RMI or XML-RPC
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import org.xmlBlaster.util.Global;
import org.jutils.log.LogChannel;
import org.jutils.JUtilsException;

import org.xmlBlaster.client.protocol.corba.CorbaConnection;
import org.xmlBlaster.client.protocol.corba.CorbaCallbackServer;
import org.xmlBlaster.client.protocol.rmi.RmiConnection;
import org.xmlBlaster.client.protocol.rmi.RmiCallbackServer;
import org.xmlBlaster.client.protocol.xmlrpc.XmlRpcConnection;
import org.xmlBlaster.client.protocol.xmlrpc.XmlRpcCallbackServer;
import org.xmlBlaster.client.protocol.socket.SocketConnection;
import org.xmlBlaster.client.protocol.socket.SocketCallbackImpl;
//import org.xmlBlaster.client.protocol.soap.SoapConnection;
//import org.xmlBlaster.client.protocol.soap.SoapCallbackServer;

import org.xmlBlaster.client.BlasterCache;
import org.xmlBlaster.client.PluginLoader;
import org.xmlBlaster.client.I_ConnectionProblems;
import org.xmlBlaster.client.I_CallbackRaw;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.recorder.I_InvocationRecorder;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.qos.storage.ClientQueueProperty;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.I_Timeout;

import java.applet.Applet;

import java.util.HashMap;
import java.util.Vector;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Collections;


/**
 * This is a helper class, helping a Java client to connect to xmlBlaster
 * using IIOP (CORBA), RMI, XML-RPC, SOCKET, SOAP or any other supported protocol.
 * <p />
 * Please note that you don't need to use this wrapper, you can use the raw I_XmlBlasterConnection
 * interface as well. You can also hack your own little wrapper, which does exactly
 * what you want.
 * <p />
 * There is a constructor for applets, and standalone Java clients.
 * <p />
 * <b>Fail Save Mode:</b><br />
 * If you need a fail save client, you can invoke the xmlBlaster methods
 * through this class as well (for example use xmlBlasterConnection.publish()
 * instead of the direct CORBA/RMI/XML-RPC server.publish()).
 * <p />
 * You need to call <b>initFailSave()</b>, to switch this on, and pass it your implementation of I_ConnectionProblems.<br />
 * If suddenly the xmlBlaster server disappears, XmlBlasterConnection
 * queues your messages locally, and starts polling to find the server again. You will get
 * an Exception and a callback through I_ConnectionProblems and may decide to continue with your work (or not).
 * <p />
 * As soon as the connection can be established again, you are informed by I_ConnectionProblems.reConnect()
 * you may send some initial messages (as on startup of your client) and invoke
 * xmlBlasterConnection.flushQueue() to send all messages collected during disruption in the correct order or
 * xmlBlasterConnection.resetQueue() to discard the queued messages to xmlBlaster.
 * <p />
 * One drawback is, that the return values of your requests are lost, since you were none blocking
 * continuing during the connection was lost.
 * <p />
 * You can have a look at xmlBlaster/testsuite/src/java/org/xmlBlaster/qos/TestFailSave.java to find out how it works
 * <p />
 * If you specify the last argument in initFailSave() to bigger than 0 milliseconds,
 * a thread is installed which does a ping to xmlBlaster (tests the connection) with the given sleep interval.
 * If the ping fails, the login polling is automatically activated.
 * <p />
 * If you want to connect from a servlet, please use the framework in xmlBlaster/src/java/org/xmlBlaster/protocol/http
 * <p />
 * The interface I_CallbackRaw/I_Callback/I_CallbackExtenden are enforced by AbstractCallbackExtended
 * is for the InvocationRecorder to playback locally queued messages and for the protocol drivers.
 * <p />
 * <b>Security Plugin:</b><br />
 * If a client side security plugin is specified in xmlBlaster.properties
 * or on command line, this will be loaded and used here. All xmlBlaster invocations
 * will be intercepted with your supplied plugin.<br />
 * Your plugin must implement the interfaces I_ClientPlugin and I_SecurityQos
 *
 * @see org.xmlBlaster.authentication.plugins.I_ClientPlugin
 * @see org.xmlBlaster.authentication.plugins.I_SecurityQos
 * @see org.xmlBlaster.test.qos.TestFailSave
 *
 * @deprecated Please use org.xmlBlaster.client.XmlBlasterAccess instead
 */
public class XmlBlasterConnection extends AbstractCallbackExtended implements I_XmlBlaster, I_CallbackServer, I_Timeout
{
   private String ME = "XmlBlasterConnection";
   // Global glob and LogChannel log  see AbstractCallbackExtended

   /** The cluster node id (name) to which we want to connect, needed for nicer logging, can be null */
   protected String serverNodeId = "xmlBlaster";

   protected int recorderCounter = 0;

   protected int subscribeUniqueCounter = 0;

   private boolean firstConnect = true;

   private boolean disconnectInProgress = false;

   /** The driver, e.g. Corba/Rmi/XmlRpc */
   private I_XmlBlasterConnection driver;
   /** The callback server, e.g. Corba/Rmi/XmlRpc */
   private I_CallbackServer cbServer;

   /** Holding the current returned QoS from the connect() call */
   private ConnectReturnQos connectReturnQos;

   /** queue all the messages, and play them back through interface I_InvocationRecorder */
   private I_InvocationRecorder recorder;

   /** This interface needs to be implemented by the client in fail save mode
       The client gets notified about abnormal connection loss or reconnect */
   private I_ConnectionProblems clientProblemCallback;

   /** Used to callback the clients update() method */
   private I_Callback updateClient;

   /** true if we are in fails save mode and polling for xmlBlaster */
   private boolean isReconnectPolling = false;

   /** Store the connection configuration and parameters */
   private ConnectQos connectQos;

   /** Number of retries if connection cannot directly be established */
   private int retries = -1;

   /** communicate from LoginThread back to CorbaConnection that we give up */
   private boolean noConnect = false;

   /** Handle on the ever running ping thread.Only switched on in fail save mode */
   private PingThread pingThread;

   private LoginThread loginThread;

   /** Remember the number of successful logins */
   private long numLogins = 0L;

   private MsgUnit[] dummyMArr = new MsgUnit[0];
   private String[] dummySArr = new String[0];
   private String dummyS = "";

   /** Cache */
   private BlasterCache cache = null;

   private Object pollingMonitor = new Object();

   /** Client side helper classes to load the authentication xml string */
   private I_ClientPlugin secPlgn = null;

   /** This map contains the registered callback interfaces for given subscriptions.
       The key is the subscription ID */
   private final HashMap callbackMap = new HashMap();

   /** Number of milliseconds xmlBlaster shall collect before publishOneway, 0 switched this feature off */
   private long publishOnewayCollectTime = 0L;
   /** Contains all collected (and not yet exported) messages */
   private Vector publishOnewayBurstModeVec = null;
   private Timeout publishOnewayTimer;
   private Timestamp publishOnewayTimerKey = null;

   // Is initialized if a specific initFailSave() is called before connect()
   private Address addressFailSaveSettings = null;


   /**
    * Client access to xmlBlaster for client applications.
    * <p />
    * This constructor defaults to CORBA (IOR driver) communication to the server.
    * <p />
    * You can choose the protocol with this command line option: <br />
    * <pre>
    * java ... -dispatch/clientSide/protocol SOCKET
    * java ... -dispatch/clientSide/protocol RMI
    * java ... -dispatch/clientSide/protocol IOR
    * java ... -dispatch/clientSide/protocol XML-RPC
    * </pre>
    */
   public XmlBlasterConnection() throws XmlBlasterException {
      super(Global.instance());
      initArgs(null);
   }

   /**
    * Client access to xmlBlaster for client applications.
    * <p />
    * This constructor defaults to CORBA (IOR driver) communication to the server.
    * <p />
    * You can choose the protocol with this command line option: <br />
    * <pre>
    * java ... -dispatch/clientSide/protocol RMI
    * java ... -dispatch/clientSide/protocol IOR
    * java ... -dispatch/clientSide/protocol XML-RPC
    * java ... -dispatch/clientSide/protocol SOCKET
    * </pre>
    * @param arg  parameters given on command line, or coded e.g.:
    * <pre>
    *    String[] args = new String[2];
    *    args[0] = "-port";
    *    args[1] = "" + serverPort;
    *    xmlBlasterConnection = new XmlBlasterConnection(args); // Find orb
    * </pre>
    * <p>
    * NOTE: If you want to use multiple XmlBlasterConnection in the same client
    * you have to use the XmlBlasterConnection(Global) constructor and provide a different
    * Global instance on each creation - or the XmlBlasterConnection(String[], boolean)
    * constructor which does this for you.
    * </p>
    */
   public XmlBlasterConnection(String[] args) throws XmlBlasterException {
      super(Global.instance());
      initArgs(args);
   }

   public XmlBlasterConnection(String[] args, boolean loadPropFile) throws XmlBlasterException {
      super(new Global(args, loadPropFile, false));
   }

   public XmlBlasterConnection(Global glob) throws XmlBlasterException {
      super(glob);
      if (glob.wantsHelp()) {
         usage();
      }
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
    * Initialize client side burst mode.
    */
   private void initBurstMode(Address address) {
      this.publishOnewayCollectTime = address.getCollectTime(); // glob.getProperty().get("burstMode.collectTimeOneway", 0L);
      if (this.publishOnewayCollectTime > 0L) {
         this.publishOnewayBurstModeVec = new Vector(1000);
         this.publishOnewayTimer = new Timeout("PublishOnewayTimer");
      }
   }

   private void initArgs(String[] args) {
      if (this.glob.init(args) != 0) {
         usage();
      }
      this.serverNodeId = glob.getId();
      /*
      this.serverNodeId = glob.getProperty().get("server.node.id", (String)null);
      if (this.serverNodeId == null)
         this.serverNodeId = glob.getProperty().get("cluster.node.id", "xmlBlaster");  // fallback
      */
   }

   /**
    * The cluster node id (name) to which we want to connect.
    * <p />
    * Needed only for nicer logging when running in a cluster.<br />
    * Is configurable with "-server.node.id golan"
    * @return e.g. "golan", defaults to "xmlBlaster"
    */
   public String getServerNodeId() {
      return this.serverNodeId;
   }

   /**
    * Allows to set the node name for nicer logging.
    */
   public void setServerNodeId(String nodeId) {
      this.serverNodeId = nodeId;
      this.ME = "XmlBlasterConnection-" + getMe();
   }

   public final Global getGlobal() {
      return this.glob;
   }

   /**
    * Initializes the little client helper framework for authentication.
    * <p />
    * The first goal is a proper loginQoS xml string for authentication.
    * <p />
    * The second goal is to intercept the messages for encryption (or whatever the
    * plugin supports).
    * <p />
    * See xmlBlaster.properties, for example:
    * <pre>
    *   Security.Client.DefaultPlugin=gui,1.0
    *   Security.Client.Plugin[gui][1.0]=org.xmlBlaster.authentication.plugins.gui.ClientSecurityHelper
    * </pre>
    */
   private void initSecuritySettings(String secMechanism, String secVersion)
   {
      PluginLoader secPlgnMgr = glob.getClientSecurityPluginLoader();
      try {
         secPlgn = secPlgnMgr.getClientPlugin(secMechanism, secVersion);
         if (secMechanism != null)  // to avoid double logging for login()
            log.info(ME, "Loaded security plugin=" + secMechanism + " version=" + secVersion);
      }
      catch (Exception e) {
         log.error(ME, "Security plugin initialization failed. Reason: "+e.toString());
         secPlgn = null;
      }
   }

   public I_ClientPlugin getSecurityPlugin()
   {
      return secPlgn;
   }

   /**
    * Load the desired protocol driver like CORBA or RMI driver.
    */
   private void initDriver(Address addr) throws XmlBlasterException
   {
      String driverType = addr.getType();
      if (driverType == null || driverType.length() < 1)
         throw new XmlBlasterException(ME+".UnknownDriver", "Connection to " + getServerNodeId() + " failed, the protocol type is unknown");

      if (log.TRACE) log.trace(ME, "Using 'client.protocol=" + driverType + "' to access " + getServerNodeId());

      if (driverType.equalsIgnoreCase("SOCKET")) {
         driver = new SocketConnection(this.glob);
      }
      else if (driverType.equalsIgnoreCase("IOR") || driverType.equalsIgnoreCase("IIOP")) {
         driver = new CorbaConnection(this.glob);
      }
      else if (driverType.equalsIgnoreCase("RMI")) {
         driver = new RmiConnection(this.glob);
      }
      else if (driverType.equalsIgnoreCase("XML-RPC") || driverType.equalsIgnoreCase("XMLRPC")) {
         driver = new XmlRpcConnection(this.glob);
      }
//      else if (driverType.equalsIgnoreCase("SOAP")) {
//         driver = new SoapConnection(this.glob);
//      }
      else {
         String text = "Unknown protocol '" + driverType + "' to access " + getServerNodeId() + ", use SOCKET, IOR, RMI, SOAP or XML-RPC.";
         log.error(ME, text);
         throw new XmlBlasterException(ME+".UnknownDriver", text);
      }
   }

   /**
    * Load the desired protocol driver like SOCKET, CORBA(IOR), RMI or XML-RPC driver.
    TODO: generic class loading -> use PluginManager
   private void initDriver(String driverType, String driverClassName) throws XmlBlasterException
   {
      if (driverType == null) driverType = glob.getProperty().get("client.protocol", "IOR");
      if (driverClassName == null) driverClassName = glob.getProperty().get("client.protocol.class",
                                                                            "org.xmlBlaster.protocol.corba.CorbaConnection");
      log.info(ME, "Using 'client.protocol=" + driverType + "' and 'client.protocol.class=" + driverClassName + "'to access " + getServerNodeId());
      try {
         java.lang.Class driverClass = Class.forName(driverClassName);
         java.lang.Class[] paramTypes = new java.lang.Class[1];
         paramTypes[0] = this.glob.getArgs().getClass();
         java.lang.reflect.Constructor constructor = driverClass.getDeclaredConstructor(paramTypes);
         Object[] params = new Object[1];
         params[0] = this.glob;
         driver = (I_XmlBlasterConnection) constructor.newInstance(params);
      }
      catch(Exception e) {
         String text = "Invalid driver class '" + driverClassName + "' to access " + getServerNodeId() + ". (Check package name and constructors!)";
         log.error(ME, text);
         throw new XmlBlasterException(ME+".InvalidDriver", text);
      }
   }
   */

   /**
    * Initialize fail save mode.
    * <p />
    * This switches fail save mode on, on lost connection we try every 5 sec a reconnect.<br />
    *
    * Configured with command line or xmlBlaster.properties settings
    * <p />
    * For example:
    * <pre>
    *   java javaclients.ClientSub -delay 8000
    * </pre>
    * on lost connection we try every 8 sec a reconnect.<br />
    * <p />
    * See private class DummyConnectionProblemHandler
    * @param connCallback Your implementation of I_ConnectionProblems, we initialize fail save mode with default settings<br />
    *                     If null, DummyConnectionProblemHandler is used and
    *                     fail save mode is only switched on, if -delay is set bigger 0
    */
   public void initFailSave(I_ConnectionProblems connCallback)
   {
      if (connCallback == null)
         this.clientProblemCallback = new DummyConnectionProblemHandler(this);
      else
         this.clientProblemCallback = connCallback;
      log.info(ME, "Initializing fail save mode");
   }


   /**
    * CORBA client access to xmlBlaster for <strong>applets</strong>.
    * <p />
    * Use these environment settings for JacORB if you don't use this constructor!
    * <br />
    * Example:
    *  <pre>
    *     &lt;APPLET
    *        CODEBASE = "http://localhost"
    *        CODE     = "DemoApplet.class"
    *        NAME     = "xmlBlaster demo"
    *        WIDTH    = 200
    *        HEIGHT   = 200
    *        HSPACE   = 0
    *        VSPACE   = 0
    *        ALIGN    = middle
    *     >
    *     &lt;PARAM name=org.omg.CORBA.ORBClass value=org.jacorb.orb.ORB>
    *     &lt;PARAM name=org.omg.CORBA.ORBSingletonClass value=org.jacorb.orb.ORBSingleton>
    *     &lt;PARAM name=SVCnameroot value=xmlBlaster-Authenticate>
    *     &lt;/APPLET>
    *  </pre>
    * @param ap  Applet handle
    * @param protocol e.g. "IOR", "RMI", "XML-RPC"
    *                 IOR is the CORBA driver.
    */
   public XmlBlasterConnection(Applet ap, String driverType) throws XmlBlasterException
   {
      super(Global.instance());
      if (driverType.equalsIgnoreCase("SOCKET"))
         driver = new SocketConnection(glob, ap);
      else if (driverType.equalsIgnoreCase("IOR") || driverType.equalsIgnoreCase("IIOP"))
         driver = new CorbaConnection(glob, ap);
      else if (driverType.equalsIgnoreCase("RMI"))
         driver = new RmiConnection(glob, ap);
      else if (driverType.equalsIgnoreCase("XML-RPC") || driverType.equalsIgnoreCase("XMLRPC"))
         driver = new XmlRpcConnection(glob, ap);
//      else if (driverType.equalsIgnoreCase("SOAP"))
//         driver = new SoapConnection(glob, ap);
      else {
         String text = "Unknown protocol '" + driverType + "' to access " + getServerNodeId() + ", use IOR, RMI or XML-RPC.";
         log.error(ME, text);
         throw new XmlBlasterException(ME+".UnknownDriver", text);
      }
   }


   /**
    * Setup the fail save mode.
    *
    * @param connCallback The interface to notify the client about problems
    * @param retryInterval How many milli seconds sleeping before we retry a connection
    * @param retries Number of retries if connection cannot directly be established
    *                passing -1 does polling forever
    * @param maxInvocations How many messages shall we queue max (using the InvocationRecorder)
    * @param pingInterval How many milli seconds sleeping between the pings<br />
    *                     < 1 switches pinging off
    * @see org.xmlBlaster.util.recorder.ram.RamRecorder
    * @deprecated Use explicit Address settings, e.g.:
    * <pre>
    *  // Setup fail save handling ...
    *  Address addressProp = new Address(glob);
    *  addressProp.setDelay(4000L);      // retry connecting every 4 sec
    *  addressProp.setRetries(-1);       // -1 == forever
    *  addressProp.setPingInterval(0L);  // switched off
    *  addressProp.setMaxEntries(1000);      // queue up to 1000 messages
    *
    *  con.initFailSave(this);           // We want to be informed about problems (interface I_ConnectionProblems)
    *
    *  connectQos.setAddress(addressProp);
    * </pre>
    */
   public synchronized void initFailSave(I_ConnectionProblems connCallback, long retryInterval,
                                         int retries, int maxInvocations, long pingInterval)
   {
      if (log.CALL) log.call(ME, "Initializing fail save mode: retryInterval=" + retryInterval + ", retries=" + retries + ", maxInvocations=" + maxInvocations + ", pingInterval=" + pingInterval);
      if (this.addressFailSaveSettings == null) this.addressFailSaveSettings = new Address(glob);
      this.clientProblemCallback = connCallback;
      this.addressFailSaveSettings.setDelay(retryInterval);
      this.addressFailSaveSettings.setRetries(retries);
      this.addressFailSaveSettings.setPingInterval(pingInterval);
   }


   /**
    * Setup the cache mode.
    * <p />
    * This installs a cache. When you call get(), a subscribe() is done
    * in the background that we always have a current value in our client side cache.
    * Further get() calls retrieve the value from the client cache.
    * <p />
    * Only the first call is used to setup the cache, following calls
    * are ignored silently
    *
    * @param size Size of the cache. This number specifies the count of subscriptions the cache
    *             can hold. It specifies NOT the number of messages.
    */
   public void initCache(int size)
   {
      if (cache != null)
         return; // Is initialized already
      if (log.CALL) log.call(ME, "Initializing cache: size=" + size);
      cache = new BlasterCache(glob, size);
      log.info(ME, "BlasterCache has been initialized with size="+size);
   }

   /**
    * Killing the ping thread (not recommended).
    */
   protected void killPing()
   {
      if (pingThread != null)
         pingThread.pingRunning = false;
   }

   protected void killLoginThread()
   {
      if (loginThread != null)
         loginThread.pollRunning = false;
   }


   /**
    * Is fail save mode switched on?
    */
   public final boolean isInFailSaveMode()
   {
      return this.clientProblemCallback != null;
      //return recorder != null;
   }


   /**
    * @return is null if not yet supplied
    */
   public ConnectQos getConnectQos() {
      return this.connectQos;
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
    * @deprecated use connect() instead
    */
   public void login(String loginName, String passwd, ConnectQos qos) throws XmlBlasterException
   {
      login(loginName, passwd, qos, null);
   }


   /**
    * Login to the server, using the default BlasterCallback implementation.
    * <p />
    * You need to implement the I_Callback interface, which informs you about arrived
    * messages with its update() method
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
    * @param client    Your implementation of I_Callback, or null if you don't want any.
    * @exception       XmlBlasterException if login fails
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">interface.connect requirement</a>
    * @deprecated use connect() instead
    */
   public synchronized void login(String loginName, String passwd, ConnectQos qos, I_Callback client) throws XmlBlasterException
   {
      this.ME = "XmlBlasterConnection-" + loginName + "-" + getServerNodeId();
      this.updateClient = client;
      if (log.CALL) log.call(ME, "login() ...");

      if (qos == null)
         qos = new ConnectQos(glob);

      // Create default security tags (as specified in xmlBlaster.properties) ...
      initSecuritySettings(null, null);
      I_SecurityQos securityQos = this.secPlgn.getSecurityQos();
      securityQos.setUserId(loginName);
      securityQos.setCredential(passwd);
      qos.setSecurityQos(securityQos);

      connect(qos, client);
   }


   /**
    * Login to the server, specify your own callback and authentication schema
    * in the qos.
    * <p />
    * Note that no asynchronous subscribe() method is available if you don't
    * specify a callback in 'qos'.
    * <p />
    * WARNING: <strong>The qos gets added a <pre>&lt;callback type='IOR'></pre> tag,
    *          so don't use it for a second login, otherwise a second callback is inserted !</strong>
    * <p />
    * Don't forget to authenticate yourself in the qos as well, e.g.
    * with loginName/Password schema:
    * <pre>
    *    &lt;qos>
    *       &lt;securityService type='htpasswd' version='1.0'>
    *          &lt;![CDATA[
    *             &lt;user>aUser&lt;/user>
    *             &lt;passwd>theUsersPwd&lt;/passwd>
    *          ]]>
    *       &lt;/securityService>
    *       &lt;callback type='IOR' sessionId='w0A0364923x4'>
    *          &lt;PtP>true&lt;/PtP>
    *          IOR:00011200070009990000....
    *          &lt;compress type='gzip' minSize='1000' />
    *          &lt;burstMode collectTime='400' />
    *       &lt;/callback>
    *    &lt;/qos>
    * </pre>
    * @param qos       The Quality of Service for this client,
    *                  you have to pass at least the authentication tags
    *                  The callback tag will be added automatically if client!=null
    * @param client    Your client code which implements I_Callback to receive messages via update()
    * @exception       XmlBlasterException if login fails
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">interface.connect requirement</a>
    */
   public ConnectReturnQos connect(ConnectQos qos, I_Callback client) throws XmlBlasterException
   {
      return connect(qos, client, (CbQueueProperty)null, (CallbackAddress)null, (String)null);
   }

   /**
    * Connect to xmlBlaster with your modified callback parameters.
    * <p />
    *
    * @param cbAddr  You can pass your special configured callback attributes here.<br />
    *                We will add the callback address of the here created callback server instance
    * @exception XmlBlasterException On connection problems
    * @see #connect(ConnectQos qos, I_Callback client)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">interface.connect requirement</a>
    */
   public ConnectReturnQos connect(ConnectQos qos, I_Callback client, CallbackAddress cbAddr) throws XmlBlasterException
   {
      return connect(qos, client, (CbQueueProperty)null, cbAddr, (String)null);
   }

   /**
    * Connect to xmlBlaster with your given callback sessionId.
    * <p />
    *
    * @param cbSessionId  You can pass a session ID which is passed to the update method for callback authentication.
    * @exception XmlBlasterException On connection problems
    * @see #connect(ConnectQos qos, I_Callback client)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">interface.connect requirement</a>
    */
   public ConnectReturnQos connect(ConnectQos qos, I_Callback client, String cbSessionId) throws XmlBlasterException
   {
      return connect(qos, client, (CbQueueProperty)null, (CallbackAddress)null, cbSessionId);
   }

   /**
    * Connect to xmlBlaster with your modified queue and callback parameters.
    * <p />
    *
    * @param prop  You can pass your special configured queue attributes here.<br />
    *              We will add the callback address of the here created callback server instance
    * @exception XmlBlasterException On connection problems
    * @see #connect(ConnectQos qos, I_Callback client)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">interface.connect requirement</a>
    */
   public ConnectReturnQos connect(ConnectQos qos, I_Callback client, CbQueueProperty prop) throws XmlBlasterException
   {
      return connect(qos, client, prop, (CallbackAddress)null, (String)null);
   }

   /**
    * Internal connect method, collecting all other connect() variants
    * @see #connect(ConnectQos qos, I_Callback client)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">interface.connect requirement</a>
    */
   private ConnectReturnQos connect(ConnectQos qos, I_Callback client, CbQueueProperty givenProp, CallbackAddress cbAddr, String cbSessionId) throws XmlBlasterException
   {
      if (qos == null) qos = new ConnectQos(glob);

      if (qos.getSecurityPluginType() == null || qos.getSecurityPluginType().length() < 1)
         throw new XmlBlasterException(ME+".Authentication", "Please add your authentication in your login QoS");

      this.connectQos = qos;

      this.ME = "XmlBlasterConnection-" + getMe();
      this.updateClient = client;
      if (log.CALL) log.call(ME, "connect() ...");
      if (log.DUMP) log.dump(ME, "connect() " + (client==null?"with":"without") + " callback qos=\n" + connectQos.toXml());

      Address address = connectQos.getAddress();
      if (this.addressFailSaveSettings != null) { // user called initFailSave(I_ConnectionProblems,,,,) before?
         address.setDelay(addressFailSaveSettings.getDelay());
         address.setRetries(addressFailSaveSettings.getRetries());
         address.setPingInterval(addressFailSaveSettings.getPingInterval());
         //address.setMaxEntries(addressFailSaveSettings.getMaxEntries());
      }

      initDriver(address);

      initBurstMode(address);

      // You should call initFailSave(I_ConnectionProblems) first
      if (this.clientProblemCallback != null && address.getDelay() < 1) {
         address.setDelay(4 * 1000L);
         if (log.TRACE) log.trace(ME, "You have called initFailSave() but -delay is 0, setting ping delay to " + address.getDelay() + " millis");
      }
      if (this.clientProblemCallback == null && address.getDelay() > 0) {
         log.warn(ME, "You have set -delay " + address.getDelay() + ", but not called initFailSave(), using default error recovery on connection problems");
         this.clientProblemCallback = new DummyConnectionProblemHandler(this);
      }

      // Load the client helper to export/import messages:
      initSecuritySettings(connectQos.getSecurityPluginType(), connectQos.getSecurityPluginVersion());

      if (client != null) { // Start a default callback server using same protocol
         this.cbServer = initCbServer(getLoginName(), null);

         // Set all configurable callback parameters, they are added to the connect QoS

         CbQueueProperty prop = givenProp; // Use user supplied property if != null
         if (prop == null)
            prop = connectQos.getSessionCbQueueProperty(); // Creates a default property for us if none is available
         else
            connectQos.setSessionCbQueueProperty(prop);

         CallbackAddress addr = null;
         if (cbAddr != null) {
            addr = cbAddr;
            addr.setType(this.cbServer.getCbProtocol());     // "IOR" "RMI" etc.
            addr.setAddress(this.cbServer.getCbAddress());   // "IOR:0000035656757..." or "rmi:..."
         }
         else if (prop.getCurrentCallbackAddress() != null) { // add the callback data to the user supplied callback attributes
            addr = prop.getCurrentCallbackAddress();
            addr.setType(this.cbServer.getCbProtocol());
            addr.setAddress(this.cbServer.getCbAddress());
         }
         else {
            addr = new CallbackAddress(glob, this.cbServer.getCbProtocol());
            addr.setAddress(this.cbServer.getCbAddress());
         }
         if (cbSessionId != null)
            addr.setSecretSessionId(cbSessionId);

         prop.setCallbackAddress(addr);
         log.info(ME, "Callback settings: " + prop.getSettings());
      } // Callback server configured and running

      this.connectReturnQos = loginRaw();
      return connectReturnQos;
   }

   /**
    * Start the message recording framework.
    * <p />
    * Only if -queue.maxEntries != 0     ( a value < 0 is unlimited)
    * <p />
    */
   private void initFailSave() {
      try {
         if (this.clientProblemCallback != null && this.recorder==null &&
             connectQos.getClientQueueProperty().getMaxEntries() != 0) { // fail save mode (RamRecorder or FileRecorder):

            String type = glob.getProperty().get("recorder.type", (String)null);
            type = glob.getProperty().get("recorder.type["+getServerNodeId()+"]", type);

            String version = glob.getProperty().get("recorder.version", "1.0");
            version = glob.getProperty().get("recorder.version["+getServerNodeId()+"]", version);

            this.recorder = glob.getRecorderPluginManager().getPlugin(type, version, createRecorderFileName(),
                            connectQos.getClientQueueProperty().getMaxEntries(), this, null);

            String mode = glob.getProperty().get("recorder.mode", (String)null);
            mode = glob.getProperty().get("recorder.mode["+getServerNodeId()+"]", mode);
            if (mode != null)
              this.recorder.setMode(mode);

            log.info(ME, "Activated fail save mode: " + connectQos.getAddress().getSettings());
         }
      } catch (XmlBlasterException e) {
         log.error(ME, "Fail save message recorder problem: " + e.toString());
      }
   }

   /**
    * Generate a unique name for this connection tail back messages.
    * <p />
    * It must be unique but should not be arbitrary, to allow a crashed client
    * to find the file again. You should specify it in such a case yourself:<br />
    * -
    * See the <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/util.recorder.html">util.recorder</a> requirement
    * see org.xmlBlaster.util.recorder.file.FileRecorder#createPathString(String)
    */
   private String createRecorderFileName() {
      String fn = null;
      fn = glob.getProperty().get("recorder.fn", (String)null);
      fn = glob.getProperty().get("recorder.fn["+glob.getId()+"]", fn);
      if (fn != null) {
         if (log.TRACE) log.trace(ME, "Using given message recorder file name '" + fn + "'.");
         return fn;
      }

      String from = glob.getId();
      String to = getServerNodeId();
      if (from == null || from.equals(to)) {
         String loginName = getLoginName();
         try {
            if (loginName == null && connectQos != null)
               loginName = connectQos.getUserId();
            if (this.connectReturnQos != null)
               loginName += this.connectReturnQos.getSessionName().getLoginName();
            else {
               synchronized (this) {
                  loginName += (recorderCounter++);
               }
            }
         } catch (Throwable e) {
            loginName = "unknown";
         }

         fn = "tailback-" + loginName + "-to-" + to + ".frc";
         if (log.TRACE) log.trace(ME, "Using message recorder file name '" + fn + "', generated for normal clients");
         return fn;
      }
      else {
         // When we are a client in a cluster node, there is only exactly one login session,
         // the file <clientNodeId>-to-<serverNodeId>.frc is unique
         fn = "tailback-" + from + "-to-" + to + ".frc";
         if (log.TRACE) log.trace(ME, "Using message recorder file name '" + fn + "', generated for cluster clients");
         return fn;
      }
   }

   /**
    * Access the returned connection QoS form xmlBlaster
    * @return null if not logged in
    */
   public ConnectReturnQos getConnectReturnQos() {
      return this.connectReturnQos;
   }

   /**
    * Load the desired protocol driver like CORBA or RMI driver.
    * <p />
    * TODO: Instantiate driver from xmlBlaster.properties instead of doing it hardcoded
    *
    * @param driverType E.g. "IOR" or "RMI", if null we look into the environment "-client.cbProtocol IOR"
    *                   if not specified as well we use the same protocol as our client access (corba is default).
    */
   public I_CallbackServer initCbServer(String loginName, String driverType) throws XmlBlasterException
   {
      if (driverType == null) driverType = glob.getProperty().get("client.cbProtocol", driver.getProtocol());
      if (log.TRACE) log.trace(ME, "Using 'client.cbProtocol=" + driverType + "' to be used by " + getServerNodeId() + ", trying to create the callback server ...");

      try {
         if (driverType.equalsIgnoreCase("SOCKET")) {
            I_CallbackServer server = new SocketCallbackImpl();
            server.initialize(this.glob, loginName, this);
            return server;
         }
         else if (driverType.equalsIgnoreCase("IOR") || driverType.equalsIgnoreCase("IIOP")) {
            I_CallbackServer server = new CorbaCallbackServer();
            server.initialize(this.glob, loginName, this);
            return server;
         }
         else if (driverType.equalsIgnoreCase("RMI")) {
            I_CallbackServer server = new RmiCallbackServer();
            server.initialize(this.glob, loginName, this);
            return server;
         }
         else if (driverType.equalsIgnoreCase("XML-RPC") || driverType.equalsIgnoreCase("XMLRPC")) {
            I_CallbackServer server = new XmlRpcCallbackServer();
            server.initialize(this.glob, loginName, this);
            return server;
         }
//         else if (driverType.equalsIgnoreCase("SOAP")) {
//            I_CallbackServer server = new SoapCallbackServer();
//            server.initialize(this.glob, loginName, this);
//            return server;
//         }
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         if (log.TRACE) log.trace(ME, "Creation of CallbackServer failed: " + e.toString());
         //e.printStackTrace();
         throw new XmlBlasterException(ME, "Creation of CallbackServer failed: " + e.toString());
      }
      String text = "Unknown driverType '" + driverType + "' to install xmlBlaster callback server.";
      log.error(ME, text);
      throw new XmlBlasterException(ME+".UnknownDriver", text);
   }

   /**
    * Login to the server.
    * <p />
    * For internal use only.
    * The qos needs to be set up correctly if you wish a callback
    * @return The returned QoS on success, null in fail save mode without connection,
    *         otherwise an exception is thrown
    * @exception       XmlBlasterException if login fails without fails save mode
    */
   private ConnectReturnQos loginRaw() throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "loginRaw(firstConnect=" + firstConnect + ") ...");
      try {
         if (firstConnect) {
            this.driver.connectLowlevel(connectQos.getAddress());
            if (log.TRACE) log.trace(ME, "loginRaw: connectQos=" + connectQos.toXml());
            String tmp = this.driver.connect(connectQos.toXml());
            this.connectReturnQos = new ConnectReturnQos(glob, tmp);
            firstConnect = false;
            if (log.DUMP) log.dump(ME, "connectReturnQos=" + connectReturnQos.toXml());
            initFailSave();
            if (this.connectReturnQos != null) {
               log.info(ME, "Connected to " + getServerNodeId() + " as " + this.connectReturnQos.getSessionName());
            }
         }
         else {
            String tmp = this.driver.connect(connectQos.toXml());
            this.connectReturnQos = new ConnectReturnQos(glob, tmp);
         }
         numLogins++;

         if (this.connectReturnQos != null) {
            // Remember sessionId for reconnects ...
            this.connectQos.getSessionQos().setSecretSessionId(this.connectReturnQos.getSecretSessionId());
            this.connectQos.getSessionQos().setSessionName(this.connectReturnQos.getSessionName());
         }

         if (log.TRACE) log.trace(ME, "Successful login to " + getServerNodeId());

         if (isReconnectPolling) {
            clientProblemCallback.reConnected();
         }

         startPinging();
         return this.connectReturnQos;

      } catch(XmlBlasterException e) {
         if (e.isCommunication()) {
            if (log.TRACE) log.trace(ME, "Login to " + getServerNodeId() + " failed, numLogins=" + numLogins + ".");
            if (log.DUMP) log.dump(ME, "Login failed, authentication string is\n" + connectQos.toXml());
            if (firstConnect) {
               initFailSave();
            }
      
            if (isInFailSaveMode()) {
               doLoginPolling();
               return null;
            }
            else
               throw e;
         }
         else {
            throw e;
         }
      }
   }


   /**
    * If we lost the connection to xmlBlaster, handle it
    * @exception ErrorCode.COMMUNICATION_NOCONNECTION_DEAD if we give up to connect<br />
    *            ErrorCode.COMMUNICATION_NOCONNECTION_POLLING if we are in fail save mode and polling for a connection and have no message recorder installed
    */
   private synchronized void handleConnectionException(XmlBlasterException e) throws XmlBlasterException
   {
      if (noConnect) {// LoginThread tried already and gave up
         if (isInFailSaveMode())
            log.error(ME, "Can't establish connection to " + getServerNodeId() + ", pinging retries = " + retries + " exceeded.");
         else
            log.error(ME, "Can't establish connection to " + getServerNodeId() + ", no fail save mode.");
         e.changeErrorCode(ErrorCode.COMMUNICATION_NOCONNECTION_DEAD);
         throw e;
      }

      if (isInFailSaveMode()) {
         synchronized(pollingMonitor) {
            if (!isReconnectPolling) {
               log.error(ME, "Lost connection to " + getServerNodeId() + " server: " + e.getMessage());
               driver.resetConnection();
               doLoginPolling();
            }
         }
         if (recorder == null) {
            String text = "No connection to " + getServerNodeId() + " server and no message recorder activated, can't handle your request";
            log.warn(ME, text);
            e.changeErrorCode(ErrorCode.COMMUNICATION_NOCONNECTION_POLLING);
            throw e;
         }
      }
      else {
         e.changeErrorCode(ErrorCode.COMMUNICATION_NOCONNECTION_DEAD);
         throw e; // Client may choose to exit
      }

   }


   /**
    * If we lost the connection to xmlBlaster, poll here to reconnect
    */
   private void doLoginPolling()
   {
      if (isInFailSaveMode()) {
         if (isReconnectPolling == false) {
            if (connectQos == null || connectQos.getAddress() == null) {
               Thread.currentThread().dumpStack();
               throw new IllegalArgumentException("Address==null in XmlBlasterConnection");
            }
            log.info(ME, "Going to poll for " + getServerNodeId() + " and queue your messages ...");
            isReconnectPolling = true;
            clientProblemCallback.lostConnection(); // notify client
            loginThread = new LoginThread(this, connectQos.getAddress().getDelay(), retries);
            loginThread.setDaemon(true);
            loginThread.start();
         }
      }
   }


   /**
    * Start a never ending ping thread
    */
   private void startPinging()
   {
      if (this.clientProblemCallback == null) {
         if (log.TRACE) log.trace(ME, "No ping initialized, we are not in fails save mode");
         return;
      }
      if (connectQos.getAddress().getPingInterval() > 0L && pingThread == null) {
         pingThread = new PingThread(this, connectQos.getAddress().getPingInterval());
         pingThread.setDaemon(true);
         pingThread.start();
      }
   }


   /**
    * Create a descriptive ME, for logging only
    * @return "joe-bilbo" in cluster environment otherwise only "joe"
    */
   private final String getMe()
   {
      if (getServerNodeId().equals("xmlBlaster")) {
         return getLoginName();
      }
      return getLoginName() + "-" + getServerNodeId();
   }


   /**
    * Access the login name.
    * @return your login name or null if you are not logged in
    */
   public String getLoginName()
   {
      /*
      if (driver != null) {
         String nm = driver.getLoginName();
         if (nm != null && nm.length() > 0)
            return nm;
      }
      */

      //try {
         if (connectQos != null) {
            String nm = connectQos.getSecurityQos().getUserId();
            if (nm != null && nm.length() > 0)
               return nm;
         }
      //}
      //catch (XmlBlasterException e) {}

      return glob.getId(); // "client?";
   }

   /**
    * @see #disconnect(DisconnectQos)
    * @deprecated Please use disconnect() instead
    */
   public boolean logout()
   {
      if (log.CALL) log.call(ME, "logout() ...");
      return disconnect(new DisconnectQos(glob));
   }

   /**
    * Logout from the server.
    * <p />
    * Flushes pending publishOneway messages if any and destroys low level connection and callback server.
    * @see org.xmlBlaster.client.protocol.XmlBlasterConnection#disconnect(DisconnectQos, boolean, boolean, boolean)
    */
   public boolean disconnect(DisconnectQos qos) {
      return disconnect(qos, true, true, true);
   }

   /**
    * Logout from the server.
    * Depending on your arguments, the callback server is removed as well, releasing all CORBA/RMI/XmlRpc threads.
    * Note that this kills the server ping thread as well (if in fail save mode)
    * @param qos The disconnect quality of service
    * @param flush Flushed pending publishOneway() messages if any
    * @param shutdown shutdown lowlevel connection as well (e.g. CORBA connection)
    * @param shutdownCb shutdown callback server as well (if any was established)
    * @return <code>true</code> successfully logged out<br />
    *         <code>false</code> failure on logout
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.disconnect.html">interface.disconnect requirement</a>
    */
   public synchronized boolean disconnect(DisconnectQos qos, boolean flush, boolean shutdown, boolean shutdownCb)
   {
      if (log.CALL) log.call(ME, "disconnect() ...");

      if (disconnectInProgress) {
         log.warn(ME, "Calling disconnect again is ignored, you are in shutdown progress already");
         return false;
      }

      disconnectInProgress = true;

      killPing();
      killLoginThread();

      if (!isLoggedIn()) {
         log.warn(ME, "You called disconnect() but you are are not logged in, we ignore it.");
         disconnectInProgress = false;
         //Thread.currentThread().dumpStack();
         return false;
      }

      if (recorder != null && recorder.getNumUnread() > 0)
         log.warn(ME, "You called disconnect(). Please note that there are " + recorder.getNumUnread() + " unsent invocations/messages in the queue");

      while (true) {
         String subscriptionId = null;
         synchronized (callbackMap) {
            Set keys = callbackMap.keySet();
            Iterator it = keys.iterator();
            if (it.hasNext()) {
               subscriptionId = (String)it.next();
               it.remove();
            }
            else
               break;
         }
         UnSubscribeKey key = new UnSubscribeKey(glob, subscriptionId);
         try {
            driver.unSubscribe(key.toXml(), "");
         }
         catch(XmlBlasterException e) {
            if (e.isCommunication()) {
               break;
            }
            log.warn(ME+".logout", "Couldn't unsubscribe '" + subscriptionId + "' : " + e.toString());
         }
      }

      synchronized (callbackMap) {
         callbackMap.clear();
      }

      if (this.publishOnewayTimer != null) {
         if (flush) {
            flushPublishOnewaySet();
         }
      }

      boolean ret = false;
      try {
         ret = driver.disconnect(qos.toXml());
         log.info(ME, "Successful disconnect from " + getServerNodeId());
      } catch(Throwable e) {
         e.printStackTrace();
         log.warn(ME+".disconnect()", e.toString());
      }

      if (shutdown) {
         shutdown(false);
      }

      if (shutdownCb) {
         try {
           shutdownCb();
         } catch (Throwable e) {
            e.printStackTrace();
            log.warn(ME+".disconnect()", e.toString());
         }
      }

      disconnectInProgress = false;
      return ret;
   }

   /**
    * Initialize and start the callback server
    * @param glob if null we use our own Global object
    */
   public void initialize(Global global, String name, I_CallbackExtended client) throws XmlBlasterException
   {
      if (global == null) global = this.glob;

      if (this.cbServer != null)
         this.cbServer.initialize(global, name, client);
   }

   /**
    * Returns the 'well known' protocol type.
    * @return E.g. "RMI", "SOCKET", "XML-RPC" or null if not known
    */
   public String getCbProtocol()
   {
      if (this.cbServer != null)
         return this.cbServer.getCbProtocol();
      return null;
   }

   /**
    * Returns the current callback address.
    * @return "rmi://develop.MarcelRuff.info:1099/xmlBlasterCB", "127.128.2.1:7607", "http://XML-RPC"
    *         or null if not known
    */
   public String getCbAddress() throws XmlBlasterException
   {
      if (this.cbServer != null)
         return this.cbServer.getCbAddress();
      return null;
   }

   /**
    * Shutdown the callback server.
    */
   public void shutdownCb() throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "shutdownCb() ...");
      //Thread.currentThread().dumpStack();
      if (this.cbServer != null)
         this.cbServer.shutdown();
   }

   /**
    * Shut down the server connection.
    * Calls disconnect() first if still connected.
    * If burst mode messages are in the queue, they are flushed.
    *
    * @return true CB server successfully shut down
    *         false failure on shutdown
    */
   public void shutdown()
   {
      shutdown(true);
   }

   /**
    * Shut down the callback server.
    * Calls disconnect() first if still connected.
    *
    * @param flush true: If burst mode messages are in the queue, they are flushed (only if connected)
    *              false: Unsent messages are lost
    * @return true server successfully shut down
    *         false failure on shutdown
    */
   public synchronized boolean shutdown(boolean flush)
   {
      if (log.CALL) log.call(ME, "shutdown(" + flush + ") ...");

      killPing();
      killLoginThread();

      try {
         if (isLoggedIn() && !disconnectInProgress) {
            if (this.publishOnewayTimer != null) {
               if (flush) {
                  flushPublishOnewaySet();
               }
            }
            disconnect(new DisconnectQos(glob), flush, false, true);
         }
         driver.shutdown();
         return true;
      } catch(Exception e) {
         log.warn(ME, e.toString());
         e.printStackTrace();
         return false;
      }
   }


   /**
    * @return true if you are logged in.
    */
   public boolean isLoggedIn()
   {
      if (driver == null) return false;
      return driver.isLoggedIn();
   }

   /**
    * @return true if we are polling for the server.
    */
   public boolean isPolling()
   {
      return isReconnectPolling;
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message.
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public final String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException
   {
      //The boss should not be interested in cache updates
      if (log.CALL) log.call(ME, "Entering update(" + ((cache != null) ? "using cache" : "no cache") + ") ...");
      boolean forCache = false;
      if( cache != null ) {
         forCache = cache.update(updateQos.getSubscriptionId(), updateKey, content, updateQos);
      }
      if (!forCache) {

         Object obj = null;
         synchronized (callbackMap) {
            obj = callbackMap.get(updateQos.getSubscriptionId());
         }

         if (obj != null) {
            // If a special callback was specified for this subscription:
            I_Callback cb = (I_Callback)obj;
            return cb.update(cbSessionId, updateKey, content, updateQos); // deliver the update to our client
         }
         else if (updateClient != null) {
            // If a general callback was specified on login:
            return updateClient.update(cbSessionId, updateKey, content, updateQos); // deliver the update to our client
         }

      }

      return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
   }

   /*
    * The oneway variant without a return value or exception
    */
   /*
   public final void updateOneway(String cbSessionId, String updateKeyLiteral, byte[] content, String updateQosLiteral)
   {
      // currently mapped in AbstractCallbackExtended to String update()
   }
   */

   private final SubscribeReturnQos subscribeRaw(String xmlKey, String qos) throws XmlBlasterException, XmlBlasterException, IllegalArgumentException
   {
      if (qos==null) qos = "";
      if (xmlKey==null) throw new IllegalArgumentException("Please provide a valid XmlKey for subscribe()");
      String xmlRet;
      if (secPlgn!=null) { // with security Plugin: interceptor
         xmlRet = secPlgn.importMessage(
                     driver.subscribe(secPlgn.exportMessage(xmlKey),
                                      secPlgn.exportMessage(qos)));
      }
      else { // without security plugin
         xmlRet = driver.subscribe(xmlKey, qos);
      }
      return new SubscribeReturnQos(glob, xmlRet);
   }

   /**
    * This subscribe variant allows to specify a specialized callback
    * for updated messages.
    * <p />
    * Implementing for every subscription a callback, you don't need to
    * dispatch updates when they are received in one central
    * update method.
    * <p />
    * Example:<br />
    * <pre>
    *   XmlBlasterConnection con = ...   // login etc.
    *   ...
    *   SubscribeKey key = new SubscribeKey(glob, "//stock", "XPATH");
    *   SubscribeQos qos = new SubscribeQos(glob);
    *   try {
    *      con.subscribe(key.toXml(), qos.toXml(), new I_Callback() {
    *            public String update(String name, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
    *               System.out.println("Receiving message for '//stock' subscription ...");
    *               return "";
    *            }
    *         });
    *   } catch(XmlBlasterException e) {
    *      System.out.println(e.getMessage());
    *   }
    * </pre>
    * <p />
    * NOTE: You need to pass a callback handle on login as well (even if you
    * never use it). It allows to setup the callback server and is the
    * default callback deliver channel.
    * <p />
    * NOTE: On logout we automatically unSubscribe() this subscription
    * if not done before.
    * @param cb      Your callback handling implementation
    * @return oid    A unique subscription Id<br>
    *                If you subscribed using a query, the subscription ID of this<br>
    *                query handling object (SubscriptionInfo.getUniqueKey()) is returned.<br>
    *                You should use this ID if you wish to unSubscribe()<br>
    *                If no match is found, an empty string "" is returned.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">interface.subscribe requirement</a>
    */
   public final SubscribeReturnQos subscribe(String xmlKey, String qos, I_Callback cb) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "subscribe(with callback) ...");
      if (driver==null) throw new XmlBlasterException(ME, "Sorry no connection to " + getServerNodeId() + ", subscribe() failed.");
      if (updateClient == null) {
         String text = "No callback server is incarnated. " +
                       " Please use XmlBlasterConnection - constructor with default I_Callback given.";
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_DEAD, ME, text);
      }
      SubscribeReturnQos subscribeQos = null;
      synchronized (callbackMap) {
         subscribeQos = subscribe(xmlKey, qos);
         callbackMap.put(subscribeQos.getSubscriptionId(), cb);
      }
      return subscribeQos;
   }

   /**
    * Enforced by I_XmlBlaster interface (fail save mode).
    * see explanations of publish() method.
    * @return oid    The oid of your subscribed Message<br>
    *                If you subscribed using a query, the subscription ID of this<br>
    *                query handling object (SubscriptionInfo.getUniqueKey()) is returned.<br>
    *                You should use this oid if you wish to unSubscribe()<br>
    *                If no match is found, an empty string "" is returned.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">interface.subscribe requirement</a>
    */
   public final SubscribeReturnQos subscribe(String xmlKey, String qos) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "subscribe() ...");
      if (log.DUMP) log.dump(ME, "subscribe key=" + xmlKey + "\nsubscribe qos=" + qos);
      try {
         return subscribeRaw(xmlKey, qos);
      } catch(XmlBlasterException e) {
         if (!e.isCommunication())
            throw e;
         SubscribeReturnQos retQos = null;
         if (recorder != null) {
            if (qos == null || qos.indexOf("<id>") == -1) { // Add a subscription ID on client side
               org.xmlBlaster.engine.qos.SubscribeQosServer sq = new org.xmlBlaster.engine.qos.SubscribeQosServer(glob, qos);
               subscribeUniqueCounter++;
               String absoluteName = (this.connectReturnQos!=null) ? this.connectReturnQos.getSessionName().getAbsoluteName() : (String)null;
               if (absoluteName == null) {
                  absoluteName = (this.connectQos!=null) ? ((this.connectQos.getSessionQos().getSessionName() != null) ? this.connectQos.getSessionQos().getSessionName().getAbsoluteName() : (String)null) : (String)null;
               }
               if (absoluteName == null) {
                  absoluteName = getServerNodeId() + "/client/" + getLoginName();
               }
               String subId = Constants.SUBSCRIPTIONID_CLIENT_PREFIX + absoluteName +
                        "/" + subscribeUniqueCounter;
               sq.setSubscriptionId(subId);
               if (log.TRACE) log.trace(ME, "Adding client side subscriptionId='" + subId + "'");
               qos = sq.toXml();
            }
            recorder.subscribe(xmlKey, qos);
            StatusQosData subRetQos = new StatusQosData(glob, MethodName.SUBSCRIBE);
            subRetQos.setStateInfo(getQueuedInfo());
            retQos = new SubscribeReturnQos(glob, subRetQos);
            // TODO: Generate a unique subscritpionId -> pass to client and later to server as well
         }
         handleConnectionException(e);
         if (retQos != null)
            return retQos;
      }
      throw new XmlBlasterException(ME, "Internal problem in subscribe()"); // never reached, there is always an exception thrown
   }

   private final String getQueuedInfo()
   {
      return Constants.INFO_QUEUED+"["+getServerNodeId()+"]";
      // Probably change to only add the server node id for cluster clients
   }


   /**
    * Enforced by I_XmlBlaster interface (fail save mode)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html">interface.unSubscribe requirement</a>
    */
   public final UnSubscribeReturnQos[] unSubscribe(String xmlKey, String qos) throws XmlBlasterException, IllegalArgumentException
   {
      if (log.CALL) log.call(ME, "unSubscribe() ...");
      if (qos==null) qos = "";
      if (xmlKey==null) throw new IllegalArgumentException("Please provide a valid XmlKey for unSubscribe()");
      if (driver==null) throw new XmlBlasterException(ME, "Sorry no connection to " + getServerNodeId() + ", unSubscribe() failed.");
      try {
         String[] arr;
         if (secPlgn!=null) { // with security Plugin
            arr = driver.unSubscribe(secPlgn.exportMessage(xmlKey), secPlgn.exportMessage(qos));
         }
         else { // without security plugin
            arr = driver.unSubscribe(xmlKey, qos);
         }
         synchronized (callbackMap) {
            UpdateKey key = new UpdateKey(glob, xmlKey); // has a SAX parser for xml key
            callbackMap.remove(key.getOid());
         }
         UnSubscribeReturnQos[] qosArr = new UnSubscribeReturnQos[arr.length];
         for (int ii=0; ii<qosArr.length; ii++)
            qosArr[ii] = new UnSubscribeReturnQos(glob, arr[ii]);
         return qosArr;
      } catch(XmlBlasterException e) {
         if (e.isCommunication()) {
            if (recorder != null) recorder.unSubscribe(xmlKey, qos);
            handleConnectionException(e);
         }
         else
            throw e;
      }
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "Internal problem in unSubscribe()"); // never reached, there is always an exception thrown
   }


   /**
    * Publish fault-tolerant the given message.
    * <p />
    * This is a wrapper around the raw CORBA publish() method
    * If the server disappears you get an exception.
    * This call will not block.
    * <p />
    * Enforced by I_XmlBlaster interface (fail save mode)
    * <p />
    * See private method handleConnectionException(XmlBlasterException)
    *
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">interface.publish requirement</a>
    * @exception ErrorCode.COMMUNICATION_NOCONNECTION_DEAD if we give up to connect<br />
    *            ErrorCode.COMMUNICATION_NOCONNECTION_POLLING if we are in fail save mode and polling for a connection and have no message recorder installed
    */
   public final PublishReturnQos publish(MsgUnit msgUnit) throws XmlBlasterException
   {
      if (log.TRACE) log.trace(ME, "Publishing ...");
      if (driver==null) throw new XmlBlasterException(ME, "Sorry no connection to " + getServerNodeId() + ", publish() failed.");
      try {
         if (secPlgn!=null) {
            MsgUnitRaw mu = secPlgn.exportMessage(msgUnit.getMsgUnitRaw());
            return new PublishReturnQos(glob, secPlgn.importMessage(driver.publish(mu)));
         }
         else {
            return new PublishReturnQos(glob, driver.publish(msgUnit.getMsgUnitRaw()));
         }
      } catch(XmlBlasterException e) {
         if (e.isCommunication()) {
            PublishReturnQos retQos = null;
            if (recorder != null) {
               String oid = getAndReplaceOid(msgUnit);
               recorder.publish(msgUnit);
               StatusQosData statRetQos = new StatusQosData(glob, MethodName.PUBLISH);
               statRetQos.setStateInfo(Constants.INFO_QUEUED);
               statRetQos.setKeyOid(oid);
               retQos = new PublishReturnQos(glob, statRetQos);
            }
            handleConnectionException(e);
            if (retQos != null)
               return retQos;
         }
         else {
            if (log.TRACE) log.trace(ME, "XmlBlasterException: " + e.getMessage());
            throw e;
         }
      }
      return new PublishReturnQos(glob, (String)null); // never reached, there is always an exception thrown
   }

   /** Extract the key oid, if none is there insert one */
   private String getAndReplaceOid(MsgUnit msgUnit) {
      String key = msgUnit.getKey();
      String oid = parseOid(key);
      if (oid == null || oid.length() < 1) {
         log.warn(ME, "Generating unknown key oid for message recorder is not implemented");
         //oid = generateKeyOid();
         //generateOidIntoXmlKey();
         //msgUnit.setXmlKey(key);
      }
      return oid;
   }

   /** Extract the oid from a "<key oid='' ..." string */
   private final String parseOid(String str) {
      int keyStart = str.indexOf("<key");
      if (keyStart < 0) return null;
      int keyEnd = str.indexOf(">", keyStart+1);
      if (keyEnd < 0) return null;
      str = str.substring(keyStart, keyEnd);

      String token = "oid=";
      int index = str.indexOf(token);
      if (index >= 0) {
         int from = index+token.length();
         char apo = str.charAt(from);
         int end = str.indexOf(apo, from+1);
         if (end > 0) {
            return str.substring(from+1, end);
         }
      }
      return null;
   }

   /**
    * Generates a unique key.
    * <p />
    * TODO: include IP adress and PID for global uniqueness
    */
   private final String generateKeyOid() {
      StringBuffer oid = new StringBuffer(160);

      String from = glob.getId();
      String to = getServerNodeId();
      if (from == null || from.equals(to)) {
         String loginName = getLoginName();
         if (loginName != null)
            oid.append(loginName);
         try {
            if (loginName == null && connectQos != null)
               oid.append(connectQos.getUserId());
         } catch (Throwable e) {
            oid.append("unknown");
         }
         oid.append(to);
      }
      else {
         oid.append(from).append(to);
      }

      // This is unique in this JVM
      Timestamp t = new Timestamp();
      oid.append(t.getTimestamp());

      return oid.toString();
   }

   /**
    * Enforced by I_XmlBlaster interface (fail save mode)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">interface.publish requirement</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public PublishReturnQos[] publishArr(MsgUnit [] msgUnitArr) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "publishArr() ...");
      if (driver==null) throw new XmlBlasterException(ME, "Sorry no connection to " + getServerNodeId() + ", publishArr() failed.");
      try {
         String[] arr;
         if (secPlgn!=null) { // security plugin allows e.g. crypting of messages ...
            MsgUnitRaw mu[] = exportAll(msgUnitArr);
            String[] result = driver.publishArr(mu);
            arr = importAll(result);
         }
         else { // security plugin not available
            MsgUnitRaw mu[] = new MsgUnitRaw[msgUnitArr.length];
            for(int i=0; i<msgUnitArr.length; i++)
               mu[i] = msgUnitArr[i].getMsgUnitRaw();
            arr = driver.publishArr(mu);
         }
         PublishReturnQos[] qosArr = new PublishReturnQos[arr.length];
         for (int ii=0; ii<qosArr.length; ii++)
            qosArr[ii] = new PublishReturnQos(glob, arr[ii]);
         return qosArr;
      } catch(XmlBlasterException e) {
         if (e.isCommunication()) {
            if (recorder != null) recorder.publishArr(msgUnitArr);
            handleConnectionException(e);
         }
         else {
            if (log.TRACE) log.trace(ME, "XmlBlasterException: " + e.getMessage());
            throw e;
         }
      }
      return new PublishReturnQos[0];
   }

   /**
    * We are notified about the burst mode timeout through this method.
    * @param userData You get bounced back your userData which you passed
    *                 with Timeout.addTimeoutListener()
    */
   public final void timeout(Object userData)
   {
      synchronized(this.publishOnewayTimer) {
         this.publishOnewayTimerKey = null;
         flushPublishOnewaySet();
      }
   }

   /**
    * Flush tailed back burst mode messages to xmlBlaster.
    */
   public final void flushPublishOnewaySet()
   {
      synchronized(this.publishOnewayTimer) { // sync to allow outside caller
         if (this.publishOnewayTimerKey != null) {
            this.publishOnewayTimer.removeTimeoutListener(this.publishOnewayTimerKey);
            this.publishOnewayTimerKey = null;
         }
         MsgUnit[] msgUnitArr = new MsgUnit[this.publishOnewayBurstModeVec.size()];
         Iterator it = this.publishOnewayBurstModeVec.iterator();
         for (int i=0; it.hasNext(); i++) {
            msgUnitArr[i] = (MsgUnit)it.next();
         }
         this.publishOnewayBurstModeVec.clear();
         log.info(ME+".flushPublishOnewaySet()", "Burst mode timeout after " + this.publishOnewayCollectTime + " millis occurred, publishing " + msgUnitArr.length + " oneway messages ...");
         publishOneway(msgUnitArr, true);
      }
   }

   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public void publishOneway(MsgUnit[] msgUnitArr)
   {
      publishOneway(msgUnitArr, false);
   }

   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   private void publishOneway(MsgUnit[] msgUnitArr, boolean flushBurstMode)
   {
      if (log.CALL) log.call(ME, "publishOneway() ...");
      if (driver==null) {
         log.error(ME, "Sorry no connection to " + getServerNodeId() + ", publishOneway() failed.");
         return;
      }
      try {
         if (this.publishOnewayCollectTime > 0L && !flushBurstMode) {
            synchronized(this.publishOnewayTimer) {
               //log.info(ME, "publishOneway() adding set ...");
               for (int ii = 0; ii<msgUnitArr.length; ii++) {
                  this.publishOnewayBurstModeVec.addElement(msgUnitArr[ii]);
               }
               if (this.publishOnewayTimerKey == null) {
                  if (log.TRACE) log.trace(ME, "Spanning timer = " +  this.publishOnewayCollectTime);
                  this.publishOnewayTimerKey = this.publishOnewayTimer.addTimeoutListener(this, this.publishOnewayCollectTime, null);
               }
               return;
            }
         }

         MsgUnitRaw[] mu = null;
         if (secPlgn!=null) { // security plugin allows e.g. crypting of messages ...
            mu = exportAll(msgUnitArr);
         }
         else {
            mu = new MsgUnitRaw[msgUnitArr.length];
            for(int i=0; i<msgUnitArr.length; i++)
               mu[i] = msgUnitArr[i].getMsgUnitRaw();
         }
         driver.publishOneway(mu);
      } catch(XmlBlasterException e) {
         if (e.isCommunication()) {
            try {
               if (recorder != null) recorder.publishArr(msgUnitArr);
               handleConnectionException(e);
            } catch(XmlBlasterException e2) {
               log.error(ME, "XmlBlasterException is not forwarded to client, we are in 'oneway' mode: " + e2.getMessage());
            }
         }
         else {
            log.error(ME, "XmlBlasterException is not forwarded to client, we are in 'oneway' mode: " + e.getMessage());
         }
      }
   }

   /**
    * Enforced by I_XmlBlaster interface (fail save mode)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.erase.html">interface.erase requirement</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public final EraseReturnQos[] erase(String xmlKey, String qos) throws XmlBlasterException, IllegalArgumentException
   {
      if (log.CALL) log.call(ME, "erase() ...");
      if (qos==null) qos = "";
      if (xmlKey==null) throw new IllegalArgumentException("Please provide a valid XmlKey for erase()");
      if (driver==null) throw new XmlBlasterException(ME, "Sorry no connection to " + getServerNodeId() + ", erase() failed.");
      try {
         String[] arr;
         if (secPlgn!=null) {
            String[] result = driver.erase(secPlgn.exportMessage(xmlKey),
                                           secPlgn.exportMessage(qos));
            arr = importAll(result);
         }
         else {
            arr = driver.erase(xmlKey, qos);
         }
         EraseReturnQos[] qosArr = new EraseReturnQos[arr.length];
         for (int ii=0; ii<qosArr.length; ii++)
            qosArr[ii] = new EraseReturnQos(glob, arr[ii]);
         return qosArr;
      } catch(XmlBlasterException e) {
         if (e.isCommunication()) {
            EraseReturnQos retQos = null;
            if (recorder != null) {
               recorder.erase(xmlKey, qos);
               StatusQosData statRetQos = new StatusQosData(glob, MethodName.ERASE);
               statRetQos.setStateInfo(Constants.INFO_QUEUED);
               retQos = new EraseReturnQos(glob, statRetQos);
            }
            handleConnectionException(e);
            if (retQos != null) {
               EraseReturnQos[] qosArr = new EraseReturnQos[1];
               qosArr[0] = retQos;
               return qosArr; // Problem: We don't know which messages are deleted
            }
         }
         else {
            if (log.TRACE) log.trace(ME, "XmlBlasterException: " + e.getMessage());
            throw e;
         }
      }
      return new EraseReturnQos[0];
   }


   private final MsgUnit[] getRaw(String xmlKey, String qos) throws XmlBlasterException
   {
      MsgUnitRaw[] units = null;
      if (secPlgn!=null) {
         units = importAll(driver.get(secPlgn.exportMessage(xmlKey),
                                      secPlgn.exportMessage(qos)));
      }
      else {
         units = driver.get(xmlKey, qos);
      }
      if (units == null) {
         return null;
      }
      MsgUnit[] msgUnitArr = new MsgUnit[units.length];
      for(int i=0; i<units.length; i++) {
         msgUnitArr[i] = new MsgUnit(units[i].getKey(), units[i].getContent(), units[i].getQos());
      }
      return msgUnitArr;
   }


   /**
    * Enforced by I_XmlBlaster interface (fail save mode)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">interface.get requirement</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public final MsgUnit[] get(String xmlKey, String qos) throws XmlBlasterException, IllegalArgumentException
   {
      MsgUnit[] units = null;
      if (log.CALL) log.call(ME, "get() ...");
      if (qos==null) qos = "";
      if (xmlKey==null) throw new IllegalArgumentException("Please provide a valid XmlKey for get()");
      if (driver==null) throw new XmlBlasterException(ME, "Sorry no connection to " + getServerNodeId() + ", get() failed.");
      try {
         //Is cache installed?
         if (cache != null) {
            units = cache.get( xmlKey, qos );
            //not found in cache
            if( units == null ) {
               units = getRaw(xmlKey, qos);              //get messages from xmlBlaster (synchronous)
               SubscribeReturnQos subId = subscribeRaw(xmlKey, qos); //subscribe to this messages (asynchronous)
               cache.newEntry(subId.getSubscriptionId(), xmlKey, units);     //fill messages to cache
               log.info(ME, "New entry in cache created (subId="+subId.getSubscriptionId()+")");
            }
         }
         else {
            units = getRaw(xmlKey, qos);
         }
         return units;
      } catch(XmlBlasterException e) {
         if (e.isCommunication()) {
            if (recorder != null) recorder.get(xmlKey, qos);
            e.printStackTrace();
            handleConnectionException(e);
         }
         else {
            if (log.TRACE) log.trace(ME, "XmlBlasterException: " + e.getMessage());
            throw e;
         }
      }
      return dummyMArr;
   }


   private String[] importAll(String[] contentArr) throws XmlBlasterException
   {
      String tmp[] = new String[contentArr.length];
      for(int i=0; i<contentArr.length; i++) {
        tmp[i]=secPlgn.importMessage(contentArr[i]);
      }
      return tmp;
   }

   private String[] exportAll(String[] contentArr) throws XmlBlasterException
   {
      String tmp[] = new String[contentArr.length];
      for(int i=0; i<contentArr.length; i++) {
        tmp[i]=secPlgn.importMessage(contentArr[i]);
      }
      return tmp;
   }

   private MsgUnitRaw[] importAll(MsgUnitRaw[] msgUnitArr) throws XmlBlasterException
   {
      MsgUnitRaw mu[] = new MsgUnitRaw[msgUnitArr.length];
      for(int i=0; i<msgUnitArr.length; i++) {
        mu[i]=secPlgn.importMessage(msgUnitArr[i]);
      }
      return mu;
   }

   private MsgUnitRaw[] exportAll(MsgUnit[] msgUnitArr) throws XmlBlasterException
   {
      MsgUnitRaw mu[] = new MsgUnitRaw[msgUnitArr.length];
      for(int i=0; i<msgUnitArr.length; i++) {
        mu[i]=secPlgn.exportMessage(msgUnitArr[i].getMsgUnitRaw());
      }
      return mu;
   }

   /**
    * Check server.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public void ping()
   {
      if (isReconnectPolling)
         return;
      try {
         driver.ping("");
         if (log.CALL) log.call(ME, "ping success() ...");
         return;
      } catch(XmlBlasterException e) {
         if (e.isCommunication()) {
            if (log.TRACE) log.trace(ME, "ping failed, " + getServerNodeId() + " seems to be down, try to reactivate connection ...");
            try {
               handleConnectionException(e);
            } catch(XmlBlasterException ep) {
               if (log.TRACE) log.trace(ME, "Exception in ping! " + ep.getMessage());
            }
         }
         else {
            log.error(ME, "ping failed: " + e.toString());
         }
      }
      return ; // never reached, there is always an exception thrown
   }

   public int queueSize()
   {
      if (recorder == null) {
         log.warn(ME, "Internal error: don't call queueSize(), you are not in fail save mode");
         return 0;
      }

      return (int)recorder.getNumUnread();
   }

   /**
    * You can access the internal invocation recorder handle and
    * do all what the interface I_InvocationRecorder allows you to do.
    * You should know what you are doing.
    * @return The invocation recorder if you have copnfigured to use one or null
    */
   public final I_InvocationRecorder getRecorder()
   {
      return this.recorder;
   }

   public final void flushQueue() throws XmlBlasterException
   {
      if (recorder == null) {
         log.warn(ME, "Internal error: don't call flushQueue(), you are not in fail save mode");
         return;
      }

      float msgPerSec = glob.getProperty().get("recorder.rate", -1.0f);
      msgPerSec = glob.getProperty().get("recorder.type["+getServerNodeId()+"]", msgPerSec);

      //recorder.pullback(0L, 0L, 0.);
      recorder.pullback(msgPerSec);
   }


   public void resetQueue()
   {
      if (recorder == null) {
         log.warn(ME, "Internal error: don't call resetQueue(), you are not in fail save mode");
         return;
      }

      recorder.destroy();
   }


   public void shutdownQueue()
   {
      if (recorder == null) {
         log.warn(ME, "Internal error: don't call shutdownQueue(), you are not in fail save mode");
         return;
      }

      recorder.shutdown();
   }


   /**
    * Poll for xmlBlaster server
    */
   private class LoginThread extends Thread
   {
      private final String ME;
      private XmlBlasterConnection con;
      private final long RETRY_INTERVAL; // would this be smarter? glob.getProperty().get("Failsave.retryInterval", 4000L);
      private final int RETRIES;         // -1 = forever
      private int counter = 0;
      private int logInterval = 10;
      boolean pollRunning = true;


      /**
       * @param retryInterval How many milli seconds sleeping before we retry a connection
       * @param retries Number of retries if connection cannot directly be established
       */
      LoginThread(XmlBlasterConnection con, long retryInterval, int retries) {
         super("LoginThread-UNSET");
         this.con = con;
         this.ME = "LoginThread-" + getMe();
         this.RETRY_INTERVAL = retryInterval;
         this.RETRIES = retries;
         long logEveryMillis = 60000; // every minute a log
         if (retryInterval < 1 || retryInterval > logEveryMillis)  // millisec
            logInterval = 1;
         else
            logInterval = (int)(logEveryMillis / retryInterval);
         if (log.CALL) log.call(ME, "Entering constructor retry delay=" + retryInterval + " millis and retries=" + retries);
      }

      public void run() {
         log.info(ME, "Polling for " + getServerNodeId() + " server every " + RETRY_INTERVAL + " millis " + ((RETRIES==-1) ? "forever." : ("" + retries + " times.")));
         con.isReconnectPolling = true;
         for (int ii=0; ii<RETRIES || RETRIES==-1; ii++) {
            if (pollRunning == false) {
               con.isReconnectPolling = false;
               return;
            }
            try {
               if (con.loginRaw() != null) {
                  log.info(ME, "Success, a new connection to " + getServerNodeId() + " is established after " + counter + " retries.");
                  con.isReconnectPolling = false;
                  return;
               }
               else {
                  if ((counter % logInterval) == 0)
                     log.warn(ME, "No connection established, " + getServerNodeId() + " still seems to be down after " + (counter+1) + " login retries.");
                  counter++;
                  try {
                     Thread.currentThread().sleep(RETRY_INTERVAL);
                  } catch (InterruptedException i) { }
               }
            } catch(Exception e) {
               log.error(ME, "Unexpected exception while login polling: " + e.toString());
               e.printStackTrace();
               if ((counter % logInterval) == 0)
                  log.warn(ME, "No connection established, " + getServerNodeId() + " still seems to be down after " + (counter+1) + " login retries.");
               counter++;
               try {
                  Thread.currentThread().sleep(RETRY_INTERVAL);
               } catch (InterruptedException i) { }
            }
         }
         con.isReconnectPolling = false;
         con.noConnect = true; // communicate back to XmlBlasterConnection that we give up
         log.info(ME, "max polling for " + getServerNodeId() + " server done, no success");
      }
   } // class LoginThread


   /**
    * Ping the xmlBlaster server, to test if connection is alive
    */
   private class PingThread extends Thread
   {
      private final String ME;
      private XmlBlasterConnection con;
      private final long PING_INTERVAL;
      boolean pingRunning = true;

      /**
       * @param pingInterval How many milli seconds sleeping between the pings
       */
      PingThread(XmlBlasterConnection con, long pingInterval) {
         super("PingThread-UNSET");
         this.con = con;
         this.ME = "PingThread-" + getMe();
         this.PING_INTERVAL = pingInterval;
         if (log.CALL) log.call(ME, "Entering constructor ping interval=" + pingInterval + " millis");
      }
      public void run() {
         log.info(ME, "Pinging " + getServerNodeId() + " server every " + PING_INTERVAL + " millis.");
         while (pingRunning) {
            try {
               con.ping();
            } catch(Exception e) {
            }
            try {
               Thread.currentThread().sleep(PING_INTERVAL);
            } catch (InterruptedException i) { }
         }
      }
   } // class PingThread

   /**
    * This class implements connection problems with xmlBlaster.
    * <p />
    * It is a dummy implementation.
    * You can use this as fail save handling. Switch it on with command
    * line argument
    * <pre>
    *   java javaclients.ClientSub -delay 5000
    * </pre>
    * NOTE: Usually you should provide these two methods yourself,
    * and initialize your subscriptions etc. again.
    * This dummy won't do it.
    */
   private class DummyConnectionProblemHandler implements I_ConnectionProblems
   {
      private final String ME = "DummyConnectionProblemHandler";
      private XmlBlasterConnection xmlBlasterConnection = null;

      DummyConnectionProblemHandler(XmlBlasterConnection con)
      {
         this.xmlBlasterConnection = con;
      }

      /**
       * This is the callback method invoked from XmlBlasterConnection
       * informing the client in an asynchronous mode if the connection was lost.
       * <p />
       * This method is enforced through interface I_ConnectionProblems
       * <p />
       * We do nothing here, only logging the situation ...
       */
      public void lostConnection()
      {
         log.warn(ME, "I_ConnectionProblems: Lost connection to " + getServerNodeId() + "");
      }

      /**
       * This is the callback method invoked from XmlBlasterConnection
       * informing the client in an asynchronous mode if the connection was established.
       * <p />
       * This method is enforced through interface I_ConnectionProblems
       * <p />
       * We will send all undelivered messages to xmlBlaster
       */
      public void reConnected()
      {
         try {
            if (xmlBlasterConnection.queueSize() > 0) {
               log.info(ME, "We were lucky, reconnected to " + getServerNodeId() + ", sending " + xmlBlasterConnection.queueSize() + " tailback messages ...");
               xmlBlasterConnection.flushQueue();
            }
            else
               log.info(ME, "We were lucky, reconnected to " + getServerNodeId() + ", no tailback messages to flush");
         }
         catch (XmlBlasterException e) {
            log.error(ME, "Sorry, flushing of tailback messages failed, they are lost: " + e.toString());
         }
      }
   }

   /**
    * Command line usage.
    */
   public static void usage()
   {
      Global glob = Global.instance();
      String text = "\n";
      text += "Choose a connection protocol:\n";
      text += "   -dispatch/clientSide/protocol    Specify a protocol to talk with xmlBlaster, 'SOCKET' or 'IOR' or 'RMI' or 'SOAP' or 'XML-RPC'.\n";
      text += "                       Current setting is '" + glob.getProperty().get("client.protocol", "IOR") + "'. See below for protocol settings.\n";
      text += "                       Example: java MyApp -dispatch/clientSide/protocol RMI -rmi.hostname 192.168.10.34\n";
      text += "\n";
      text += "Security features:\n";
      text += "   -Security.Client.DefaultPlugin \"gui,1.0\"\n";
      text += "                       Force the given authentication schema, here the GUI is enforced\n";
      text += "                       Clients can overwrite this with ConnectQos.java\n";

      LogChannel log = glob.getLog(null);
      log.plain("",text);
      //try {
         log.plain("",new ConnectQos(glob).usage());
      //} catch (XmlBlasterException e) {}
      log.plain("",new Address(glob).usage());
      log.plain("",new ClientQueueProperty(glob,null).usage());
      log.plain("",new CallbackAddress(glob).usage());
      log.plain("",new CbQueueProperty(glob,null,null).usage());
      log.plain("",SocketConnection.usage());
      log.plain("",CorbaConnection.usage());
      log.plain("",RmiConnection.usage());
      log.plain("",XmlRpcConnection.usage());
      log.plain("",Global.instance().usage()); // for LogChannel help
   }

} // class XmlBlasterConnection
