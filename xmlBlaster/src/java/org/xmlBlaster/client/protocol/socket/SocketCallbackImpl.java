/*------------------------------------------------------------------------------
Name:      SocketCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.socket;


import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.protocol.socket.Parser;
import org.xmlBlaster.protocol.socket.Executor;
import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;

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
   private String callbackAddressStr = null;

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
      return "1.0";
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
   }

   /**
    * Initialize and start the callback server
    * A thread receiving all messages from xmlBlaster, and delivering them back to the client code.
    */
   public synchronized final void initialize(Global glob, String loginName, I_CallbackExtended cbClient) throws XmlBlasterException {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.log = this.glob.getLog("socket");
      this.ME = "SocketCallbackImpl-" + loginName;
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

         try {
            super.initialize(this.sockCon.getGlobal(), this.sockCon.getSocket(), null);
         }
         catch (IOException e) {
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "Creation of SOCKET callback handler failed", e);
         }
         this.callbackAddressStr = this.sockCon.getLocalAddress();

         this.running = true;
         Thread t = new Thread(this, "XmlBlaster.SOCKET.callback-"+this.sockCon.getLoginName());
         t.setDaemon(true);
         t.setPriority(glob.getProperty().get("socket.threadPrio", Thread.NORM_PRIORITY));
         t.start();
      }
   }

   /**
    * Returns the protocol type. 
    * @return "SOCKET"
    */
   public final String getCbProtocol()
   {
      return "SOCKET";
   }

   /**
    * Returns the callback address. 
    * <p />
    * This is no listen socket, as we need no callback server.
    * It is just the client side socket data of the established connection to xmlBlaster.
    * @return "192.168.2.1:34520"
    */
   public String getCbAddress() throws XmlBlasterException
   {
      if ( sockCon == null ) {
         return "";
      }
      return sockCon.getLocalAddress();
   }
   
   /**
    * Starts the callback thread
    */
   public void run()
   {
      log.info(ME, "Started callback receiver");
      boolean multiThreaded = glob.getProperty().get("socket.cb.multiThreaded", true);

      while(running) {

         Parser receiver = new Parser(glob);
         try {
            receiver.parse(iStream); // This method blocks until a message arrives

            if (log.DUMP) log.dump(ME, "Receiving message >" + Parser.toLiteral(receiver.createRawMsg()) + "<\n" + receiver.dump());

            if (receiver.isInvoke() && multiThreaded) {
               // Parse the message and invoke callback to client code in a separate thread
               // to avoid dead lock when client does a e.g. publish() during this update()
               WorkerThread t = new WorkerThread(glob, this, receiver);
               t.setPriority(glob.getProperty().get("socket.cbInvokerThreadPrio", Thread.NORM_PRIORITY));
               t.start();
            }
            else {                  
               receive(receiver);    // Parse the message and invoke actions in same thread
            }
         }
         catch(XmlBlasterException e) {
            log.error(ME, e.toString());
         }
         catch(Throwable e) {
            if (running == true) {
               log.error(ME, "Closing connection to server: " + e.toString());
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

   /**
    * @return The XML-RPC registry entry of this server, which can be used for the loginQoS
    */
   public CallbackAddress getCallbackHandle()
   {
      CallbackAddress addr = new CallbackAddress(glob, "SOCKET");
      addr.setAddress(callbackAddressStr);
      return addr;
   }

   final SocketConnection getSocketConnection() {
      return sockCon;
   }

   /**
    * Shutdown callback, called by SocketConnection on problems
    * @return true everything is OK, false if probably messages are lost on shutdown
    */
   public synchronized void shutdown() {
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

