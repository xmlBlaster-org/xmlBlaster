/*------------------------------------------------------------------------------
Name:      XmlBlasterConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP/RMI or XML-RPC
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.jutils.JUtilsException;

import org.xmlBlaster.client.protocol.corba.CorbaConnection;
import org.xmlBlaster.client.protocol.corba.CorbaCallbackServer;
import org.xmlBlaster.client.protocol.rmi.RmiConnection;
import org.xmlBlaster.client.protocol.rmi.RmiCallbackServer;
import org.xmlBlaster.client.protocol.xmlrpc.XmlRpcConnection;
import org.xmlBlaster.client.protocol.xmlrpc.XmlRpcCallbackServer;
import org.xmlBlaster.client.protocol.socket.SocketConnection;
import org.xmlBlaster.client.protocol.socket.SocketCallbackImpl;

import org.xmlBlaster.client.BlasterCache;
import org.xmlBlaster.client.PluginLoader;
import org.xmlBlaster.client.I_ConnectionProblems;
import org.xmlBlaster.client.I_CallbackRaw;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.KeyWrapper;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.I_InvocationRecorder;
import org.xmlBlaster.util.InvocationRecorder;
import org.xmlBlaster.engine.helper.Address;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.QueueProperty;
import org.xmlBlaster.engine.helper.CbQueueProperty;
import org.xmlBlaster.engine.helper.MessageUnit;
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
 * using IIOP (CORBA), RMI, XML-RPC, SOCKET or any other supported protocol.
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
 * You can have a look at xmlBlaster/testsuite/org/xmlBlaster/TestFailSave.java to find out how it works
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
 * @see testsuite.org.xmlBlaster.TestFailSave
 *
 * @author $Author: ruff $
 */
public class XmlBlasterConnection extends AbstractCallbackExtended implements I_InvocationRecorder, I_CallbackServer, I_Timeout
{
   private String ME = "XmlBlasterConnection";
   // protected Global glob = null; see AbstractCallbackExtended

   /** The cluster node id (name) to which we want to connect, needed for nicer logging, can be null */
   protected String serverNodeId = "xmlBlaster";

   private boolean disconnectInProgress = false;

   /** The driver, e.g. Corba/Rmi/XmlRpc */
   private I_XmlBlasterConnection driver = null;
   /** The callback server, e.g. Corba/Rmi/XmlRpc */
   private I_CallbackServer cbServer = null;

   /** Holding the current returned QoS from the connect() call */
   private ConnectReturnQos connectReturnQos = null;

   /** queue all the messages, and play them back through interface I_InvocationRecorder */
   private InvocationRecorder recorder = null;

   /** This interface needs to be implemented by the client in fail save mode
       The client gets notified about abnormal connection loss or reconnect */
   private I_ConnectionProblems clientProblemCallback = null;

   /** Used to callback the clients update() method */
   private I_Callback updateClient = null;

   /** true if we are in fails save mode and polling for xmlBlaster */
   private boolean isReconnectPolling = false;

   /** Store the connection configuration and parameters */
   private ConnectQos connectQos = null;

   /** Number of retries if connection cannot directly be established */
   private int retries = -1;

   /** communicate from LoginThread back to CorbaConnection that we give up */
   private boolean noConnect = false;

   /** Handle on the ever running ping thread.Only switched on in fail save mode */
   private PingThread pingThread = null;

   /** Remember the number of successful logins */
   private long numLogins = 0L;

   private MessageUnit[] dummyMArr = new MessageUnit[0];
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
    * java ... -client.protocol SOCKET
    * java ... -client.protocol RMI
    * java ... -client.protocol IOR
    * java ... -client.protocol XML-RPC
    * </pre>
    */
   public XmlBlasterConnection() throws XmlBlasterException {
      initArgs(null);
   }

   /**
    * Client access to xmlBlaster for client applications.
    * <p />
    * This constructor defaults to CORBA (IOR driver) communication to the server.
    * <p />
    * You can choose the protocol with this command line option: <br />
    * <pre>
    * java ... -client.protocol RMI
    * java ... -client.protocol IOR
    * java ... -client.protocol XML-RPC
    * java ... -client.protocol SOCKET
    * </pre>
    * @param arg  parameters given on command line, or coded e.g.:
    * <pre>
    *    String[] args = new String[2];
    *    args[0] = "-port";
    *    args[1] = "" + serverPort;
    *    xmlBlasterConnection = new XmlBlasterConnection(args); // Find orb
    * </pre>
    */
   public XmlBlasterConnection(String[] args) throws XmlBlasterException {
      initArgs(args);
   }

