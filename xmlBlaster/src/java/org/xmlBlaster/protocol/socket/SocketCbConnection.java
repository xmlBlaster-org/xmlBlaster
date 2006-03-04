/*------------------------------------------------------------------------------
Name:      SocketCbConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles connection to xmlBlaster with plain sockets
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import java.io.IOException;
import java.net.Socket;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;

import org.xmlBlaster.util.protocol.socket.SocketExecutor;
import org.xmlBlaster.util.protocol.socket.SocketUrl;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.MsgInfo;



/**
 * This instance establishes exactly one connection to a listening client side callback server. 
 * <p />
 * NOTE: First step for a different SOCKET connection on callback
 * NOTE: This code is currently NOT in use (as we reuse the same SOCKET with CallbackSocketDriver.java)
 * <p />
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">The protocol.socket requirement</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class SocketCbConnection extends SocketExecutor
{
   private String ME = "SocketCbConnection";
   private Global glob;
   private static Logger log = Logger.getLogger(SocketCbConnection.class.getName());
   /** Holds the hostname/port of the callback server running on client side to which we want connect */
   private SocketUrl socketUrl;
   /** The socket connection to one client */
   protected Socket sock;
   /** The unique client cbSessionId */
   protected String cbSessionId;
   protected CallbackAddress clientAddress;

   /**
    * Connect to xmlBlaster using plain socket with native message format.
    */
   public SocketCbConnection(Global glob) throws XmlBlasterException {
      this.glob = (glob == null) ? Global.instance() : glob;

      if (log.isLoggable(Level.FINER)) log.finer("Entering init()");
   }

   /**
    * Get the raw socket handle
    */
   public Socket getSocket() throws XmlBlasterException
   {
      if (this.sock == null) {
         if (log.isLoggable(Level.FINE)) log.fine("No socket connection available.");
         //Thread.currentThread().dumpStack();
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                                       "No plain SOCKET connection available.");
      }
      return this.sock;
   }

   final Global getGlobal() {
      return this.glob;
   }

   public String getType() {
      return "SOCKET";
   }

   /**
    * Connects to clients callback server with one socket connection. 
    */
   public void connectLowlevel(CallbackAddress callbackAddress) throws XmlBlasterException {
      if (isConnected())
         return;
 
      this.clientAddress = callbackAddress;
      this.socketUrl = new SocketUrl(glob, this.clientAddress);

      if (log.isLoggable(Level.FINER)) log.finer("Entering connectLowlevel(), connection with seperate raw socket to client " +
                                    this.socketUrl.getUrl() + " ...");
      try {
         // SSL support
         boolean ssl = this.clientAddress.getEnv("SSL", false).getValue();
         if (log.isLoggable(Level.FINE)) log.fine(clientAddress.getEnvLookupKey("SSL") + "=" + ssl);
          
         // TODO: use clientAddress.getCompressType() !!!
         
         if (ssl) {
            this.socketUrl.createSocketSSL(null, this.clientAddress);
         }
         else {
             this.sock = new Socket(this.socketUrl.getInetAddress(), this.socketUrl.getPort());
         }
         
         //this.localPort = this.sock.getLocalPort();
         //this.localHostname = this.sock.getLocalAddress().getHostAddress();
         log.info("Created SOCKET client connected to '" + this.socketUrl.getUrl() + "', callback address is " + getLocalAddress());

         // initialize base class SocketExecutor
         initialize(glob, this.clientAddress, this.sock.getInputStream(), this.sock.getOutputStream());
      }
      catch (java.net.UnknownHostException e) {
         String str = "XmlBlaster server host is unknown, '-dispatch/callback/plugin/socket/hostname=<ip>': " + e.toString();
         if (log.isLoggable(Level.FINE)) log.fine(str);
         //e.printStackTrace(); 
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, 
                                       "XmlBlaster server is unknown, '-dispatch/callback/plugin/socket/hostname=<ip>'", e);
      }
      catch (java.io.IOException e) {
         String str = "Connection to xmlBlaster server failed: " + e.toString();
         if (log.isLoggable(Level.FINE)) log.fine(str);
         //e.printStackTrace(); 
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, str);
      }
      catch (Throwable e) {
         if (!(e instanceof IOException) && !(e instanceof java.net.ConnectException)) e.printStackTrace();
         String str = "Socket client connection to '" + this.socketUrl.getUrl() +
                      "' failed, try options '-dispatch/callback/plugin/socket/hostname <ip> -dispatch/callback/plugin/socket/port <port>' and check if the client has established a callback SOCKET server";
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, str, e);
      }

      if (log.isLoggable(Level.FINE)) log.fine("Created '" + getProtocol() + "' protocol plugin and connect to client side callback server on '" + this.socketUrl.getUrl() + "'");
   }


   /**
    * Reset the driver on problems
    */
   public void resetConnection()
   {
      if (log.isLoggable(Level.FINE)) log.fine("SocketClient is re-initialized, no connection available");
      try {
         shutdown();
      }
      catch (XmlBlasterException ex) {
         log.severe("disconnect. Could not shutdown properly. " + ex.getMessage());
      }
   }

   /**
    * A string with the local address and port (the xmlBlaster side). 
    * @return For example "localhost:66557"
    */
   public String getLocalAddress() {
      if (this.sock == null) {
         // Happens if on client startup an xmlBlaster server is not available
         if (log.isLoggable(Level.FINE)) log.fine("Can't determine xmlBlaster local address, no socket connection available");
         return null;
      }
      return "" + this.sock.getLocalAddress().getHostAddress() + ":" + this.sock.getLocalPort();
   }

   /**
    * Returns the protocol type. 
    * @return "SOCKET"
    */
   public final String getProtocol() {
      // TODO: return (this.pluginInfo == null) ? "SOCKET" : this.pluginInfo.getType();
      return "SOCKET";
   }

   /**
    * Shut down the callback server.
    * Is called by logout()
    */
   public void shutdown() throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering shutdown of callback server");
      try { if (this.iStream != null) { this.iStream.close(); this.iStream=null; } } catch (IOException e) { log.warning(e.toString()); }
      try { if (this.oStream != null) { this.oStream.close(); this.oStream=null; } } catch (IOException e) { log.warning(e.toString()); }
      try { if (this.sock != null) { this.sock.close(); this.sock=null; } } catch (IOException e) { log.warning(e.toString()); }
   }

   /**
    * @return true if the socket connection is established
    */
   public final boolean isConnected()
   {
      return this.sock != null;
   }

   /*
    * Updating multiple messages in one sweep, callback to client. This method is 
    * invoked when the callback socket is different from  
    * <p />
    * @param expectingResponse is WAIT_ON_RESPONSE or ONEWAY
    * @return null if oneway
    * @see org.xmlBlaster.engine.RequestBroker
    *
   private final String[] sendUpdate(String cbSessionId, MsgUnitRaw[] msgArr, boolean expectingResponse) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.call(ME, "Entering update: id=" + cbSessionId);
      if (!isConnected())
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "update() invocation ignored, we are not connected.");

      if (msgArr == null || msgArr.length < 1) {
         log.error(ME + ".InvalidArguments", "The argument of method update() are invalid");
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdate() argument");
      }
      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.UPDATE, cbSessionId);
         parser.addMessage(msgArr);
         if (expectingResponse) {
            Object response = execute(parser, SocketExecutor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
            if (log.isLoggable(Level.FINE)) log.trace(ME, "Got update response " + response.toString());
            return (String[])response; // return the QoS
         }
         else {
            execute(parser, SocketExecutor.ONEWAY, SocketUrl.SOCKET_TCP); // TODO: SOCKET_UDP
            return null;
         }
      }
      catch (XmlBlasterException xmlBlasterException) {
         // WE ONLY ACCEPT ErrorCode.USER... FROM CLIENTS !
         if (xmlBlasterException.isUser())
            throw xmlBlasterException;

         if (xmlBlasterException.isCommunication())
            throw xmlBlasterException;

         throw new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ERROR, ME,
                   "SOCKET callback of " + msgArr.length + " messages failed", xmlBlasterException);
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.trace(ME+".update", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
               "SOCKET callback of " + msgArr.length + " messages failed", e1);
      }
   }
*/

   /**
    * Check the clients cb server.
    */
   public final String ping(String qos) throws XmlBlasterException
   {
      if (!isConnected())
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "ping() invocation ignored, we are shutdown.");

      try {
         String cbSessionId = "";
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.PING, cbSessionId);
         parser.addMessage(qos);
         Object response = requestAndBlockForReply(parser, SocketExecutor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
         return (String)response;
      }
      catch (Throwable e) {
         if (log.isLoggable(Level.FINE)) log.fine("IO exception: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION,
                   ME, "SOCKET callback ping failed", e);
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
}

