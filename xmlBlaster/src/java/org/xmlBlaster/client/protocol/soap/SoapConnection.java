/*------------------------------------------------------------------------------
Name:      SoapConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.soap;

import java.io.IOException;
import java.net.URL;

import org.jutils.text.StringHelper;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.protocol.ProtoConverter;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;

import java.applet.Applet;

import org.jafw.saw.*;
import org.jafw.saw.rpc.*;
import org.jafw.saw.util.*;
import org.jafw.saw.transport.*;


/**
 * This is an xmlBlaster proxy. It implements the interface I_XmlBlasterConnection. 
 * The client can invoke it as if the
 * xmlBlaster would be on the same VM, making this way the soap protocol
 * totally transparent.
 * <p />
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class SoapConnection implements I_XmlBlasterConnection
{
   private String ME = "SoapConnection";
   public static final int DEFAULT_SERVER_PORT = 8686; // port of xmlBlaster SOAP server
   private Global glob;
   private LogChannel log;
   private String url = "http://localhost:" + DEFAULT_SERVER_PORT;
   private TransportConnection soapClient = null; // SOAP client to send method calls.
   private String sessionId = null;
   protected String loginName = null;
   private String passwd = null;
   protected ConnectQos connectQos = null;
   protected ConnectReturnQos connectReturnQos = null;
   /** See service.xml configuration */
   private final String authenticationService = "urn:AuthenticationService";
   /** See service.xml configuration */
   private final String xmlBlasterService = "urn:XmlBlasterService";

   /**
    * Called by plugin loader which calls init(Global, PluginInfo) thereafter. 
    */
   public SoapConnection() {
   }

   /**
    * Connect to xmlBlaster using SOAP.
    */
   public SoapConnection(Global glob) {
      init(glob, null);
   }

   /**
    * Connect to xmlBlaster using SOAP.
    */
   public SoapConnection(Global glob, Applet ap) {
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
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.log = this.glob.getLog("soap");
   }

   /**
    * @return The connection protocol name "SOAP"
    */
   public final String getProtocol() {
      return "SOAP";
   }

   private void initSoapClient() throws XmlBlasterException {
      String hostname = connectQos.getAddress().getHostname();
      hostname = glob.getProperty().get("soap.hostname", hostname);

      // default xmlBlaster SOAP publishing port is 8080
      int port = glob.getProperty().get("soap.port", DEFAULT_SERVER_PORT);
      this.url = "http://" + hostname + ":" + port;

      SAWHelper.initLogging();

      URL sawUrl;
      try {
         //This will only work if you are using an HTTP server defined in 'conf/config.xml'
         //hostname    -    the hostname of the computer running the MathService example that we created
         //            probably localhost.
         //port      -   the port that the server is running on
         sawUrl = new URL(this.url); // "http://develop:8686";
      } catch (Exception e) {
         log.error(ME, "Invalid URL '" + this.url + "', no callback possible");
         return;
      }

      try {
         this.soapClient = TransportConnectionManager.createTransportConnection(sawUrl);
         log.info(ME, "Created Soap client to " + url);
      } catch (SOAPException e) {
         log.error(ME, "FaultCode: " + e.getFaultCode() + " FaultString: " + e.getFaultString());
         throw new XmlBlasterException("SOAPException", e.toString());
      }
   }

   public void resetConnection() {
      log.trace(ME, "SoapCLient is initialized, no connection available");
      this.soapClient = null;
   }

   private TransportConnection getSoapClient() throws XmlBlasterException {
      if (this.soapClient == null) {
         if (log.TRACE) log.trace(ME, "No SOAP connection available.");
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                                       "The SOAP xmlBlaster handle is null, no connection available");
      }
      return this.soapClient;
   }

   /** @deprecated Use connect() */
   public void login(String loginName, String passwd, ConnectQos qos) throws XmlBlasterException {
      throw new XmlBlasterException(ME, "login is not supported any more, please use connect()");
   }

   /**
    * Login to the server.
    * @exception       XmlBlasterException if login fails
    */
   public ConnectReturnQos connect(ConnectQos qos) throws XmlBlasterException {
      if (qos == null)
         throw new XmlBlasterException(ME+".connect()", "Please specify a valid QoS");

      this.ME = "SoapConnection-" + qos.getUserId();
      if (log.CALL) log.call(ME, "Entering login: name=" + qos.getUserId());
      if (isLoggedIn()) {
         log.warn(ME, "You are already logged in, no relogin possible.");
         return this.connectReturnQos;
      }

      this.connectQos = qos;
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
   public ConnectReturnQos loginRaw() throws XmlBlasterException {
      try {
         initSoapClient();
         // prepare the argument vector for the method call

         String qosOrig = connectQos.toXml();
         String qosStripped = StringHelper.replaceAll(qosOrig, "<![CDATA[", "");
         qosStripped = StringHelper.replaceAll(qosStripped, "]]>", "");
         if (!qosStripped.equals(qosOrig)) {
            log.trace(ME, "Stripped CDATA tags surrounding security credentials, SOAP does not like it (Helma does not escape ']]>'). " +
                           "This shouldn't be a problem as long as your credentials doesn't contain '<'");
         }

         /*
         Vector args = new Vector();
         if (passwd == null) // The new schema
         {
            if (log.TRACE) log.trace(ME, "Executing authenticate.connect() via Soap with security plugin" + qosStripped);
            args.addElement(qosStripped);
            sessionId = null;
            String tmp = (String)getSoapClient().execute("authenticate.connect", args);
            this.connectReturnQos = new ConnectReturnQos(glob, tmp);
            this.sessionId = connectReturnQos.getSecretSessionId();
         }
         else
         {
            if (log.TRACE) log.trace(ME, "Executing authenticate.login() via Soap for loginName " + loginName);

            args.addElement(loginName);
            args.addElement(passwd);
            args.addElement(qosStripped);
            sessionId = ""; // Let xmlBlaster generate the sessionId
            args.addElement(sessionId);
            this.sessionId = (String)getSoapClient().execute("authenticate.login", args);
         }
         */
         if (log.DUMP) log.dump(ME, connectQos.toXml());
         return this.connectReturnQos;
      }
      catch (ClassCastException e) {
         log.error(ME+".login", "return value not a valid String: " + e.toString());
         throw new XmlBlasterException(ME+".LoginFailed", "return value not a valid String, Class Cast Exception: " + e.toString());
      }
      /*
      catch (IOException e) {
         log.error(ME+".login", "IO exception: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                                       "The SOAP login to xmlBlaster possible");
      }
      catch (SOAPException e) {
         throw extractXmlBlasterException(e);
      }
      */
   }

   /**
    * Access the login name.
    * @return your login name or null if you are not logged in
    */
   public String getLoginName() {
      return this.loginName;
   }

   /**
    * Does a logout. 
    * <p />
    * @param sessionId The client sessionId
    */
   public boolean disconnect(DisconnectQos qos) {
      if (log.CALL) log.call(ME, "Entering logout: id=" + sessionId);

      if (!isLoggedIn()) {
         log.warn(ME, "You are not logged in, no logout possible.");
      }

      log.error(ME, "disconnect not IMPLEMENTED");
      try {
         if (this.soapClient != null) {
            Call call = new Call();
            call.setService(authenticationService);
            call.setMethodName("disconnect");
            Parameter param1 = new Parameter("sessionId", String.class, sessionId);
            Parameter param2 = new Parameter("qos_literal", String.class, (qos==null)?" ":qos.toXml());
            call.setParamCount(2);
            call.setParam(0, param1);
            call.setParam(1, param2);
            try {
               Parameter returnParam = soapClient.invoke(call);
               if (returnParam != null)
                  log.error(ME, "I got a none-null response for disconnect(), something went wrong");
            } catch (SOAPException se) {
               log.error(ME, "Disconnect failed, faultCode: " + se.getFaultCode() + " faultString: " + se.getFaultString());
               return false;
            }
         }
      }
      finally {
         shutdown();
         resetConnection();
      }
      return true;
   }

   /**
    * Shut down. 
    * Is called by logout()
    */
   public void shutdown() throws XmlBlasterException {
      if (this.soapClient != null) {
         // TODO: 
         // ((SOAPHTTPConnection)this.soapClient).closeConnection();
         this.soapClient = null;
      }
   }

   /**
    * @return true if you are logged in
    */
   public boolean isLoggedIn() {
      return this.soapClient != null;
   }

   /**
    * Enforced by I_XmlBlasterConnection interface (failsafe mode).
    * Subscribe to messages.
    * <p />
    */
   public final String subscribe (String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering subscribe(id=" + sessionId + ")");
      log.error(ME, "NOT IMPLEMENTED");
      return "";
      /*
      try {
         // prepare the argument vector for the xml-rpc method call
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);
         return (String)getSoapClient().execute("xmlBlaster.subscribe", args);
      }
      catch (ClassCastException e) {
         log.error(ME+".subscribe", "return value not a valid String: " + e.toString());
         throw new XmlBlasterException(ME+".subscribe", "return value not a valid String, Class Cast Exception");
      }
      catch (IOException e1) {
         log.error(ME+".subscribe", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "subscribe", e1);
      }
      catch (SoapException e) {
         throw extractXmlBlasterException(e);
      }
      */
   }

   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final void unSubscribe (String xmlKey_literal,
                                 String qos_literal) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering unsubscribe(): id=" + sessionId);
      log.error(ME, "NOT IMPLEMENTED");
      /*
      try {
         // prepare the argument list:
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);

         getSoapClient().execute("xmlBlaster.unSubscribe", args);
      }
      catch (IOException e1) {
         log.error(ME+".unSubscribe", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "unSubscribe", e1);
      }
      catch (SoapException e) {
         throw extractXmlBlasterException(e);
      }
      */
   }

   /**
    * Publish a message.
    */
   public final String publish(MsgUnitRaw msgUnit) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering publish(): id=" + sessionId);
      log.error(ME, "NOT IMPLEMENTED");
      return "";
      /*
      //PublishQos publishQos = new PublishQos(msgUnit.getQos());
      //msgUnit.getQos() = publishQos.toXml();

      try {
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(msgUnit.getKey());
         args.addElement(msgUnit.getContent());
         args.addElement(msgUnit.getQos());

         return (String)getSoapClient().execute("xmlBlaster.publish", args);
      }

      catch (ClassCastException e) {
         log.error(ME+".publish", "not a valid MsgUnitRaw: " + e.toString());
         throw new XmlBlasterException("Not a valid Message Unit", "Class Cast Exception");
      }
      catch (IOException e1) {
         log.error(ME+".publish", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "publish", e1);
      }
      catch (SoapException e) {
         throw extractXmlBlasterException(e);
      }
      */
   }


   /**
    * Publish multiple messages in one sweep.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] publishArr(MsgUnitRaw[] msgUnitArr)
      throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering publishArr: id=" + sessionId);
      log.error(ME, "NOT IMPLEMENTED");
      return new String[0];
      /*
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

         Vector returnVectorWrap = (Vector)getSoapClient().execute("xmlBlaster.publishArr", args);

      // re-extractXmlBlasterException the resuts to String[]
         return ProtoConverter.vector2StringArray(returnVectorWrap);
      }

      catch (ClassCastException e) {
         log.error(ME+".publishArr", "not a valid String[]: " + e.toString());
         throw new XmlBlasterException("Not a valid String[]", "Class Cast Exception");
      }

      catch (IOException e1) {
         log.error(ME+".publishArr", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "publishArr", e);
      }
      catch (SoapException e) {
         throw extractXmlBlasterException(e);
      }
      */
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
      log.error(ME, "NOT IMPLEMENTED");
      /*
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
         getSoapClient().execute("xmlBlaster.publishOneway", args);
      }
      catch (ClassCastException e) {
         log.error(ME+".publishOneway", e.toString());
         e.printStackTrace();
         throw new XmlBlasterException(ME+".publishOneway", "Class Cast Exception");
      }
      catch (IOException e1) {
         log.error(ME+".publishOneway", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "publishOneway", e1);
      }
      catch (SoapException e) {
         throw extractXmlBlasterException(e);
      }
      */
   }

   /**
    * Delete messages.
    * <p />
    */
   public final String[] erase (XmlKey xmlKey, EraseQos eraseQoS)
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
      throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering erase() id=" + sessionId);
      log.error(ME, "NOT IMPLEMENTED");
      return new String[0];
      /*
      try {
         // prepare the argument list (as a Vector) for xml-rpc
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);

         Vector vec = (Vector)getSoapClient().execute("xmlBlaster.erase", args);
         return ProtoConverter.vector2StringArray(vec);

      }

      catch (ClassCastException e) {
         log.error(ME+".erase", "not a valid Vector: " + e.toString());
         throw new XmlBlasterException("Not a valid Vector", "Class Cast Exception");
      }

      catch (IOException e1) {
         log.error(ME+".erase", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "erase", e1);
      }

      catch (SoapException e) {
         throw extractXmlBlasterException(e);
      }
      */
   }

   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final MsgUnitRaw[] get (XmlKey xmlKey, GetQos getQoS)
      throws XmlBlasterException {
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
      log.error(ME, "NOT IMPLEMENTED");
      return new MsgUnitRaw[0];
      /*
      try {
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);

         Vector retVector = (Vector)getSoapClient().execute("xmlBlaster.get", args);
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
      catch (SoapException e) {
         throw extractXmlBlasterException(e);
      }
      */
   }


   /**
    * xml-rpc exception: org.apache.soap.SoapException: java.lang.Exception: id=RequestBroker.UnavailableKey reason=The key 'NotExistingMessage' is not available.
    */
   public static XmlBlasterException extractXmlBlasterException(SOAPException e) {
      String all = e.toString();
      String id = "Soap";
      String reason = all;
      /*
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
      */
      return new XmlBlasterException(id, reason);
   }

   /**
    * Check server.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String ping(String str) {
      Call call = new Call();
      call.setService(xmlBlasterService);
      call.setMethodName("ping");
      Parameter param1 = new Parameter("qos", String.class, str);
      call.setParamCount(1);
      call.setParam(0, param1);

      try {
         Parameter returnParam = soapClient.invoke(call);
         
         //Ensure we received a non null response, Note: if the call was invoking a 'void' method 
         //then the return will always be null, but there will be a SOAPException thrown if an error occurs
         if (returnParam == null) {
            log.error(ME, "I got a null response for ping(), something went wrong");
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "I got a null response for ping(), something went wrong");
         } else {
            Class returnType = returnParam.getType();
            log.info(ME, "Return had class type of: " + returnType.getName());
            Object returnValue = returnParam.getValue();
            log.info(ME, "Return was: " + returnValue.toString());
            return returnValue.toString();
         }
      } catch (SOAPException se) {
         log.error(ME, "Ping failed, faultCode: " + se.getFaultCode() + " faultString: " + se.getFaultString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "ping", se);
      }
   }

   public String toXml() throws XmlBlasterException {
      log.warn(ME, "toXml() is not implemented");
      return "";
      //return toXml("");
   }

   /**
    * Dump of the server, remove in future.
   public String toXml(String extraOffset) throws XmlBlasterException
   {
      if (this.soapClient == null) return "<noConnection />";
      try {
         Vector args = new Vector();
         args.addElement(extraOffset);
         return (String)this.soapClient.execute("xmlBlaster.toXml", args);
      }
      catch (ClassCastException e) {
         log.error(ME+".toXml", "not a valid Vector: " + e.toString());
         throw new XmlBlasterException("Not a valid Vector", "Class Cast Exception");
      }
      catch (IOException e1) {
         log.error(ME+".toXml", "IO exception: " + e1.toString());
         throw new XmlBlasterException("IO exception", e1.toString());
      }
      catch (SoapException e) {
         throw extractXmlBlasterException(e);
      }
   }
    */

   /**
    * Command line usage.
    * <p />
    * These variables may be set in xmlBlaster.properties as well.
    * Don't use the "-" prefix there.
    */
   public static String usage()
   {
      String text = "\n";
      text += "SoapConnection 'SOAP' options:\n";
      text += "   -soap.port        Specify a port number where xmlBlaster SOAP web server listens.\n";
      text += "                     Default is port "+DEFAULT_SERVER_PORT+", the port 0 switches this feature off.\n";
      text += "   -soap.hostname    Specify a hostname where the xmlBlaster web server runs.\n";
      text += "                     Default is the localhost.\n";
      text += "   -soap.portCB      Specify a port number for the callback web server to listen.\n";
      text += "                     Default is port "+SoapCallbackServer.DEFAULT_CALLBACK_PORT+", the port 0 switches this feature off.\n";
      text += "   -soap.hostnameCB  Specify a hostname where the callback web server shall run.\n";
      text += "                     Default is the localhost (useful for multi homed hosts).\n";
      text += "\n";
      return text;
   }

   /**
    * For Testing.
    * <pre>
    * java org.xmlBlaster.client.protocol.soap.SoapConnection
    * </pre>
    */
   public static void main(String args[]) {
   /*
      final String ME = "SoapHttpClient";
      try { XmlBlasterProperty.init(args); } catch(org.jutils.JUtilsException e) { log.panic(ME, e.toString()); }
      // build the proxy
      try {
         SoapConnection proxy = new SoapConnection("http://localhost:8080", 8081);

         String qos = "<qos><callback type='SOAP'>http://localhost:8081</callback></qos>";
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

