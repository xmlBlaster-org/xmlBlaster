/*------------------------------------------------------------------------------
Name:      SocketCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.socket;


import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.MsgInfo;
import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.protocol.socket.SocketExecutor;
import org.xmlBlaster.util.protocol.socket.SocketUrl;

import java.net.Socket;
import java.net.SocketException;
import java.io.IOException;


/**
 * Used for client to receive xmlBlaster callbacks over plain sockets. 
 * <p />
 * One instance of this for each client, as a separate thread blocking
 * on the socket input stream waiting for messages from xmlBlaster. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see org.xmlBlaster.util.xbformat.MsgInfo
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">The protocol.socket requirement</a>
 */
public class SocketCallbackImpl extends SocketExecutor implements Runnable, I_CallbackServer
{
   private String ME = "SocketCallbackImpl";
   private Global glob;
   private LogChannel log;
   /** The connection manager 'singleton' */
   private SocketConnection sockCon;
   /** A unique name for this client socket */
   private SocketUrl socketUrl;
   private CallbackAddress callbackAddress;
   private PluginInfo pluginInfo;
   /** The socket connection to/from one client */
   protected Socket sock;

   /** Stop the thread */
   boolean running = false;

   /**
    * Called by plugin loader which calls init(Global, PluginInfo) thereafter. 
    * A thread receiving all messages from xmlBlaster, and delivering them back to the client code.
    */
   public SocketCallbackImpl() {
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getCbProtocol();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return (this.pluginInfo == null) ? "1.0" : this.pluginInfo.getVersion();
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) {
      this.pluginInfo = pluginInfo;
   }

   /**
    * Initialize and start the callback server
    * A thread receiving all messages from xmlBlaster, and delivering them back to the client code.
    */
   public synchronized final void initialize(Global glob, String loginName,
                            CallbackAddress callbackAddress, I_CallbackExtended cbClient) throws XmlBlasterException {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.log = this.glob.getLog("socket");
      this.ME = "SocketCallbackImpl-" + loginName;
      this.callbackAddress = callbackAddress;
      if (this.pluginInfo != null)
         this.callbackAddress.setPluginInfoParameters(this.pluginInfo.getParameters());
      setLoginName(loginName);
      setCbClient(cbClient); // access callback client in super class SocketExecutor:callback

      if (this.running == false) {
         // Lookup SocketConnection instance in the NameService
         this.sockCon = (SocketConnection)glob.getObjectEntry("org.xmlBlaster.client.protocol.socket.SocketConnection");

         if (this.sockCon == null) {
            // SocketConnection.java must be instantiated first and registered to reuse the socket for callbacks
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME,
                  "Sorry, creation of SOCKET callback handler is not possible if client connection is not of type 'SOCKET'");
         }
         
         this.sockCon.registerCbReceiver(this);

         try {
            this.sock = this.sockCon.getSocket();
         }
         catch (XmlBlasterException e) {
            log.trace(ME, "There is no client socket connection which i could use: " + e.getMessage());
            return ;
         }


         try { // SocketExecutor
            super.initialize(this.sockCon.getGlobal(), this.callbackAddress, this.sock.getInputStream(), this.sock.getOutputStream());
         }
         catch (IOException e) {
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "Creation of SOCKET callback handler failed", e);
         }

         try {
            // You should not activate SoTimeout, as this timeouts if InputStream.read() blocks too long.
            // But we always block on input read() to receive update() messages.
            setSoTimeout(this.callbackAddress.getEnv("SoTimeout", 0L).getValue()); // switch off
            this.sock.setSoTimeout((int)this.soTimeout);
            if (log.TRACE) log.trace(ME, this.callbackAddress.getEnvLookupKey("SoTimeout") + "=" + this.soTimeout);
   
            setSoLingerTimeout(this.callbackAddress.getEnv("SoLingerTimeout", soLingerTimeout).getValue());
            if (log.TRACE) log.trace(ME, this.callbackAddress.getEnvLookupKey("SoLingerTimeout") + "=" + getSoLingerTimeout());
            if (getSoLingerTimeout() >= 0L) {
               // >0: Try to send any unsent data on close(socket) (The UNIX kernel waits very long and ignores the given time)
               // =0: Discard remaining data on close()  <-- CHOOSE THIS TO AVOID BLOCKING close() calls
               this.sock.setSoLinger(true, (int)this.soLingerTimeout);
            }
            else
               this.sock.setSoLinger(false, 0); // false: default handling, kernel tries to send queued data after close() (the 0 is ignored)
         }
         catch (SocketException e) {
            log.error(ME, "Failed to set socket attributes, we ignore it and continue: " + e.toString());
         }

         this.socketUrl = this.sockCon.getLocalSocketUrl();
         this.callbackAddress.setRawAddress(this.socketUrl.getUrl());
         if (log.TRACE) log.trace(ME, "Callback uri=" + this.socketUrl.getUrl());

