/*------------------------------------------------------------------------------
Name:      XmlRpcConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.xmlrpc;

import java.io.IOException;
import java.util.Vector;

import org.jutils.text.StringHelper;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.protocol.ProtoConverter;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;

import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.engine.qos.GetQosServer;
import org.xmlBlaster.engine.qos.EraseQosServer;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;

import java.applet.Applet;

import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpc;
import org.apache.xmlrpc.XmlRpcException;


/**
 * This is an xmlBlaster proxy. It implements the interface I_XmlBlasterConnection. 
 * The client can invoke it as if the
 * xmlBlaster would be on the same VM, making this way the xml-rpc protocol
 * totally transparent.
 * <p />
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class XmlRpcConnection implements I_XmlBlasterConnection
{
   private String ME = "XmlRpcConnection";
   public static final int DEFAULT_SERVER_PORT = 8080; // port of xmlBlaster server
   private final Global glob;
   private final LogChannel log;
   private String url = "http://localhost:" + DEFAULT_SERVER_PORT;
   private XmlRpcClient xmlRpcClient; // xml-rpc client to send method calls.
   private String sessionId;
   protected String loginName;
   private String passwd;
   protected ConnectQos connectQos;
   protected ConnectReturnQos connectReturnQos;
   private boolean firstAttempt = true;

   /**
    * Connect to xmlBlaster using XML-RPC.
    */
   public XmlRpcConnection(Global glob) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = glob.getLog("xmlrpc");
   }

   /**
    * Connect to xmlBlaster using XML-RPC.
    */
   public XmlRpcConnection(Global glob, Applet ap) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = glob.getLog("xmlrpc");
    }

   /**
    * @return The connection protocol name "XML-RPC"
    */
   public final String getProtocol()
   {
      return "XML-RPC";
   }

   private void initXmlRpcClient() throws XmlBlasterException
   {
      try {
         String hostname = connectQos.getAddress().getHostname();
         hostname = glob.getProperty().get("xmlrpc.hostname", hostname);

         // default xmlBlaster XML-RPC publishing port is 8080
         int port = glob.getProperty().get("xmlrpc.port", DEFAULT_SERVER_PORT);
         this.url = "http://" + hostname + ":" + port + "/";

         if (glob.getProperty().get("xmlrpc.debug", false) == true)
            XmlRpc.setDebug(true);

         this.xmlRpcClient = new XmlRpcClient(url);
         log.info(ME, "Created XmlRpc client to " + url);
      }
      catch (java.net.MalformedURLException e) {
         log.error(ME+".constructor", "Malformed URL: " + e.toString());
         throw new XmlBlasterException("Malformed URL", e.toString());
      }
      catch (IOException e1) {
         log.error(ME+".constructor", "IO Exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "IO Exception", e1);
      }
   }

   public void resetConnection()
   {
      log.trace(ME, "XmlRpcCLient is initialized, no connection available");
      this.xmlRpcClient = null;
      this.sessionId = null;
   }

   private XmlRpcClient getXmlRpcClient() throws XmlBlasterException
   {
      if (this.xmlRpcClient == null) {
         if (log.TRACE) log.trace(ME, "No XML-RPC connection available.");
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                                       "The XML-RPC xmlBlaster handle is null, no connection available");
      }
      return this.xmlRpcClient;
   }


   /**
    * Does a login.
    * <p />
    * The callback is delivered like this in the qos argument:
    * <pre>
    *    &lt;qos>
    *       &lt;callback type='XML-RPC'>
    *          http://localhost:8081
    *       &lt;/callback>
    *    &lt;/qos>
    * </pre>
    * @param loginName The login name for xmlBlaster
    * @param passwd    The login password for xmlBlaster
    * @param qos       The Quality of Service for this client
    * @exception       XmlBlasterException if login fails
    */
   public void login(String loginName, String passwd, ConnectQos qos) throws XmlBlasterException
   {
      this.ME = "XmlRpcConnection-" + loginName;
      if (log.CALL) log.call(ME, "Entering login: name=" + loginName);
      if (isLoggedIn()) {
         log.warn(ME, "You are already logged in, no relogin possible.");
         return;
      }

      this.loginName = loginName;
      this.passwd = passwd;
      if (qos == null)
         this.connectQos = new ConnectQos(glob);
      else
         this.connectQos = qos;

      loginRaw(true);
   }


   public ConnectReturnQos connect(ConnectQos qos) throws XmlBlasterException
   {
      if (qos == null)
         throw new XmlBlasterException(ME+".connect()", "Please specify a valid QoS");

      this.ME = "XmlRpcConnection-" + qos.getUserId();
      if (log.CALL) log.call(ME, "Entering login: name=" + qos.getUserId());
      if (isLoggedIn()) {
         log.warn(ME, "You are already logged in, no relogin possible.");
         return this.connectReturnQos;
      }

      this.connectQos = qos;
      this.loginName = qos.getUserId();
      this.passwd = null;

      boolean verbose = this.firstAttempt;
      this.firstAttempt = false;

      return loginRaw(verbose);
   }

   /**
    * @see I_XmlBlasterConnection#loginRaw
    */
   public ConnectReturnQos loginRaw() throws XmlBlasterException {
      return loginRaw(false);
   }

   /**
    * Login to the server.
    * <p />
    * For internal use only.
    * @exception       XmlBlasterException if login fails
    */
   private ConnectReturnQos loginRaw(boolean verbose) throws XmlBlasterException {
      try {
         initXmlRpcClient();
         // prepare the argument vector for the xml-rpc method call

         String qosOrig = connectQos.toXml();
         String qosStripped = StringHelper.replaceAll(qosOrig, "<![CDATA[", "");
         qosStripped = StringHelper.replaceAll(qosStripped, "]]>", "");
         if (!qosStripped.equals(qosOrig)) {
            log.trace(ME, "Stripped CDATA tags surrounding security credentials, XML-RPC does not like it (Helma does not escape ']]>'). " +
                           "This shouldn't be a problem as long as your credentials doesn't contain '<'");
         }

         Vector args = new Vector();
         if (passwd == null) // The new schema
         {
            if (log.TRACE) log.trace(ME, "Executing authenticate.connect() via XmlRpc with security plugin" + qosStripped);
            args.addElement(qosStripped);
            this.sessionId = null;
            String tmp = (String)getXmlRpcClient().execute("authenticate.connect", args);
            this.connectReturnQos = new ConnectReturnQos(glob, tmp);
            this.sessionId = connectReturnQos.getSecretSessionId();
         }
         else
         {
            if (log.TRACE) log.trace(ME, "Executing authenticate.login() via XmlRpc for loginName " + loginName);

            args.addElement(loginName);
            args.addElement(passwd);
            args.addElement(qosStripped);
            this.sessionId = null;
            args.addElement(""/*sessionId*/); // Let xmlBlaster generate the sessionId
            this.sessionId = (String)getXmlRpcClient().execute("authenticate.login", args);
         }
         if (log.DUMP) log.dump(ME, connectQos.toXml());
         return this.connectReturnQos;
      }
      catch (ClassCastException e) {
         log.error(ME+".login", "return value not a valid String: " + e.toString());
         throw new XmlBlasterException(ME+".LoginFailed", "return value not a valid String, Class Cast Exception: " + e.toString());
      }
      catch (IOException e) {
         this.sessionId = null;
         log.warn(ME+".login", "Login to xmlBlaster failed: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "Login failed", e);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e);
      }
   }


   /**
    * Access the login name.
    * @return your login name or null if you are not logged in
    */
   public String getLoginName()
   {
      return this.loginName;
   }


   /**
    * Does a logout. 
    * <p />
    * @param sessionId The client sessionId
    */
   public boolean disconnect(DisconnectQos qos)
   {
      if (log.CALL) log.call(ME, "Entering logout: id=" + sessionId);

      if (!isLoggedIn()) {
         log.warn(ME, "You are not logged in, no logout possible.");
      }

      try {
         if (this.xmlRpcClient != null) {
            // prepare the argument vector for the xml-rpc method call
            Vector args = new Vector();
            args.addElement(sessionId);
            args.addElement((qos==null)?" ":qos.toXml()); // qos
            this.xmlRpcClient.execute("authenticate.disconnect", args);
         }

         try {
            shutdown();
         }
         catch (XmlBlasterException ex) {
            this.log.error(ME, "disconnect() could not shutdown properly. " + ex.getMessage());
         }
         resetConnection();
         return true;
      }
      catch (IOException e1) {
         log.warn(ME+".logout", "IO exception: " + e1.toString());
      }
      catch (XmlRpcException e) {
         log.warn(ME+".logout", "xml-rpc exception: " + extractXmlBlasterException(glob, e).toString());
      }

      try {
         shutdown();
      }
      catch (XmlBlasterException ex) {
         this.log.error(ME, "disconnect() could not shutdown properly. " + ex.getMessage());
      }
      resetConnection();
      return false;
   }


   /**
    * Shut down. 
    * Is called by logout()
    */
   public void shutdown() throws XmlBlasterException
   {
   }


   /**
    * @return true if you are logged in
    */
   public boolean isLoggedIn()
   {
      return this.xmlRpcClient != null && this.sessionId != null;
   }


   /**
    * Enforced by I_XmlBlasterConnection interface (fail save mode).
    * Subscribe to messages.
    * <p />
    */
   public final String subscribe (String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering subscribe(id=" + sessionId + ")");
      try {
         // prepare the argument vector for the xml-rpc method call
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);
         return (String)getXmlRpcClient().execute("xmlBlaster.subscribe", args);
      }
      catch (ClassCastException e) {
         log.error(ME+".subscribe", "return value not a valid String: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME+".subscribe", "return value not a valid String, Class Cast Exception", e);
      }
      catch (IOException e1) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "subscribe", e1);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e);
      }
   }


   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] unSubscribe (String xmlKey_literal,
                                 String qos_literal) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering unsubscribe(): id=" + sessionId);

      try {
         // prepare the argument list:
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);

         Vector vec = (Vector)getXmlRpcClient().execute("xmlBlaster.unSubscribe", args);
         return ProtoConverter.vector2StringArray(vec);
      }
      catch (IOException e1) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "unSubscribe", e1);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e);
      }
   }



   /**
    * Publish a message.
    */
   public final String publish(MsgUnitRaw msgUnit) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering publish(): id=" + sessionId);

      //PublishQos publishQos = new PublishQos(msgUnit.getQos());
      //msgUnit.getQos() = publishQos.toXml();

      try {
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(msgUnit.getKey());
         args.addElement(msgUnit.getContent());
         args.addElement(msgUnit.getQos());

         return (String)getXmlRpcClient().execute("xmlBlaster.publish", args);
      }

      catch (ClassCastException e) {
         log.error(ME+".publish", "not a valid MsgUnitRaw: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME+".publish", "Not a valid MsgUnitRaw", e);
      }
      catch (IOException e1) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "publish", e1);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e);
      }
   }


   /**
    * Publish multiple messages in one sweep.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] publishArr(MsgUnitRaw[] msgUnitArr)
      throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering publishArr: id=" + sessionId);

      if (msgUnitArr == null) {
         log.error(ME + ".InvalidArguments", "The argument of method publishArr() are invalid");
         throw new XmlBlasterException(ME + ".InvalidArguments",
                                       "The argument of method publishArr() are invalid");
      }

      try {

         Vector msgUnitArrWrap = ProtoConverter.messageUnitArray2Vector(msgUnitArr);
         // prepare the argument list (as a Vector)
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(msgUnitArrWrap);

         Vector returnVectorWrap = (Vector)getXmlRpcClient().execute("xmlBlaster.publishArr", args);

      // re-extractXmlBlasterException the resuts to String[]
         return ProtoConverter.vector2StringArray(returnVectorWrap);
      }

      catch (ClassCastException e) {
         log.error(ME+".publishArr", "not a valid String[]: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME+".publishArr", "Not a valid String[]", e);
      }

      catch (IOException e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "publishArr", e);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e);
      }
   }


   /**
    * Publish multiple messages in one sweep.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final void publishOneway(MsgUnitRaw[] msgUnitArr)
      throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering publishOneway: id=" + sessionId);

      if (msgUnitArr == null) {
         log.error(ME + ".InvalidArguments", "The argument of method publishOneway() are invalid");
         throw new XmlBlasterException(ME + ".InvalidArguments",
                                       "The argument of method publishOneway() are invalid");
      }

      try {
         Vector msgUnitArrWrap = ProtoConverter.messageUnitArray2Vector(msgUnitArr);
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(msgUnitArrWrap);
         getXmlRpcClient().execute("xmlBlaster.publishOneway", args);
      }
      catch (ClassCastException e) {
         log.error(ME+".publishOneway", e.toString());
         e.printStackTrace();
         throw new XmlBlasterException(ME+".publishOneway", "Class Cast Exception");
      }
      catch (IOException e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "publishOneway", e);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e);
      }
   }


   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] erase (XmlKey xmlKey, EraseQosServer eraseQoS)
      throws XmlBlasterException
   {
      String xmlKey_literal = xmlKey.toXml();
      String eraseQoS_literal = eraseQoS.toXml();

      return erase(xmlKey_literal, eraseQoS_literal);
   }



   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] erase(String xmlKey_literal, String qos_literal)
      throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering erase() id=" + sessionId);

      try {
         // prepare the argument list (as a Vector) for xml-rpc
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);

         Vector vec = (Vector)getXmlRpcClient().execute("xmlBlaster.erase", args);
         return ProtoConverter.vector2StringArray(vec);

      }

      catch (ClassCastException e) {
         log.error(ME+".erase", "not a valid Vector: " + e.toString());
         throw new XmlBlasterException("Not a valid Vector", "Class Cast Exception");
      }

      catch (IOException e1) {
         log.error(ME+".erase", "IO exception: " + e1.toString() + " sessionId=" + this.sessionId);
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "erase", e1);
      }

      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e);
      }
   }


   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final MsgUnitRaw[] get (XmlKey xmlKey, GetQosServer getQoS)
      throws XmlBlasterException
   {
      String xmlKey_literal = xmlKey.toXml();
      String getQoS_literal = getQoS.toXml();

      return get(xmlKey_literal, getQoS_literal);
   }


   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final MsgUnitRaw[] get(String xmlKey_literal,
                                  String qos_literal) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering get() xmlKey=\n" + xmlKey_literal + ") ...");
      try {
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);

         Vector retVector = (Vector)getXmlRpcClient().execute("xmlBlaster.get", args);
         // extractXmlBlasterException the vector of vectors to a MsgUnitRaw[] type
         return ProtoConverter.vector2MsgUnitRawArray(retVector);
      }
      catch (ClassCastException e) {
         log.error(ME+".get", "not a valid Vector: " + e.toString());
         throw new XmlBlasterException("Not a valid Vector", "Class Cast Exception");
      }
      catch (IOException e1) {
         log.error(ME+".get", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "get", e1);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e);
      }
   }


   /**
    * Helma XmlRpc does in XmlRpcServer.java:314 an exception.toString() which is sent back to the client. 
    * <br />
    * xml-rpc exception: org.apache.xmlrpc.XmlRpcException: java.lang.Exception: errorCode=resource.unavailable message=The key 'NotExistingMessage' is not available.
    */
   public static XmlBlasterException extractXmlBlasterException(Global glob, XmlRpcException e) {
      return XmlBlasterException.parseToString(glob, e.toString());
   }


   /**
    * Check server.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String ping(String str) throws XmlBlasterException
   {
      try {
         Vector args = new Vector();
         args.addElement("");
         return (String)getXmlRpcClient().execute("xmlBlaster.ping", args);
      } catch(Exception e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "ping", e);
      }
   }

   public String toXml() throws XmlBlasterException
   {
      return toXml("");
   }

   /**
    * Dump of the server, remove in future.
    */
   public String toXml(String extraOffset) throws XmlBlasterException
   {
      if (!isLoggedIn()) return "<noConnection />";
      try {
         Vector args = new Vector();
         args.addElement(extraOffset);
         return (String)this.xmlRpcClient.execute("xmlBlaster.toXml", args);
      }
      catch (ClassCastException e) {
         log.error(ME+".toXml", "not a valid Vector: " + e.toString());
         throw new XmlBlasterException("Not a valid Vector", "Class Cast Exception");
      }
      catch (IOException e1) {
         log.error(ME+".toXml", "IO exception: " + e1.toString());
         throw new XmlBlasterException("IO exception", e1.toString());
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e);
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
      text += "XmlRpcConnection 'XML-RPC' options:\n";
      text += "   -xmlrpc.port        Specify a port number where xmlBlaster XML-RPC web server listens.\n";
      text += "                       Default is port "+DEFAULT_SERVER_PORT+", the port 0 switches this feature off.\n";
      text += "   -xmlrpc.hostname    Specify a hostname where the xmlBlaster web server runs.\n";
      text += "                       Default is the localhost.\n";
      text += "   -xmlrpc.portCB      Specify a port number for the callback web server to listen.\n";
      text += "                       Default is port "+XmlRpcCallbackServer.DEFAULT_CALLBACK_PORT+", the port 0 switches this feature off.\n";
      text += "   -xmlrpc.hostnameCB  Specify a hostname where the callback web server shall run.\n";
      text += "                       Default is the localhost (useful for multi homed hosts).\n";
      text += "   -xmlrpc.debug       true switches on detailed XML-RPC debugging [false].\n";
      text += "\n";
      return text;
   }


   /**
    * For Testing.
    * <pre>
    * java org.xmlBlaster.client.protocol.xmlrpc.XmlRpcConnection
    * </pre>
    */
   public static void main(String args[])
   {
   /*
      final String ME = "XmlRpcHttpClient";
      try { XmlBlasterProperty.init(args); } catch(org.jutils.JUtilsException e) { log.panic(ME, e.toString()); }
      // build the proxy
      try {
         XmlRpcConnection proxy = new XmlRpcConnection("http://localhost:8080", 8081);

         String qos = "<qos><callback type='XML-RPC'>http://localhost:8081</callback></qos>";
         String sessionId = "Session1";

         String loginAnswer = proxy.login("LunaMia", "silence", qos, sessionId);
         log.info(ME, "The answer from the login is: " + loginAnswer);

         String contentString = "This is a simple Test Message for the xml-rpc Protocol";
         byte[] content = contentString.getBytes();

         org.xmlBlaster.client.key.PublishKey xmlKey = new org.xmlBlaster.client.key.PublishKey("", "text/xml", null);

         MsgUnitRaw msgUnit = new MsgUnitRaw(xmlKey.toXml(), content, "<qos></qos>");
         String publishOid = proxy.publish(sessionId, msgUnit);
         log.info(ME, "Published message with " + publishOid);

         org.xmlBlaster.client.key.SubscribeKey subscribeKey = new org.xmlBlaster.client.key.SubscribeKey(publishOid);

         log.info(ME, "Subscribe key: " + subscribeKey.toXml());

         proxy.subscribe(sessionId, subscribeKey.toXml(), "");

         // wait some time if necessary ....
         proxy.erase(sessionId, subscribeKey.toXml(), "");

         log.exit(ME, "Good bye.");

      } catch(XmlBlasterException e) {
         log.error(ME, "XmlBlasterException: " + e.toString());
      }

      // wait for some time here ....
     */
   }


}

