/*------------------------------------------------------------------------------
Name:      CorbaConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: CorbaConnection.java,v 1.48 2000/05/18 17:21:02 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.*;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;
import org.xmlBlaster.protocol.corba.authenticateIdl.AuthServer;
import org.xmlBlaster.protocol.corba.authenticateIdl.AuthServerHelper;

import org.omg.CosNaming.*;
import java.applet.Applet;
import java.util.Properties;


/**
 * This is a helper class, helping a Java client to connect to xmlBlaster
 * using IIOP (CORBA).
 * <p>
 * Please note that you don't need to use this wrapper, you can use the raw CORBA
 * interface as well. You can also hack your own little wrapper, which does exactly
 * what you want.
 * <p>
 * There is a constructor for applets, and standalone Java clients.
 * <p />
 * If you need a fail save client, you can invoke the xmlBlaster CORBA methods
 * through this class as well (for example use corbaConnection.publish() instead of the direct
 * CORBA server.publish()).
 * <p />
 * You need to call initFailSave(), to switch this on, and pass it your implementation of I_ConnectionProblems.<br />
 * If suddenly the xmlBlaster server disappears, CorbaConnection
 * queues your messages locally, and starts polling to find the server again. You will get
 * an Exception and a callback through I_ConnectionProblems and may decide to continue with your work (or not).<br />
 * As soon as the connection can be established again, you are informed by I_ConnectionProblems.reConnect()
 * you may send some initial messages (as on startup of your client) and invoke
 * corbaConnection.flushQueue() to send all messages collected during disruption in the correct order or
 * corbaConnection.resetQueue() to discard the queued messages.
 * to xmlBlaster.<br />
 * One drawback is, that the return values of your requests are lost, since you were none blocking
 * continuing during the connection was lost.
 * <p />
 * You can have a look at xmlBlaster/testsuite/org/xmlBlaster/TestFailSave.java to find out how it works
 * <p />
 * You should set jacorb.retries=0  in $HOME/.jacorb_properties if you use the fail save mode
 * <p />
 * If you specify the last argument in initFailSave() to bigger than 0 milliseconds,
 * a thread is installed which does a ping to xmlBlaster (tests the connection) with the given sleep interval.
 * If the ping fails, the login polling is automatically activated.
 * <p />
 * If you want to connect from a servlet, please use the framework in xmlBlaster/src/java/org/xmlBlaster/protocol/http
 * @version $Revision: 1.48 $
 * @author $Author: ruff $
 */
public class CorbaConnection implements ServerOperations
{
   private static final String ME = "CorbaConnection";
   protected String[] args = null;
   protected org.omg.CORBA.ORB orb = null;
   protected NamingContext nameService = null;
   protected AuthServer authServer = null;
   protected Server xmlBlaster = null;
   protected BlasterCallback callback = null;
   protected String loginName = null;
   private String passwd = null;
   protected LoginQosWrapper loginQos = null;

   /** queue all the messages, and play them back through interface ServerOperations */
   private InvocationRecorder recorder = null;

   /** This interface needs to be implemented by the client in fail save mode
       The client gets notified about abnormal connection loss or reconnect */
   private I_ConnectionProblems clientCallback = null;

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

   private MessageUnitContainer[] dummyMArr = new MessageUnitContainer[0];
   private String[] dummySArr = new String[0];
   private String dummyS = "";

   /** Cache **/
   private BlasterCache cache = null;


   /**
    * CORBA client access to xmlBlaster (default behavior).
    * <p />
    * If you need a fails save mode, call initFailSave()
    */
   public CorbaConnection()
   {
      args = new String[0];  // dummy
      orb = org.omg.CORBA.ORB.init(args, null);
   }


