package org.xmlBlaster.client.protocol.http.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

/**
 * A java client implementation to access xmlBlaster using a persistent http connection
 * for instant callback messages. 
 * <p>
 * You can control logging with the Applet PARAM tag, logging output is put to the Java console
 * of your browser:
 * </p>
 * <pre>
 * &lt;PARAM name="xmlBlaster/logLevels" value="ERROR,WARN">
 * with more logging:
 * &lt;PARAM name="xmlBlaster/logLevels" value="ERROR,WARN,INFO,DEBUG">
 * </pre>
 * See the example applet {@link http.applet.HelloWorld3} on how to use it.
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see http.applet.HelloWorld3
 */
public abstract class XmlBlasterAccessRawBase implements I_XmlBlasterAccessRaw
{
   /** Typically "http://localhost:8080/xmlBlaster/AppletServlet" */
   protected String xmlBlasterServletUrl;
   private PersistentRequest persistentHttpConnection;
   private I_CallbackRaw callback;
   public static final boolean ONEWAY = true;
   private boolean isConnected = false;
   protected Hashtable properties = new Hashtable();
   protected I_Log logListener;
   protected String logLevels = "ERROR,WARN,INFO";
   private Hashtable cookie;
   private static int staticInstanceCounter;
   protected int instanceCount;

   /**
    * Provides access to xmlBlaster server. 
    * @see #parseAppletParameter
    */
   public XmlBlasterAccessRawBase(Hashtable properties) {
      synchronized (XmlBlasterAccessRawBase.class) {
         staticInstanceCounter++;
         this.instanceCount = staticInstanceCounter;
      }
      this.properties = properties;
      this.xmlBlasterServletUrl = (String)this.properties.get("xmlBlaster/servletUrl"); //param from html page
      if (this.properties.get("xmlBlaster/logLevels") != null)
         logLevels = (String)this.properties.get("xmlBlaster/logLevels");
      log("DEBUG", new StringBuffer("constructor - ").append(getXmlBlasterServletUrl()).toString());
   }

   public String getInstanceId() {
      return ""+getInstanceCount();
   }

   /**
    * Access the unique counter of this object instance. 
    */
   public int getInstanceCount() {
      return this.instanceCount;
   }

   /**
    * Register to receive the logging output
    * @param listener If null we log to the java console of the browser
    */
   public synchronized void setLogListener(I_Log logListener) {
      this.logListener = logListener;

   }
   /**
    * Get a list of all PARAM in the HTML file following our convention. 
    * <p>
    * As the applet class has no getAllParameters() method we expect a PARAM <i>deliveredParamKeys</i>
    * which contains a list of all delivered PARAM in the HTML page:
    * </p>
    * <pre>
    *  &lt;applet ...>
    *     &lt;param name="deliveredParamKeys" value="protocol,anotherKey,Key3">
    *     &lt;param name="protocol" value="SOCKET">
    *     &lt;param name="anotherKey" value="someValue">
    *     &lt;param name="Key3" value="xxx">
    *  &lt;/applet>
    * </pre>
    */
   public Hashtable getHtmlProperties() {
      return this.properties;
   }

   /**
    * Log to java console of the browser of the logListener if any is registered
    */
   protected void log(String level, String text) {
      log("XmlBlasterAccess", level, text);
   }

   /**
    * @see I_XmlBlasterAccessRaw#log(String, String, String)
    */
   public synchronized void log(String location, String level, String text) {
      if (this.logListener != null) {
         this.logListener.log(location, level, text);
      }
      if (logLevels.indexOf(level) != -1) System.out.println(new StringBuffer(location).append(" #").append(this.instanceCount).append(" [").append(level).append("]: ").append(text).toString());
   }

   /** 
    * Access the URL of the xmlBlaster servlet. 
    * @return Typically "http://localhost:8080/xmlBlaster/AppletServlet&appletInstanceCount=1"
    */
   public String getXmlBlasterServletUrl() {
      String url = this.xmlBlasterServletUrl;
      if (url != null) {
         StringBuffer tmp = new StringBuffer();
         if (url.indexOf("?") >= 0) 
            tmp.append(url).append("&appletInstanceCount=").append(this.instanceCount);
         else
            tmp.append(url).append("?appletInstanceCount=").append(this.instanceCount);
         url = tmp.toString();
      }
      log("DEBUG", new StringBuffer("URL=").append(url).toString());
      return url;
   }

   public void isConnected(boolean isConnected) {
      this.isConnected = isConnected;
      log("INFO", new StringBuffer("isConnected(").append(isConnected).append(")").toString());
   }

