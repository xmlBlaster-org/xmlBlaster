/*------------------------------------------------------------------------------
Name:      CorbaConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: CorbaConnection.java,v 1.13 1999/12/16 11:49:16 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.FileUtil;
import org.xmlBlaster.util.Args;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.serverIdl.Server;
import org.xmlBlaster.serverIdl.MessageUnit;
import org.xmlBlaster.authenticateIdl.AuthServer;
import org.xmlBlaster.authenticateIdl.AuthServerHelper;
import org.xmlBlaster.clientIdl.*;

import org.omg.CosNaming.*;
import java.applet.Applet;
// import javax.servlet.*;
import java.util.Properties;


/**
 * This is a little helper class, helping a Java client to connect to xmlBlaster
 * using IIOP (CORBA).
 * <p>
 * There is a constructor for applets, servlets and standalone Java clients.
 * <p />
 * If you have a servlet development kit installed (http://java.sun.com/products/servlet/index.html)
 * you may remove the comments from all servlets based code.
 * <p />
 * Invoke: jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestSub
 *
 * @version $Revision: 1.13 $
 * @author $Author: ruff $
 */
public class CorbaConnection
{
   private final String ME = "CorbaConnection";
   private String[] args = null;
   private org.omg.CORBA.ORB orb = null;
   private NamingContext nameService = null;
   private AuthServer authServer = null;
   private Server xmlBlaster = null;
   private BlasterCallback callback = null;
   private String loginName = null;
   private String qos = null;


   /**
    * CORBA client access to xmlBlaster (default behavior).
    */
   public CorbaConnection()
   {
      args = new String[0];  // dummy
      orb = org.omg.CORBA.ORB.init(args, null);
   }

   /**
    * CORBA client access to xmlBlaster for <strong>normal client applications</strong>.
    * <p />
    * Use these environment settings for VisiBroker
    * <br />
    * <ul>
    *    <li>-DORBservices</li>
    *    <li>-DSVCnameroot</li>
    *    <li>-DORBagentAddr</li>
    *    <li>-DORBagentPort</li>
    * </ul>
    * <br />
    * <b>Example:</b>
    * <br />
    * <code>java -DORBagentAddr=192.168.1.1 -DORBagentPort=14000 -DORBservices=CosNaming -DSVCnameroot=xmlBlaster-Authenticate org.xmlBlaster.Main [arg]</code>
    * @param arg  parameters given on command line
    */
   public CorbaConnection(String[] arg)
   {
      args = arg;
      orb = org.omg.CORBA.ORB.init(args, null);
   }


   /**
    * CORBA client access to xmlBlaster for <strong>applets</strong>.
    * <p />
    * Use these environment settings for VisiBroker
    * <br />
    * <ul>
    *    <li>ORBservices</li>
    *    <li>SVCnameroot</li>
    *    <li>ORBagentAddr</li>
    *    <li>ORBagentPort</li>
    * </ul>
    * <br />
    * <b>Example:</b>
    *  <code>
    *     <-APPLET<br />
    *        CODEBASE = "http://localhost"<br>
    *        CODE     = "DemoApplet.class"<br>
    *        NAME     = "xmlBlaster demo"<br>
    *        WIDTH    = 200<br>
    *        HEIGHT   = 200<br>
    *        HSPACE   = 0<br>
    *        VSPACE   = 0<br>
    *        ALIGN    = middle<br>
    *     ><br />
    *     <-PARAM name=org.omg.CORBA.ORBClass value=com.visigenic.vbroker.orb.ORB><br>
    *     <-PARAM name=ORBServices value=CosNaming><br>
    *     <-PARAM name=SVCnameroot value=xmlBlaster-Authenticate><br>
    *     <-PARAM name=ORBagentAddr value=192.168.1.1><br>
    *     <-PARAM name=ORBagentPort value=14000><br>
    *     <-PARAM name=ORBgatekeeperIOR value=15000><br>
    *     <-/APPLET>
    *  </code>
    * @param ap  Applet handle
    */
   public CorbaConnection(Applet ap)
   {
      orb = org.omg.CORBA.ORB.init(ap, null);
   }