   /**
    * CORBA client access to xmlBlaster for <strong>normal client applications</strong>.
    * <p />
    * @param arg  parameters given on command line
    * <ul>
    *    <li>-ior  IOR string is directly given</li>
    *    <li>-iorFile IOR string is given through a file</li>
    *    <li>-iorHost hostName or IP where xmlBlaster is running</li>
    *    <li>-iorPort where the internal xmlBlaster-http server publishes its IOR (defaults to 7609)</li>
    *    <li>-ns true/false, if a naming service shall be used</li>
    * </ul>
    */
   public CorbaConnection(String[] arg)
   {
      args = arg;
      Property.addArgs2Props(Property.getProps(), args); // enforce that the args are added to the xmlBlaster.properties hash table
      orb = org.omg.CORBA.ORB.init(args, null);
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
    *     &lt;PARAM name=org.omg.CORBA.ORBClass value=jacorb.orb.ORB>
    *     &lt;PARAM name=org.omg.CORBA.ORBSingletonClass value=jacorb.orb.ORBSingleton>
    *     &lt;PARAM name=SVCnameroot value=xmlBlaster-Authenticate>
    *     &lt;/APPLET>
    *  </pre>
    * @param ap  Applet handle
    */
   public CorbaConnection(Applet ap)
   {
      // try to force to use JacORB instead of builtin CORBA:
      String orbClassName = "jacorb.orb.ORB";
      String orbSingleton = "jacorb.orb.ORBSingleton";
      java.util.Properties props = new java.util.Properties();
      props.put("org.omg.CORBA.ORBClass", orbClassName);
      props.put("org.omg.CORBA.ORBSingletonClass", orbSingleton);

      Log.info(ME, "Using ORB=" + orbClassName + " and ORBSingleton=" + orbSingleton);

      orb = org.omg.CORBA.ORB.init(ap, props);
   }


   /**
    * Setup the fail save mode.
    *
    * @param connCallback The interface to notify the client about problems
    * @param retryInterval How many milli seconds sleeping before we retry a connection
    * @param retries Number of retries if connection cannot directly be established
    *                passing -1 does polling forever
    * @param maxInvocations How many messages shall we queue max
    * @param pingInterval How many milli seconds sleeping between the pings<br />
    *                     < 1 switches pinging off
    */
   public void initFailSave(I_ConnectionProblems connCallback, long retryInterval, int retries, int maxInvocations, long pingInterval)
   {
      if (Log.CALLS) Log.calls(ME, "Initializing fail save mode: retryInterval=" + retryInterval + ", retries=" + retries + ", maxInvocations=" + maxInvocations + ", pingInterval=" + pingInterval);
      this.clientCallback = connCallback;
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
      if (Log.CALLS) Log.calls(ME, "Initializing cache: size=" + size);
      cache = new BlasterCache( this, size );
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
    * Accessing the orb handle.
    * @return org.omg.CORBA.ORB
    */
   public org.omg.CORBA.ORB getOrb()
   {
      return orb;
   }


   /**
    * Accessing the xmlBlaster handle.
    * @return Server
    * @exception if not logged in
    */
   public Server getXmlBlaster() throws XmlBlasterException
   {
      if (xmlBlaster == null)
         throw new XmlBlasterException(ME + ".NotLoggedIn", "Sorry, no xmlBlaster handle available, please login first.");
      return xmlBlaster;
   }


   /**
    * Locate the CORBA Name Service.
    * <p />
    * The found name service is cached, for better performance in subsequent calls
    * @return NamingContext, reference on name service
    * @exception XmlBlasterException
    *                    CORBA error handling if no naming service is found
    */
   NamingContext getNamingService() throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "getNamingService() ...");
      if (nameService != null)
         return nameService;

      if (orb == null)
         Log.panic(ME, "orb==null, internal problem");

      // Get a reference to the Name Service, CORBA compliant:
      org.omg.CORBA.Object nameServiceObj = null;
      try {
         nameServiceObj = orb.resolve_initial_references("NameService");
      }
      catch (Exception e) {
         String text = "Can't access naming service, is there any running?\n" +
                       " - try to specify '-iorFile <fileName>' if server is running on same host (not using any naming service)\n" +
                       " - try to specify '-iorHost <hostName> -iorPort 7609' to locate xmlBlaster (not using any naming service)\n" +
                       " - or contact your system administrator to start a naming service";
         Log.warning(ME + ".NoNameService", text);
         throw new XmlBlasterException(ME + ".NoNameService", text);
      }
      if (nameServiceObj == null) {
         if (!isReconnectPolling)
            Log.warning(ME + ".NoNameService", "Can't access naming service (null), is there any running?");
         throw new XmlBlasterException(ME + ".NoNameService", "Can't access naming service (null), is there any running?");
      }
      if (Log.TRACE) Log.trace(ME, "Successfully accessed initial orb references for naming service (IOR)");

      try {
         nameService = org.omg.CosNaming.NamingContextHelper.narrow(nameServiceObj);
         if (nameService == null) {
            Log.error(ME + ".NoNameService", "Can't access naming service (narrow problem)");
            throw new XmlBlasterException(ME + ".NoNameService", "Can't access naming service (narrow problem)");
         }
         if (Log.TRACE) Log.trace(ME, "Successfully narrowed handle for naming service");
         return nameService; // Note: the naming service IOR is successfully evaluated (from a IOR),
                             // but it is not sure that the naming service is really running
      }
      catch (Exception e) {
         Log.warning(ME + ".NoNameService", "Can't access naming service");
         throw new XmlBlasterException(ME + ".NoNameService", e.toString());
      }
   }


