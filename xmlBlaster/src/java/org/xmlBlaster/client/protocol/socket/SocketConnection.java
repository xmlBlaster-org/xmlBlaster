/*------------------------------------------------------------------------------
Name:      SocketConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native xmlBlaster Proxy. Can be called by the client in the same VM
Version:   $Id: SocketConnection.java,v 1.7 2002/02/15 23:41:04 ruff Exp $
Author:    ruff@swand.lake.de
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
import org.xmlBlaster.protocol.socket.I_ResponseListener;
import org.xmlBlaster.protocol.socket.Executor;
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
public class SocketConnection implements I_XmlBlasterConnection
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
   /** The socket connection to/from one client */
   protected Socket sock;
   /** Reading from socket */
   protected InputStream iStream;
   /** Writing to socket */
   protected OutputStream oStream;
   /** SocketCallbackImpl listens on socket to receive callbacks */
   protected SocketCallbackImpl cbReceiver = null;
   /** The client code which wants the callback messages */
   protected I_CallbackExtended client = null;
   private String passwd = null;
   protected ConnectQos loginQos = null;
   protected ConnectReturnQos returnQos = null;
   /** Praefix for requestId */
   protected String praefix = null;
   /** The unique client sessionId */
   protected String sessionId = null;
   /** The client login name */
   protected String loginName = "";

   /**
    * Connect to xmlBlaster using plain socket with native message format.
    */
   public SocketConnection(String[] args) throws XmlBlasterException
   {
   }

   /**
    * Connect to xmlBlaster using XML-RPC.
    */
   public SocketConnection(java.applet.Applet ap) throws XmlBlasterException
   {
   }

   public Socket getSocket() throws ConnectionException
   {
      if (this.sock == null) {
         if (Log.TRACE) Log.trace(ME, "No socket connection available.");
         throw new ConnectionException(ME+".init", "No plain socket connection available.");
      }
      return this.sock;
   }

   /**
    * Connects to xmlBlaster with one socket connection. 
    */
   private void initSocketClient() throws XmlBlasterException, ConnectionException
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
         throw new ConnectionException(ME, str);
      }
      catch (Throwable e) {
         if (!(e instanceof IOException) && !(e instanceof java.net.ConnectException)) e.printStackTrace();
         //Log.error(ME+".constructor", e.toString());
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
      this.praefix = this.loginName + ":";
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
      this.praefix = this.loginName + ":";
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
            this.cbReceiver = new SocketCallbackImpl(this, client);
             // We set our IP:port just for information, it is not actively used by xmlBlaster:
            loginQos.addCallbackAddress(new CallbackAddress("SOCKET", getLocalAddress()));
         }

         if (passwd == null) { // connect() the new schema
            if (Log.TRACE) Log.trace(ME, "Executing authenticate.connect() via Socket with security plugin" + loginQos.toXml());
            Parser parser = new Parser(Parser.INVOKE_TYPE, Constants.CONNECT, sessionId); // sessionId is usually null on login, on reconnect != null
            parser.addQos(loginQos.toXml());
            String resp = (String)cbReceiver.execute(parser, praefix, true);
            ConnectReturnQos response = new ConnectReturnQos(resp);
            this.sessionId = response.getSessionId();
            // return (String)response; // in future change to return QoS
         }
         else {
            throw new XmlBlasterException(ME, "login() is not supported, please use connect()");
         }
         if (Log.DUMP) Log.dump(ME, loginQos.toXml());
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
         //Log.error(ME+".constructor", e.toString());
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

      try {
         Parser parser = new Parser(Parser.INVOKE_TYPE, Constants.DISCONNECT, sessionId);
         parser.addQos("<qos><state>OK</state></qos>");
         cbReceiver.execute(parser, loginName, false);
         shutdown(); // the callback server
         init();
         return true;
      }
      catch (XmlBlasterException e) {
         //Log.error(ME+".disconnect", e.toString());
         throw new ConnectionException(ME+".disconnect", e.toString());
      }
      catch (IOException e1) {
         Log.error(ME+".disconnect", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".disconnect", e1.toString());
      }
   }


   /**
    * Shut down the callback server.
    * Is called by logout()
    */
   public boolean shutdown()
   {
      if (this.cbReceiver != null) {
         this.cbReceiver.shutdown();
         this.cbReceiver = null;
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
      try {
         Parser parser = new Parser(Parser.INVOKE_TYPE, Constants.SUBSCRIBE, sessionId);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = cbReceiver.execute(parser, loginName, true);
         Log.info(ME, "Got subscribe response " + response.toString());
         return (String)response; // return the QoS
      }
      catch (IOException e1) {
         Log.error(ME+".subscribe", "IO exception: " + e1.toString());
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
      if (Log.CALL) Log.call(ME, "Entering unSubscribe(): id=" + sessionId);
      if (Log.DUMP) Log.dump(ME, "Entering unSubscribe(): id=" + sessionId + " key='" + xmlKey_literal + "' qos='" + qos_literal + "'");

      try {
         Parser parser = new Parser(Parser.INVOKE_TYPE, Constants.UNSUBSCRIBE, sessionId);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = cbReceiver.execute(parser, loginName, true);
         Log.info(ME, "Got unSubscribe response " + response.toString());
         // return (String)response; // return the QoS TODO
         return;
      }
      catch (IOException e1) {
         Log.error(ME+".unSubscribe", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".unSubscribe", e1.toString());
      }
   }



   /**
    * Publish a message.
    * The normal publish is handled here like a publishArr
    */
   public final String publish(MessageUnit msgUnit) throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "Entering publish(): id=" + sessionId);

      try {
         Parser parser = new Parser(Parser.INVOKE_TYPE, Constants.PUBLISH, sessionId);
         parser.addMessage(msgUnit);
         Object response = cbReceiver.execute(parser, loginName, true);
         String[] arr = (String[])response; // return the QoS
         Log.info(ME, "Got publish response " + arr[0]);
         return arr[0]; // return the QoS
      }
      catch (IOException e1) {
         Log.error(ME+".publish", "IO exception: " + e1.toString());
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
      if (Log.CALL) Log.call(ME, "Entering publishArr: id=" + sessionId);

      if (msgUnitArr == null) {
         Log.error(ME + ".InvalidArguments", "The argument of method publishArr() are invalid");
         throw new XmlBlasterException(ME + ".InvalidArguments",
                                       "The argument of method publishArr() are invalid");
      }
      try {
         Parser parser = new Parser(Parser.INVOKE_TYPE, Constants.PUBLISH, sessionId);
         parser.addMessage(msgUnitArr);
         Object response = cbReceiver.execute(parser, loginName, true);
         Log.info(ME, "Got publishArr response " + response.toString());
         return (String[])response; // return the QoS
      }
      catch (IOException e1) {
         Log.error(ME+".publishArr", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".publishArr", e1.toString());
      }
   }


   /**
    * Updating multiple messages in one sweep.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
    /* The client should not send an update
   public final String[] sendUpdate(MessageUnit[] msgUnitArr)
      throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "Entering update: id=" + sessionId);

      if (msgUnitArr == null) {
         Log.error(ME + ".InvalidArguments", "The argument of method update() are invalid");
         throw new XmlBlasterException(ME + ".InvalidArguments",
                                       "The argument of method update() are invalid");
      }
      try {
         Parser parser = new Parser(Parser.INVOKE_TYPE, Constants.UPDATE, sessionId);
         parser.addMessage(msgUnitArr);
         Object response = cbReceiver.execute(parser, loginName, true);
         Log.info(ME, "Got update response " + response.toString());
         return (String[])response; // return the QoS
      }
      catch (IOException e1) {
         Log.error(ME+".update", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".update", e1.toString());
      }
   }
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
      if (Log.CALL) Log.call(ME, "Entering erase() id=" + sessionId);

      try {
         Parser parser = new Parser(Parser.INVOKE_TYPE, Constants.ERASE, sessionId);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = cbReceiver.execute(parser, loginName, true);
         Log.info(ME, "Got erase response " + response.toString());
         return (String[])response; // return the QoS TODO
      }
      catch (IOException e1) {
         Log.error(ME+".erase", "IO exception: " + e1.toString());
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
      if (Log.CALL) Log.call(ME, "Entering get() xmlKey=\n" + xmlKey_literal + ") ...");
      try {
         Parser parser = new Parser(Parser.INVOKE_TYPE, Constants.GET, sessionId);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = cbReceiver.execute(parser, loginName, true);
         Log.info(ME, "Got get response " + response.toString());
         return (MessageUnit[])response;
      }
      catch (IOException e1) {
         Log.error(ME+".get", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".get", e1.toString());
      }
   }


   /**
    * Check server.
    * @see xmlBlaster.idl
    */
   public void ping() throws ConnectionException, XmlBlasterException
   {
      try {
         Parser parser = new Parser(Parser.INVOKE_TYPE, Constants.PING, null); // sessionId not necessary
         parser.addQos("<qos><state>OK</state></qos>");
         Object response = cbReceiver.execute(parser, loginName, true);
         Log.info(ME, "Got ping response " + response.toString());
         // return (String)response; // return the QoS TODO
         return;
      }
      catch (IOException e1) {
         Log.error(ME+".ping", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".ping", e1.toString());
      }
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
}

