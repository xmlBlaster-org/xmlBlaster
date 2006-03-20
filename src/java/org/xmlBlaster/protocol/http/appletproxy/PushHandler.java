/*------------------------------------------------------------------------------
Name:      PushHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling callback over http
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http.appletproxy;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.protocol.http.common.I_XmlBlasterAccessRaw;
import org.xmlBlaster.client.protocol.http.common.ObjectOutputStreamMicro;
import org.xmlBlaster.client.qos.UpdateQos;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Hashtable;

import org.apache.commons.codec.binary.Base64;

/**
 * This handles and hides the different http push modes when sending
 * data back to the applet through method update().
 * <br />
 * Push mode works with keeping a permanent http connection
 * <p />
 * TODO:
 *   HTTP 1.1 specifies rfc2616 that the connection stays open as the
 *   default case. How must this code be changed?
 * <p />
 * See Java Servlet Programming from Jason Hunter
 * @author Marcel Ruff xmlBlaster@marcelruff.info
 */
public class PushHandler implements I_Callback, I_Timeout 
{
   private String ME  = "PushHandler";
   private Global glob;
   private static Logger log = Logger.getLogger(PushHandler.class.getName());

   /**
    * Ping the applet every 10 seconds. 
    */
   private long pingInterval = 10000L;
   private final Timeout timeout;
   private Timestamp pingTimeoutHandle;
   private long pingCounter;
   private long missingPongs;

   private I_XmlBlasterAccess xmlBlasterAccess;
   private I_Callback callbackInterceptor;

   private HttpServletResponse res;
   private String sessionId;

   /** Current http connection state */
   private boolean closed = false;
   private ServletOutputStream outMulti;

   /* Send Base64 all in one line */
   boolean isChunked = false;

   /**
    * Use the persistent HTTP callback connection. 
    * @param res The response object
    * @param sessionId The applet id
    * @param loginName For loggin only
    * @param xmlBlasterAccess Not yet logged in
    */
   public PushHandler(HttpServletRequest req, HttpServletResponse res, String sessionId,
                          String loginName, I_XmlBlasterAccess xmlBlasterAccess, Timeout timeout)
                               throws ServletException, IOException {


      this.res = res;
      this.sessionId = sessionId;
      this.xmlBlasterAccess = xmlBlasterAccess;
      this.glob = this.xmlBlasterAccess.getGlobal();
      this.timeout = timeout;
      String appletId = req.getRemoteAddr() + "-" + loginName + "-" + sessionId;
      this.ME  = "PushHandler-" + appletId;

      // Setting HTTP headers to prevent caching
      /* !!! activate when migrating to servlet 2.2 !!!
      res.addHeader("Expires", "Tue, 31 Dec 1997 23:59:59 GMT");
      res.addHeader("Cache-Control", "no-cache");
      res.addHeader("Pragma", "no-cache");
      */

      this.outMulti =  this.res.getOutputStream();
      this.res.setContentType("multipart/x-mixed-replace;boundary=End");
      outMulti.println();
      outMulti.println("--End");
   }

   /**
    * @param userData
    * @see org.xmlBlaster.util.I_Timeout
    */
   public void timeout(Object userData) {
      if (log.isLoggable(Level.FINER)) log.finer("Pinging applet ...");
      this.pingCounter++;
      try {
         if (this.missingPongs > 2) {
            // Allow three pongs delay over slow connections
            log.warning("Applet seems to have disappeared, no response for my ping=" + pingCounter +
                         ", missing " + this.missingPongs + " responses. Closing connection.");
            cleanup();
         }
         else {
            String text = "refresh-" + pingCounter;
            if (log.isLoggable(Level.FINE)) log.fine("Sending ping '" + text + "'  to applet, missingPongs=" + this.missingPongs + " ...");
            ping(text);
            this.missingPongs++;
         }
      } catch(Exception e) {
         //error handling: applet closed connection.
         log.warning("We tried to ping=" + pingCounter + " an applet who is not interested. Close PushHandler.");
         cleanup();
      }

      synchronized (this) {
         if (this.pingInterval > 0)
            this.pingTimeoutHandle = this.timeout.addTimeoutListener(this, this.pingInterval, userData);
      }
   }

   public void startPing() throws XmlBlasterException {
      log.fine("startPing ...");
      setPingInterval(this.pingInterval);
      ping("refresh");
   }

   public void stopPing() throws XmlBlasterException {
      log.fine("stopPing ...");
      setPingInterval(0L);
   }

   public boolean isClosed() {
      return this.closed;
   }

   /**
    * Set or change the ping interval. 
    * <p>
    * Ping the applet to avoid that the web server or a internet proxy
    * closes the http connection after a vendor specific timeout.
    * <br />
    * Note that the ping sends some bytes as well, a proxy
    * may close the http connection if the amount of bytes per second
    * falls below a certain level.
    * <br />
    * The applet responses with 'pong' which allows us to check if the applet
    * is still here.
    * </p>
    * @param pingInterval in milli seconds (defaults to 10000)
    */
   public synchronized void setPingInterval(long pingInterval) throws XmlBlasterException {
      this.pingInterval = pingInterval;
      if (this.pingInterval < 1) {
         if (this.pingTimeoutHandle != null) {
            this.timeout.removeTimeoutListener(this.pingTimeoutHandle);
         }
         return;
      }
      this.pingTimeoutHandle = this.timeout.addOrRefreshTimeoutListener(this,
               this.pingInterval, null, this.pingTimeoutHandle);
   }
 
