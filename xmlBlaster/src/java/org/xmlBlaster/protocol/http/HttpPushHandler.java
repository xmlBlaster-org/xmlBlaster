/*------------------------------------------------------------------------------
Name:      HttpPushHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling callback over http
Version:   $Id: HttpPushHandler.java,v 1.9 2000/03/21 00:13:12 kkrafft2 Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import java.rmi.RemoteException;
import java.io.*;
import java.net.URLEncoder;
import javax.servlet.*;
import javax.servlet.http.*;
import org.xmlBlaster.util.*;


/**
 * This handles and hides the different http push modes when sending
 * data back to the browser through method update().
 * <br />
 * Push mode works with keeping a permanent http connection
 * <p />
 * TODO:
 *   HTTP 1.1 specifies rfc2616 that the connection stays open as the
 *   default case. How must this code be changed?
 *
 * @author Marcel Ruff ruff@swand.lake.de
 * @see Java Servlet Programming from Jason Hunter
 */
public class HttpPushHandler
{

   private final String ME = "HttpPushHandler";
   private final long PING_INTERVAL             = 40000L;
   private final HttpServletRequest req;
   private final HttpServletResponse res;
   private boolean closed = false;
   private ServletOutputStream outMulti;
   private PrintWriter outPlain;
   /** The header of the HTML page */
   private String head;
   /** The tail of the HTML page */
   private String tail;
   /** handlesMultipart is true for netscape browser */
   private boolean handlesMultipart = false;

   private HttpPingThread pingThread = null;


   /**
    * Use on browser request.
    * This could (should) be an interface and the different https methods
    * should be implemented in derived classes, but this is better performing :-)
    * @param req The request object
    * @param res The response object
    * @param head The HTML header section including the body start tag
    * @param tail The HTML tail section including the body end tag
    */
   public HttpPushHandler(HttpServletRequest req, HttpServletResponse res, String head, String tail) throws IOException
   {
      this.req = req;
      this.res = res;
      initialize(head, tail);
   }


   /**
    * Use this constructor if you are too lazy to pass a HTML header, a default will be used.
    * @param req The request object
    * @param res The response object
    */
   public HttpPushHandler(HttpServletRequest req, HttpServletResponse res) throws IOException
   {
      this.closed = false;
      this.req = req;
      this.res = res;
      initialize(null, null);

      Log.info(ME,"Creating PingThread ...");
      pingThread = new HttpPingThread( this, PING_INTERVAL );
      pingThread.start();
   }


   /**
    */
   private void initialize(String head, String tail) throws IOException
   {
      if (Log.TRACE) Log.trace(ME, "Creating CallbackHandler ...");
      this.head = head;
      this.tail = tail;
      this.handlesMultipart = doesHandleMultipart();
      if (handlesMultipart)
         this.outMulti =  res.getOutputStream();
      else
         this.outPlain =  res.getWriter();

      Log.info(ME, "Initialize ...");

      if (this.head == null) {
         // this.head = "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML//EN\">" +
         this.head  = "<HTML>\n" +
          "<HEAD>\n" +
          "   <meta http-equiv='Content-Type' content='text/html; charset=iso-8859-1'>\n" +
          "   <meta http-equiv='Pragma' content='no-cache'>\n" +
          "   <meta http-equiv='Expires' content='Tue, 31 Dec 1997 23:59:59 GMT'>\n" +
          "   <TITLE>BlasterHttpProxy Connection</TITLE>\n" +
          "</HEAD>\n" +
          "<BODY>\n" +
          "   <br>&nbsp;\n" +
          "   <br>&nbsp;\n" +
          "   <form ACTION=\"\" METHOD=\"POST\" TARGET=\"callback\">\n" +
          "      <INPUT TYPE=\"HIDDEN\" NAME=\"NoName\" VALUE=\"NoValue\">\n" +
          "   </form>\n" +
          "   <script language='JavaScript'>\n";
      }

      if (this.tail == null) {
         this.tail  = "\n   </script>\n" +
          "</BODY></HTML>";
      }

      if (handlesMultipart) {
         res.setContentType("multipart/x-mixed-replace;boundary=End");

         outMulti.println();
         outMulti.println("--End");
      }
   }

      /**
    */
   public void deinitialize()
   {
      try {
         if (handlesMultipart)
            outMulti.close();
         else
            outPlain.close();

         //pingThread should die in one millisecond
         pingThread.join(1);
      }
      catch(Exception e) {
         Log.error(ME,"Error occurred while de-initializing the push handler :"+e.toString());
      }


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
      this.closed = closed;
   }