   /**
    * Access the authentication service.
    * <p />
    * There are several ways to bootstrap the authentication service:
    * <br />
    * <ul>
    *    <li>Give the authentication service string-IOR at command line, e.g.<br />
    *        <code>   -ior "IOR:0000..."</code><br />
    *        or giving a file name<br />
    *        <code>   -iorFile yourIorFile</code></li>
    *    <li>Give the xmlBlaster host and iorPort where xmlBlaster-Authenticate serves the IOR via http, give at command line e.g.
    *        <code>   -iorHost server.xmlBlaster.org  -iorPort 7609</code></li>
    *    <li>Try to find a naming service which knows about 'xmlBlaster-Authenticate'</li>
    * </ul>
    * <p />
    * @return a handle on the AuthServer IDL interface
    *
    */
   AuthServer getAuthenticationService() throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "getAuthenticationService() ...");
      if (authServer != null) {
         return authServer;
      }


      // 1) check if argument -IOR at program startup is given
      String authServerIOR = Property.getProperty("ior", (String)null);  // -ior IOR string is directly given
      if (authServerIOR != null) {
         authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
         Log.info(ME, "Accessing xmlBlaster using your given IOR string");
         return authServer;
      }
      if (Log.TRACE) Log.trace(ME, "No -ior ...");

      String authServerIORFile = Property.getProperty("iorFile", (String)null);  // -iorFile IOR string is given through a file
      if (authServerIORFile != null) {
         authServerIOR = FileUtil.readAsciiFile(authServerIORFile);
         authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
         Log.info(ME, "Accessing xmlBlaster using your given IOR file " + authServerIORFile);
         return authServer;
      }
      if (Log.TRACE) Log.trace(ME, "No -iorFile ...");


      // 2) check if argument -iorHost <hostName or IP> -iorPort <number> at program startup is given
      String iorHost = Property.getProperty("iorHost", "localhost");
      int iorPort = Property.getProperty("iorPort", org.xmlBlaster.Main.DEFAULT_HTTP_PORT); // 7609
      if (iorHost != null && iorPort > 0) {
         try {
            authServerIOR = getAuthenticationServiceIOR(iorHost, iorPort);
            authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
            Log.info(ME, "Accessing xmlBlaster AuthServer IOR using builtin http connection, host " + iorHost + " and port " + iorPort);
            return authServer;
         }
         catch(Exception e) {
            if (!isReconnectPolling)
               Log.warning(ME, "XmlBlaster not found on host " + iorHost + " and port " + iorPort + ". Trying to find a naming service ...");
         }
      }
      if (Log.TRACE) Log.trace(ME, "No -iorHost / iorPort ...");

      String text = "Can't access xmlBlaster Authentication Service, is the server running and ready?\n" +
                  " - try to specify '-iorFile <fileName>' if server is running on same host\n" +
                  " - try to specify '-iorHost <hostName> -iorPort 7609' to locate xmlBlaster\n" +
                  " - or contact your system administrator to start a naming service";

      // 3) asking Name Service CORBA compliant
      boolean useNameService = Property.getProperty("ns", true);  // -ns default is to ask the naming service
      if (useNameService) {

         try {
            NamingContext nc = getNamingService();
            NameComponent [] name = new NameComponent[1];
            name[0] = new NameComponent();
            name[0].id = "xmlBlaster-Authenticate";
            name[0].kind = "MOM";
            authServer = AuthServerHelper.narrow(nc.resolve(name));
            Log.info(ME, "Accessing xmlBlaster using a naming service.");
            return authServer;
         }
         catch(Exception e) {
            if (isInFailSaveMode()) {
               if (!isReconnectPolling)  Log.warning(ME + ".NoAuthService", text);
            }
            else {
               Log.error(ME + ".NoAuthService", text);
               throw new XmlBlasterException(ME + ".NoAuthService", text);
            }
         }
      }
      if (Log.TRACE) Log.trace(ME, "No -ns ...");

      throw new XmlBlasterException(ME + ".NoAuthService", text);
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
   public Server login(String loginName, String passwd, LoginQosWrapper qos) throws XmlBlasterException
   {
      return login(loginName, passwd, qos, null);
   }


   /**
    * Login to the server, providing your own BlasterCallback implementation
    * with default Quality of Service for this client.
    * <p />
    * @param loginName The login name for xmlBlaster
    * @param passwd    The login password for xmlBlaster
    * @param callback  The Callback interface of this client or null if none is used
    * @exception       XmlBlasterException if login fails
    */
    /*  !!! old stuff
   public Server login(String loginName, String passwd, BlasterCallback callback) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "login(" + loginName + ") ...");
      if (xmlBlaster != null) {
         Log.warning(ME, "You are already logged in, returning cached handle on xmlBlaster");
         return xmlBlaster;
      }

      this.callback = callback;
      this.loginName = loginName;
      this.passwd = passwd;
      this.loginQos = qos;

      loginRaw();
      return xmlBlaster;
   }
      */


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
   public Server login(String loginName, String passwd, LoginQosWrapper qos, I_Callback client) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "login(" + loginName + ") ...");
      if (xmlBlaster != null) {
         Log.warning(ME, "You are already logged in, returning cached handle on xmlBlaster");
         return xmlBlaster;
      }

      this.loginName = loginName;
      this.passwd = passwd;
      if (qos == null)
         this.loginQos = new LoginQosWrapper();
      else
         this.loginQos = qos;

      if (client != null) {
         this.callback = createCallbackServer(new DefaultCallback(loginName, client, cache));

         // Add the stringified IOR to QoS ...
         CallbackAddress addr = new CallbackAddress("IOR");
         addr.setAddress(orb.object_to_string(this.callback));
         loginQos.addCallbackAddress(addr);
         if (Log.TRACE) Log.trace(ME, "Success, exported BlasterCallback Server interface for " + loginName);
      }

      loginRaw();
      return xmlBlaster;
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
      if (Log.CALLS) Log.calls(ME, "loginRaw(" + loginName + ") ...");
      try {
         AuthServer authServer = getAuthenticationService();
         xmlBlaster = authServer.login(loginName, passwd, loginQos.toXml());
         numLogins++;
         if (Log.TRACE) Log.trace(ME, "Success, login for " + loginName);
         if (Log.DUMP) Log.dump(ME, loginQos.toXml());
      } catch(XmlBlasterException e) {
         if (Log.TRACE) Log.trace(ME, "Login failed for " + loginName + ", numLogins=" + numLogins);
         if (numLogins == 0)
            startPinging();
         throw e;
      }
      if (isReconnectPolling && numLogins > 0)
         clientCallback.reConnected();

      startPinging();
   }


   /**
    * If we lost the connection to xmlBlaster, handle it
    */
   private synchronized void handleConnectionException(Exception e) throws XmlBlasterException
   {
      if (noConnect) // LoginThread tried already and gave up
         throw new XmlBlasterException("NoConnect", e.toString()); // Client may choose to exit

      if (isInFailSaveMode()) {
         if (xmlBlaster != null) {
            // the first time a org.omg.CORBA.COMM_FAILURE Exception is thrown
            // then NullPointerExceptions (because of xmlBlaster==null)
            Log.error(ME, "Lost connection to xmlBlaster server: " + e.toString());
            authServer = null;
            xmlBlaster = null;
            clientCallback.lostConnection(); // notify client
            doLoginPolling();
            throw new XmlBlasterException("TryingReconnect", "Trying to find xmlBlaster again ..."); // Client may hope on reconnect
         }
         if (numLogins == 0L) {
            doLoginPolling();
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
      return loginName;
   }


   /**
    * Logout from the server.
    * <p />
    * Note that this kills the server ping thread as well (if in fail save mode)
    * @return true successfully logged out
    *         false failure on logout
    * @deprecated Use logout() without arguments
    */
   public boolean logout(Server xmlBlaster)
   {
      killPing();
      return logout();
   }


   /**
    * Logout from the server.
    * @return true successfully logged out
    *         false failure on logout
    */
   public boolean logout()
   {
      if (Log.CALLS) Log.calls(ME, "logout() ...");

      if (xmlBlaster == null) {
         if (!isInFailSaveMode() || recorder.size() == 0)
            Log.warning(ME, "No logout, you are not logged in");
         else
            Log.warning(ME, "Logout! Please note that there are " + recorder.size() + " unsent invokations/messages in the queue");
         return false;
      }

      try {
         authServer.logout(xmlBlaster);
         xmlBlaster = null;
         return true;
      } catch(XmlBlasterException e) {
         Log.warning(ME, "XmlBlasterException: [" + e.id + "]" + " " + e.reason);
      } catch(Exception e) {
         Log.warning(ME, e.toString());
         e.printStackTrace();
      }

      xmlBlaster = null;
      return false;
   }


   /**
    * @return true if you are logged in
    */
   public boolean isLoggedIn()
   {
      return xmlBlaster != null;
   }


   /**
    * Building a Callback server, using the tie approach.
    *
    * @return the BlasterCallback server
    * @exception XmlBlasterException if the BlasterCallback server can't be created
    */
   public BlasterCallback createCallbackServer(BlasterCallbackOperations callbackImpl) throws XmlBlasterException
   {
      org.omg.PortableServer.POA rootPOA;
      BlasterCallbackPOATie callbackTie = new BlasterCallbackPOATie(callbackImpl);

      // Getting the default POA implementation "RootPOA"
      try {
         rootPOA = org.omg.PortableServer.POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
      } catch (Exception e) {
         Log.error(ME + ".CallbackCreationError", "Can't create a BlasterCallback server, RootPOA not found: " + e.toString());
         throw new XmlBlasterException(ME + ".CallbackCreationError", e.toString());
      }

      try {
         callback = BlasterCallbackHelper.narrow(rootPOA.servant_to_reference( callbackTie ));
         rootPOA.the_POAManager().activate();
         // necessary for orbacus
         if (orb.work_pending()) orb.perform_work();
         return callback;
      } catch (Exception e) {
         Log.error(ME + ".CallbackCreationError", "Can't create a BlasterCallback server, narrow failed: " + e.toString());
         throw new XmlBlasterException(ME + ".CallbackCreationError", e.toString());
      }
   }


   /**
    * To avoid the name service, one can access the AuthServer IOR directly
    * using a http connection.
    * <p />
    * @param host the host running xmlBlaster
    * @param iorPort the port on which the IOR is served (the xmlBlaster mini http server)
    */
   private String getAuthenticationServiceIOR(String iorHost, int iorPort) throws Exception
   {
      java.net.URL nsURL = new java.net.URL("http", iorHost, iorPort, "/AuthenticationService.ior");
      // Note: the file name /AuthenticationService.ior is ignored in the current server implementation
      java.io.InputStream nsis = nsURL.openStream();
      byte[] bytes = new byte[4096];
      java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
      int numbytes;
      while (nsis.available() > 0 && (numbytes = nsis.read(bytes)) > 0) {
         bos.write(bytes, 0, numbytes);
      }
      nsis.close();
      String ior = bos.toString();
      if (!ior.startsWith("IOR:"))
         ior = "IOR:000" + ior; // hack for JDK 1.1.x, where the IOR: is cut away from ByteReader ??? !!!
      if (Log.TRACE) Log.trace(ME, "Retrieved authentication service IOR='" + ior + "'");
      return ior;
   }


   /**
    * Enforced by ServerOperations interface (fail save mode).
    * see explanations of publish() method.
    * @see xmlBlaster.idl
    */
   public final String subscribe(String xmlKey, String qos) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "subscribe() ...");
      try {
         return xmlBlaster.subscribe(xmlKey, qos);
      } catch(XmlBlasterException e) {
         throw e;
      } catch(Exception e) {
         if (recorder != null) recorder.subscribe(xmlKey, qos);
         handleConnectionException(e);
      }
      return ""; // never reached, there is always an exception thrown
   }


   /**
    * Enforced by ServerOperations interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public final void unSubscribe(String xmlKey, String qos) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "unSubscribe() ...");
      try {
         xmlBlaster.unSubscribe(xmlKey, qos);
      } catch(XmlBlasterException e) {
         throw e;
      } catch(Exception e) {
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
    * Enforced by ServerOperations interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public final String publish(MessageUnit msgUnit, String qos) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Publishing ...");
      try {
         return xmlBlaster.publish(msgUnit, qos);
      } catch(XmlBlasterException e) {
         if (Log.TRACE) Log.trace(ME, "XmlBlasterException: " + e.reason);
         throw e;
      } catch(Exception e) { // org.omg.CORBA.COMM_FAILURE (others as well??)
                             // NullPointerException the following calls, because of xmlBlaster is set to null
         if (recorder != null) recorder.publish(msgUnit, qos);
         handleConnectionException(e);
      }
      return dummyS; // never reached, there is always an exception thrown
   }


   /**
    * Enforced by ServerOperations interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public String[] publishArr(MessageUnit [] msgUnitArr, String [] qosArr) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "publishArr() ...");
      try {
         return xmlBlaster.publishArr(msgUnitArr, qosArr);
      } catch(XmlBlasterException e) {
         if (Log.TRACE) Log.trace(ME, "XmlBlasterException: " + e.reason);
         throw e;
      } catch(Exception e) {
         if (recorder != null) recorder.publishArr(msgUnitArr, qosArr);
         handleConnectionException(e);
      }
      return dummySArr;
   }


   /**
    * Enforced by ServerOperations interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public final String[] erase(String xmlKey, String qos) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "erase() ...");
      try {
         return xmlBlaster.erase(xmlKey, qos);
      } catch(XmlBlasterException e) {
         throw e;
      } catch(Exception e) {
         if (recorder != null) recorder.erase(xmlKey, qos);
         handleConnectionException(e);
      }
      return dummySArr;
   }


   /**
    * Enforced by ServerOperations interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public final MessageUnitContainer[] get(String xmlKey, String qos) throws XmlBlasterException
   {
      MessageUnitContainer[] units = null;
      if (Log.CALLS) Log.calls(ME, "get() ...");
      try {
         //Is cache installed?
         if (cache != null) {
            units = cache.get( xmlKey, qos );
            //not found in cache
            if( units == null ) {
               units = xmlBlaster.get(xmlKey, qos);              //get messages from xmlBlaster (synchronous)
               String subId = xmlBlaster.subscribe(xmlKey, qos); //subscribe to this messages (asynchronous)
               cache.newEntry(subId, xmlKey, units);             //fill messages to cache
               Log.info(ME,"New Entry in Cache created (subId="+subId+")");
            }
         }
         else
            units = xmlBlaster.get(xmlKey, qos);

                return units;

      } catch(XmlBlasterException e) {
         throw e;
      } catch(Exception e) {
         if (recorder != null) recorder.get(xmlKey, qos);
         handleConnectionException(e);
      }
      return dummyMArr;
   }


   /**
    * Enforced by ServerOperations interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public final void setClientAttributes(String clientName, String xmlAttr, String qos) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "setClientAttributes() ...");
      try {
         xmlBlaster.setClientAttributes(clientName, xmlAttr, qos);
      } catch(XmlBlasterException e) {
         throw e;
      } catch(Exception e) {
         if (recorder != null) recorder.setClientAttributes(clientName, xmlAttr, qos);
         handleConnectionException(e);
      }
   }


   /**
    * Enforced by ServerOperations interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public void ping()
   {
      if (isReconnectPolling)
         return;
      try {
         xmlBlaster.ping();
         if (Log.TRACE) Log.trace(ME, "ping success() ...");
         return;
      } catch(Exception e) {
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
         Log.warning(ME, "Internal error: don't call queueSize(), you are not in fail save mode");
         return 0;
      }

      return recorder.size();
   }


   public void flushQueue() throws XmlBlasterException
   {
      if (recorder == null) {
         Log.warning(ME, "Internal error: don't call flushQueue(), you are not in fail save mode");
         return;
      }

      recorder.pullback(0L, 0L, 0.);
   }


   public void resetQueue()
   {
      if (recorder == null) {
         Log.warning(ME, "Internal error: don't call flushQueue(), you are not in fail save mode");
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
      private CorbaConnection corbaConnection;
      private final long RETRY_INTERVAL; // would this be smarter? Property.getProperty("Failsave.retryInterval", 4000L);
      private final int RETRIES;         // -1 = forever


      /**
       * @param retryInterval How many milli seconds sleeping before we retry a connection
       * @param retries Number of retries if connection cannot directly be established
       */
      LoginThread(CorbaConnection corbaConnection, long retryInterval, int retries) {
         this.corbaConnection = corbaConnection;
         this.RETRY_INTERVAL = retryInterval;
         this.RETRIES = retries;
         if (Log.CALLS) Log.calls(ME, "Entering constructor retryInterval=" + retryInterval + " millis and retries=" + retries);
      }

      public void run() {
         Log.info(ME, "Polling for xmlBlaster server");
         corbaConnection.isReconnectPolling = true;
         for (int ii=0; ii<RETRIES || RETRIES==-1; ii++) {
            try {
               corbaConnection.loginRaw();
               Log.info(ME, "Success, a new connection is established.");
               corbaConnection.isReconnectPolling = false;
               return;
            } catch(Exception e) {
               Log.warning(ME, "No connection established, the xmlBlaster server still seems to be down");
               try {
                  Thread.currentThread().sleep(RETRY_INTERVAL);
               } catch (InterruptedException i) { }
            }
         }
         corbaConnection.noConnect = true; // communicate back to CorbaConnection that we give up
         Log.info(ME, "max polling for xmlBlaster server done, no success");
      }
   } // class LoginThread


   /**
    * Ping the xmlBlaster server, to test if connection is alive
    */
   private class PingThread extends Thread
   {
      private final String ME = "PingThread";
      private CorbaConnection corbaConnection;
      private final long PING_INTERVAL;
      boolean pingRunning = true;

      /**
       * @param pingInterval How many milli seconds sleeping between the pings
       */
      PingThread(CorbaConnection corbaConnection, long pingInterval) {
         this.corbaConnection = corbaConnection;
         this.PING_INTERVAL = pingInterval;
         if (Log.CALLS) Log.calls(ME, "Entering constructor ping interval=" + pingInterval + " millis");
      }
      public void run() {
         Log.info(ME, "Pinging xmlBlaster server");
         while (pingRunning) {
            try {
               corbaConnection.ping();
            } catch(Exception e) {
            }
            try {
               Thread.currentThread().sleep(PING_INTERVAL);
            } catch (InterruptedException i) { }
         }
      }
   } // class PingThread


   /**
    * Command line usage.
    * <p />
    * These variables may be set in xmlBlaster.properties as well.
    * Don't use the "-" prefix there.
    */
   public static void usage()
   {
      Log.plain(ME, "");
      Log.plain(ME, "Client connection options:");
      Log.plain(ME, "   -ior                The IOR string.");
      Log.plain(ME, "   -iorHost            The host where to find xmlBlaster [localhost]");
      Log.plain(ME, "   -iorPort            The port where xmlBlaster publishes its IOR [7609]");
      Log.plain(ME, "   -iorFile <fileName> A file with the xmlBlaster IOR.");
      Log.plain(ME, "   -ns <true/false>    Try to access xmlBlaster through a naming service [true]");
      Log.plain(ME, "");
   }
} // class CorbaConnection


