/*------------------------------------------------------------------------------
Name:      SocketDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   SocketDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: SocketDriver.java,v 1.3 2002/02/14 19:04:52 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.Constants;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;


/**
 * Socket driver class to invoke the xmlBlaster server over a native message format
 * <p />
 * This driver needs to be registered in xmlBlaster.properties
 * and will be started on xmlBlaster startup, for example:
 * <pre>
 *   Protocol.Drivers=IOR:org.xmlBlaster.protocol.corba.CorbaDriver,\
 *                    RMI:org.xmlBlaster.protocol.rmi.RmiDriver,\
 *                    SOCKET:org.xmlBlaster.protocol.socket.SocketDriver
 *
 *   Protocol.CallbackDrivers=IOR:org.xmlBlaster.protocol.corba.CallbackCorbaDriver,\
 *                            RMI:org.xmlBlaster.protocol.rmi.CallbackRmiDriver,\
 *                            SOCKET:org.xmlBlaster.protocol.socket.CallbackSocketDriver
 * </pre>
 *
 * The variable socket.port (default 7607) sets the socket server port,
 * you may change it in xmlBlaster.properties or on command line:
 * <pre>
 * java -jar lib/xmlBlaster.jar  -socket.port 9090
 * </pre>
 *
 * The interface I_Driver is needed by xmlBlaster to instantiate and shutdown
 * this driver implementation.
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>
 * @see org.xmlBlaster.protocol.socket.Parser
 */
public class SocketDriver extends Thread implements I_Driver
{
   private static final String ME = "SocketDriver";
   /** The singleton handle for this authentication server */
   private I_Authenticate authenticate = null;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl = null;
   /** Default port of xmlBlaster socket server is 7607 */
   public static final int DEFAULT_SERVER_PORT = 7607;
   /** The port for the socket server */
   private int socketPort = DEFAULT_SERVER_PORT;
   /** The socket server */
   private ServerSocket listen = null;
   /** The URL which clients need to use to access this server */
   private String serverUrl = null;
   /** The string representation like "192.168.1.1", useful if multihomed computer */
   private String hostname = null;
   /** xmlBlaster server host */
   private java.net.InetAddress inetAddr = null;
   /** State of server */
   private boolean running = true;


   /**
    * This static map is a hack!. We need this to map asynchronous update() to the correct socket
    * The key is the unique client loginName, the value is the HandleRequest instances belonging to this client.
    * <p />
    * TODO: Change loginName to sessionId when the new callback framework is available
    */
   private static final Map socketMap = Collections.synchronizedMap(new HashMap());


   /**
    * Note: getName() is enforced by interface I_Driver, but is already defined in Thread class
    */
   public SocketDriver()
   {
      super(ME);
   }

   I_Authenticate getAuthenticate() {
      return this.authenticate;
   }

   I_XmlBlaster getXmlBlaster() {
      return this.xmlBlasterImpl;
   }

   Map getSocketMap() {
      return this.socketMap;
   }


   /**
    * Start xmlBlaster SOCKET access.
    * <p />
    * Enforced by interface I_Driver.
    * @param args The command line parameters
    */
   public void init(String args[], I_Authenticate authenticate, I_XmlBlaster xmlBlasterImpl)
      throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering init()");
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;

      socketPort = XmlBlasterProperty.get("socket.port", 7607);

      if (socketPort < 1) {
         Log.info(ME, "Option socket.port set to " + socketPort + ", xmlRpc server not started");
         return;
      }

      //if (XmlBlasterProperty.get("socket.debug", false) == true)
      //   setDebug(true);

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

