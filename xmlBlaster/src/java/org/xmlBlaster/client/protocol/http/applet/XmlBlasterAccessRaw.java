package org.xmlBlaster.client.protocol.http.applet;

import java.applet.Applet;
import java.net.URLConnection;
import java.net.URL;
import java.io.*;
import java.util.Map;
import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.commons.codec.binary.Base64;
import org.xmlBlaster.util.enum.MethodName;

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
public class XmlBlasterAccessRaw implements I_XmlBlasterAccessRaw
{
   private Applet applet;
   /** Typically "http://localhost:8080/xmlBlaster/AppletServlet" */
   private String xmlBlasterServletUrl;
   private PersistentRequest persistentHttpConnection;
   private I_CallbackRaw callback;
   public static final boolean ONEWAY = true;
   public static final boolean POST = true;
   public static final boolean GET = false;
   private boolean isConnected = false;
   private Properties properties = new Properties();
   private I_Log logListener;
   private String logLevels = "ERROR,WARN,INFO";

   /**
    * Provides access to xmlBlaster server. 
    * @param applet My environment
    * @see #parseAppletParameter
    */
   public XmlBlasterAccessRaw(Applet applet) {
      this.applet = applet;
      parseAppletParameter();
      this.xmlBlasterServletUrl = this.applet.getParameter("xmlBlaster/servletUrl"); //param from html page
      if (this.xmlBlasterServletUrl == null) {
         // getCodeBase() == http://localhost:8080/xmlBlaster/
         this.xmlBlasterServletUrl = this.applet.getCodeBase() + "AppletServlet";
      }
      if (this.applet.getParameter("xmlBlaster/logLevels") != null)
         logLevels = this.applet.getParameter("xmlBlaster/logLevels");

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
   public Properties getHtmlProperties() {
      return this.properties;
   }

   /**
    * Parse the applet parameter from the HTML page. 
    * <p>
    * As the applet class has no getAllParameters() method we expect a PARAM <i>deliveredParamKeys</i>
    * which contains a list of all delivered PARAM in the HTML page.
    * </p>
    * @see #getHtmlProperties
    */
   private void parseAppletParameter() {
      String deliveredParamKeys = this.applet.getParameter("deliveredParamKeys"); // a comma seperated list of all param from html page
      log("DEBUG", "Reading HTML PARAM deliveredParamKeys=" + deliveredParamKeys);
      if (deliveredParamKeys != null) {
         StringTokenizer st = new StringTokenizer(deliveredParamKeys, ",;:");
         while (st.hasMoreTokens()) {
            String key = st.nextToken();
            if (key == null) continue;
            key = key.trim();
            String value = this.applet.getParameter(key);
            if (value != null && value.length() > 0) {
               this.properties.put(key, value);
               log("DEBUG", "Reading HTML PARAM " + key + " = '" + value + "'");
            }
         }
      }
   }

   /**
    * Log to java console of the browser of the logListener if any is registered
    */
   private void log(String level, String text) {
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
         String loginName = this.applet.getParameter("xmlBlaster/loginName");
         String passwd = this.applet.getParameter("xmlBlaster/passwd");
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

   public Map subscribe(java.lang.String xmlKey, java.lang.String qos) throws Exception {
      log("DEBUG", "subscribe(key="+xmlKey+")");
      String keyEnc = encode(xmlKey, "UTF-8");
      String qosEnc = encode(qos, "UTF-8");
      return (Map)request("ActionType=subscribe&key="+keyEnc+"&qos="+qosEnc, POST, !ONEWAY);
   }

   public Msg[] get(java.lang.String xmlKey, java.lang.String qos) throws Exception {
      log("DEBUG", "get(key="+xmlKey+")");
      String keyEnc = encode(xmlKey, "UTF-8");
      String qosEnc = encode(qos, "UTF-8");
      ArrayList list = (ArrayList)request("ActionType=get&key="+keyEnc+"&qos="+qosEnc, POST, !ONEWAY);
      Msg[] msgs = new Msg[list.size()/3];
      for (int i=0; i<list.size()/3; i++) {
         log("DEBUG", "Synchronous get is not implented");
         Map qosRet = (Map)list.get(i*3);
         Map keyRet = (Map)list.get(i*3+1);
         byte[] content = (byte[])list.get(i*3+2);
         msgs[i] = new Msg(keyRet, content, qosRet);
      }
      return msgs;
   }

   public Map[] unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws Exception {
      log("DEBUG", "unSubscribe(key="+xmlKey+")");
      String keyEnc = encode(xmlKey, "UTF-8");
      String qosEnc = encode(qos, "UTF-8");
      return (Map[])request("ActionType=unSubscribe&key="+keyEnc+"&qos="+qosEnc, POST, !ONEWAY);
   }

   public Map publish(String xmlKey, byte[] content, String qos) throws Exception {
      log("DEBUG", "publish(key="+xmlKey+")");
      String keyEnc = encode(xmlKey, "UTF-8");
      String qosEnc = encode(qos, "UTF-8");
      byte[] serial = Base64.encodeBase64(content);
      return (Map)request("ActionType=publish&key="+keyEnc+"&qos="+qosEnc+"&content="+new String(serial), POST, !ONEWAY);
   }

   public Map[] erase(java.lang.String xmlKey, java.lang.String qos) throws Exception {
      log("DEBUG", "erase(key="+xmlKey+")");
      String keyEnc = encode(xmlKey, "UTF-8");
      String qosEnc = encode(qos, "UTF-8");
      return (Map[])request("ActionType=erase&key="+keyEnc+"&qos="+qosEnc, POST, !ONEWAY);
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
         // applet.getAppletContext().showDocument(URL url, String target);
         
         URL url = (doPost) ? new URL(this.xmlBlasterServletUrl) : new URL(this.xmlBlasterServletUrl + request);
         URLConnection conn = url.openConnection();
         conn.setUseCaches(false);

         log("DEBUG", "doPost=" + doPost + ", sending '" + url.toString() + "' with request '" + request + "' ...");
         if(doPost){  // for HTTP-POST, e.g. for  publish(), subscribe()
            conn.setDoOutput(true);
            DataOutputStream dataOutput = new DataOutputStream(conn.getOutputStream());
            dataOutput.writeBytes(request);
            dataOutput.close();
         }

         if (oneway) {
            return "";
         }

         // Read the return value ...
         BufferedReader dataInput = new BufferedReader(new InputStreamReader(conn.getInputStream()));
         String line;
         Object returnObject = null;
         StringBuffer ret = new StringBuffer(1024);
         while ((line = dataInput.readLine()) != null){
            log("DEBUG", "Return value for '" + request + "' = '" + line + "'");
            if (line == null || line.length() < 1)
               continue;
            if (true) { // doPost) {  // All POST is returned Base64 encoded, all GET as ordinary string
               byte[] serial = Base64.decodeBase64(line.getBytes());
               log("DEBUG", "Parsing now: <" + new String(serial) + ">");

               ByteArrayInputStream in = new ByteArrayInputStream(serial);
               ObjectInputStream ois = new ObjectInputStream(in);
               String method = (String)ois.readObject(); // e.g. "subscribe"

               if (MethodName.PUBLISH.toString().equals(method)) {
                  Map returnQos = (Map)ois.readObject();
                  returnObject = returnQos;
               }
               else if (MethodName.GET.toString().equals(method)) {
                  ArrayList returnQos = (ArrayList)ois.readObject();
                  returnObject = returnQos;
               }
               else if (MethodName.SUBSCRIBE.toString().equals(method)) {
                  Map returnQos = (Map)ois.readObject();
                  returnObject = returnQos;
               }
               else if (MethodName.UNSUBSCRIBE.toString().equals(method)) {
                  Map[] returnQos = (Map[])ois.readObject();
                  returnObject = returnQos;
               }
               else if (MethodName.ERASE.toString().equals(method)) {
                  Map[] returnQos = (Map[])ois.readObject();
                  returnObject = returnQos;
               }
               else if (MethodName.DISCONNECT.toString().equals(method)) {
                  returnObject = ois.readObject();
               }
               else if ("exception".equals(method)) {
                  String err = (String)ois.readObject();
                  log("INFO", "Caught XmlBlasterException: " + err);
                  throw new Exception(err);
               }
               else if ("dummyToCreateASessionId".equals(method)) {
                  returnObject = (String)ois.readObject();
               }
               else if ("pong".equals(method)) {
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
   public String update(String cbSessionId, Map updateKey, byte[] content, Map updateQos) throws Exception {
      if (this.callback == null) {
         String text = "Receiving unexpected update message '" + updateKey.get("/key/@oid") + "', no callback handle available";
         log("WARN", "XmlBlasterAccessRaw: " + text);
         throw new Exception(text);
      }
      log("DEBUG", "Receiving update message '" + updateKey.get("/key/@oid") + "' state=" + updateQos.get("/qos/state/@id"));
      return this.callback.update(cbSessionId, updateKey, content, updateQos);
   }

   /**
    * This notation is URLEncoder since JDK 1.4.
    * To avoid deprecation warnings
    * at many places and support JDK < 1.4 we provide it here
    * and simply map it to the old encode(String)
    */
   public static String encode(String s, String enc) {
      return java.net.URLEncoder.encode(s);
   }
}


