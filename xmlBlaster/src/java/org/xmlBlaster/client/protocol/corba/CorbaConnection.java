/*------------------------------------------------------------------------------
Name:      CorbaConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: CorbaConnection.java,v 1.6 2000/10/23 22:04:46 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.corba;

import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;
import org.xmlBlaster.client.protocol.ConnectionException;
import org.xmlBlaster.client.LoginQosWrapper;
import org.xmlBlaster.client.protocol.I_CallbackExtended;

import org.xmlBlaster.util.Log;
import org.jutils.io.FileUtil;
import org.jutils.JUtilsException;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.protocol.corba.CorbaDriver;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.protocol.corba.authenticateIdl.AuthServer;
import org.xmlBlaster.protocol.corba.authenticateIdl.AuthServerHelper;

import org.omg.CosNaming.NamingContext;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextHelper;

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
 * @version $Revision: 1.6 $
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public class CorbaConnection implements I_XmlBlasterConnection
{
   private String ME = "CorbaConnection";

   // HACK May,24 2000 !!! (search 'Thread leak' in this file to remove the hack again and remove the two 'static' qualifiers below.)
   // Thread leak from JacORB 1.1, the threads
   //   - JacORB Listener Thread
   //   - JacORB ReplyReceptor
   //   - JacORB Request Receptor
   // are never released on orb.shutdown() and rootPoa.deactivate()
   //
   // So we use a static orb and poa and recycle it.
   // The drawback is that a running client can't change the
   // orb behavior
   static protected org.omg.CORBA.ORB orb = null;

   protected NamingContext nameService = null;
   protected AuthServer authServer = null;
   protected Server xmlBlaster = null;
   /** Our default implementation for a Corba callback server */
   protected CorbaCallbackServer callback = null;
   protected String loginName = null;
   private String passwd = null;
   protected LoginQosWrapper loginQos = null;


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
   public CorbaConnection(String[] args)
   {
      if (orb == null) // Thread leak !!!
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

   public void init()
   {
      authServer = null;
      xmlBlaster = null;
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
    * For internal use, throws an ordinary Exception if xmlBlaster==null
    * We use this for similar handling as org.omg exceptions.
    * @return Server
    */
   private Server getXmlBlaster() throws Exception
   {
      if (xmlBlaster == null) {
         throw new Exception("The xmlBlaster handle is null, no connection available");
      }
      return xmlBlaster;
   }


   /**
    * Locate the CORBA Name Service.
    * <p />
    * The found name service is cached, for better performance in subsequent calls
    * @return NamingContext, reference on name service
    * @exception XmlBlasterException id="NoNameService"
    *                    CORBA error handling if no naming service is found
    */
   NamingContext getNamingService() throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "getNamingService() ...");
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
         Log.warn(ME + ".NoNameService", text);
         throw new XmlBlasterException("NoNameService", text);
      }
      if (nameServiceObj == null) {
         throw new XmlBlasterException("NoNameService", "Can't access naming service (null), is there any running?");
      }
      if (Log.TRACE) Log.trace(ME, "Successfully accessed initial orb references for naming service (IOR)");

      try {
         nameService = org.omg.CosNaming.NamingContextHelper.narrow(nameServiceObj);
         if (nameService == null) {
            Log.error(ME + ".NoNameService", "Can't access naming service (narrow problem)");
            throw new XmlBlasterException("NoNameService", "Can't access naming service (narrow problem)");
         }
         if (Log.TRACE) Log.trace(ME, "Successfully narrowed handle for naming service");
         return nameService; // Note: the naming service IOR is successfully evaluated (from a IOR),
                             // but it is not sure that the naming service is really running
      }
      catch (Exception e) {
         Log.warn(ME + ".NoNameService", "Can't access naming service");
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
    *        <code>   -iorFile yourIorFile</code></li>
    *    <li>Give the xmlBlaster host and iorPort where xmlBlaster-Authenticate serves the IOR via http, give at command line e.g.
    *        <code>   -iorHost server.xmlBlaster.org  -iorPort 7609</code></li>
    *    <li>Try to find a naming service which knows about 'xmlBlaster-Authenticate'</li>
    * </ul>
    * <p />
    * @return a handle on the AuthServer IDL interface
    * @exception XmlBlasterException id="NoAuthService"
    *
    */
   AuthServer getAuthenticationService() throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "getAuthenticationService() ...");
      if (authServer != null) {
         return authServer;
      }


      // 1) check if argument -IOR at program startup is given
      String authServerIOR = XmlBlasterProperty.get("ior", (String)null);  // -ior IOR string is directly given
      if (authServerIOR != null) {
         authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
         Log.info(ME, "Accessing xmlBlaster using your given IOR string");
         return authServer;
      }
      if (Log.TRACE) Log.trace(ME, "No -ior ...");

      String authServerIORFile = XmlBlasterProperty.get("iorFile", (String)null);  // -iorFile IOR string is given through a file
      if (authServerIORFile != null) {
         try {
            authServerIOR = FileUtil.readAsciiFile(authServerIORFile);
         } catch (JUtilsException e) {
            throw new XmlBlasterException(e);
         }
         authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
         Log.info(ME, "Accessing xmlBlaster using your given IOR file " + authServerIORFile);
         return authServer;
      }
      if (Log.TRACE) Log.trace(ME, "No -iorFile ...");


      // 2) check if argument -iorHost <hostName or IP> -iorPort <number> at program startup is given
      String iorHost = getLocalIP();
      int iorPort = XmlBlasterProperty.get("iorPort", CorbaDriver.DEFAULT_HTTP_PORT); // 7609
      if (iorHost != null && iorPort > 0) {
         try {
            authServerIOR = getAuthenticationServiceIOR(iorHost, iorPort);
            authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
            Log.info(ME, "Accessing xmlBlaster AuthServer IOR using builtin http connection, host " + iorHost + " and port " + iorPort);
            return authServer;
         }
         catch(Exception e) {
            if (Log.TRACE) Log.trace(ME, "XmlBlaster not found on host " + iorHost + " and port " + iorPort + ": " + e.toString());
            Log.warn(ME, "XmlBlaster not found on host " + iorHost + " and port " + iorPort + ".");
         }
      }
      if (Log.TRACE) Log.trace(ME, "No -iorHost / iorPort ...");

      String text = "Can't access xmlBlaster Authentication Service, is the server running and ready?\n" +
                  " - try to specify '-iorFile <fileName>' if server is running on same host\n" +
                  " - try to specify '-iorHost <hostName> -iorPort 7609' to locate xmlBlaster\n" +
                  " - or contact your system administrator to start a naming service";

      // 3) asking Name Service CORBA compliant
      boolean useNameService = XmlBlasterProperty.get("ns", true);  // -ns default is to ask the naming service
      if (useNameService) {

         Log.info(ME, "Trying to find a CORBA naming service ...");
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
            Log.warn(ME + ".NoAuthService", text);
            throw new ConnectionException("NoAuthService", text);
         }
      }
      if (Log.TRACE) Log.trace(ME, "No -ns ...");

      throw new ConnectionException("NoAuthService", text);
   }


   /**
    * The IP address where the HTTP server publishes the IOR.
    * <p />
    * You can specify the local IP address with e.g. -iorHost 192.168.10.1
    * on command line, useful for multi-homed hosts.
    *
    * @return The local IP address, defaults to '127.0.0.1' if not known.
    */
   public String getLocalIP()
   {
      String ip_addr = XmlBlasterProperty.get("iorHost", (String)null);
      if (ip_addr == null) {
         try {
            ip_addr = java.net.InetAddress.getLocalHost().getHostAddress(); // e.g. "204.120.1.12"
         } catch (java.net.UnknownHostException e) {
            Log.warn(ME, "Can't determine local IP address, try e.g. '-iorHost 192.168.10.1' on command line: " + e.toString());
         }
         if (ip_addr == null) ip_addr = "127.0.0.1";
      }
      return ip_addr;
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
   public void login(String loginName, String passwd, LoginQosWrapper qos) throws XmlBlasterException, ConnectionException
   {
      login(loginName, passwd, qos, null);
   }


   /**
    * Login to the server, using the default BlasterCallback implementation.
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
    * @param client    Your implementation of I_CallbackExtended, or null if you don't want any updates.
    * @exception       XmlBlasterException if login fails
    */
   public void login(String loginName, String passwd, LoginQosWrapper qos, I_CallbackExtended client) throws XmlBlasterException, ConnectionException
   {
      this.ME = "CorbaConnection-" + loginName;
      if (Log.CALL) Log.call(ME, "login() ...");
      if (xmlBlaster != null) {
         Log.warn(ME, "You are already logged in.");
         return;
      }

      this.loginName = loginName;
      this.passwd = passwd;
      if (qos == null)
         this.loginQos = new LoginQosWrapper();
      else
         this.loginQos = qos;

      if (client != null) {
         this.callback = new CorbaCallbackServer(loginName, client, orb);
         loginQos.addCallbackAddress(this.callback.getCallbackIOR());
      }

      loginRaw();
   }


   /**
    * Login to the server.
    * <p />
    * For internal use only.
    * The qos needs to be set up correctly if you wish a callback
    * @exception       XmlBlasterException if login fails
    */
   public void loginRaw() throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "loginRaw(" + loginName + ") ...");
      try {
         AuthServer authServer = getAuthenticationService();
         xmlBlaster = authServer.login(loginName, passwd, loginQos.toXml());
         if (Log.TRACE) Log.trace(ME, "Success, login for " + loginName);
         if (Log.DUMP) Log.dump(ME, loginQos.toXml());
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         if (Log.TRACE) Log.trace(ME, "Login failed for " + loginName);
         throw new ConnectionException(e.id, e.reason);
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
    * The callback server is removed as well, releasing all CORBA threads.
    * Note that this kills the server ping thread as well (if in fail save mode)
    * @return true successfully logged out
    *         false failure on logout
    */
   public boolean logout()
   {
      if (Log.CALL) Log.call(ME, "logout() ...");

      if (xmlBlaster == null) {
         if (this.callback != null) this.callback.shutdown();
         // Thread leak !!!
         // orb.shutdown(true);
         // orb = null;
         return false;
      }

      try {
         authServer.logout(xmlBlaster);
         if (this.callback != null) this.callback.shutdown();
         // Thread leak !!!
         // orb.shutdown(true);
         // orb = null;
         xmlBlaster = null;
         return true;
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: [" + e.id + "]" + " " + e.reason);
      } catch(Exception e) {
         Log.warn(ME, e.toString());
         e.printStackTrace();
      }

      if (this.callback != null) this.callback.shutdown();
      // Thread leak !!!
      // orb.shutdown(true);
      // orb = null;
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
    * To avoid the name service, one can access the AuthServer IOR directly
    * using a http connection.
    * <p />
    * @param host the host running xmlBlaster
    * @param iorPort the port on which the IOR is served (the xmlBlaster mini http server)
    */
   private String getAuthenticationServiceIOR(String iorHost, int iorPort) throws Exception
   {
      if (Log.CALL) Log.call(ME, "Trying authentication service on " + iorHost + ":" + iorPort);
      java.net.URL nsURL = new java.net.URL("http", iorHost, iorPort, "/AuthenticationService.ior");
      // Note: the file name /AuthenticationService.ior is ignored in the current server implementation
      java.io.InputStream nsis = nsURL.openStream();
      byte[] bytes = new byte[4096];
      java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
      int numbytes;
      while (nsis.available() > 0 && (numbytes = nsis.read(bytes)) > 0) {
         bos.write(bytes, 0, (numbytes > 4096) ? 4096 : numbytes);
      }
      nsis.close();
      String ior = bos.toString();
      if (!ior.startsWith("IOR:"))
         ior = "IOR:000" + ior; // hack for JDK 1.1.x, where the IOR: is cut away from ByteReader ??? !!!
      if (Log.TRACE) Log.trace(ME, "Retrieved authentication service IOR='" + ior + "'");
      return ior;
   }


   /**
    * Enforced by I_XmlBlasterConnection interface (fail save mode).
    * see explanations of publish() method.
    * @see xmlBlaster.idl
    */
   public final String subscribe(String xmlKey, String qos) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "subscribe() ...");
      try {
         return getXmlBlaster().subscribe(xmlKey, qos);
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform Corba exception to native exception
      } catch(Exception e) {
         throw new ConnectionException(ME+".InvokeError", e.toString());
      }
   }


   /**
    * Enforced by I_XmlBlasterConnection interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public final void unSubscribe(String xmlKey, String qos) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "unSubscribe() ...");
      try {
         getXmlBlaster().unSubscribe(xmlKey, qos);
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform Corba exception to native exception
      } catch(Exception e) {
         throw new ConnectionException(ME+".InvokeError", e.toString());
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
    * @see xmlBlaster.idl
    */
   public final String publish(MessageUnit msgUnit) throws XmlBlasterException, ConnectionException
   {
      if (Log.TRACE) Log.trace(ME, "Publishing ...");
      try {
         return getXmlBlaster().publish(CorbaDriver.convert(msgUnit));
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         if (Log.TRACE) Log.trace(ME, "XmlBlasterException: " + e.reason);
         throw new XmlBlasterException(e.id, e.reason); // transform Corba exception to native exception
      } catch(Exception e) {
         throw new ConnectionException(ME+".InvokeError", e.toString());
      }
   }


   /**
    * @see xmlBlaster.idl
    */
   public String[] publishArr(MessageUnit [] msgUnitArr) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "publishArr() ...");
      try {
         return getXmlBlaster().publishArr(CorbaDriver.convert(msgUnitArr));
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         if (Log.TRACE) Log.trace(ME, "XmlBlasterException: " + e.reason);
         throw new XmlBlasterException(e.id, e.reason); // transform Corba exception to native exception
      } catch(Exception e) {
         throw new ConnectionException(ME+".InvokeError", e.toString());
      }
   }


   /**
    * @see xmlBlaster.idl
    */
   public final String[] erase(String xmlKey, String qos) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "erase() ...");
      try {
         return getXmlBlaster().erase(xmlKey, qos);
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform Corba exception to native exception
      } catch(Exception e) {
         throw new ConnectionException(ME+".InvokeError", e.toString());
      }
   }


   /**
    * @see xmlBlaster.idl
    */
   public final MessageUnit[] get(String xmlKey, String qos) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "get() ...");
      try {
         return CorbaDriver.convert(getXmlBlaster().get(xmlKey, qos));
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform Corba exception to native exception
      } catch(Exception e) {
         e.printStackTrace();
         throw new ConnectionException(ME+".InvokeError", e.toString());
      }
   }


   /**
    * Check server.
    * @see xmlBlaster.idl
    */
   public void ping() throws ConnectionException
   {
      try {
         getXmlBlaster().ping();
         return;
      } catch(Exception e) {
         throw new ConnectionException(ME+".InvokeError", e.toString());
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
      text += "   -iorHost <host>     The host where to find xmlBlaster [localhost]\n";
      text += "   -iorPort <port>     The port where xmlBlaster publishes its IOR [7609]\n";
      text += "   -iorFile <fileName> A file with the xmlBlaster IOR.\n";
      text += "   -ns <true/false>    Try to access xmlBlaster through a naming service [true]\n";
      text += "   java -DOAIAddr=<ip> For JacORB only, allows to set the callback-server's IP address\n";
      text += "                       for multi-homed hosts\n";
      text += "\n";
      return text;
   }
} // class CorbaConnection
