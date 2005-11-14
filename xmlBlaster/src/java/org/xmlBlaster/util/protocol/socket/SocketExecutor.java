/*------------------------------------------------------------------------------
Name:      SocketExecutor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Send/receive messages over outStream and inStream.
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol.socket;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.protocol.RequestReplyExecutor;
import org.xmlBlaster.util.protocol.ZBlockInputStream;
import org.xmlBlaster.util.protocol.ZBlockOutputStream;
import org.xmlBlaster.util.protocol.ZFlushInputStream;
import org.xmlBlaster.util.protocol.ZFlushOutputStream;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.util.xbformat.MsgInfo;
import org.xmlBlaster.util.def.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Send/receive messages over outStream and inStream.
 * <p />
 * A common base class for socket based messaging.
 * Allows to block during a request and deliver the return message
 * to the waiting thread.
 *
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html" target="others">xmlBlaster SOCKET access protocol</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public abstract class SocketExecutor extends RequestReplyExecutor
{
   private String ME = SocketExecutor.class.getName();
   private LogChannel log;
   /** Reading from socket */
   protected InputStream iStream;
   /** Writing to socket */
   protected OutputStream oStream;
   /** How long to block the socket on input stream read */
   protected long soTimeout = 0;
   /** How long to block the socket on close with remaining data */
   protected long soLingerTimeout = 0; // Constants.MINUTE_IN_MILLIS -> this can lead to blocking close(), so we choose '0'
   /** This is the client side */
   protected String loginName;

   public SocketExecutor() {
   }

   /**
    * Used by SocketCallbackImpl on client side, uses I_CallbackExtended to invoke client classes
    * <p />
    * Used by HandleClient on server side, uses I_XmlBlaster to invoke xmlBlaster core
    * <p />
    * This executor has mixed client and server specific code for two reasons:<br />
    * - Possibly we can use the same socket between two xmlBlaster server (load balance)<br />
    * - Everything is together<br />
    * @param iStream The reading stream (for example a socket InputStream)
    * @param oStream The writing stream (for example a socket OutputStream)
    */
   protected void initialize(Global glob, AddressBase addressConfig, InputStream iStream, OutputStream oStream) throws IOException {
      super.initialize(glob, addressConfig);
      this.log = this.glob.getLog("socket");

      if (isCompressZlibStream()) { // Statically configured for server side protocol plugin
         this.iStream = new ZFlushInputStream(iStream);
         this.oStream =  new ZFlushOutputStream(oStream);
      }
      else if (isCompressZlib()) { // Compress each message indiviually
         this.iStream = new ZBlockInputStream(iStream);
         this.oStream = new ZBlockOutputStream(oStream, (int)super.addressConfig.getMinSize());
      }
      else {
         this.iStream = iStream;
         this.oStream = oStream;
      }
   }

   /**
    * Sets the loginName and automatically the requestId as well
    */
   protected void setLoginName(String loginName) {
      super.setLoginName(loginName);
      this.loginName = loginName;
   }

   /**
    * Set the given millis to protect against blocking socket on input stream read() operations
    * @param millis If <= 0 it is disabled
    */
   public final void setSoTimeout(long millis) {
      if (millis < 0L) {
         log.warn(ME, this.addressConfig.getEnvLookupKey("SoTimeout") + "=" + millis +
                      " is invalid, is invalid, deactivating timeout");
         this.soTimeout = 0L;
      }
      this.soTimeout = millis;
   }

   public final long getSoTimeout() {
      return this.soTimeout;
   }

   /**
    * Set the given millis to timeout socket close if data are lingering
    * @param millis If < 0 it is set to one minute, 0 disable timeout
    */
   public final void setSoLingerTimeout(long millis) {
      if (millis < 0L) {
         log.warn(ME, this.addressConfig.getEnvLookupKey("SoLingerTimeout") + "=" + millis +
                      " is invalid, setting it to " + Constants.MINUTE_IN_MILLIS + " millis");
         this.soLingerTimeout = Constants.MINUTE_IN_MILLIS;
      }
      this.soLingerTimeout = millis;
   }

   public final long getSoLingerTimeout() {
      return this.soLingerTimeout;
   }

   public final OutputStream getOutputStream() {
      return this.oStream;
   }

   public final InputStream getInputStream() {
      return this.iStream;
   }

   /**
    * Flush the data to the socket. 
    * Overwrite this in your derived class to send UDP 
    */
   protected void sendMessage(MsgInfo msgInfo, String requestId, MethodName methodName, boolean udp) throws XmlBlasterException, IOException {
      byte[] msg = msgInfo.createRawMsg();
      I_ProgressListener listener = this.progressListener;
      if (listener != null) {
         listener.progressWrite("", 0, msg.length);
      }
      synchronized (oStream) {
         oStream.write(msg);
         oStream.flush();
         if (log.TRACE) log.trace(ME, "TCP data is send");
      }
      if (listener != null) {
         listener.progressWrite("", msg.length, msg.length);
      }
   }
}

