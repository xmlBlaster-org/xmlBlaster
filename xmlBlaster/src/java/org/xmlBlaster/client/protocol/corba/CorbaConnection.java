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
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.util.JdkCompatible;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.qos.address.Address;

import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;

import org.jutils.io.FileUtil;
import org.jutils.JUtilsException;

import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.qos.address.ServerRef;
import org.xmlBlaster.protocol.corba.OrbInstanceFactory;
import org.xmlBlaster.protocol.corba.CorbaDriver;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.protocol.corba.serverIdl.ServerHelper;
import org.xmlBlaster.protocol.corba.authenticateIdl.AuthServer;
import org.xmlBlaster.protocol.corba.authenticateIdl.AuthServerHelper;

import org.omg.CosNaming.NamingContext;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.CosNaming.BindingHolder;
import org.omg.CosNaming.BindingListHolder;
import org.omg.CosNaming.BindingIteratorHolder;

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
public final class CorbaConnection implements I_XmlBlasterConnection, I_Plugin
{
   private String ME = "CorbaConnection";
   private Global glob;
   private LogChannel log;

   private org.omg.CORBA.ORB orb;

   private NamingContextExt nameService;
   private AuthServer authServer;
   private Server xmlBlaster;
   private Address clientAddress;
   private String sessionId;
   private boolean verbose = true;


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

      orb = org.omg.CORBA.ORB.init(ap, props); // for applets only

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
      this.glob = (glob == null) ? Global.instance() : glob;
      this.log = this.glob.getLog("corba");

      if (glob == null) {
         log.error(ME, "glob is null");
         Thread.currentThread().dumpStack();
      }

