/*------------------------------------------------------------------------------
Name:      EmailConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles connection to xmlBlaster with plain emails
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.email;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.protocol.Executor;
import org.xmlBlaster.util.protocol.email.EmailExecutor;
import org.xmlBlaster.util.protocol.email.Pop3Driver;
import org.xmlBlaster.util.protocol.socket.SocketUrl;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.xbformat.MsgInfo;
import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;


/**
 * This driver sends emails to the xmlBlaster server, the return QOS are polled via POP3. 
 * <p />
 * This "EMAIL" driver needs to be registered in xmlBlaster.properties
 * and will be started on xmlBlaster startup:
 * <pre>
 * ClientProtocolPlugin[EMAIL][1.0]=org.xmlBlaster.client.protocol.email.EmailConnection
 * </pre>
 * <p />
 * All adjustable parameters are explained in {@link org.xmlBlaster.client.protocol.email.EmailConnection#usage()}
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.email.html">The protocol.email requirement</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class EmailConnection extends EmailExecutor implements I_XmlBlasterConnection
{
   private static Logger log = Logger.getLogger(EmailConnection.class.getName());
   private String ME = "EmailConnection";
   private Global glob;
   /** The unique client sessionId */
   protected String sessionId = "VOID";
   protected String loginName = "dummyLoginName";
   protected Address clientAddress;
   private PluginInfo pluginInfo;
   private boolean isLoggedIn;
   private boolean isInitialized;

   /**
    * Called by plugin loader which calls init(Global, PluginInfo) thereafter. 
    */
   public EmailConnection() {
   }

   /**
    */
   public String getLoginName() {
      return this.loginName;
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getProtocol();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return (this.pluginInfo == null) ? "1.0" : this.pluginInfo.getVersion();
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.pluginInfo = pluginInfo;
      if (log.isLoggable(Level.FINE)) log.fine("Entering init()");
   }

   /**
    * Connects to xmlBlaster with one email connection. 
    * @see I_XmlBlasterConnection#connectLowlevel(Address)
    */
   public void connectLowlevel(Address address) throws XmlBlasterException {
      if (!this.isInitialized) {
         super.pop3Driver = (Pop3Driver)glob.getObjectEntry(Pop3Driver.class.getName());
         if (super.pop3Driver == null) {
            super.pop3Driver = new Pop3Driver();
            super.pop3Driver.init(glob, this.pluginInfo);
         }
         
         super.init(glob, address, this.pluginInfo);
         
         // Who are we?
         // We need to correct the mail addresses from EmailExecutor
         // as it assumes server side operation
         if (super.fromAddress == null) {
            //try {
            //   super.fromAddress = new InternetAddress(this.callbackAddress.getRawAddress());
            //} catch (AddressException e) {
               throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT,
                     ME, "Please configure a 'from' address with 'mail.smtp.from=xy@somehost.com'");
            //}
         }
         
         // Guess the email address to reach the xmlBlaster server
         // TODO: Extract the address dynamically from the received UPDATE message 
         String to = this.glob.get("mail.smtp.to", "xmlBlaster@localhost", null,
               this.pluginInfo);
         try {
            super.toAddress = new InternetAddress(to);
         } catch (AddressException e) {
            throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT,
                  ME, "Illegal 'from' address '" + to + "'");
         }
         this.isInitialized = true;
         log.info("Initialized email connection from='" + super.fromAddress.toString() + "' to='" + super.toAddress.toString() + "'");
      }
   }

   /**
    * Reset the driver on problems
    */
   public void resetConnection() {
      shutdown();
   }

   /**
    * @see I_XmlBlasterConnection#setConnectReturnQos(ConnectReturnQos)
    */
   public void setConnectReturnQos(ConnectReturnQos connectReturnQos) {
      this.sessionId = connectReturnQos.getSecretSessionId();
      this.loginName = connectReturnQos.getSessionName().getLoginName();
      this.ME = "EmailConnection-"+loginName;
      this.isLoggedIn = true;
   }

   /**
    * Login to the server. 
    * <p />
    * @param connectQos The encrypted connect QoS 
    * @exception XmlBlasterException if login fails
    */
   public String connect(String connectQos) throws XmlBlasterException {
      if (connectQos == null)
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME+".connect()", "Please specify a valid QoS");
      if (log.isLoggable(Level.FINER)) log.finer("Entering connect("+connectQos+")");
      if (isLoggedIn()) {
         log.warning("You are already logged in, we try again");
         Thread.dumpStack();
      }

      connectLowlevel(this.clientAddress);

      try {
         // sessionId is usually null on login, on reconnect != null
         return (String)super.sendEmail(connectQos, MethodName.CONNECT, Executor.WAIT_ON_RESPONSE);
/*
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.CONNECT, sessionId);
         parser.addQos(connectQos);
         return (String)super.execute(parser, Executor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
         */
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         if (!(e instanceof IOException) && !(e instanceof java.net.ConnectException)) e.printStackTrace();
         if (log.isLoggable(Level.FINE)) log.fine(e.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "login failed", e);
      }
   }

   /**
    * Returns the protocol type. 
    * @return "EMAIL"
    */
   public final String getProtocol() {
      return (this.pluginInfo == null) ? "EMAIL" : this.pluginInfo.getType();
   }

    /**
    * Does a logout and removes the callback server.
    * <p />
    * @param sessionId The client sessionId
    */       
   public boolean disconnect(String qos) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering logout/disconnect: id=" + sessionId);

      if (!isLoggedIn()) {
         log.warning("You are not logged in, no logout possible.");
         return false;
      }

      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.DISCONNECT, sessionId);
         parser.addQos((qos==null)?"":qos);
         super.execute(parser, Executor.WAIT_ON_RESPONSE/*ONEWAY*/, SocketUrl.SOCKET_TCP);
         return true;
      }
      catch (XmlBlasterException e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "disconnect", e);
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.fine(e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "disconnect", e1);
      }
      finally {
       //  shutdown(); // the callback server
       //  sessionId = null;
      }
   }

   /**
    * Shut down the callback server.
    * Is called by logout()
    */
   public void shutdown() {
      if (log.isLoggable(Level.FINER)) log.finer("Entering shutdown of callback server");
      this.isLoggedIn = false;
   }

   /**
    * @return true if you are logged in
    */
   public final boolean isLoggedIn() {
      return this.isLoggedIn;
   }

   /**
    * Enforced by I_XmlBlasterConnection interface (failsafe mode).
    * Subscribe to messages.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">The interface.subscribe requirement</a>
    */
   public final String subscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering subscribe(id=" + sessionId + ")");
      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.SUBSCRIBE, sessionId);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = super.execute(parser, Executor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
         return (String)response; // return the QoS
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.fine(xmlKey_literal + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, MethodName.SUBSCRIBE.toString(), e1);
      }
   }

   /**
    * Unsubscribe from messages.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html">The interface.unSubscribe requirement</a>
    */
   public final String[] unSubscribe(String xmlKey_literal,
                                 String qos_literal) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering unSubscribe(): id=" + sessionId);

      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.UNSUBSCRIBE, sessionId);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = super.execute(parser, Executor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
         return (String[])response;
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.fine(e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, MethodName.UNSUBSCRIBE.toString(), e1);
      }
   }

   /**
    * Publish a message.
    * The normal publish is handled here like a publishArr
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public final String publish(MsgUnitRaw msgUnit) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering publish(): id=" + sessionId);

      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.PUBLISH, sessionId);
         parser.addMessage(msgUnit);
         Object response = super.execute(parser, Executor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
         String[] arr = (String[])response; // return the QoS
         return arr[0]; // return the QoS
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.fine(e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, MethodName.PUBLISH.toString(), e1);
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
         if (log.isLoggable(Level.FINE)) log.fine("The argument of method publishArr() are invalid");
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".InvalidArguments",
                                       "The argument of method publishArr() are invalid");
      }
      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.PUBLISH, sessionId);
         parser.addMessage(msgUnitArr);
         Object response = super.execute(parser, Executor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
         return (String[])response; // return the QoS
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.fine(e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "publishArr", e1);
      }
   }

   /**
    * Publish multiple messages in one sweep.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public final void publishOneway(MsgUnitRaw[] msgUnitArr) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering publishOneway: id=" + sessionId);

      if (msgUnitArr == null) {
         if (log.isLoggable(Level.FINE)) log.fine("The argument of method publishOneway() are invalid");
         return;
      }

      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.PUBLISH_ONEWAY, sessionId);
         parser.addMessage(msgUnitArr);
         super.execute(parser, Executor.ONEWAY, SocketUrl.SOCKET_TCP);
      }
      catch (Throwable e) {
         if (log.isLoggable(Level.FINE)) log.fine("Sending of oneway message failed: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, MethodName.PUBLISH_ONEWAY.toString(), e);
      }
   }

   /**
    * Delete messages.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.erase.html">The interface.erase requirement</a>
    */
   public final String[] erase(String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering erase() id=" + sessionId);

      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.ERASE, sessionId);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = super.execute(parser, Executor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
         return (String[])response; // return the QoS TODO
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.fine(e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, MethodName.ERASE.toString(), e1);
      }
   }

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
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.GET, sessionId);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = super.execute(parser, Executor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
         return (MsgUnitRaw[])response;
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.fine(e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, MethodName.GET.toString(), e1);
      }
   }

   /**
    * Check server.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String ping(String qos) throws XmlBlasterException
   {
      if (this.isInitialized) return "";
      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.PING, null); // sessionId not necessary
         parser.addQos(""); // ("<qos><state id='OK'/></qos>");
         Object response = super.execute(parser, Executor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
         return (String)response;
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.fine("IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, MethodName.PING.toString(), e1);
      }
   }

   /**
    * Command line usage.
    * @see  SmtpClient#setSessionProperties(Properties, Global, I_PluginConfig)
    */
   public static String usage()
   {
      String text = "\n";
      text += "EmailConnection 'EMAIL' options:\n";
      text += "   -dispatch/connection/plugin/email/port\n";
      text += "                       Specify a port number where the SMTP MTA listens [25].\n";
      text += "   -dispatch/connection/plugin/email/hostname\n";
      text += "                       Specify a hostname where the SMTP MTA runs [localhost].\n";
      text += "   -dispatch/connection/plugin/email/responseTimeout\n";
      text += "                       How long to wait for a method invocation to return [60000].\n";
      text += "                       Defaults to one minute.\n";
      text += "   -dispatch/connection/plugin/email/multiThreaded\n";
      text += "                       Use seperate threads per update() on client side [true].\n";
      text += "   -dispatch/connection/plugin/email/compress/type\n";
      text += "                       Valid values are: '', '"+Constants.COMPRESS_ZLIB_STREAM+"', '"+Constants.COMPRESS_ZLIB+"' [].\n";
      text += "                       '' disables compression, '"+Constants.COMPRESS_ZLIB_STREAM+"' compresses whole stream.\n";
      text += "                       '"+Constants.COMPRESS_ZLIB+"' only compresses flushed chunks bigger than 'compress/minSize' bytes.\n";
      text += "   -dispatch/connection/plugin/email/compress/minSize\n";
      text += "                       Compress message bigger than given bytes, see above.\n";
      text += "   -dump[email]       true switches on detailed EMAIL debugging [false].\n";
      text += "\n";
      return text;
   }
}

