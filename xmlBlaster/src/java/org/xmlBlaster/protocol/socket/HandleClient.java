/*------------------------------------------------------------------------------
Name:      HandleClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   HandleClient class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: HandleClient.java,v 1.1 2002/02/14 22:53:37 ruff Exp $
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
 * Handles a request from a client, delivering the AuthServer IOR
 */
public class HandleClient extends Thread
{
   private String ME = "HandleClientRequest";
   private SocketDriver driver;
   /** The singleton handle for this authentication server */
   private I_Authenticate authenticate;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl;
   private Socket sock;
   private CallbackSocketDriver callback;
   private boolean running = true;
   private InputStream iStream;
   private OutputStream oStream;
   private String sessionId = null;
   private String loginName = "";


   /**
    */
   public HandleClient(SocketDriver driver, Socket sock) throws IOException {
      this.sock = sock;
      this.driver = driver;
      this.authenticate = driver.getAuthenticate();
      this.xmlBlasterImpl = driver.getXmlBlaster();
      this.oStream = sock.getOutputStream();
      this.iStream = sock.getInputStream();
      start();
   }

   /**
    * Close connection for one specific client
    */
   public void shutdown() {
      Log.info(ME, "Schutdown connection ...");
      driver.getSocketMap().remove(this.loginName);
      if (sessionId != null) {
         try {
            authenticate.disconnect(sessionId, "<qos/>");
         }
         catch(Throwable e) {
            Log.warn(ME, e.toString());
            e.printStackTrace();
         }
      }
      running = false;
   }

   public String sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, MessageUnit[] messageUnitArr) throws XmlBlasterException {
      Log.info(ME, "sendUpdate ...");
      return "";
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
               //iStream = sock.getInputStream();
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
                  conQos.setCallbackDriver(driver);  // tell that we are the callback driver as well (see hack in CbInfo.java)
                  this.loginName = conQos.getUserId();
                  this.ME += "-" + this.loginName;
                  callback = new CallbackSocketDriver(this.loginName, this);

                  driver.getSocketMap().put(conQos.getUserId(), this);

                  ConnectReturnQos retQos = authenticate.connect(conQos);

                  //driver.getSocketMap().put(retQos.getSessionId(), this); // To late
                  this.sessionId = retQos.getSessionId();
                  Parser returner = new Parser(Parser.RESPONSE_TYPE, receiver.getRequestId(),
                                      receiver.getMethodName(), retQos.getSessionId());
                  returner.addQos(retQos.toXml());
                  Log.info(ME, "Successful login sending return QoS back to client '" + Parser.toLiteral(returner.createRawMsg()) + "'");
                  //oStream = sock.getOutputStream();
                  oStream.write(returner.createRawMsg());
                  oStream.flush();
                  Log.info(ME, "Successful sent");
                }
               else if (Constants.DISCONNECT.equals(receiver.getMethodName())) {
                  String qos = authenticate.disconnect(receiver.getSessionId(), receiver.getQos());
                  Parser returner = new Parser(Parser.RESPONSE_TYPE, receiver.getRequestId(),
                                      receiver.getMethodName(), receiver.getSessionId());
                  returner.addQos(qos);
                  oStream.write(returner.createRawMsg());
                  oStream.flush();
                  this.sessionId = null;
                  shutdown();
               }
            }
            catch (XmlBlasterException e) {
               Log.error(ME, "Server can't handle message: " + e.toString());
               Parser returner = new Parser(Parser.EXCEPTION_TYPE, receiver.getRequestId(), receiver.getMethodName(), receiver.getSessionId());
               returner.setChecksum(false);
               returner.setCompressed(false);
               returner.addException(e);
               try {
                  oStream.write(returner.createRawMsg());
                  oStream.flush();
               }
               catch (Throwable e2) {
                  Log.error(ME, "Lost connection to client, can't deliver exception message: " + e2.toString());
                  shutdown();
               }
            }
            catch (IOException e) {
               Log.error(ME, "Lost connection to client: " + e.toString());
               shutdown();
            }
            catch (Throwable e) {
               e.printStackTrace();
               Log.error(ME, "Lost connection to client: " + e.toString());
               shutdown();
            }
         } // while(running)
      }
      finally {
         Log.info(ME, "Schutdown thread ...");
         try { if (iStream != null) { iStream.close(); iStream=null; } } catch (IOException e) { Log.warn(ME+".shutdown", e.toString()); }
         try { if (oStream != null) { oStream.close(); oStream=null; } } catch (IOException e) { Log.warn(ME+".shutdown", e.toString()); }
         try { if (sock != null) { sock.close(); sock=null; } } catch (IOException e) { Log.warn(ME+".shutdown", e.toString()); }
      }
   }
}

