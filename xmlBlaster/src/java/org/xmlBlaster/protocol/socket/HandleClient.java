/*------------------------------------------------------------------------------
Name:      HandleClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   HandleClient class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: HandleClient.java,v 1.3 2002/02/15 19:06:54 ruff Exp $
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

   public String sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, MessageUnit[] msgUnitArr) throws XmlBlasterException {
      Log.error(ME, "sendUpdate() not implemented");
      // Same code as publishArr()
         /*
      try {
         Parser receiver = new Parser(Parser.INVOKE_TYPE, Constants.UPDATE, clientInfo.getSessionId());
         receiver.addMessage(msgUnitArr);
         Object response = execute(receiver, oStream, clientInfo.getLoginName(), true);
         Log.info(ME, "Got publishArr response " + response.toString());
         return (String[])response; // return the QoS
      }
      catch (IOException e1) {
         Log.error(ME+".publishArr", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".publishArr", e1.toString());
      }
      Log.info(ME, "Successful sent response for " + receiver.getMethodName() + "()");
         */
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

               Log.info(ME, "Receiving message " + receiver.getMethodName() + "()");
               if (Log.DUMP) Log.dump(ME, "Receiving message >" + Parser.toLiteral(receiver.createRawMsg()) + "<");

               if (Constants.PUBLISH.equals(receiver.getMethodName())) {
                  MessageUnit[] arr = receiver.getMessageArr();
                  if (arr == null || arr.length < 1)
                     throw new XmlBlasterException(ME, "Invocation of " + receiver.getMethodName() + "() failed, missing arguments");
                  String[] response = xmlBlasterImpl.publishArr(receiver.getSessionId(), arr);
                  executeResponse(receiver, response);
               }
               else if (Constants.GET.equals(receiver.getMethodName())) {
                  MessageUnit[] arr = receiver.getMessageArr();
                  if (arr == null || arr.length != 1)
                     throw new XmlBlasterException(ME, "Invocation of " + receiver.getMethodName() + "() failed, wrong arguments");
                  MessageUnit[] response = xmlBlasterImpl.get(receiver.getSessionId(), arr[0].getXmlKey(), arr[0].getQos());
                  executeResponse(receiver, response);
               }
               else if (Constants.PING.equals(receiver.getMethodName())) {
                  executeResponse(receiver, "<qos><state>OK</state></qos>");
               }
               else if (Constants.SUBSCRIBE.equals(receiver.getMethodName())) {
                  MessageUnit[] arr = receiver.getMessageArr();
                  if (arr == null || arr.length != 1)
                     throw new XmlBlasterException(ME, "Invocation of " + receiver.getMethodName() + "() failed, wrong arguments");
                  String response = xmlBlasterImpl.subscribe(receiver.getSessionId(), arr[0].getXmlKey(), arr[0].getQos());
                  executeResponse(receiver, response);
               }
               else if (Constants.UNSUBSCRIBE.equals(receiver.getMethodName())) {
                  MessageUnit[] arr = receiver.getMessageArr();
                  if (arr == null || arr.length != 1)
                     throw new XmlBlasterException(ME, "Invocation of " + receiver.getMethodName() + "() failed, wrong arguments");
                  xmlBlasterImpl.unSubscribe(receiver.getSessionId(), arr[0].getXmlKey(), arr[0].getQos());
                  // !!! TODO better return value?
                  executeResponse(receiver, "<qos><state>OK</state></qos>");
               }
               else if (Constants.UPDATE.equals(receiver.getMethodName())) {
                  throw new XmlBlasterException(ME, "Method " + receiver.getMethodName() + "() is not supported");
                  //String response = xmlBlasterImpl.update();
               }
               else if (Constants.ERASE.equals(receiver.getMethodName())) {
                  MessageUnit[] arr = receiver.getMessageArr();
                  if (arr == null || arr.length != 1)
                     throw new XmlBlasterException(ME, "Invocation of " + receiver.getMethodName() + "() failed, wrong arguments");
                  String[] response = xmlBlasterImpl.erase(receiver.getSessionId(), arr[0].getXmlKey(), arr[0].getQos());
                  executeResponse(receiver, response);
               }
               else if (Constants.CONNECT.equals(receiver.getMethodName())) {
                  
                  ConnectQos conQos = new ConnectQos(receiver.getQos());
                  this.loginName = conQos.getUserId();
                  this.ME += "-" + this.loginName;
                  callback = new CallbackSocketDriver(this.loginName, this);
                  conQos.setCallbackDriver(callback);  // tell that we are the callback driver as well (see hack in CbInfo.java)

                  driver.getSocketMap().put(conQos.getUserId(), this);

                  ConnectReturnQos retQos = authenticate.connect(conQos);
                  this.sessionId = retQos.getSessionId();
                  receiver.setSessionId(retQos.getSessionId()); // executeResponse needs it

                  //driver.getSocketMap().put(retQos.getSessionId(), this); // To late

                  executeResponse(receiver, retQos.toXml());
                }
               else if (Constants.DISCONNECT.equals(receiver.getMethodName())) {
                  this.sessionId = null;
                  // Note: the diconnect will call over the CbInfo our shutdown as well
                  // setting sessionId = null prevents that our shutdown calls disconnect() again.
                  String qos = authenticate.disconnect(receiver.getSessionId(), receiver.getQos());
                  //executeResponse(receiver, qos);   // The socket is closed already
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


   /**
    * Send a message back to client
    */
   private void executeResponse(Parser receiver, Object response) throws XmlBlasterException, IOException {
      Parser returner = new Parser(Parser.RESPONSE_TYPE, receiver.getRequestId(),
                           receiver.getMethodName(), receiver.getSessionId());
      if (response instanceof String)
         returner.addMessage((String)response);
      else if (response instanceof String[])
         returner.addMessage((String[])response);
      else if (response instanceof MessageUnit[])
         returner.addMessage((MessageUnit[])response);
      else if (response instanceof MessageUnit)
         returner.addMessage((MessageUnit)response);
      else
         throw new XmlBlasterException(ME, "Invalid response data type " + response.toString());
      if (Log.DUMP) Log.dump(ME, "Successful " + receiver.getMethodName() + "(), sending back to client '" + Parser.toLiteral(returner.createRawMsg()) + "'");
      oStream.write(returner.createRawMsg());
      oStream.flush();
      Log.info(ME, "Successful sent response for " + receiver.getMethodName() + "()");
   }
}

