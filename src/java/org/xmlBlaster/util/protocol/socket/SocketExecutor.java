/*------------------------------------------------------------------------------
Name:      SocketExecutor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Send/receive messages over outStream and inStream.
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol.socket;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.protocol.RequestReplyExecutor;
import org.xmlBlaster.util.protocol.ZBlockInputStream;
import org.xmlBlaster.util.protocol.ZBlockOutputStream;
import org.xmlBlaster.util.protocol.ZFlushInputStream;
import org.xmlBlaster.util.protocol.ZFlushOutputStream;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.util.xbformat.MsgInfo;
import org.xmlBlaster.util.xbformat.XbfParser;
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
public abstract class SocketExecutor extends RequestReplyExecutor implements SocketExecutorMBean
{
   private String ME = SocketExecutor.class.getName();
   private static Logger log = Logger.getLogger(SocketExecutor.class.getName());
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
   /** Which message format parser to use */
   protected String msgInfoParserClassName;
   /**
    *  Set to true if a XmlScript is send.If scripts used over socket we need to terminate each script with a null byte
    */
   private boolean isNullTerminated;

   private final static int MAX_CHUNKSIZE_DEFAULT = 50 * 1024;
   protected int maxChunkSize = MAX_CHUNKSIZE_DEFAULT;

   protected boolean running = true;

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

      this.isNullTerminated = addressConfig.getEnv("isNullTerminated", false).getValue();
      this.maxChunkSize = addressConfig.getEnv("maxChunkSize", MAX_CHUNKSIZE_DEFAULT).getValue();
      if (log.isLoggable(Level.FINE)) log.fine("Max chunk size is '" + this.maxChunkSize + "'");
      if (isCompressZlibStream()) { // Statically configured for server side protocol plugin
         this.iStream = new ZFlushInputStream(iStream);
         this.oStream =  new ZFlushOutputStream(oStream);
      }
      else if (isCompressZlib()) { // Compress each message indiviually
         this.iStream = new ZBlockInputStream(iStream);
         this.oStream = new ZBlockOutputStream(oStream, getMinSizeForCompression());
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
    * Which parser to use.
    * The SOCKET protocol uses as a default setting the XbfParser
    * @return The class name of the parser, "org.xmlBlaster.util.xbformat.XbfParser"
    */
   public String getMsgInfoParserClassName() {
      if (this.msgInfoParserClassName == null) {
         this.msgInfoParserClassName = this.addressConfig.getEnv("parserClass", XbfParser.class.getName()).getValue();
      }
      return this.msgInfoParserClassName; // "org.xmlBlaster.util.xbformat.XbfParser"
   }

   public String getCbMsgInfoParserClassName() {
      return getMsgInfoParserClassName();
   }

   /**
    * Set the given millis to protect against blocking socket on input stream read() operations
    * @param millis If <= 0 it is disabled
    */
   public final void setSoTimeout(long millis) {
      if (millis < 0L) {
         log.warning(this.addressConfig.getEnvLookupKey("SoTimeout") + "=" + millis +
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
         log.warning(this.addressConfig.getEnvLookupKey("SoLingerTimeout") + "=" + millis +
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
   
   public void setRunning(boolean run) {
      this.running = run;
   }


   /**
    * Updating multiple messages in one sweep, callback to client.
    * <p />
    * @param expectingResponse is WAIT_ON_RESPONSE or ONEWAY
    * @return null if oneway
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] sendUpdate(String cbSessionId, MsgUnitRaw[] msgArr,
         boolean expectingResponse, boolean useUdpForOneway, PluginInfo callbackPluginInfo) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering update: id=" + cbSessionId + " numSend=" + msgArr.length + " oneway=" + !expectingResponse);
      if (!running)
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "update() invocation ignored, we are shutdown.");

      if (msgArr == null || msgArr.length < 1) {
         log.severe("The argument of method update() are invalid");
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdate() argument");
      }
      try {
         if (expectingResponse) {
            MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.UPDATE, 
                         cbSessionId, progressListener, getCbMsgInfoParserClassName());
            parser.setPluginConfig(callbackPluginInfo); // is usually null as it is loaded dynamically
            parser.addMessage(msgArr);
            Object response = requestAndBlockForReply(parser, SocketExecutor.WAIT_ON_RESPONSE, false);
            if (log.isLoggable(Level.FINE)) log.fine("Got update response " + response.toString());
            return (String[])response; // return the QoS
         }
         else {
            MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.UPDATE_ONEWAY,
                  cbSessionId, progressListener, getCbMsgInfoParserClassName());
            parser.setPluginConfig(callbackPluginInfo);
            parser.addMessage(msgArr);
            requestAndBlockForReply(parser, SocketExecutor.ONEWAY, useUdpForOneway);
            return null;
         }
      }
      catch (XmlBlasterException e) {
         throw XmlBlasterException.tranformCallbackException(e);
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.fine("IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
               "Callback of " + msgArr.length + " messages failed", e1);
      }
   }

   /**
    * Ping to check if callback server (or server protocol) is alive.
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    * @exception XmlBlasterException If client not reachable
    */
   public String ping(String qos) throws XmlBlasterException
   {
      if (!running)
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "ping() invocation ignored, we are shutdown.");
      try {
         String cbSessionId = "";
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.PING, cbSessionId, progressListener, getMsgInfoParserClassName());
         parser.addMessage(qos);
         Object response = requestAndBlockForReply(parser, SocketExecutor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
         if (log.isLoggable(Level.FINE)) log.fine("Got ping response " + ((response == null) ? "null" : response.toString()));
         return (String)response; // return the QoS
      } catch (Throwable e) {
         boolean weAreOnServerSide = getXmlBlasterCore() != null; //// TODO !!!!!!!!!!!
         String txt = weAreOnServerSide ? "Callback ping failed" :  MethodName.PING.toString();
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, txt, e);
      }
   }


   /**
    * Flush the data to the socket.
    * Overwrite this in your derived class to send UDP
    */
   protected void sendMessage(MsgInfo msgInfo, String requestId, MethodName methodName, boolean udp) throws XmlBlasterException, IOException {
      I_ProgressListener listener = this.progressListener;
      try {
         // TODO: On server side the msgInfoParserClassName should be from CbSocketDriver configuration
         byte[] msg = msgInfo.createRawMsg(getCbMsgInfoParserClassName());
         if (log.isLoggable(Level.FINEST)) log.finest("Sending TCP data of length " + msg.length + " >" + XbfParser.createLiteral(msg) + "<");
         if (listener != null) {
            listener.progressWrite("", 0, msg.length);
         }
         else
            log.fine("The progress listener is null");

         int bytesLeft = msg.length;
         int bytesRead = 0;
         if (oStream == null)
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "sendMessage() invocation ignored, we are shutdown.");
         synchronized (oStream) {
            while (bytesLeft > 0) {
               int toRead = bytesLeft > this.maxChunkSize ? this.maxChunkSize : bytesLeft;
               oStream.write(msg, bytesRead, toRead);
               oStream.flush();
               bytesRead += toRead;
               bytesLeft -= toRead;
               if (listener != null)
                  listener.progressWrite("", bytesRead, msg.length);
            }
            if (this.isNullTerminated) { // If using XmlScriptInterpreter as parserClass, we finish each script with a null byte
               oStream.write(0);
               oStream.flush();
            }
            if (log.isLoggable(Level.FINE)) log.fine("TCP data is send");
         }
         if (listener != null) {
            listener.progressWrite("", msg.length, msg.length);
         }

      }
      catch (IOException ex) {
         if (listener != null) {
            listener.clearCurrentWrites();
            listener.clearCurrentReads();
         }
         throw ex;
      }
      catch (XmlBlasterException ex) {
         if (listener != null) {
            listener.clearCurrentWrites();
            listener.clearCurrentReads();
         }
         throw ex;
      }
   }

   public void shutdown() {}

   public String getVersion() {
      return "1.0";
   }

   /**
    * @return a human readable usage help string
    */
   public java.lang.String usage() {
      return Global.getJmxUsageLinkInfo(this.getClass().getName(), null);
   }

   /**
    * @return A link for JMX usage
    */
   public java.lang.String getUsageUrl() {
      return Global.getJavadocUrl(this.getClass().getName(), null);
   }

   /* dummy to have a copy/paste functionality in jconsole */
   public void setUsageUrl(java.lang.String url) {
   }
   
   public static String getGlobalKey(SessionName sessionName) {
       return "ClusterManager[cluster]/SocketExecutor" + ((sessionName == null) ? "" : sessionName.getRelativeName(true));
   }
}

