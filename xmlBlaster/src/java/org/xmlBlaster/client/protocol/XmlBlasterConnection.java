/*------------------------------------------------------------------------------
Name:      XmlBlasterConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP/RMI or XML-RPC
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import org.xmlBlaster.util.Log;
import org.jutils.JUtilsException;

import org.xmlBlaster.client.protocol.corba.CorbaConnection;
import org.xmlBlaster.client.protocol.rmi.RmiConnection;
import org.xmlBlaster.client.protocol.xmlrpc.XmlRpcConnection;

import org.xmlBlaster.client.BlasterCache;
import org.xmlBlaster.client.PluginLoader;
import org.xmlBlaster.client.I_ConnectionProblems;
import org.xmlBlaster.client.I_CallbackRaw;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.KeyWrapper;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.I_InvocationRecorder;
import org.xmlBlaster.util.InvocationRecorder;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;

import java.applet.Applet;

import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;


/**
 * This is a helper class, helping a Java client to connect to xmlBlaster
 * using IIOP (CORBA), RMI, XML-RPC or any other supported protocol.
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
public class XmlBlasterConnection extends AbstractCallbackExtended implements I_InvocationRecorder
{
   private String ME = "XmlBlasterConnection";
   protected String[] args = null;

   /** The driver, e.g. Corba/Rmi/XmlRpc */
   private I_XmlBlasterConnection driver = null;

   /** queue all the messages, and play them back through interface I_InvocationRecorder */
   private InvocationRecorder recorder = null;

   /** This interface needs to be implemented by the client in fail save mode
       The client gets notified about abnormal connection loss or reconnect */
   private I_ConnectionProblems clientProblemCallback = null;

   /** Used to callback the clients update() method */
   private I_Callback updateClient = null;

   /** true if we are in fails save mode and polling for xmlBlaster */
   private boolean isReconnectPolling = false;

   /** How many milli seconds sleeping before we retry a connection */
   private long retryInterval;

   /** Number of retries if connection cannot directly be established */
   private int retries = -1;

   /** communicate from LoginThread back to CorbaConnection that we give up */
   private boolean noConnect = false;

   /** How many milli seconds sleeping between the pings */
   private long pingInterval;

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
    * </pre>
    */
   public XmlBlasterConnection() throws XmlBlasterException
   {
      initArgs(args);
      initDriver(null);
      initFailSave(null);
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
    * </pre>
    * @param arg  parameters given on command line, or coded e.g.:
    * <pre>
    *    String[] args = new String[2];
    *    args[0] = "-iorPort";
    *    args[1] = "" + serverPort;
    *    xmlBlasterConnection = new XmlBlasterConnection(args); // Find orb
    * </pre>
    */
   public XmlBlasterConnection(String[] args) throws XmlBlasterException
   {
      initArgs(args);
      initDriver(null);
      initFailSave(null);
   }

   /**
    * Client access to xmlBlaster for <strong>normal client applications</strong>.
    * <p />
    * @param arg  parameters given on command line
    * @param protocol e.g. "IOR", "RMI", "XML-RPC"
    *                 IOR is the CORBA driver.
    */
   public XmlBlasterConnection(String[] args, String driverType) throws XmlBlasterException
   {
      initArgs(args);
      initDriver(driverType);
      initFailSave(null);
   }

   private void initArgs(String[] args)
   {
      this.args = args;
      if (this.args == null)
         this.args = new String[0];
      if (this.args.length > 0) {
         try {
            XmlBlasterProperty.addArgs2Props(this.args); // enforce that the args are added to the xmlBlaster.properties hash table
         }
         catch (JUtilsException e) {
            Log.warn(ME, e.toString());
         }
      }
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
      PluginLoader secPlgnMgr = PluginLoader.getInstance();
      try {
         secPlgn      = secPlgnMgr.getClientPlugin(secMechanism, secVersion);
         if (secMechanism != null)  // to avoid double logging for login()
            Log.info(ME, "Loaded security plugin=" + secMechanism + " version=" + secVersion);
      }
      catch (Exception e) {
         Log.error(ME, "Security plugin initialization failed. Reason: "+e.toString());
         secPlgn=null;
      }
   }

   I_ClientPlugin getSecurityPlugin()
   {
      return secPlgn;
   }

   /**
    * Load the desired protocol driver like CORBA or RMI driver.
    */
   private void initDriver(String driverType) throws XmlBlasterException
   {
      if (driverType == null) driverType = XmlBlasterProperty.get("client.protocol", "IOR");

      Log.info(ME, "Using 'client.protocol=" + driverType + "' to access xmlBlaster");

      if (driverType.equalsIgnoreCase("IOR") || driverType.equalsIgnoreCase("IIOP"))
         driver = new CorbaConnection(this.args);
      else if (driverType.equalsIgnoreCase("RMI"))
         driver = new RmiConnection(this.args);
      else if (driverType.equalsIgnoreCase("XML-RPC"))
         driver = new XmlRpcConnection(this.args);
      else {
         String text = "Unknown protocol '" + driverType + "' to access xmlBlaster, use IOR, RMI or XML-RPC.";
         Log.error(ME, text);
         throw new XmlBlasterException(ME+".UnknownDriver", text);
      }
   }

   /**
    * Initialize fail save mode.
    * Configured with command line or xmlBlaster.properties settings
    * <p />
    * For example:
    * <pre>
    *   java javaclients.ClientSub -client.failSave.retryInterval 5000
    * </pre>
    * switches fail save mode on, on lost connection we try every 5 sec a reconnect.<br />
    * -1 switches it off, which is default.
    * @param connCallback Your implementation of I_ConnectionProblems, we initialize fail save mode with default settings<br />
    *                     If null, DummyConnectionProblemHandler is used and
    *                     fail save mode is only switched on, if -client.failSave.retryInterval is set bigger 0
    * @see org.xmlBlaster.client.protocol.DummyConnectionProblemHandler
    */
   public void initFailSave(I_ConnectionProblems connCallback)
   {
      int retryInterval = -1;
      if (connCallback == null)
         retryInterval = XmlBlasterProperty.get("client.failSave.retryInterval", -1);
      else
         retryInterval = XmlBlasterProperty.get("client.failSave.retryInterval", 5000);

      if (retryInterval > 0) {
         if (connCallback == null)
            this.clientProblemCallback = new DummyConnectionProblemHandler(this);
         else
            this.clientProblemCallback = connCallback;
         this.retryInterval = retryInterval;
         this.pingInterval = XmlBlasterProperty.get("client.failSave.pingInterval", 10 * 1000L);
         this.retries = XmlBlasterProperty.get("client.failSave.retries", -1);
         int maxInvocations = XmlBlasterProperty.get("client.failSave.maxInvocations", 10000);
         this.recorder = new InvocationRecorder(maxInvocations, this, null);
         Log.info(ME, "Initializing fail save mode: retryInterval=" + retryInterval +
                      " msec, retries=" + retries + ", maxInvocations=" + maxInvocations +
                      ", pingInterval=" + pingInterval + " msec");
      }
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
      if (driverType.equalsIgnoreCase("IOR") || driverType.equalsIgnoreCase("IIOP"))
         driver = new CorbaConnection(ap);
      else if (driverType.equalsIgnoreCase("RMI"))
         driver = new RmiConnection(ap);
      else if (driverType.equalsIgnoreCase("XML-RPC"))
         driver = new XmlRpcConnection(ap);
      else {
         String text = "Unknown protocol '" + driverType + "' to access xmlBlaster, use IOR, RMI or XML-RPC.";
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
    */
   public synchronized void initFailSave(I_ConnectionProblems connCallback, long retryInterval, int retries, int maxInvocations, long pingInterval)
   {
      if (Log.CALL) Log.call(ME, "Initializing fail save mode: retryInterval=" + retryInterval + ", retries=" + retries + ", maxInvocations=" + maxInvocations + ", pingInterval=" + pingInterval);
      this.clientProblemCallback = connCallback;
      this.retryInterval = retryInterval;
      this.pingInterval = pingInterval;
      this.retries = retries;
      this.recorder = new InvocationRecorder(maxInvocations, this, null);
   }


   /**
    * Setup the cache mode.
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
      Log.info(ME,"BlasterCache has been initialized with size="+size);
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
         qos = new ConnectQos();
      
      I_SecurityQos securityQos = qos.getSecurityQos();
      if(securityQos == null) {
         // Create default security tags (as specified in xmlBlaster.properties) ...
         initSecuritySettings(null, null);
         securityQos = secPlgn.getSecurityQos();
         securityQos.setUserId(loginName);
         securityQos.setCredential(passwd);
         qos.setSecurityQos(securityQos);
      }

      connect(qos, client);
   }


   /**
    * Login to the server, specify your own callback and authentication schema
    * in the qos.
    * <p />
    * Note that no asynchronous subscribe() method is available if you don't
    * specify a callback in 'qos'.
    *
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
    *       &lt;callback type='IOR'>
    *          &lt;PtP>true&lt;/PtP>
    *          IOR:00011200070009990000....
    *          &lt;compress type='gzip' minSize='1000' />
    *          &lt;burstMode collectTime='400' />
    *       &lt;/callback>
    *    &lt;/qos>
    * </pre>
    * @param qos       The Quality of Service for this client,
    *                  you have to pass at least the authentication tags
    * @exception       XmlBlasterException if login fails
    */
   public void connect(ConnectQos qos, I_Callback client) throws XmlBlasterException
   {
      if (qos.getSecurityPluginType() == null || qos.getSecurityPluginType().length() < 1)
         throw new XmlBlasterException(ME+".Authentication", "Please add your authentication in your login QoS");

      this.ME = "XmlBlasterConnection-" + qos.getSecurityQos().getUserId();
      this.updateClient = client;
      if (Log.CALL) Log.call(ME, "connect() ...");
      if (Log.DUMP) Log.dump(ME, "connect() " + (client==null?"with":"without") + " callback qos=\n" + qos.toXml());

      // Load the client helper to export/import messages:
      initSecuritySettings(qos.getSecurityPluginType(), qos.getSecurityPluginVersion());

      try {
         // 'this' forces to invoke our update() method which we then delegate to the updateClient
         driver.connect(qos, (client != null) ? this : null);
         numLogins++;
      }
      catch(ConnectionException e) {
         if (Log.TRACE) Log.trace(ME, "Login failed, numLogins=" + numLogins + ". Authentication string is\n" + qos.toXml());
         if (numLogins == 0)
            startPinging();
         throw new XmlBlasterException(e);
      }
      if (isReconnectPolling && numLogins > 0)
         clientProblemCallback.reConnected();

      startPinging();
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
   private synchronized void handleConnectionException(Exception e) throws XmlBlasterException
   {
      if (noConnect) {// LoginThread tried already and gave up
         if (isInFailSaveMode())
            Log.error(ME, "Can't establish connection to xmlBlaster, pinging retries = " + retries + " exceeded.");
         else
            Log.error(ME, "Can't establish connection to xmlBlaster, no fail save mode.");
         throw new XmlBlasterException("NoConnect", e.toString()); // Client may choose to exit
      }

      if (isInFailSaveMode()) {
         synchronized(pollingMonitor) {
            if (isReconnectPolling)
               throw new XmlBlasterException("TryingReconnect", "Still trying to find xmlBlaster again ..."); // Client may hope on reconnect

            if (numLogins == 0L) {
               doLoginPolling();
            }
            else {
               Log.error(ME, "Lost connection to xmlBlaster server: " + e.toString());
               driver.init();
               clientProblemCallback.lostConnection(); // notify client
               doLoginPolling();
               throw new XmlBlasterException("TryingReconnect", "Trying to find xmlBlaster again ..."); // Client may hope on reconnect
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
      Log.info(ME, "Going to poll for xmlBlaster and queue your messages ...");
      isReconnectPolling = true;
      LoginThread lt = new LoginThread(this, retryInterval, retries);
      lt.start();
   }


   /**
    * Start a never ending ping thread
    */
   private void startPinging()
   {
      if (pingInterval > 0L && pingThread == null) {
         pingThread = new PingThread(this, pingInterval);
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
    */
   public synchronized boolean logout()
   {
      return disconnect(new DisconnectQos());
   }


   /**
    * Logout from the server.
    * The callback server is removed as well, releasing all CORBA/RMI/XmlRpc threads.
    * Note that this kills the server ping thread as well (if in fail save mode)
    * @return true successfully logged out
    *         false failure on logout
    */
   public synchronized boolean disconnect(DisconnectQos qos)
   {
      if (Log.CALL) Log.call(ME, "logout() ...");

      killPing();

      if (!isLoggedIn()) {
         if (!isInFailSaveMode() || recorder.size() == 0)
            Log.warn(ME, "No logout, you are not logged in");
         else
            Log.warn(ME, "Logout! Please note that there are " + recorder.size() + " unsent invokations/messages in the queue");
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
         boolean ret = driver.logout();
         Log.info(ME, "Successful logout");
         return ret;
      }

      try {
         driver.logout();
      } catch(Exception e) {
         Log.warn(ME, e.toString());
         e.printStackTrace();
      }

      return false;
   }

   /**
    * Shut down the callback server.
    * Is called by logout() automatically.
    *
    * @return true CB server successfully shut down
    *         false failure on shutdown
    */
   public synchronized boolean shutdown()
   {
      try {
         return driver.shutdown();
      } catch(Exception e) {
         Log.warn(ME, e.toString());
         e.printStackTrace();
         return false;
      }
   }


   /**
    * @return true if you are logged in
    */
   public boolean isLoggedIn()
   {
      return driver.isLoggedIn();
   }


   /**
    * This is the callback method (I_Callback) invoked from xmlBlaster
    * informing the client in an asynchronous mode about a new message.
    * <p />
    *
    * @param loginName The name to whom the callback belongs
    * @param updateKey The arrived key
    * @param content   The arrived message content
    * @param qos       Quality of Service of the MessageUnit
    */
   public void update(String loginName, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS) throws XmlBlasterException
   {
      //The boss should not be interested in cache updates
      if (Log.CALL) Log.call(ME, "Entering update(" + ((cache != null) ? "using cache" : "no cache") + ") ...");
      boolean forCache = false;
      if( cache != null ) {
         forCache = cache.update(updateQoS.getSubscriptionId(), updateKey, content, updateQoS);
      }
      if (!forCache) {

         Object obj = null;
         synchronized (callbackMap) {
            obj = callbackMap.get(updateQoS.getSubscriptionId());
         }

         if (obj != null) {
            // If a special callback was specified for this subscription:
            I_Callback cb = (I_Callback)obj;
            cb.update(loginName, updateKey, content, updateQoS); // deliver the update to our client
         }
         else if (updateClient != null) {
            // If a general callback was specified on login:
            updateClient.update(loginName, updateKey, content, updateQoS); // deliver the update to our client
         }

      }
   }


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
    *            public void update(String name, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS) {
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
    * @return oid    The oid of your subscribed Message<br>
    *                If you subscribed using a query, the subscription ID of this<br>
    *                query handling object (SubscriptionInfo.getUniqueKey()) is returned.<br>
    *                You should use this oid if you wish to unSubscribe()<br>
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
            XmlKey key = new XmlKey(xmlKey);
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
               Log.info(ME,"New Entry in Cache created (subId="+subId+")");
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
         driver.ping();
         if (Log.CALL) Log.call(ME, "ping success() ...");
         return;
      } catch(ConnectionException e) {
         if (Log.TRACE) Log.trace(ME, "ping failed, xmlBlaster seems to be down, try to reactivate connection ...");
         try {
            handleConnectionException(e);
         } catch(XmlBlasterException ep) {
            if (Log.TRACE) Log.trace(ME, "Exception in ping! " + ep.reason);
         }
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
      private final long RETRY_INTERVAL; // would this be smarter? XmlBlasterProperty.get("Failsave.retryInterval", 4000L);
      private final int RETRIES;         // -1 = forever


      /**
       * @param retryInterval How many milli seconds sleeping before we retry a connection
       * @param retries Number of retries if connection cannot directly be established
       */
      LoginThread(XmlBlasterConnection con, long retryInterval, int retries) {
         this.con = con;
         this.RETRY_INTERVAL = retryInterval;
         this.RETRIES = retries;
         if (Log.CALL) Log.call(ME, "Entering constructor retryInterval=" + retryInterval + " millis and retries=" + retries);
      }

      public void run() {
         Log.info(ME, "Polling for xmlBlaster server");
         con.isReconnectPolling = true;
         for (int ii=0; ii<RETRIES || RETRIES==-1; ii++) {
            try {
               con.loginRaw();
               Log.info(ME, "Success, a new connection is established.");
               con.isReconnectPolling = false;
               return;
            } catch(Exception e) {
               Log.warn(ME, "No connection established, the xmlBlaster server still seems to be down");
               try {
                  Thread.currentThread().sleep(RETRY_INTERVAL);
               } catch (InterruptedException i) { }
            }
         }
         con.noConnect = true; // communicate back to XmlBlasterConnection that we give up
         Log.info(ME, "max polling for xmlBlaster server done, no success");
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
         Log.info(ME, "Pinging xmlBlaster server");
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
    *   java javaclients.ClientSub -client.failSave.retryInterval 5000
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
         Log.warn(ME, "I_ConnectionProblems: Lost connection to xmlBlaster");
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
               Log.info(ME, "We were lucky, reconnected to xmlBlaster, sending backup " + xmlBlasterConnection.queueSize() + " messages ...");
               xmlBlasterConnection.flushQueue();
            }
            else
               Log.info(ME, "We were lucky, reconnected to xmlBlaster, no backup messages to flush");
         }
         catch (XmlBlasterException e) {
            Log.error(ME, "Sorry, flushing of backup messages failed, they are lost: " + e.toString());
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
      text += "   -client.protocol    Specify a protocol to talk with xmlBlaster, 'IOR' or 'RMI' or 'XML-RPC'.\n";
      text += "                       Current setting is '" + XmlBlasterProperty.get("client.protocol", "IOR") + "'. See below for protocol settings.\n";
      text += "\n";
      text += "Security features:\n";
      text += "   -Security.Client.DefaultPlugin \"gui,1.0\"\n";
      text += "                       Force the given authentication schema, here the GUI is enforced\n";
      text += "                       Clients can overwrite this with ConnectQos.java\n";
      text += "\n";
      text += "Fail Save Mode:\n";
      text += "   -client.failSave.retryInterval   How many milli seconds sleeping before we retry a connection [5000]\n";
      text += "   -client.failSave.pingInterval    How many milli seconds sleeping between the pings [10 * 1000]\n";
      text += "   -client.failSave.retries         Number of retries if connection cannot directly be established [-1] (-1 is forever)\n";
      text += "   -client.failSave.maxInvocations  How many messages shall we queue max (using the InvocationRecorder) [10000]\n";
      Log.plain(text);
      Log.plain(CorbaConnection.usage());
      Log.plain(RmiConnection.usage());
      Log.plain(XmlRpcConnection.usage());
   }

} // class XmlBlasterConnection
