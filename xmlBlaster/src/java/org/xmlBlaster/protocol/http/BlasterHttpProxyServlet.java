/*------------------------------------------------------------------------------
Name:      BlasterHttpProxyServlet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling callback over http
Version:   $Id: BlasterHttpProxyServlet.java,v 1.61 2002/09/13 23:18:10 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.jutils.runtime.Memory;
import org.jutils.time.TimeHelper;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlQoSBase;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.PublishRetQos;
import org.xmlBlaster.client.SubscribeRetQos;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.client.*;

import java.rmi.RemoteException;
import java.io.*;
import java.util.*;
import java.net.URLEncoder;
import java.net.URLDecoder;

import javax.servlet.*;
import javax.servlet.http.*;


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
 *    http://localhost/xmlBlaster/BlasterHttpProxyServlet?ActionType=login&xmlBlaster.loginName=martin&xmlBlaster.passwd=secret
 * @author Marcel Ruff ruff@swand.lake.de
 */
public class BlasterHttpProxyServlet extends HttpServlet implements org.jutils.log.LogableDevice
{
   private static boolean propertyRead = false;
   private final String header = "<html><meta http-equiv='no-cache'><meta http-equiv='Cache-Control' content='no-cache'><meta http-equiv='expires' content='Wed, 26 Feb 1997 08:21:57 GMT'>";
   private Global glob = null;
   private LogChannel log;