   public I_XmlBlasterAccess getXmlBlasterAccess() {
      return this.xmlBlasterAccess;
   }

   /**
    * Don't forget to call this method when you want to close the connection.
    */
   public void shutdownAppletConnection() {
      try {
         this.closed = true;
         stopPing();
         if (this.outMulti != null) this.outMulti.close();
         log.info("Closed push connection to applet");
      }
      catch(Exception e) {
         e.printStackTrace();
         log.severe("Error occurred while de-initializing the push handler :"+e.toString());
      }
   }

   /**
    * If you implement I_ProxyInterceptor and register it here,
    * your update() implementation is called and may manipulate the
    * received message from xmlBlaster before it is sent to the applet.
    * @param interceptor Your optional implementation
    */
   public void setProxyInterceptor(I_Callback interceptor) {
      this.callbackInterceptor = interceptor;
   }

   /**
    * Shutdown applet connection and xmlBlaster connection. 
    */
   public void cleanup() {
      if (log.isLoggable(Level.FINER)) log.finer("Entering cleanup() ...");
      
      if (this.xmlBlasterAccess != null) {
         try {
            this.xmlBlasterAccess.disconnect(null);
            log.info("XmlBlaster connection removed");
            this.xmlBlasterAccess = null;
         }
         catch (Exception e) {
            e.printStackTrace();
            log.severe("Can't destroy http connection: " + e.toString());
         }
      }

      this.callbackInterceptor = null;

      try {
         shutdownAppletConnection();
      }
      catch (Exception e) {
         e.printStackTrace();
         log.severe("Can't destroy http connection: " + e.toString());
      }
   }

   /**
    * Pushing a message to the applet. 
    * @param chunk The raw data, we encode it with base64, the applet must know how to handle it.
    */
   private void pushToApplet(byte[] chunk) throws IOException {
      if (log.isLoggable(Level.FINE)) log.fine("Pushing multipart for applet, size=" + chunk.length);
      if (log.isLoggable(Level.FINE)) log.fine("Pushing multipart for applet, content='" + new String(chunk) + "'");
      
      byte[] base64 = Base64.encodeBase64(chunk, isChunked);
      if (log.isLoggable(Level.FINE)) log.fine("Pushing multipart for applet, content (encoded)='" + new String(base64) + "'");
      synchronized(outMulti) {
         outMulti.println(new String(base64));
         outMulti.println("--End");
         outMulti.flush();
         log.fine("Pushed data successfully as multipart to applet.");
      }
   }

   /**
    * Pushes received message back to browser or applet. 
    * <p>
    * The callbacks are java-serialized Maps for key/qos etc.
    * </p>
    * <pre>
    * Format:  Serialized 
    *      "'update' sessionId QoS Key Content"
    * and then Base64 encoded
    * </pre>
    */
   public String update(String sessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      try {
         if (log.isLoggable(Level.FINE)) log.fine("update '" + updateKey.getOid() + "'");

         if(callbackInterceptor != null) {
            callbackInterceptor.update(sessionId, updateKey, content, updateQos);
         }

         ByteArrayOutputStream dump = new ByteArrayOutputStream(1024);
         ObjectOutputStreamMicro out = new ObjectOutputStreamMicro(dump);

         out.writeObject(I_XmlBlasterAccessRaw.UPDATE_NAME.toString()); // "update"
         out.writeObject(sessionId);

         Hashtable qosMap = updateQos.getData().toJXPath();
         out.writeObject(qosMap);

         Hashtable keyMap = updateKey.getData().toJXPath();
         out.writeObject(keyMap);

         out.writeObject(new String(Base64.encodeBase64(content, isChunked)));

         pushToApplet(dump.toByteArray());
         if (log.isLoggable(Level.FINE)) log.fine("Sent update message '" + updateKey.getOid() + "' content='" + new String(content) + "' to applet");
      }
      catch(Exception e) {
         e.printStackTrace();
         log.severe(e.toString());
      }
      return "<qos/>"; // TODO: Async wait on return value from browser/applet
   }

   /**
    * calls the ping method in the parentframe of the callback frame
    * The data must be Javascript code
    * @param state The string "refresh"<br />
    *              When login is done successfully, state="loginSucceeded" is sent
    *              one time
    */
   public void ping(String state) throws XmlBlasterException {
      try {
         ByteArrayOutputStream dump = new ByteArrayOutputStream(1024);
         ObjectOutputStreamMicro out = new ObjectOutputStreamMicro(dump);

         out.writeObject(I_XmlBlasterAccessRaw.PING_NAME); // "ping"
         out.writeObject("<qos id='"+state+"'/>");

         pushToApplet(dump.toByteArray());
         if (log.isLoggable(Level.FINE)) log.fine("Sent ping '" + state + "' to applet");
      }
      catch (IOException e) {
         throw XmlBlasterException.convert(glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, "ping(" + state + ") failed", e);
      }
   }

   /**
    * This is the browser response for our previous ping.
    */
   public void pong() {
      this.missingPongs = 0;
   }
}

