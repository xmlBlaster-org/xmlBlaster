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
import org.xmlBlaster.protocol.socket.Parser;
import org.xmlBlaster.protocol.socket.Executor;
import org.xmlBlaster.protocol.socket.SocketUrl;
import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.util.plugin.PluginInfo;

import java.net.DatagramSocket;
import java.io.IOException;


/**
 * Used for client to receive xmlBlaster callbacks over plain sockets. 
 * <p />
 * One instance of this for each client, as a separate thread blocking
 * on the socket input stream waiting for messages from xmlBlaster. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see org.xmlBlaster.protocol.socket.Parser
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">The protocol.socket requirement</a>
 */
public class SocketCallbackImpl extends Executor implements Runnable, I_CallbackServer
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
      setLoginName(loginName);
      setCbClient(cbClient); // access callback client in super class Executor:callback

      if (this.running == false) {
         // Lookup SocketConnection instance in the NameService
         this.sockCon = (SocketConnection)glob.getObjectEntry("org.xmlBlaster.client.protocol.socket.SocketConnection");

         if (this.sockCon == null) {
            // SocketConnection.java must be instantiated first and registered to reuse the socket for callbacks
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME,
                  "Sorry, creation of SOCKET callback handler is not possible if client connection is not of type 'SOCKET'");
         }
         
         this.sockCon.registerCbReceiver(this);

         java.net.Socket sock = null;
         try {
            sock = this.sockCon.getSocket();
         }
         catch (XmlBlasterException e) {
            log.trace(ME, "There is no client socket connection which i could use: " + e.getMessage());
            return ;
         }


         try { // Executor
            DatagramSocket sockUDP = null; // SOCKET_TCP: TODO for UDP
            super.initialize(this.sockCon.getGlobal(), this.callbackAddress, sock, null, sockUDP);
         }
         catch (IOException e) {
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "Creation of SOCKET callback handler failed", e);
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

         Parser receiver = new Parser(glob);
         try {
            receiver.parse(iStream); // This method blocks until a message arrives

            if (log.DUMP) log.dump(ME, "Receiving message >" + Parser.toLiteral(receiver.createRawMsg()) + "<\n" + receiver.dump());

            if (receiver.isInvoke() && multiThreaded) {
               // Parse the message and invoke callback to client code in a separate thread
               // to avoid dead lock when client does a e.g. publish() during this update()
               WorkerThread t = new WorkerThread(glob, this, receiver);
               // -dispatch/callback/plugin/socket/invokerThreadPrio 5
               t.setPriority(this.callbackAddress.getEnv("invokerThreadPrio", Thread.NORM_PRIORITY).getValue());
               t.start();
            }
            else {                  
               receive(receiver, SOCKET_TCP);    // Parse the message and invoke actions in same thread
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
      setCbClient(null); // reset callback client in super class Executor:callback
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

