/*------------------------------------------------------------------------------
Name:      BlasterHttpProxyServlet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.jutils.runtime.Memory;
import org.jutils.time.TimeHelper;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.EraseKey;

import java.rmi.RemoteException;
import java.io.*;
import java.util.*;
import java.net.URLEncoder;
import java.net.URLDecoder;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.commons.codec.binary.Base64;

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
 * @author Marcel Ruff xmlBlaster@marcelruff.info
 */
public class BlasterHttpProxyServlet extends HttpServlet implements org.jutils.log.LogableDevice
{
   private static boolean propertyRead = false;
   private final String header = "<html><meta http-equiv='no-cache'><meta http-equiv='Cache-Control' content='no-cache'><meta http-equiv='expires' content='Wed, 26 Feb 1997 08:21:57 GMT'>";
   private Global glob = null;
   private LogChannel log;
   public final static String ENCODING = "UTF-8";

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
         Enumeration enumer = conf.getInitParameterNames();
         int count = 0;
         while(enumer.hasMoreElements()) {
            if (enumer.nextElement() == null)
               continue;
            count++;
         }
         String[] args = new String[2*count];

         count = 0;
         enumer = conf.getInitParameterNames();
         while(enumer.hasMoreElements()) {
            String name = (String)enumer.nextElement();
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
         if (invalidate == true) {
            log.info(ME, "Entering servlet doGet(), forcing a new sessionId");
            session.invalidate();   // force a new sessionId
         }
         session = req.getSession(true);
      }
      String sessionId = session.getId();

      ME += "-" + sessionId;
      if (log.TRACE) log.trace(ME, "Entering servlet doGet() ...");

      if (sessionId == null) {
         PrintWriter out = res.getWriter();
         out.println(HttpPushHandler.alert("Sorry, your sessionId is invalid"));
         return;
      }

