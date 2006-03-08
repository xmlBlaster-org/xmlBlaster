/*------------------------------------------------------------------------------
Name:      SocketGetter.java
Project:   xmlBlaster.org
Comment:   Code to get a message using a telnet.
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.socketgetter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;

/**
 * SocketGetter launches a xmlBlaster-client which 
 * listens on the given socket and delivers the
 * content of the oid given in a telnet session.
 * 
 * <p>
 * Usage:
 * <br/>
 * <code>
 * java org.xmlBlaster.client.socketgetter.SocketGetter -myPort &lt;port&gt;
 * </code>
 * </p>
 * 
 * <p>
 * Example for usage:
 * <br/>
 * <code>
 * java org.xmlBlaster.client.socketgetter.SocketGetter -myPort 9876 -plugin/socket/hostname server
 * </code>
 * <br/>
 * <code>
 * (sleep 1; echo "get __sys__UserList"; sleep 1) | telnet localhost 9876
 * </code>
 * <br/>
 * The host which the telnet connects to is the one which runns the SocketGetter.
 * This host may differ from the one running the xmlBaslter server, of course. 
 * <br/>
 * In this example, the xmlBlaster runns on the host called <b>server</b>, where at the SocketGetter
 * runns on the <b>localhost</b> on port 9876.
 * </p>
 * 
 * @author goetzger@xmlblaster.org
 * 
 */
public class SocketGetter {
   
   /** Holds the logger for this class. */
   private static Logger log = Logger.getLogger(SocketGetter.class.getName());

   /**
    * Starts the SocketGetter. 
    * The args must contain a valid port.
    * <br/>
    * This instance opens one connection to the xmlBlaster-server 
    * and starts a socket server on the given port.
    * Once a client has been accepted by the server, a new thread will be startet
    * for this client.
    * A new client may connect to the listening socket immediately afterwards.
    * <br/>
    * The socket-thread answers <b>one</b> get request and closes the connection right after
    * the request has been answered.
    * <br/>
    * The request must start with <b>get</b> otherwise no message will be delivered.
    * <p>
    * <code>java.util.logging.Level.FINER</code> is good for debugging purposes.
    * </p>
    */
   public static void main(String[] args) {

      try {
         /** Holds the connection to the xmlBlaster server. */
         I_XmlBlasterAccess xmlBlasterConnection;
         
         /** The util.Global instance for this client. */
         Global glob = new Global(args);

         /** The port where this client listens. */
         int port = glob.getProperty().get("myPort", 0);

         
         // establish a connection to xmlBlaster server
         xmlBlasterConnection = null;
         
         try {
            xmlBlasterConnection = glob.getXmlBlasterAccess();
            ConnectQos qos = new ConnectQos(glob);
            xmlBlasterConnection.connect(qos, null); // Login to xmlBlaster
         }
         catch (Exception e) {
            log.severe(e.toString());
            e.printStackTrace();
         }

         try {
            if (log.isLoggable(Level.INFO)) {
               log.info("Wait for connection on port " + port);
            }
            // start the socket server
            ServerSocket socketServer = new ServerSocket(port);
            while (true) {
               // wait for a new client
               Socket socket = socketServer.accept();
               // a client has been accepted, create a thread ...
               Thread thread = new SocketConnectorThread(glob, xmlBlasterConnection, socket);
               // ... and start it
               thread.start();
            }
         } catch (IOException ioe) {
            log.throwing(SocketGetter.class.getName(), "main", ioe);
            System.exit(1);
         } finally {
            if (log.isLoggable(Level.INFO)) {
               log.info("Close connection to xmlBlaster.");
            }
            xmlBlasterConnection.disconnect(null);
         }

      }
      catch (Exception e) {
         log.throwing(SocketGetter.class.getName(), "main", e);
      }
   }
}
   

/**
 * The socket thread which handles a single request from a client.
 * <br/>
 * The socket-thread answers <b>one</b> get request and closes the connection 
 * right after the request has been answered.
 * <br/>
 * The request must start with <b>get</b> otherwise no message will be delivered.
 * 
 * @author goetzger@xmlblaster.org
 * 
 */
class SocketConnectorThread extends Thread {

   /** Holds the logger for this class. */
   private static Logger log = Logger.getLogger(SocketGetter.class.getName());

