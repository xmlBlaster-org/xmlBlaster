/*------------------------------------------------------------------------------
Name:      HttpPushHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling callback over http
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import org.xmlBlaster.util.Log;
import org.jutils.io.FileUtil;
import org.jutils.text.StringHelper;

import org.xmlBlaster.util.XmlBlasterException;

import java.rmi.RemoteException;
import java.io.*;
import java.util.*;
import java.net.URLEncoder;
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
 * This could (should) be an interface and the different https methods
 * should be implemented in derived classes, but this is better performing :-)
 * <p />
 * @author Marcel Ruff ruff@swand.lake.de
 * @see Java Servlet Programming from Jason Hunter
 */
public class HttpPushHandler
{
   private String ME  = "HttpPushHandler";
   /**
    * Ping the browser every 10 seconds.
    * <br />
    * You need to adjust ping() in persistenWindow/index.html as well,
    * if you change the value here.
    */
   private final long PING_INTERVAL = 10000L;

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
   /** Javascript code to invoke browserReady method */
   private String readyStr;
   /** Check it the browser is ready to accept more messages */
   private boolean browserIsReady = false;

   /** handlesMultipart is true for netscape browser */
   private boolean handlesMultipart = false;

   /** Check browser and holds the http connection */
   private HttpPingThread pingThread = null;

   /** Queue to hold messages until the browser is ready for them */
   private Vector pushQueue = null;

   private boolean firstPush = true;


   /**
    * Use this constructor if you are too lazy to pass a HTML header, a default will be used.
    * @param req The request object
    * @param res The response object
    * @param sessionId The browser id
    * @param loginName For debugging only
    */
   public HttpPushHandler(HttpServletRequest req, HttpServletResponse res, String sessionId, String loginName)
                               throws ServletException, IOException
   {
      this.req = req;
      this.res = res;
      this.sessionId = sessionId;
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

      Log.trace(ME,"Creating PingThread ...");
      pingThread = new HttpPingThread(this, PING_INTERVAL, browserId);
   }


   /**
    */
   private void initialize(String head, String tail) throws IOException
   {
      if (Log.TRACE) Log.trace(ME, "Creating HttpPushHandler ...");
      this.head = head;
      this.tail = tail;
      this.handlesMultipart = doesHandleMultipart();
      if (handlesMultipart)
         this.outMulti =  res.getOutputStream();
      else
         this.outPlain =  res.getWriter();

      Log.trace(ME, "Initialize ...");

      if (this.head == null) {
         // this.head = "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML//EN\">" +
         this.head  = "<HTML>\n" +
          "<HEAD>\n" +
          "   <meta http-equiv='Content-Type' content='text/html; charset=iso-8859-1'>\n" +
          "   <meta http-equiv='Pragma' content='no-cache'>\n" +
         "    <meta http-equiv='Cache-Control' content='no-cache'>\n" +
          "   <meta http-equiv='Expires' content='Tue, 31 Dec 1997 23:59:59 GMT'>\n" +
          "   <TITLE>BlasterHttpProxy Connection</TITLE>\n" +
          "</HEAD>\n" +
          "<BODY>\n" +
          "   <br>&nbsp;\n" +
          "   <br>&nbsp;\n" +
          "   <form ACTION=\"\" METHOD=\"POST\" TARGET=\"callback\">\n" +
          "      <INPUT TYPE=\"HIDDEN\" NAME=\"NoName\" VALUE=\"NoValue\" />\n" +
          "   </form>\n" +
          "   <script language='JavaScript'>\n";
      }

      if(readyStr == null) {
         readyStr = "\n  if (parent.browserReady != null) parent.browserReady();\n";
      }

      if (handlesMultipart) {
         if (this.tail == null)
            this.tail  ="\n   </script>\n</BODY></HTML>";
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
   public void deinitialize()
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
         Log.info(ME, "Closed push connection to browser");
      }
      catch(Exception e) {
         Log.error(ME,"Error occurred while de-initializing the push handler :"+e.toString());
      }
   }


