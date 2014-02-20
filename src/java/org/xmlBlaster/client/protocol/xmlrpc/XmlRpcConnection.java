/*------------------------------------------------------------------------------
Name:      XmlRpcConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.xmlrpc;

import java.util.Vector;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.util.protocol.ProtoConverter;
import org.xmlBlaster.util.protocol.xmlrpc.XmlRpcClientFactory;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.PluginInfo;

import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.protocol.xmlrpc.XmlRpcUrl;

import java.applet.Applet;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;


/**
 * This is an xmlBlaster proxy. It implements the interface I_XmlBlasterConnection. 
 * The client can invoke it as if the
 * xmlBlaster would be on the same VM, making this way the xml-rpc protocol
 * totally transparent.
 * <p />
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class XmlRpcConnection implements I_XmlBlasterConnection
{
   private String ME = "XmlRpcConnection";
   private Global glob;
   private static Logger log = Logger.getLogger(XmlRpcConnection.class.getName());
   private XmlRpcUrl xmlRpcUrl;
   private XmlRpcClient xmlRpcClient; // xml-rpc client to send method calls.
   private String sessionId;
   protected ConnectReturnQos connectReturnQos;
   protected Address clientAddress;
   protected PluginInfo pluginInfo;
   private String secretSessionId;
   private boolean contentAsString;
   private boolean xmlScript;
   private XmlScriptSerializer serializer;
   
   private final static String AUTH = "authenticate.";
   private final static String XMLBLASTER = "xmlBlaster.";
   public final static String XML_SCRIPT_INVOKE = "xmlScriptInvoke";
   /**
    * This flag is used since it may be necessary to throw away the connection if a proxy or a
    * gateway is somehow holding the session and after a failure constantly throwing exceptions.
    */
   private boolean forceNewConnectionOnReconnect = true;
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
   public void init(org.xmlBlaster.util.Global global, org.xmlBlaster.util.plugin.PluginInfo plInfo) throws XmlBlasterException {
      this.glob = (global == null) ? Global.instance() : global;

      this.pluginInfo = plInfo;
      
      log.info("Created '" + getProtocol() + "' protocol plugin to connect to xmlBlaster server");
      
   }

   
   public XmlRpcConnection getCopy() throws XmlBlasterException {
      if (false) {
         XmlRpcConnection conn = new XmlRpcConnection();
         conn.init(glob, pluginInfo);
         conn.connectLowlevel(clientAddress);
         conn.sessionId = sessionId;
         return conn;
      }
      return this;
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
      
      if (!forceNewConnectionOnReconnect) {
         if (this.xmlRpcClient != null) {
            return;
         }
      }
      glob.addObjectEntry("xmlrpc3-connection", this);

      this.clientAddress = address;
      contentAsString = clientAddress.getEnv("contentAsString", false).getValue();
      xmlScript = clientAddress.getEnv("xmlScript", false).getValue();
      if (xmlScript)
         serializer = new XmlScriptSerializer(glob, pluginInfo);
      
      if (this.pluginInfo != null)
         this.clientAddress.setPluginInfoParameters(this.pluginInfo.getParameters());
      this.xmlRpcUrl = new XmlRpcUrl(glob, this.clientAddress);
      try {
         // dispatch/connection/plugin/xmlrpc/debug
         if (this.clientAddress.getEnv("debug", false).getValue() == true) {
            // XmlRpc.setDebug(true);
            log.warning("debug has been set but it is not implemented");
         }
         xmlRpcClient = XmlRpcClientFactory.getXmlRpcClient(glob, xmlRpcUrl, address, false);
         log.info("Created XmlRpc client to " + this.xmlRpcUrl.getUrl());
      }
      catch (java.net.MalformedURLException e) {
         log.severe("Malformed URL: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, "Malformed URL for XmlRpc connection", e.toString());
      }
   }

   /**
    * This is used by the callback driver in case the xmlScript has been set to true (for update responses
    * and ca
    * @return
    */
   public XmlScriptSerializer getSerializer() {
      return serializer;
   }
   
   public void resetConnection() {
      log.fine("XmlRpcCLient is initialized, no connection available");
      this.xmlRpcClient = null;
      this.sessionId = null;
   }

   public XmlRpcClient getXmlRpcClient() throws XmlBlasterException {
      if (this.xmlRpcClient == null) {
         if (log.isLoggable(Level.FINE)) 
            log.fine("No XMLRPC connection available.");
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

      
      if (connectQos != null) {
         final String token = "sessionId=";
         int pos = connectQos.indexOf(token);
         if (pos > -1) {
            String tmp = connectQos.substring(pos + token.length()+1);
            pos = tmp.indexOf('\'');
            if (pos < 0)
               pos = tmp.indexOf('\"');
            if (pos > -1)
               secretSessionId = tmp.substring(0, pos);
         }
         if (secretSessionId == null)
            secretSessionId = "unknown";
         if (serializer != null)
            serializer.setSecretSessionId(secretSessionId);
      }
      
      if (log.isLoggable(Level.FINER)) log.finer("Entering login");
      if (isLoggedIn()) {
         log.warning("You are already logged in, we try again: " + toXml());
         //log.warn(ME, "You are already logged in, no relogin possible.");
         //return "";
      }

      try {
         connectLowlevel(this.clientAddress);
         // prepare the argument vector for the xml-rpc method call

         String qosOrig = connectQos;
         String qosStripped = org.xmlBlaster.util.ReplaceVariable.replaceAll(qosOrig, "<![CDATA[", "");
         connectQos = org.xmlBlaster.util.ReplaceVariable.replaceAll(qosStripped, "]]>", "");
         if (!connectQos.equals(qosOrig)) {
            log.fine("Stripped CDATA tags surrounding security credentials, XMLRPC does not like it (Helma does not escape ']]>'). " +
                           "This shouldn't be a problem as long as your credentials doesn't contain '<'");
         }
         boolean singleChannel = clientAddress.getEnv("singleChannel", false).getValue();
 
         if (serializer != null) {
            String literal = serializer.getConnect(connectQos);
            return (String)sendXmlScript(literal, AUTH, singleChannel);
         }
         else {
            Vector<String> args = new Vector<String>();
            if (log.isLoggable(Level.FINE)) log.fine("Executing authenticate.connect() via XmlRpc");
            args.addElement(sessionId);
            args.addElement(connectQos);
            if (singleChannel) {
               return (String)getXmlRpcClient().execute("authenticate.connectSingleChannel", args);
            }
            else
               return (String)getXmlRpcClient().execute("authenticate.connect", args);
         }
      }
      catch (ClassCastException e) {
         log.severe("return value not a valid String: " + e.toString());
         throw XmlBlasterException.convert(glob, ME, "return value not a valid String, Class Cast Exception", e);
      }
      /*
      catch (IOException e) {
         if (log.isLoggable(Level.FINE)) log.fine("Login to xmlBlaster failed: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "Login failed", e);
      }
      */
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e, ME+".connect");
      }
   }

   /**
    * @see I_XmlBlasterConnection#setConnectReturnQos(ConnectReturnQos)
    */
   public void setConnectReturnQos(ConnectReturnQos connectReturnQos) throws XmlBlasterException {
      sessionId = connectReturnQos.getSecretSessionId();
      if (serializer != null)
         serializer.setSecretSessionId(sessionId);
      XmlRpcCallbackServer cb = (XmlRpcCallbackServer)glob.getObjectEntry("xmlrpc-callback");
      if (cb != null)
         cb.postInitialize();
      
      this.ME = "XmlRpcConnection-"+connectReturnQos.getSessionName().toString();
   }

   /**
    * Does a logout. 
    * <p />
    * @param sessionId The client sessionId
    */
   public boolean disconnect(String disconnectQos) {
      if (log.isLoggable(Level.FINER)) log.finer("Entering logout");

      if (!isLoggedIn()) {
         log.warning("You are not logged in, no logout possible.");
      }

      try {
         // shutdown the callback if any
         XmlRpcCallbackServer cb = (XmlRpcCallbackServer)glob.getObjectEntry("xmlrpc-callback");
         if (cb != null)
            cb.shutdown();
         
         if (this.xmlRpcClient != null) {
            if (serializer != null) {
               try {
                  String literal = serializer.getDisconnect(disconnectQos);
                  final boolean singleChannel = false; // since not a connect!
                  sendXmlScript(literal, AUTH, singleChannel);
                  return true;
               }
               catch (XmlBlasterException ex) {
                  log.severe("Exception occured when shutting down: " + ex.getMessage());
                  ex.printStackTrace();
                  
               }
            }
            else {
               // prepare the argument vector for the xml-rpc method call
               Vector<String> args = new Vector<String>();
               args.addElement(sessionId);
               args.addElement((disconnectQos==null)?" ":disconnectQos);
               this.xmlRpcClient.execute("authenticate.disconnect", args);
            }
         }

         try {
            shutdown();
         }
         catch (XmlBlasterException ex) {
            log.severe("disconnect() could not shutdown properly. " + ex.getMessage());
         }
         resetConnection();
         return true;
      }
      catch (XmlRpcException e) {
         log.warning("xml-rpc exception: " + extractXmlBlasterException(glob, e, ME+"setConnectReturn").toString());
      }

      try {
         shutdown();
      }
      catch (XmlBlasterException ex) {
         log.severe("disconnect() could not shutdown properly. " + ex.getMessage());
      }
      resetConnection();
      return false;
   }


   /**
    * Shut down. 
    * Is called by logout()
    */
   public void shutdown() throws XmlBlasterException {
      glob.removeObjectEntry("xmlrpc3-connection");
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
   public final String subscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering subscribe(id=" + sessionId + ")");
      try {
         if (serializer != null) {
            String literal = serializer.getSubscribe(xmlKey_literal, qos_literal);
            final boolean singleChannel = false; // since no connect
            return (String)sendXmlScript(literal, XMLBLASTER, singleChannel);
         }
         // prepare the argument vector for the xml-rpc method call
         Vector<String> args = new Vector<String>();
         args.addElement(this.sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);
         return (String)getXmlRpcClient().execute("xmlBlaster.subscribe", args);
      }
      catch (ClassCastException e) {
         log.severe("return value not a valid String: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME+".subscribe", "return value not a valid String, Class Cast Exception", e);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e, ME+".subscribe");
      }
   }


   /**
    * Unsubscribe from messages.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html">The interface.unSubscribe requirement</a>
    */
   public final String[] unSubscribe (String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering unsubscribe(): id=" + sessionId);

      try {
         Object obj = null;
         if (serializer != null) {
            String literal = serializer.getUnSubscribe(xmlKey_literal, qos_literal);
            final boolean singleChannel = false; // since no connect
            obj = sendXmlScript(literal, XMLBLASTER, singleChannel);
         }
         else {
            // prepare the argument list:
            Vector<String> args = new Vector<String>();
            args.addElement(sessionId);
            args.addElement(xmlKey_literal);
            args.addElement(qos_literal);

            obj = getXmlRpcClient().execute("xmlBlaster.unSubscribe", args);
         }
         return ProtoConverter.objToStringArray(obj);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e, ME+".unSubscribe");
      }
   }



   /**
    * Publish a message.
    */
   public final String publish(MsgUnitRaw msgUnit) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering publish(): id=" + sessionId);

      //PublishQos publishQos = new PublishQos(msgUnit.getQos());
      //msgUnit.getQos() = publishQos.toXml();

      try {
         if (serializer != null) {
            String literal = serializer.getPublish(msgUnit);
            final boolean singleChannel = false; // since no connect
            return (String)sendXmlScript(literal, XMLBLASTER, singleChannel);
         }
         else {
            Vector<Object> args = new Vector<Object>();
            args.addElement(sessionId);
            args.addElement(msgUnit.getKey());
            if (contentAsString)
               args.addElement(msgUnit.getContentStr());
            else
               args.addElement(msgUnit.getContent());
            args.addElement(msgUnit.getQos());
            return (String)getXmlRpcClient().execute("xmlBlaster.publish", args);
         }
      }

      catch (ClassCastException e) {
         log.severe("not a valid MsgUnitRaw: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME+".publish", "Not a valid MsgUnitRaw", e);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e, ME+".publish");
      }
   }


   /**
    * Publish multiple messages in one sweep.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public final String[] publishArr(MsgUnitRaw[] msgUnitArr) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering publishArr: id=" + sessionId);

      if (msgUnitArr == null) {
         log.severe("The argument of method publishArr() are invalid");
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME,
                                       "The argument of method publishArr() are invalid");
      }

      Object tmpObj = null;
      try {
         if (serializer != null) {
            String literal = serializer.getPublishArr(msgUnitArr);
            final boolean singleChannel = false; // since no connect
            tmpObj = sendXmlScript(literal, XMLBLASTER, singleChannel);
         }
         else {
            Vector<Object> msgUnitArrWrap = ProtoConverter.messageUnitArray2Vector(contentAsString, msgUnitArr);
            // prepare the argument list (as a Vector)
            Vector<Object> args = new Vector<Object>();
            args.addElement(sessionId);
            args.addElement(msgUnitArrWrap);

            tmpObj = getXmlRpcClient().execute("xmlBlaster.publishArr", args);
         }
         return ProtoConverter.objToStringArray(tmpObj);
      }

      catch (ClassCastException e) {
         log.severe("not a valid String[]: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME+".publishArr", "Not a valid String[]", e);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e, ME+".publishArr");
      }
   }


   /**
    * Publish multiple messages in one sweep.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public final void publishOneway(MsgUnitRaw[] msgUnitArr)
      throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering publishOneway: id=" + sessionId);

      if (msgUnitArr == null) {
         log.severe("The argument of method publishOneway() are invalid");
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME,
                                       "The argument of method publishOneway() are invalid");
      }

      try {
         if (serializer != null) {
            String literal = serializer.getPublishOneway(msgUnitArr);
            final boolean singleChannel = false; // since no connect
            sendXmlScript(literal, XMLBLASTER, singleChannel);
         }
         else {
            Vector<Object> msgUnitArrWrap = ProtoConverter.messageUnitArray2Vector(contentAsString, msgUnitArr);
            Vector<Object> args = new Vector<Object>();
            args.addElement(sessionId);
            args.addElement(msgUnitArrWrap);
            getXmlRpcClient().execute("xmlBlaster.publishOneway", args);
         }
      }
      catch (ClassCastException e) {
         log.severe(e.toString());
         e.printStackTrace();
         throw XmlBlasterException.convert(glob, ME, "publishOneway Class Cast Exception", e);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e, ME+".publishOneway");
      }
   }


   /*
    * Delete messages.
    * <p />
   public final String[] erase (XmlKey xmlKey, EraseQosServer eraseQoS)
      throws XmlBlasterException
   {
      String xmlKey_literal = xmlKey.toXml();
      String eraseQoS_literal = eraseQoS.toXml();

      return erase(xmlKey_literal, eraseQoS_literal);
   }
    */



   /**
    * Delete messages.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.erase.html">The interface.erase requirement</a>
    */
   public final String[] erase(String xmlKey_literal, String qos_literal)
      throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering erase() id=" + sessionId);

      try {
         // prepare the argument list (as a Vector) for xml-rpc
         Object obj = null;
         if (serializer != null) {
            String literal = serializer.getErase(xmlKey_literal, qos_literal);
            final boolean singleChannel = false; // since no connect
            obj = sendXmlScript(literal, XMLBLASTER, singleChannel);
         }
         else {
            Vector<String> args = new Vector<String>();
            args.addElement(sessionId);
            args.addElement(xmlKey_literal);
            args.addElement(qos_literal);
            obj = getXmlRpcClient().execute("xmlBlaster.erase", args);
         }
         return ProtoConverter.objToStringArray(obj);
      }

      catch (ClassCastException e) {
         log.severe("not a valid Vector: " + e.toString());
         throw XmlBlasterException.convert(glob, ME, "erase Class Cast Exception", e);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e, ME+".erase");
      }
   }


   /*
    * Synchronous access a message.
    * <p />
   public final MsgUnitRaw[] get (XmlKey xmlKey, GetQosServer getQoS)
      throws XmlBlasterException
   {
      String xmlKey_literal = xmlKey.toXml();
      String getQoS_literal = getQoS.toXml();

      return get(xmlKey_literal, getQoS_literal);
   }
    */


   /**
    * Synchronous access a message.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html">The interface.get requirement</a>
    */
   public final MsgUnitRaw[] get(String xmlKey_literal,
                                  String qos_literal) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering get() xmlKey=\n" + xmlKey_literal + ") ...");
      try {
         Object[] tmpObj = null;
         if (serializer != null) {
            String literal = serializer.getGet(xmlKey_literal, qos_literal);
            final boolean singleChannel = false; // since no connect
            tmpObj = (Object[])sendXmlScript(literal, XMLBLASTER, singleChannel);
         }
         else {
            Vector<Object> args = new Vector<Object>();
            args.addElement(sessionId);
            args.addElement(xmlKey_literal);
            args.addElement(qos_literal);
            args.addElement("" + contentAsString);
            // Vector retVector = (Vector)getXmlRpcClient().execute("xmlBlaster.get", args);
            tmpObj = (Object[])getXmlRpcClient().execute("xmlBlaster.get", args);
         }
         
         // extractXmlBlasterException the vector of vectors to a MsgUnitRaw[] type
         return ProtoConverter.objMatrix2MsgUnitRawArray(tmpObj);
      }
      catch (ClassCastException e) {
         log.severe("not a valid Vector: " + e.toString());
         throw XmlBlasterException.convert(glob, ME, "get Class Cast Exception", e);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e, ME+".get");
      }
   }

   public static XmlBlasterException extractXmlBlasterException(Global glob, XmlRpcException e, String txt) {
      // return extractXmlBlasterException(glob, e, ErrorCode.INTERNAL_UNKNOWN, txt);
      StringBuffer buf = new StringBuffer();
      buf.append("XmlRpcException: \n");
      buf.append("code='").append(e.code).append("'");
      if (e.linkedException != null)
         buf.append("linked exception: '").append(e.linkedException).append("'\n");
      if (e.getCause() != null)
         buf.append("cause (ex): '").append(e.getCause()).append("'\n");
      buf.append("localizedMessage: '").append(e.getLocalizedMessage()).append("'");
      buf.append("message: '").append(e.getMessage()).append("'");
      buf.append("toString: '").append(e.toString()).append("'");
      log.severe(buf.toString());
      ErrorCode errCode = ErrorCode.COMMUNICATION_NOCONNECTION;
      int code = e.code;
      if (code >= 400 && code < 500 && code != 408)
         errCode = ErrorCode.USER_MESSAGE_INVALID;
      return extractXmlBlasterException(glob, e, errCode, txt);
   }
   
   /**
    * Helma XmlRpc does in XmlRpcServer.java:314 an exception.toString() which is sent back to the client. 
    * <br />
    * xml-rpc exception: org.apache.xmlrpc.XmlRpcException: java.lang.Exception: errorCode=resource.unavailable message=The key 'NotExistingMessage' is not available.
    * @param glob
    * @param e The original exception
    * @param fallback The error code to use if e is unparsable
    */
   public static XmlBlasterException extractXmlBlasterException(Global glob, XmlRpcException e, ErrorCode fallback, String txt) {
      XmlBlasterException ex = null;
      if (e.linkedException != null && e.linkedException instanceof XmlBlasterException) {
         // since the xmlBlaster Exception here is an empty one (no global and stuff) we fill it again
         XmlBlasterException emptyEx = (XmlBlasterException)e.linkedException;
         ErrorCode code = ErrorCode.toErrorCode(emptyEx.getErrorCodeStr());
         
         Throwable cause = e.getCause();
         if (cause != null) {
            if (cause instanceof XmlBlasterException) {
               if (cause == emptyEx) {
                  log.fine("cause and linked exception are the same object");
               }
            }
         }
         try {
            ex = new XmlBlasterException(glob, code, txt, "", e);
         }
         catch (Throwable th) {
            ex = new XmlBlasterException(glob, code, e.getMessage());
         }
      }
      else
         // ex = XmlBlasterException.parseToString(glob, e.toString(), fallback);
         if (fallback == null)
            fallback = ErrorCode.COMMUNICATION_NOCONNECTION;
         ex = new XmlBlasterException(glob, fallback, txt, "", e);

      ex.isServerSide(true);
      return ex;
   }


   /**
    * Check server.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String ping(String str) throws XmlBlasterException {
      try {
         if (serializer != null) {
            String literal = serializer.getPing(str);
            final boolean singleChannel = false; // since no connect
            return (String)sendXmlScript(literal, AUTH, singleChannel);
         }
         else {
            Vector<String> args = new Vector<String>();
            args.addElement("");
            return (String)getXmlRpcClient().execute("xmlBlaster.ping", args);
         }
      }
      catch (ClassCastException e) {
         log.severe(e.toString());
         e.printStackTrace();
         throw XmlBlasterException.convert(glob, ME, "ping Class Cast Exception", e);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e, ME+".ping");
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
         Vector<String> args = new Vector<String>();
         args.addElement(extraOffset);
         return (String)this.xmlRpcClient.execute("xmlBlaster.toXml", args);
      }
      catch (ClassCastException e) {
         log.severe("not a valid Vector: " + e.toString());
         throw XmlBlasterException.convert(glob, ME, "toXml Class Cast Exception", e);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e, ME+".toXml");
      }
   }

   
   
   /**
    * Publish multiple messages in one sweep.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public final void getUpdates(I_CallbackExtended cb) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) 
         log.finer("Entering getUpdates: id=" + sessionId);
      // public Object[] updateRequest(String sessionId, Long waitTime) throws XmlBlasterException {
      try {
         // prepare the argument list (as a Vector)
         Vector<Object> args = new Vector<Object>();
         args.addElement(sessionId);

         long delay = clientAddress.getEnv("updateTimeout", 30000L).getValue();
         String waitDelay = "" + delay;
         args.addElement(waitDelay);
         args.addElement("" + contentAsString);
         Object[] retObj = (Object[])getXmlRpcClient().execute("xmlBlaster.updateRequest", args);
         if (retObj == null)
            return;
         
         String methodName = (String)retObj[0];
         Object[] vec = (Object[])retObj[2];
         
         MsgUnitRaw[] msgUnitArr = ProtoConverter.objMatrix2MsgUnitRawArray(vec);
         
         if (methodName.equals("update")) {
            String uniqueId = (String)retObj[1];
            try {
               String[] ret = cb.update(secretSessionId, msgUnitArr);
               sendAckOrEx(uniqueId, ret, null);
            }
            catch (Throwable ex) {
               sendAckOrEx(uniqueId, null, ex.getMessage());
            }
            // we now must send them back to the server ...
         }
         
         else if (methodName.equals("updateOneway")) {
            cb.update("unknown", msgUnitArr);
         }
         else {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME+".getUpdates", "request not valid " + methodName);
         }
      }
      catch (ClassCastException e) {
         log.severe("not a valid String[]: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME+".publishArr", "Not a valid String[]", e);
      }
      catch (XmlRpcException e) {
         throw extractXmlBlasterException(glob, e, ME+".getUpdates");
      }
   }


   public void sendAckOrEx(String uniqueId, String[] acks, String exTxt) {
      // String updateAckOrException(String sessionId, String reqId, String[] ack, String ex)
      try {
         Vector<Object> args = new Vector<Object>();
         args.addElement(sessionId);
         args.addElement(uniqueId);
         Vector<String> ackVec = new Vector<String>();
         if (acks != null) {
            for (int i=0; i < acks.length; i++) {
               ackVec.addElement(acks[i]);
            }
         }
         args.addElement(ackVec);
         args.addElement(exTxt);
         getXmlRpcClient().execute("xmlBlaster.updateAckOrException", args);
      }
      catch (Throwable e) {
         e.printStackTrace();
      }
   }

   
   public void sendShutdownCb() {
      // public String shutdownCb(String sessionId);
      try {
         Vector<Object> args = new Vector<Object>();
         args.addElement(sessionId);
         getXmlRpcClient().execute("xmlBlaster.shutdownCb", args);
      }
      catch (Throwable e) {
         // e.printStackTrace();
         log.warning("Could not reach server anymore to notify him to shutdown the callback server");
      }
   }

   
   /**
    * Register a listener for to receive information about the progress of incoming data. 
    * Only one listener is supported, the last call overwrites older calls. This implementation
    * does nothing here, it just returns null.
    * 
    * @param listener Your listener, pass 0 to unregister.
    * @return The previously registered listener or 0
    */
   public I_ProgressListener registerProgressListener(I_ProgressListener listener) {
      log.fine("This method is currently not implemeented.");
      return null;
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
      text += "                       Specify a hostname where the XMLRPC web server runs.\n";
      text += "                       Default is the localhost. If you specify the protocol then port and ssl info are taken from here\n";
      text += "                       for example https://localhost:8443/somePath would ignore port, SSL, path\n";
      text += "   -dispatch/callback/plugin/xmlrpc/port\n";
      text += "                       Specify a port number for the callback web server to listen.\n";
      text += "                       Default is port "+XmlRpcCallbackServer.DEFAULT_CALLBACK_PORT+", the port 0 switches this feature off. Only relevant if singleChannel=false.\n";
      text += "   -dispatch/callback/plugin/xmlrpc/hostname\n";
      text += "                       Specify a hostname where the callback web server shall run.\n";
      text += "                       Default is the localhost (useful for multi homed hosts).\n";
      // text += "   -plugin/xmlrpc/debug\n";
      // text += "                       true switches on detailed XMLRPC debugging [false].\n";
      
      text += "   -plugin/xmlrpc/singleChannel\n";
      text += "                       true tunnels back the updates on the same channel, which allows to pass firewalls. If set to \n";
      text += "                         true makes callback properties to be ignored here. [false].\n";

      text += "   -dispatch/connection/plugin/xmlrpc/SoTimeout\n";
      text += "                       How long may a socket read block in msec.\n";
      text += "   -dispatch/connection/plugin/xmlrpc/responseTimeout\n";
      text += "                       Max wait for the method return value/exception in msec.\n";
//      text += "                       The default is " +getDefaultResponseTimeout() + ".\n";
      text += "                       Defaults to 'forever', the value to pass is milli seconds.\n";
      // text += "   -"+getEnvPrefix()+"backlog\n";
      // text += "                       Queue size for incoming connection request [50].\n";
      text += "   -dispatch/connection/plugin/xmlrpc/SSL\n";
      text += "                       True enables SSL support on socket [false] (if no protocol specified in hostname).\n";
      text += "   -dispatch/connection/plugin/xmlrpc/keyStore\n";
      text += "                       The path of your keystore file. Use the java utility keytool.\n";
      text += "   -dispatch/connection/plugin/xmlrpc/keyStorePassword\n";
      text += "                       The password of your keystore file.\n";
      
      text += "   -dispatch/connection/plugin/xmlrpc/compress/type\n";
      text += "                       Valid values are: '', '"+Constants.COMPRESS_ZLIB_STREAM+"', '"+Constants.COMPRESS_ZLIB+"' [].\n";
      text += "                       '' disables compression, '"+Constants.COMPRESS_ZLIB_STREAM+"' compresses whole stream.\n";
      text += "                       '"+Constants.COMPRESS_ZLIB+"' both compress the same (both are provided as compatibility to the socket protocol).\n";
      
      text += "\n";
      return text;
   }

   private final Object sendXmlScript(String literal, String prefix, boolean singleChannel) throws XmlRpcException, XmlBlasterException {
      Vector<String> args = new Vector<String>();
      args.addElement(literal);
      String method = prefix + XML_SCRIPT_INVOKE;
      if (singleChannel)
         method += "SingleChannel";
      if (log.isLoggable(Level.FINE)) 
         log.fine("Executing " + method + " with value : " + literal);
      
      return getXmlRpcClient().execute(method, args);
      
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
      try { Global.init(args); } catch(XmlBlasterException e) { log.severe(e.toString()); }
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

   public String getSessionId() {
	   return sessionId;
   }
   
}

