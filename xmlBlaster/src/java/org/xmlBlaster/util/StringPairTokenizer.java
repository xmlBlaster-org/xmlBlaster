/*------------------------------------------------------------------------------
Name:      StringPairTokenizer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.xmlBlaster.util.qos.ClientProperty;

/**
 * StringPairTokenizer is a utility class used to parse a string giving
 * back a map containing pairs of key/value strings.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class StringPairTokenizer {

   /**
    * @param rawString e.g. "org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100"
    * @param outerToken is for example ";" or ","
    * @param innterToken is for example "=" or " "
    * If a value is missing then a null object as value.
    * the map returns pairs 'String,ClientProperty' if wantClientProperties is true,
    * otherwise it returns 'String,String' pairs. 
    * @param 
    */
   private static Map parseStringProperties(Global glob, String rawString, String outerToken, String innerToken, boolean wantClientProperties) {
      if (rawString==null) throw new IllegalArgumentException("SessionInfo.parsePropertyValue(null)");
      Map ret = new HashMap();
      StringTokenizer st = new StringTokenizer(rawString, outerToken);
      while(st.hasMoreTokens()) {
         String tok = (String)st.nextToken().trim();
         int pos = tok.indexOf(innerToken);
         if (pos < 0) {
            ret.put(tok.trim(), null);
         }
         else {
            String key = tok.substring(0,pos).trim();
            String value = tok.substring(pos+1).trim(); 
            if (wantClientProperties) 
               ret.put(key, new ClientProperty(key, null, null, value));
            else
               ret.put(key, value);
         }
      }
      return ret;
   }
   
     
   /**
    * @param rawString e.g. "org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100"
    * @param outerToken is for example ";" or ","
    * @param innterToken is for example "=" or " "
    * If a value is missing then a null object as value.
    * the map returns pairs 'String,ClientProperty'.
    */
   public static Map parseToStringClientPropertyPairs(Global glob, String rawString, String outerToken, String innerToken) {
      return parseStringProperties(glob, rawString, outerToken, innerToken, true);
   }
   
   /**
    * @param rawString e.g. "org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100"
    * @param outerToken is for example ";" or ","
    * @param innterToken is for example "=" or " "
    * If a value is missing then a null object as value.
    * the map returns pairs 'String,String'.
    */
   public static Map parseToStringStringPairs(Global glob, String rawString, String outerToken, String innerToken) {
      return parseStringProperties(glob, rawString, outerToken, innerToken, false);
   }
}
