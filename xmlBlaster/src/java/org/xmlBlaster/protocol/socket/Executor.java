/*------------------------------------------------------------------------------
Name:      Executor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Executor class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: Executor.java,v 1.1 2002/02/15 21:04:44 ruff Exp $
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
 * Send/receive messages over outStream and inStream. 
 * <p />
 * A common base class for socket based messaging.
 * Allows to block during a request and deliver the return message
 * to the waiting thread.
 */
public class Executor
{
   private String ME = "ExecutorRequest";
   protected Socket sock;
   protected InputStream iStream;
   protected OutputStream oStream;
   protected String sessionId = null;
   protected String loginName = "";


   /**
    */
   protected Executor(Socket sock) throws IOException {
      this.sock = sock;
      this.oStream = sock.getOutputStream();
      this.iStream = sock.getInputStream();
   }

   public OutputStream getOutputStream() {
      return this.oStream;
   }

   public InputStream getInputStream() {
      return this.iStream;
   }

   /**
    * Send a message back to client
    */
   protected void executeResponse(Parser receiver, Object response) throws XmlBlasterException, IOException {
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

