/*------------------------------------------------------------------------------
Name:      SystemInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Servlet to monitor system load on web server
Version:   $Id: SystemInfo.java,v 1.1 2000/05/03 13:31:20 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package demo.html.systemInfo;

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
 * This servlets subnscribes to messages in xmlBlaster, which are requested
 * from the browser
 * <p />
 * With Apache/Jserv add this line to zone.properties:
 *   <code>servlet.SystemInfo.code=demo.html.systemInfo.SystemInfo</code>
 *
 * jaco org.xmlBlaster.client.feeder.PublishFile -content 56 -xmlKey "<key oid=\"cpuinfo\" contentMime=\"text/plain\" contentMimeExtended=\"systemInfo\"><TestTag></TestTag> </key>" -xmlQos "<qos><ForceUpdate/></qos>"
 */
public class SystemInfo extends HttpServlet
{
   private static final String ME          = "SystemInfo";
   private static final int closeTime      = 1000;

   public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException
   {
      doGet(request, response);
   }


   /*
    * @param request
    * @param response
    */
   public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException
   {
      if (Log.CALLS) Log.calls(ME, "Entering SystemInfo.doRequest() ...");
      StopWatch stop = new StopWatch();

      String sessionId = request.getRequestedSessionId();
      String actionType = Util.getParameter(request, "ActionType", null);
      String output = "0";

      try {
         if (actionType == null) {
            Log.error(ME, "Missing ActionType. Please call servlet with some ActionType e.g. servlet/SystemInfo?ActionType=cpuinfo");
            htmlOutput("Please call servlet with some ActionType e.g. servlet/SystemInfo?ActionType=cpuinfo", response);
            return;
         }
         else if (actionType.equals("cpuinfo")) {
            Log.info(ME,"Got request for cpuinfo, sessionId=" + sessionId + " ...");
            CorbaConnection corbaConnection = BlasterHttpProxy.getCorbaConnection(sessionId);
            corbaConnection.initCache(100);

            SubscribeKeyWrapper xmlKey = new SubscribeKeyWrapper("cpuinfo");
            SubscribeQosWrapper xmlQos = new SubscribeQosWrapper();

            MessageUnitContainer[] msgUnitArr = corbaConnection.get(xmlKey.toXml(), xmlQos.toXml());
            if (msgUnitArr.length == 1) {
               output = new String(msgUnitArr[0].msgUnit.content);
               Log.info(ME, "Accessed cpuinfo=" + output);
            }
         }
         else if( actionType.equals("meminfo")) {
            Log.info(ME,"Got request for meminfo ...");
         }
         else {
            Log.error(ME, "Unsupported actionType '"+actionType+"'.");
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

      // !!! htmlOutput(comp.toString(), "Message", response);
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
