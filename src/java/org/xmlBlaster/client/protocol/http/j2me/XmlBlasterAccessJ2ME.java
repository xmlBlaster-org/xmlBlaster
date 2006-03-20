package org.xmlBlaster.client.protocol.http.j2me;

import java.util.Hashtable;

import javax.microedition.midlet.MIDlet;
import org.xmlBlaster.client.protocol.http.common.*;
import org.xmlBlaster.util.Base64;

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
public class XmlBlasterAccessJ2ME extends XmlBlasterAccessRawBase {
   
   private Hashtable cookie;
   /**
    * Provides access to xmlBlaster server. 
    * @param applet My environment
    * @see #parseAppletParameter
    */
   public XmlBlasterAccessJ2ME(MIDlet midlet) {
      super(parseMidletParameter(midlet));
   }

   /**
    * Provides access to xmlBlaster server. 
    * @see #parseAppletParameter
    */
   public XmlBlasterAccessJ2ME(Hashtable properties) {
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
   private static Hashtable parseMidletParameter(MIDlet midlet) {
      String deliveredParamKeys = midlet.getAppProperty("deliveredParamKeys"); // a comma seperated list of all param from html page
      Hashtable properties = new Hashtable();
      //log("DEBUG", "Reading HTML PARAM deliveredParamKeys=" + deliveredParamKeys);
      if (deliveredParamKeys != null) {
         /*
         StringTokenizer st = new StringTokenizer(deliveredParamKeys, ",;:");
         while (st.hasMoreTokens()) {
            String key = st.nextToken();
            if (key == null) continue;
            key = key.trim();
            String value = applet.getParameter(key);
            if (value != null && value.length() > 0) {
               properties.put(key, value);
            }
         }
         */
      }
      String loginName = midlet.getAppProperty("xmlBlaster/loginName");
      String passwd = midlet.getAppProperty("xmlBlaster/passwd");
      String xmlBlasterServletUrl = midlet.getAppProperty("xmlBlaster/servletUrl"); //param from html page

      if (xmlBlasterServletUrl != null) properties.put("xmlBlaster/servletUrl", xmlBlasterServletUrl);
      if (midlet.getAppProperty("xmlBlaster/logLevels") != null)
         properties.put("xmlBlaster/logLevels", midlet.getAppProperty("xmlBlaster/logLevels"));
      return properties;
   }

   /**
    * This was previously URLEncoder but j2me has no such support
    */
   /*
   public String encode(String s, String enc) {
      //return Base64.encode(s.getBytes());
      throw new IllegalArgumentException("No URLEncoder is implemeted in XmlBlasterAccessJ2ME.encode()");
   }
   */
   
   public byte[] encodeBase64(byte[] data) {
      return Base64.encode(data).getBytes();
   }

   public byte[] decodeBase64(byte[] data) {
      return Base64.decode(new String(data));
   }

   public I_Connection createConnection(String url) throws Exception {
      return new UrlConnectionMicro(url);
   }

}


