/*------------------------------------------------------------------------------
Name:      SocketConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles connection to xmlBlaster with plain sockets
Version:   $Id: SocketConnection.java,v 1.31 2002/09/09 13:37:22 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.socket;

import java.io.IOException;
import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.util.XmlBlasterException;

import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.xml2java.EraseQoS;
import org.xmlBlaster.engine.xml2java.GetQoS;
import org.xmlBlaster.protocol.socket.ExecutorBase;
import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;
import org.xmlBlaster.client.protocol.ConnectionException;
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
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
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
   int SOCKET_DEBUG=0;

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
      SOCKET_DEBUG = glob.getProperty().get("socket.debug", 0);
   }

   /**
    * Get the raw socket handle
    */
   public Socket getSocket() throws ConnectionException
   {
      if (this.sock == null) {
         if (log.TRACE) log.trace(ME, "No socket connection available.");
         throw new ConnectionException(ME+".init", "No plain socket connection available.");
      }
      return this.sock;
   }

   final Global getGlobal() {
      return this.glob;
   }

   /**
    * Connects to xmlBlaster with one socket connection. 
    */
   private void initSocketClient() throws XmlBlasterException, ConnectionException
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
            if (SOCKET_DEBUG>0 || log.TRACE) log.info(ME, "Trying socket connection to " + hostname + " on port " + port + " ...");
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
         log.error(ME+".constructor", str);
         //e.printStackTrace(); 
         throw new ConnectionException(ME, str);
      }
      catch (Throwable e) {
         if (!(e instanceof IOException) && !(e instanceof java.net.ConnectException)) e.printStackTrace();
         String str = "Socket client connection to " + hostname + " on port " + port + " failed, try options '-socket.hostname <ip> -socket.port <port>' and check if the xmlBlaster server has loaded the socket driver in xmlBlaster.properties: " + e.toString();
         //log.error(ME+".constructor", e.toString());
         throw new ConnectionException(ME, str);
      }
   }


   /**
    * Reset the driver on problems
    */
   public void init()
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
    */
   public void login(String loginName, String passwd, ConnectQos qos) throws XmlBlasterException, ConnectionException
   {
      this.ME = "SocketConnection-" + loginName;
      if (log.CALL) log.call(ME, "Entering login: name=" + loginName);
      if (isLoggedIn()) {
         log.warn(ME, "You are already logged in, no relogin possible.");
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
   public ConnectReturnQos loginRaw() throws XmlBlasterException, ConnectionException
   {
      try {

         if (passwd == null) { // connect() the new schema
            Parser parser = new Parser(Parser.INVOKE_BYTE, Constants.CONNECT, sessionId); // sessionId is usually null on login, on reconnect != null
            parser.addQos(loginQos.toXml());
            String resp = (String)getCbReceiver().execute(parser, WAIT_ON_RESPONSE);
            this.connectReturnQos = new ConnectReturnQos(glob, resp);
            this.sessionId = this.connectReturnQos.getSessionId();
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
      catch (ConnectionException e) {
         throw e;
      }
      catch (Throwable e) {
         if (!(e instanceof IOException) && !(e instanceof java.net.ConnectException)) e.printStackTrace();
         e.printStackTrace();
         //log.error(ME+".constructor", e.toString());
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
   public boolean disconnect(DisconnectQos qos)
   {
      if (log.CALL) log.call(ME, "Entering logout/disconnect: id=" + sessionId);

      if (!isLoggedIn()) {
         log.warn(ME, "You are not logged in, no logout possible.");
      }

      try {
         Parser parser = new Parser(Parser.INVOKE_BYTE, Constants.DISCONNECT, sessionId);
         parser.addQos((qos==null)?"":qos.toXml());
         // We close first the callback thread, this could be a bit early ?
         getCbReceiver().running = false; // To avoid error messages as xmlBlaster closes the connection during disconnect()
         getCbReceiver().execute(parser, ONEWAY);
         shutdown(); // the callback server
         init();
         return true;
      }
      catch (XmlBlasterException e) {
         //log.error(ME+".disconnect", e.toString());
         throw new ConnectionException(ME+".disconnect", e.toString());
      }
      catch (IOException e1) {
         log.error(ME+".disconnect", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".disconnect", e1.toString());
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
   public I_CallbackServer getCallbackServer() throws ConnectionException, XmlBlasterException
   {
      return getCbReceiver();
   }

   /**
    * Access handle of callback server. 
    * <p />
    * Returns the valid SocketCallbackImpl, opens the socket connection if not logged in.
    */
   private final SocketCallbackImpl getCbReceiver() throws ConnectionException, XmlBlasterException
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
   public final String subscribe (String xmlKey_literal, String qos_literal) throws XmlBlasterException, ConnectionException
   {
      if (log.CALL) log.call(ME, "Entering subscribe(id=" + sessionId + ")");
      try {
         Parser parser = new Parser(Parser.INVOKE_BYTE, Constants.SUBSCRIBE, sessionId);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = getCbReceiver().execute(parser, WAIT_ON_RESPONSE);
         return (String)response; // return the QoS
      }
      catch (IOException e1) {
         log.error(ME+".subscribe", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".subscribe", e1.toString());
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
      if (log.CALL) log.call(ME, "Entering unSubscribe(): id=" + sessionId);
      if (log.DUMP) log.dump(ME, "Entering unSubscribe(): id=" + sessionId + " key='" + xmlKey_literal + "' qos='" + qos_literal + "'");

      try {
         Parser parser = new Parser(Parser.INVOKE_BYTE, Constants.UNSUBSCRIBE, sessionId);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = getCbReceiver().execute(parser, WAIT_ON_RESPONSE);
         // return (String)response; // return the QoS TODO
         return;
      }
      catch (IOException e1) {
         log.error(ME+".unSubscribe", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".unSubscribe", e1.toString());
      }
   }



   /**
    * Publish a message.
    * The normal publish is handled here like a publishArr
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String publish(MessageUnit msgUnit) throws XmlBlasterException, ConnectionException
   {
      if (log.CALL) log.call(ME, "Entering publish(): id=" + sessionId);

      try {
         Parser parser = new Parser(Parser.INVOKE_BYTE, Constants.PUBLISH, sessionId);
         parser.addMessage(msgUnit);
         Object response = getCbReceiver().execute(parser, WAIT_ON_RESPONSE);
         String[] arr = (String[])response; // return the QoS
         return arr[0]; // return the QoS
      }
      catch (IOException e1) {
         log.error(ME+".publish", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".publish", e1.toString());
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
      if (log.CALL) log.call(ME, "Entering publishArr: id=" + sessionId);

      if (msgUnitArr == null) {
         log.error(ME + ".InvalidArguments", "The argument of method publishArr() are invalid");
         throw new XmlBlasterException(ME + ".InvalidArguments",
                                       "The argument of method publishArr() are invalid");
      }
      try {
         Parser parser = new Parser(Parser.INVOKE_BYTE, Constants.PUBLISH, sessionId);
         parser.addMessage(msgUnitArr);
         Object response = getCbReceiver().execute(parser, WAIT_ON_RESPONSE);
         return (String[])response; // return the QoS
      }
      catch (IOException e1) {
         log.error(ME+".publishArr", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".publishArr", e1.toString());
      }
   }

   /**
    * Publish multiple messages in one sweep.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final void publishOneway(MessageUnit[] msgUnitArr) throws ConnectionException
   {
      if (log.CALL) log.call(ME, "Entering publishOneway: id=" + sessionId);

      if (msgUnitArr == null) {
         log.error(ME + ".InvalidArguments", "The argument of method publishOneway() are invalid");
         return;
      }

      try {
         Parser parser = new Parser(Parser.INVOKE_BYTE, Constants.PUBLISH_ONEWAY, sessionId);
         parser.addMessage(msgUnitArr);
         getCbReceiver().execute(parser, ONEWAY);
      }
      catch (Throwable e) {
         log.error(ME+".publishOneway", "Sending of oneway message failed: " + e.toString());
         throw new ConnectionException(ME+".publishOneway", e.toString());
      }
   }

   /*
   public final String[] sendUpdate(MessageUnit[] msgUnitArr)
      throws XmlBlasterException, ConnectionException
      see HandleClient.java
   */


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
      if (log.CALL) log.call(ME, "Entering erase() id=" + sessionId);

      try {
         Parser parser = new Parser(Parser.INVOKE_BYTE, Constants.ERASE, sessionId);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = getCbReceiver().execute(parser, WAIT_ON_RESPONSE);
         return (String[])response; // return the QoS TODO
      }
      catch (IOException e1) {
         log.error(ME+".erase", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".erase", e1.toString());
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
      if (log.CALL) log.call(ME, "Entering get() xmlKey=\n" + xmlKey_literal + ") ...");
      try {
         Parser parser = new Parser(Parser.INVOKE_BYTE, Constants.GET, sessionId);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = getCbReceiver().execute(parser, WAIT_ON_RESPONSE);
         return (MessageUnit[])response;
      }
      catch (IOException e1) {
         log.error(ME+".get", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".get", e1.toString());
      }
   }


   /**
    * Check server.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String ping(String qos) throws ConnectionException, XmlBlasterException
   {
      try {
         Parser parser = new Parser(Parser.INVOKE_BYTE, Constants.PING, null); // sessionId not necessary
         parser.addQos(""); // ("<qos><state id='OK'/></qos>");
         Object response = getCbReceiver().execute(parser, WAIT_ON_RESPONSE);
         return (String)response;
      }
      catch (IOException e1) {
         log.error(ME+".ping", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".ping", e1.toString());
      }
   }


   /**
    * The update method.
    * <p />
    * Gets invoked from xmlBlaster callback
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
    /*
   public String[] update(MessageUnit[] arr) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering update()");
      if (cbClient == null) {
         log.warn(ME, "Ignoring callback message, client is not interested in it");
         return "<qos><state id='OK'/></qos>";
      }
      if (arr != null) {
         for (int ii=0; ii<arr.length; ii++) {
            cbClient.update(getLoginName(), arr[ii].getXmlKey(), arr[ii].getContent(), arr[ii].getQos());
         }
      }
      return "<qos><state id='OK'/></qos>";
   }
    */

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
    *  <li>-socket.debug       1 or 2 switches on detailed SOCKET debugging [0]</li>
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
      text += "   -socket.debug       1 or 2 switches on detailed SOCKET debugging [0].\n";
      text += "\n";
      return text;
   }
}

