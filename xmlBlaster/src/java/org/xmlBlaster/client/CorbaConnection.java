/*------------------------------------------------------------------------------
Name:      CorbaConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: CorbaConnection.java,v 1.25 2000/02/24 22:08:28 ruff Exp $
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
 * This is a little helper class, helping a Java client to connect to xmlBlaster
 * using IIOP (CORBA).
 * <p>
 * There is a constructor for applets, and standalone Java clients.
 * <p />
 * If you need some fails save clients, you can invoke the xmlBlaster CORBA methods
 * through this class as well (for example use corbaConnection.publish() instead of the direct
 * CORBA server.publish()).<br />
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
 *
 * Invoke: jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestSub
 * <p />
 * If you want to connect from a servlet, please use the framework in xmlBlaster/src/java/org/xmlBlaster/protocol/http
 *
 * @version $Revision: 1.25 $
 * @author $Author: ruff $
 */
public class CorbaConnection implements ServerOperations
{
   private final String ME = "CorbaConnection";
   protected String[] args = null;
   protected org.omg.CORBA.ORB orb = null;
   protected NamingContext nameService = null;
   protected AuthServer authServer = null;
   protected Server xmlBlaster = null;
   protected BlasterCallback callback = null;
   protected String loginName = null;
   protected String qos = null;

   /** Maximum polling in milli-sec */
   private long pollTimeout = -1L;
   /** queue all the messages, and play them back through interface ServerOperations */
   private InvocationRecorder recorder = null;
   // !!! remove these again:
   private MessageUnitContainer[] dummyMArr = new MessageUnitContainer[0];
   private String[] dummySArr = new String[0];
   private String dummyS = "";


   /**
    * CORBA client access to xmlBlaster (default behavior).
    * <p />
    * If you need a fails save mode, call initFailSave()
    */
   public CorbaConnection()
   {
      args = new String[1];  // dummy
      args[0] = ME;
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
      java.util.Properties props = null;
      /*
      // try to force to use JacORB instead of builtin CORBA:
      String orbClassName = "jacorb.orb.ORB";
      String orbSingleton = "jacorb.orb.ORBSingleton";
      java.util.Properties props = new java.util.Properties();
      props.put("org.omg.CORBA.ORBClass", orbClassName);
      props.put("org.omg.CORBA.ORBSingletonClass", orbSingleton);

      Log.info(ME, "Using ORB=" + orbClassName + " and ORBSingleton=" + orbSingleton);
      */

      orb = org.omg.CORBA.ORB.init(ap, props);
   }


   /**
    * @param timeout How long shall we try polling (in seconds), -1 == forever
    * @param maxMessages How many messages shall we queue max
    */
   public void initFailSave(I_ConnectionProblems callback, int timeout, int maxMessages)
   {
      this.pollTimeout = timeout * 1000L;
      this.recorder = new InvocationRecorder(maxMessages, this, null);
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
      String authServerIOR = Args.getArg(args, "-ior", (String)null);  // IOR string is directly given
      if (authServerIOR != null) {
         authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
         Log.info(ME, "Accessing xmlBlaster using your given IOR string");
         return authServer;
      }
      String authServerIORFile = Args.getArg(args, "-iorFile", (String)null);  // IOR string is given through a file
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
         try {
            authServerIOR = getAuthenticationServiceIOR(iorHost, iorPort);
            authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
            Log.info(ME, "Accessing xmlBlaster AuthServer IOR using builtin http connection, host " + iorHost + " and port " + iorPort);
            return authServer;
         }
         catch(Exception e) {
            Log.warning(ME, "XmlBlaster not found on host " + iorHost + " and port " + iorPort + ". Trying to find a naming service ...");
         }
      }


