/*------------------------------------------------------------------------------
Name:      CorbaConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.corba;

import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.util.JdkCompatible;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;

import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;

import org.jutils.io.FileUtil;
import org.jutils.JUtilsException;

import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.qos.address.ServerRef;
import org.xmlBlaster.protocol.corba.CorbaDriver;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.protocol.corba.serverIdl.ServerHelper;
import org.xmlBlaster.protocol.corba.authenticateIdl.AuthServer;
import org.xmlBlaster.protocol.corba.authenticateIdl.AuthServerHelper;

import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExtHelper;

import java.applet.Applet;


/**
 * This is a helper class, helping a Java client to connect to xmlBlaster
 * using IIOP (CORBA).
 * <p>
 * Please note that you don't need to use this wrapper, you can use the raw CORBA
 * interface as well. You can also hack your own little wrapper, which does exactly
 * what you want.
 * <p>
 * This class converts the Corba based exception<br />
 *    org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException<br />
 * to<br />
 *    org.xmlBlaster.util.XmlBlasterException
 * <p>
 * There is a constructor for applets, and standalone Java clients.
 * <p />
 * If you need a fail save client, you can invoke the xmlBlaster CORBA methods
 * through this class as well (for example use corbaConnection.publish() instead of the direct
 * CORBA server.publish()).
 * <p />
 * You should set jacorb.retries=0  in $HOME/.jacorb_properties if you use the fail save mode
 * <p />
 * If you want to connect from a servlet, please use the framework in xmlBlaster/src/java/org/xmlBlaster/protocol/http
 * <p />
 * NOTE: JacORB 1.1 does not release the listener thread and the poa threads of the callback server
 * on orb.shutdown().<br />
 * Therefor we recycle the ORB and POA instance to avoid a thread leak.
 * The drawback is that a client for the bug being can't change the orb behavior after the
 * first time the ORB is created.<br />
 * This will be fixed as soon as possible.
 *
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class CorbaConnection implements I_XmlBlasterConnection, I_Plugin
{
   private String ME = "CorbaConnection";
   private Global glob;
   private LogChannel log;

   // HACK May,24 2000 !!! (search 'Thread leak' in this file to remove the hack again and remove the two 'static' qualifiers below.)
   // Thread leak from JacORB 1.2.2, the threads
   //   - JacORB Listener Thread
   //   - JacORB ReplyReceptor
   //   - JacORB Request Receptor
   // are never released on orb.shutdown() and rootPoa.deactivate()
   //
   // So we use a static orb and poa and recycle it.
   // The drawback is that a running client can't change the
   // orb behavior
   static protected org.omg.CORBA.ORB orb = null;

   protected NamingContextExt nameService = null;
   protected AuthServer authServer = null;
   protected Server xmlBlaster = null;
   protected String loginName = ""; //null;
   private   String passwd = null; //null;
   protected ConnectQos connectQos = null;
   protected ConnectReturnQos connectReturnQos = null;

   private   String               sessionId = null;

   private boolean firstAttempt = true;

   /**
    * Called by plugin loader which calls init(Global, PluginInfo) thereafter. 
    */
   public CorbaConnection() {
   }

   /**
    * CORBA client access to xmlBlaster for <strong>normal client applications</strong>.
    * <p />
    * @param glob  parameters given on command line
    * <ul>
    *    <li>-ior  IOR string is directly given</li>
    *    <li>-ior.file IOR string is given through a file</li>
    *    <li>-hostname hostName or IP where xmlBlaster is running</li>
    *    <li>-port where the internal xmlBlaster-http server publishes its IOR (defaults to 3412)</li>
    *    <li>-ns true/false, if a naming service shall be used</li>
    * </ul>
    */
   public CorbaConnection(Global glob) {
      init(glob, null);
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
    */
   public CorbaConnection(Global glob, Applet ap) {
       // try to force to use JacORB instead of builtin CORBA:
      String orbClassName = "org.jacorb.orb.ORB";
      String orbSingleton = "org.jacorb.orb.ORBSingleton";
      java.util.Properties props = new java.util.Properties();
      props.put("org.omg.CORBA.ORBClass", orbClassName);
      props.put("org.omg.CORBA.ORBSingletonClass", orbSingleton);

      orb = org.omg.CORBA.ORB.init(ap, props);

      init(glob, null);

      log.info(ME, "Using ORB=" + orbClassName + " and ORBSingleton=" + orbSingleton);
   }

   /** 
    * Enforced by I_Plugin
    * @return "IOR"
    */
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
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) {
      this.glob = glob;
      this.log = glob.getLog("corba");
      if (orb == null) { // Thread leak !!!
         CorbaDriver.initializeOrbEnv(glob,true);
         orb = org.omg.CORBA.ORB.init(glob.getArgs(), null);
      }
      resetConnection();
   }

   /**
    * Reset
    */
   public void resetConnection() {
      authServer   = null;
      xmlBlaster   = null;
   }

   /**
    * @return The connection protocol name "IOR"
    */
   public final String getProtocol() {
      return "IOR";
   }

   /**
    * Accessing the orb handle.
    * @return org.omg.CORBA.ORB
    */
   public org.omg.CORBA.ORB getOrb() {
      return orb;
   }

   /**
    * Accessing the xmlBlaster handle.
    * For internal use, throws a COMMUNICATION XmlBlasterException if xmlBlaster==null
    * We use this for similar handling as org.omg exceptions.
    * @return Server
    */
   private Server getXmlBlaster() throws XmlBlasterException {
      if (xmlBlaster == null) {
         if (log.TRACE) log.trace(ME, "No CORBA connection available.");
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                                       "The CORBA xmlBlaster handle is null, no connection available");
      }
      return xmlBlaster;
   }


   /**
    * Locate the CORBA Name Service.
    * <p />
    * The found name service is cached, for better performance in subsequent calls
    * @return NamingContextExt, reference on name service
    * @exception XmlBlasterException id="NoNameService"
    *                    CORBA error handling if no naming service is found
    */
   NamingContextExt getNamingService(boolean verbose) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "getNamingService() ...");
      if (nameService != null)
         return nameService;

      if (orb == null) {
         log.error(ME, "orb==null, internal problem");
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "orb==null, internal problem");
      }

      // Get a reference to the Name Service, CORBA compliant:
      org.omg.CORBA.Object nameServiceObj = null;
      try {
         nameServiceObj = orb.resolve_initial_references("NameService");
      }
      catch (Throwable e) {
         String text = "Can't access naming service, is there any running?\n" +
                       " - try to specify '-ior.file <fileName>' if server is running on same host (not using any naming service)\n" +
                       " - try to specify '-hostname <hostName> -port " + Constants.XMLBLASTER_PORT + "' to locate xmlBlaster (not using any naming service)\n" +
                       " - or contact the server administrator to start a naming service";
         if (verbose)
            log.warn(ME + ".NoNameService", text);
         throw new XmlBlasterException("NoNameService", text);
      }
      if (nameServiceObj == null) {
         throw new XmlBlasterException("NoNameService", "Can't access naming service (null), is there any running?");
      }
      // if (log.TRACE) log.trace(ME, "Successfully accessed initial orb references for naming service (IOR)");

      try {
         nameService = org.omg.CosNaming.NamingContextExtHelper.narrow(nameServiceObj);
         if (nameService == null) {
            log.error(ME + ".NoNameService", "Can't access naming service (narrow problem)");
            throw new XmlBlasterException("NoNameService", "Can't access naming service (narrow problem)");
         }
         if (log.TRACE) log.trace(ME, "Successfully narrowed handle for naming service");
         return nameService; // Note: the naming service IOR is successfully evaluated (from a IOR),
                             // but it is not sure that the naming service is really running
      }
      catch (Throwable e) {
         if (verbose) log.warn(ME + ".NoNameService", "Can't access naming service");
         throw new XmlBlasterException("NoNameService", e.toString());
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
    *        <code>   -ior.file yourIorFile</code></li>
    *    <li>Give the xmlBlaster host and port where xmlBlaster-Authenticate serves the IOR via http, give at command line e.g.
    *        <code>   -hostname server.xmlBlaster.org  -port 3412</code></li>
    *    <li>Try to find a naming service which knows about 'xmlBlaster-Authenticate'</li>
    * </ul>
    * <p />
    * @return a handle on the AuthServer IDL interface
    * @exception XmlBlasterException id="NoAuthService"
    *
    */
   public AuthServer getAuthenticationService(boolean verbose) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "getAuthenticationService() ...");
      if (authServer != null) {
         return authServer;
      }


      // 1) check if argument -IOR at program startup is given
      String authServerIOR = glob.getProperty().get("ior", (String)null);  // -ior IOR string is directly given
      if (authServerIOR != null) {
         authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
         if (verbose) log.info(ME, "Accessing xmlBlaster using your given IOR string");
         return authServer;
      }
      if (log.TRACE) log.trace(ME, "No -ior ...");

      String authServerIORFile = glob.getProperty().get("ior.file", (String)null);  // -ior.file IOR string is given through a file
      if (authServerIORFile != null) {
         try {
            authServerIOR = FileUtil.readAsciiFile(authServerIORFile);
         } catch (JUtilsException e) {
            throw new XmlBlasterException(e);
         }
         authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
         log.info(ME, "Accessing xmlBlaster using your given IOR file " + authServerIORFile);
         return authServer;
      }
      if (log.TRACE) log.trace(ME, "No -ior.file ...");


      // 2) check if argument -hostname <hostName or IP> -port <number> at program startup is given
      // To avoid the name service, one can access the AuthServer IOR directly
      // using a http connection.
      try {
         authServerIOR = glob.accessFromInternalHttpServer(connectQos.getAddress(), "AuthenticationService.ior", verbose);
         if (System.getProperty("java.version").startsWith("1") &&  !authServerIOR.startsWith("IOR:")) {
            authServerIOR = "IOR:000" + authServerIOR; // hack for JDK 1.1.x, where the IOR: is cut away from ByteReader ??? !!!
            log.warn(ME, "Manipulated IOR because of missing 'IOR:'");
         }
         authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
         log.info(ME, "Accessing xmlBlaster AuthServer IOR using builtin http connection to " +
                      connectQos.getAddress().getHostname() + ":" + connectQos.getAddress().getPort());
         return authServer;
      }
      catch(XmlBlasterException e) {
         ;
      }
      catch(Throwable e) {
         if (verbose)  {
            log.error(ME, "XmlBlaster not found with internal HTTP download");
            e.printStackTrace();
         }
      }
      if (log.TRACE) log.trace(ME, "No -hostname / port for " + connectQos.getAddress() + " ...");

      String text = "Can't access xmlBlaster Authentication Service, is the server running and ready?\n" +
                  " - try to specify '-ior.file <fileName>' if server is running on same host\n" +
                  " - try to specify '-hostname <hostName> -port " + Constants.XMLBLASTER_PORT + "' to locate xmlBlaster\n" +
                  " - or start a naming service";

      // 3) asking Name Service CORBA compliant
      boolean useNameService = glob.getProperty().get("ns", true);  // -ns default is to ask the naming service
      if (useNameService) {

         if (verbose) log.info(ME, "Trying to find a CORBA naming service ...");
         try {
            NamingContextExt nc = getNamingService(verbose);
            NameComponent [] name = new NameComponent[1];
            name[0] = new NameComponent();
            name[0].id = "xmlBlaster-Authenticate";
            name[0].kind = "MOM";
            authServer = AuthServerHelper.narrow(nc.resolve(name));
            log.info(ME, "Accessing xmlBlaster using a naming service.");
            return authServer;
         }
         catch(Throwable e) {
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, text);
         }
      }
      if (log.TRACE) log.trace(ME, "No -ns ...");

      throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, text);
   }


   /**
    * Login to the server. 
    * <p />
    * WARNING: <strong>The qos gets added a <pre>&lt;callback type='IOR'></pre> tag,
    *          so don't use it for a second login, otherwise a second callback is inserted !</strong>
    *
    * @param loginName The login name for xmlBlaster
    * @param passwd    The login password for xmlBlaster
    * @param qos       The Quality of Service for this client (the callback tag will be added automatically if client!=null)
    *                  The authentication tags should be set already if security manager is switched on
    * @exception       XmlBlasterException if login fails
    */
   public void login(String loginName, String passwd, ConnectQos qos) throws XmlBlasterException {
      this.ME = "CorbaConnection-" + loginName;
      if (log.CALL) log.call(ME, "login() ...");
      if (xmlBlaster != null) {
         log.warn(ME, "You are already logged in.");
         return;
      }

      this.loginName=loginName;
      this.passwd=passwd;

      if (qos == null)
         this.connectQos = new ConnectQos(glob);
      else
         this.connectQos = qos;

      loginRaw(true);
   }

   /**
    * Login to the server. 
    * <p />
    * WARNING: <strong>The qos gets added a <pre>&lt;callback type='IOR'></pre> tag,
    *          so don't use it for a second login, otherwise a second callback is inserted !</strong>
    *
    * @param loginName The login name for xmlBlaster
    * @param passwd    The login password for xmlBlaster
    * @param qos       The Quality of Service for this client (the callback tag will be added automatically if client!=null)
    *                  The authentication tags should be set already if security manager is switched on
    * @exception       XmlBlasterException if login fails
    */
   public ConnectReturnQos connect(ConnectQos qos) throws XmlBlasterException {
      if (qos == null)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Please pass a valid QoS for connect()");

      this.ME = "CorbaConnection-" + qos.getUserId();
      if (log.CALL) log.call(ME, "connect() ...");
      if (xmlBlaster != null) {
         log.warn(ME, "You are already logged in.");
         return this.connectReturnQos;
      }

      this.connectQos = qos;
      this.loginName=qos.getUserId();
      this.passwd=null; // not necessary here

      boolean verbose = this.firstAttempt;
      this.firstAttempt = false;

      return loginRaw(verbose);
   }


   /**
    * Is invoked when we poll for the server, for example after we have lost the connection. 
    * @see I_XmlBlasterConnection#loginRaw
    */
   public ConnectReturnQos loginRaw() throws XmlBlasterException {
      return loginRaw(false);
   }


   /**
    * Login to the server.
    * <p />
    * For internal use only.
    * The qos needs to be set up correctly if you wish a callback
    * @param verbose false: suppress log output
    * @return The returned QoS or null
    * @exception       XmlBlasterException if login fails
    */
   private ConnectReturnQos loginRaw(boolean verbose) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "loginRaw(" + loginName + ") ...");

      try {
         AuthServer authServer = getAuthenticationService(verbose);
         if (passwd != null) {
            log.warn(ME, "No security Plugin. Switched back to the old login scheme!");
            xmlBlaster = authServer.login(loginName, passwd, connectQos.toXml());
         }
         else {
            if (log.TRACE) log.trace(ME, "Got authServer handle, trying connect ...");
            if (log.DUMP) log.dump(ME, "Got authServer handle, trying connect:" + connectQos.toXml());

            String tmp = authServer.connect(connectQos.toXml());
            this.connectReturnQos = new ConnectReturnQos(glob, tmp);
            sessionId = this.connectReturnQos.getSessionId();
            String xmlBlasterIOR = connectReturnQos.getServerRef().getAddress();

            xmlBlaster = ServerHelper.narrow(orb.string_to_object(xmlBlasterIOR));
         }
         if (log.TRACE) log.trace(ME, "Success, login for " + loginName);
         return this.connectReturnQos;
      }
      catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         XmlBlasterException xmlBlasterException = CorbaDriver.convert(glob, e);
         //xmlBlasterException.changeErrorCode(ErrorCode.COMMUNICATION_NOCONNECTION);
         if (log.DUMP) log.dump(ME, "Login failed for " + loginName + ": " + xmlBlasterException.getMessage() + " connectQos=" + connectQos.toXml());
         throw xmlBlasterException; // Wrong credentials 
      }
      catch(Throwable e) {
         XmlBlasterException xmlBlasterException = XmlBlasterException.convert(glob, ME, "Login failed", e);
         xmlBlasterException.changeErrorCode(ErrorCode.COMMUNICATION_NOCONNECTION);
         if (log.DUMP) log.dump(ME, "Login failed for " + loginName + ": " + xmlBlasterException.getMessage() + " connectQos=" + connectQos.toXml());
         throw xmlBlasterException;
      }
   }


   /**
    * Access the login name.
    * @return your login name or null if you are not logged in
    */
   public String getLoginName() {
      return loginName;
   }

   /**
    * Logout from the server.
    * Note that this kills the server ping thread as well (if in fail save mode)
    * @return true successfully logged out
    *         false failure on logout
    */
   public boolean disconnect(DisconnectQos qos) {
      if (log.CALL) log.call(ME, "disconnect() ...");

      if (xmlBlaster == null) {
         shutdown();
         // Thread leak !!!
         // orb.shutdown(true);
         // orb = null;
         return false;
      }

      try {
         if (authServer != null) {
            if(sessionId==null) {
               authServer.logout(xmlBlaster);
            }
            else {
               authServer.disconnect(sessionId, (qos==null)?"":qos.toXml()); // secPlgn.exportMessage(""));
            }
         }
         shutdown();
         // Thread leak !!!
         // orb.shutdown(true);
         // orb = null;
         xmlBlaster = null;
         return true;
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         log.warn(ME, "Remote exception: " + CorbaDriver.convert(glob, e).getMessage());
      } catch(org.omg.CORBA.OBJ_ADAPTER e) {
         log.warn(ME, "No disconnect possible, no CORBA connection available: " + e.toString());
      } catch(Throwable e) {
         XmlBlasterException xmlBlasterException = XmlBlasterException.convert(glob, ME, null, e);
         log.warn(ME, xmlBlasterException.getMessage());
         e.printStackTrace();
      }

      shutdown();
      // Thread leak !!!
      // orb.shutdown(true);
      // orb = null;
      xmlBlaster = null;
      return false;
   }

   /**
    * Shut down the callback server.
    * Is called by logout()
    */
   public boolean shutdown() {
      if (this.authServer != null) {
         this.authServer._release();
         this.authServer = null;
      }
      if (this.xmlBlaster != null) {
         this.xmlBlaster._release();
         this.xmlBlaster = null;
      }
      return true;
   }

   /**
    * @return true if you are logged in
    */
   public boolean isLoggedIn() {
      return xmlBlaster != null;
   }

   /**
    * Enforced by I_XmlBlasterConnection interface (fail save mode).
    * see explanations of publish() method.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public final String subscribe(String xmlKey, String qos) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "subscribe() ...");
      try {
         return getXmlBlaster().subscribe(xmlKey, qos);
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         throw CorbaDriver.convert(glob, e); // transform Corba exception to native exception
      } catch(Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "subscribe", e);
      }
   }

   /**
    * Enforced by I_XmlBlasterConnection interface (fail save mode)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public final String[] unSubscribe(String xmlKey, String qos) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "unSubscribe() ...");
      try {
         return getXmlBlaster().unSubscribe(xmlKey, qos);
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         throw CorbaDriver.convert(glob, e); // transform Corba exception to native exception
      } catch(Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "unSubscribe", e);
      }
   }


   /**
    * Publish fault-tolerant the given message.
    * <p />
    * This is a wrapper around the raw CORBA publish() method
    * If the server disappears you get an exception.
    * This call will not block.
    * <p />
    * Enforced by I_XmlBlasterConnection interface (fail save mode)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public final String publish(MsgUnitRaw msgUnit) throws XmlBlasterException {
      if (log.TRACE) log.trace(ME, "Publishing ...");
      try {
         return getXmlBlaster().publish(CorbaDriver.convert(msgUnit));
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         if (log.TRACE) log.trace(ME, "XmlBlasterException: " + e.getMessage());
         throw CorbaDriver.convert(glob, e); // transform Corba exception to native exception
      } catch(Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "publish() failed", e);
      }
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] publishArr(MsgUnitRaw [] msgUnitArr) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "publishArr() ...");
      try {
         return getXmlBlaster().publishArr(CorbaDriver.convert(msgUnitArr));
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         if (log.TRACE) log.trace(ME, "XmlBlasterException: " + e.getMessage());
         throw CorbaDriver.convert(glob, e); // transform Corba exception to native exception
      } catch(Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "publishArr", e);
      }
   }

   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public void publishOneway(MsgUnitRaw[] msgUnitArr) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "publishOneway() ...");
      try {
         getXmlBlaster().publishOneway(CorbaDriver.convert(msgUnitArr));
      } catch(Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "publishOneway", e);
      }
   }

   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public final String[] erase(String xmlKey, String qos) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "erase() ...");
      if (xmlKey==null) xmlKey = "";
      if (qos==null) qos = "";
      try {
         return getXmlBlaster().erase(xmlKey, qos);
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         throw CorbaDriver.convert(glob, e); // transform Corba exception to native exception
      } catch(Throwable e) {
         log.error(ME+".erase", "IO exception: " + e.toString() + " sessionId=" + this.sessionId);
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "erase", e);
      }
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public final MsgUnitRaw[] get(String xmlKey, String qos) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "get() ...");
      try {
         return CorbaDriver.convert(glob, getXmlBlaster().get(xmlKey, qos));
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         throw CorbaDriver.convert(glob, e); // transform Corba exception to native exception
      } catch(Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "get", e);
      }
   }

   /**
    * Check server.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String ping(String qos) throws XmlBlasterException {
      try {
         return getXmlBlaster().ping(qos);
      } catch(Throwable e) {
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
      text += "CorbaConnection 'IOR' options:\n";
      text += "   -ior <IOR:00459...  The IOR string from the running xmlBlaster server.\n";
      text += "   -ior.file <fileName>A file with the xmlBlaster IOR.\n";
      text += "   -hostname <host>    The host where to find xmlBlaster internal HTTP IOR download [localhost]\n";
      text += "   -port <port>        The port where xmlBlaster publishes its IOR [" + Constants.XMLBLASTER_PORT + "]\n";
      text += "   -ns <true/false>    Try to access xmlBlaster through a naming service [true]\n";
      text += "   -ior.hostnameCB     Allows to set the callback-server's IP address for multi-homed hosts.\n";
      text += "   -ior.portCB         Allows to set the callback-server's port number.\n";
      text += " For JacORB only:\n";
      text += "   java -DOAIAddr=<ip> Use '-ior.hostnameCB'\n";
      text += "   java -DOAPort=<nr>  Use '-ior.portCB'\n";
      text += "   java -Djacorb.verbosity=3  Switch CORBA debugging on\n";
      text += "\n";
      return text;
   }
} // class CorbaConnection
