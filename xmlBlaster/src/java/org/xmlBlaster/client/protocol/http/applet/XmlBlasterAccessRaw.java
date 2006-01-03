package org.xmlBlaster.client.protocol.http.applet;

import java.applet.Applet;
import java.util.Hashtable;
import java.util.StringTokenizer;

import org.apache.commons.codec.binary.Base64;
import org.xmlBlaster.client.protocol.http.common.*;

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
   /**
    * Provides access to xmlBlaster server. 
    * @param applet My environment
    * @see #parseAppletParameter
    */
   public XmlBlasterAccessRaw(Applet applet) {
      super(parseAppletParameter(applet, null));
   }

   /**
    * Provides access to xmlBlaster server. 
    * @param applet My environment
    * @param properties Additional properties to send to the servlet
    * They must start with "servlet/xyz=someValue". The "servlet/" will
    * be stripped away and in the web-servlet will arrive "xyz=someValue".
    * The key/values are send in the URL. 
    * @see #parseAppletParameter
    */
   public XmlBlasterAccessRaw(Applet applet, Hashtable properties) {
      super(parseAppletParameter(applet, properties));
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
   private static Hashtable parseAppletParameter(Applet applet, Hashtable additionalProperties) {
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
      //String loginName = applet.getParameter("xmlBlaster/loginName");
      //String passwd = applet.getParameter("xmlBlaster/passwd");
      String xmlBlasterServletUrl = applet.getParameter("xmlBlaster/servletUrl"); //param from html page
      if (xmlBlasterServletUrl == null) {
         // getCodeBase() == http://localhost:8080/xmlBlaster/
         xmlBlasterServletUrl = applet.getCodeBase() + "AppletServlet";
      }
      if (xmlBlasterServletUrl != null) properties.put("xmlBlaster/servletUrl", xmlBlasterServletUrl);
      if (applet.getParameter("xmlBlaster/logLevels") != null)
         properties.put("xmlBlaster/logLevels", applet.getParameter("xmlBlaster/logLevels"));

      if (applet.getParameter("xmlBlaster/invalidate") != null) // never used yet, useful?
         properties.put("xmlBlaster/invalidate", applet.getParameter("xmlBlaster/invalidate"));

      if (additionalProperties != null)
         properties.putAll(additionalProperties);
      return properties;
   }

   /**
    * This notation is URLEncoder since JDK 1.4.
    * To avoid deprecation warnings
    * at many places and support JDK &lt; 1.4 we provide it here
    * and simply map it to the old encode(String)
    */
   //public String encode(String s, String enc) {
      //return new String(encodeBase64(s.getBytes()));
      // No Global available:
      //return Global.encode(s, enc);
      // Deprecated since JDK 1.4:
   // return java.net.URLEncoder.encode(s);
      /* JDK >= 1.4 (older won't compile)
      try {
         return java.net.URLEncoder.encode(s, enc);
      }
      catch (java.io.UnsupportedEncodingException e) {
         throw new IllegalArgumentException(e.toString());
      }
      */
   //}
   
   public byte[] encodeBase64(byte[] data) {
      return Base64.encodeBase64(data);
   }

   public byte[] decodeBase64(byte[] data) {
      return Base64.decodeBase64(data);
   }

   public I_Connection createConnection(String urlString) throws Exception {
      return new UrlConnection(urlString);
   }

}


