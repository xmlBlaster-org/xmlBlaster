/*------------------------------------------------------------------------------
Name:      SocketConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native xmlBlaster Proxy. Can be called by the client in the same VM
Version:   $Id: SocketConnection.java,v 1.1 2002/02/14 15:01:15 ruff Exp $
Author:    michele.laghi@attglobal.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.socket;

import java.io.IOException;
import java.util.Vector;
import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;

import org.xmlBlaster.util.Log;
import org.jutils.text.StringHelper;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;
import org.xmlBlaster.client.protocol.ConnectionException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.util.protocol.ProtoConverter;
import org.xmlBlaster.client.protocol.I_CallbackExtended;

import org.xmlBlaster.protocol.socket.Parser;


/**
 * This is an xmlBlaster proxy. It implements the interface I_XmlBlaster
 * through AbstractCallbackExtended. The client can invoke it as if the
 * xmlBlaster would be on the same VM, making this way the plain socket protocol
 * totally transparent.
 * <p />
 * This driver establishes exactly one connection to xmlBlaster-Server and
 * uses this socket for asynchronous callbacks as well. This way we don't need
 * to setup a callbackserver.
 * <p />
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public class SocketConnection implements I_XmlBlasterConnection, I_ResponseListener
{
   private String ME = "SocketConnection";
   /** Default port of xmlBlaster socket server is 7607 */
   public static final int DEFAULT_SERVER_PORT = 7607;
   /** The port for the socket server */
   private int port = DEFAULT_SERVER_PORT;
   /** The port for our client side */
   private int localPort = -1;
   /** xmlBlaster server host */
   private String hostname = "localhost";
   /** our client side host */
   private String localHostname = "localhost";
   /** xmlBlaster server host */
   private java.net.InetAddress inetAddr = null;
   /** our client side host */
   private java.net.InetAddress localInetAddr = null;
   /** socket client used to send method calls. */
   private Socket sock = null;
   /** Listens on socket to receive callbacks */
   protected SocketCallbackImpl callback = null;
   /** The client code which wants the callback messages */
   protected I_CallbackExtended client = null;
   private String sessionId = null;
   protected String loginName = null;
   private String passwd = null;
   /** Reading from socket */
   private InputStream iStream = null;
   /** Writing to socket */
   private OutputStream oStream = null;
   protected ConnectQos loginQos = null;
   protected ConnectReturnQos returnQos = null;
   private long responseWaitTime = 0;

   /**
    * Connect to xmlBlaster using plain socket with native message format.
    */
   public SocketConnection(String[] args) throws XmlBlasterException
   {
      responseWaitTime = XmlBlasterProperty.get("socket.responseTimeout", Constants.MINUTE_IN_MILLIS);
   }

   /**
    * Connect to xmlBlaster using XML-RPC.
    */
   public SocketConnection(java.applet.Applet ap) throws XmlBlasterException
   {
   }

   /**
    * Connects to xmlBlaster with one socket connection. 
    */
   private void initSocketClient() throws XmlBlasterException
   {
      try {

         port = XmlBlasterProperty.get("socket.port", DEFAULT_SERVER_PORT);
         if (port < 1) {
            String str = "Option socket.port set to " + port + ", socket client not started";
            Log.info(ME, str);
            throw new XmlBlasterException(ME, str);
         }

         hostname = XmlBlasterProperty.get("socket.hostname", (String)null);
         if (hostname == null) {
            try  {
               java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
               hostname = addr.getHostName();
            } catch (Exception e) {
               Log.info(ME, "Can't determine your hostname");
               hostname = "localhost";
            }
         }
         try {
            inetAddr = java.net.InetAddress.getByName(hostname);
         } catch(java.net.UnknownHostException e) {
            throw new XmlBlasterException("InitSocketFailed", "The host [" + hostname + "] is invalid, try '-socket.hostname=<ip>': " + e.toString());
         }



         localPort = XmlBlasterProperty.get("socket.localPort", -1);
         localHostname = XmlBlasterProperty.get("socket.localHostname", (String)null);
         if (localHostname == null) {
            try  {
               java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
               localHostname = addr.getHostName();
            } catch (Exception e) {
               Log.info(ME, "Can't determine your localHostname");
               localHostname = "localhost";
            }
         }
         try {
            localInetAddr = java.net.InetAddress.getByName(localHostname);
         } catch(java.net.UnknownHostException e) {
            throw new XmlBlasterException("InitSocketFailed", "The host [" + localHostname + "] is invalid, try '-socket.localHostname=<ip>': " + e.toString());
         }

         //if (XmlBlasterProperty.get("socket.debug", false) == true)
         //   setDebug(true);

         if (localPort > -1) {
            this.sock = new Socket(inetAddr, port, localInetAddr, localPort);
            Log.info(ME, "Created socket client connected to " + hostname + " on port " + port);
            Log.info(ME, "Local parameters are " + localHostname + " on port " + localPort);
         }
         else {
            this.sock = new Socket(inetAddr, port);
            Log.info(ME, "Created socket client connected to " + hostname + " on port " + port);
         }
         oStream = this.sock.getOutputStream();
         iStream = this.sock.getInputStream();
      }
      catch (java.net.UnknownHostException e) {
         String str = "XmlBlaster server is unknown, '-socket.hostname=<ip>': " + e.toString();
         Log.error(ME+".constructor", str);
         throw new XmlBlasterException(ME, str);
      }
      catch (IOException e1) {
         Log.error(ME+".constructor", "IO Exception: " + e1.toString());
         throw new XmlBlasterException("IO Exception", e1.toString());
      }
      catch (Throwable e) {
         e.printStackTrace();
         Log.error(ME+".constructor", e.toString());
         throw new XmlBlasterException(ME, e.toString());
      }
   }


   /**
    * Reset the driver on problems
    */
   public void init()
   {
      Log.trace(ME, "SocketClient is re-initialized, no connection available");
      this.shutdown();
   }

   /**
    * Enforced by I_CallbackEvent
    */
   public void responseEvent(String requestId, String qos) {
      Log.info(ME, "RequestId=" + requestId + ": Return QoS value arrived ...");
   }
   /**
    * Enforced by I_CallbackEvent
    */
   public void responseEvent(String requestId, MessageUnit[] msgArr) {
      Log.info(ME, "RequestId=" + requestId + ": Return messages arrived ...");
   }
   /**
    * Enforced by I_CallbackEvent
    */
   public void responseEvent(String requestId, XmlBlasterException e) {
      Log.info(ME, "RequestId=" + requestId + ": XmlBlaster Exception arrived ...");
   }


   /**
    * A string with the local address and port (the client side). 
    * @return For example "localhost:66557"
    */
   public String getLocalAddress() {
      if (sock == null) {
         Log.error(ME, "Can't determine client address, no socket connection available");
         return null;
      }
      return "" + sock.getLocalAddress().getHostAddress() + ":" + sock.getLocalPort();
   }

   private Socket getSocket() throws ConnectionException
   {
      if (this.sock == null) {
         if (Log.TRACE) Log.trace(ME, "No socket connection available.");
         throw new ConnectionException(ME+".init", "No plain socket connection available.");
      }
      return this.sock;
   }


   /**
    * Does a login.
    * <p />
    * The callback is delivered like this in the qos argument:
    * <pre>
    *    &lt;qos>
    *       &lt;callback type='SOCKET'>
    *          http://localhost:8081
    *       &lt;/callback>
    *    &lt;/qos>
    * </pre>
    * @param loginName The login name for xmlBlaster
    * @param passwd    The login password for xmlBlaster
    * @param qos       The Quality of Service for this client (the callback tag will be added automatically if client!=null)
    * @param client    Your implementation of I_CallbackExtended, or null if you don't want any updates.
    * @exception       XmlBlasterException if login fails
    */
   public void login(String loginName, String passwd, ConnectQos qos, I_CallbackExtended client) throws XmlBlasterException, ConnectionException
   {
      this.ME = "SocketConnection-" + loginName;
      if (Log.CALL) Log.call(ME, "Entering login: name=" + loginName);
      if (isLoggedIn()) {
         Log.warn(ME, "You are already logged in, no relogin possible.");
         return;
      }

      this.loginName = loginName;
      this.passwd = passwd;
      this.client = client;
      if (qos == null)
         this.loginQos = new ConnectQos();
      else
         this.loginQos = qos;

      loginRaw();
   }


   public void connect(ConnectQos qos, I_CallbackExtended client) throws XmlBlasterException, ConnectionException
   {
      if (qos == null)
         throw new XmlBlasterException(ME+".connect()", "Please specify a valid QoS");

      this.ME = "SocketConnection-" + qos.getUserId();
      if (Log.CALL) Log.call(ME, "Entering login: name=" + qos.getUserId());
      if (isLoggedIn()) {
         Log.warn(ME, "You are already logged in, no relogin possible.");
         return;
      }

      this.loginQos = qos;
      this.loginName = qos.getUserId();
      this.passwd = null;
      this.client = client;

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
      try {
         initSocketClient();

         if (client != null) {
            // start the socket callback thread here (to receive callbacks)
            this.callback = new SocketCallbackImpl(this, iStream, client);
             // We set our IP:port just for information, it is not actively used by xmlBlaster:
            loginQos.addCallbackAddress(new CallbackAddress("SOCKET", getLocalAddress()));
         }

         if (passwd == null) { // connect() the new schema
            if (Log.TRACE) Log.trace(ME, "Executing authenticate.connect() via Socket with security plugin" + loginQos.toXml());
            Parser parser = new Parser();
            parser.setMethodName(Constants.CONNECT);
            parser.setType(Parser.INVOKE_TYPE);
            String requestId = parser.createRequestId(loginName);
            //parser.setSessionId("");
            parser.setChecksum(false);
            parser.setCompressed(false);
            parser.addQos(loginQos.toXml());
            byte[] rawMsg = parser.createRawMsg();
            callback.addResponseListener(requestId, this);
            oStream.write(rawMsg);
            oStream.flush();
            if (Log.TRACE) Log.trace(ME, "connect() send, waiting for response ...");
            try {
               Thread.currentThread().sleep(responseWaitTime);          // !!! How to detect timeout
               Log.warn(ME, "Waking up (waited on connect() response)");
            }
            catch (InterruptedException e) {
               Log.warn(ME, "Waking up (waited on connect() response): " + e.toString());
            }
         }
         else {
            throw new XmlBlasterException(ME, "login() is not supported, please use connect()");
         }
         if (Log.DUMP) Log.dump(ME, loginQos.toXml());
      }
      catch (ClassCastException e) {
         Log.error(ME+".login", "return value not a valid String: " + e.toString());
         throw new XmlBlasterException(ME+".LoginFailed", "return value not a valid String, Class Cast Exception: " + e.toString());
      }
      catch (IOException e) {
         Log.error(ME+".login", "IO exception: " + e.toString());
         throw new ConnectionException(ME+".LoginFailed", e.toString());
      }
      catch (Throwable e) {
         e.printStackTrace();
         Log.error(ME+".constructor", e.toString());
         throw new XmlBlasterException(ME, e.toString());
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
    * Does a logout and removes the callback server.
    * <p />
    * @param sessionId The client sessionId
    * @exception XmlBlasterException If sessionId is invalid
    */
   public boolean logout()
   {
      if (Log.CALL) Log.call(ME, "Entering logout: id=" + sessionId);

      if (!isLoggedIn()) {
         Log.warn(ME, "You are not logged in, no logout possible.");
      }

      Log.error(ME, "logout() is not implemented");
      /*
      try {
         if (this.sock != null) {
            if(passwd==null) {
               Vector args = new Vector();
               args.addElement(sessionId);
               args.addElement(" "); // qos
               this.sock.execute("authenticate.disconnect", args);
            }
            else {
               Vector args = new Vector();
               args.addElement(sessionId);
               this.sock.execute("authenticate.logout", args);
            }
         }
         shutdown(); // the callback server
         init();
         return true;
      }
      catch (IOException e1) {
         Log.warn(ME+".logout", "IO exception: " + e1.toString());
      }
      catch (SocketException e) {
         Log.warn(ME+".logout", "exception: " + extractXmlBlasterException(e).toString());
      }
      */
      shutdown(); // the callback server
      init();
      return false;
   }


   /**
    * Shut down the callback server.
    * Is called by logout()
    */
   public boolean shutdown()
   {
      if (this.callback != null) {
         this.callback.shutdown();
         this.callback = null;
      }
      try { if (iStream != null) { iStream.close(); iStream=null; } } catch (IOException e) { Log.warn(ME+".shutdown", e.toString()); }
      try { if (oStream != null) { oStream.close(); oStream=null; } } catch (IOException e) { Log.warn(ME+".shutdown", e.toString()); }
      try { if (sock != null) { sock.close(); sock=null; } } catch (IOException e) { Log.warn(ME+".shutdown", e.toString()); }
      return true;
   }


   /**
    * @return true if you are logged in
    */
   public boolean isLoggedIn()
   {
      return this.sock != null;
   }


   /**
    * Enforced by I_XmlBlasterConnection interface (fail save mode).
    * Subscribe to messages.
    * <p />
    */
   public final String subscribe (String xmlKey_literal, String qos_literal) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "Entering subscribe(id=" + sessionId + ")");
      throw new XmlBlasterException(ME, "subscribe() is not implemented");
      /*
      try {
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);
         return (String)getSocketClient().execute("xmlBlaster.subscribe", args);
      }
      catch (ClassCastException e) {
         Log.error(ME+".subscribe", "return value not a valid String: " + e.toString());
         throw new XmlBlasterException(ME+".subscribe", "return value not a valid String, Class Cast Exception");
      }
      catch (IOException e1) {
         Log.error(ME+".subscribe", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".subscribe", e1.toString());
      }
      catch (SocketException e) {
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
                                 String qos_literal) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "Entering unsubscribe(): id=" + sessionId);

      throw new XmlBlasterException(ME, "unSubscribe() is not implemented");
      /*
      try {
         // prepare the argument list:
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);

         getSocketClient().execute("xmlBlaster.unSubscribe", args);
      }
      catch (IOException e1) {
         Log.error(ME+".unSubscribe", "IO exception: " + e1.toString());
         throw new ConnectionException("IO exception", e1.toString());
      }
      catch (SocketException e) {
         throw extractXmlBlasterException(e);
      }
      */
   }



   /**
    * Publish a message.
    */
   public final String publish(MessageUnit msgUnit) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "Entering publish(): id=" + sessionId);

      throw new XmlBlasterException(ME, "publish() is not implemented");
      /*
      Parser parser = new Parser();
      parser.setType(Parser.INVOKE_TYPE);
      parser.setMethodName(Constants.PUBLISH);
      parser.setSessionId(sessionId);
      parser.setChecksum(false);
      parser.setCompressed(false);
      parser.addMessage(msgUnit);
      byte[] rawMsg = parser.createRawMsg();
      String send = toLiteral(rawMsg);
      System.out.println("Created and ready to send: \n|" + send + "|");

      try {
         return (String)getSocketClient().execute("xmlBlaster.publish", args);
      }
      catch (IOException e1) {
         Log.error(ME+".publish", "IO exception: " + e1.toString());
         throw new ConnectionException("IO exception", e1.toString());
      }
      catch (SocketException e) {
         throw extractXmlBlasterException(e);
      }
      */
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

      throw new XmlBlasterException(ME, "publishArr() is not implemented");
      /*
      try {

         Vector msgUnitArrWrap = ProtoConverter.messageUnitArray2Vector(msgUnitArr);
         // prepare the argument list (as a Vector)
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(msgUnitArrWrap);

         Vector returnVectorWrap = (Vector)getSocketClient().execute("xmlBlaster.publishArr", args);

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
      catch (SocketException e) {
         throw extractXmlBlasterException(e);
      }
      */
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

      throw new XmlBlasterException(ME, "erase() is not implemented");
      /*
      try {
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);

         Vector vec = (Vector)getSocketClient().execute("xmlBlaster.erase", args);
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

      catch (SocketException e) {
         throw extractXmlBlasterException(e);
      }
      */
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
      throw new XmlBlasterException(ME, "get() is not implemented");
      /*
      try {
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);

         Vector retVector = (Vector)getSocketClient().execute("xmlBlaster.get", args);
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
      catch (SocketException e) {
         throw extractXmlBlasterException(e);
      }
      */
   }


   /**
    * Check server.
    * @see xmlBlaster.idl
    */
   public void ping() throws ConnectionException
   {
      Log.error(ME, "ping() is not implemented");
      /*
      try {
         Vector args = new Vector();
         getSocketClient().execute("xmlBlaster.ping", args);
      } catch(Exception e) {
         throw new ConnectionException(ME+".InvokeError", e.toString());
      }
      */
   }


   /**
    * The update method.
    * <p />
    * Gets invoked from xmlBlaster callback
    */
   public String update(MessageUnit[] arr) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering update()");
      if (arr != null) {
         for (int ii=0; ii<arr.length; ii++) {
            client.update(getLoginName(), arr[ii].getXmlKey(), arr[ii].getContent(), arr[ii].getQos());
         }
      }
      return "<qos><state>OK</state></qos>";
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
      if (this.sock == null) return "<noConnection />";
      else return "<connected/>";
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
      text += "SocketConnection 'SOCKET' options:\n";
      text += "   -socket.port        Specify a port number where xmlBlaster SOCKET server listens.\n";
      text += "                       Default is port "+DEFAULT_SERVER_PORT+", the port 0 switches this feature off.\n";
      text += "   -socket.hostname    Specify a hostname where the xmlBlaster web server runs.\n";
      text += "                       Default is the localhost.\n";
      text += "   -socket.localPort   You can specify our client side port as well (usually you shouldn't)\n";
      text += "                       Default is that the port is choosen by the operating system.\n";
      text += "   -socket.localHostname  Specify the hostname who we are. Makes sense for multi homed computers.\n";
      text += "                       Defaults to our hostname.\n";
      text += "   -socket.responseTimeout  How long to wait for a method invocation to return.\n";
      text += "                       Defaults to one minute.\n";
      //text += "   -socket.debug       true switches on detailed SOCKET debugging [false].\n";
      text += "\n";
      return text;
   }


   /**
    * For Testing.
    * <pre>
    * java org.xmlBlaster.client.protocol.socket.SocketConnection
    * </pre>
    */
   public static void main(String args[])
   {
   /*
      final String ME = "SocketHttpClient";
      try { XmlBlasterProperty.init(args); } catch(org.jutils.JUtilsException e) { Log.panic(ME, e.toString()); }
      // build the proxy
      try {
         SocketConnection proxy = new SocketConnection("http://localhost:7607", 8081);

         String qos = "<qos><callback type='SOCKET'>http://localhost:8081</callback></qos>";
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