   public boolean isConnected() {
      return this.isConnected;
   }

   private String startPersistentHttpConnection() throws Exception {
      this.persistentHttpConnection.start();
      log("DEBUG", "Waiting for connect() to establish ...");

      int num = 100;
      int i;
      for (i=0; i<num; i++) {
         if (this.isConnected) {
            break;
         }
         try {
            Thread.sleep(500);
         } catch(java.lang.InterruptedException e){
            log("WARN", e.toString());
         }
      }
      if (i >= num) {
         log("ERROR", "Can't login to xmlBlaster, timed out.");
         throw new Exception("Can't login to xmlBlaster, timed out.");
      }
      log("INFO", new StringBuffer("Successfully connected to xmlBlaster '").append(getXmlBlasterServletUrl()).append("'").toString());
      return this.persistentHttpConnection.getConnectReturnQos();
   }

   /**
    * @see I_XmlBlasterAccessRaw#connect(String, I_CallbackRaw)
    */
   public String connect(String qos, I_CallbackRaw callback) throws Exception {
      this.callback = callback;

      // TODO!!!: pass getHtmlProperties() to the servlet to be used to initialize Global
      
      if (qos == null) {
         String loginName = (String)this.properties.get("xmlBlaster/loginName");
         String passwd = (String)this.properties.get("xmlBlaster/passwd");
         if (loginName != null && passwd != null) {
            log("INFO", new StringBuffer("Using loginName = ").append(loginName).append(" as configured in your HTML page to connect to xmlBlaster").toString());
            this.persistentHttpConnection = new PersistentRequest(this, getXmlBlasterServletUrl(), loginName, passwd);
         }
         else
            qos = "<qos/>"; // Servlet does authentication (can be a security issue!)
      }

      if (qos != null) {
         this.persistentHttpConnection = new PersistentRequest(this, getXmlBlasterServletUrl(), qos);
      }

      return startPersistentHttpConnection();
   }

   /**
    * @see I_XmlBlasterAccessRaw#connect(String, I_CallbackRaw)
    */
   public String sendXmlScript(String xmlRequest) throws Exception {
      log("DEBUG", new StringBuffer("xmlScript(xmlRequest=").append(xmlRequest).append(")").toString());
      return (String)postRequest("xmlScript", xmlRequest, null, null, !ONEWAY);
   }

   public Hashtable ping(java.lang.String qos) throws Exception {
      log("DEBUG", new StringBuffer("ping(qos=").append(qos).append(")").toString());
      return (Hashtable)postRequest(PING_NAME, null, qos, null, !ONEWAY);
   }

   public Hashtable subscribe(java.lang.String xmlKey, java.lang.String qos) throws Exception {
      log("DEBUG", new StringBuffer("subscribe(key=").append(xmlKey).append(")").toString());
      return (Hashtable)postRequest(SUBSCRIBE_NAME, xmlKey, qos, null, !ONEWAY);
   }

   public Msg[] get(java.lang.String xmlKey, java.lang.String qos) throws Exception {
      log("DEBUG", new StringBuffer("get(key=").append(xmlKey).append(")").toString());
      //String keyEnc = encode(xmlKey, "UTF-8");
      //String qosEnc = encode(qos, "UTF-8");
      Vector list = (Vector)postRequest(GET_NAME, xmlKey, qos, null, !ONEWAY);
      Msg[] msgs = new Msg[list.size()/3];
      for (int i=0; i<list.size()/3; i++) {
         log("DEBUG", "Synchronous get is not implented");
         Hashtable qosRet = (Hashtable)list.elementAt(i*3);
         Hashtable keyRet = (Hashtable)list.elementAt(i*3+1);
         byte[] content = (byte[])list.elementAt(i*3+2);
         msgs[i] = new Msg(keyRet, content, qosRet);
      }
      return msgs;
   }

   public Hashtable[] unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws Exception {
      log("DEBUG", new StringBuffer("unSubscribe(key=").append(xmlKey).append(")").toString());
      return (Hashtable[])postRequest(UNSUBSCRIBE_NAME, xmlKey, qos, null, !ONEWAY);
   }

   public Hashtable publish(String xmlKey, byte[] content, String qos) throws Exception {
      log("DEBUG", new StringBuffer("publish(key=").append(xmlKey).append(")").toString());
      return (Hashtable)postRequest(PUBLISH_NAME, xmlKey, qos, content, !ONEWAY);
   }