   /**
    * CORBA client access to xmlBlaster for <strong>servlets</strong>.
    * <p />
    * Use these environment settings for VisiBroker
    * <br />
    * <ul>
    *    <li>ORBservices</li>
    *    <li>SVCnameroot</li>
    *    <li>ORBagentAddr</li>
    *    <li>ORBagentPort</li>
    * </ul>
    * <br />
    * Usually you set these variables in the configuration file of your servlet engine (jrun, jserv etc.)
    * <p />
    * If you have a servlet development kit installed (http://java.sun.com/products/servlet/index.html)
    * you may remove the comments from this servlet based code.
    *
    * @param conf   Servlet Handle
    */
/*
   public CorbaConnection(ServletConfig conf)
   {
      // Setting the system properties
      Properties props = System.getProperties();
      java.util.Enumeration e = props.propertyNames();

      // These may be overwritten in /usr/local/apache/etc/servlet.properties
      // servlets.default.initArgs=DefaultTemplDir=/usr/local/apache/share/templates/,ORBagentAddr=192.168.1.1,ORBagentPort=14000,ORBservices=CosNaming,SVCnameroot=xmlBlaster-Authenticate

      if (conf.getInitParameter("ORBservices") != null) {
         props.put( "ORBservices", conf.getInitParameter("ORBservices"));
         if (Log.TRACE) Log.trace(ME, "  Found system parameter ORBservices=" + conf.getInitParameter("ORBservices"));
      }
      else
         props.put( "ORBservices", "CosNaming" );

      if (conf.getInitParameter("SVCnameroot") != null) {
         props.put( "SVCnameroot", conf.getInitParameter("SVCnameroot"));
         if (Log.TRACE) Log.trace(ME, "  Found system parameter SVCnameroot=" + conf.getInitParameter("SVCnameroot"));
      }
      else
         props.put( "SVCnameroot", "xmlBlaster-Authenticate" );

      if (conf.getInitParameter("ORBagentAddr") != null) {
         props.put( "ORBagentAddr", conf.getInitParameter("ORBagentAddr"));
         if (Log.TRACE) Log.trace(ME, "  Found system parameter ORBagentAddr=" + conf.getInitParameter("ORBagentAddr"));
      }
      else
         props.put( "ORBagentAddr", "192.168.200.8" );

      if (conf.getInitParameter("ORBagentPort") != null) {
         props.put( "ORBagentPort", conf.getInitParameter("ORBagentPort"));
         if (Log.TRACE) Log.trace(ME, "  Found system parameter ORBagentPort=" + conf.getInitParameter("ORBagentPort"));
      }
      else
         props.put( "ORBagentPort", "14000" );

      System.setProperties( props );

      if (Log.TRACE) {
         Log.trace(ME, "Known servlet system properties:");
         props = System.getProperties();
         e = props.propertyNames();
         while (e.hasMoreElements())
            Log.trace(ME, "    " + e.nextElement());
      }

      String agrs[] = null;
      orb = org.omg.CORBA.ORB.init(args, null);
   }
*/


   /**
    * Accessing the orb handle.
    * @return org.omg.CORBA.ORB
    */
   public org.omg.CORBA.ORB getOrb()
   {
      return orb;
   }