      if (this.orb == null) { // Thread leak !!!
         this.orb = OrbInstanceFactory.createOrbInstance(this.glob,this.glob.getArgs(),null,false);
      }
      resetConnection();
      log.info(ME, "Created '" + getProtocol() + "' protocol plugin to connect to xmlBlaster server");
   }

   /**
    * Reset
    */
   public void resetConnection() {
      this.authServer   = null;
      this.xmlBlaster = null;
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
      return this.orb;
   }

   /**
    * Accessing the xmlBlaster handle.
    * For internal use, throws a COMMUNICATION XmlBlasterException if xmlBlaster==null
    * We use this for similar handling as org.omg exceptions.
    * @return Server
    */
   private Server getXmlBlaster() throws XmlBlasterException {
      if (this.xmlBlaster == null) {
         if (log.TRACE) log.trace(ME, "No CORBA connection available.");
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                                       "The CORBA xmlBlaster handle is null, no connection available");
      }
      return this.xmlBlaster;
   }


   /**
    * Locate the CORBA Name Service.
    * <p />
    * The found name service is cached, for better performance in subsequent calls
    * @return NamingContextExt, reference on name service
    * @exception XmlBlasterException id="NoNameService"
    *                    CORBA error handling if no naming service is found
    */
   NamingContextExt getNamingService() throws XmlBlasterException
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
         if (this.verbose)
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
         if (this.verbose) log.warn(ME + ".NoNameService", "Can't access naming service");
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
   public AuthServer getAuthenticationService(Address address) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "getAuthenticationService() ...");
      if (this.authServer != null) {
         return this.authServer;
      }

      try {
         // 1) check if argument -IOR at program startup is given
         String authServerIOR = glob.getProperty().get("ior", (String)null);  // -ior IOR string is directly given
         if (authServerIOR != null) {
            this.authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
            if (this.verbose) log.info(ME, "Accessing xmlBlaster using your given IOR string");
            return this.authServer;
         }
         if (log.TRACE) log.trace(ME, "No -ior ...");

         String authServerIORFile = glob.getProperty().get("ior.file", (String)null);  // -ior.file IOR string is given through a file
         if (authServerIORFile != null) {
            try {
               authServerIOR = FileUtil.readAsciiFile(authServerIORFile);
            } catch (JUtilsException e) {
               throw new XmlBlasterException(e);
            }
            this.authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
            log.info(ME, "Accessing xmlBlaster using your given IOR file " + authServerIORFile);
            return this.authServer;
         }
         if (log.TRACE) log.trace(ME, "No -ior.file ...");


         // 2) check if argument -hostname <hostName or IP> -port <number> at program startup is given
         // To avoid the name service, one can access the AuthServer IOR directly
         // using a http connection.
         try {
            authServerIOR = glob.accessFromInternalHttpServer(address, "AuthenticationService.ior", this.verbose);
            if (System.getProperty("java.version").startsWith("1") &&  !authServerIOR.startsWith("IOR:")) {
               authServerIOR = "IOR:000" + authServerIOR; // hack for JDK 1.1.x, where the IOR: is cut away from ByteReader ??? !!!
               log.warn(ME, "Manipulated IOR because of missing 'IOR:'");
            }
            this.authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
            log.info(ME, "Accessing xmlBlaster AuthServer IOR using builtin http connection to " +
                         address.getHostname() + ":" + address.getPort());
            return this.authServer;
         }
         catch(XmlBlasterException e) {
            ;
         }
         catch(Throwable e) {
            if (this.verbose)  {
               log.error(ME, "XmlBlaster not found with internal HTTP download");
               e.printStackTrace();
            }
         }
         if (log.TRACE) log.trace(ME, "No -hostname / port for " + address.getAddress() + " ...");

         String contextId = glob.getProperty().get("NameService.context.id", "xmlBlaster");
         if (contextId == null) contextId = "";
         String contextKind = glob.getProperty().get("NameService.context.kind", "MOM");
         if (contextKind == null) contextKind = "";
         String clusterId = glob.getProperty().get("NameService.node.id", glob.getStrippedId());
         if (clusterId == null) clusterId = "";
         String clusterKind = glob.getProperty().get("NameService.node.kind", "MOM");
         if (clusterKind == null) clusterKind = "";

         String text = "Can't access xmlBlaster Authentication Service, is the server running and ready?\n" +
                     " - try to specify '-ior.file <fileName>' if server is running on same host\n" +
                     " - try to specify '-hostname <hostName> -port " + Constants.XMLBLASTER_PORT + "' to locate xmlBlaster\n" +
                     " - or start a naming service '" + contextId + "." + contextKind + "/" +
                              clusterId + "." + clusterKind + "'";

         // 3) asking Name Service CORBA compliant
         boolean useNameService = glob.getProperty().get("ns", true);  // -ns default is to ask the naming service
         if (useNameService) {

            if (this.verbose) log.info(ME, "Trying to find a CORBA naming service ...");
            try {
               
               // NameService entry is e.g. "xmlBlaster.MOM/heron.MOM"
               // where "xmlBlaster.MOM" is a context node and
               // "heron.MOM" is a subnode for each running server (containing the AuthServer POA reference)

               NamingContextExt namingContextExt = getNamingService();
               NameComponent [] nameXmlBlaster = new NameComponent[] { new NameComponent(contextId, contextKind) };
               if (log.TRACE) log.trace(ME, "Query NameServer -ORBInitRef NameService=" + glob.getProperty().get("ORBInitRef","") +
                             ((System.getProperty("ORBInitRef.NameService") != null) ? System.getProperty("ORBInitRef.NameService") : "") +
                             " to find the xmlBlaster root context " + CorbaDriver.getString(nameXmlBlaster));
               org.omg.CORBA.Object obj = namingContextExt.resolve(nameXmlBlaster);
               NamingContext relativeContext = org.omg.CosNaming.NamingContextExtHelper.narrow(obj);

               if (relativeContext == null) {
                  throw new Exception("Can't resolve CORBA NameService");
               }

               NameComponent [] nameNode = new NameComponent[] { new NameComponent(clusterId, clusterKind) };

               AuthServer authServerFirst = null;
               String tmpId = "";           // for logging only
               String tmpServerName = "";   // for logging only
               String firstServerName = ""; // for logging only
               int countServerFound = 0;    // for logging only
               String serverNameList = "";  // for logging only
               try {
                  this.authServer = AuthServerHelper.narrow(relativeContext.resolve(nameNode));
               }
               catch (Exception ex) {
                  if (log.TRACE) log.trace(ME, "Query NameServer to find a suitable xmlBlaster server for " + CorbaDriver.getString(nameXmlBlaster) + "/" + CorbaDriver.getString(nameNode));
                  BindingListHolder bl = new BindingListHolder();
                  BindingIteratorHolder bi = new BindingIteratorHolder();
                  relativeContext.list(0, bl, bi);
                  //for (int i=0; i<bl.value.length; i++) { // bl.value.length should be 0
                  //   String id = bl.value[i].binding_name[0].id;
                  //   String kind = bl.value[i].binding_name[0].kind;

                  // process the remaining bindings if an iterator exists:
                  if (this.authServer == null && bi.value != null) {
                     BindingHolder bh = new BindingHolder();
                     int i = 0;
                     while ( bi.value.next_one(bh) ) {
                        String id = bh.value.binding_name[0].id;
                        String kind = bh.value.binding_name[0].kind;
                        NameComponent [] nameNodeTmp = new NameComponent[] { new NameComponent(id, kind) };

                        tmpId = id;
                        countServerFound++;
                        tmpServerName = CorbaDriver.getString(nameXmlBlaster)+"/"+CorbaDriver.getString(nameNodeTmp);
                        if (i>0) serverNameList += ", ";
                        i++;
                        serverNameList += tmpServerName;

                        if (clusterId.equals(id) && clusterKind.equals(kind)) {
                           try {
                              if (log.TRACE) log.trace(ME, "Trying to resolve NameService entry '"+CorbaDriver.getString(nameNodeTmp)+"'");
                              this.authServer = AuthServerHelper.narrow(relativeContext.resolve(nameNodeTmp));
                              break; // found a matching server
                           }
                           catch (Exception exc) {
                              log.warn(ME, "Connecting to NameService entry '"+tmpServerName+"' failed: " + exc.toString());
                           }
                        }

                        if (authServerFirst == null) {
                           if (log.TRACE) log.trace(ME, "Remember the first server");
                           try {
                              firstServerName = tmpServerName;
                              if (log.TRACE) log.trace(ME, "Remember the first reachable xmlBlaster server from NameService entry '"+firstServerName+"'");
                              authServerFirst = AuthServerHelper.narrow(relativeContext.resolve(nameNodeTmp));
                           }
                           catch (Exception exc) {
                              log.warn(ME, "Connecting to NameService entry '"+tmpServerName+"' failed: " + exc.toString());
                           }
                        }
                     }
                  }
               }

               if (this.authServer == null) {
                  if (authServerFirst != null) {
                     if (countServerFound > 1) {
                        String str = "Can't choose one of " + countServerFound +
                                     " avalailable server in CORBA NameService: " + serverNameList +
                                     ". Please choose one with e.g. -NameService.node.id " + tmpId;
                        log.error(ME, str);
                        throw new Exception(str);
                     }
                     log.info(ME, "Choosing only available server '" + firstServerName + "' in CORBA NameService -ORBInitRef NameService=" +
                                  System.getProperty("ORBInitRef"));
                     this.authServer = authServerFirst;
                     return authServerFirst;
                  }
                  else {
                     throw new Exception("No xmlBlaster server found in NameService");
                  }
               }

               log.info(ME, "Accessing xmlBlaster using a naming service '" + nameXmlBlaster[0].id + "." + nameXmlBlaster[0].kind + "/" +
                              nameNode[0].id + "." + nameNode[0].kind + "' on " + System.getProperty("ORBInitRef"));
               return this.authServer;
            }
            catch(Throwable e) {
               throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, text, e);
            }
         }
         if (log.TRACE) log.trace(ME, "No -ns ...");
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, text);
      }
      finally {
         this.verbose = false;
      }
   }

   /**
    * Login to the server. 
    * <p />
    * @param connectQos The encrypted connect QoS 
    * @exception XmlBlasterException if login fails
    */
   public String connect(String connectQos) throws XmlBlasterException {
      if (connectQos == null)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Please pass a valid QoS for connect()");

      this.ME = "CorbaConnection";
      if (log.CALL) log.call(ME, "connect(xmlBlaster="+xmlBlaster+") ...");
      if (this.xmlBlaster != null) {
         log.warn(ME, "You are already logged in.");
         return "";
      }

      getAuthenticationService(this.clientAddress);

      try {
         AuthServer remoteAuthServer = getAuthenticationService(this.clientAddress);
         if (log.TRACE) log.trace(ME, "Got authServer handle, trying connect ...");
         return remoteAuthServer.connect(connectQos);
      }
      catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         XmlBlasterException xmlBlasterException = CorbaDriver.convert(glob, e);
         //xmlBlasterException.changeErrorCode(ErrorCode.COMMUNICATION_NOCONNECTION);
         throw xmlBlasterException; // Wrong credentials 
      }
      catch(Throwable e) {
         XmlBlasterException xmlBlasterException = XmlBlasterException.convert(glob, ME, "Login failed", e);
         xmlBlasterException.changeErrorCode(ErrorCode.COMMUNICATION_NOCONNECTION);
         throw xmlBlasterException;
      }
   }

   /**
    * @see I_XmlBlasterConnection#connectLowlevel(Address)
    */
   public void connectLowlevel(Address address) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "connectLowlevel() ...");
      this.clientAddress = address;
      getAuthenticationService(this.clientAddress);
      if (log.TRACE) log.trace(ME, "Success, connectLowlevel()");
   }

    /**
    * @see I_XmlBlasterConnection#setConnectReturnQos(ConnectReturnQos)
    */
   public void setConnectReturnQos(ConnectReturnQos connectReturnQos) throws XmlBlasterException {
      try {
         this.sessionId = connectReturnQos.getSecretSessionId();
         String xmlBlasterIOR = connectReturnQos.getServerRef().getAddress();
         this.xmlBlaster = ServerHelper.narrow(orb.string_to_object(xmlBlasterIOR));
         this.ME = "CorbaConnection-"+connectReturnQos.getSessionName().toString();
         if (log.TRACE) log.trace(ME, "setConnectReturnQos(): xmlBlaster=" + this.xmlBlaster);
      }
      catch(Throwable e) {
         XmlBlasterException xmlBlasterException = XmlBlasterException.convert(glob, ME, "Login failed", e);
         xmlBlasterException.changeErrorCode(ErrorCode.COMMUNICATION_NOCONNECTION);
         throw xmlBlasterException;
      }
   }

   /**
    * Logout from the server.
    * Note that this kills the server ping thread as well (if in fail save mode)
    * @return true successfully logged out
    *         false failure on logout
    */
   public boolean disconnect(String qos) {
      if (log.CALL) log.call(ME, "disconnect() ...");

      if (this.xmlBlaster == null) {
         try {
            shutdown();
         }
         catch (XmlBlasterException ex) {
            this.log.error(ME, "disconnect. Could not shutdown properly. " + ex.getMessage());
         }
         return false;
      }

      try {
         if (this.authServer != null) {
            if(this.sessionId==null) {
               this.authServer.logout(xmlBlaster);
            }
            else {
               this.authServer.disconnect(this.sessionId, (qos==null)?"":qos); // secPlgn.exportMessage(""));
            }
         }
         shutdown();
         this.xmlBlaster = null;
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

      try {
         shutdown();
      }
      catch (XmlBlasterException ex) {
         this.log.error(ME, "disconnect. Could not shutdown properly. " + ex.getMessage());
      }
      this.xmlBlaster = null;
      return false;
   }

   /**
    * Shut down the callback server.
    * Is called by logout()
    */
   public void shutdown() throws XmlBlasterException {
      if (this.authServer != null) {
         this.authServer._release();
         this.authServer = null;
      }
      if (this.xmlBlaster != null) {
         this.xmlBlaster._release();
         this.xmlBlaster = null;
      }
      if (this.orb != null) {
         boolean wait_for_completion = false;
         try {
            this.orb.shutdown(wait_for_completion);
            this.orb = null;
         }
         catch (Throwable ex) {
            this.log.warn(ME, "shutdown: Exception occured during orb.shutdown("+wait_for_completion+"): " + ex.toString());
         }
      }
   }

   /**
    * @return true if you are logged in
    */
   public boolean isLoggedIn() {
      return this.xmlBlaster != null;
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
    * Ping the server.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String ping(String qos) throws XmlBlasterException {
      if (this.xmlBlaster == null && this.authServer != null) {
         return this.authServer.ping(qos); // low level ping without having connect() to xmlBlaster
      }

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
      text += "   java ... -ORBInitRef NameService=corbaloc:iiop:localhost:7608/StandardNS/NameServer-POA/_root\n";
      text += "   java -DORBInitRef.NameService=corbaloc:iiop:localhost:7608/StandardNS/NameServer-POA/_root\n";
      text += "\n";
      return text;
   }
} // class CorbaConnection