      start(); // Start the listen thread
   }



   /**
    * Starts the server socket
    */
   public void run()
   {
      try {
         int backlog = XmlBlasterProperty.get("socket.backlog", 50); // queue for max 50 incoming connection request
         listen = new ServerSocket(socketPort, backlog, inetAddr);
         Log.info(ME, "Started successfully socket driver, access hostname=" + hostname + " port=" + socketPort);
         while (running) {
            Socket accept = listen.accept();
            //Log.trace(ME, "New incoming request on port=" + socketPort + " ...");
            if (!running) {
               Log.info(ME, "Closing server " + hostname + " on port " + socketPort + ".");
               break;
            }
            HandleRequest hh = new HandleRequest(this, accept);
         }
      }
      catch (java.net.UnknownHostException e) {
         Log.error(ME, "Socket server problem, IP address '" + hostname + "' is invalid: " + e.toString());
      }
      catch (java.net.BindException e) {
         Log.error(ME, "Socket server problem, port " + hostname + ":" + socketPort + " is not available: " + e.toString());
      }
      catch (java.net.SocketException e) {
         Log.info(ME, "Socket " + hostname + ":" + socketPort + " closed successfully: " + e.toString());
      }
      catch (IOException e) {
         Log.error(ME, "Socket server problem on " + hostname + ":" + socketPort + ": " + e.toString());
      }

      if (listen != null) {
         try { listen.close(); } catch (java.io.IOException e) { Log.warn(ME, "listen.close()" + e.toString()); }
         listen = null;
      }
   }


   /**
    * Close the listener port
    */
   public void shutdown()// throws IOException
   {
      if (Log.CALL) Log.call(ME, "Entering shutdown");
      running = false;

      boolean closeHack = true;
      if (listen != null && closeHack) {
         // On some JDKs, listen.close() is not immediate (has a delay for about 1 sec.)
         // force closing by invoking server with this temporary client:
         try {
            java.net.Socket socket = new Socket(listen.getInetAddress(), socketPort);
            socket.close();
         } catch (java.io.IOException e) {
            Log.warn(ME, "shutdown problem: " + e.toString());
         }
      }

      try {
         if (listen != null) {
            listen.close();
            listen = null;
            Log.info(ME, "Socket driver stopped, all resources released.");
         }
      } catch (java.io.IOException e) {
         Log.warn(ME, "shutdown problem: " + e.toString());
      }
   }

   /**
    * Command line usage.
    * <p />
    * Enforced by interface I_Driver.
    */
   public String usage()
   {
      String text = "\n";
      text += "SocketDriver options:\n";
      text += "   -socket.port        The SOCKET web server port [7607].\n";
      text += "   -socket.hostname    Specify a hostname where the SOCKET web server runs.\n";
      text += "                       Default is the localhost.\n";
      text += "   -socket.backlog     Queue size for incmming connection request [50].\n";
      //text += "   -socket.debug       true switches on detailed SOCKET debugging [false].\n";
      text += "\n";
      return text;
   }
}



/**
 * Handles a request from a client, delivering the AuthServer IOR
 */
class HandleRequest extends Thread
{
   private String ME = "SocketDriverRequest";
   private SocketDriver driver;
   /** The singleton handle for this authentication server */
   private I_Authenticate authenticate;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl;
   private Socket sock;
   private boolean running = true;
   private InputStream iStream;
   private OutputStream oStream;


   /**
    */
   public HandleRequest(SocketDriver driver, Socket sock) throws IOException {
      this.sock = sock;
      this.driver = driver;
      this.authenticate = driver.getAuthenticate();
      this.xmlBlasterImpl = driver.getXmlBlaster();
      this.iStream = sock.getInputStream();
      this.oStream = sock.getOutputStream();
      start();
   }

   public void shutdown() {
      running = false;
   }

   public OutputStream getOutputStream() {
      return this.oStream;
   }

