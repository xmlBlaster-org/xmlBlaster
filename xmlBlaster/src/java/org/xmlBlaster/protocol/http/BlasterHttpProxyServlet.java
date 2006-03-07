/*------------------------------------------------------------------------------
Name:      BlasterHttpProxyServlet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.EraseKey;

import java.io.*;
import java.util.*;

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
 * @author Marcel Ruff xmlBlaster@marcelruff.info
 */
public class BlasterHttpProxyServlet extends HttpServlet implements org.xmlBlaster.util.log.I_LogListener
{
   private static final long serialVersionUID = 1L;
   private static boolean propertyRead = false;
   private final String header = "<html><meta http-equiv='no-cache'><meta http-equiv='Cache-Control' content='no-cache'><meta http-equiv='expires' content='Wed, 26 Feb 1997 08:21:57 GMT'>";
   private Global glob = null;
   private static Logger log = Logger.getLogger(BlasterHttpProxyServlet.class.getName());
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
            args[count++] = conf.getInitParameter(name);
            //Global.instance().getLog(null).info("", "Reading web.xml property " + args[count-2] + "=" + args[count-1]);
         }

         glob = new Global();
         int ret = glob.init(args);

         if (ret != 0) {
            // init problems
         }
      }

      // Redirect xmlBlaster logs to servlet log file (see method log() below)
      // Use xmlBlaster/demo/http/WEB-INF/web.xml to configure logging.

      // To redirect your Logging output into the servlet logfile (jserv.log),
      // outcomment this line:
      //log.addLogableDevice(this);

      log.fine("Initialize ...");

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
      if (log.isLoggable(Level.FINER)) log.finer("Entering doGet() ... " + Global.getMemoryStatistic());
      res.setContentType("text/html");
      String ME  = "BlasterHttpProxyServlet-" + req.getRemoteAddr();

      String actionType = Util.getParameter(req, "ActionType", "");
      if (actionType == null) {
         String str = "Please call servlet with some ActionType";
         log.severe(str);
         htmlOutput(str, res);
         return;
      }

      HttpSession session = req.getSession(true);
      if (actionType.equals("login")) {
         boolean invalidate = Util.getParameter(req, "xmlBlaster.invalidate", true);
         if (invalidate == true) {
            log.info("Entering servlet doGet(), forcing a new sessionId");
            session.invalidate();   // force a new sessionId
         }
         session = req.getSession(true);
      }
      String sessionId = session.getId();

      ME += "-" + sessionId;
      if (log.isLoggable(Level.FINE)) log.fine("Entering servlet doGet() ...");

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
               log.info("Login action, browser has not yet joined this sessionId (cookie), so first pings pong may return an invalid sessionId");
               pushHandler.startPing(); // This is too early here, we need to start the ping thread later?
            }

            BlasterHttpProxy.addHttpPushHandler( sessionId, pushHandler );

            // Don't fall out of doGet() to keep the HTTP connection open
            log.info("Waiting forever, permanent HTTP connection from " +
                           req.getRemoteHost() + "/" + req.getRemoteAddr() +
                           ", sessionName=" + connectQos.getSessionName().getRelativeName() + " sessionId=" + sessionId +
                           "', protocol='" + req.getProtocol() +
                           "', agent='" + req.getHeader("User-Agent") +
                           "', referer='" + req.getHeader("Referer") +
                           "'.");

            if (log.isLoggable(Level.FINE)) log.fine("user='" + req.getRemoteUser() +
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
                  Thread.sleep(10000L);
               }
               catch (InterruptedException i) {
                  log.severe("Error in Thread handling, don't know what to do: "+i.toString());
                  pushHandler.cleanup();
                  break;
               }
            }
            pushHandler = null;
            log.info("Persistent HTTP connection lost, leaving doGet() ....");
            /*
            System.out.println("Currently consumed threads:");
            System.out.println("===========================");
            ThreadLister.listAllThreads(System.out);
            */
         }


         //------------------ first request from applet --------------------------
         else if(actionType.equals("dummyToCreateASessionId")) {  // I_XmlBlasterAccessRaw.CREATE_SESSIONID_NAME
            log.info("doGet: dummyToCreateASessionId");
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
               if (log.isLoggable(Level.FINE)) log.fine("Received 'browserReady'");
               pushHandler.setBrowserIsReady( true );

               // Otherwise the browser (controlFrame) complains 'document contained no data'
               PrintWriter out = res.getWriter();
               out.println(header+"<body text='white' bgcolor='white'>Empty response for your ActionType='browserReady' " + System.currentTimeMillis() + "</body></html>");
               return;
            }
            catch (XmlBlasterException e) {
               log.severe("Caught XmlBlaster Exception for actionType '" + actionType + "': " + e.getMessage());
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
               if (log.isLoggable(Level.FINE)) log.fine("Received pong '" + Util.getParameter(req, "state", "noState") + "'");

               // Otherwise the browser (controlFrame) complains 'document contained no data'
               PrintWriter out = res.getWriter();
               out.println(header+"<body text='white' bgcolor='white'>Empty response for your ActionType='pong' " + Util.getParameter(req, "state", "noState") + " " + System.currentTimeMillis() + "</body></html>");
               return;
            }
            catch (XmlBlasterException e) {
               log.severe("Caught XmlBlaster Exception for actionType '" + actionType + "': " + e.getMessage());
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
            log.info("Logout arrived ...");
            try {
               HttpPushHandler pc = BlasterHttpProxy.getHttpPushHandler(sessionId);
               pc.cleanup();
            } catch(XmlBlasterException e) {
               log.severe(e.toString());
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
         log.severe("Caught XmlBlaster Exception: " + e.getMessage());
         String codedText = Global.encode( e.getMessage(), ENCODING );
         try {
            HttpPushHandler pushHandler = BlasterHttpProxy.getHttpPushHandler(sessionId);
            pushHandler.push(new PushDataItem(PushDataItem.LOGGING, "if (parent.error != null) parent.error('"+codedText+"');\n"));
         } catch (XmlBlasterException e2) {
            PrintWriter out = res.getWriter();
            out.println(HttpPushHandler.alert(e.getMessage()));
         }
      } catch (Exception e) {
         log.severe("Caught Exception: " + e.toString());
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
      /*HttpSession session =*/ req.getSession(false);
      String sessionId = req.getRequestedSessionId();

      log.info("Entering servlet doPost() ...");

      I_XmlBlasterAccess xmlBlaster = null;
      HttpPushHandler pushHandler = null;

      try {
         pushHandler = BlasterHttpProxy.getHttpPushHandler(sessionId);
         xmlBlaster = pushHandler.getXmlBlasterAccess();
      }
      catch (XmlBlasterException e) {
         log.severe("Caught XmlBlaster Exception: " + e.getMessage());
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
            if (log.isLoggable(Level.FINEST)) log.finest("key=\n'" + key + "'");
         }
         
         String content = Util.getParameter(req, "content", null);
         if (content != null) {
            content = Global.decode(content, ENCODING);
         }
         else
            content = "";
         if (log.isLoggable(Level.FINEST)) log.finest("content=\n'" + content + "'");

         String qos = Util.getParameter(req, "qos", null);
         if (qos != null) {
            qos = Global.decode(qos, ENCODING);
         }
         else
            qos = ""; 
         if (log.isLoggable(Level.FINEST)) log.finest("qos=\n'" + qos + "'");

         if (action.equals(MethodName.SUBSCRIBE)) { // "subscribe"
            log.fine("subscribe arrived ...");
            
            if (oid != null) {
               SubscribeKey xmlKey = new SubscribeKey(glob, oid);
               SubscribeReturnQos ret = xmlBlaster.subscribe(xmlKey.toXml(), qos);
               log.info("Subscribed to simple key.oid=" + oid + ": " + ret.getSubscriptionId());
            }
            else if (key != null) {
               SubscribeReturnQos ret = xmlBlaster.subscribe(key, qos);
               log.info("Subscribed to " + key + ": SubscriptionId=" + ret.getSubscriptionId() + " qos=" + qos);
            }
            else {
               String str = "Please call servlet with some 'key.oid=...' or 'key=<key ...' when subscribing";
               log.severe(str);
               htmlOutput(str, res);
               return;
            }
         }

         else if (action.equals(MethodName.UNSUBSCRIBE)) { // "unSubscribe"
            log.fine("unSubscribe arrived ...");
            //UnSubscribeReturnQos[] ret;

            if (oid != null) {
               UnSubscribeKey xmlKey = new UnSubscribeKey(glob, oid);
               /*ret = */xmlBlaster.unSubscribe(xmlKey.toXml(), qos);
            }
            else if (key != null) {
               /*ret = */xmlBlaster.unSubscribe(key, qos);
            }
            else {
               String str = "Please call servlet with some 'key.oid=...' or 'key=<key ...' when unsubscribing";
               log.severe(str);
               htmlOutput(str, res);
               return;
            }
         }

         else if (action.equals(MethodName.GET)) { // "get"
            throw new Exception("Synchronous ActionType=get is not supported");
         }

         else if (action.equals(MethodName.PUBLISH)) { // "publish"
            log.fine("publish arrived ...");
            if (key == null) {
               String str = "Please call servlet with some key when publishing";
               log.severe(str);
               htmlOutput(str, res);
               return;
            }
            if (content == null)
               content = "";

            log.info("Publishing '" + key + "'");
            MsgUnit msgUnit = new MsgUnit(glob, key, content.getBytes(), qos);
            try {
               PublishReturnQos prq = xmlBlaster.publish(msgUnit);
               log.fine("Success: Publishing done, returned oid=" + prq.getKeyOid());
            } catch(XmlBlasterException e) {
               log.warning("XmlBlasterException: " + e.getMessage());
            }
         }

         else if (action.equals(MethodName.ERASE)) { // "erase"
            log.fine("erase arrived ...");
            //EraseReturnQos[] ret;

            if (oid != null) {
               EraseKey ek = new EraseKey(glob, oid);
               /*ret =*/ xmlBlaster.erase(ek.toXml(), qos);
            }
            else if (key != null) {
               /*ret =*/ xmlBlaster.erase(key, qos);
            }
            else {
               String str = "Please call servlet with some 'key.oid=...' or 'key=<key ...' when subscribing";
               log.severe(str);
               htmlOutput(str, res);
               return;
            }
         }

         else if (action.equals(MethodName.PING)) { // "ping"
            log.fine("ping arrived, doing nothing ...");
            //String ret = xmlBlaster.ping(qos);
         }

         else {
            throw new Exception("Unknown or missing 'ActionType=" + actionType + "' please choose 'subscribe' 'unSubscribe' 'erase' etc.");
         }
      } catch (XmlBlasterException e) {
         log.severe("Caught XmlBlaster Exception: " + e.getMessage());
         String codedText = Global.encode( e.getMessage(), ENCODING );
         pushHandler.push(new PushDataItem(PushDataItem.LOGGING, "if (parent.error != null) parent.error('"+codedText+"');\n"));
      } catch (Exception e) {
         log.severe("RemoteException: " + e.getMessage());
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
      Properties props = System.getProperties();

      // Check for orb configuration
      if (conf.getInitParameter("org.omg.CORBA.ORBClass") != null) { // "org.jacorb.orb.ORB"
         props.put( "org.omg.CORBA.ORBClass", conf.getInitParameter("org.omg.CORBA.ORBClass"));
         log.info("Using servlet system parameter org.omg.CORBA.ORBClass=" + props.get("org.omg.CORBA.ORBClass"));
      }

      if (conf.getInitParameter("org.omg.CORBA.ORBSingletonClass") != null) { // "org.jacorb.orb.ORBSingleton");
         props.put( "org.omg.CORBA.ORBSingletonClass", conf.getInitParameter("org.omg.CORBA.ORBSingletonClass"));
         log.info("Using servlet system parameter org.omg.CORBA.ORBSingletonClass=" + props.get("org.omg.CORBA.ORBSingletonClass"));
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
    * Event fired by Logger.java through interface I_LogListener.
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
   public void log(LogRecord record)
   {
      getServletContext().log(record.getMessage());
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