   public XmlBlasterConnection(Global glob) throws XmlBlasterException {
      this.glob = glob;
      if (glob.wantsHelp()) {
         usage();
      }
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
      this.glob = new Global();
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
            Log.info(ME, "Loaded security plugin=" + secMechanism + " version=" + secVersion);
      }
      catch (Exception e) {
         Log.error(ME, "Security plugin initialization failed. Reason: "+e.toString());
         secPlgn = null;
      }
   }

   I_ClientPlugin getSecurityPlugin()
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

      Log.info(ME, "Using 'client.protocol=" + driverType + "' to access " + getServerNodeId());

      if (driverType.equalsIgnoreCase("SOCKET")) {
         driver = new SocketConnection(this.glob);
      }
      else if (driverType.equalsIgnoreCase("IOR") || driverType.equalsIgnoreCase("IIOP")) {
         driver = new CorbaConnection(this.glob);
      }
      else if (driverType.equalsIgnoreCase("RMI")) {
         driver = new RmiConnection(this.glob);
      }
      else if (driverType.equalsIgnoreCase("XML-RPC")) {
         driver = new XmlRpcConnection(this.glob);
      }
      else {
         String text = "Unknown protocol '" + driverType + "' to access " + getServerNodeId() + ", use SOCKET, IOR, RMI or XML-RPC.";
         Log.error(ME, text);
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
      Log.info(ME, "Using 'client.protocol=" + driverType + "' and 'client.protocol.class=" + driverClassName + "'to access " + getServerNodeId());
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
         Log.error(ME, text);
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
    * @param connCallback Your implementation of I_ConnectionProblems, we initialize fail save mode with default settings<br />
    *                     If null, DummyConnectionProblemHandler is used and
    *                     fail save mode is only switched on, if -delay is set bigger 0
    * @see org.xmlBlaster.client.protocol.DummyConnectionProblemHandler
    */
   public void initFailSave(I_ConnectionProblems connCallback)
   {
      if (connCallback == null)
         this.clientProblemCallback = new DummyConnectionProblemHandler(this);
      else
         this.clientProblemCallback = connCallback;
      Log.info(ME, "Initializing fail save mode");
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
      if (driverType.equalsIgnoreCase("SOCKET"))
         driver = new SocketConnection(glob, ap);
      else if (driverType.equalsIgnoreCase("IOR") || driverType.equalsIgnoreCase("IIOP"))
         driver = new CorbaConnection(glob, ap);
      else if (driverType.equalsIgnoreCase("RMI"))
         driver = new RmiConnection(glob, ap);
      else if (driverType.equalsIgnoreCase("XML-RPC"))
         driver = new XmlRpcConnection(glob, ap);
      else {
         String text = "Unknown protocol '" + driverType + "' to access " + getServerNodeId() + ", use IOR, RMI or XML-RPC.";
         Log.error(ME, text);
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
    * @see org.xmlBlaster.util.InvocationRecorder
    * @deprecated Use explicit Address settings, e.g.:
    * <pre>
    *  // Setup fail save handling ...
    *  Address addressProp = new Address(glob);
    *  addressProp.setDelay(4000L);      // retry connecting every 4 sec
    *  addressProp.setRetries(-1);       // -1 == forever
    *  addressProp.setPingInterval(0L);  // switched off
    *  addressProp.setMaxMsg(1000);      // queue up to 1000 messages
    *
    *  con.initFailSave(this);           // We want to be informed about problems (interface I_ConnectionProblems)
    *
    *  connectQos.setAddress(addressProp);
    * </pre>
    */
   public synchronized void initFailSave(I_ConnectionProblems connCallback, long retryInterval, int retries, int maxInvocations, long pingInterval)
   {
      if (Log.CALL) Log.call(ME, "Initializing fail save mode: retryInterval=" + retryInterval + ", retries=" + retries + ", maxInvocations=" + maxInvocations + ", pingInterval=" + pingInterval);
      if (this.addressFailSaveSettings == null) this.addressFailSaveSettings = new Address(glob);
      this.clientProblemCallback = connCallback;
      this.addressFailSaveSettings.setDelay(retryInterval);
      this.addressFailSaveSettings.setRetries(retries);
      this.addressFailSaveSettings.setMaxMsg(maxInvocations);
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
      if (Log.CALL) Log.call(ME, "Initializing cache: size=" + size);
      cache = new BlasterCache(size);
      Log.info(ME, "BlasterCache has been initialized with size="+size);
   }

   /**
    * Killing the ping thread (not recommended).
    */
   public void killPing()
   {
      if (pingThread != null)
         pingThread.pingRunning = false;
   }


   /**
    * Is fail save mode switched on?
    */
   public final boolean isInFailSaveMode()
   {
      return recorder != null;
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
    */
   public synchronized void login(String loginName, String passwd, ConnectQos qos, I_Callback client) throws XmlBlasterException
   {
      this.ME = "XmlBlasterConnection-" + loginName;
      this.updateClient = client;
      if (Log.CALL) Log.call(ME, "login() ...");

      if (qos == null)
         qos = new ConnectQos(glob);

      I_SecurityQos securityQos = qos.getSecurityQos();
      if(securityQos == null) {
         // Create default security tags (as specified in xmlBlaster.properties) ...
         initSecuritySettings(null, null);
         securityQos = secPlgn.getSecurityQos();
         qos.setSecurityQos(securityQos);
      }
      securityQos.setUserId(loginName);
      securityQos.setCredential(passwd);

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
    *       &lt;securityService type=\"simple\" version=\"1.0\">
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
    */
   public ConnectReturnQos connect(ConnectQos qos, I_Callback client, CbQueueProperty prop) throws XmlBlasterException
   {
      return connect(qos, client, prop, (CallbackAddress)null, (String)null);
   }

   /**
    * Internal connect method, collecting all other connect() variants
    * @see #connect(ConnectQos qos, I_Callback client)
    */
   private ConnectReturnQos connect(ConnectQos qos, I_Callback client, CbQueueProperty givenProp, CallbackAddress cbAddr, String cbSessionId) throws XmlBlasterException
   {
      if (qos == null) qos = new ConnectQos(glob);

      if (qos.getSecurityPluginType() == null || qos.getSecurityPluginType().length() < 1)
         throw new XmlBlasterException(ME+".Authentication", "Please add your authentication in your login QoS");

      this.connectQos = qos;

      String loginName = connectQos.getSecurityQos().getUserId();
      this.ME = "XmlBlasterConnection-" + loginName;
      this.updateClient = client;
      if (Log.CALL) Log.call(ME, "connect() ...");
      if (Log.DUMP) Log.dump(ME, "connect() " + (client==null?"with":"without") + " callback qos=\n" + connectQos.toXml());

      Address address = connectQos.getAddress();
      if (address == null) {
         address = new Address(glob);
         connectQos.setAddress(address);
      }

      if (this.addressFailSaveSettings != null) { // user called initFailSave(I_ConnectionProblems,,,,) before?
         address.setDelay(addressFailSaveSettings.getDelay());
         address.setRetries(addressFailSaveSettings.getRetries());
         address.setPingInterval(addressFailSaveSettings.getPingInterval());
         address.setMaxMsg(addressFailSaveSettings.getMaxMsg());
      }
      
      initDriver(address);
      
      initBurstMode(address);

      // You should call initFailSave(I_ConnectionProblems) first
      if (this.clientProblemCallback != null && address.getDelay() < 1) {
         address.setDelay(4 * 1000L);
         Log.warn(ME, "You have called initFailSave() but -delay is 0, setting ping delay to " + address.getDelay() + " millis");
      }
      if (this.clientProblemCallback == null && address.getDelay() > 0) {
         Log.warn(ME, "You have set -delay " + address.getDelay() + ", but not called initFailSave(), using default error recovery on connection problems");
         this.clientProblemCallback = new DummyConnectionProblemHandler(this);
      }
      if (this.clientProblemCallback != null) { // fail save mode:
         //this.clientProblemCallback = new DummyConnectionProblemHandler(this);
         this.recorder = new InvocationRecorder(address.getMaxMsg(), this, null);
         Log.info(ME, "Activated fail save mode: " + address.getSettings());
      }

      // Load the client helper to export/import messages:
      initSecuritySettings(connectQos.getSecurityPluginType(), connectQos.getSecurityPluginVersion());

      if (client != null) { // Start a default callback server using same protocol
         this.cbServer = initCbServer(loginName, null);

         // Set all configurable callback parameters, they are added to the connect QoS

         CbQueueProperty prop = givenProp; // Use user supplied property if != null
         if (prop == null)
            prop = connectQos.getCbQueueProperty(); // Creates a default property for us if none is available
         else
            connectQos.addCbQueueProperty(prop);

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
            addr.setSessionId(cbSessionId);

         prop.setCallbackAddress(addr);
         Log.info(ME, "Callback settings: " + prop.getSettings());
      } // Callback server configured and running

      //Log.info(ME, "DUMP of ConnectQos\n"  + connectQos.toXml());
      try {
         // 'this' forces to invoke our update() method which we then delegate to the updateClient
         this.connectReturnQos = driver.connect(connectQos);
         numLogins++;
      }
      catch(ConnectionException e) {
         if (Log.TRACE) Log.trace(ME, "Login to " + getServerNodeId() + " failed, numLogins=" + numLogins + ". Authentication string is\n" + connectQos.toXml());
         if (numLogins == 0)
            startPinging();
         throw new XmlBlasterException(e);
      }
      if (isReconnectPolling && numLogins > 0)
         clientProblemCallback.reConnected();

      startPinging();
      return connectReturnQos;
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
      Log.info(ME, "Using 'client.cbProtocol=" + driverType + "' to be used by " + getServerNodeId() + ", trying to create the callback server ...");

      try {
         if (driverType.equalsIgnoreCase("SOCKET")) {
            SocketConnection sc = (SocketConnection)this.driver; // downcast hack
            I_CallbackServer server =  sc.getCallbackServer();
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
         else if (driverType.equalsIgnoreCase("XML-RPC")) {
            I_CallbackServer server = new XmlRpcCallbackServer();
            server.initialize(this.glob, loginName, this);
            return server;
         }
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         Log.error(ME, "Creation of CallbackServer failed: " + e.toString());
         e.printStackTrace();
         throw new XmlBlasterException(ME, "Creation of CallbackServer failed: " + e.toString());
      }
      String text = "Unknown driverType '" + driverType + "' to install xmlBlaster callback server.";
      Log.error(ME, text);
      throw new XmlBlasterException(ME+".UnknownDriver", text);
   }

   /**
    * Login to the server.
    * <p />
    * For internal use only.
    * The qos needs to be set up correctly if you wish a callback
    * @exception       XmlBlasterException if login fails
    */
   private void loginRaw() throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "loginRaw() ...");
      try {
         driver.loginRaw();
         numLogins++;
         if (Log.TRACE) Log.trace(ME, "Success login");
      } catch(ConnectionException e) {
         if (Log.TRACE) Log.trace(ME, "Login failed , numLogins=" + numLogins);
         if (numLogins == 0)
            startPinging();
         throw new XmlBlasterException(e);
      }
      if (isReconnectPolling && numLogins > 0)
         clientProblemCallback.reConnected();

      startPinging();
   }


   /**
    * If we lost the connection to xmlBlaster, handle it
    * @exception XmlBlasterException id="NoConnect" if we give up to connect<br />
    *            id="TryingReconnect" if we are in fail save mode and polling for a connection
    */
   private synchronized void handleConnectionException(ConnectionException e) throws XmlBlasterException
   {
      if (noConnect) {// LoginThread tried already and gave up
         if (isInFailSaveMode())
            Log.error(ME, "Can't establish connection to " + getServerNodeId() + ", pinging retries = " + retries + " exceeded.");
         else
            Log.error(ME, "Can't establish connection to " + getServerNodeId() + ", no fail save mode.");
         throw new XmlBlasterException("NoConnect", e.toString()); // Client may choose to exit
      }

      if (isInFailSaveMode()) {
         synchronized(pollingMonitor) {
            if (isReconnectPolling) {
               // Thread.currentThread().dumpStack();
               throw new XmlBlasterException("TryingReconnect", "Still trying to find " + getServerNodeId() + " again ..."); // Client may hope on reconnect
            }

            if (numLogins == 0L) {
               doLoginPolling();
            }
            else {
               Log.error(ME, "Lost connection to " + getServerNodeId() + " server: " + e.toString());
               driver.init();
               clientProblemCallback.lostConnection(); // notify client
               doLoginPolling();
               throw new XmlBlasterException("TryingReconnect", "Trying to find " + getServerNodeId() + " again ..."); // Client may hope on reconnect
            }
         }
      }
      else {
         throw new XmlBlasterException("NoConnect", e.toString()); // Client may choose to exit
      }

   }


   /**
    * If we lost the connection to xmlBlaster, poll here to reconnect
    */
   private void doLoginPolling()
   {
      if (connectQos == null || connectQos.getAddress() == null) {
         Thread.currentThread().dumpStack();
         throw new IllegalArgumentException("Address==null in XmlBlasterConnection");
      }
      Log.info(ME, "Going to poll for " + getServerNodeId() + " and queue your messages ...");
      isReconnectPolling = true;
      LoginThread lt = new LoginThread(this, connectQos.getAddress().getDelay(), retries);
      lt.start();
   }


   /**
    * Start a never ending ping thread
    */
   private void startPinging()
   {
      if (this.clientProblemCallback == null) {
         if (Log.TRACE) Log.trace(ME, "No ping initialized, we are not in fails save mode");
         return;
      }
      if (connectQos.getAddress().getPingInterval() > 0L && pingThread == null) {
         pingThread = new PingThread(this, connectQos.getAddress().getPingInterval());
         pingThread.start();
      }
   }


   /**
    * Access the login name.
    * @return your login name or null if you are not logged in
    */
   public String getLoginName()
   {
      return driver.getLoginName();
   }

   /**
    * @see #disconnect()
    * @deprecated Please use disconnect() instead
    */
   public synchronized boolean logout()
   {
      if (Log.CALL) Log.call(ME, "logout() ...");
      return disconnect(new DisconnectQos());
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
    */
   public synchronized boolean disconnect(DisconnectQos qos, boolean flush, boolean shutdown, boolean shutdownCb)
   {
      if (Log.CALL) Log.call(ME, "disconnect() ...");
      disconnectInProgress = true;

      killPing();

      if (!isLoggedIn()) {
         if (isInFailSaveMode() || recorder != null && recorder.size() > 0)
            Log.warn(ME, "Logout! Please note that there are " + recorder.size() + " unsent invocations/messages in the queue");
         else
            Log.trace(ME, "No logout, you are not logged in");
         disconnectInProgress = false;
         return false;
      }

      synchronized (callbackMap) {
         Set keys = callbackMap.keySet();
         Iterator it = keys.iterator();
         while(it.hasNext()) {
            String subscriptionId = (String)it.next();
            KeyWrapper key = new KeyWrapper(subscriptionId);
            try {
               driver.unSubscribe(key.toString(), "");
            }
            catch(XmlBlasterException e) {
               Log.warn(ME+".logout", "Couldn't unsubscribe '" + subscriptionId + "' : " + e.toString());
            }
            catch(ConnectionException e) {
               break;
            }
         }
         callbackMap.clear();
      }

      if (this.publishOnewayTimer != null) {
         if (flush) {
            flushPublishOnewaySet();
         }
      }

      boolean ret = false;
      try {
         ret = driver.logout();
         Log.info(ME, "Successful disconnect from " + getServerNodeId());
      } catch(Throwable e) {
         e.printStackTrace();
         Log.warn(ME+".disconnect()", e.toString());
      }

      if (shutdown) {
         shutdown(false);
      }

      if (shutdownCb) {
         try {
           shutdownCb();
         } catch (Throwable e) {
            e.printStackTrace();
            Log.warn(ME+".disconnect()", e.toString());
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
   public boolean shutdownCb() throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "shutdownCb() ...");
      //Thread.currentThread().dumpStack();
      if (this.cbServer != null)
         return this.cbServer.shutdownCb();
      return false;
   }

   /**
    * Shut down the server connection. 
    * Calls disconnect() first if still connected.
    * If burst mode messages are in the queue, they are flushed.
    *
    * @return true CB server successfully shut down
    *         false failure on shutdown
    */
   public boolean shutdown()
   {
      return shutdown(true);
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
      if (Log.CALL) Log.call(ME, "shutdown(" + flush + ") ...");

      try {
         if (isLoggedIn() && !disconnectInProgress) {
            if (this.publishOnewayTimer != null) {
               if (flush) {
                  flushPublishOnewaySet();
               }
            }
            disconnect(new DisconnectQos(), flush, false, true);
         }
         return driver.shutdown();
      } catch(Exception e) {
         Log.warn(ME, e.toString());
         e.printStackTrace();
         return false;
      }
   }


   /**
    * @return true if you are logged in. 
    */
   public boolean isLoggedIn()
   {
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
      if (Log.CALL) Log.call(ME, "Entering update(" + ((cache != null) ? "using cache" : "no cache") + ") ...");
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

   private final String subscribeRaw(String xmlKey, String qos) throws XmlBlasterException, ConnectionException, IllegalArgumentException
   {
      if (qos==null) qos = "";
      if (xmlKey==null) throw new IllegalArgumentException("Please provide a valid XmlKey for subscribe()");
      if (secPlgn!=null) { // with security Plugin: interceptor
         return secPlgn.importMessage(
                     driver.subscribe(secPlgn.exportMessage(xmlKey),
                                      secPlgn.exportMessage(qos)));
      }
      else { // without security plugin
         return driver.subscribe(xmlKey, qos);
      }
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
    *   SubscribeKeyWrapper key = new SubscribeKeyWrapper("//stock", "XPATH");
    *   SubscribeQosWrapper qos = new SubscribeQosWrapper();
    *   try {
    *      con.subscribe(key.toXml(), qos.toXml(), new I_Callback() {
    *            public void update(String name, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
    *               System.out.println("Receiving message for '//stock' subscription ...");
    *            }
    *         });
    *   } catch(XmlBlasterException e) {
    *      System.out.println(e.reason);
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
    * @see xmlBlaster.idl
    * @see org.xmlBlaster.engine.RequestBroker#subscribe
    */
   public final String subscribe(String xmlKey, String qos, I_Callback cb) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "subscribe(with callback) ...");
      if (updateClient == null) {
         String text = "No callback server is incarnated. " +
                       " Please use XmlBlasterConnection - constructor with default I_Callback given.";
         throw new XmlBlasterException(ME+".NoCallback", text);
      }
      String subscriptionId = null;
      synchronized (callbackMap) {
         subscriptionId = subscribe(xmlKey, qos);
         callbackMap.put(subscriptionId, cb);
      }
      return subscriptionId;
   }

   /**
    * Enforced by I_InvocationRecorder interface (fail save mode).
    * see explanations of publish() method.
    * @return oid    The oid of your subscribed Message<br>
    *                If you subscribed using a query, the subscription ID of this<br>
    *                query handling object (SubscriptionInfo.getUniqueKey()) is returned.<br>
    *                You should use this oid if you wish to unSubscribe()<br>
    *                If no match is found, an empty string "" is returned.
    * @see xmlBlaster.idl
    * @see org.xmlBlaster.engine.RequestBroker#subscribe
    */
   public final String subscribe(String xmlKey, String qos) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "subscribe() ...");
      try {
         return subscribeRaw(xmlKey, qos);
      } catch(XmlBlasterException e) {
         throw e;
      } catch(ConnectionException e) {
         if (recorder != null) recorder.subscribe(xmlKey, qos);
         handleConnectionException(e);
      }
      return ""; // never reached, there is always an exception thrown
   }


   /**
    * Enforced by I_InvocationRecorder interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public final void unSubscribe(String xmlKey, String qos) throws XmlBlasterException, IllegalArgumentException
   {
      if (Log.CALL) Log.call(ME, "unSubscribe() ...");
      if (qos==null) qos = "";
      if (xmlKey==null) throw new IllegalArgumentException("Please provide a valid XmlKey for unSubscribe()");
      try {
         if (secPlgn!=null) { // with security Plugin
            driver.unSubscribe(secPlgn.exportMessage(xmlKey), secPlgn.exportMessage(qos));
         }
         else { // without security plugin
            driver.unSubscribe(xmlKey, qos);
         }
         synchronized (callbackMap) {
            UpdateKey key = new UpdateKey(glob, xmlKey); // has a SAX parser for xml key
            callbackMap.remove(key.getUniqueKey());
         }
      } catch(XmlBlasterException e) {
         throw e;
      } catch(ConnectionException e) {
         if (recorder != null) recorder.unSubscribe(xmlKey, qos);
         handleConnectionException(e);
      }
   }


   /**
    * Publish fault-tolerant the given message.
    * <p />
    * This is a wrapper around the raw CORBA publish() method
    * If the server disappears you get an exception.
    * This call will not block.
    * <p />
    * Enforced by I_InvocationRecorder interface (fail save mode)
    * @see xmlBlaster.idl
    * @exception XmlBlasterException id="NoConnect" if we give up to connect<br />
    *            id="TryingReconnect" if we are in fail save mode and polling for a connection,
    #            your message is tailed back and flushed on reconnect
    * @see #handleConnectionException(ConnectionException)
    */
   public final String publish(MessageUnit msgUnit) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Publishing ...");
      try {
         if (secPlgn!=null) {
            MessageUnit mu = secPlgn.exportMessage(msgUnit);
            return secPlgn.importMessage(driver.publish(mu));
         }
         else {
            return driver.publish(msgUnit);
         }
      } catch(XmlBlasterException e) {
         if (Log.TRACE) Log.trace(ME, "XmlBlasterException: " + e.reason);
         throw e;
      } catch(ConnectionException e) {
         if (recorder != null) recorder.publish(msgUnit);
         handleConnectionException(e);
      }
      return dummyS; // never reached, there is always an exception thrown
   }


   /**
    * Enforced by I_InvocationRecorder interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public String[] publishArr(MessageUnit [] msgUnitArr) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "publishArr() ...");
      try {
         if (secPlgn!=null) { // security plugin allows e.g. crypting of messages ...
            MessageUnit mu[] = exportAll(msgUnitArr);
            String[] result = driver.publishArr(mu);
            return importAll(result);
         }
         else { // security plugin not available
            return driver.publishArr(msgUnitArr);
         }
      } catch(XmlBlasterException e) {
         if (Log.TRACE) Log.trace(ME, "XmlBlasterException: " + e.reason);
         throw e;
      } catch(ConnectionException e) {
         if (recorder != null) recorder.publishArr(msgUnitArr);
         handleConnectionException(e);
      }
      return dummySArr;
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
         MessageUnit[] msgUnitArr = new MessageUnit[this.publishOnewayBurstModeVec.size()];
         Iterator it = this.publishOnewayBurstModeVec.iterator();
         for (int i=0; it.hasNext(); i++) {
            msgUnitArr[i] = (MessageUnit)it.next();
         }
         this.publishOnewayBurstModeVec.clear();
         Log.info(ME+".flushPublishOnewaySet()", "Burst mode timeout after " + this.publishOnewayCollectTime + " millis occurred, publishing " + msgUnitArr.length + " oneway messages ...");
         publishOneway(msgUnitArr, true);
      }
   }

   /**
    * @see xmlBlaster.idl
    */
   public void publishOneway(MessageUnit [] msgUnitArr)
   {
      publishOneway(msgUnitArr, false);
   }

   /**
    * @see xmlBlaster.idl
    */
   private void publishOneway(MessageUnit [] msgUnitArr, boolean flushBurstMode)
   {
      if (Log.CALL) Log.call(ME, "publishOneway() ...");
      try {
         if (this.publishOnewayCollectTime > 0L && !flushBurstMode) {
            synchronized(this.publishOnewayTimer) {
               //Log.info(ME, "publishOneway() adding set ...");
               for (int ii = 0; ii<msgUnitArr.length; ii++) {
                  this.publishOnewayBurstModeVec.addElement(msgUnitArr[ii].getClone());
               }
               if (this.publishOnewayTimerKey == null) {
                  if (Log.TRACE) Log.trace(ME, "Spanning timer = " +  this.publishOnewayCollectTime);
                  this.publishOnewayTimerKey = this.publishOnewayTimer.addTimeoutListener(this, this.publishOnewayCollectTime, null);
               }
               return;
            }
         }

         MessageUnit[] mu = msgUnitArr;
         if (secPlgn!=null) { // security plugin allows e.g. crypting of messages ...
            mu = exportAll(msgUnitArr);
         }
         driver.publishOneway(mu);
      } catch(XmlBlasterException e) {
         Log.error(ME, "XmlBlasterException is not forwarded to client, we are in 'oneway' mode: " + e.reason);
      } catch(ConnectionException e) {
         try {
            if (recorder != null) recorder.publishArr(msgUnitArr);
            handleConnectionException(e);
         } catch(XmlBlasterException e2) {
            Log.error(ME, "XmlBlasterException is not forwarded to client, we are in 'oneway' mode: " + e2.reason);
         }
      }
   }

   /**
    * Enforced by I_InvocationRecorder interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public final String[] erase(String xmlKey, String qos) throws XmlBlasterException, IllegalArgumentException
   {
      if (Log.CALL) Log.call(ME, "erase() ...");
      if (qos==null) qos = "";
      if (xmlKey==null) throw new IllegalArgumentException("Please provide a valid XmlKey for erase()");
      try {
         if (secPlgn!=null) {
            String[] result = driver.erase(secPlgn.exportMessage(xmlKey),
                                           secPlgn.exportMessage(qos));
            return importAll(result);
         }
         else {
            return driver.erase(xmlKey, qos);
         }
      } catch(XmlBlasterException e) {
         if (Log.TRACE) Log.trace(ME, "XmlBlasterException: " + e.reason);
         throw e;
      } catch(ConnectionException e) {
         if (recorder != null) recorder.erase(xmlKey, qos);
         handleConnectionException(e);
      }
      return dummySArr;
   }


   private final MessageUnit[] getRaw(String xmlKey, String qos) throws XmlBlasterException, ConnectionException
   {
      MessageUnit[] units = null;
      if (secPlgn!=null) {
         units = importAll(driver.get(secPlgn.exportMessage(xmlKey),
                                      secPlgn.exportMessage(qos)));
      }
      else {
         units = driver.get(xmlKey, qos);
      }
      return units;
   }


   /**
    * Enforced by I_InvocationRecorder interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public final MessageUnit[] get(String xmlKey, String qos) throws XmlBlasterException, IllegalArgumentException
   {
      MessageUnit[] units = null;
      if (Log.CALL) Log.call(ME, "get() ...");
      if (qos==null) qos = "";
      if (xmlKey==null) throw new IllegalArgumentException("Please provide a valid XmlKey for get()");
      try {
         //Is cache installed?
         if (cache != null) {
            units = cache.get( xmlKey, qos );
            //not found in cache
            if( units == null ) {
               units = getRaw(xmlKey, qos);              //get messages from xmlBlaster (synchronous)
               String subId = subscribeRaw(xmlKey, qos); //subscribe to this messages (asynchronous)
               cache.newEntry(subId, xmlKey, units);     //fill messages to cache
               Log.info(ME, "New entry in cache created (subId="+subId+")");
            }
         }
         else {
            units = getRaw(xmlKey, qos);
         }
         return units;
      } catch(XmlBlasterException e) {
         if (Log.TRACE) Log.trace(ME, "XmlBlasterException: " + e.reason);
         throw e;
      } catch(ConnectionException e) {
         if (recorder != null) recorder.get(xmlKey, qos);
         e.printStackTrace();
         handleConnectionException(e);
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

   private MessageUnit[] importAll(MessageUnit[] msgUnitArr) throws XmlBlasterException
   {
      MessageUnit mu[] = new MessageUnit[msgUnitArr.length];
      for(int i=0; i<msgUnitArr.length; i++) {
        mu[i]=secPlgn.importMessage(msgUnitArr[i]);
      }
      return mu;
   }

   private MessageUnit[] exportAll(MessageUnit[] msgUnitArr) throws XmlBlasterException
   {
      MessageUnit mu[] = new MessageUnit[msgUnitArr.length];
      for(int i=0; i<msgUnitArr.length; i++) {
        mu[i]=secPlgn.exportMessage(msgUnitArr[i]);
      }
      return mu;
   }

   /**
    * Check server. 
    * @see xmlBlaster.idl
    */
   public void ping()
   {
      if (isReconnectPolling)
         return;
      try {
         driver.ping("");
         if (Log.CALL) Log.call(ME, "ping success() ...");
         return;
      } catch(ConnectionException e) {
         if (Log.TRACE) Log.trace(ME, "ping failed, " + getServerNodeId() + " seems to be down, try to reactivate connection ...");
         try {
            handleConnectionException(e);
         } catch(XmlBlasterException ep) {
            if (Log.TRACE) Log.trace(ME, "Exception in ping! " + ep.reason);
         }
      } catch(XmlBlasterException e) {
         Log.error(ME, "ping failed: " + e.toString());
      }
      return ; // never reached, there is always an exception thrown
   }

   public int queueSize()
   {
      if (recorder == null) {
         Log.warn(ME, "Internal error: don't call queueSize(), you are not in fail save mode");
         return 0;
      }

      return recorder.size();
   }


   public void flushQueue() throws XmlBlasterException
   {
      if (recorder == null) {
         Log.warn(ME, "Internal error: don't call flushQueue(), you are not in fail save mode");
         return;
      }

      recorder.pullback(0L, 0L, 0.);
   }


   public void resetQueue()
   {
      if (recorder == null) {
         Log.warn(ME, "Internal error: don't call flushQueue(), you are not in fail save mode");
         return;
      }

      recorder.reset();
   }


   /**
    * Poll for xmlBlaster server
    */
   private class LoginThread extends Thread
   {
      private final String ME = "LoginThread";
      private XmlBlasterConnection con;
      private final long RETRY_INTERVAL; // would this be smarter? glob.getProperty().get("Failsave.retryInterval", 4000L);
      private final int RETRIES;         // -1 = forever
      private int counter = 0;
      private int logInterval = 10;


      /**
       * @param retryInterval How many milli seconds sleeping before we retry a connection
       * @param retries Number of retries if connection cannot directly be established
       */
      LoginThread(XmlBlasterConnection con, long retryInterval, int retries) {
         this.con = con;
         this.RETRY_INTERVAL = retryInterval;
         this.RETRIES = retries;
         if (retryInterval > 30000)  // millisec
            logInterval = 1;
         else
            logInterval = (int)(30000L / retryInterval);
         if (Log.CALL) Log.call(ME, "Entering constructor retryInterval=" + retryInterval + " millis and retries=" + retries);
      }

      public void run() {
         Log.info(ME, "Polling for " + getServerNodeId() + " server");
         con.isReconnectPolling = true;
         for (int ii=0; ii<RETRIES || RETRIES==-1; ii++) {
            try {
               con.loginRaw();
               Log.info(ME, "Success, a new connection to " + getServerNodeId() + " is established.");
               con.isReconnectPolling = false;
               return;
            } catch(Exception e) {
               if ((counter % logInterval) == 0)
                  Log.warn(ME, "No connection established, " + getServerNodeId() + " still seems to be down after " + (counter+1) + " pings.");
               counter++;
               try {
                  Thread.currentThread().sleep(RETRY_INTERVAL);
               } catch (InterruptedException i) { }
            }
         }
         con.noConnect = true; // communicate back to XmlBlasterConnection that we give up
         Log.info(ME, "max polling for " + getServerNodeId() + " server done, no success");
      }
   } // class LoginThread


   /**
    * Ping the xmlBlaster server, to test if connection is alive
    */
   private class PingThread extends Thread
   {
      private final String ME = "PingThread";
      private XmlBlasterConnection con;
      private final long PING_INTERVAL;
      boolean pingRunning = true;

      /**
       * @param pingInterval How many milli seconds sleeping between the pings
       */
      PingThread(XmlBlasterConnection con, long pingInterval) {
         this.con = con;
         this.PING_INTERVAL = pingInterval;
         if (Log.CALL) Log.call(ME, "Entering constructor ping interval=" + pingInterval + " millis");
      }
      public void run() {
         Log.info(ME, "Pinging " + getServerNodeId() + " server every " + PING_INTERVAL + " millis.");
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
         Log.warn(ME, "I_ConnectionProblems: Lost connection to " + getServerNodeId() + "");
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
               Log.info(ME, "We were lucky, reconnected to " + getServerNodeId() + ", sending tailback " + xmlBlasterConnection.queueSize() + " messages ...");
               xmlBlasterConnection.flushQueue();
            }
            else
               Log.info(ME, "We were lucky, reconnected to " + getServerNodeId() + ", no tailback messages to flush");
         }
         catch (XmlBlasterException e) {
            Log.error(ME, "Sorry, flushing of tailback messages failed, they are lost: " + e.toString());
         }
      }
   }

   /**
    * Command line usage.
    */
   public static void usage()
   {
      String text = "\n";
      text += "Choose a connection protocol:\n";
      text += "   -client.protocol    Specify a protocol to talk with xmlBlaster, 'SOCKET' or 'IOR' or 'RMI' or 'XML-RPC'.\n";
      text += "                       Current setting is '" + Global.instance().getProperty().get("client.protocol", "IOR") + "'. See below for protocol settings.\n";
      text += "\n";
      text += "Security features:\n";
      text += "   -Security.Client.DefaultPlugin \"gui,1.0\"\n";
      text += "                       Force the given authentication schema, here the GUI is enforced\n";
      text += "                       Clients can overwrite this with ConnectQos.java\n";

      Log.plain(text);
      Global glob = new Global();
      try {
         Log.plain(new ConnectQos(glob).usage());
      } catch (XmlBlasterException e) {}
      Log.plain(new Address(glob).usage());
      Log.plain(new QueueProperty(glob,null).usage());
      Log.plain(new CallbackAddress(glob).usage());
      Log.plain(new CbQueueProperty(glob,null,null).usage());
      Log.plain(SocketConnection.usage());
      Log.plain(CorbaConnection.usage());
      Log.plain(RmiConnection.usage());
      Log.plain(XmlRpcConnection.usage());
   }

} // class XmlBlasterConnection
