/*------------------------------------------------------------------------------
Name:      SocketConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles connection to xmlBlaster with plain sockets
Version:   $Id: SocketConnection.java,v 1.41 2003/01/18 16:57:23 ruff Exp $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.socket;

import java.io.IOException;
import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.enum.MethodName;

import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.qos.GetQosServer;
import org.xmlBlaster.engine.qos.EraseQosServer;
import org.xmlBlaster.protocol.socket.ExecutorBase;
import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.client.protocol.I_CallbackExtended;

import org.xmlBlaster.protocol.socket.Parser;


/**
 * This driver establishes exactly one connection to xmlBlaster-Server and
 * uses this socket for asynchronous callbacks as well. This way we don't need
 * to setup a callbackserver.
 * <p />
 * This "SOCKET:" driver needs to be registered in xmlBlaster.properties
 * and will be started on xmlBlaster startup:
 * <pre>
 * ProtocolPlugin[SOCKET][1.0]=org.xmlBlaster.protocol.socket.SocketDriver
 * CbProtocolPlugin[SOCKET][1.0]=org.xmlBlaster.protocol.socket.CallbackSocketDriver
 * </pre>
 * <p />
 * All adjustable parameters are explained in {@link org.xmlBlaster.client.protocol.socket.SocketConnection#usage()}
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">The protocol.socket requirement</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class SocketConnection implements I_XmlBlasterConnection, ExecutorBase
{
   private String ME = "SocketConnection";
   private final Global glob;
   private final LogChannel log;
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
   /** The socket connection to/from one client */
   protected Socket sock;
   /** Reading from socket */
   protected InputStream iStream;
   /** Writing to socket */
   protected OutputStream oStream;
   /** SocketCallbackImpl listens on socket to receive callbacks */
   protected SocketCallbackImpl cbReceiver = null;
   private String passwd = null;
   protected ConnectQos loginQos = null;
   protected ConnectReturnQos connectReturnQos = null;
   /** The unique client sessionId */
   protected String sessionId = null;
   /** The client login name */
   protected String loginName = "";
   private I_CallbackExtended cbClient = null;

   /**
    * Connect to xmlBlaster using plain socket with native message format.
    */
   public SocketConnection(Global glob) throws XmlBlasterException
   {
      this(glob, null);
   }

   /**
    * Connect to xmlBlaster using plain socket messaging.
    */
   public SocketConnection(Global glob, java.applet.Applet ap) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = glob.getLog("socket");
   }

   /**
    * Get the raw socket handle
    */
   public Socket getSocket() throws XmlBlasterException
   {
      if (this.sock == null) {
         if (log.TRACE) log.trace(ME, "No socket connection available.");
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                                       "No plain SOCKET connection available.");
      }
      return this.sock;
   }

   final Global getGlobal() {
      return this.glob;
   }

   /**
    * Connects to xmlBlaster with one socket connection. 
    */
   private void initSocketClient() throws XmlBlasterException
   {
      if (isConnected())
         return;

      try {
         port = glob.getProperty().get("socket.port", DEFAULT_SERVER_PORT);
         if (port < 1) {
            String str = "Option socket.port set to " + port + ", socket client not started";
            log.info(ME, str);
            throw new XmlBlasterException(ME, str);
         }

         hostname = glob.getProperty().get("socket.hostname", (String)null);
         if (hostname == null) {
            try  {
               java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
               hostname = addr.getHostName();
            } catch (Exception e) {
               log.info(ME, "Can't determine your hostname");
               hostname = "localhost";
            }
         }
         try {
            inetAddr = java.net.InetAddress.getByName(hostname);
         } catch(java.net.UnknownHostException e) {
            throw new XmlBlasterException("InitSocketFailed", "The host [" + hostname + "] is invalid, try '-socket.hostname <ip>': " + e.toString());
         }


         localPort = glob.getProperty().get("socket.localPort", -1);
         localHostname = glob.getProperty().get("socket.localHostname", (String)null);
         if (localHostname == null) {
            try  {
               java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
               localHostname = addr.getHostName();
            } catch (Exception e) {
               log.info(ME, "Can't determine your localHostname");
               localHostname = "localhost";
            }
         }
         try {
            localInetAddr = java.net.InetAddress.getByName(localHostname);
         } catch(java.net.UnknownHostException e) {
            throw new XmlBlasterException("InitSocketFailed", "The host [" + localHostname + "] is invalid, try '-socket.localHostname <ip>': " + e.toString());
         }

         if (localPort > -1) {
            this.sock = new Socket(inetAddr, port, localInetAddr, localPort);
            log.info(ME, "Created socket client connected to " + hostname + " on port " + port);
            log.info(ME, "Local parameters are " + localHostname + " on port " + localPort);
         }
         else {
            if (log.TRACE) log.trace(ME, "Trying socket connection to " + hostname + " on port " + port + " ...");
            this.sock = new Socket(inetAddr, port);
            this.localPort = sock.getLocalPort();
            this.localHostname = sock.getLocalAddress().getHostAddress();
            log.info(ME, "Created socket client connected to " + hostname + " on port " + port + ", callback address is " + getLocalAddress());
         }
         oStream = this.sock.getOutputStream();
         iStream = this.sock.getInputStream();

         // start the socket sender and callback thread here
         if (this.cbReceiver == null) { // only the first time, not on reconnect
            this.cbReceiver = new SocketCallbackImpl(this);
            this.cbReceiver.initialize(glob, loginName, this.cbClient);
         }
      }
      catch (java.net.UnknownHostException e) {
         String str = "XmlBlaster server is unknown, '-socket.hostname=<ip>': " + e.toString();
         if (log.TRACE) log.trace(ME+".constructor", str);
         //e.printStackTrace(); 
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, 
                                       "XmlBlaster server is unknown, '-socket.hostname=<ip>'", e);
      }
      catch (Throwable e) {
         if (!(e instanceof IOException) && !(e instanceof java.net.ConnectException)) e.printStackTrace();
         String str = "Socket client connection to " + hostname + " on port " + port + " failed, try options '-socket.hostname <ip> -socket.port <port>' and check if the xmlBlaster server has loaded the socket driver in xmlBlaster.properties";
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, str, e);
      }
   }


   /**
    * Reset the driver on problems
    */
   public void resetConnection()
   {
      if (log.TRACE) log.trace(ME, "SocketClient is re-initialized, no connection available");
      this.shutdown();
   }


   /**
    * A string with the local address and port (the client side). 
    * @return For example "localhost:66557"
    */
   public String getLocalAddress() {
      if (sock == null) {
         log.error(ME, "Can't determine client address, no socket connection available");
         Thread.currentThread().dumpStack();
         return null;
      }
      return "" + sock.getLocalAddress().getHostAddress() + ":" + sock.getLocalPort();
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
    * @exception       XmlBlasterException if login fails
    * @deprecated Use #connect(ConnectQos qos) instead
    */
   public void login(String loginName, String passwd, ConnectQos qos) throws XmlBlasterException
   {
      if (qos == null)
         this.loginQos = new ConnectQos(glob);
      else
         this.loginQos = qos;
      this.loginQos.setUserId(loginName);
      //this.loginQos.setCredential(passwd);
      connect(this.loginQos);
   }


   public ConnectReturnQos connect(ConnectQos qos) throws XmlBlasterException
   {
      if (qos == null)
         throw new XmlBlasterException(ME+".connect()", "Please specify a valid QoS");

      this.ME = "SocketConnection-" + qos.getUserId();
      if (log.CALL) log.call(ME, "Entering login: name=" + qos.getUserId());
      if (isLoggedIn()) {
         log.warn(ME, "You are already logged in, no relogin possible.");
         return this.connectReturnQos;
      }

      this.loginQos = qos;
      this.loginName = qos.getUserId();
      this.passwd = null;

      initSocketClient();

      return loginRaw();
   }


   /**
    * Login to the server.
    * <p />
    * For internal use only.
    * The qos needs to be set up correctly if you wish a callback
    * @exception       XmlBlasterException if login fails
    */
   public ConnectReturnQos loginRaw() throws XmlBlasterException
   {
      try {

         if (passwd == null) { // connect() the new schema
            Parser parser = new Parser(glob, Parser.INVOKE_BYTE, MethodName.CONNECT, sessionId); // sessionId is usually null on login, on reconnect != null
            parser.addQos(loginQos.toXml());
            String resp = (String)getCbReceiver().execute(parser, WAIT_ON_RESPONSE);
            this.connectReturnQos = new ConnectReturnQos(glob, resp);
            this.sessionId = this.connectReturnQos.getSecretSessionId();
         }
         else {
            throw new XmlBlasterException(ME, "login() is not supported, please use connect()");
         }
         if (log.DUMP) log.dump(ME+".ConnectQos", loginQos.toXml());
         if (log.DUMP && this.connectReturnQos!=null) log.dump(ME+".ConnectReturnQos", connectReturnQos.toXml());
         return this.connectReturnQos;
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         if (!(e instanceof IOException) && !(e instanceof java.net.ConnectException)) e.printStackTrace();
         if (log.TRACE) log.trace(ME+".loginRaw", e.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "login failed", e);
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
    * Returns the protocol type. 
    * @return "SOCKET"
    */
   public final String getProtocol()
   {
      return "SOCKET";
   }

   /**
    * Does a logout and removes the callback server.
    * <p />
    * @param sessionId The client sessionId
    */       
   public boolean disconnect(DisconnectQos qos) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering logout/disconnect: id=" + sessionId);

      if (!isLoggedIn()) {
         log.warn(ME, "You are not logged in, no logout possible.");
         return false;
      }

      try {
         Parser parser = new Parser(glob, Parser.INVOKE_BYTE, MethodName.DISCONNECT, sessionId);
         parser.addQos((qos==null)?"":qos.toXml());
         // We close first the callback thread, this could be a bit early ?
         getCbReceiver().running = false; // To avoid error messages as xmlBlaster closes the connection during disconnect()
         getCbReceiver().execute(parser, ONEWAY);
         shutdown(); // the callback server
         sessionId = null;
         return true;
      }
      catch (XmlBlasterException e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "disconnect", e);
      }
      catch (IOException e1) {
         if (log.TRACE) log.trace(ME+".disconnect", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "disconnect", e1);
      }
   }

   /**
    * Shut down the callback server.
    * Is called by logout()
    */
   public boolean shutdown()
   {
      if (log.CALL) log.call(ME, "Entering shutdown of callback server");
      if (this.cbReceiver != null) {
         this.cbClient = this.cbReceiver.getCbClient(); // remember for reconnects
         this.cbReceiver.shutdownCb();
         this.cbReceiver = null;
      }
      try { if (iStream != null) { iStream.close(); iStream=null; } } catch (IOException e) { log.warn(ME+".shutdown", e.toString()); }
      try { if (oStream != null) { oStream.close(); oStream=null; } } catch (IOException e) { log.warn(ME+".shutdown", e.toString()); }
      try { if (sock != null) { sock.close(); sock=null; } } catch (IOException e) { log.warn(ME+".shutdown", e.toString()); }
      return true;
   }

   /**
    * @return true if you are logged in
    */
   public final boolean isLoggedIn()
   {
      return this.sessionId != null;
   }

   /**
    * @return true if the socket connection is established
    */
   public final boolean isConnected()
   {
      return this.sock != null; // && cbReceiver != null
   }

   /**
    * Access handle of callback server. 
    * <p />
    * Opens the socket connection if not logged in.
    */
   public I_CallbackServer getCallbackServer() throws XmlBlasterException
   {
      return getCbReceiver();
   }

   /**
    * Access handle of callback server. 
    * <p />
    * Returns the valid SocketCallbackImpl, opens the socket connection if not logged in.
    */
   private final SocketCallbackImpl getCbReceiver() throws XmlBlasterException
   {
      if (!isConnected()) {
         initSocketClient();
      }
      return this.cbReceiver;
   }

   /**
    * Enforced by I_XmlBlasterConnection interface (fail save mode).
    * Subscribe to messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String subscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering subscribe(id=" + sessionId + ")");
      try {
         Parser parser = new Parser(glob, Parser.INVOKE_BYTE, MethodName.SUBSCRIBE, sessionId);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = getCbReceiver().execute(parser, WAIT_ON_RESPONSE);
         return (String)response; // return the QoS
      }
      catch (IOException e1) {
         if (log.TRACE) log.trace(ME+".subscribe", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "subscribe", e1);
      }
   }

   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] unSubscribe(String xmlKey_literal,
                                 String qos_literal) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering unSubscribe(): id=" + sessionId);
      if (log.DUMP) log.dump(ME, "Entering unSubscribe(): id=" + sessionId + " key='" + xmlKey_literal + "' qos='" + qos_literal + "'");

      try {
         Parser parser = new Parser(glob, Parser.INVOKE_BYTE, MethodName.UNSUBSCRIBE, sessionId);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = getCbReceiver().execute(parser, WAIT_ON_RESPONSE);
         return (String[])response;
      }
      catch (IOException e1) {
         if (log.TRACE) log.trace(ME+".unSubscribe", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "unSubscribe", e1);
      }
   }

   /**
    * Publish a message.
    * The normal publish is handled here like a publishArr
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String publish(MsgUnitRaw msgUnit) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering publish(): id=" + sessionId);

      try {
         Parser parser = new Parser(glob, Parser.INVOKE_BYTE, MethodName.PUBLISH, sessionId);
         parser.addMessage(msgUnit);
         Object response = getCbReceiver().execute(parser, WAIT_ON_RESPONSE);
         String[] arr = (String[])response; // return the QoS
         return arr[0]; // return the QoS
      }
      catch (IOException e1) {
         if (log.TRACE) log.trace(ME+".publish", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "publish", e1);
      }
   }

   /**
    * Publish multiple messages in one sweep.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] publishArr(MsgUnitRaw[] msgUnitArr) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering publishArr: id=" + sessionId);

      if (msgUnitArr == null) {
         if (log.TRACE) log.trace(ME + ".InvalidArguments", "The argument of method publishArr() are invalid");
         throw new XmlBlasterException(ME + ".InvalidArguments",
                                       "The argument of method publishArr() are invalid");
      }
      try {
         Parser parser = new Parser(glob, Parser.INVOKE_BYTE, MethodName.PUBLISH, sessionId);
         parser.addMessage(msgUnitArr);
         Object response = getCbReceiver().execute(parser, WAIT_ON_RESPONSE);
         return (String[])response; // return the QoS
      }
      catch (IOException e1) {
         if (log.TRACE) log.trace(ME+".publishArr", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "publishArr", e1);
      }
   }

   /**
    * Publish multiple messages in one sweep.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final void publishOneway(MsgUnitRaw[] msgUnitArr) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering publishOneway: id=" + sessionId);

      if (msgUnitArr == null) {
         if (log.TRACE) log.trace(ME + ".InvalidArguments", "The argument of method publishOneway() are invalid");
         return;
      }

      try {
         Parser parser = new Parser(glob, Parser.INVOKE_BYTE, MethodName.PUBLISH_ONEWAY, sessionId);
         parser.addMessage(msgUnitArr);
         getCbReceiver().execute(parser, ONEWAY);
      }
      catch (Throwable e) {
         if (log.TRACE) log.trace(ME+".publishOneway", "Sending of oneway message failed: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "publishOneway", e);
      }
   }

   /*
   public final String[] sendUpdate(MsgUnitRaw[] msgUnitArr)
      throws XmlBlasterException
      see HandleClient.java
   */


   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] erase (XmlKey xmlKey, EraseQosServer eraseQoS) throws XmlBlasterException {
      String xmlKey_literal = xmlKey.toXml();
      String eraseQoS_literal = eraseQoS.toXml();

      return erase(xmlKey_literal, eraseQoS_literal);
   }

   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] erase(String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering erase() id=" + sessionId);

      try {
         Parser parser = new Parser(glob, Parser.INVOKE_BYTE, MethodName.ERASE, sessionId);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = getCbReceiver().execute(parser, WAIT_ON_RESPONSE);
         return (String[])response; // return the QoS TODO
      }
      catch (IOException e1) {
         if (log.TRACE) log.trace(ME+".erase", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "erase", e1);
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
         Parser parser = new Parser(glob, Parser.INVOKE_BYTE, MethodName.GET, sessionId);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = getCbReceiver().execute(parser, WAIT_ON_RESPONSE);
         return (MsgUnitRaw[])response;
      }
      catch (IOException e1) {
         if (log.TRACE) log.trace(ME+".get", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "get", e1);
      }
   }

   /**
    * Check server.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String ping(String qos) throws XmlBlasterException
   {
      try {
         Parser parser = new Parser(glob, Parser.INVOKE_BYTE, MethodName.PING, null); // sessionId not necessary
         parser.addQos(""); // ("<qos><state id='OK'/></qos>");
         Object response = getCbReceiver().execute(parser, WAIT_ON_RESPONSE);
         return (String)response;
      }
      catch (IOException e1) {
         if (log.TRACE) log.trace(ME+".ping", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "ping", e1);
      }
   }

   /**
    * Dump of the state, remove in future.
    */
   public String toXml() throws XmlBlasterException
   {
      return toXml("");
   }

   /**
    * Dump of the state, remove in future.
    */
   public String toXml(String extraOffset) throws XmlBlasterException
   {
      if (this.sock == null) return "<noConnection />";
      else return "<connected/>";
   }

   /**
    * Command line usage.
    * <p />
    *  <li>-socket.port        Specify a port number where xmlBlaster SOCKET server listens
    *                      Default is port "+DEFAULT_SERVER_PORT+", the port 0 switches this feature off</li>
    *  <li>-socket.hostname    Specify a hostname where the xmlBlaster web server runs.
    *                      Default is the localhost</li>
    *  <li>-socket.localPort   You can specify our client side port as well (usually you shouldn't)
    *                      Default is that the port is choosen by the operating system</li>
    *  <li>-socket.localHostname  Specify the hostname who we are. Makes sense for multi homed computers
    *                      Defaults to our hostname</li>
    *  <li>-socket.responseTimeout  How long to wait for a method invocation to return
    *                      Defaults to one minute</li>
    *  <li>-socket.cb.multiThreaded Use seperate threads per update() on client side [true]</li>
    *  <li>-dump[socket]   true switches on detailed SOCKET debugging [false]</li>
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
      text += "   -socket.threadPrio  The priority 1=min - 10=max of the callback listener thread [5].\n";
      text += "   -socket.cb.multiThreaded Use seperate threads per update() on client side [true].\n";
      text += "   -dump[socket]       true switches on detailed SOCKET debugging [false].\n";
      text += "\n";
      return text;
   }
}

