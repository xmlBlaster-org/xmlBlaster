/*------------------------------------------------------------------------------
Name:      SystemInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Servlet to monitor system load on web server
Version:   $Id: SystemInfo.java,v 1.7 2000/05/07 09:49:53 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package html.systemInfo;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.StopWatch;
import org.xmlBlaster.protocol.http.Util;
import org.xmlBlaster.protocol.http.BlasterHttpProxy;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnitContainer;
import org.xmlBlaster.client.*;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;

/**
 * This servlets subscribes to messages in xmlBlaster, which are requested
 * from the browser
 * <p />
 * With Apache/Jserv add this line to zone.properties:<br />
 *   <code>servlet.SystemInfo.code=html.systemInfo.SystemInfo</code>
 */
public class SystemInfo extends HttpServlet
{
   private static final String ME          = "SystemInfo";


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
      if (Log.CALLS) Log.calls(ME, "Entering SystemInfo.doRequest() ...");
      StopWatch stop = new StopWatch();

      String sessionId = request.getRequestedSessionId();
      String actionType = Util.getParameter(request, "ActionType", null);
      String output = "Subscribing to " + actionType + " message ...";

      try {
         if (actionType == null) {
            Log.error(ME, "Missing ActionType. Please call servlet with some ActionType e.g. servlet/SystemInfo?ActionType=cpuinfo");
            htmlOutput("Please call servlet with some ActionType e.g. servlet/SystemInfo?ActionType=cpuinfo", response);
            return;
         }

         CorbaConnection corbaConnection = BlasterHttpProxy.getCorbaConnection(sessionId);
         corbaConnection.initCache(10);

         // Expecting actionType = "cpuinfo" or "meminfo" but it could be
         // any valid key oid.
         Log.info(ME,"Got request for " + actionType + ", sessionId=" + sessionId + " ...");

         SubscribeKeyWrapper xmlKey = new SubscribeKeyWrapper(actionType);
         SubscribeQosWrapper xmlQos = new SubscribeQosWrapper();

         // This get() call does a subscribe() as well behind the scenes, since
         // we invoked corbaConnection.initCache(10); above.
         // Further incoming messages are directly transferred to the browser.
         // We could as well have used corbaConnection.subscribe() here.
         // Usually you would use this get() when you need the result here
         // synchronously, for example to process it with a XSL style sheet ...
         MessageUnitContainer[] msgUnitArr = corbaConnection.get(xmlKey.toXml(), xmlQos.toXml());
         if (msgUnitArr.length == 1) {
            String ret = new String(msgUnitArr[0].msgUnit.content);
            Log.info(ME, "Accessed " + actionType + "=" + ret);
         }
      }
      catch (XmlBlasterException e) {
         String text = "Error from xmlBlaster: " + e.reason;
         Log.error(ME, text);
         popupError(sessionId, text);
      }
      catch (Exception e) {
         e.printStackTrace();
         Log.error(ME, "Error: " + e.toString());
      }

      htmlOutput(output, response);
      Log.trace(ME, "Leaving SystemInfo.doRequest() ..."+stop.nice());
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
         Log.warning(ME, "Could not deliver HTML page to browser:"+e.toString());
         throw new ServletException(e.toString());
      }
   }


   /**
    * Report an error to the browser, which displays it in an alert() message.
    * @param sessionId The browser
    * @param error The text to display
    */
   public void popupError(String sessionId, String error)
   {
      try {
         BlasterHttpProxy.getProxyConnectionBySessionId(sessionId).getHttpPushHandler(sessionId).error(error);
      }
      catch(XmlBlasterException e) {
         Log.error(ME, e.id+":"+e.reason);
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
         Log.warning(ME, text);
         PrintWriter pw;
         try { pw = response.getWriter(); } catch(IOException e2) { Log.error(ME, "2.xml send problem"); return; }
         pw.println("<html><body>Request Problems" + text + "</body></html>");
         pw.close();
      }
   }
}
