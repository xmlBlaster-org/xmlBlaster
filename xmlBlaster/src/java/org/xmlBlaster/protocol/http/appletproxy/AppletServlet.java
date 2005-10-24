/*------------------------------------------------------------------------------
Name:      AppletServlet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http.appletproxy;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.jutils.runtime.Memory;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.protocol.http.common.I_XmlBlasterAccessRaw;
import org.xmlBlaster.client.protocol.http.common.MsgHolder;
import org.xmlBlaster.client.protocol.http.common.ObjectInputStreamMicro;
import org.xmlBlaster.client.protocol.http.common.ObjectOutputStreamMicro;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.EraseKey;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;
import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.commons.codec.binary.Base64;

/**
 * This servlet supports requests from an applet and sends instant message callbacks to it. 
 * <p>
 * The servlet doesn't leave the doGet() method after an invocation of actionType "connect"
 * keeping a permanent http connection.
 * </p>
 * <p>
 * The logging output is redirected to the normal servlet log file.
 * If you use Apache/Jserv, look into /var/log/httpd/jserv.log, for
 * tomcat 5.x check jakarta-tomcat/logs/catalina.out
 * </p>
 * <p>
 * The file
 * </p>
 * <code>
 * xmlBlaster/demo/http/WEB-INF/web.xml
 * </code>
 * <p>
 * allows to switch on/off logging and to choose any other xmlBlaster client side configuration
 * like queue sizes etc.
 * </p>
 * @see org.xmlBlaster.client.protocol.http.applet.XmlBlasterAccessRaw
 * @see http.applet.HelloWorld3
 * @author Marcel Ruff xmlBlaster@marcelruff.info
 */
public class AppletServlet extends HttpServlet implements org.jutils.log.LogableDevice
{
   private Global initialGlobal;
   private Timeout timeout;
   public final static String ENCODING = "UTF-8";
   public static long requestCounter;

   /**
    * This method is invoked only once when the servlet is started. 
    * @param conf init parameter of the servlet
    */
   public void init(ServletConfig conf) throws ServletException {
      super.init(conf);

      // Add the web.xml parameters to our environment settings:
      Enumeration enumer = conf.getInitParameterNames();
      Properties props = new Properties();
      while(enumer.hasMoreElements()) {
         String name = (String)enumer.nextElement();
         if (name != null && name.length() > 0)
            props.setProperty(name, conf.getInitParameter(name));
      }
      this.initialGlobal = new Global();
      if (this.initialGlobal.init(props) != 0)
         System.out.println("AppletServlet: Global initialization problem: " + this.initialGlobal.getErrorText());

      // Redirect xmlBlaster logs to servlet log file (see method log() below)
      // Use xmlBlaster/demo/http/WEB-INF/web.xml to configure logging.

      // To redirect your Logging output into the servlet logfile (jserv.log),
      // outcomment this line:
      //log.addLogableDevice(this);

      this.initialGlobal.getLog("servlet").info(this.getClass().getName(), "Initialize ...");

      initSystemProperties(conf); // Using JacORB and Suns XML parser as a default ...

      this.timeout = new Timeout("xmlBlaster.appletPinger");
   }


