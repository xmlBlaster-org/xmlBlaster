package org.xmlBlaster.client.protocol.http.applet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.StringTokenizer;
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
   public static final boolean POST = true;
   public static final boolean GET = false;
   private boolean isConnected = false;
   protected Hashtable properties = new Hashtable();
   protected I_Log logListener;
   protected String logLevels = "ERROR,WARN,INFO";

   /**
    * Provides access to xmlBlaster server. 
    * @see #parseAppletParameter
    */
   public XmlBlasterAccessRawBase(Hashtable properties) {
      this.properties = properties;
      this.xmlBlasterServletUrl = (String)this.properties.get("xmlBlaster/servletUrl"); //param from html page
      if (this.properties.get("xmlBlaster/logLevels") != null)
         logLevels = (String)this.properties.get("xmlBlaster/logLevels");
      log("DEBUG", "constructor - " + this.xmlBlasterServletUrl);
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
      if (logLevels.indexOf(level) != -1) System.out.println(location + " [" + level + "]: " + text);
   }

   /** 
    * Access the URL of the xmlBlaster servlet. 
    * @return Typically "http://localhost:8080/xmlBlaster/AppletServlet"
    */
   public String getXmlBlasterServletUrl() {
      return this.xmlBlasterServletUrl;
   }

   public void isConnected(boolean isConnected) {
      this.isConnected = isConnected;
      log("INFO", "isConnected(" + isConnected + ")");
   }

   public boolean isConnected() {
      return this.isConnected;
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
            log("INFO", "Using loginName = " + loginName + " as configured in your HTML page to connect to xmlBlaster");
            this.persistentHttpConnection = new PersistentRequest(this, this.xmlBlasterServletUrl, loginName, passwd);
         }
         else
            qos = "<qos/>"; // Servlet does authentication (can be a security issue!)
      }

      if (qos != null) {
         this.persistentHttpConnection = new PersistentRequest(this, this.xmlBlasterServletUrl, qos);
      }

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
      log("INFO", "Successfully connected to xmlBlaster");
      return this.persistentHttpConnection.getConnectReturnQos();
   }

   public Hashtable subscribe(java.lang.String xmlKey, java.lang.String qos) throws Exception {
      log("DEBUG", "subscribe(key="+xmlKey+")");
      String keyEnc = encode(xmlKey, "UTF-8");
      String qosEnc = encode(qos, "UTF-8");
      return (Hashtable)request("ActionType=subscribe&key="+keyEnc+"&qos="+qosEnc, POST, !ONEWAY);
   }

   public Msg[] get(java.lang.String xmlKey, java.lang.String qos) throws Exception {
      log("DEBUG", "get(key="+xmlKey+")");
      String keyEnc = encode(xmlKey, "UTF-8");
      String qosEnc = encode(qos, "UTF-8");
      Vector list = (Vector)request("ActionType=get&key="+keyEnc+"&qos="+qosEnc, POST, !ONEWAY);
      Msg[] msgs = new Msg[list.size()/3];
      for (int i=0; i<list.size()/3; i++) {
         log("DEBUG", "Synchronous get is not implented");
         Hashtable qosRet = (Hashtable)list.get(i*3);
         Hashtable keyRet = (Hashtable)list.get(i*3+1);
         byte[] content = (byte[])list.get(i*3+2);
         msgs[i] = new Msg(keyRet, content, qosRet);
      }
      return msgs;
   }

   public Hashtable[] unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws Exception {
      log("DEBUG", "unSubscribe(key="+xmlKey+")");
      String keyEnc = encode(xmlKey, "UTF-8");
      String qosEnc = encode(qos, "UTF-8");
      return (Hashtable[])request("ActionType=unSubscribe&key="+keyEnc+"&qos="+qosEnc, POST, !ONEWAY);
   }

   public Hashtable publish(String xmlKey, byte[] content, String qos) throws Exception {
      log("DEBUG", "publish(key="+xmlKey+")");
      String keyEnc = encode(xmlKey, "UTF-8");
      String qosEnc = encode(qos, "UTF-8");
      byte[] serial = encodeBase64(content);
      return (Hashtable)request("ActionType=publish&key="+keyEnc+"&qos="+qosEnc+"&content="+new String(serial), POST, !ONEWAY);
   }

   public Hashtable[] erase(java.lang.String xmlKey, java.lang.String qos) throws Exception {
      log("DEBUG", "erase(key="+xmlKey+")");
      String keyEnc = encode(xmlKey, "UTF-8");
      String qosEnc = encode(qos, "UTF-8");
      return (Hashtable[])request("ActionType=erase&key="+keyEnc+"&qos="+qosEnc, POST, !ONEWAY);
   }

   public void disconnect(String qos) {
      log("DEBUG", "disconnect()");
      String qosEnc = encode(qos, "UTF-8");
      try {
         request("ActionType=disconnect&qos="+qosEnc, POST, !ONEWAY);
         log("INFO", "Successfully disconnected from xmlBlaster");
      }
      catch (Exception e) {
         log("WARN", "Ignoring unexpected exception during disconnect: " + e.toString());
      }
   }

   /**
    * Send a http request to the servlet. 
    * @param request The request string without the URL prefix, e.g. "?XmlBlasterAccessRawType=pong"
    * @param doPost if true POST else GET
    * @param oneway true for requests returning void
    * @return The returned value for the given request, "" on error or for oneway messages
    */
   public Object request(String request, boolean doPost, boolean oneway) throws Exception {
      try {
         InputStream in = prepareRequest(request, doPost, oneway);

         if (oneway) {
            return "";
         }

         // Read the return value ...
         BufferedInputStreamMicro dataInput = new BufferedInputStreamMicro(in);

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

   /**
    * see I_CallbackRaw#update
    */
   public String update(String cbSessionId, Hashtable updateKey, byte[] content, Hashtable updateQos) throws Exception {
      if (this.callback == null) {
         String text = "Receiving unexpected update message '" + updateKey.get("/key/@oid") + "', no callback handle available";
         log("WARN", "XmlBlasterAccessRaw: " + text);
         throw new Exception(text);
      }
      log("DEBUG", "Receiving update message '" + updateKey.get("/key/@oid") + "' state=" + updateQos.get("/qos/state/@id"));
      return this.callback.update(cbSessionId, updateKey, content, updateQos);
   }

   protected Hashtable extractCookies(String cookieTxt) {
      Hashtable ret = new Hashtable();
      StringTokenizer token = new StringTokenizer(cookieTxt.trim(), ";");
      while (token.hasMoreTokens()) {
         String prop = token.nextToken().trim();
         int pos = prop.indexOf("=");
         if (pos < 0) continue;
         String key = prop.substring(0, pos);
         String val = prop.substring(pos+1);
         log("DEBUG", " extractCookies: " + "(key='" + key + "', val='" + val + "')");
         ret.put(key, val);
      }
      return ret;
   }

   public abstract String encode(String s, String enc);
   
   public abstract byte[] encodeBase64(byte[] data);

   public abstract byte[] decodeBase64(byte[] data);

   public abstract void readCookie(Object conn);

   public abstract void writeCookie(Object conn);
   
   protected abstract InputStream prepareRequest(String request, boolean doPost, boolean oneway) throws Exception;
}