      try {
         //------------------ Login -------------------------------------------------
         if (actionType.equals("login") || actionType.equals("connect")) {
            ConnectQos connectQos;
            if (actionType.equals("connect")) {
               String qos = Util.getParameter(req, "xmlBlaster.connectQos", null);
               if (qos == null || qos.length() < 1)
                  throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME, "Missing connect QoS. Pass xmlBlaster.connectQos='<qos> ... </qos>' with your URL in your POST in a hidden form field or in your cookie.");
               connectQos = new ConnectQos(glob, glob.getConnectQosFactory().readObject(qos));
            }
            else {
               String loginName = Util.getParameter(req, "xmlBlaster.loginName", null);    // "Joe";
               if (loginName == null || loginName.length() < 1)
                  throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME, "Missing login name. Pass xmlBlaster.loginName=xy with your URL or in your cookie.");
               String passwd = Util.getParameter(req, "xmlBlaster.passwd", null);  // "secret";
               if (passwd == null || passwd.length() < 1)
                  throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME, "Missing passwd");
               connectQos = new ConnectQos(glob, loginName, passwd);
            }

            ME  = "BlasterHttpProxyServlet-" + req.getRemoteAddr() + "-" +
                  connectQos.getSessionName().getLoginName() + "-" + sessionId;

            I_XmlBlasterAccess xmlBlasterAccess = glob.getXmlBlasterAccess();
            HttpPushHandler pushHandler = new HttpPushHandler(req, res, sessionId,
                                                     connectQos.getSessionName().getRelativeName(),
                                                     xmlBlasterAccess);

            xmlBlasterAccess.connect(connectQos, pushHandler);
            if (!session.isNew()) {
               pushHandler.startPing();
            }
            else {
               log.info(ME, "Login action, browser has not yet joined this sessionId (cookie), so first pings pong may return an invalid sessionId");
               pushHandler.startPing(); // This is too early here, we need to start the ping thread later?
            }

            BlasterHttpProxy.addHttpPushHandler( sessionId, pushHandler );

            // Don't fall out of doGet() to keep the HTTP connection open
            log.info(ME, "Waiting forever, permanent HTTP connection from " +
                           req.getRemoteHost() + "/" + req.getRemoteAddr() +
                           ", sessionName=" + connectQos.getSessionName().getRelativeName() + " sessionId=" + sessionId +
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
                  pushHandler.cleanup();
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


         //------------------ first request from applet --------------------------
         else if(actionType.equals("dummyToCreateASessionId")) {  // I_XmlBlasterAccessRaw.CREATE_SESSIONID_NAME
            log.info(ME, "doGet: dummyToCreateASessionId");
            PrintWriter out = res.getWriter();
            out.println(header+"<body text='white' bgcolor='white'>Empty response for your ActionType='dummyToCreateASessionId' " +
                        System.currentTimeMillis() + "</body></html>");
            return;
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
               log.error(ME, "Caught XmlBlaster Exception for actionType '" + actionType + "': " + e.getMessage());
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
               log.error(ME, "Caught XmlBlaster Exception for actionType '" + actionType + "': " + e.getMessage());
               return;
            }
         }

         else if (MethodName.PUBLISH.toString().equalsIgnoreCase(actionType)) { // "publish"
            doPost(req, res);
         }
         else if (MethodName.SUBSCRIBE.toString().equalsIgnoreCase(actionType)) { // "subscribe"
            doPost(req, res);
         }
         else if (MethodName.UNSUBSCRIBE.toString().equalsIgnoreCase(actionType)) { // "unSubscribe"
            doPost(req, res);
         }
         else if (MethodName.GET.toString().equalsIgnoreCase(actionType)) { // "get"
            doPost(req, res);
         }
         else if (MethodName.ERASE.toString().equalsIgnoreCase(actionType)) { // "erase"
            doPost(req, res);
         }
         else if (MethodName.PING.toString().equalsIgnoreCase(actionType)) { // "ping"
            doPost(req, res);
         }

         //------------------ logout ---------------------------------------------------------
         else if (actionType.equals("logout")) {
            log.info(ME, "Logout arrived ...");
            try {
               HttpPushHandler pc = BlasterHttpProxy.getHttpPushHandler(sessionId);
               pc.cleanup();
            } catch(XmlBlasterException e) {
               log.error(ME, e.toString());
            }

            // Otherwise the browser (controlFrame) complains 'document contained no data'
            PrintWriter out = res.getWriter();
            out.println(header+" <body text='white' bgcolor='white'><script language='JavaScript' type='text/javascript'>top.close()</script></body></html>");
         }

         else {
            String text = "Unknown ActionType '" + actionType + "', request for permanent http connection ignored";
            throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME, text);
         }


      } catch (XmlBlasterException e) {
         log.error(ME, "Caught XmlBlaster Exception: " + e.getMessage());
         String codedText = Global.encode( e.getMessage(), ENCODING );
         try {
            HttpPushHandler pushHandler = BlasterHttpProxy.getHttpPushHandler(sessionId);
            pushHandler.push(new PushDataItem(PushDataItem.LOGGING, "if (parent.error != null) parent.error('"+codedText+"');\n"));
         } catch (XmlBlasterException e2) {
            PrintWriter out = res.getWriter();
            out.println(HttpPushHandler.alert(e.getMessage()));
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
      log.info(ME, "Entering servlet doPost() ...");

      I_XmlBlasterAccess xmlBlaster = null;
      HttpPushHandler pushHandler = null;
      Object returnObject = null;

      try {
         pushHandler = BlasterHttpProxy.getHttpPushHandler(sessionId);
         xmlBlaster = pushHandler.getXmlBlasterAccess();
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Caught XmlBlaster Exception: " + e.getMessage());
         return;
      }

      try {
         String actionType = Util.getParameter(req, "ActionType", "NONE");
         MethodName action;
         try {
            action = MethodName.toMethodName(actionType);
         }
         catch (IllegalArgumentException ie) {
            throw new Exception("Unknown or missing 'ActionType=" + actionType + "' please choose 'subscribe' 'unSubscribe' 'erase' etc.");
         }

         // Extract the message data
         String oid = Util.getParameter(req, "key.oid", null);
         if (oid != null) oid = Global.decode(oid, ENCODING);

         String key = Util.getParameter(req, "key", null);
         if (key != null) {
            key = Global.decode(key, ENCODING);
            if (log.DUMP) log.dump(ME, "key=\n'" + key + "'");
         }
         
         String content = Util.getParameter(req, "content", null);
         if (content != null) {
            content = Global.decode(content, ENCODING);
         }
         else
            content = "";
         if (log.DUMP) log.dump(ME, "content=\n'" + content + "'");

         String qos = Util.getParameter(req, "qos", null);
         if (qos != null) {
            qos = Global.decode(qos, ENCODING);
         }
         else
            qos = ""; 
         if (log.DUMP) log.dump(ME, "qos=\n'" + qos + "'");

         if (action.equals(MethodName.SUBSCRIBE)) { // "subscribe"
            log.trace(ME, "subscribe arrived ...");
            
            if (oid != null) {
               SubscribeKey xmlKey = new SubscribeKey(glob, oid);
               SubscribeReturnQos ret = xmlBlaster.subscribe(xmlKey.toXml(), qos);
               log.info(ME, "Subscribed to simple key.oid=" + oid + ": " + ret.getSubscriptionId());
            }
            else if (key != null) {
               SubscribeReturnQos ret = xmlBlaster.subscribe(key, qos);
               log.info(ME, "Subscribed to " + key + ": SubscriptionId=" + ret.getSubscriptionId() + " qos=" + qos);
            }
            else {
               String str = "Please call servlet with some 'key.oid=...' or 'key=<key ...' when subscribing";
               log.error(ME, str);
               htmlOutput(str, res);
               return;
            }
         }

         else if (action.equals(MethodName.UNSUBSCRIBE)) { // "unSubscribe"
            log.trace(ME, "unSubscribe arrived ...");
            UnSubscribeReturnQos[] ret;

            if (oid != null) {
               UnSubscribeKey xmlKey = new UnSubscribeKey(glob, oid);
               ret = xmlBlaster.unSubscribe(xmlKey.toXml(), qos);
            }
            else if (key != null) {
               ret = xmlBlaster.unSubscribe(key, qos);
            }
            else {
               String str = "Please call servlet with some 'key.oid=...' or 'key=<key ...' when unsubscribing";
               log.error(ME, str);
               htmlOutput(str, res);
               return;
            }
         }

         else if (action.equals(MethodName.GET)) { // "get"
            throw new Exception("Synchronous ActionType=get is not supported");
         }

         else if (action.equals(MethodName.PUBLISH)) { // "publish"
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
            MsgUnit msgUnit = new MsgUnit(glob, key, content.getBytes(), qos);
            try {
               PublishReturnQos prq = xmlBlaster.publish(msgUnit);
               log.trace(ME, "Success: Publishing done, returned oid=" + prq.getKeyOid());
            } catch(XmlBlasterException e) {
               log.warn(ME, "XmlBlasterException: " + e.getMessage());
            }
         }

         else if (action.equals(MethodName.ERASE)) { // "erase"
            log.trace(ME, "erase arrived ...");
            EraseReturnQos[] ret;

            if (oid != null) {
               EraseKey ek = new EraseKey(glob, oid);
               ret = xmlBlaster.erase(ek.toXml(), qos);
            }
            else if (key != null) {
               ret = xmlBlaster.erase(key, qos);
            }
            else {
               String str = "Please call servlet with some 'key.oid=...' or 'key=<key ...' when subscribing";
               log.error(ME, str);
               htmlOutput(str, res);
               return;
            }
         }

         else if (action.equals(MethodName.PING)) { // "ping"
            log.trace(ME, "ping arrived, doing nothing ...");
            //String ret = xmlBlaster.ping(qos);
         }

         else {
            throw new Exception("Unknown or missing 'ActionType=" + actionType + "' please choose 'subscribe' 'unSubscribe' 'erase' etc.");
         }
      } catch (XmlBlasterException e) {
         log.error(ME, "Caught XmlBlaster Exception: " + e.getMessage());
         String codedText = Global.encode( e.getMessage(), ENCODING );
         pushHandler.push(new PushDataItem(PushDataItem.LOGGING, "if (parent.error != null) parent.error('"+codedText+"');\n"));
      } catch (Exception e) {
         log.error(ME, "RemoteException: " + e.getMessage());
         e.printStackTrace();
         out.println("<body>http communication problem</body>");
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

      // Check for orb configuration
      if (conf.getInitParameter("org.omg.CORBA.ORBClass") != null) { // "org.jacorb.orb.ORB"
         props.put( "org.omg.CORBA.ORBClass", conf.getInitParameter("org.omg.CORBA.ORBClass"));
         log.info(ME, "Using servlet system parameter org.omg.CORBA.ORBClass=" + props.get("org.omg.CORBA.ORBClass"));
      }

      if (conf.getInitParameter("org.omg.CORBA.ORBSingletonClass") != null) { // "org.jacorb.orb.ORBSingleton");
         props.put( "org.omg.CORBA.ORBSingletonClass", conf.getInitParameter("org.omg.CORBA.ORBSingletonClass"));
         log.info(ME, "Using servlet system parameter org.omg.CORBA.ORBSingletonClass=" + props.get("org.omg.CORBA.ORBSingletonClass"));
      }

      // xmlBlaster uses Suns XML parser as default
      /*
      if (conf.getInitParameter("org.xml.sax.parser") != null) {
         props.put( "org.xml.sax.parser", conf.getInitParameter("org.xml.sax.parser"));
         log.trace(ME, "Found system parameter org.xml.sax.parser=" + conf.getInitParameter("org.xml.sax.parser"));
      }
      else
         props.put("org.xml.sax.parser", "org.apache.crimson.parser.Parser2"); // xmlBlaster uses Suns XML parser as default
      log.info(ME, "Using system parameter org.xml.sax.parser=" + props.get("org.xml.sax.parser"));
      */
      if (props.size() > 0) {
         System.setProperties(props);
      }
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
