/*------------------------------------------------------------------------------
Name:      BlasterHttpProxyServlet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling callback over http
Version:   $Id: BlasterHttpProxyServlet.java,v 1.16 2000/05/06 20:33:44 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import java.rmi.RemoteException;
import java.io.*;
import java.util.*;
import java.net.URLEncoder;
import javax.servlet.*;
import javax.servlet.http.*;
import org.xmlBlaster.client.*;
import org.xmlBlaster.util.*;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;


/**
 * This servlet doesn't leave the doGet() method after an invocation
 * keeping a permanent http connection. 
 * <p />
 * With the doGet() method you may login/logout to xmlBlaster, and
 * receive your instant callbacks.<br />
 * With the doPost() you can invoke publish/subscribe etc.<br />
 * <p />
 * The logging output is redirected to the normal servlet log file.
 * If you use Apache/Jserv, look into /var/log/httpd/jserv.log
 * <p />
 * Invoke for testing:<br />
 *    http://localhost/servlet/BlasterHttpProxyServlet?ActionType=login&loginName=martin&passwd=secret
 * @author Marcel Ruff ruff@swand.lake.de
 * @version $Revision: 1.16 $
 */
public class BlasterHttpProxyServlet extends HttpServlet implements org.xmlBlaster.util.LogListener
{
   private final String ME = "BlasterHttpProxyServlet";

   /**
    * This method is invoked only once when the servlet is startet.
    * @param conf init parameter of the servlet
    */
   public void init(ServletConfig conf) throws ServletException
   {
      super.init(conf);
      // Redirect xmlBlaster logs to servlet log file (see method log() below)
      Log.setDefaultLogLevel();
      // Log.addLogLevel("DUMP");  // Use this to see all messages!
      // Log.addLogLevel("TRACE");
      // Log.addLogLevel("CALLS");
      Log.addLogLevel("TIME");
      // Log.addLogListener(this);

      Log.trace(ME, "Initialize ...");
   }


   /**
    * GET request from the browser, usually to do an initial login. 
    * <p />
    * Used for login and for keeping a permanent http connection.
    * <br />
    * The sessionId from the login is delivered back to the browser,
    * and will be used for all following calls to this and other servlets.
    * <br />
    * It is important that this login servlet generates the sessionId
    * and no other servlet generates one - so call other servlets *after*
    * successful login.
    * <p />
    * Example:<br />
    *  <code>index.html?ActionType=login&loginName=karl&passwd=secret</code>
    */
   public void doGet(HttpServletRequest req, HttpServletResponse res)
                                 throws ServletException, IOException
   {
      if (Log.TRACE) Log.trace(ME, "Entering doGet() ... " + Memory.getStatistic());
      res.setContentType("text/html");
      StringBuffer retStr = new StringBuffer("");
      String errorText="";

      HttpSession session = req.getSession(true);
      String sessionId = session.getId();
      if (Log.TRACE) Log.trace(ME, "Entering doGet() for sessionId=" + sessionId);

/*
      if(!req.isRequestedSessionIdFromCookie()) { // && isCookieEnabled() ?????
         pushHandler.push("alert('Sorry, your browser does not support cookies, you will not get updates from xmlBlaster."+sessionId+"');\n",false);
         pushHandler.cleanup();
         Log.error(ME, "Cookies are not supported by the browser.");
         return;
      }
      else{
         Log.info(ME,"Cookies are supported.");
      }
*/

      try {
         String actionType = Util.getParameter(req, "ActionType", "");

         //------------------ Login -------------------------------------------------
         if (actionType.equals("login")) {

            String loginName = Util.getParameter(req, "loginName", null);    // "Joe";
            if (loginName == null || loginName.length() < 1)
               throw new XmlBlasterException(ME, "Missing login name");
            String passwd = Util.getParameter(req, "passwd", null);  // "secret";
            if (passwd == null || passwd.length() < 1)
               throw new XmlBlasterException(ME, "Missing passwd");

            Log.info(ME, "Login action for user " + loginName);

            HttpPushHandler pushHandler = new HttpPushHandler(req, res);

            // Find proxyConnection !!Attention, other browser can use an existing
            //                        xmlBlaster connection. This is a security problem.
            ProxyConnection proxyConnection = BlasterHttpProxy.getProxyConnection( loginName, passwd );
            pushHandler.startPing();

            proxyConnection.addHttpPushHandler( sessionId, pushHandler );

            // Don't fall out of doGet() to keep the HTTP connection open
            Log.info(ME, "Waiting forever, permanent HTTP connection. loginName=" + loginName + " sessionId=" + sessionId);

            pushHandler.ping("loginSucceeded");

            while (!pushHandler.closed()) {
               try {
                  Thread.currentThread().sleep(10000L);
               }
               catch (InterruptedException i) {
                  Log.error(ME,"Error in Thread handling, don't know what to do: "+i.toString());
               }
            }

            //Thread.currentThread().join();

            pushHandler.deinitialize();
            proxyConnection.removeHttpPushHandler( sessionId, pushHandler );

            Log.trace(ME, "Permamenent HTTP connection lost, leaving BlasterHttpProxyServlet.doGet(sessionId=" + sessionId + ") ....");
         }


         //------------------ ready --------------------------------------------------
         else if(actionType.equals("browserReady")) {
            try {
               HttpPushHandler pushHandler = BlasterHttpProxy.getHttpPushHandler(sessionId);
               pushHandler.setReady( true );

               // Otherwise the browser (controlFrame) complains 'document contained no data'
               PrintWriter out = res.getWriter();
               out.println(" <html><body text='white' bgcolor='white'>Empty response for your ActionType='browserReady'</body></html>");
               return;
            }
            catch (XmlBlasterException e) {
               Log.error(ME, "Caught XmlBlaster Exception: " + e.reason);
               return;
            }
         }
         else {
            String text = "Unknown ActionType '" + actionType + "', request for permanent http connection ignored";
            throw new XmlBlasterException(ME, text);
         }


      } catch (XmlBlasterException e) {
         Log.error(ME, "Caught XmlBlaster Exception: " + e.reason);
         String codedText = URLEncoder.encode( e.reason );
         try {
            HttpPushHandler pushHandler = BlasterHttpProxy.getHttpPushHandler(sessionId);
            pushHandler.push("if (parent.error != null) parent.error('"+codedText+"');\n",false);
         } catch (XmlBlasterException e2) {
         }
      } catch (Exception e) {
         Log.error(ME, "Caught Exception: " + e.toString());
         try {
            HttpPushHandler pushHandler = BlasterHttpProxy.getHttpPushHandler(sessionId);
            pushHandler.push("if (parent.error != null) parent.error('"+e.toString()+"');\n",false);
         } catch (XmlBlasterException e2) {
         }
         e.printStackTrace();
      }
   }