   /**
    * Serve a client
    */
   public void run() {
      if (Log.CALL) Log.call(ME, "Handling client request ...");
      Parser receiver = new Parser();
      try {
         Log.info(ME, "Client accepted ...");

         while (running) {

            try {
               receiver.parse(iStream);  // blocks until a message arrive

               Log.info(ME, "Received message '" + receiver.getMethodName() + "'");
               if (Log.DUMP) Log.dump(ME, "Receiving message >" + Parser.toLiteral(receiver.createRawMsg()) + "<");

               if (Constants.PUBLISH.equals(receiver.getMethodName())) {
                  //String response = xmlBlasterImpl.publish();
               }
               else if (Constants.GET.equals(receiver.getMethodName())) {
                  //MessageUnit[] arr = xmlBlasterImpl.get();
               }
               else if (Constants.PING.equals(receiver.getMethodName())) {
                  Log.info(ME, "Responding to ping");
               }
               else if (Constants.SUBSCRIBE.equals(receiver.getMethodName())) {
                  //String response = xmlBlasterImpl.subscribe();
               }
               else if (Constants.UNSUBSCRIBE.equals(receiver.getMethodName())) {
                  //String response = xmlBlasterImpl.unSubscribe();
               }
               else if (Constants.UPDATE.equals(receiver.getMethodName())) {
                  //String response = xmlBlasterImpl.update();
               }
               else if (Constants.ERASE.equals(receiver.getMethodName())) {
                  //String response = xmlBlasterImpl.erase();
               }
               else if (Constants.CONNECT.equals(receiver.getMethodName())) {
                  ConnectQos conQos = new ConnectQos(receiver.getQos());
                  driver.getSocketMap().put(conQos.getUserId(), this);
                  ConnectReturnQos retQos = authenticate.connect(conQos);
                  //socketMap.put(conQos.getSessionId(), this); // To late
                  Parser parser = new Parser(Parser.RESPONSE_TYPE, receiver.getRequestId(),
                                      receiver.getMethodName(), receiver.getSessionId());
                  parser.addQos(retQos.toXml());
                  oStream.write(parser.createRawMsg());
                  oStream.flush();
                }
               else if (Constants.DISCONNECT.equals(receiver.getMethodName())) {
                  String qos = authenticate.disconnect(receiver.getSessionId(), receiver.getQos());
                  Parser parser = new Parser(Parser.RESPONSE_TYPE, receiver.getRequestId(),
                                      receiver.getMethodName(), receiver.getSessionId());
                  parser.addQos(qos);
                  oStream.write(parser.createRawMsg());
                  oStream.flush();
               }
            }
            catch (XmlBlasterException e) {
               Log.error(ME, "Server can't handle message: " + e.toString());
               Parser parser = new Parser(Parser.EXCEPTION_TYPE, receiver.getRequestId(), receiver.getMethodName(), receiver.getSessionId());
               parser.setChecksum(false);
               parser.setCompressed(false);
               parser.addException(e);
               try {
                  oStream.write(parser.createRawMsg());
                  oStream.flush();
               }
               catch (Throwable e2) {
                  Log.error(ME, "Lost connection to client, can't deliver exception message: " + e2.toString());
                  try { authenticate.disconnect(receiver.getSessionId(), "<qos/>"); } catch(Throwable e3) { e3.printStackTrace(); }
                  shutdown();
               }
            }
            catch (IOException e) {
               Log.error(ME, "Lost connection to client: " + e.toString());
               try { authenticate.disconnect(receiver.getSessionId(), "<qos/>"); } catch(Throwable e3) { e3.printStackTrace(); }
               shutdown();
            }
            catch (Throwable e) {
               e.printStackTrace();
               Log.error(ME, "Lost connection to client: " + e.toString());
               try { authenticate.disconnect(receiver.getSessionId(), "<qos/>"); } catch(Throwable e3) { e3.printStackTrace(); }
               shutdown();
            }
         } // while(running)
      }
      finally {
         try { if (iStream != null) { iStream.close(); iStream=null; } } catch (IOException e) { Log.warn(ME+".shutdown", e.toString()); }
         try { if (oStream != null) { oStream.close(); oStream=null; } } catch (IOException e) { Log.warn(ME+".shutdown", e.toString()); }
         try { if (sock != null) { sock.close(); sock=null; } } catch (IOException e) { Log.warn(ME+".shutdown", e.toString()); }
      }
   }
}