   protected void connect(String ME, LogChannel log, HttpSession session, String qos, HttpServletRequest req, HttpServletResponse res) 
      throws XmlBlasterException, IOException, ServletException {
      if (qos == null || qos.length() < 1)
         throw new XmlBlasterException(this.initialGlobal, ErrorCode.USER_CONFIGURATION, ME, "Missing connect QoS. Pass xmlBlaster.connectQos='<qos> ... </qos>' with your URL in your POST in a hidden form field or in your cookie.");

      Global glob = this.initialGlobal.getClone(null);
      ConnectQos connectQos;
      boolean warnAuth = false;
      if (qos.toLowerCase().indexOf("securityservice") >= 0) {
         connectQos = new ConnectQos(glob, glob.getConnectQosFactory().readObject(qos)); // Applet provides authentication
      }
      else {
         connectQos = new ConnectQos(glob);  // User servlets default authentication setting
         warnAuth = true;
      }
      ME  += connectQos.getSessionName().getLoginName() + "-" + session.getId();
            
      if (warnAuth)
         log.warn(ME, "Login action, applet has not supplied connect QoS authentication information - we login with the servlets default authentication settings");
      else
         log.info(ME, "Login action with applet supplied connect QoS authentication information");

      I_XmlBlasterAccess xmlBlasterAccess = glob.getXmlBlasterAccess();
      PushHandler pushHandler = new PushHandler(req, res, session.getId(),
                                               connectQos.getSessionName().getRelativeName(),
                                               xmlBlasterAccess, this.timeout);
      xmlBlasterAccess.connect(connectQos, pushHandler);
      pushHandler.startPing();
      String key = "PushHandler"+getParameter(req, "appletInstanceCount", "0");
      session.setAttribute(key, pushHandler);

      // Don't fall out of doGet() to keep the HTTP connection open
      log.info(ME, "Waiting forever, permanent HTTP connection from " +
                     req.getRemoteHost() + "/" + req.getRemoteAddr() +
                     ", sessionName=" + connectQos.getSessionName().getRelativeName() + " sessionId=" + session.getId() +
                     "', protocol='" + req.getProtocol() +
                     "', agent='" + req.getHeader("User-Agent") +
                     "', referer='" + req.getHeader("Referer") +
                     "', storing PushHandler with key '" + key +
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

      pushHandler.ping("loginSucceeded");

      while (!pushHandler.isClosed()) {
         try {
            Thread.sleep(10000L);
         }
         catch (InterruptedException i) {
            log.error(ME,"Error in Thread handling, don't know what to do: "+i.toString());
            pushHandler.cleanup();
            break;
         }
      }
      pushHandler = null;
      log.info(ME, "Persistent HTTP connection lost, leaving doGet() ....");
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
    */
   public void doGetFake(String ME, HttpServletRequest req, HttpServletResponse res, String actionType, MsgHolder msgHolder) 
      throws ServletException, IOException {
      res.setContentType("text/plain");
      String errorText="";
      LogChannel log = this.initialGlobal.getLog("servlet");
      if (log.CALL) log.call(this.getClass().getName(), "Entering doGet() ... " + Memory.getStatistic());

      if (actionType.equalsIgnoreCase("NONE")) {
         String str = "Please call servlet with some ActionType";
         log.error(ME, str);
         XmlBlasterException x = new XmlBlasterException(this.initialGlobal, ErrorCode.USER_CONFIGURATION, ME, str);
         writeResponse(res, I_XmlBlasterAccessRaw.EXCEPTION_NAME, x.getMessage());
         return;
      }

      // for logging only:
      HttpSession oldSession = req.getSession(false);
      String oldSessionId = (oldSession == null) ? "no-session-id" : oldSession.getId();

      HttpSession session = req.getSession(true);
      if (actionType.equals(I_XmlBlasterAccessRaw.CREATE_SESSIONID_NAME) ||
          actionType.equals(I_XmlBlasterAccessRaw.CONNECT_NAME)) { // "connect" TODO: !!! Reconnect to old session
         boolean invalidate = getParameter(req, "xmlBlaster/invalidate", false);
         if (invalidate == true) {
            log.info(ME, "Entering servlet doGet("+I_XmlBlasterAccessRaw.CONNECT_NAME+"), forcing a new sessionId");
            session.invalidate();   // force a new sessionId
         }
         session = req.getSession(true);
      }
      
      String sessionId = session.getId();

      ME += "-" + sessionId;
      if (log.TRACE) log.trace(ME, "Entering servlet doGet(oldSessionId="+oldSessionId+") ...");

      if (false) {
         // HttpServletResponse.addCookie(javax.servlet.http.Cookie)
         javax.servlet.http.Cookie[] cookies = req.getCookies();
         if (cookies != null) {
            for (int i=0; i<cookies.length; i++) {
               log.info(ME, "Receiving cookie name=" + cookies[i].getName() + ", domain=" + cookies[i].getDomain() + ", path=" + cookies[i].getPath());
            }
         }
      }

      if (sessionId == null) {
         String str = "Sorry, your sessionId is invalid";
         XmlBlasterException x = new XmlBlasterException(this.initialGlobal, ErrorCode.USER_CONFIGURATION, ME, str);
         writeResponse(res, I_XmlBlasterAccessRaw.EXCEPTION_NAME, str);
         return;
      }

      try {
         if (actionType.equals(I_XmlBlasterAccessRaw.CONNECT_NAME)) {
            // Here we NEVER return to hold the persistent http connection for callbacks to the applet
            String qos = getParameter(req, "xmlBlaster.connectQos", (String)null);
            // if the binary protocol is used ...
            if (msgHolder != null) qos = msgHolder.getQos();
            connect(ME, log, session, qos, req, res);
         }
         else if (actionType.equals(I_XmlBlasterAccessRaw.CREATE_SESSIONID_NAME)) {
            //------------------ first request from applet --------------------------
            if (log.TRACE) log.trace(ME, "doGet: dummyToCreateASessionId");
            writeResponse(res, I_XmlBlasterAccessRaw.CREATE_SESSIONID_NAME, "OK-"+System.currentTimeMillis());
            return;
         }
         else if (actionType.equals(I_XmlBlasterAccessRaw.PONG_NAME)){
            //------------------ answer of a ping -----------------------------------------------
            // The PushHandler adds 'ping' which
            // pings the applet to hold the http connection.
            // The applet responses with 'pong', to allow the servlet to
            // detect if the applet is alive.
            try {
               PushHandler pushHandler = getPushHandler(req);
               pushHandler.pong();
               if (log.TRACE) log.trace(ME, "Received pong");
               writeResponse(res, I_XmlBlasterAccessRaw.PONG_NAME, "OK-"+System.currentTimeMillis());
               return;
            }
            catch (XmlBlasterException e) {
               log.error(ME, "Caught XmlBlaster Exception for actionType '" + actionType + "': " + e.getMessage());
               return;
            }
         }
         else if (actionType.equals(I_XmlBlasterAccessRaw.DISCONNECT_NAME)) { // "disconnect"
            log.info(ME, "Logout arrived ...");
            try {
               PushHandler pc = getPushHandler(req);
               pc.cleanup();
            } catch(XmlBlasterException e) {
               log.error(ME, e.toString());
            }
            writeResponse(res, I_XmlBlasterAccessRaw.DISCONNECT_NAME, "<qos/>");
         }
         else {
            String text = "Unknown ActionType '" + actionType + "', request for permanent http connection ignored";
            throw new XmlBlasterException(this.initialGlobal, ErrorCode.USER_CONFIGURATION, ME, text);
         }
      } catch (XmlBlasterException e) {
         log.warn(ME, "Caught XmlBlaster Exception: " + e.getMessage());
         writeResponse(res, I_XmlBlasterAccessRaw.EXCEPTION_NAME, e.getMessage());
      } catch (Exception e) {
         log.error(ME, "Caught Exception: " + e.toString());
         e.printStackTrace();
         writeResponse(res, I_XmlBlasterAccessRaw.EXCEPTION_NAME, e.toString());
      }
   }

   private final String decode(String in, String encoding) {
      //return new String(Base64.decodeBase64(in.getBytes()));
     return Global.decode(in, encoding);
   }

   private byte[] readBodyContent(HttpServletRequest req) {
      try {
         int length = req.getContentLength();
         if (length < 0) {
            String tmp = req.getHeader("Data-Length");
            if (tmp != null) length = Integer.parseInt(tmp);
         }
         //System.out.println("readBodyContent: Length=" + length);
         byte[] ret = new byte[length];
         DataInputStream in = new DataInputStream(req.getInputStream());
         in.readFully(ret);
         return ret;
      }
      catch (Exception ex) {
         ex.printStackTrace();
         return new byte[0];
      }
   }
   
   /**
    * This method is used by the binary protocol. It passes all information in
    * the http body.
    * @param req
    * @param res
    * @throws IOException
    * @return null if it was no binary protocol, otherwise an Object[3] = { (String)key, (String)qos, byte[] content }
    * where every one of the three elements could be null.
    */
   private MsgHolder readBinaryProtocol(HttpServletRequest req, HttpServletResponse res) 
      throws IOException {
      try {         
         
         //System.out.println("entering readMessageFromContent");
   
         byte[] contentAsBytes = readBodyContent(req);

         String tmp = req.getHeader("BinaryProtocol");
         //System.out.println("BinaryProtocol=" + tmp);
         if (tmp == null || tmp.equalsIgnoreCase("false")) return null; // then it was not set ...

         String actionType = req.getHeader("ActionType");

         ServletInputStream in = req.getInputStream();
         int length = req.getContentLength();
      
         //System.out.println("Content-Length=" + length);
         MsgHolder msg = ObjectInputStreamMicro.readMessage(contentAsBytes);

         //System.out.println("msg: ActionType='" + actionType + "' length='" + length + "'");
         //System.out.println("    - key    : '" + msg.key + "'");
         //System.out.println("    - qos    : '" + msg.qos + "'");
         //String tmp1 = "null";
         //if (msg.content != null) tmp1 = new String(msg.content);
         //System.out.println("    - content: '" + tmp1 + "'");
         //System.out.println("============================");
         //req.getInputStream().close();
         return msg;
      }
      catch (IOException ex) {
         ex.printStackTrace();
         throw ex;
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         throw new IOException(ex.getMessage());
      }
   }

   protected MsgHolder extractMessage(String ME, LogChannel log, HttpServletRequest req, MsgHolder binaryMsg) {
      if (binaryMsg != null) return binaryMsg;

      String oid = getParameter(req, "key.oid", (String)null);
      if (log.TRACE) log.trace(ME, "encoded oid=" + oid);
      if (oid != null) oid = this.decode(oid, ENCODING);

      String key = getParameter(req, "key", (String)null);
      if (log.TRACE) log.trace(ME, "encoded key=" + key);
      if (key != null) {
         key = this.decode(key, ENCODING);
         if (log.DUMP) log.dump(ME, "key=\n'" + key + "'");
      }
         
      byte[] content;
      String contentStr = getParameter(req, "content", (String)null);
      if (contentStr != null) {
         content = this.decode(contentStr, ENCODING).getBytes();
         //content = Base64.decodeBase64(contentStr.getBytes());
      }
      else
         content = new byte[0];
      if (log.DUMP) log.dump(ME, "content=\n'" + new String(content) + "'");

      String qos = getParameter(req, "qos", (String)null);
      if (log.TRACE) log.trace(ME, "encoded qos=" + qos);
      if (qos != null) {
         qos = this.decode(qos, ENCODING);
      }
      else
         qos = ""; 
      if (log.DUMP) log.dump(ME, "qos=\n'" + qos + "'");

      // See http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.script.html
      String xmlRequest = getParameter(req, "xmlRequest", (String)null);
      if (log.TRACE) log.trace(ME, "encoded xmlRequest=" + xmlRequest);
      if (xmlRequest != null) {
         xmlRequest = this.decode(xmlRequest, ENCODING);
         if (log.DUMP) log.dump(ME, "xmlRequest=\n'" + xmlRequest + "'");
      }

      return new MsgHolder(oid, key, qos, content);
   }

   /**
    * This method is supported just for cases where the servlet is contacted
    * directy from a browser in which case it is easy for the user to pass
    * the data directly via the url.
    */
   public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
      doPost(req, res);
   }
   
   /**
    * POST request from the applet. 
    * <p>
    * Handles all requests coming from the applet. It reads the 
    * passed parameters either from the url (in which case they are
    * encoded) or directly from the input stream (the body of the request).
    * In the latter case they are binary data which is not encoded and
    * is refered to as binary protocol.
    *
    * The asynchronous updates are pushed back using PushHandler.java
    *
    * @param req Data from browser
    * @param res Response of the servlet
    */
   public void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
      String ME;
      // null if the message was not passed via the binary protocol
      MsgHolder binaryMsg = readBinaryProtocol(req, res);

      String actionType = req.getHeader("ActionType");
      
      if (actionType == null) {
         actionType = getParameter(req, "ActionType", "NONE");
      }    
      
      synchronized(AppletServlet.class) {
         requestCounter++;  // For logging only
         String appletInstanceCount = getParameter(req, "appletInstanceCount", "0");
         ME  = this.getClass().getName() + "-" + req.getRemoteAddr() + "-#" + appletInstanceCount + "req" + requestCounter + "-" + actionType;
      }
      
      String appletInstanceCount = getParameter(req, "appletInstanceCount", "0");

      if (actionType.equalsIgnoreCase(I_XmlBlasterAccessRaw.CONNECT_NAME) || 
          actionType.equalsIgnoreCase(I_XmlBlasterAccessRaw.DISCONNECT_NAME) ||
          actionType.equalsIgnoreCase(I_XmlBlasterAccessRaw.PONG_NAME) ||
          actionType.equalsIgnoreCase(I_XmlBlasterAccessRaw.CREATE_SESSIONID_NAME)) { // "connect", "disconnect"
         doGetFake(ME, req, res, actionType, binaryMsg);
         return;
      }

      res.setContentType("text/plain");
      HttpSession session = req.getSession(false);
      String sessionId = req.getRequestedSessionId();
      LogChannel log = this.initialGlobal.getLog("servlet");
      ME  += "-" + sessionId;
      if (log.TRACE) log.trace(ME, "Entering servlet doPost() ...");

      Global glob = null;
      I_XmlBlasterAccess xmlBlaster = null;
      PushHandler pushHandler = null;
      Object returnObject = null;

      try {
         pushHandler = getPushHandler(req);
         xmlBlaster = pushHandler.getXmlBlasterAccess();
         glob = xmlBlaster.getGlobal();
      }
      catch (XmlBlasterException e) {
         log.warn(ME, "Caught XmlBlaster Exception: " + e.getMessage());
         writeResponse(res, I_XmlBlasterAccessRaw.EXCEPTION_NAME, e.getMessage());
         return;
      }

      try {
         // Extract the message data

         MsgHolder msg = extractMessage(ME, log, req, binaryMsg);
         String oid = msg.getOid();
         String key = msg.getKey();
         String qos = msg.getQos();
         String xmlRequest = msg.getKey(); // in case of xmlScript the request is sent as the key (all other are null)
         byte[] content = msg.getContent();
         
         if (actionType.equals(I_XmlBlasterAccessRaw.PING_NAME)) { // "ping"
            if (log.TRACE) log.trace(ME, "ping arrived, qos=" + qos);
            Hashtable map = new Hashtable();
            if (xmlBlaster.isAlive()) {
               map.put("/qos/state/@id", Constants.STATE_OK);
               map.put("/qos/state/@info", ConnectionStateEnum.ALIVE.toString()); // "ALIVE"
            }
            else if (xmlBlaster.isPolling()) {
               map.put("/qos/state/@id", Constants.STATE_OK);
               map.put("/qos/state/@info", ConnectionStateEnum.POLLING.toString()); // "POLLING"
            }
            else {
               map.put("/qos/state/@id", Constants.STATE_WARN);
               map.put("/qos/state/@info", ConnectionStateEnum.DEAD.toString()); // "DEAD"
            }
            returnObject = map;
         }

         else if (actionType.equals(I_XmlBlasterAccessRaw.SUBSCRIBE_NAME)) { // "subscribe"
            if (log.TRACE) log.trace(ME, "subscribe arrived ... oid=" + oid + ", key=" + key + ", qos=" + qos);
      
            if (oid != null) {
               SubscribeKey xmlKey = new SubscribeKey(glob, oid);
               SubscribeReturnQos ret = xmlBlaster.subscribe(xmlKey.toXml(), qos);
               returnObject = ret.getData().toJXPath();
               if (log.TRACE) log.trace(ME, "Subscribed to simple key.oid=" + oid + ": " + ret.getSubscriptionId());
            }
            else if (key != null) {
               SubscribeReturnQos ret = xmlBlaster.subscribe(key, qos);
               returnObject = ret.getData().toJXPath();
               if (log.TRACE) log.trace(ME, "Subscribed to " + key + ": SubscriptionId=" + ret.getSubscriptionId() + " qos=" + qos + " returnObject=" + returnObject.getClass().getName());
            }
            else {
               String str = "Please call servlet with some 'key.oid=...' or 'key=<key ...' when subscribing";
               log.warn(ME, str);
               throw new XmlBlasterException(this.initialGlobal, ErrorCode.USER_CONFIGURATION, ME, str);
            }
         }

         else if (actionType.equals(I_XmlBlasterAccessRaw.UNSUBSCRIBE_NAME)) { // "unSubscribe"
            if (log.TRACE) log.trace(ME, "unSubscribe arrived ...");
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
               log.warn(ME, str);
               throw new XmlBlasterException(this.initialGlobal, ErrorCode.USER_CONFIGURATION, ME, str);
            }
            Vector arr = new Vector();
            for (int ii=0; ii<ret.length; ii++) {
               arr.add(ret[ii].getData().toJXPath());
               if (log.TRACE) log.trace(ME, "UnSubscribed " + ret[ii].getSubscriptionId());
            }
            returnObject = (Hashtable[])arr.toArray(new Hashtable[arr.size()]);
         }

         else if (actionType.equals(I_XmlBlasterAccessRaw.GET_NAME)) { // "get"
            if (log.TRACE) log.trace(ME, "get arrived ...");
            MsgUnit[] msgUnitArr = xmlBlaster.get(key, qos);
            Vector list = new Vector(msgUnitArr.length*3);
            for (int i=0; i<msgUnitArr.length; i++) {
               list.add(((MsgQosData)msgUnitArr[i].getQosData()).toJXPath());
               list.add(((MsgKeyData)msgUnitArr[i].getKeyData()).toJXPath());
               list.add(msgUnitArr[i].getContent());
            }
            returnObject = list;
         }

         else if (actionType.equals(I_XmlBlasterAccessRaw.PUBLISH_NAME)) { // "publish"
            if (log.TRACE) log.trace(ME, "publish arrived ...");
            if (key == null) {
               String str = "Please call servlet with some key when publishing";
               log.warn(ME, str);
               XmlBlasterException x = new XmlBlasterException(this.initialGlobal, ErrorCode.USER_ILLEGALARGUMENT, ME, str);
               writeResponse(res, I_XmlBlasterAccessRaw.EXCEPTION_NAME, x.getMessage());
               return;
            }
            if (log.TRACE) log.trace(ME, "Publishing '" + key + "'");
            MsgUnit msgUnit = new MsgUnit(glob, key, content, qos);
            try {
               PublishReturnQos prq = xmlBlaster.publish(msgUnit);
               returnObject = prq.getData().toJXPath();
               if (log.TRACE) log.trace(ME, "Success: Publishing done, returned oid=" + prq.getKeyOid());
            } catch(XmlBlasterException e) {
               log.warn(ME, "XmlBlasterException: " + e.getMessage());
            }
         }

         else if (actionType.equals(I_XmlBlasterAccessRaw.ERASE_NAME)) { // "erase"
            if (log.TRACE) log.trace(ME, "erase arrived ...");
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
               log.warn(ME, str);
               throw new XmlBlasterException(this.initialGlobal, ErrorCode.USER_CONFIGURATION, ME, str);
            }
            Vector arr = new Vector();
            for (int ii=0; ii<ret.length; ii++) {
               arr.add(ret[ii].getData().toJXPath());
               if (log.TRACE) log.trace(ME, "Erased " + ret[ii].getKeyOid());
            }
            returnObject = (Hashtable[])arr.toArray(new Hashtable[arr.size()]);
         }

