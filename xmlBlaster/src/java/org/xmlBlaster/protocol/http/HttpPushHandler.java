/*------------------------------------------------------------------------------
Name:      HttpPushHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling callback over http
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.jutils.text.StringHelper;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;

import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

/**
 * This handles and hides the different http push modes when sending
 * data back to the browser through method update().
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
public class HttpPushHandler implements I_Callback 
{
   private String ME  = "HttpPushHandler";
   private static Logger log = Logger.getLogger(HttpPushHandler.class.getName());

   /**
    * Ping the browser every 10 seconds.
    * <br />
    * You need to adjust ping() in persistenWindow/index.html as well,
    * if you change the value here.
    */
   private final long PING_INTERVAL = 10000L;

   private I_XmlBlasterAccess xmlBlasterAccess;
   private I_Callback callbackInterceptor;

   private HttpServletRequest req = null;
   private HttpServletResponse res = null;
   private String sessionId = null;

   /** Current http connection state */
   private boolean closed = false;

   private ServletOutputStream outMulti;
   private PrintWriter outPlain;

   /** The header of the HTML page */
   private String head;
   /** The tail of the HTML page */
   private String tail;
   /** Check it the browser is ready to accept more messages */
   private boolean browserIsReady = false;

   /** handlesMultipart is true for netscape browser */
   private boolean handlesMultipart = false;

   /** Check browser and holds the http connection */
   private HttpPingThread pingThread = null;

   /** Queue to hold messages (class PushDataItem) until the browser is ready for them */
   private Vector pushQueue = null;

   private boolean firstPush = true;


   /**
    * Use this constructor if you are too lazy to pass a HTML header, a default will be used.
    * @param req The request object
    * @param res The response object
    * @param sessionId The browser id
    * @param loginName For loggin only
    * @param xmlBlasterAccess Not yet logged in
    */
   public HttpPushHandler(HttpServletRequest req, HttpServletResponse res, String sessionId,
                          String loginName, I_XmlBlasterAccess xmlBlasterAccess)
                               throws ServletException, IOException
   {

      this.req = req;
      this.res = res;
      this.sessionId = sessionId;
      this.xmlBlasterAccess = xmlBlasterAccess;
      String browserId = req.getRemoteAddr() + "-" + loginName + "-" + sessionId;
      this.ME  = "HttpPushHandler-" + browserId;

      // Setting HTTP headers to prevent caching
      /* !!! activate when migrating to servlet 2.2 !!!
      res.addHeader("Expires", "Tue, 31 Dec 1997 23:59:59 GMT");
      res.addHeader("Cache-Control", "no-cache");
      res.addHeader("Pragma", "no-cache");
      */

      initialize(null, null);

      pushQueue = new Vector();
      setBrowserIsReady(true);

      log.fine(ME + " Creating PingThread ...");
      pingThread = new HttpPingThread(this, PING_INTERVAL, browserId);
   }

   public I_XmlBlasterAccess getXmlBlasterAccess() {
      return this.xmlBlasterAccess;
   }

   /**
    */
   private void initialize(String head, String tail) throws IOException
   {
      if (log.isLoggable(Level.FINE)) log.fine("Creating HttpPushHandler ...");
      this.head = head;
      this.tail = tail;
      this.handlesMultipart = doesHandleMultipart();
      if (handlesMultipart)
         this.outMulti =  res.getOutputStream();
      else
         this.outPlain =  res.getWriter();

      log.fine("Initialize ...");

      if (this.head == null) {
         this.head = "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML//EN\">" +
                                 "<HTML>\n" +
          "<HEAD>\n" +
          "   <meta http-equiv='Content-Type' content='text/html; charset=iso-8859-1'>\n" +
          "   <meta http-equiv='Pragma' content='no-cache'>\n" +
          "   <meta http-equiv='Cache-Control' content='no-cache'>\n" +
          "   <meta http-equiv='Expires' content='Tue, 31 Dec 1997 23:59:59 GMT'>\n" +
          "   <TITLE>BlasterHttpProxy Connection</TITLE>\n" +
          "</HEAD>\n" +
          "<BODY>\n" +
          "   <br>&nbsp;\n" +
          "   <br>&nbsp;\n" +
          "   <form ACTION=\"\" METHOD=\"POST\" TARGET=\"callback\">\n" +
          "      <INPUT TYPE=\"HIDDEN\" NAME=\"NoName\" VALUE=\"NoValue\" />\n" +
          "   </form>\n" +
          "   <script language='JavaScript' type='text/javascript'>\n";
      }

      if (handlesMultipart) {
         if (this.tail == null) {
            this.tail  =   "</script>\n</BODY></HTML>";
         }
         res.setContentType("multipart/x-mixed-replace;boundary=End");

         outMulti.println();
         outMulti.println("--End");
      }
      else {
         res.setContentType("text/html"); // bugfix, thanks to Just van den Broecke <just@justobjects.nl>
      }
   }

   /**
    * Don't forget to call this method when you want to close the connection.
    */
   public void shutdownBrowserConnection()
   {
      try {
         setClosed(true);
         setBrowserIsReady(false);

         if (handlesMultipart) {
            outMulti.close();
         }
         else {
            outPlain.close();
         }

         pingThread.stopThread();
         log.info("Closed push connection to browser");
      }
      catch(Exception e) {
         log.severe("Error occurred while de-initializing the push handler :"+e.toString());
      }
   }

   /**
    * If you implement I_ProxyInterceptor and register it here,
    * your update() implementation is called and may manipulate the
    * received message from xmlBlaster before it is sent to the browser.
    * @param interceptor Your optional implementation
    */
   public void setProxyInterceptor( I_Callback interceptor )
   {
      this.callbackInterceptor = interceptor;
   }

   /**
    * Delegates the cleanup call to HttpPushHandler
    */
   public void cleanup() {
      if (log.isLoggable(Level.FINER)) log.finer("Entering cleanup(" + sessionId + ") ...");
      try {
         if (this.sessionId != null) {
            BlasterHttpProxy.cleanup(this.sessionId);
         }

         if (this.xmlBlasterAccess != null) {
            this.xmlBlasterAccess.disconnect(null);
            log.info("XmlBlaster connection removed");
            this.xmlBlasterAccess = null;
         }

         this.callbackInterceptor = null;

         shutdownBrowserConnection();
      }
      catch (Exception e) {
         e.printStackTrace();
         log.severe("Can't destroy http connection for sessionId=" + sessionId + ": " + e.toString());
      }
   }

   public void startPing() throws ServletException, IOException
   {
      pingThread.start();
      ping("refresh");
   }


   /**
    * check's whether the HTTP connection is closed or not
    */
   public boolean closed()
   {
      return closed;
   }
   /**
    */
   public void setClosed(boolean closed)
   {
      if (log.isLoggable(Level.FINE)) log.fine("Setting closed from " + this.closed + " to " + closed);
      this.closed = closed;
   }


   /**
    * check's whether the browser is ready to accept more messages or not
    * [if (parent.browserReady != null) parent.browserReady();].
    *        This shows, that the browser had processed the whole message.
    *        If the Browser implements this javascript function, it could send
    *        a browserReady signal back to the  BlasterHttpProxyServlet.
    *        This feature solves the problem, that the browser got messages too fast
    *        and could not process all content of the message.
    */
   public final boolean isBrowserReady()
   {
      return browserIsReady;
   }
   /**
    */
   public void setBrowserIsReady(boolean ready)
   {
      if (closed()) { this.browserIsReady=false; return; }

      this.browserIsReady = ready;
      if (log.isLoggable(Level.FINE)) log.fine("Setting browserReady=" + browserIsReady);

      pong(); // Use this as a browser alive as well, since some internet proxies seem to hold back my pongs

      //send queue if browser is ready
      if (browserIsReady) {
         try {
            pushToBrowser();
         }
         catch(Exception e) {
            log.severe("sending push queue to browser failed. ["+e.toString()+"]");
            shutdownBrowserConnection();
         }
      }
   }


   /**
    * Determine if the browser can handle multipart pushs.
    * Only Netscape returns true.
    */
   private boolean doesHandleMultipart() throws IOException
   {
      String browser = req.getHeader("User-Agent");

      // netscape 4.74:                   "Mozilla/4.74 [de] (X11; U; Linux 2.2.16 i686)"
      // opera 4.0b2 (Identify as Opera): "Mozilla/ (Linux; U) Opera 4.01  [en]"

      boolean doesMulti = false;
      if (browser == null)
         doesMulti = false;
      else if (browser.indexOf("Mozilla") != -1 &&
               browser.indexOf("MSIE") == -1  &&
               browser.indexOf("Opera") == -1)
         doesMulti = true;   // Only real mozillas support multipart request


      if (doesMulti)
         log.info("Checking browser = " + browser + ". Assuming it supports 'multipart requests'");
      else
         log.info("Checking browser = " + browser + ". We won't use 'multipart requests'");
      return doesMulti;
   }


   /**
    * Updating data to the browser (callback/push mode).
    * The data must be Javascript code
    * @param str
    */
   public void push(PushDataItem item) throws ServletException, IOException
   {
      if (closed()) return;
      synchronized(pushQueue) {
         pushQueue.addElement( item );
         pushToBrowser();
      }
   }


   /**
    * Pushing messages in the queue to the browser
    */
   private void pushToBrowser() throws ServletException, IOException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering pushToBrowser() ...");

      synchronized(pushQueue) {
         if(pushQueue.size() == 0)
            return;
         if(!isBrowserReady() ) {
            log.info("Waiting until browser is ready to send " + pushQueue.size() + " messages");
            return;
         }

         //setting Http push connection to false.
         if (handlesMultipart) {
            if (log.isLoggable(Level.FINE)) log.fine("Pushing multipart, pushQueue.size=" + pushQueue.size());
            /* Every line which is sent to the browser overwrites the former one
               Problems: (Linux/netscape)
               1. The watch-wait cursor is displayed, until the doGet() leaves.
               2. Resizing the browser window doesn't resize the content.
               We help us by displaying a specialized little 'Connection-Window'
               which holds the connection, the other application windows are
               running fine then.
            */
            synchronized(outMulti) {
               outMulti.println("Content-Type: text/html");
               outMulti.println();

               StringBuffer buf = new StringBuffer(head);

               boolean isMessage = false;
               // Collect all messages, pings etc from the queue ...
               for( int i = 0; i < pushQueue.size(); i++ ) {
                  PushDataItem item = (PushDataItem)pushQueue.elementAt(i);
                  buf.append(item.data);
                  if (item.type == PushDataItem.MESSAGE) isMessage = true;
               }

               if (isMessage)
                  setBrowserIsReady(false); // Force browser to tell us when it is ready

               buf.append(tail);
               if (log.isLoggable(Level.FINEST)) log.finest("Sending to callbackFrame:\n" + buf.toString());

               outMulti.println(buf.toString());

               outMulti.println();
               outMulti.println("--End");
               outMulti.flush();
               log.fine("Push content queue successfully sent as multipart.");
               pushQueue.clear();
            }
         }
         else {
            log.fine("Pushing plain, pushQueue.size=" + pushQueue.size());
            /*
               Problems: (Linux/netscape)
               1. The watch-wait cursor is displayed, until the doGet() leaves.
               2. Resizing the browser window doesn't resize the content.
               3. Browser only refreshes after one line is full
                  (depending on current width every 3 messages)
                  So the message should contain some <p> to force a refresh
               4. Every line which is sent again to the browser is written after
                  the previous one resulting in a list of ten rows.
            */
            synchronized(outPlain) {
               StringBuffer buf = new StringBuffer();
               if (firstPush) {
                  buf.append(head);
               }
               else
                  buf.append("<script language='JavaScript' type='text/javascript'>\n");

               boolean isMessage = false;
               // Collect all messages, pings etc from the queue ...
               for( int i = 0; i < pushQueue.size(); i++ ) {
                  PushDataItem item = (PushDataItem)pushQueue.elementAt(i);
                  buf.append(item.data);
                  if (item.type == PushDataItem.MESSAGE) isMessage = true;
               }

               if (isMessage)
                  setBrowserIsReady(false); // Force browser to tell us when it is ready

               // bug, thanks to Just: buf.append(tail);

               if (log.isLoggable(Level.FINEST)) log.finest("Sending plain to callbackFrame:\n" + buf.toString());

               outPlain.println(buf.toString());

               outPlain.println("</script><p />\n");

               outPlain.flush();
               log.fine("Push content queue successfully sent.");
               pushQueue.clear();
            }
         }
      }
      firstPush = false;
   }


   /**
    * Pushes received message back to browser or applet. 
    * <p>
    * Browser: Calls the update method in the parentframe of the callback frame
    * The data must be Javascript code
    * </p>
    * <p>
    * Applet: The callbacks are java-serialized Map for key/qos etc.
    * </p>
    */
   public String update(String sessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      try {
         if (log.isLoggable(Level.FINE)) log.fine("update '" + updateKey.getOid() + "'");

         if(callbackInterceptor != null) {
            callbackInterceptor.update(sessionId, updateKey, content, updateQos);
         }

         String pushStr="";
         String codedKey     = Global.encode(updateKey.toXml().trim(), BlasterHttpProxyServlet.ENCODING );
         String codedContent = Global.encode(new String(content), BlasterHttpProxyServlet.ENCODING );
         String codedQos     = Global.encode(updateQos.toXml().trim(), BlasterHttpProxyServlet.ENCODING );
         pushStr = "if (parent.update != null) parent.update('"+codedKey+"','"+codedContent+"','"+codedQos+"');\n";

         push(new PushDataItem(PushDataItem.MESSAGE, pushStr));
      }
      catch(Exception e) {
         e.printStackTrace();
         log.severe(e.toString());
      }
      return "<qos/>"; // TODO: Async wait on return value from browser/applet
   }


   /**
    * Display a popup alert message containing the error text.
    * <p />
    * This method wraps your text into javascript/alert code
    * and escapes "\n" and "'" characters
    *
    * @param text The error text
    */
   static public final String alert(String text)
   {
      StringBuffer retStr = new StringBuffer();
      retStr.append("<html><body>\n");
      retStr.append("<script language='JavaScript' type='text/javascript'>\n");
      String tmp = StringHelper.replaceAll(text, "'", "\\'");
      retStr.append("alert(\'" + StringHelper.replaceAll(tmp, "\n", "\\n'+\n'") + "');\n");
      retStr.append("</script>\n");
      retStr.append("</body></html>\n");
      log.warning("Sending alert to browser: " + text);
      return retStr.toString();
   }

   /**
    * Calls the message method in the parentframe of the callback frame.
    * <p />
    * See callback.js message() method.
    * Can be used by applications to send a message to the browser.
    * @param text This must be Javascript code (usually a message string)
    */
   public void message( String text )
   {
      try {
         String codedText = Global.encode(text, BlasterHttpProxyServlet.ENCODING);
         push(new PushDataItem(PushDataItem.LOGGING, "if (parent.message != null) parent.message('"+codedText+"');\n"));
      }
      catch(Exception e) {
         log.severe(e.toString());
      }
   }

   /**
    * Calls the error method in the parentframe of the callback frame.
    * <p />
    * See callback.js error() method, which shows an alert window.
    * @param text This must be Javascript code (usually an error string)
    */
    /*
   public void error(String text)
   {
      try {
         String codedText = Global.encode(text, BlasterHttpProxyServlet.ENCODING);
         push(new PushDataItem(PushDataItem.LOGGING, "if (parent.error != null) parent.error('"+codedText+"');\n"));
      }
      catch(Exception e) {
         log.error(ME,e.toString());
      }
   }
    */

   /**
    * calls the ping method in the parentframe of the callback frame
    * The data must be Javascript code
    * @param state The string "refresh"<br />
    *              When login is done successfully, state="loginSucceeded" is sent
    *              one time
    */
   public void ping(String state) throws ServletException, IOException
   {
      if (!isBrowserReady()) {
         log.warning("Browser seems not to be ready, forcing a push nevertheless (checking browser with ping)");
         setBrowserIsReady(true);
      }
      push(new PushDataItem(PushDataItem.PING, "if (parent.ping != null) parent.ping('" + state + "');\n"));
   }


   /**
    * This is the browser response for our previous ping.
    */
   public void pong()
   {
      if (closed()) return;
      if (pingThread != null) pingThread.pong();
   }

   /**
    * Ping the browser, to avoid that the web server or the browser
    * closes the http connection after a vendor specific timeout.
    * <p />
    * Note that the ping sends some bytes as well, the netscape browser
    * for example closes the http connection if the amount of bytes per second
    * falls below a certain level.
    * <p />
    * The browser responses with 'pong' which allows us to check if the browser
    * is still here.
    */
   private class HttpPingThread extends Thread
   {
      private String ME = "HttpPingThread";
      private HttpPushHandler pushHandler;
      private final long PING_INTERVAL;
      private boolean pingRunning = true;
      /** We wait for a browser response (pong) after out ping */
      private int waitForPong = 0;
      private long counter = 0L;

      /**
       * Response from the browser on our ping.
       * <p />
       * Sometimes the browser is fine but suddenly a pong is missing,
       * for example ... "refresh-2084", "refresh-2086", ...<br />
       * Number 2085 was just missing in the logs.<br />
       * So we reset the waitForPong counter, and accept a sometimes missing pong.
       */
      public void pong()
      {
         if (log.isLoggable(Level.FINE)) log.fine("Received pong, current waitForPong=" + waitForPong + ", counter=" + counter);
         waitForPong = 0; // not waitForPong--; if (waitForPong<0) waitForPong=0; since we don't care about delay, we are happy if the browser does respond somehow
      }

      public void stopThread()
      {
         pingRunning = false;
      }

      /**
       * @param pingInterval How many milli seconds sleeping between the pings
       * @param loginName For debugging only
       */
      HttpPingThread(HttpPushHandler pushHandler, long pingInterval, String loginName) {
         this.ME = "HttpPingThread-" + loginName;
         this.pushHandler = pushHandler;
         this.PING_INTERVAL = pingInterval;
         if (log.isLoggable(Level.FINER)) log.finer("Entering constructor HTTP ping interval=" + pingInterval + " millis");
      }
      public void run() {
         if (log.isLoggable(Level.FINER)) log.finer("Pinging browser ...");
         while (pingRunning) {

            try {
               Thread.currentThread().sleep(PING_INTERVAL);
               if (pingRunning == false)
                  break;
               counter++;
            }
            catch (InterruptedException i) { }

            try {
               //if (false) {  //// Switched off !!!!!
               if (waitForPong > 2) {  // Allow three pongs delay over slow connections
               // This ping doesn't work over the internet??????
                  log.warning("Browser seems to have disappeared, no response for my ping=" + counter + ", missing " + waitForPong + " responses. Closing connection.");
                  pushHandler.cleanup();
                  stopThread();
               }
               else {
                  String text = "refresh-" + counter;
                  if (log.isLoggable(Level.FINE)) log.fine("Sending ping '" + text + "'  to browser ...");
                  pushHandler.ping(text);
                  waitForPong++;
               }
            } catch(Exception e) {
               //error handling: browser closed connection.
               log.warning("You tried to ping=" + counter + " a browser who is not interested. Close HttpPushHandler.");
               pushHandler.cleanup();
               stopThread();
            }
         }
         if (log.isLoggable(Level.FINE)) log.fine("Ping thread dies ...");
      }
   } // class PingThread


}

