/*------------------------------------------------------------------------------
Name:      CallbackServletDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling callback over http
Version:   $Id: CallbackServletDriver.java,v 1.2 2000/02/23 06:14:52 jsrbirch Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import java.rmi.RemoteException;
import java.io.*;
import java.util.*;
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
 * With the doPost() method you may login/logout to xmlBlaster, and
 * do your work with publish/subscribe etc.<br />
 * With the doGet() method you receive your instant callbacks.
 * <p />
 * The logging output is redirected to the normal servlet log file.
 * If you use Apache/Jserv, look into /var/log/httpd/jserv.log
 * <p />
 * Invoke for testing:<br />
 *    http://localhost/servlet/CallbackServletDriver?ActionType=login&loginName=martin&passwd=secret
 * <p />
 * TODO:
 *   HTTP 1.1 specifies rfc2616 that the connection stays open as the
 *   default case. How must this code be changed?
 * @author Marcel Ruff ruff@swand.lake.de
 * @version $Revision: 1.2 $
 */
public class CallbackServletDriver extends HttpServlet implements org.xmlBlaster.client.I_Callback, org.xmlBlaster.util.LogListener
{
   private final String ME = "CallbackServletDriver";
   /** Mapping the sessionId to a ServletConnection instance */
   private Hashtable sessionIdHash = new Hashtable();
   /** Mapping the loginName to a ServletConnection instance */
   private Hashtable loginNameHash = new Hashtable();

