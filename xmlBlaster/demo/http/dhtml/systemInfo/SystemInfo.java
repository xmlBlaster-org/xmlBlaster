/*------------------------------------------------------------------------------
Name:      SystemInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Servlet to monitor system load on web server
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package http.dhtml.systemInfo;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.jutils.time.StopWatch;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.http.Util;
import org.xmlBlaster.protocol.http.BlasterHttpProxy;
import org.xmlBlaster.protocol.http.HttpPushHandler;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;

/**
 * A servlet demonstrating the browser callback framework, using a persistent
 * http connection.
 * <p />
 * This servlets subscribes to messages in xmlBlaster, which are requested
 * from the browser, currently 'cpuinfo' and 'meminfo' messages.<br />
 * You see in your browser two bars displaying the current load of the
 * xmlBlaster.org web server.<br />
 * Inside the browser the bars are updated with DHTML.
 * <p />
 * If you want to do something similar, you can use this as a base
 * for your application.
 * <p />
 * See xmlBlaster/demo/http/README for further informations
 */
public class SystemInfo extends HttpServlet
{
   private static final String ME = "SystemInfo";
   private static Logger log = Logger.getLogger(SystemInfo.class.getName());


   /**
    * This method is invoked only once when the servlet is started.
    * @param conf init parameter of the servlet
    */
   public void init(ServletConfig conf) throws ServletException
   {
      super.init(conf);

   }


   /**
    * dummy
    */
   public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException
   {
      doGet(request, response);
   }


   /**
    * Subscribes to xmlBlaster messages 'cpuinfo' and 'meminfo'.
    * <p />
    * The message updates are received asynchronous over the callbackFrame.
    * <br />
    * The return from this doGet() may be ignored
    * <p />
    * Invoking example:
    * <br />
    * "/servlet/SystemInfo?ActionType=cpuinfo"
    * @param request
    * @param response
    */
   public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering SystemInfo.doRequest() ...");
      StopWatch stop = new StopWatch();

      String sessionId = request.getRequestedSessionId();
      String actionType = Util.getParameter(request, "ActionType", null);
      String output = "Subscribing to " + actionType + " message ...";

      try {
         if (actionType == null) {
            String str = "Please call servlet with some ActionType e.g. xmlBlaster/dhtml/systemInfo?ActionType=cpuinfo";
            log.severe(str);
            htmlOutput(str, response);
            return;
         }

         I_XmlBlasterAccess corbaConnection = BlasterHttpProxy.getXmlBlasterAccess(sessionId);
         if (corbaConnection == null) {
            String text = "Your Session ID is not valid, please try again with cookies enabled";
            log.severe(text);
            popupError(response, text);
            return;
         }

         // Expecting actionType = "cpuinfo" or "meminfo" but it could be
         // any valid key oid.
         log.info("Got request for " + actionType + ", sessionId=" + sessionId + " ...");

         SubscribeKey xmlKey = new SubscribeKey(null, actionType);
         SubscribeQos xmlQos = new SubscribeQos(null);

         String ret = corbaConnection.subscribe(xmlKey.toXml(), xmlQos.toXml()).getSubscriptionId();
         log.info("Subscribed to " + actionType + "=" + ret);

         // NOTE: The callback messages (update()) are handled by our
         // BlasterHttpProxyServlet framework and pushed to the browser.
         // Typically a browser (or applet) can subscribe itself directly at BlasterHttpProxyServlet
         // so there is no need for this servlet
      }
      catch (XmlBlasterException e) {
         String text = "Error from xmlBlaster: " + e.getMessage();
         log.severe(text);
         popupError(response, text);
         return;
      }
      catch (Exception e) {
         e.printStackTrace();
         log.severe("Error in doGet(): " + e.toString());
         popupError(response, e.toString());
         return;
      }

      htmlOutput(output, response);
      log.fine("Leaving SystemInfo.doRequest() ..."+stop.nice());
      System.gc();
   }


   /**
    * Returns a HTML file to the Browser.
    * @param htmlData the complete HTML page
    * @param response the servlet response-object
    * @see HttpServletResponse
    */
   public void htmlOutput(String htmlData, HttpServletResponse response) throws ServletException
   {
      response.setContentType("text/html");
      try {
         PrintWriter pw;
         pw = response.getWriter();
         pw.println(htmlData);
         pw.close();
      }
      catch(IOException e) {
         log.warning("Could not deliver HTML page to browser:"+e.toString());
         throw new ServletException(e.toString());
      }
   }


   /**
    * Report an error to the browser, which displays it in an alert() message.
    * @param sessionId The browser
    * @param error The text to display
    */
   public void popupError(HttpServletResponse response, String error)
   {
      try {
         response.setContentType("text/html");
         PrintWriter pw;
         pw = response.getWriter();
         pw.println(HttpPushHandler.alert(error));
         pw.close();
      }
      catch(IOException e) {
         log.severe("Sending of error failed: " + error + "\n Reason=" + e.toString());
      }
   }


   /**
    * Send XML-Data to browser.
    * The browser needs to handle the data.
    * @param xmlData XML data
    * @param response servlet response
    */
   public void xmlOutput( String xmlData, HttpServletResponse response ) throws ServletException
   {
      response.setContentType("text/xml");

      try {
         PrintWriter pw;
         pw = response.getWriter();
         pw.println(xmlData);
         pw.close();
      }
      catch(IOException e) {
         String text = "Sending XML data to browser failed: " + e.toString();
         log.warning(text);
         PrintWriter pw;
         try { pw = response.getWriter(); } catch(IOException e2) { log.severe("2.xml send problem"); return; }
         pw.println("<html><body>Request Problems" + text + "</body></html>");
         pw.close();
      }
   }
}