   public Hashtable[] erase(java.lang.String xmlKey, java.lang.String qos) throws Exception {
      log("DEBUG", new StringBuffer("erase(key=").append(xmlKey).append(")").toString());
      return (Hashtable[])postRequest(ERASE_NAME, xmlKey, qos, null, !ONEWAY);
   }

   public void disconnect(String qos) {
      log("DEBUG", "disconnect()");
      try {
         postRequest("disconnect", null, qos, null, !ONEWAY);
         log("INFO", "Successfully disconnected from xmlBlaster");
      }
      catch (Exception e) {
         log("WARN", new StringBuffer("Ignoring unexpected exception during disconnect: ").append(e.toString()).toString());
      }
   }
   
   /**
    * The format:
    * oid + \0 + key + '\0' + qos + '\0' + content: length = oid + key + qos + content + 3
    * @param conn
    * @param actionType
    * @param key
    * @param qos
    * @param content
    */
   static void writeRequest(I_Connection conn, String actionType, String key, String qos, byte[] content) throws IOException {
      conn.setRequestProperty("ActionType", actionType);
      conn.setRequestProperty("BinaryProtocol", "true");
      int length = ObjectOutputStreamMicro.getMessageLength(null, key, qos, content);
      // this is needed since J2ME does not set Content-Length (don't know why)
      conn.setRequestProperty("Data-Length", String.valueOf(length));
      ObjectOutputStreamMicro.writeMessage(conn.getOutputStream(), null, key, qos, content);
      //conn.getOutputStream().close();
   }


   /**
    * Send a http request to the servlet. 
    * @param request The request string without the URL prefix, e.g. "?XmlBlasterAccessRawType=pong"
    * @param doPost if true POST else GET
    * @param oneway true for requests returning void
    * @return The returned value for the given request, "" on error or for oneway messages
    */
   /*
   Object postRequestOld(String actionType, String key, String qos, byte[] content, boolean oneway) throws Exception {
      String request = "ActionType=" + actionType;
      try {
         boolean doPost = true;
         // applet.getAppletContext().showDocument(URL url, String target);
         //String url = (doPost) ? this.xmlBlasterServletUrl : this.xmlBlasterServletUrl + request;

         if ("xmlScript".equals(actionType)) {
            if (key != null) request += "&xmlRequest=" + encode(key, "UTF-8");
         }
         else {
            if (key != null) request += "&key=" + encode(key, "UTF-8");
            if (qos != null) request += "&qos=" + encode(qos, "UTF-8");
            if (content != null) request += "&content=" + encode(new String(content), "UTF-8");
         }

         String url = (doPost) ? this.xmlBlasterServletUrl + "?" + request : this.xmlBlasterServletUrl + request;
      
         I_Connection conn = createConnection(url);      
         // conn.setUseCaches(false);
         writeCookie(conn);
         log("DEBUG", "doPost=" + doPost + ", sending '" + url + "' with request '" + request + "' ...");
         if(doPost){  // for HTTP-POST, e.g. for  publish(), subscribe()
            conn.setDoOutput(true);
            conn.setPostMethod();
            conn.setDoInput(true);
            conn.setUseCaches(false);
            writeRequest(conn, actionType, key, qos, content);
         }

         conn.connect();
         readCookie(conn);

         if (oneway) {
            return "";
         }

         // Read the return value ...
         BufferedInputStreamMicro dataInput = new BufferedInputStreamMicro(conn.getInputStream());

         String line;
         Object returnObject = null;
         StringBuffer ret = new StringBuffer(1024);
         while ((line = dataInput.readLine()) != null){
            log("DEBUG", "Return value for '" + request + "' = '" + line + "'");
            if (line == null || line.length() < 1)
               continue;
            if (true) { // doPost) {  // All POST is returned Base64 encoded, all GET as ordinary string
               byte[] serial = decodeBase64(line.getBytes());
               log("DEBUG", "Parsing now: <" + new String(serial) + ">");

               ByteArrayInputStream bais = new ByteArrayInputStream(serial);
               ObjectInputStreamMicro ois = new ObjectInputStreamMicro(bais);
               String method = (String)ois.readObject(); // e.g. "subscribe"

               if (PUBLISH_NAME.equals(method)) {
                  Hashtable returnQos = (Hashtable)ois.readObject();
                  returnObject = returnQos;
               }
               else if (GET_NAME.equals(method)) {
                  Vector returnQos = (Vector)ois.readObject();
                  returnObject = returnQos;
               }
               else if (SUBSCRIBE_NAME.equals(method)) {
                  Hashtable returnQos = (Hashtable)ois.readObject();
                  returnObject = returnQos;
               }
               else if (UNSUBSCRIBE_NAME.equals(method)) {
                  Hashtable[] returnQos = (Hashtable[])ois.readObject();
                  returnObject = returnQos;
               }
               else if (ERASE_NAME.equals(method)) {
                  Hashtable[] returnQos = (Hashtable[])ois.readObject();
                  returnObject = returnQos;
               }
               else if ("xmlScript".equals(method)) {
                  returnObject = (String)ois.readObject();
               }
               else if (DISCONNECT_NAME.equals(method)) {
                  returnObject = ois.readObject();
               }
               else if (EXCEPTION_NAME.equals(method)) {
                  String err = (String)ois.readObject();
                  log("INFO", "Caught XmlBlasterException: " + err);
                  throw new Exception(err);
               }
               else if (CREATE_SESSIONID_NAME.equals(method)) {
                  returnObject = (String)ois.readObject();
               }
               else if (PONG_NAME.equals(method)) {
                  returnObject = (String)ois.readObject();
               }
               else {
                  log("ERROR", "Unknown method=" + method);
                  returnObject = line;
               }
            }
            else
               returnObject = line;
         }
         return returnObject;
      }
      catch (java.lang.ClassNotFoundException e) {
         e.printStackTrace();
         log("ERROR", "request(" + request + ") failed: " + e.toString());
      }
      catch (IOException e) {
         e.printStackTrace();
         log("ERROR", "request(" + request + ") failed: " + e.toString());
      }
      return "";
   }
   */