   /**
    * This method is invoked only once when the servlet is startet.
    * @param conf init parameter of the servlet
    */
   public void init(ServletConfig conf) throws ServletException
   {
      super.init(conf);
      // Redirect xmlBlaster logs to servlet log file (see method log() below)
      Log.setDefaultLogLevel();
      Log.addLogLevel("DUMP");
      Log.addLogLevel("TRACE");
      Log.addLogLevel("CALLS");
      Log.addLogLevel("TIME");
      Log.addLogListener(this);
      Log.info(ME, "Initialize ...");
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
      Log.info(ME, "Entering CallbackServletDriver servlet for sessionId=" + sessionId);
      ServletConnection servletConnection = (ServletConnection)sessionIdHash.get(sessionId);
      Server xmlBlaster = servletConnection.getXmlBlaster();

      StringBuffer retStr = new StringBuffer();
      try {
         String actionType = req.getParameter("ActionType");

         if (actionType!=null && actionType.equals("logout")) {
            Log.info(ME, "Logout ActionType arrived ...");
            servletConnection.logout();
            sessionIdHash.remove(sessionId);
            loginNameHash.remove(servletConnection.getLoginName());
         }

         else if (actionType!=null && actionType.equals("subscribe")) {
            Log.info(ME, "subscribe arrived ...");
            String xmlKey =
                      "<key oid='' queryType='XPATH'>\n" +
                      "   //key" +
                      "</key>";
            String qos = "<qos></qos>";
            try {
               String subscribeOid = xmlBlaster.subscribe(xmlKey, qos);
               Log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
            } catch(XmlBlasterException e) {
               Log.warning(ME, "XmlBlasterException: " + e.reason);
            }
         }

         else if (actionType!=null && actionType.equals("unSubscribe")) {
            Log.info(ME, "unSubscribe arrived ...");
         }

         else if (actionType!=null && actionType.equals("get")) {
            throw new Exception("Synchronous ActionType=get is not supported");
         }

         else if (actionType!=null && actionType.equals("publish")) {
            Log.info(ME, "publish arrived ...");
            String xmlKey =
                      "<key oid='HelloWorld' contentMime='text/plain' contentMimeExtended='-'>\n" +
                      "</key>";
            String qos = "<qos></qos>";
            String content = "Hello world";
            MessageUnit messageUnit = new MessageUnit(xmlKey, content.getBytes());
            try {
               String publishOid = xmlBlaster.publish(messageUnit, qos);
               Log.info(ME, "Success: Publishing done, returned oid=" + publishOid);
            } catch(XmlBlasterException e) {
               Log.warning(ME, "XmlBlasterException: " + e.reason);
            }
         }

         else if (actionType!=null && actionType.equals("erase")) {
            Log.info(ME, "erase arrived ...");
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
    * GET request from the browser
    * Used for login and for keeping a permanent http connection
    */
   public void doGet(HttpServletRequest req, HttpServletResponse res)
                                 throws ServletException, IOException
   {
      res.setContentType("text/html");
      StringBuffer retStr = new StringBuffer("");
      String errorText="";
      CallbackHandler callbackHandler = new CallbackHandler(req, res);

      HttpSession session = req.getSession(true);

      String sessionId = req.getRequestedSessionId();
      String loginName = req.getParameter("loginName");    // "Joe";
      String passwd = req.getParameter("passwd");  // "secret";
      Log.info(ME, "Entering CallbackServletDriver servlet for '" + loginName + "', sessionId=" + sessionId);

      ServletConnection servletConnection = null;
      Server xmlBlaster = null;

      try {
         String actionType = req.getParameter("ActionType");

         if (actionType!=null && actionType.equals("login")) {
            Log.info(ME, "Login action ...");

            if (loginName == null || loginName.length() < 1)
               throw new Exception("Missing login name");
            if (passwd == null || passwd.length() < 1)
               throw new Exception("Missing passwd");

            // Find orb
            servletConnection = new ServletConnection(getServletConfig(), callbackHandler, sessionId, loginName);

            // Login to xmlBlaster
            String qos = "<qos></qos>";
            xmlBlaster = servletConnection.login(loginName, passwd, qos, this);

            sessionIdHash.put(sessionId, servletConnection);
            loginNameHash.put(loginName, servletConnection);

            // confirm successful login:
            servletConnection.update("/*if (top.loginSuccess != null) */top.loginSuccess(true);\n");

            // Don't fall out of doGet() to keep the HTTP connection open
            Log.info(ME, "Waiting forever, permanent HTTP connection ...");
            Thread.currentThread().join();
         }
      } catch (XmlBlasterException e) {
         Log.error(ME, "Caught XmlBlaster Exception: " + e.reason);
         errorText = e.reason;
      } catch (Exception e) {
         Log.error(ME, "Caught Exception: " + e.toString());
         errorText = e.toString();
         e.printStackTrace();
      } finally {
         Log.info(ME, "Entering finally of permanent connection");

         if (servletConnection != null) {
            if (Log.DUMP) Log.dump(ME, retStr.toString());
            servletConnection.update(retStr.toString());
         }
         else {
            retStr.append(alert("Sorry, login to XmlBlaster failed\n\n" + errorText));
            if (Log.DUMP) Log.dump(ME, retStr.toString());
            servletConnection.update(retStr.toString());
         }
         servletConnection.cleanup();
      }
   }


   /**
    * This is the callback method (I_Callback) invoked from ServletConnection
    * informing the client in an asynchronous mode about a new message.<br />
    * This message is then forwarded to the browser, using the permanent
    * http connection in the doGet() method.
    * <p />
    * The raw CORBA-BlasterCallback.update() is unpacked and for each arrived message
    * this update is called.
    *
    * @param loginName The name to whom the callback belongs
    * @param updateKey The arrived key
    * @param content   The arrived message content
    * @param qos       Quality of Service of the MessageUnit
    */
   public void update(String loginName, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS)
   {
      if (Log.CALLS) Log.calls(ME, "Receiving update of a message from xmlBlaster ...");
      Log.info(ME, "Receiving update of a message from xmlBlaster ...");
      ServletConnection servletConnection = (ServletConnection)sessionIdHash.get(loginName);
      // updateQoS.getSender(), updateKey.getUniqueKey(), new String(content), updateKey.getContentMimeExtended()
      // servletConnection.update();
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
    * @param der Fehlertext
    * @see azu.js mit Javascript für Popup
    */
   public final String alert(String text)
   {
      StringBuffer retStr = new StringBuffer();
      retStr.append("alert(\"" + text.replace('\n', ' ') + "\");\n");
      Log.warning(ME, "Sending alert to browser: " + text);
      return retStr.toString();
   }

}