   /**
    * POST request from the browser.
    * Handles the following requests 'ActionType' from the browser<br />
    * <ul>
    *    <li>logout</li>
    *    <li>get - The synchronous get is not supported</li>
    *    <li>subscribe</li>
    *    <li>unSubscribe</li>
    *    <li>publish</li>
    *    <li>erase</li>
    * </ul>
    * <p>
    * This method is called through a SUBMIT of a HTML FORM,<br>
    * the TARGET should be set to "callbackFrame".
    * The parameter ActionType must be set to one of the above methods.<br />
    * For an explanation of these methods see the file xmlBlaster.idl
    * @param req Data from browser
    * @param res Response of the servlet
    */
   public void doPost(HttpServletRequest req, HttpServletResponse res)
                               throws ServletException, IOException
   {
      res.setContentType("text/html");
      PrintWriter out = res.getWriter();

      //HttpSession session = req.getSession();
      HttpSession session = req.getSession(true);
      String sessionId = req.getRequestedSessionId();
      Log.info(ME, "Entering BlasterHttpProxy.doPost() servlet for sessionId=" + sessionId);
      ProxyConnection proxyConnection = null;
      Server xmlBlaster = null;
      HttpPushHandler pushHandler = null;

      try {
         proxyConnection = BlasterHttpProxy.getProxyConnectionBySessionId(sessionId);
         if( proxyConnection == null ) {
            throw new XmlBlasterException(ME, "Session not registered yet (sessionId="+sessionId+")");
         }
         xmlBlaster = proxyConnection.getXmlBlaster();
         pushHandler = proxyConnection.getHttpPushHandler(sessionId);
      }
      catch (XmlBlasterException e) {
         Log.error(ME, "Caught XmlBlaster Exception: " + e.reason);
         return;
      }

      StringBuffer retStr = new StringBuffer();
      try {
         String actionType = Util.getParameter(req, "ActionType", "NONE");

         if (actionType.equals("logout")) {
            Log.trace(ME, "Logout ActionType arrived ...");
         }

         else if (actionType.equals("subscribe")) {
            Log.trace(ME, "subscribe arrived ...");
            String xmlKey =
                      "<key oid='' queryType='XPATH'>\n" +
                      "   //key" +
                      "</key>";
            String qos = "<qos></qos>";
            try {
               String subscribeOid = xmlBlaster.subscribe(xmlKey, qos);
               Log.trace(ME, "Success: Subscribe on " + subscribeOid + " done");
            } catch(XmlBlasterException e) {
               Log.warning(ME, "XmlBlasterException: " + e.reason);
            }
         }

         else if (actionType.equals("unSubscribe")) {
            Log.trace(ME, "unSubscribe arrived ...");
         }

         else if (actionType.equals("get")) {
            throw new Exception("Synchronous ActionType=get is not supported");
         }

         else if (actionType.equals("publish")) {
            Log.trace(ME, "publish arrived ...");
            String xmlKey =
                      "<key oid='HelloWorld' contentMime='text/plain' contentMimeExtended='-'>\n" +
                      "</key>";
            String qos = "<qos></qos>";
            String content = "Hello world";
            MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes());
            try {
               String publishOid = xmlBlaster.publish(msgUnit, qos);
               Log.trace(ME, "Success: Publishing done, returned oid=" + publishOid);
            } catch(XmlBlasterException e) {
               Log.warning(ME, "XmlBlasterException: " + e.reason);
            }
         }

         else if (actionType.equals("erase")) {
            Log.trace(ME, "erase arrived ...");
         }

         else {
            throw new Exception("Unknown or missing 'ActionType'");
         }

      } catch (Exception e) {
         Log.error(ME, "RemoteException: " + e.getMessage());
         e.printStackTrace();
         retStr.append("<body>http communication problem</body>");
      } finally {
         out.println(retStr.toString());
      }
   }


   /**
    * Event fired by Log.java through interface LogListener.
    * <p />
    * Log output from Log.info(); etc. into Servlet log file.
    * <p />
    * Note that System.err.println("Hello"); will be printed into
    * the Apache error log file /var/log/httpd.error_log<br />
    * I don't know what other web servers are doing with it.
    * <p />
    * System.out.println("Hello"); will be printed to the console
    * where you started the servlet engine.
    */
   public void log(String str)
   {
      getServletContext().log(str);
   }


   /**
    * Display a popup alert message containing the error text
    *
    * @param text The error text
    */
   public final String alert(String text)
   {
      StringBuffer retStr = new StringBuffer();
      retStr.append("alert(\"" + text.replace('\n', ' ') + "\");\n");
      Log.warning(ME, "Sending alert to browser: " + text);
      return retStr.toString();
   }


}