/**
 * Example for a callback implementation.
 * <p />
 * You can use this default callback handling with your clients,
 * but if you need other handling of callbacks, take a copy
 * of this Callback implementation and add your own code.
 */
class DefaultCallback implements BlasterCallbackOperations
{
   private final String ME;
   private final I_Callback boss;
   private final String loginName;
   private final BlasterCache cache;

   /**
    * Construct a persistently named object.
    */
   public DefaultCallback(String name, I_Callback boss, BlasterCache cache)
   {
      this.ME = "DefaultCallback-" + name;
      this.boss = boss;
      this.loginName = name;
      this.cache = cache;
      if (Log.CALLS) Log.trace(ME, "Entering constructor with argument");
   }


   /**
    * This is the callback method invoked from the server
    * informing the client in an asynchronous mode about new messages.
    * <p />
    * You don't need to use this little method, but it nicely converts
    * the raw CORBA BlasterCallback.update() with raw Strings and arrays
    * in corresponding objects and calls for every received message
    * the I_Callback.update().
    * <p />
    * So you should implement in your client the I_Callback interface -
    * suppling the update() method.
    *
    * @param loginName        The name to whom the callback belongs
    * @param msgUnit      Contains a MessageUnit structs (your message)
    * @param qos              Quality of Service of the MessageUnit
    */
   public void update(MessageUnit[] msgUnitArr, String[] qos_literal_Arr)
   {
      if (Log.CALLS) Log.calls(ME, "Receiving update of " + msgUnitArr.length + " message ...");

      if (msgUnitArr.length == 0) {
         Log.warning(ME, "Entering update() with 0 messages");
         return;
      }

      for (int ii=0; ii<msgUnitArr.length; ii++) {
         MessageUnit msgUnit = msgUnitArr[ii];
         UpdateKey updateKey = null;
         UpdateQoS updateQoS = null;
         byte[] content = msgUnit.content;
         try {
            updateKey = new UpdateKey();
            updateKey.init(msgUnit.xmlKey);
            updateQoS = new UpdateQoS(qos_literal_Arr[ii]);
         } catch (XmlBlasterException e) {
            Log.error(ME, e.reason);
         }

         // Now we know all about the received message, dump it or do some checks
         if (Log.DUMP) Log.dump("UpdateKey", "\n" + updateKey.printOn().toString());
         if (Log.DUMP) Log.dump("content", "\n" + new String(content));
         if (Log.DUMP) Log.dump("UpdateQoS", "\n" + updateQoS.printOn().toString());
         if (Log.TRACE) Log.trace(ME, "Received message [" + updateKey.getUniqueKey() + "] from publisher " + updateQoS.getSender());

         //Checking whether the Update is for the Cache or for the boss
         //The boss should not be interested in cache updates
         boolean forCache = false;
         if( cache != null ) {
            forCache = cache.update(updateQoS.getSubscriptionId(), updateKey.toXml(), content);
         }
         if (!forCache)
            boss.update(loginName, updateKey, content, updateQoS); // Call my boss
      }
   }
} // class DefaultCallback

