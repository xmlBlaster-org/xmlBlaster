/*------------------------------------------------------------------------------
Name:      JdkCompatible.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to create/start/stop a xmlBlaster server in a thread
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.util.Properties;


/**
 * Helper to use only JDK 1.1 supported methods.
 * For xmlBlaster CLIENT side java classes only, since xmlBlaster server only
 * runs withc JDK 1.2 or newer.
 */
public class JdkCompatible
{
    private static final float majorJavaVersion = getMajorJavaVersion();
    public static final float DEFAULT_JAVA_VERSION = 1.3f;

   /**
    * System.setProperty(String, String); is since JDK 1.2
    * This method supports JDK 1.1 as well
    * @deprecated since JDK 1.2
    */
   public static void setSystemProperty(String key, String value)
   {
      try {
         System.setProperty(key, value); // Since JDK 1.2
      } catch(NoSuchMethodError e) {
         Properties prop = System.getProperties();  // JDK 1.1 workaround
         prop.put(key, value);
         System.setProperties(prop);
      }
   }

   /**
     * Parses the java version system property to determine the major java version,
     * ie 1.x
     *
     * @return A float of the form 1.x
     */
    private static final float getMajorJavaVersion() {
        try {
            return Float.parseFloat(System.getProperty("java.specification.version"));
        } catch ( NumberFormatException e ){
            // Some JVMs may not conform to the x.y.z java.version format
            return DEFAULT_JAVA_VERSION;
        }
    }

    public static boolean is14() {
        return majorJavaVersion >= 1.4f;
    }

    public static boolean is15() {
        return majorJavaVersion >= 1.5f;
    }

    public static boolean is16() {
        return majorJavaVersion >= 1.6f;
    }

    public static boolean isSun() {
        return System.getProperty("java.vm.vendor").indexOf("Sun") != -1;
    }

    public static boolean isApple() {
        return System.getProperty("java.vm.vendor").indexOf("Apple") != -1;
    }

    public static boolean isHPUX() {
        return System.getProperty("java.vm.vendor").indexOf("Hewlett-Packard Company") != -1;
    }

    public static boolean isIBM() {
    	return System.getProperty("java.vm.vendor").indexOf("IBM") != -1;
    }

    public static boolean isBlackdown() {
        return System.getProperty("java.vm.vendor").indexOf("Blackdown") != -1;
    }
    
    public static boolean isBEAWithUnsafeSupport() {
        // This property should be "BEA Systems, Inc."
        return System.getProperty("java.vm.vendor").indexOf("BEA") != -1;
    }
} // class JdkCompatible

