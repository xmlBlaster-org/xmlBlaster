/*------------------------------------------------------------------------------
Name:      XmlRpcConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native xmlBlaster Proxy. Can be called by the client in the same VM
Version:   $Id: XmlRpcConnection.java,v 1.26 2002/05/19 12:55:41 ruff Exp $
Author:    michele.laghi@attglobal.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.xmlrpc;

import java.io.IOException;
import java.util.Vector;

import org.jutils.text.StringHelper;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.util.protocol.ProtoConverter;
import org.xmlBlaster.util.XmlBlasterException;

import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;
import org.xmlBlaster.client.protocol.ConnectionException;

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
 * @author michele.laghi@attglobal.net
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public class XmlRpcConnection implements I_XmlBlasterConnection
{
   private String ME = "XmlRpcConnection";
   public static final int DEFAULT_SERVER_PORT = 8080; // port of xmlBlaster server
   private final Global glob;
   private String url = "http://localhost:" + DEFAULT_SERVER_PORT;
   private XmlRpcClient xmlRpcClient = null; // xml-rpc client to send method calls.
   private String sessionId = null;
   protected String loginName = null;
   private String passwd = null;
   protected ConnectQos loginQos = null;
   protected ConnectReturnQos connectReturnQos = null;

   /**
    * Connect to xmlBlaster using XML-RPC.
    */
   public XmlRpcConnection(Global glob) throws XmlBlasterException
   {
      this.glob = glob;
   }

   /**
    * Connect to xmlBlaster using XML-RPC.
    */
   public XmlRpcConnection(Global glob, Applet ap) throws XmlBlasterException
   {
      this.glob = glob;
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
         String hostname;
         try  {
            java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
            hostname = addr.getHostName();
         } catch (Exception e) {
            Log.warn(ME, "Can't determin your hostname");
            hostname = "localhost";
         }
         hostname = glob.getProperty().get("xmlrpc.hostname", hostname);

         // default xmlBlaster XML-RPC publishing port is 8080
         int port = glob.getProperty().get("xmlrpc.port", DEFAULT_SERVER_PORT);
         this.url = "http://" + hostname + ":" + port + "/";

         if (glob.getProperty().get("xmlrpc.debug", false) == true)
            XmlRpc.setDebug(true);

         this.xmlRpcClient = new XmlRpcClient(url);
         Log.info(ME, "Created XmlRpc client to " + url);
      }
      catch (java.net.MalformedURLException e) {
         Log.error(ME+".constructor", "Malformed URL: " + e.toString());
         throw new XmlBlasterException("Malformed URL", e.toString());
      }
      catch (IOException e1) {
         Log.error(ME+".constructor", "IO Exception: " + e1.toString());
         throw new XmlBlasterException("IO Exception", e1.toString());
      }
   }

   public void init()
   {
      Log.trace(ME, "XmlRpcCLient is initialized, no connection available");
      this.xmlRpcClient = null;
   }

   private XmlRpcClient getXmlRpcClient() throws ConnectionException
   {
      if (this.xmlRpcClient == null) {
         if (Log.TRACE) Log.trace(ME, "No XML-RPC connection available.");
         throw new ConnectionException(ME+".init", "No XML-RPC connection available.");
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
   public void login(String loginName, String passwd, ConnectQos qos) throws XmlBlasterException, ConnectionException
   {
      this.ME = "XmlRpcConnection-" + loginName;
      if (Log.CALL) Log.call(ME, "Entering login: name=" + loginName);
      if (isLoggedIn()) {
         Log.warn(ME, "You are already logged in, no relogin possible.");
         return;
      }

      this.loginName = loginName;
      this.passwd = passwd;
      if (qos == null)
         this.loginQos = new ConnectQos(glob);
      else
         this.loginQos = qos;

      loginRaw();
   }


   public ConnectReturnQos connect(ConnectQos qos) throws XmlBlasterException, ConnectionException
   {
      if (qos == null)
         throw new XmlBlasterException(ME+".connect()", "Please specify a valid QoS");

      this.ME = "XmlRpcConnection-" + qos.getUserId();
      if (Log.CALL) Log.call(ME, "Entering login: name=" + qos.getUserId());
      if (isLoggedIn()) {
         Log.warn(ME, "You are already logged in, no relogin possible.");
         return this.connectReturnQos;
      }

      this.loginQos = qos;
      this.loginName = qos.getUserId();
      this.passwd = null;

      return loginRaw();
   }


   /**
    * Login to the server.
    * <p />
    * For internal use only.
    * @exception       XmlBlasterException if login fails
    */
   public ConnectReturnQos loginRaw() throws XmlBlasterException, ConnectionException
   {
      try {
         initXmlRpcClient();
         // prepare the argument vector for the xml-rpc method call

         String qosOrig = loginQos.toXml();
         String qosStripped = StringHelper.replaceAll(qosOrig, "<![CDATA[", "");
         qosStripped = StringHelper.replaceAll(qosStripped, "]]>", "");
         if (!qosStripped.equals(qosOrig)) {
            Log.trace(ME, "Stripped CDATA tags surrounding security credentials, XML-RPC does not like it (Helma does not escape ']]>'). " +
                           "This shouldn't be a problem as long as your credentials doesn't contain '<'");
         }

         Vector args = new Vector();
         if (passwd == null) // The new schema
         {
            if (Log.TRACE) Log.trace(ME, "Executing authenticate.connect() via XmlRpc with security plugin" + qosStripped);
            args.addElement(qosStripped);
            sessionId = null;
            String tmp = (String)getXmlRpcClient().execute("authenticate.connect", args);
            this.connectReturnQos = new ConnectReturnQos(glob, tmp);
            this.sessionId = connectReturnQos.getSessionId();
         }
         else
         {
            if (Log.TRACE) Log.trace(ME, "Executing authenticate.login() via XmlRpc for loginName " + loginName);

            args.addElement(loginName);
            args.addElement(passwd);
            args.addElement(qosStripped);
            sessionId = ""; // Let xmlBlaster generate the sessionId
            args.addElement(sessionId);
            this.sessionId = (String)getXmlRpcClient().execute("authenticate.login", args);
         }
         if (Log.DUMP) Log.dump(ME, loginQos.toXml());
         return this.connectReturnQos;
      }
      catch (ClassCastException e) {
         Log.error(ME+".login", "return value not a valid String: " + e.toString());
         throw new XmlBlasterException(ME+".LoginFailed", "return value not a valid String, Class Cast Exception: " + e.toString());
      }
      catch (IOException e) {
         Log.error(ME+".login", "IO exception: " + e.toString());
         throw new ConnectionException(ME+".LoginFailed", e.toString());
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(e);
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
   public boolean logout()
   {
      if (Log.CALL) Log.call(ME, "Entering logout: id=" + sessionId);

      if (!isLoggedIn()) {
         Log.warn(ME, "You are not logged in, no logout possible.");
      }

      try {
         if (this.xmlRpcClient != null) {
            if(passwd==null) {
               // prepare the argument vector for the xml-rpc method call
               Vector args = new Vector();
               args.addElement(sessionId);
               args.addElement(" "); // qos
               this.xmlRpcClient.execute("authenticate.disconnect", args);
            }
            else {
               // prepare the argument vector for the xml-rpc method call
               Vector args = new Vector();
               args.addElement(sessionId);
               this.xmlRpcClient.execute("authenticate.logout", args);
            }
         }
         shutdown();
         init();
         return true;
      }
      catch (IOException e1) {
         Log.warn(ME+".logout", "IO exception: " + e1.toString());
      }
      catch (XmlRpcException e) {
         Log.warn(ME+".logout", "xml-rpc exception: " + extractXmlBlasterException(e).toString());
      }

      shutdown();
      init();
      return false;
   }


   /**
    * Shut down. 
    * Is called by logout()
    */
   public boolean shutdown()
   {
      return true;
   }


   /**
    * @return true if you are logged in
    */
   public boolean isLoggedIn()
   {
      return this.xmlRpcClient != null;
   }


   /**
    * Enforced by I_XmlBlasterConnection interface (fail save mode).
    * Subscribe to messages.
    * <p />
    */
   public final String subscribe (String xmlKey_literal, String qos_literal) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "Entering subscribe(id=" + sessionId + ")");
      try {
         // prepare the argument vector for the xml-rpc method call
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);
         return (String)getXmlRpcClient().execute("xmlBlaster.subscribe", args);
      }
      catch (ClassCastException e) {
         Log.error(ME+".subscribe", "return value not a valid String: " + e.toString());
         throw new XmlBlasterException(ME+".subscribe", "return value not a valid String, Class Cast Exception");
      }
      catch (IOException e1) {
         Log.error(ME+".subscribe", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".subscribe", e1.toString());
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(e);
      }
   }


   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final void unSubscribe (String xmlKey_literal,
                                 String qos_literal) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "Entering unsubscribe(): id=" + sessionId);

      try {
         // prepare the argument list:
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);

         getXmlRpcClient().execute("xmlBlaster.unSubscribe", args);
      }
      catch (IOException e1) {
         Log.error(ME+".unSubscribe", "IO exception: " + e1.toString());
         throw new ConnectionException("IO exception", e1.toString());
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(e);
      }
   }



   /**
    * Publish a message.
    */
   public final String publish(MessageUnit msgUnit) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "Entering publish(): id=" + sessionId);

      //PublishQos publishQos = new PublishQos(msgUnit.qos);
      //msgUnit.qos = publishQos.toXml();

      try {
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(msgUnit.xmlKey);
         args.addElement(msgUnit.content);
         args.addElement(msgUnit.qos);

         return (String)getXmlRpcClient().execute("xmlBlaster.publish", args);
      }

      catch (ClassCastException e) {
         Log.error(ME+".publish", "not a valid MessageUnit: " + e.toString());
         throw new XmlBlasterException("Not a valid Message Unit", "Class Cast Exception");
      }
      catch (IOException e1) {
         Log.error(ME+".publish", "IO exception: " + e1.toString());
         throw new ConnectionException("IO exception", e1.toString());
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(e);
      }
   }


   /**
    * Publish multiple messages in one sweep.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] publishArr(MessageUnit[] msgUnitArr)
      throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "Entering publishArr: id=" + sessionId);

      if (msgUnitArr == null) {
         Log.error(ME + ".InvalidArguments", "The argument of method publishArr() are invalid");
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
         Log.error(ME+".publishArr", "not a valid String[]: " + e.toString());
         throw new XmlBlasterException("Not a valid String[]", "Class Cast Exception");
      }

      catch (IOException e1) {
         Log.error(ME+".publishArr", "IO exception: " + e1.toString());
         throw new ConnectionException("IO exception", e1.toString());
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(e);
      }
   }


   /**
    * Publish multiple messages in one sweep.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final void publishOneway(MessageUnit[] msgUnitArr)
      throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "Entering publishOneway: id=" + sessionId);

      if (msgUnitArr == null) {
         Log.error(ME + ".InvalidArguments", "The argument of method publishOneway() are invalid");
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
         Log.error(ME+".publishOneway", e.toString());
         e.printStackTrace();
         throw new XmlBlasterException(ME+".publishOneway", "Class Cast Exception");
      }
      catch (IOException e1) {
         Log.error(ME+".publishOneway", "IO exception: " + e1.toString());
         throw new ConnectionException("IO exception", e1.toString());
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(e);
      }
   }


   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] erase (XmlKey xmlKey, EraseQoS eraseQoS)
      throws XmlBlasterException, ConnectionException
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
      throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "Entering erase() id=" + sessionId);

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
         Log.error(ME+".erase", "not a valid Vector: " + e.toString());
         throw new XmlBlasterException("Not a valid Vector", "Class Cast Exception");
      }

      catch (IOException e1) {
         Log.error(ME+".erase", "IO exception: " + e1.toString());
         throw new ConnectionException("IO exception", e1.toString());
      }

      catch (XmlRpcException e) {
         throw extractXmlBlasterException(e);
      }
   }


   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final MessageUnit[] get (XmlKey xmlKey, GetQoS getQoS)
      throws XmlBlasterException, ConnectionException
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
   public final MessageUnit[] get(String xmlKey_literal,
                                  String qos_literal) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "Entering get() xmlKey=\n" + xmlKey_literal + ") ...");
      try {
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);

         Vector retVector = (Vector)getXmlRpcClient().execute("xmlBlaster.get", args);
         // extractXmlBlasterException the vector of vectors to a MessageUnit[] type
         return ProtoConverter.vector2MessageUnitArray(retVector);
      }
      catch (ClassCastException e) {
         Log.error(ME+".get", "not a valid Vector: " + e.toString());
         throw new XmlBlasterException("Not a valid Vector", "Class Cast Exception");
      }
      catch (IOException e1) {
         Log.error(ME+".get", "IO exception: " + e1.toString());
         throw new ConnectionException("IO exception", e1.toString());
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(e);
      }
   }


   /**
    * xml-rpc exception: org.apache.xmlrpc.XmlRpcException: java.lang.Exception: id=RequestBroker.UnavailableKey reason=The key 'NotExistingMessage' is not available.
    */
   public static XmlBlasterException extractXmlBlasterException(XmlRpcException e) {
      String all = e.toString();
      String id = "XmlRpc";
      String reason = all;
      int start = all.indexOf("id=");
      int end = all.indexOf("reason=");
      if (start >= 0) {
         if (end >= 0) {
            try { id = all.substring(start+3, end-1); } catch(IndexOutOfBoundsException e1) {}
         }
         else {
            try { id = all.substring(start+3); } catch(IndexOutOfBoundsException e2) {}
         }
      }
      if (end >= 0) {
         try { reason = all.substring(end+7); } catch(IndexOutOfBoundsException e3) {}
      }
      return new XmlBlasterException(id, reason);
   }


   /**
    * Check server.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>" target="others">CORBA xmlBlaster.idl</a>
    */
   public String ping(String str) throws ConnectionException
   {
      try {
         Vector args = new Vector();
         args.addElement("");
         return (String)getXmlRpcClient().execute("xmlBlaster.ping", args);
      } catch(Exception e) {
         throw new ConnectionException(ME+".InvokeError", e.toString());
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
      if (this.xmlRpcClient == null) return "<noConnection />";
      try {
         Vector args = new Vector();
         args.addElement(extraOffset);
         return (String)this.xmlRpcClient.execute("xmlBlaster.toXml", args);
      }
      catch (ClassCastException e) {
         Log.error(ME+".toXml", "not a valid Vector: " + e.toString());
         throw new XmlBlasterException("Not a valid Vector", "Class Cast Exception");
      }
      catch (IOException e1) {
         Log.error(ME+".toXml", "IO exception: " + e1.toString());
         throw new XmlBlasterException("IO exception", e1.toString());
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(e);
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
      try { XmlBlasterProperty.init(args); } catch(org.jutils.JUtilsException e) { Log.panic(ME, e.toString()); }
      // build the proxy
      try {
         XmlRpcConnection proxy = new XmlRpcConnection("http://localhost:8080", 8081);

         String qos = "<qos><callback type='XML-RPC'>http://localhost:8081</callback></qos>";
         String sessionId = "Session1";

         String loginAnswer = proxy.login("LunaMia", "silence", qos, sessionId);
         Log.info(ME, "The answer from the login is: " + loginAnswer);

         String contentString = "This is a simple Test Message for the xml-rpc Protocol";
         byte[] content = contentString.getBytes();

         org.xmlBlaster.client.PublishKeyWrapper xmlKey = new org.xmlBlaster.client.PublishKeyWrapper("", "text/xml", null);

         MessageUnit msgUnit = new MessageUnit(xmlKey.toXml(), content, "<qos></qos>");
         String publishOid = proxy.publish(sessionId, msgUnit);
         Log.info(ME, "Published message with " + publishOid);

         org.xmlBlaster.client.SubscribeKeyWrapper subscribeKey = new org.xmlBlaster.client.SubscribeKeyWrapper(publishOid);

         Log.info(ME, "Subscribe key: " + subscribeKey.toXml());

         proxy.subscribe(sessionId, subscribeKey.toXml(), "");

         // wait some time if necessary ....
         proxy.erase(sessionId, subscribeKey.toXml(), "");

         Log.exit(ME, "Good bye.");

      } catch(XmlBlasterException e) {
         Log.error(ME, "XmlBlasterException: " + e.toString());
      }

      // wait for some time here ....
     */
   }


}

