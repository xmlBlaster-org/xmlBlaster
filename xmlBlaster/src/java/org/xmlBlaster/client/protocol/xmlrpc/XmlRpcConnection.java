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
import org.xmlBlaster.client.qos.ConnectReturnQos;
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
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.protocol.xmlrpc.XmlRpcUrl;

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
   private Global glob;
   private LogChannel log;
   private XmlRpcUrl xmlRpcUrl;
   private XmlRpcClient xmlRpcClient; // xml-rpc client to send method calls.
   private String sessionId;
   protected ConnectReturnQos connectReturnQos;
   protected Address clientAddress;

   /**
    * Called by plugin loader which calls init(Global, PluginInfo) thereafter. 
    */
   public XmlRpcConnection() {
   }

   /**
    * Connect to xmlBlaster using XMLRPC.
    */
   public XmlRpcConnection(Global glob) throws XmlBlasterException {
      init(glob, null);
   }

   /**
    * Connect to xmlBlaster using XMLRPC.
    */
   public XmlRpcConnection(Global glob, Applet ap) throws XmlBlasterException {
      init(glob, null);
    }

   /** Enforced by I_Plugin */
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
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) throws XmlBlasterException {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.log = this.glob.getLog("xmlrpc");
      log.info(ME, "Created '" + getProtocol() + "' protocol plugin to connect to xmlBlaster server");
   }

   /**
    * @return The connection protocol name "XMLRPC"
    */
   public final String getProtocol()
   {
      return "XMLRPC";
   }

   /**
    * @see I_XmlBlasterConnection#connectLowlevel(Address)
    */
   public void connectLowlevel(Address address) throws XmlBlasterException {
      if (this.xmlRpcClient != null) {
         return;
      }

      this.clientAddress = address;
      this.xmlRpcUrl = new XmlRpcUrl(glob, this.clientAddress);
      try {
         // dispatch/connection/plugin/xmlrpc/debug
         if (this.clientAddress.getEnv("debug", false).getValue() == true)
            XmlRpc.setDebug(true);

         this.xmlRpcClient = new XmlRpcClient(this.xmlRpcUrl.getUrl());
         log.info(ME, "Created XmlRpc client to " + this.xmlRpcUrl.getUrl());
      }
      catch (java.net.MalformedURLException e) {
         log.error(ME+".constructor", "Malformed URL: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, "Malformed URL for XmlRpc connection", e.toString());
      }
      catch (IOException e1) {
         log.error(ME+".constructor", "IO Exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "IO Exception", e1);
      }
   }

   public void resetConnection() {
      log.trace(ME, "XmlRpcCLient is initialized, no connection available");
      this.xmlRpcClient = null;
      this.sessionId = null;
   }

   private XmlRpcClient getXmlRpcClient() throws XmlBlasterException {
      if (this.xmlRpcClient == null) {
         if (log.TRACE) log.trace(ME, "No XMLRPC connection available.");
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                                       "The XMLRPC xmlBlaster handle is null, no connection available");
      }
      return this.xmlRpcClient;
   }

   /**
    * Login to the server. 
    * <p />
    * @param connectQos The encrypted connect QoS 
    * @exception XmlBlasterException if login fails
    */
   public String connect(String connectQos) throws XmlBlasterException {
      if (connectQos == null)
         throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME, "Please specify a valid ConnectQoS");

      if (log.CALL) log.call(ME, "Entering login");
      if (isLoggedIn()) {
         log.warn(ME, "You are already logged in, no relogin possible.");
         return "";
      }

      try {
         connectLowlevel(this.clientAddress);
         // prepare the argument vector for the xml-rpc method call

         String qosOrig = connectQos;
         String qosStripped = StringHelper.replaceAll(qosOrig, "<![CDATA[", "");
         connectQos = StringHelper.replaceAll(qosStripped, "]]>", "");
         if (!connectQos.equals(qosOrig)) {
            log.trace(ME, "Stripped CDATA tags surrounding security credentials, XMLRPC does not like it (Helma does not escape ']]>'). " +
                           "This shouldn't be a problem as long as your credentials doesn't contain '<'");
         }

         Vector args = new Vector();
         if (log.TRACE) log.trace(ME, "Executing authenticate.connect() via XmlRpc");
         args.addElement(connectQos);
         return (String)getXmlRpcClient().execute("authenticate.connect", args);
      }
      catch (ClassCastException e) {
         log.error(ME+".login", "return value not a valid String: " + e.toString());
         throw XmlBlasterException.convert(glob, ME, "return value not a valid String, Class Cast Exception", e);
      }
      catch (IOException e) {
         log.warn(ME+".login", "Login to xmlBlaster failed: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "Login failed", e);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e);
      }
   }

   /**
    * @see I_XmlBlasterConnection#setConnectReturnQos(ConnectReturnQos)
    */
   public void setConnectReturnQos(ConnectReturnQos connectReturnQos) {
      this.sessionId = connectReturnQos.getSecretSessionId();
      this.ME = "XmlRpcConnection-"+connectReturnQos.getSessionName().toString();
   }

   /**
    * Does a logout. 
    * <p />
    * @param sessionId The client sessionId
    */
   public boolean disconnect(String disconnectQos) {
      if (log.CALL) log.call(ME, "Entering logout");

      if (!isLoggedIn()) {
         log.warn(ME, "You are not logged in, no logout possible.");
      }

      try {
         if (this.xmlRpcClient != null) {
            // prepare the argument vector for the xml-rpc method call
            Vector args = new Vector();
            args.addElement(sessionId);
            args.addElement((disconnectQos==null)?" ":disconnectQos);
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
    * Enforced by I_XmlBlasterConnection interface (failsafe mode).
    * Subscribe to messages.
    * <p />
    */
   public final String subscribe (String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering subscribe(id=" + sessionId + ")");
      try {
         // prepare the argument vector for the xml-rpc method call
         Vector args = new Vector();
         args.addElement(this.sessionId);
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
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME,
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
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME,
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
         throw XmlBlasterException.convert(glob, ME, "publishOneway Class Cast Exception", e);
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
         throw XmlBlasterException.convert(glob, ME, "erase Class Cast Exception", e);
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
         throw XmlBlasterException.convert(glob, ME, "get Class Cast Exception", e);
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
         throw XmlBlasterException.convert(glob, ME, "toXml Class Cast Exception", e);
      }
      catch (IOException e) {
         log.error(ME+".toXml", "IO exception: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "toXml", e);
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
      text += "XmlRpcConnection 'XMLRPC' options:\n";
      text += "   -dispatch/connection/plugin/xmlrpc/port\n";
      text += "                       Specify a port number where xmlBlaster XMLRPC web server listens.\n";
      text += "                       Default is port "+org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver.DEFAULT_HTTP_PORT+", the port 0 switches this feature off.\n";
      text += "   -dispatch/connection/plugin/xmlrpc/hostname\n";
      text += "                       Specify a hostname where the xmlBlaster web server runs.\n";
      text += "                       Default is the localhost.\n";
      text += "   -dispatch/callback/plugin/xmlrpc/port\n";
      text += "                       Specify a port number for the callback web server to listen.\n";
      text += "                       Default is port "+XmlRpcCallbackServer.DEFAULT_CALLBACK_PORT+", the port 0 switches this feature off.\n";
      text += "   -dispatch/callback/plugin/xmlrpc/hostname\n";
      text += "                       Specify a hostname where the callback web server shall run.\n";
      text += "                       Default is the localhost (useful for multi homed hosts).\n";
      text += "   -plugin/xmlrpc/debug\n";
      text += "                       true switches on detailed XMLRPC debugging [false].\n";
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

         String qos = "<qos><callback type='XMLRPC'>http://localhost:8081</callback></qos>";
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