   Object postRequest(String actionType, String key, String qos, byte[] content, boolean oneway) throws Exception {
      String request = new StringBuffer("ActionType=").append(actionType).toString();
      try {
         boolean doPost = true;
         String url = getXmlBlasterServletUrl();

         I_Connection conn = createConnection(url);      
         // conn.setUseCaches(false);
         writeCookie(conn);
         log("DEBUG", new StringBuffer("doPost=").append(doPost).append(", sending '").append(url).append("' with request '").append(request).append("' ...").toString());
         if(doPost){  // for HTTP-POST, e.g. for  publish(), subscribe()
            conn.setDoOutput(true);
            conn.setPostMethod();
            conn.setDoInput(true);
            conn.setUseCaches(false);
            writeRequest(conn, actionType, key, qos, content);
         }

         conn.connect();
         readCookie(conn);

         if (oneway) {
            return "";
         }

         // Read the return value ...
         BufferedInputStreamMicro dataInput = new BufferedInputStreamMicro(conn.getInputStream());

         String line;
         Object returnObject = null;
         while ((line = dataInput.readLine()) != null){
            log("DEBUG", new StringBuffer("Return value for '").append(request).append("' = '").append(line).append("'").toString());
            if (line == null || line.length() < 1)
               continue;
            if (true) { // doPost) {  // All POST is returned Base64 encoded, all GET as ordinary string
               byte[] serial = decodeBase64(line.getBytes());
               //log("DEBUG", "Parsing now: <" + new String(serial) + ">");

               ByteArrayInputStream bais = new ByteArrayInputStream(serial);
               ObjectInputStreamMicro ois = new ObjectInputStreamMicro(bais);
               String method = (String)ois.readObject(); // e.g. "subscribe"

               if (PUBLISH_NAME.equals(method)) {
                  Hashtable returnQos = (Hashtable)ois.readObject();
                  returnObject = returnQos;
               }
               else if (GET_NAME.equals(method)) {
                  Vector returnQos = (Vector)ois.readObject();
                  returnObject = returnQos;
               }
               else if (PING_NAME.equals(method)) {
                  Hashtable returnQos = (Hashtable)ois.readObject();
                  returnObject = returnQos;
               }
               else if (SUBSCRIBE_NAME.equals(method)) {
                  Hashtable returnQos = (Hashtable)ois.readObject();
                  returnObject = returnQos;
               }
               else if (UNSUBSCRIBE_NAME.equals(method)) {
                  Hashtable[] returnQos = (Hashtable[])ois.readObject();
                  returnObject = returnQos;
               }
               else if (ERASE_NAME.equals(method)) {
                  Hashtable[] returnQos = (Hashtable[])ois.readObject();
                  returnObject = returnQos;
               }
               else if ("xmlScript".equals(method)) {
                  returnObject = (String)ois.readObject();
               }
               else if (DISCONNECT_NAME.equals(method)) {
                  returnObject = ois.readObject();
               }
               else if (EXCEPTION_NAME.equals(method)) {
                  String err = (String)ois.readObject();
                  log("INFO", new StringBuffer("Caught XmlBlasterException: ").append(err).toString());
                  throw new Exception(err);
               }
               else if (CREATE_SESSIONID_NAME.equals(method)) {
                  returnObject = (String)ois.readObject();
               }
               else if (PONG_NAME.equals(method)) {
                  returnObject = (String)ois.readObject();
               }
               else {
                  log("ERROR", new StringBuffer("Unknown method=").append(method).toString());
                  returnObject = line;
               }
            }
            else
               returnObject = line;
         }
         return returnObject;
      }
      catch (java.lang.ClassNotFoundException e) {
         e.printStackTrace();
         log("ERROR", new StringBuffer("request(").append(request).append(") failed: ").append(e.toString()).toString());
      }
      catch (IOException e) {
         e.printStackTrace();
         log("ERROR", new StringBuffer("request(").append(request).append(") failed: ").append(e.toString()).toString());
      }
      return "";
   }