         // TODO: not yet tested
         else if (actionType.equals("xmlScript")) {
            // Send xml encoded requests to the xmlBlaster server. 
            // http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.script.html
            java.io.Reader reader = new java.io.StringReader(xmlRequest);
            java.io.OutputStream outStream = new java.io.ByteArrayOutputStream();
            org.xmlBlaster.client.script.XmlScriptInterpreter interpreter = 
                new org.xmlBlaster.client.script.XmlScriptInterpreter(glob, xmlBlaster, pushHandler, null, outStream);
            interpreter.parse(reader);
            returnObject = outStream.toString();
         }

         else {
            String str = "Unknown or missing 'ActionType=" + actionType + "' please choose 'subscribe' 'unSubscribe' 'erase' etc.";
            log.warn(ME, str);
            throw new XmlBlasterException(this.initialGlobal, ErrorCode.USER_CONFIGURATION, ME, str);
         }

         writeResponse(res, actionType, returnObject);
      } catch (XmlBlasterException e) {
         log.warn(ME, "Caught XmlBlaster Exception: " + e.getMessage());
         writeResponse(res, I_XmlBlasterAccessRaw.EXCEPTION_NAME, e.getMessage());
      } catch (Throwable e) {
         log.error(ME, "Exception: " + e.toString());
         writeResponse(res, I_XmlBlasterAccessRaw.EXCEPTION_NAME, XmlBlasterException.convert(this.initialGlobal, ME, "", e).getMessage());
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
   static public final void initSystemProperties(ServletConfig conf) {
      String ME  = "AppletServlet";

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
      if (conf.getInitParameter("org.xml.sax.parser") != null) {
         props.put( "org.xml.sax.parser", conf.getInitParameter("org.xml.sax.parser"));
         if (log.TRACE) log.trace(ME, "Found system parameter org.xml.sax.parser=" + conf.getInitParameter("org.xml.sax.parser"));
      }
      else
         props.put("org.xml.sax.parser", "org.apache.crimson.parser.Parser2"); // xmlBlaster uses Suns XML parser as default
      log.info(ME, "Using system parameter org.xml.sax.parser=" + props.get("org.xml.sax.parser"));

      if (props.size() > 0) {
         System.setProperties(props);
      }
   }


   /**
    * @see #writeResponse(HttpServletResponse, String, text)
    */
   private void writeResponse(HttpServletResponse res, String actionType, String text) throws IOException {
      writeResponse(res, actionType, (text==null) ? (Object)null : (Object)text);
   }

   /**
    * Transforms the given text to send it back to the applet. 
    * <p>
    * The actionType and the text are java.io.Serialized and than Base64 encoded.
    * </p>
    * @param res
    * @param actionType A type with the applet knows how to read, "subscribe" etc. for subscribe return QoS
    */
   private void writeResponse(HttpServletResponse res, String actionType, Object obj) throws IOException {
      //this.initialGlobal.getLog("servlet").trace("AppletServlet", "writeResponse actionType=" + actionType + " obj=" + obj.getClass().getName());
      ByteArrayOutputStream dump = new ByteArrayOutputStream(1024);
      ObjectOutputStreamMicro objectOut = new ObjectOutputStreamMicro(dump);
      objectOut.writeObject(actionType); // "subscribe" etc.
      if (obj != null) {
         objectOut.writeObject(obj);
      }
      boolean isChunked = false; // All in one line
      byte[] base64 = Base64.encodeBase64(dump.toByteArray(), isChunked);
      PrintWriter out = res.getWriter();
      out.println(new String(base64));
      out.flush();
   }

   /**
    * @return The PushHandler of this session, never null
    * @exception If no PushHandler exists
    */
   private PushHandler getPushHandler(HttpServletRequest req) throws XmlBlasterException {
      HttpSession session = req.getSession(false);
      if (session == null) {
         throw new XmlBlasterException(this.initialGlobal, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, "No servlet session available");
      }
      String key = "PushHandler"+getParameter(req, "appletInstanceCount", "0");
      PushHandler pushHandler = (PushHandler)session.getAttribute(key);
      if (pushHandler == null) {
         throw new XmlBlasterException(this.initialGlobal, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, "The PushHandler is missing in the session scope");
      }
      return pushHandler;
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
   public void log(int level, String source, String str) {
      getServletContext().log(str);
   }

   /**
    * Get the request parameter. 
    * <br />
    * NOTE: Servlet API 2.1 or higher
    *
    * @param req request from client
    * @param name parameter name
    * @param defaultVal default value if parameter not found
    * @return The value
    */
   public static final String getParameter(HttpServletRequest req, String name, String defaultVal) {
      if (name == null) {
         return defaultVal;
      }
      Object obj = req.getParameter(name);
      if (obj != null) {
         return (String)obj;
      }
      return defaultVal;
   }

   /**
    * Get a request attribute, if not found the session is checked, if
    * not found again, the given default is returned. 
    * <br />
    * NOTE: Servlet API 2.1 or higher
    *
    * @param req request from client
    * @param name parameter name
    * @param defaultVal default value if parameter not found
    * @return The value
    */
   public static final String getAttribute(HttpServletRequest req, String name, String defaultVal) {
      if (name == null) {
         return defaultVal;
      }
      Object obj = req.getAttribute(name);
      if (obj != null) {
         return (String)obj;
      }
      HttpSession session = req.getSession(false);
      if (session == null) {
         return defaultVal;
      }
      obj = session.getAttribute(name);
      if (obj != null) {
         return (String)obj;
      }
      return defaultVal;
   }

   /**
    * @see #getParameter(HttpServletRequest, String, String)
    */
   public static final boolean getParameter(HttpServletRequest req, String name, boolean defaultVal) {
      Boolean b = new Boolean(getParameter(req, name, new Boolean(defaultVal).toString()));
      return b.booleanValue();
   }
}