   /**
    * Delegates the cleanup call to HttpPushHandler
    */
   private void cleanup()
   {
      if (Log.CALL) Log.call(ME, "Entering cleanup(" + sessionId + ") ...");
      try {
         ProxyConnection pc = BlasterHttpProxy.getProxyConnectionBySessionId(sessionId);
         if (pc != null) {
            pc.cleanup(sessionId);
            if (Log.TRACE) Log.trace(ME, "Deleted " + sessionId + " ...");
         }
      }
      catch (XmlBlasterException e) {
         Log.error(ME, "Can't destroy http connection for sessionId=" + sessionId + ":\n" + org.jutils.runtime.StackTrace.getStackTrace());
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
      if (Log.TRACE) Log.trace(ME, "Setting closed from " + this.closed + " to " + closed);
      this.closed = closed;
   }


   /**
    * check's whether the browser is ready to accept more messages or not
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
      if (Log.TRACE) Log.trace(ME, "Setting browserReady=" + browserIsReady);

      pong(); // Use this as a browser alive as well, since some internet proxies seem to hold back my pongs

      //send queue if browser is ready
      if (browserIsReady) {
         try {
            pushToBrowser(true);
         }
         catch(Exception e) {
            Log.error(ME,"sending push queue to browser failed. ["+e.toString()+"]");
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
         doesMulti = true;

      if (doesMulti)
         Log.info(ME, "Checking browser = " + browser + ". Assuming it supports 'multipart requests'");
      else
         Log.info(ME, "Checking browser = " + browser + ". We won't use 'multipart requests'");
      return doesMulti;
   }


   /**
    * Updating data to the browser (callback/push mode).
    * The data must be Javascript code
    * @param str
    * @param confirm true - There will be append an Javascript code
    *        [if (parent.browserReady != null) parent.browserReady();].
    *        This shows, that the browser had processed the whole message.
    *        If the Browser implements this javascript function, it could send
    *        a browserReady signal back to the  BlasterHttpProxyServlet.
    *        This feature solves the problem, that the browser got messages too fast
    *        and could not process all content of the message.
    */
   public void push(String str, boolean confirm) throws ServletException, IOException
   {
      if (closed()) return;
      synchronized(pushQueue) {
         pushQueue.addElement( str );
         pushToBrowser(confirm);
      }
   }
   public void push(String str) throws ServletException, IOException
   {
      if (closed()) return;
      synchronized(pushQueue) {
         pushQueue.addElement( str );
         pushToBrowser(true);
      }
   }


   /**
    * Pushing messages in the queue to the browser
    */
   private void pushToBrowser(boolean confirm) throws ServletException, IOException
   {
      if (Log.CALL) Log.call(ME, "Entering pushToBrowser() ...");

      synchronized(pushQueue) {
         if(pushQueue.size() == 0)
            return;
         if(!isBrowserReady() ) {
            Log.info(ME, "Waiting until browser is ready to send " + pushQueue.size() + " messages");
            return;
         }

         //setting Http push connection to false.
         if (handlesMultipart) {
            if (Log.TRACE) Log.trace(ME, "Pushing multipart, pushQueue.size=" + pushQueue.size());
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

               for( int i = 0; i < pushQueue.size(); i++ )
                  buf.append((String)pushQueue.elementAt(i));

               if( confirm ) {
                  buf.append(readyStr);
                  setBrowserIsReady(false);
               }

               buf.append(tail);
               if (Log.DUMP) Log.dump(ME, "Sending to callbackFrame:\n" + buf.toString());

               outMulti.println(buf.toString());

               outMulti.println();
               outMulti.println("--End");
               outMulti.flush();
               Log.trace(ME,"Push content queue successfully sent as multipart.");
               pushQueue.clear();
            }
         }
         else {
            Log.trace(ME, "Pushing plain, pushQueue.size=" + pushQueue.size());
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
                  buf.append("<script language='JavaScript'>\n");

               for( int i = 0; i < pushQueue.size(); i++ )
                  buf.append((String)pushQueue.elementAt(i));

               if( confirm ) {
                  buf.append(readyStr);
                  setBrowserIsReady(false);
               }

               // bug, thanks to Just: buf.append(tail);

               if (Log.DUMP) Log.dump(ME, "Sending plain to callbackFrame:\n" + buf.toString());

               outPlain.println(buf.toString());

               outPlain.println("</script><p />\n");

               outPlain.flush();
               Log.trace(ME,"Push content queue successfully sent.");
               pushQueue.clear();
            }
         }
      }
      firstPush = false;
   }


   /**
    * calls the update method in the parentframe of the callback frame
    * The data must be Javascript code
    */
   public void update( String updateKey, String content, String updateQos )
   {
      try {
         String codedKey     = URLEncoder.encode( updateKey );
         String codedContent = URLEncoder.encode( content );
         String codedQos     = URLEncoder.encode( updateQos );

         if (Log.TRACE) Log.trace(ME,"update dump: " + updateKey.substring(0,50) + " ...");
         /*
         Log.plain(ME,"Key:"+updateKey);
         Log.plain(ME,"\nContent:"+content);
         Log.trace(ME,"************* End of Update *************************");
         */
         String pushStr = "if (parent.update != null) parent.update('"+codedKey+"','"+codedContent+"','"+codedQos+"');\n";
         push(pushStr);
      }
      catch(Exception e) {
         Log.error(ME,e.toString());
      }
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
      retStr.append("<script language='JavaScript1.2'>\n");
      String tmp = StringHelper.replaceAll(text, "'", "\\'");
      retStr.append("alert(\'" + StringHelper.replaceAll(tmp, "\n", "\\n'+\n'") + "');\n");
      retStr.append("</script>\n");
      retStr.append("</body></html>\n");
      Log.warn("HttpPushHandler", "Sending alert to browser: " + text);
      return retStr.toString();
   }


   /**
    * Calls the message method in the parentframe of the callback frame.
    * <p />
    * See callback.js message() method.
    * @param text This must be Javascript code (usually a message string)
    */
   public void message( String text )
   {
      try {
         String codedText = URLEncoder.encode(text);
         push("if (parent.message != null) parent.message('"+codedText+"');\n");
      }
      catch(Exception e) {
         Log.error(ME,e.toString());
      }
   }


   /**
    * Calls the error method in the parentframe of the callback frame.
    * <p />
    * See callback.js error() method, which shows an alert window.
    * @param text This must be Javascript code (usually an error string)
    */
   public void error(String text)
   {
      try {
         String codedText = URLEncoder.encode(text);
         push("if (parent.error != null) parent.error('"+codedText+"');\n");
      }
      catch(Exception e) {
         Log.error(ME,e.toString());
      }
   }


   /**
    * calls the ping method in the parentframe of the callback frame
    * The data must be Javascript code
    * @param state The string "refresh"<br />
    *              When login is done successfully, state="loginSucceeded" is sent
    *              one time
    */
   public void ping(String state) throws ServletException, IOException
   {
      push("if (parent.ping != null) parent.ping('" + state + "');\n",false);
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
         if (Log.TRACE) Log.trace(ME, "Received pong, current waitForPong=" + waitForPong + ", counter=" + counter);
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
         if (Log.CALL) Log.call(ME, "Entering constructor HTTP ping interval=" + pingInterval + " millis");
      }
      public void run() {
         if (Log.CALL) Log.call(ME, "Pinging browser ...");
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
                  Log.warn(ME, "Browser seems to have disappeared, no response for my ping=" + counter + ", missing " + waitForPong + " responses. Closing connection.");
                  pushHandler.cleanup();
                  stopThread();
               }
               else {
                  String text = "refresh-" + counter;
                  if (Log.TRACE) Log.trace(ME,"Sending ping '" + text + "'  to browser ...");
                  pushHandler.ping(text);
                  waitForPong++;
               }
            } catch(Exception e) {
               //error handling: browser closed connection.
               Log.warn(ME,"You tried to ping=" + counter + " a browser who is not interested. Close HttpPushHandler.");
               pushHandler.cleanup();
               stopThread();
            }
         }
         if (Log.TRACE) Log.trace(ME, "Ping thread dies ...");
      }
   } // class PingThread


}