         this.running = true;
         Thread t = new Thread(this, "XmlBlaster."+getType());
         t.setDaemon(true);
         int threadPrio = this.callbackAddress.getEnv("threadPrio", Thread.NORM_PRIORITY).getValue();
         try {
            t.setPriority(threadPrio);
            if (log.TRACE) log.trace(ME, "-dispatch/callback/plugin/socket/threadPrio = " + threadPrio);
         }
         catch (IllegalArgumentException e) {
            log.warn(ME, "Your -dispatch/callback/plugin/socket/threadPrio " + threadPrio + " is out of range, we continue with default setting " + Thread.NORM_PRIORITY);
         }
         t.start();
      }
   }

   /*
    * TODO: Is this needed anymore?
    * @return
    */
   protected boolean hasConnection() {
      return (this.sock != null);
   }

   /**
    * Returns the protocol type. 
    * @return The configured [type] in xmlBlaster.properties, defaults to "SOCKET"
    */
   public final String getCbProtocol()
   {
      return (this.pluginInfo == null) ? "SOCKET" : this.pluginInfo.getType();
   }

   /**
    * Returns the callback address. 
    * <p />
    * This is no listen socket, as we need no callback server.
    * It is just the client side socket data of the established connection to xmlBlaster.
    * @return "socket://192.168.2.1:34520"
    */
   public String getCbAddress() throws XmlBlasterException
   {
      if ( socketUrl == null ) {
         return "";
      }
      return socketUrl.getUrl();
   }
   
   /**
    * Starts the callback thread
    */
   public void run()
   {
      log.info(ME, "Started callback receiver plugin on '" + this.socketUrl.getUrl() + "'");
      boolean multiThreaded = this.callbackAddress.getEnv("multiThreaded", true).getValue();
      if (log.TRACE) log.trace(ME, "SOCKET multiThreaded=" + multiThreaded);

      while(running) {

         try {
            // This method blocks until a message arrives
            MsgInfo receiver = MsgInfo.parse(glob, progressListener, iStream);

            if (log.DUMP) log.dump(ME, "Receiving message >" + receiver.toLiteral() + "<\n" + receiver.dump());

            if (receiver.isInvoke() && multiThreaded) {
               // Parse the message and invoke callback to client code in a separate thread
               // to avoid dead lock when client does a e.g. publish() during this update()
               WorkerThread t = new WorkerThread(glob, this, receiver);
               // -dispatch/callback/plugin/socket/invokerThreadPrio 5
               t.setPriority(this.callbackAddress.getEnv("invokerThreadPrio", Thread.NORM_PRIORITY).getValue());
               t.start();
            }
            else {                  
               receiveReply(receiver, SocketUrl.SOCKET_TCP);    // Parse the message and invoke actions in same thread
            }
            if (MethodName.DISCONNECT == receiver.getMethodName() && receiver.isResponse()) {
               if (log.TRACE) log.trace(ME, "Terminating socket callback thread because of disconnect response");
               running = false;
            }
         }
         catch(XmlBlasterException e) {
            log.warn(ME, e.toString());
         }
         catch(Throwable e) {
            if (running == true) {
               if (e.toString().indexOf("javax.net.ssl") != -1) {
                  log.warn(ME, "Closing connection to server, please try debugging SSL with 'java -Djavax.net.debug=all ...': " + e.toString());
               }
               else if (e instanceof IOException) {
                  log.warn(ME, "Closing connection to server: " + e.toString());
               }
               else {
                  log.error(ME, "Closing connection to server: " + e.toString());
               }
               try {
                  sockCon.shutdown();
               }
               catch (XmlBlasterException ex) {
                  this.log.error(ME, "run() could not shutdown correctly. " + ex.getMessage());
               }
               // Exceptions ends nowhere but terminates the thread
            }
         }
      }
      if (log.TRACE) log.trace(ME, "Terminating socket callback thread");
   }

   final SocketConnection getSocketConnection() {
      return sockCon;
   }

   /**
    * Shutdown callback only.
    */
   public synchronized void shutdown() {
      setCbClient(null); // reset callback client in super class SocketExecutor:callback
   }

   /**
    * Shutdown SOCKET connection and callback, called by SocketConnection on problems
    */
   public synchronized void shutdownSocket() {
      if (log.TRACE) log.trace(ME, "Entering shutdownSocket()");
      this.running = false;
      if (this.iStream != null) {
         try {
            this.iStream.close();
            this.iStream = null;
         } catch(IOException e) {
            log.warn(ME, e.toString());
         }
      }
      try {
         if (responseListenerMap.size() > 0) {
            java .util.Iterator iterator = responseListenerMap.keySet().iterator();
            StringBuffer buf = new StringBuffer(256);
            while (iterator.hasNext()) {
               if (buf.length() > 0) buf.append(", ");
               String key = (String)iterator.next();
               buf.append(key);
            }
            log.warn(ME, "There are " + responseListenerMap.size() + " messages pending without a response, request IDs are " + buf.toString());
            responseListenerMap.clear();
         }
      }
      finally {
         freePendingThreads();
      }
   }
} // class SocketCallbackImpl

