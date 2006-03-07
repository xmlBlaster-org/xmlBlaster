/*------------------------------------------------------------------------------
Name:      SocketGetter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.reader;

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

/**
 * SocketGetter
 * <p>
 * Example for usage:
 * </p>
 * <p>
 * <tt>
 * java org.xmlBlaster.client.script.SocketGetter -myPort <port>
 * </tt>
 * </p>
 * Launches a client which runns on the given socket and delivers the
 * content of the oid given in an telnet session:
 * (sleep 1; echo "get <oid>"; sleep 1) | telnet <host> <port>
 */
public class SocketGetter {
   
   private static Logger log = Logger.getLogger(SocketGetter.class.getName());

   public static void main(String[] args) {

      I_XmlBlasterAccess xmlBlasterConnection;

      try {
         Global glob = new Global(args);

         int port = glob.getProperty().get("myPort", 0);
         
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
            ServerSocket socketServer = new ServerSocket(port);
            while (true) {
               Socket socket = socketServer.accept();
               Thread thread = new SocketConnectorThread(glob, xmlBlasterConnection, socket);
               thread.start();
            }
         } catch (IOException e) {
            log.throwing(SocketGetter.class.getName(), "main", e);
            System.exit(1);
         } finally {
            if (log.isLoggable(Level.INFO)) {
               log.info("Close connection to xmlBlaster");
            }
            xmlBlasterConnection.disconnect(null);
         }

      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }
}
   

class SocketConnectorThread extends Thread {
   private static Logger log = Logger.getLogger(SocketGetter.class.getName());

   private Socket socket;
   private Global glob;
   private I_XmlBlasterAccess connection;

   public SocketConnectorThread(final Global glob, final I_XmlBlasterAccess connection, final Socket socket) {
      this.socket = socket;
      this.glob = glob;
      this.connection = connection;
   }

   public void run() {
      if (log.isLoggable(Level.INFO)) {
         log.info("Connection established");
      }
      
      String xmlKey = getKey();
      if (xmlKey == null) {
         log.severe("No key given, abbort!");

         try {
            if (log.isLoggable(Level.INFO)) {
               log.info("Connection will be closed.");
            }
            socket.close();
         } catch (IOException e) {
            log.throwing(this.getClass().getName(), "run", e);
         }

      }
      GetKey xmlKeyWr = null;

      try {
         xmlKeyWr = new GetKey(glob, xmlKey, "EXACT");
      } catch (XmlBlasterException xe) {
         log.throwing(this.getClass().getName(), "run", xe);
      }

      GetQos xmlQos = new GetQos(glob);
      MsgUnit[] msgs = null;

      try {
         msgs = connection.get(xmlKeyWr.toXml(), xmlQos.toXml());
      } catch (XmlBlasterException xe) {
         log.throwing(this.getClass().getName(), "run", xe);
      }
      if (log.isLoggable(Level.FINER)) {
         log.finer("Got " + msgs.length + " messages for '" + xmlKey + "'");
      }
      for (int ii=0; ii<msgs.length; ii++) {
         if (log.isLoggable(Level.FINER)) {
            log.finer(ii + ": [" + msgs[ii].toXml() + "]");
         }
      }
      

      BufferedWriter bw = null;
      try {
         bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
      } catch (IOException e) {
         log.throwing(this.getClass().getName(), "run", e);
      }

      try {
         bw.write(msgs[0].toXml());
         bw.flush();
         bw.close();
      } catch (IOException e) {
         log.throwing(this.getClass().getName(), "run", e);
      }

      
      try {
         if (log.isLoggable(Level.INFO)) {
            log.info("Connection will be closed.");
         }
         socket.close();
      } catch (IOException e) {
         log.throwing(this.getClass().getName(), "run", e);
      }
   }

   private final String getKey() {
      String xmlKey = null;
      
      InputStream is = null;

      try {
         is = socket.getInputStream();
      } catch (IOException e) {
         log.throwing(SocketGetter.class.getName(), "getKey", e);
      }

      BufferedReader br = new BufferedReader(new InputStreamReader(is) );

      String line = null;
      try {
         line = br.readLine();
      } catch (IOException e) {
         log.throwing(SocketGetter.class.getName(), "getKey", e);
      }
      
      if (line == null) {
         log.severe("EOF reached");
         return null;
      }
      
      StringTokenizer st = new StringTokenizer(line);
      if (st.countTokens() < 2) {
         log.severe("To few arguments in String [" + line + "]");
         return null;
      }
      if (st.nextToken().equalsIgnoreCase("get")) {
         xmlKey = st.nextToken();
      }
      
      if (log.isLoggable(Level.FINER)) {
         log.finer("key is [" + xmlKey + "]");
      }

      return xmlKey;
   }
}