      // 3) asking Name Service CORBA compliant
      boolean useNameService = Args.getArg(args, "-ns", true);  // default is to ask the naming service
      if (useNameService) {
         NamingContext nc = getNamingService();

         try {
            NameComponent [] name = new NameComponent[1];
            name[0] = new NameComponent();
            name[0].id = "xmlBlaster-Authenticate";
            name[0].kind = "MOM";
            authServer = AuthServerHelper.narrow(nc.resolve(name));
            Log.info(ME, "Accessing xmlBlaster using a naming service.");
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

      String text = "Can't access xmlBlaster Authentication Service, is the server running and ready?\n" +
                     " - try to specify '-iorFile <fileName>' if server is running on same host\n" +
                     " - try to specify '-iorHost <hostName> -iorPort 7609' to locate xmlBlaster\n" +
                     " - or contact your system administrator to start a naming service";
      throw new XmlBlasterException(ME + ".NoAuthService", text);
   }


   /**
    * Login to the server without any callback.
    * <p />
    * Note that only the synchronous get() method is available in this case.
    * @param loginName The login name for xmlBlaster
    * @param passwd    The login password for xmlBlaster
    * @param qos       The Quality of Service for this client
    * @exception       XmlBlasterException if login fails
    */
   public Server login(String loginName, String passwd, String qos) throws XmlBlasterException
   {
      return login(loginName, passwd, (BlasterCallback)null, qos);
   }


   /**
    * Login to the server, providing your own BlasterCallback implementation.
    * <p />
    * @param loginName The login name for xmlBlaster
    * @param passwd    The login password for xmlBlaster
    * @param callback  The Callback interface of this client or null if none is used
    * @param qos       The Quality of Service for this client
    * @exception       XmlBlasterException if login fails
    */
   public Server login(String loginName, String passwd, BlasterCallback callback, String qos) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "login(" + loginName + ") ...");

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
      // Log.trace(ME, "Sending IOR='" + ior + "'");
      return ior;
   }


   /**
    * Enforced by ServerOperations interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public String subscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "subscribe() ...");
      return "";
   }


   /**
    * Enforced by ServerOperations interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public void unSubscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "unSubscribe() ...");
   }


   /**
    * Publish fault-tolerant the given message.
    * <p />
    * This is a wrapper around the raw CORBA publish() method
    * If the server disappears you get an exception.
    * <p />
    * Enforced by ServerOperations interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public String publish(MessageUnit messageUnit, String qos_literal) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Publishing ...");
      try {
         return xmlBlaster.publish(messageUnit, qos);
      } catch(XmlBlasterException e) {
         Log.warning(ME, "XmlBlasterException: " + e.reason);
         throw e;
      } catch(Exception e) {
         if (recorder != null) {
            String text = "Exception: " + e.toString() + "\nGoing to poll for xmlBlaster and queue your messages ...";
            Log.warning(ME, text);
            throw new XmlBlasterException("TryingConnect", text);
         }
         Log.error(ME, e.toString());
         throw new XmlBlasterException("NoConnect", e.toString());
      }
   }


   /**
    * Enforced by ServerOperations interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public String[] publishArr(MessageUnit [] messageUnitArr, String [] qos_literal_Arr) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "publishArr() ...");
      return dummySArr;
   }


   /**
    * Enforced by ServerOperations interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public String[] erase(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "erase() ...");
      return dummySArr;
   }


   /**
    * Enforced by ServerOperations interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public MessageUnitContainer[] get(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "get() ...");
      return dummyMArr;
   }


   /**
    * Enforced by ServerOperations interface (fail save mode)
    * @see xmlBlaster.idl
    */
   public void setClientAttributes(String clientName, String xmlAttr_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "setClientAttributes() ...");
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
         if (Log.DUMP) Log.dump("UpdateKey", "\n" + updateKey.printOn().toString());
         if (Log.DUMP) Log.dump("content", "\n" + new String(content));
         if (Log.DUMP) Log.dump("UpdateQoS", "\n" + updateQoS.printOn().toString());
         if (Log.TRACE) Log.trace(ME, "Received message [" + updateKey.getUniqueKey() + "] from publisher " + updateQoS.getSender());

         boss.update(loginName, updateKey, content, updateQoS); // Call my boss
      }
   }
} // DefaultCallback

