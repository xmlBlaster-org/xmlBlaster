/*------------------------------------------------------------------------------
Name:      SocketDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   SocketDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: SocketDriver.java,v 1.4 2002/02/14 22:53:37 ruff Exp $
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
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.Constants;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
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
public class SocketDriver extends Thread implements I_Driver, I_CallbackDriver
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
    * The key is the unique client loginName, the value is the HandleClient instances belonging to this client.
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
            HandleClient hh = new HandleClient(this, accept);
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
      finally {
         if (listen != null) {
            try { listen.close(); } catch (java.io.IOException e) { Log.warn(ME, "listen.close()" + e.toString()); }
            listen = null;
         }
      }
   }


   /**
    * Intialize the driver.
    * <p />
    * Enforced by I_CallbackDriver
    * @param  callbackAddress Contains the callback address,
    *         e.g. the stringified CORBA callback handle of the client or his email address.
    */
   public void init(CallbackAddress callbackAddress) throws XmlBlasterException {
      Log.error(ME, "Implement init()");
   }


   /**
    * Send the message update to the client.
    * <p />
    * The protocol for sending is implemented in the derived class
    * <p />
    * Enforced by I_CallbackDriver
    *
    * @param clientInfo Data about a specific client
    * @param msgUnitWrapper For Logoutput only (deprecated?)
    * @param messageUnitArr Array of all messages to send
    * @return Clients should return a qos as follows.
    *         An empty qos string "" is valid as well and
    *         interpreted as OK
    * <pre>
    *  &lt;qos>
    *     &lt;state>       &lt;!-- Client processing state -->
    *        OK            &lt;!-- OK | ERROR -->
    *     &lt;/state>
    *  &lt;/qos>
    * </pre>
    * @exception On callback problems you need to throw a XmlBlasterException e.id="CallbackFailed",
    *            the message will queued until the client logs in again
    */
   public String sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, MessageUnit[] messageUnitArr) throws XmlBlasterException {
      Log.error(ME, "Implement sendUpdate()");
      return "";
   }


   /**
    * Close the listener port
    */
   public void shutdown()// throws IOException
   {
      if (Log.CALL) Log.call(ME, "Entering shutdown");
      
      //System.out.println(org.jutils.runtime.StackTrace.getStackTrace());

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

      socketMap.clear();

      Log.info(ME, "Socket driver stopped, all resources released bye.");
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
