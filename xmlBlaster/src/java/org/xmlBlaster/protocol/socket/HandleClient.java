/*------------------------------------------------------------------------------
Name:      HandleClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   HandleClient class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: HandleClient.java,v 1.13 2002/03/13 16:41:30 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.queue.MsgQueueEntry;
import org.xmlBlaster.client.protocol.ConnectionException;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * Holds one socket connection to a client and handles
 * all requests from one client with plain socket messaging. 
 * 
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
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
    * Creates an instance which serves exactly one client. 
    */
   public HandleClient(SocketDriver driver, Socket sock) throws IOException {
      super(sock, driver.getXmlBlaster(), null);
      this.driver = driver;
      this.authenticate = driver.getAuthenticate();
      this.SOCKET_DEBUG = driver.SOCKET_DEBUG;
      Thread t = new Thread(this, "XmlBlaster.SOCKET.HandleClient");
      t.setPriority(XmlBlasterProperty.get("socket.threadPrio", Thread.NORM_PRIORITY));
      t.start();
   }

   /**
    * Close connection for one specific client
    */
   public void shutdown() {
      if (Log.TRACE || SOCKET_DEBUG>0) Log.info(ME, "Schutdown connection ...");
      if (sessionId != null) {
         String tmp = sessionId;
         sessionId = null;
         try {
            authenticate.disconnect(tmp, "<qos/>");
         }
         catch(Throwable e) {
            Log.warn(ME, e.toString());
            e.printStackTrace();
         }
      }
      running = false;
      if (responseListenerMap.size() > 0)
         Log.warn(ME, "There are " + responseListenerMap.size() + " messages pending without a response");
   }

   /**
    * Updating multiple messages in one sweep, callback to client. 
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] sendUpdate(String cbSessionId, MsgQueueEntry[] msg)
      throws XmlBlasterException, ConnectionException
   {
      if (Log.CALL) Log.call(ME, "Entering update: id=" + cbSessionId);

      if (msg == null || msg.length < 1) {
         Log.error(ME + ".InvalidArguments", "The argument of method update() are invalid");
         throw new XmlBlasterException(ME + ".InvalidArguments",
                                       "The argument of method update() are invalid");
      }
      try {
         MessageUnit[] msgUnitArr = new MessageUnit[msg.length];
         for (int ii=0; ii<msg.length; ii++)
            msgUnitArr[ii] = msg[ii].getMessageUnit();

         Parser parser = new Parser(Parser.INVOKE_BYTE, Constants.UPDATE, cbSessionId);
         parser.addMessage(msgUnitArr);
         if (updateIsOneway) {
            if (warnUpdateIsOneway) {
               Log.info(ME, "blocking update() mode is currently switched of");
               warnUpdateIsOneway = false;
            }
            execute(parser, ONEWAY);
            String[] arr = new String[msg.length];
            for (int jj=0; jj<msg.length; jj++)
               arr[jj] = "<qos><state>OK</state></qos>";
            return arr;
         }
         else {
            Object response = execute(parser, WAIT_ON_RESPONSE);
            if (Log.TRACE || SOCKET_DEBUG>0) Log.info(ME, "Got update response " + response.toString());
            return (String[])response; // return the QoS
         }
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
      receiver.SOCKET_DEBUG = SOCKET_DEBUG;

      try {
         if (Log.TRACE) Log.trace(ME, "Client accepted, coming from host=" + sock.getInetAddress().toString() + " port=" + sock.getPort());

         while (running) {
            try {
               //iStream = sock.getInputStream();
               receiver.parse(iStream);  // blocks until a message arrive

               if (Log.TRACE || SOCKET_DEBUG>0) Log.info(ME, "Receiving message " + receiver.getMethodName() + "(" + receiver.getRequestId() + ")");
               if (SOCKET_DEBUG>1) Log.info(ME, "Receiving message >" + Parser.toLiteral(receiver.createRawMsg()) + "<");

               // receive() processes all invocations, only connect()/disconnect() we do locally ...
               if (receive(receiver) == false) {
                  if (Constants.CONNECT.equals(receiver.getMethodName())) {
                     ConnectQos conQos = new ConnectQos(receiver.getQos());
                     setLoginName(conQos.getUserId());
                     this.ME += "-" + this.loginName;
                     Log.info(ME, "Client accepted, coming from host=" + sock.getInetAddress().toString() + " port=" + sock.getPort());
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
                     authenticate.disconnect(receiver.getSessionId(), receiver.getQos());
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
         try { if (iStream != null) { iStream.close(); iStream=null; } } catch (IOException e) { Log.warn(ME+".shutdown", e.toString()); }
         try { if (oStream != null) { oStream.close(); oStream=null; } } catch (IOException e) { Log.warn(ME+".shutdown", e.toString()); }
         try { if (sock != null) { sock.close(); sock=null; } } catch (IOException e) { Log.warn(ME+".shutdown", e.toString()); }
         if (Log.TRACE || SOCKET_DEBUG>0) Log.info(ME, "Deleted thread for '" + loginName + "'.");
      }
   }
}