   /**
    * This method is invoked only once when the servlet is startet.
    * @param conf init parameter of the servlet
    */
   public void init(ServletConfig conf) throws ServletException
   {
      super.init(conf);

      if (!propertyRead) {
         propertyRead = true;
         // Add the web.xml parameters to our environment settings:
         Enumeration enum = conf.getInitParameterNames();
         int count = 0;
         while(enum.hasMoreElements()) {
            if (enum.nextElement() == null)
               continue;
            count++;
         }
         String[] args = new String[2*count];

         count = 0;
         enum = conf.getInitParameterNames();
         while(enum.hasMoreElements()) {
            String name = (String)enum.nextElement();
            if (name == null)
               continue;
            if (!name.startsWith("-"))
               args[count++] = "-" + name;
            else
               args[count++] = name;
            args[count++] = (String)conf.getInitParameter(name);
            //Global.instance().getLog(null).info("", "Reading web.xml property " + args[count-2] + "=" + args[count-1]);
         }

         glob = new Global();
         int ret = glob.init(args);
         log = glob.getLog(null);
         if (ret != 0) {
            // init problems
         }
      }

      // Redirect xmlBlaster logs to servlet log file (see method log() below)
      // Use xmlBlaster/demo/http/WEB-INF/web.xml to configure logging.

      // To redirect your Logging output into the servlet logfile (jserv.log),
      // outcomment this line:
      //log.addLogableDevice(this);

      log.trace("BlasterHttpProxyServlet", "Initialize ...");

      initSystemProperties(conf); // Using JacORB and Suns XML parser as a default ...
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
    *  <code>index.html?ActionType=login&xmlBlaster.loginName=karl&xmlBlaster.passwd=secret</code>
    */
   public void doGet(HttpServletRequest req, HttpServletResponse res)
                                 throws ServletException, IOException
   {
      if (log.CALL) log.call("BlasterHttpProxyServlet", "Entering doGet() ... " + Memory.getStatistic());
      res.setContentType("text/html");
      StringBuffer retStr = new StringBuffer("");
      String errorText="";
      String ME  = "BlasterHttpProxyServlet-" + req.getRemoteAddr();

      String actionType = Util.getParameter(req, "ActionType", "");
      if (actionType == null) {
         String str = "Please call servlet with some ActionType";
         log.error(ME, str);
         htmlOutput(str, res);
         return;
      }

      HttpSession session = req.getSession(true);
      if (actionType.equals("login")) {
         boolean invalidate = Util.getParameter(req, "xmlBlaster.invalidate", true);
         if (invalidate == true)
            session.invalidate();   // force a new sessionId
         session = req.getSession(true);
      }
      String sessionId = session.getId();

      ME += "-" + sessionId;
      if (log.TRACE) log.trace(ME, "Processing doGet()");

      if (sessionId == null) {
         PrintWriter out = res.getWriter();
         out.println(HttpPushHandler.alert("Sorry, your sessionId is invalid"));
         return;
      }

      try {
         //------------------ Login -------------------------------------------------
         if (actionType.equals("login")) {

            String loginName = Util.getParameter(req, "xmlBlaster.loginName", null);    // "Joe";
            if (loginName == null || loginName.length() < 1)
               throw new XmlBlasterException(ME, "Missing login name. Pass xmlBlaster.loginName=xy with your URL or in your cookie.");
            String passwd = Util.getParameter(req, "xmlBlaster.passwd", null);  // "secret";
            if (passwd == null || passwd.length() < 1)
               throw new XmlBlasterException(ME, "Missing passwd");

            ME  = "BlasterHttpProxyServlet-" + req.getRemoteAddr() + "-" + loginName + "-" + sessionId;
            log.info(ME, "Login action");

            HttpPushHandler pushHandler = new HttpPushHandler(req, res, sessionId, loginName);

            ProxyConnection proxyConnection = BlasterHttpProxy.getNewProxyConnection(glob, loginName, passwd);
            pushHandler.startPing();

            proxyConnection.addHttpPushHandler( sessionId, pushHandler );

            // Don't fall out of doGet() to keep the HTTP connection open
            log.info(ME, "Waiting forever, permanent HTTP connection from " +
                          req.getRemoteHost() + "/" + req.getRemoteAddr() +
                          ", loginName=" + loginName + " sessionId=" + sessionId +
                          "', protocol='" + req.getProtocol() +
                          "', agent='" + req.getHeader("User-Agent") +
                          "', referer='" + req.getHeader("Referer") +
                          "'.");

            if (log.TRACE) log.trace(ME,
                          "user='" + req.getRemoteUser() +
                          "', serverPort='" + req.getServerPort() +
                          "', query='" + req.getQueryString() +
                          "', pathInfo='" + req.getPathInfo() +
                          "', pathTranslated='" + req.getPathTranslated() +
                          "', servletPath='" + req.getServletPath() +
                          "', documentRoot='" + getServletConfig().getServletContext().getRealPath("/") +
                          "', accept='" + req.getHeader("Accept") +
                          "', referer='" + req.getHeader("Referer") +
                          "', authorization='" + req.getHeader("Authorization") +
                          "'.");

            pushHandler.setBrowserIsReady( true );
            pushHandler.ping("loginSucceeded");

            while (!pushHandler.closed()) {
               try {
                  Thread.currentThread().sleep(10000L);
               }
               catch (InterruptedException i) {
                  log.error(ME,"Error in Thread handling, don't know what to do: "+i.toString());
                  proxyConnection.cleanup(sessionId);
                  break;
               }
            }
            pushHandler = null;
            log.info(ME, "Persistent HTTP connection lost, leaving doGet() ....");
            /*
            System.out.println("Currently consumed threads:");
            System.out.println("===========================");
            org.jutils.runtime.ThreadLister.listAllThreads(System.out);
            */
         }


         //------------------ ready, browser processed last message --------------------------
         // The HttpPushHandler adds javascript 'parent.browserReady();' which
         // is invoked after the browser is ready.
         else if(actionType.equals("browserReady")) {
            try {
               HttpPushHandler pushHandler = BlasterHttpProxy.getHttpPushHandler(sessionId);
               if (log.TRACE) log.trace(ME, "Received 'browserReady'");
               pushHandler.setBrowserIsReady( true );

               // Otherwise the browser (controlFrame) complains 'document contained no data'
               PrintWriter out = res.getWriter();
               out.println(header+"<body text='white' bgcolor='white'>Empty response for your ActionType='browserReady' " + System.currentTimeMillis() + "</body></html>");
               return;
            }
            catch (XmlBlasterException e) {
               log.error(ME, "Caught XmlBlaster Exception for actionType '" + actionType + "': " + e.reason);
               return;
            }
         }
         //------------------ answer of a ping -----------------------------------------------
         // The HttpPushHandler adds javascript 'parent.ping();' which
         // pings the browser to hold the http connection.
         // The browser responses with 'pong', to allow the servlet to
         // detect if the browser is alive.
         // Locally this works fine, but over the internet the second or third pong from the browser
         // was never reaching this servlet. Adding some dynamic content/URL helped a bit,
         // but after some ten pongs, the following pongs where lost.
         // The browserReady request hasn't got this problem, why??
         // So we do a pong on browserReady as well, which solved the problem (see HttpPushHandler.java)
         else if(actionType.equals("pong")) {
            try {
               HttpPushHandler pushHandler = BlasterHttpProxy.getHttpPushHandler(sessionId);
               pushHandler.pong();

               // state is only for debugging and to avoid internet proxies to discard this content (since it has not changed)
               if (log.TRACE) log.trace(ME, "Received pong '" + Util.getParameter(req, "state", "noState") + "'");

               // Otherwise the browser (controlFrame) complains 'document contained no data'
               PrintWriter out = res.getWriter();
               out.println(header+"<body text='white' bgcolor='white'>Empty response for your ActionType='pong' " + Util.getParameter(req, "state", "noState") + " " + System.currentTimeMillis() + "</body></html>");
               return;
            }
            catch (XmlBlasterException e) {
               log.error(ME, "Caught XmlBlaster Exception for actionType '" + actionType + "': " + e.reason);
               return;
            }
         }

         else if ("publish".equalsIgnoreCase(actionType)) {
            doPost(req, res);
         }
         else if ("subscribe".equalsIgnoreCase(actionType)) {
            doPost(req, res);
         }
         else if ("unSubscribe".equalsIgnoreCase(actionType)) {
            doPost(req, res);
         }
         else if ("get".equalsIgnoreCase(actionType)) {
            doPost(req, res);
         }
         else if ("erase".equalsIgnoreCase(actionType)) {
            doPost(req, res);
         }

         //------------------ logout ---------------------------------------------------------
         else if (actionType.equals("logout")) {
            log.info(ME, "Logout arrived ...");
            try {
               ProxyConnection pc = BlasterHttpProxy.getProxyConnectionBySessionId(sessionId);
               pc.cleanup(sessionId);
            } catch(XmlBlasterException e) {
               log.error(ME, e.toString());
            }

            // Otherwise the browser (controlFrame) complains 'document contained no data'
            PrintWriter out = res.getWriter();
            out.println(header+" <body text='white' bgcolor='white'><script language='JavaScript1.2'>top.close()</script></body></html>");
         }

         else {
            String text = "Unknown ActionType '" + actionType + "', request for permanent http connection ignored";
            throw new XmlBlasterException(ME, text);
         }


      } catch (XmlBlasterException e) {
         log.error(ME, "Caught XmlBlaster Exception: " + e.reason);
         String codedText = URLEncoder.encode( e.reason );
         try {
            HttpPushHandler pushHandler = BlasterHttpProxy.getHttpPushHandler(sessionId);
            pushHandler.push(new PushDataItem(PushDataItem.LOGGING, "if (parent.error != null) parent.error('"+codedText+"');\n"));
         } catch (XmlBlasterException e2) {
            PrintWriter out = res.getWriter();
            out.println(HttpPushHandler.alert(e.reason));
         }
      } catch (Exception e) {
         log.error(ME, "Caught Exception: " + e.toString());
         e.printStackTrace();
         try {
            HttpPushHandler pushHandler = BlasterHttpProxy.getHttpPushHandler(sessionId);
            pushHandler.push(new PushDataItem(PushDataItem.LOGGING, "if (parent.error != null) parent.error('"+e.toString()+"');\n"));
         } catch (XmlBlasterException e2) {
            PrintWriter out = res.getWriter();
            out.println(HttpPushHandler.alert(e.toString()));
         }
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
    * the TARGET should be set to "requestFrame".
    * The parameter ActionType must be set to one of the above methods.<br />
    * For an explanation of these methods see the file xmlBlaster.idl
    * <p />
    * The asynchronous updates are pushed into the 'callbackFrame' of your browser
    * <p />
    * The key/qos values are expected to be URLEncoded
    * <p />
    * Allows simple subscribe/unSubscribe/erase of the form
    *    <pre>?ActionType=subscribe&key.oid=cpuinfo</pre>
    * and complete key XML strings like
    *    <pre>?ActionType=subscribe&key=&lt;key oid='hello'>&lt;/key></pre>
    * as well.<br />
    * QoS is optional, the content only needed when publishing
    * @param req Data from browser
    * @param res Response of the servlet
    */
   public void doPost(HttpServletRequest req, HttpServletResponse res)
                               throws ServletException, IOException
   {
      res.setContentType("text/html");
      PrintWriter out = res.getWriter();

      //HttpSession session = req.getSession();
      HttpSession session = req.getSession(false);
      String sessionId = req.getRequestedSessionId();

      String ME  = "BlasterHttpProxyServlet-" + req.getRemoteAddr() + "-" + sessionId;
      log.info(ME, "Entering BlasterHttpProxy.doPost() servlet");

      ProxyConnection proxyConnection = null;
      XmlBlasterConnection xmlBlaster = null;
      HttpPushHandler pushHandler = null;

      try {
         proxyConnection = BlasterHttpProxy.getProxyConnectionBySessionId(sessionId);
         xmlBlaster = proxyConnection.getXmlBlasterConnection();
         pushHandler = proxyConnection.getHttpPushHandler(sessionId);
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Caught XmlBlaster Exception: " + e.reason);
         return;
      }

      StringBuffer retStr = new StringBuffer();
      try {
         String actionType = Util.getParameter(req, "ActionType", "NONE");

         // Extract the message data
         String oid = Util.getParameter(req, "key.oid", null);
         if (oid != null) oid = URLDecoder.decode(oid);

         String key = Util.getParameter(req, "key", null);
         if (key != null) {
            key = URLDecoder.decode(key);
            if (log.DUMP) log.dump(ME, "key=\n'" + key + "'");
         }
         
         String content = Util.getParameter(req, "content", null);
         if (content != null) {
            content = URLDecoder.decode(content);
         }
         else
            content = "";
         if (log.DUMP) log.dump(ME, "content=\n'" + content + "'");

         String qos = Util.getParameter(req, "qos", null);
         if (qos != null) {
            qos = URLDecoder.decode(qos);
         }
         else
            qos = ""; 
         if (log.DUMP) log.dump(ME, "qos=\n'" + qos + "'");

         if (actionType.equals("subscribe")) {
            log.trace(ME, "subscribe arrived ...");
            
            if (oid != null) {
               SubscribeKeyWrapper xmlKey = new SubscribeKeyWrapper(oid);
               //SubscribeQosWrapper xmlQos = new SubscribeQosWrapper();
               SubscribeRetQos ret = xmlBlaster.subscribe(xmlKey.toXml(), qos);
               log.info(ME, "Subscribed to simple key.oid=" + oid + ": " + ret.getSubscriptionId());
            }
            else if (key != null) {
               SubscribeRetQos ret = xmlBlaster.subscribe(key, qos);
               log.info(ME, "Subscribed to " + key + ": SubscriptionId=" + ret.getSubscriptionId() + " qos=" + qos);
            }
            else {
               String str = "Please call servlet with some 'key.oid=...' or 'key=<key ...' when subscribing";
               log.error(ME, str);
               htmlOutput(str, res);
               return;
            }
         }

         else if (actionType.equals("unSubscribe")) {
            log.trace(ME, "unSubscribe arrived ...");

            if (oid != null) {
               UnSubscribeKeyWrapper xmlKey = new UnSubscribeKeyWrapper(oid);
               xmlBlaster.unSubscribe(xmlKey.toXml(), qos);
               log.info(ME, "UnSubscribed to simple key.oid=" + oid);
            }
            else if (key != null) {
               xmlBlaster.unSubscribe(key, qos);
               log.info(ME, "UnSubscribed from key=" + key + " qos=" + qos);
            }
            else {
               String str = "Please call servlet with some 'key.oid=...' or 'key=<key ...' when unsubscribing";
               log.error(ME, str);
               htmlOutput(str, res);
               return;
            }
         }

         else if (actionType.equals("get")) {
            throw new Exception("Synchronous ActionType=get is not supported");
         }

         else if (actionType.equals("publish")) {
            log.trace(ME, "publish arrived ...");
            if (key == null) {
               String str = "Please call servlet with some key when publishing";
               log.error(ME, str);
               htmlOutput(str, res);
               return;
            }
            if (content == null)
               content = "";

            log.info(ME, "Publishing '" + key + "'");
            MessageUnit msgUnit = new MessageUnit(key, content.getBytes(), qos);
            try {
               PublishRetQos publish = xmlBlaster.publish(msgUnit);
               log.trace(ME, "Success: Publishing done, returned oid=" + publish.getOid());
            } catch(XmlBlasterException e) {
               log.warn(ME, "XmlBlasterException: " + e.reason);
            }
         }

         else if (actionType.equals("erase")) {
            log.trace(ME, "erase arrived ...");

            if (oid != null) {
               EraseKeyWrapper xmlKey = new EraseKeyWrapper(oid);
               //EraseQosWrapper xmlQos = new EraseQosWrapper();
               EraseRetQos[] ret = xmlBlaster.erase(xmlKey.toXml(), qos);
               for (int ii=0; ii<ret.length; ii++)
                  log.info(ME, "Erased " + ret[ii].getOid());
            }
            else if (key != null) {
               EraseRetQos ret[] = xmlBlaster.erase(key, qos);
               for (int ii=0; ii<ret.length; ii++)
                  log.info(ME, "Erased " + ret[ii].getOid());
            }
            else {
               String str = "Please call servlet with some 'key.oid=...' or 'key=<key ...' when subscribing";
               log.error(ME, str);
               htmlOutput(str, res);
               return;
            }
         }

         else {
            throw new Exception("Unknown or missing 'ActionType=" + actionType + "' please choose 'subscribe' 'unSubscribe' 'erase' etc.");
         }

      } catch (XmlBlasterException e) {
         log.error(ME, "Caught XmlBlaster Exception: " + e.reason);
         String codedText = URLEncoder.encode( e.reason );
         pushHandler.push(new PushDataItem(PushDataItem.LOGGING, "if (parent.error != null) parent.error('"+codedText+"');\n"));
      } catch (Exception e) {
         log.error(ME, "RemoteException: " + e.getMessage());
         e.printStackTrace();
         retStr.append("<body>http communication problem</body>");
      } finally {
         out.println(retStr.toString());
      }
   }


   /**
    * Setting the system properties.
    * <p />
    * These may be overwritten in zone.properties, e.g.
    *    servlets.default.initArgs=servlets.default.initArgs=org.xml.sax.parser=org.apache.crimson.parser.Parser2
    * <p />
    * We set the properties to choose JacORB and Suns XML parser as a default.
    */
   static public final void initSystemProperties(ServletConfig conf)
   {
      String ME  = "BlasterHttpProxyServlet";

      Properties props = System.getProperties();
      LogChannel log = Global.instance().getLog("http");

      // XmlBlaster uses as a default JacORB
      if (conf.getInitParameter("org.omg.CORBA.ORBClass") != null) {
         props.put( "org.omg.CORBA.ORBClass", conf.getInitParameter("org.omg.CORBA.ORBClass"));
         log.trace(ME, "Found system parameter org.omg.CORBA.ORBClass=" + conf.getInitParameter("org.omg.CORBA.ORBClass"));
      }
      else
         props.put("org.omg.CORBA.ORBClass", "org.jacorb.orb.ORB");
      log.info(ME, "Using system parameter org.omg.CORBA.ORBClass=" + props.get("org.omg.CORBA.ORBClass"));

      if (conf.getInitParameter("org.omg.CORBA.ORBSingletonClass") != null) {
         props.put( "org.omg.CORBA.ORBSingletonClass", conf.getInitParameter("org.omg.CORBA.ORBSingletonClass"));
         log.trace(ME, "Found system parameter org.omg.CORBA.ORBSingletonClass=" + conf.getInitParameter("org.omg.CORBA.ORBSingletonClass"));
      }
      else
         props.put("org.omg.CORBA.ORBSingletonClass", "org.jacorb.orb.ORBSingleton");
      log.info(ME, "Using system parameter org.omg.CORBA.ORBSingletonClass=" + props.get("org.omg.CORBA.ORBSingletonClass"));

      // xmlBlaster uses Suns XML parser as default
      if (conf.getInitParameter("org.xml.sax.parser") != null) {
         props.put( "org.xml.sax.parser", conf.getInitParameter("org.xml.sax.parser"));
         log.trace(ME, "Found system parameter org.xml.sax.parser=" + conf.getInitParameter("org.xml.sax.parser"));
      }
      else
         props.put("org.xml.sax.parser", "org.apache.crimson.parser.Parser2"); // xmlBlaster uses Suns XML parser as default
      log.info(ME, "Using system parameter org.xml.sax.parser=" + props.get("org.xml.sax.parser"));

      System.setProperties(props);
   }


   /**
    * Event fired by LogChannel.java through interface LogableDevice.
    * <p />
    * Log output from log.info(); etc. into Servlet log file.
    * <p />
    * Note that System.err.println("Hello"); will be printed into
    * the Apache error log file /var/log/httpd.error_log<br />
    * I don't know what other web servers are doing with it.
    * <p />
    * System.out.println("Hello"); will be printed to the console
    * where you started the servlet engine.
    */
   public void log(int level, String source, String str)
   {
      getServletContext().log(str);
   }


   /**
    * Returns a HTML file to the Browser.
    * @param htmlData the complete HTML page
    * @param response the servlet response-object
    * @see HttpServletResponse
    */
   public void htmlOutput(String htmlData, HttpServletResponse response) throws ServletException
   {
      String ME  = "BlasterHttpProxyServlet";
      response.setContentType("text/html");
      try {
         PrintWriter pw;
         pw = response.getWriter();
         pw.println(htmlData);
         pw.close();
      }
      catch(IOException e) {
         log.warn(ME, "Could not deliver HTML page to browser:"+e.toString());
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
      String ME  = "BlasterHttpProxyServlet";
      try {
         response.setContentType("text/html");
         PrintWriter pw;
         pw = response.getWriter();
         pw.println(HttpPushHandler.alert(error));
         pw.close();
      }
      catch(IOException e) {
         log.error(ME, "Sending of error failed: " + error + "\n Reason=" + e.toString());
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
      String ME  = "BlasterHttpProxyServlet";
      response.setContentType("text/xml");

      try {
         PrintWriter pw;
         pw = response.getWriter();
         pw.println(xmlData);
         pw.close();
      }
      catch(IOException e) {
         String text = "Sending XML data to browser failed: " + e.toString();
         log.warn(ME, text);
         PrintWriter pw;
         try { pw = response.getWriter(); } catch(IOException e2) { log.error(ME, "2.xml send problem"); return; }
         pw.println("<html><body>Request Problems" + text + "</body></html>");
         pw.close();
      }
   }
}