   /** The actual socket. */
   private Socket socket;
   
   /** The user global. */
   private Global glob;
   
   /** The connection to the xmlBlaster server. */
   private I_XmlBlasterAccess connection;

   /**
    * Creates the thread.
    * @param glob the user global
    * @param connection the connection to the xmlBlaster server.
    * @param socket the accepted socket for tha actual (telnet) client.
    */
   public SocketConnectorThread(final Global glob, final I_XmlBlasterAccess connection, final Socket socket) {
      this.socket = socket;
      this.glob = glob;
      this.connection = connection;
   }

   /**
    * Starts the actual thread.
    */
   public void run() {
      if (log.isLoggable(Level.FINER)) {
         log.finer("Connection established");
      }
      
      // parses the oid from the request
      final String oid = getOID();
      if (oid == null) {
         // no oid is given ...
         log.severe("No oid given, abort!");

         try {
            if (log.isLoggable(Level.FINER)) {
               log.finer("Connection will be closed.");
            }
            // ... close this connection now!
            socket.close();
         } catch (IOException e) {
            log.throwing(this.getClass().getName(), "run", e);
         }

      }

      MsgUnit[] msgs = null;

      try {
         // starts the get request for the given oid
         msgs = connection.get(new GetKey(glob, oid, Constants.EXACT).toXml(), new GetQos(glob).toXml());
      } catch (XmlBlasterException xe) {
         log.throwing(this.getClass().getName(), "run", xe);
      }
      
      if (msgs == null || msgs.length < 1) {
         // if no message is given ...
         log.finer("No message given for oid [" + oid + "] => abort.");

         try {
            if (log.isLoggable(Level.FINER)) {
               log.finer("Connection will be closed.");
            }
            // ... close this connection now!
            socket.close();
         } catch (IOException e) {
            log.throwing(this.getClass().getName(), "run", e);
         }

      }
      
      if (log.isLoggable(Level.FINER)) {
         log.finer("Got " + msgs.length + " messages for '" + oid + "'");

         for (int ii=0; ii<msgs.length; ii++) {
            log.finer(ii + ": [" + msgs[ii].toXml() + "]");
         }
      }
      

      // create a convenience writer to the socket outputstream
      BufferedWriter bw = null;
      try {
         bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
      } catch (IOException e) {
         log.throwing(this.getClass().getName(), "run", e);
      }

      try {
         // obtain the first! message, given in the message ...
         // ... and write it to the client
         if (msgs != null && msgs.length > 0) {
            bw.write(msgs[0].toXml());
         }
            
         bw.flush();
         bw.close();
      } catch (IOException e) {
         log.throwing(this.getClass().getName(), "run", e);
      }
      
      // close the clients socket connection
      try {
         if (log.isLoggable(Level.FINER)) {
            log.finer("Connection will be closed.");
         }
         socket.close();
      } catch (IOException e) {
         log.throwing(this.getClass().getName(), "run", e);
      }
   }

   /**
    * Extract the oid from the given request 
    * out of the clients socket input stream.
    * The request must start with <b>get</b> otherwise null will be returned.
    * @return An oid or null, if no oid was given.
    */
   private final String getOID() {
      String oid = null;
      
      InputStream is = null;

      // get the input stream from the clients socket
      try {
         is = socket.getInputStream();
      } catch (IOException e) {
         log.throwing(SocketGetter.class.getName(), "getKey", e);
      }

      // create a conveninece reader
      BufferedReader br = new BufferedReader(new InputStreamReader(is) );

      String line = null;
      try {
         // read the first! line
         line = br.readLine();
      } catch (IOException e) {
         log.throwing(SocketGetter.class.getName(), "getKey", e);
      }

      // if no line is given, retrun null
      if (line == null) {
         log.severe("EOF reached");
         return null;
      }
      
      StringTokenizer st = new StringTokenizer(line);
      
      // check for at least 2 arguments in the straeam
      if (st.countTokens() < 2) {
         log.severe("To few arguments in String [" + line + "]");
         return null;
      }
      // if the request starts with "get", get the next token for the oid
      if (st.nextToken().equalsIgnoreCase("get")) {
         oid = st.nextToken();
      }
      
      if (log.isLoggable(Level.FINER)) {
         log.finer("key is [" + oid + "]");
      }

      return oid;
   }
}