   /**
    * Don't forget to call this method when you want to close the connection.
    */
   public void cleanup() throws IOException
   {
      if (handlesMultipart) {
         outMulti.close();
      }
      else {
         outPlain.close();
      }
   }


   /**
    * Determine if the browser can handle multipart pushs.
    * Only Netscape returns true.
    */
   private boolean doesHandleMultipart() throws IOException
   {
      String browser = req.getHeader("User-Agent");
      if (browser == null)
         return false;
      if (browser.indexOf("Mozilla") != -1 && browser.indexOf("MSIE") == -1)
         return true;
      return false;
   }


   /**
    * Updating data to the browser (callback/push mode).
    * The data must be Javascript code
    */
   public void push(String str) throws ServletException, IOException
   {
      if (handlesMultipart) {
         /* Every line which is sent to the browser overwrites the former one
            Problems: (Linux/netscape)
            1. The watch-wait cursor is displayed, until the doGet() leaves.
            2. Resizing the browser window doesn't resize the content.
         */
            outMulti.println("Content-Type: text/html");
            outMulti.println();

            StringBuffer buf = new StringBuffer(head);
            buf.append(str);
            buf.append(tail);

            if (Log.DUMP) Log.dump(ME, "Sending to callbackFrame:\n" + buf.toString());

            outMulti.println(buf.toString());

            outMulti.println();
            outMulti.println("--End");
            outMulti.flush();
      }
      else {
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
            res.setContentType("text/html");

            outPlain.println(head);

            outPlain.println(str);

            // This newline forces a refresh everytime,
            // only necessary if the str fills less then one line in the netscape browser window
            //outPlain.println("<P />");

            outPlain.println(tail);

            outPlain.flush();
            outPlain.close();
      }
   }


   /**
    * calls the update method in the parentframe of the callback frame
    * The data must be Javascript code
    */
   public void update( String updateKey, String content, String updateQos )
   {
      try {
         String codedKey               = URLEncoder.encode( updateKey );
         String codedContent           = URLEncoder.encode( content );
         String codedQos               = URLEncoder.encode( updateQos );

         Log.info(ME,"************* Update an Browser *********************");
         Log.plain(ME,"Key:"+updateKey);
         Log.plain(ME,"\nContent:"+content);
         Log.info(ME,"************* End of Update *************************");
                                                    
         String pushStr = "if (parent.update != null) parent.update('"+codedKey+"','"+codedContent+"','"+codedQos+"');\n";
         push(pushStr);
      }
      catch(Exception e) {
         Log.error(ME,e.toString());
      }
   }


   /**
    * calls the message method in the parentframe of the callback frame
    * The data must be Javascript code
    */
   public void message( String text )
   {
      try {
         String codedText = URLEncoder.encode( text );
         push("if (parent.message != null) parent.message('"+codedText+"');\n");
      }
      catch(Exception e) {
         Log.error(ME,e.toString());
      }
   }

      /**
    * calls the ping method in the parentframe of the callback frame
    * The data must be Javascript code
    */
   public void ping() throws ServletException, IOException
   {
      push("if (parent.ping != null) parent.ping();\n");
   }


      /**
    * Ping the xmlBlaster server, to test if connection is alive
    */
   private class HttpPingThread extends Thread
   {
      private final String ME = "HttpPingThread";
      private HttpPushHandler pushHandler;
      private final long PING_INTERVAL;
      boolean pingRunning = true;

      /**
       * @param pingInterval How many milli seconds sleeping between the pings
       */
      HttpPingThread(HttpPushHandler pushHandler, long pingInterval) {
         this.pushHandler = pushHandler;
         this.PING_INTERVAL = pingInterval;
         if (Log.CALLS) Log.calls(ME, "Entering constructor HTTP ping interval=" + pingInterval + " millis");
      }
      public void run() {
         Log.info(ME, "Pinging browser ...");
         while (pingRunning) {
            try {
               Thread.currentThread().sleep(PING_INTERVAL);
            } catch (InterruptedException i) { }
            try {
               if(Log.TRACE) Log.trace(ME,"pinging the Browser ...");
               pushHandler.ping();
            } catch(Exception e) {
               //error handling: browser closed connection.
               Log.warning(ME,"You tried to ping a browser who is not interested. Close HttpPushHandler.");
               pushHandler.setClosed( true );
               pingRunning = false;
            }
         }
      }
   } // class PingThread


}
