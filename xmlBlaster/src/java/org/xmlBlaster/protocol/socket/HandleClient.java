/*------------------------------------------------------------------------------
Name:      HandleClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   HandleClient class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: HandleClient.java,v 1.6 2002/02/16 11:23:21 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.client.protocol.ConnectionException;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedOutputStream;


/**
 * Handles a request from a client, delivering the AuthServer IOR
 */
public class HandleClient extends Executor implements Runnable
{
   private String ME = "HandleClientRequest";
   private SocketDriver driver;
   /** The singleton handle for this authentication server */
   private I_Authenticate authenticate;
   private CallbackSocketDriver callback;
   private boolean running = true;


   /**
    */
   public HandleClient(SocketDriver driver, Socket sock) throws IOException {
      super(sock, driver.getXmlBlaster(), null);
      this.driver = driver;
      this.authenticate = driver.getAuthenticate();
      Thread t = new Thread(this);
      t.start();
   }

   /**
    * Close connection for one specific client
    */
   public void shutdown() {
      Log.info(ME, "Schutdown connection ...");
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

   /**
    * Updating multiple messages in one sweep.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, MessageUnit[] msgUnitArr)
      throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "Entering update: id=" + sessionId);

      if (msgUnitArr == null) {
         Log.error(ME + ".InvalidArguments", "The argument of method update() are invalid");
         throw new XmlBlasterException(ME + ".InvalidArguments",
                                       "The argument of method update() are invalid");
      }
      try {
         Parser parser = new Parser(Parser.INVOKE_TYPE, Constants.UPDATE, sessionId);
         parser.addMessage(msgUnitArr);
         Object response = execute(parser, WAIT_ON_RESPONSE);
         Log.info(ME, "Got update response " + response.toString());
         String[] arr = (String[])response; // return the QoS
         return arr[0]; // Hack until every update uses arrays (one qos for each message
      }
      catch (IOException e1) {
         Log.error(ME+".update", "IO exception: " + e1.toString());
         throw new ConnectionException(ME+".update", e1.toString());
      }
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

               if (receive(receiver) == false) {
                  if (Constants.CONNECT.equals(receiver.getMethodName())) {
                     ConnectQos conQos = new ConnectQos(receiver.getQos());
                     setLoginName(conQos.getUserId());
                     this.ME += "-" + this.loginName;
                     callback = new CallbackSocketDriver(this.loginName, this);
                     conQos.setCallbackDriver(callback);  // tell that we are the callback driver as well (see hack in CbInfo.java)

                     ConnectReturnQos retQos = authenticate.connect(conQos);
                     this.sessionId = retQos.getSessionId();
                     receiver.setSessionId(retQos.getSessionId()); // executeResponse needs it

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
            }
            catch (XmlBlasterException e) {
               Log.error(ME, "Server can't handle message: " + e.toString());
               try {
                  executeExecption(receiver, e);
               }
               catch (Throwable e2) {
                  Log.error(ME, "Lost connection, can't deliver exception message: " + e.toString() + " Reason is: " + e2.toString());
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