   /**
    * Locate the CORBA Name Service.
    * <p />
    * The found name service is cached, for better performance in subsequent calls
    * @return NamingContext, reference on name service
    * @exception XmlBlasterException
    *                    CORBA error handling if no naming service is found
    */
   public NamingContext getNamingService() throws XmlBlasterException
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
         Log.error(ME + ".NoNameService", text);
         throw new XmlBlasterException(ME + ".NoNameService", text);
      }
      if (nameServiceObj == null) {
         Log.error(ME + ".NoNameService", "Can't access naming service (null), is there any running?");
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
         Log.error(ME + ".NoNameService", "Can't access naming service");
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
   public AuthServer getAuthenticationService() throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "getAuthenticationService() ...");
      if (authServer != null) {
         // !!! check connection and do polling if not connected
         return authServer;
      }


      // 1) check if argument -IOR at program startup is given
      String authServerIOR = Args.getArg(args, "-ior", null);  // IOR string is directly given
      if (authServerIOR != null) {
         authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
         Log.info(ME, "Accessing xmlBlaster using your given IOR string");
         return authServer;
      }
      String authServerIORFile = Args.getArg(args, "-iorFile", null);  // IOR string is given through a file
      if (authServerIORFile != null) {
         authServerIOR = FileUtil.readAsciiFile(authServerIORFile);
         authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
         Log.info(ME, "Accessing xmlBlaster using your given IOR file " + authServerIORFile);
         return authServer;
      }


      // 2) check if argument -iorHost <hostName or IP> -iorPort <number> at program startup is given
      String iorHost = Args.getArg(args, "-iorHost", "localhost");
      int iorPort = Args.getArg(args, "-iorPort", 7609);
      if (iorHost != null && iorPort > 0) {
         authServerIOR = getAuthenticationServiceIOR(iorHost, iorPort);
         authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
         Log.info(ME, "Accessing xmlBlaster AuthServer IOR using builtin http connection, host " + iorHost + " and port " + iorPort);
         return authServer;
      }


      // 3) asking Name Service CORBA compliant
      NamingContext nc = getNamingService();

      try {
         NameComponent [] name = new NameComponent[1];
         name[0] = new NameComponent();
         name[0].id = "xmlBlaster-Authenticate";
         name[0].kind = "MOM";
         authServer = AuthServerHelper.narrow(nc.resolve(name));
         return authServer;
      }
      catch(Exception e) {
         Log.error(ME, e.toString());
         String text = "Can't access xmlBlaster Authentication Service, is the server running and ready?\n" +
                       " - try to specify '-iorFile <fileName>' if server is running on same host\n" +
                       " - try to specify '-iorHost <hostName> -iorPort 7609' to locate xmlBlaster\n" +
                       " - or contact your system administrator to start a naming service";

         throw new XmlBlasterException(ME + ".NoAuthService", text);
      }
   }


   /**
    * Login to the server, providing your own BlasterCallback implementation.
    * <p />
    * @param loginName The login name for xmlBlaster
    * @param passwd    The login password for xmlBlaster
    * @param qos       The Quality of Service for this client
    * @exception       XmlBlasterException if login fails
    */
   public Server login(String loginName, String passwd, BlasterCallback callback, String qos) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "login() ...");

      if (xmlBlaster != null) {
         Log.warning(ME, "You are already logged in, returning cached handle on xmlBlaster");
         return xmlBlaster;
      }

      this.callback = callback;
      this.loginName = loginName;
      this.qos = qos;

      try {
         xmlBlaster = getAuthenticationService().login(loginName, passwd, callback, qos);
         if (Log.TRACE) Log.trace(ME, "Success, login for " + loginName);
      } catch(XmlBlasterException e) {
         Log.error(ME, "Login failed");
         throw e;
      }
      return xmlBlaster;
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
    * @param loginName The login name for xmlBlaster
    * @param passwd    The login password for xmlBlaster
    * @param qos       The Quality of Service for this client
    * @param client    Your implementation of I_Callback
    * @exception       XmlBlasterException if login fails
    */
   public Server login(String loginName, String passwd, String qos, I_Callback client) throws XmlBlasterException
   {
      BlasterCallback callback = createCallbackServer(new DefaultCallback(loginName, client));

      if (Log.TRACE) Log.trace(ME, "Success, exported BlasterCallback Server interface for " + loginName);

      return login(loginName, passwd, callback, qos);
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
    * @return true successfully logged out
    *         false failure on logout
    */
   public boolean logout(Server xmlBlaster)
   {
      if (Log.CALLS) Log.calls(ME, "logout() ...");

      if (xmlBlaster == null) {
         Log.warning(ME, "Please pass your xmlBlaster server handle for logout method");
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
    * Building a Callback server, using the tie approach.
    *
    * @return the BlasterCallback server
    * @exception XmlBlasterException if the BlasterCallback server can't be created
    */
   public BlasterCallback createCallbackServer(BlasterCallbackOperations callbackImpl) throws XmlBlasterException
   {
      org.omg.PortableServer.POA poa;
      BlasterCallbackPOATie callbackTie;

      // Getting the default POA implementation "RootPOA"
      try {
         poa = org.omg.PortableServer.POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
      } catch (Exception e) {
         Log.error(ME + ".CallbackCreationError", "Can't create a BlasterCallback server, RootPOA not found: " + e.toString());
         throw new XmlBlasterException(ME + ".CallbackCreationError", e.toString());
      }

      // Intialize my Callback interface (tie approach):
      callbackTie = new BlasterCallbackPOATie(callbackImpl);

      try {
         callback = BlasterCallbackHelper.narrow(poa.servant_to_reference( callbackTie ));
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
   private String getAuthenticationServiceIOR(String iorHost, int iorPort)
   {
      try {
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
         return bos.toString();
      }
      catch (Exception ex) {
         Log.panic(ME, "Is xmlBlaster up and running? \n" + ex);
         return null;
      }
   }
}


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

   /**
    * Construct a persistently named object.
    */
   public DefaultCallback(String name, I_Callback boss)
   {
      this.ME = "DefaultCallback-" + name;
      this.boss = boss;
      this.loginName = name;
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
    * @param messageUnit      Contains a MessageUnit structs (your message)
    * @param qos              Quality of Service of the MessageUnit
    */
   public void update(MessageUnit[] messageUnitArr, String[] qos_literal_Arr)
   {
      if (Log.CALLS) Log.calls(ME, "Receiving update of " + messageUnitArr.length + " message ...");

      if (messageUnitArr.length == 0) {
         Log.warning(ME, "Entering update() with 0 messages");
         return;
      }

      for (int ii=0; ii<messageUnitArr.length; ii++) {
         MessageUnit messageUnit = messageUnitArr[ii];
         UpdateKey updateKey = null;
         UpdateQoS updateQoS = null;
         byte[] content = messageUnit.content;
         try {
            updateKey = new UpdateKey();
            updateKey.init(messageUnit.xmlKey);
            updateQoS = new UpdateQoS(qos_literal_Arr[ii]);
         } catch (XmlBlasterException e) {
            Log.error(ME, e.reason);
         }

         // Now we know all about the received message, dump it or do some checks
         if (Log.DUMP) Log.dump("UpdateKey", updateKey.printOn().toString());
         if (Log.DUMP) Log.dump("content", (new String(content)).toString());
         if (Log.DUMP) Log.dump("UpdateQoS", updateQoS.printOn().toString());
         if (Log.TRACE) Log.trace(ME, "Received message [" + updateKey.getUniqueKey() + "] from publisher " + updateQoS.getSender());

         boss.update(loginName, updateKey, content, updateQoS); // Call my boss
      }
   }
} // DefaultCallback

