package org.xmlBlaster.client.protocol.http.applet;

import java.applet.Applet;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Hashtable;
import java.util.StringTokenizer;

import org.apache.commons.codec.binary.Base64;
//import org.xmlBlaster.util.def.MethodName;

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
public class XmlBlasterAccessRaw extends XmlBlasterAccessRawBase
{
   
   private boolean runsAsApplet;
   private Hashtable cookie;
   
   /**
    * Provides access to xmlBlaster server. 
    * @param applet My environment
    * @see #parseAppletParameter
    */
   public XmlBlasterAccessRaw(Applet applet) {
      super(parseAppletParameter(applet));
      this.runsAsApplet = true;
   }

   /**
    * Provides access to xmlBlaster server. 
    * @see #parseAppletParameter
    */
   public XmlBlasterAccessRaw(Hashtable properties) {
      super(properties);
   }

   /**
    * Parse the applet parameter from the HTML page. 
    * <p>
    * As the applet class has no getAllParameters() method we expect a PARAM <i>deliveredParamKeys</i>
    * which contains a list of all delivered PARAM in the HTML page.
    * </p>
    * @see #getHtmlProperties
    */
   private static Hashtable parseAppletParameter(Applet applet) {
      String deliveredParamKeys = applet.getParameter("deliveredParamKeys"); // a comma seperated list of all param from html page
      Hashtable properties = new Hashtable();
      //log("DEBUG", "Reading HTML PARAM deliveredParamKeys=" + deliveredParamKeys);
      if (deliveredParamKeys != null) {
         StringTokenizer st = new StringTokenizer(deliveredParamKeys, ",;:");
         while (st.hasMoreTokens()) {
            String key = st.nextToken();
            if (key == null) continue;
            key = key.trim();
            String value = applet.getParameter(key);
            if (value != null && value.length() > 0) {
               properties.put(key, value);
               //log("DEBUG", "Reading HTML PARAM " + key + " = '" + value + "'");
            }
         }
      }
      String loginName = applet.getParameter("xmlBlaster/loginName");
      String passwd = applet.getParameter("xmlBlaster/passwd");
      String xmlBlasterServletUrl = applet.getParameter("xmlBlaster/servletUrl"); //param from html page
      if (xmlBlasterServletUrl == null) {
         // getCodeBase() == http://localhost:8080/xmlBlaster/
         xmlBlasterServletUrl = applet.getCodeBase() + "AppletServlet";
      }
      if (xmlBlasterServletUrl != null) properties.put("xmlBlaster/servletUrl", xmlBlasterServletUrl);
      if (applet.getParameter("xmlBlaster/logLevels") != null)
         properties.put("xmlBlaster/logLevels", applet.getParameter("xmlBlaster/logLevels"));
      return properties;
   }


   /**
    * This notation is URLEncoder since JDK 1.4.
    * To avoid deprecation warnings
    * at many places and support JDK < 1.4 we provide it here
    * and simply map it to the old encode(String)
    */
   public String encode(String s, String enc)
   {
     return java.net.URLEncoder.encode(s);
   }
   
   public byte[] encodeBase64(byte[] data) {
      return Base64.encodeBase64(data);
   }

   public byte[] decodeBase64(byte[] data) {
      return Base64.decodeBase64(data);
   }

   protected InputStream prepareRequest(String request, boolean doPost, boolean oneway) throws Exception {
      // applet.getAppletContext().showDocument(URL url, String target);
      URL url = (doPost) ? new URL(this.xmlBlasterServletUrl) : new URL(this.xmlBlasterServletUrl + request);
      URLConnection conn = url.openConnection();
      conn.setUseCaches(false);
      writeCookie(conn);
      log("DEBUG", "doPost=" + doPost + ", sending '" + url.toString() + "' with request '" + request + "' ...");
      if(doPost){  // for HTTP-POST, e.g. for  publish(), subscribe()
         conn.setDoOutput(true);
         DataOutputStream dataOutput = new DataOutputStream(conn.getOutputStream());
         dataOutput.writeBytes(request);
         dataOutput.close();
      }
      readCookie(conn);
      return conn.getInputStream();
   }


   public void readCookie(Object obj) {
      if (!this.runsAsApplet) { // no cookie is setting the sessionId
         //conn.setRequestProperty("cookie", "JSESSIONID=" + this.sessionId);
         URLConnection conn = (URLConnection)obj;
         String threadName = Thread.currentThread().getName();
         log("DEBUG", threadName + " readCookie: original cookie: " + cookie);
         log("DEBUG", threadName + " readCookie: Cookie         : " + conn.getRequestProperty("Cookie"));
         log("DEBUG", threadName + " readCookie: Set-Cookie     : " + conn.getHeaderField("Set-Cookie"));

         String setCookie = conn.getHeaderField("Set-Cookie"); 
         if (setCookie != null) this.cookie = extractCookies(setCookie);
      }
   }

   public void writeCookie(Object obj) {
      if (!this.runsAsApplet) { // no cookie is setting the sessionId
         //conn.setRequestProperty("cookie", "JSESSIONID=" + this.sessionId);
         URLConnection conn = (URLConnection)obj;
         String threadName = Thread.currentThread().getName();
         log("DEBUG", threadName + " writeCookie: original cookie: " + this.cookie);
         if (this.cookie == null) {
            conn.setRequestProperty("cookie", "");
         }
         else conn.setRequestProperty("cookie", "JSESSIONID=" + (String)this.cookie.get("JSESSIONID"));
         conn.setRequestProperty("Cache-Control", "no-cache");
         conn.setRequestProperty("Pragma", "no-cache");
         conn.setRequestProperty("Connection", "keep-alive");
      }
   }

}


