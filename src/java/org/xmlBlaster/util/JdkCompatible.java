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

} // class JdkCompatible