   /**
    * see I_CallbackRaw#update
    */
   public String update(String cbSessionId, Hashtable updateKey, byte[] content, Hashtable updateQos) throws Exception {
      if (this.callback == null) {
         String text = new StringBuffer("Receiving unexpected update message '").append(updateKey.get("/key/@oid")).append("', no callback handle available").toString();
         log("WARN", text);
         throw new Exception(text);
      }
      log("DEBUG", new StringBuffer("Receiving update message '").append(updateKey.get("/key/@oid")).append("' state=").append(updateQos.get("/qos/state/@id")).toString());
      return this.callback.update(cbSessionId, updateKey, content, updateQos);
   }

   /**
    * Converts a string containing all cookies to an hashtable containing
    * all cookies as key/value pairs.
    *  
    * @param cookieTxt The string from which to extract the cookies
    * @return an hashtable containing all found cookies as key/value pairs
    */
   protected Hashtable extractCookies(String cookieTxt) {
      Hashtable ret = new Hashtable();
      //StringTokenizer token = new StringTokenizer(cookieTxt.trim(), ";");
      
      char ch = ';';
      Vector token = new Vector();
      cookieTxt = cookieTxt.trim();
      while (true) {
         int pos=cookieTxt.indexOf(ch);         
         if (pos < 0) {
            if (cookieTxt.length() > 0) token.addElement(cookieTxt);
            break;
         }
         token.addElement(cookieTxt.substring(0, pos));
         cookieTxt = cookieTxt.substring(pos+1).trim(); 
      }

      for (int i=0; i < token.size(); i++) {
         String prop = (String)token.elementAt(i);
         int pos = prop.indexOf("=");
         if (pos < 0) continue;
         String key = prop.substring(0, pos);
         String val = prop.substring(pos+1);
         log("DEBUG", new StringBuffer(" extractCookies: ").append("(key='").append(key).append("', val='").append(val).append("')").toString());
         ret.put(key, val);
      }
      return ret;
   }

   /**
    * reads the cookie and stores it.
    * @param conn
    */
   public void readCookie(I_Connection conn) {
      //conn.setRequestProperty("cookie", "JSESSIONID=" + this.sessionId);
      log("DEBUG", new StringBuffer(" readCookie: Cookie         : ").append(conn.getHeaderField("Cookie")).toString());
      log("DEBUG", new StringBuffer(" readCookie: Set-Cookie     : ").append(conn.getHeaderField("Set-Cookie")).toString());

      String setCookie = conn.getHeaderField("Set-Cookie"); 
      if (setCookie != null) this.cookie = extractCookies(setCookie);
   }

   public void writeCookie(I_Connection conn) {
      //conn.setRequestProperty("cookie", "JSESSIONID=" + this.sessionId);
      log("DEBUG", new StringBuffer("writeCookie: original cookie: ").append(this.cookie).toString());
      if (this.cookie == null) {
         conn.setRequestProperty("cookie", "");
      }
      else conn.setRequestProperty("cookie", new StringBuffer("JSESSIONID=").append((String)this.cookie.get("JSESSIONID")).toString());
      conn.setRequestProperty("Cache-Control", "no-cache");
      conn.setRequestProperty("Pragma", "no-cache");
      conn.setRequestProperty("Connection", "keep-alive");
   }

   /**
    * Url encodes the string
    * @param s
    * @param enc
    * @return
    */
   //public abstract String encode(String s, String enc);
   
   public abstract byte[] encodeBase64(byte[] data);

   /**
    * decodes binary data to Base64. The returned bytes are all
    * text characters.
    * 
    * @param data
    * @return
    */
   public abstract byte[] decodeBase64(byte[] data);

   public abstract I_Connection createConnection(String urlString) throws Exception;

}


