/*------------------------------------------------------------------------------
Name:      Global.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Properties for xmlBlaster, using org.jutils
Version:   $Id: Global.java,v 1.5 2002/04/29 09:43:00 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.jutils.JUtilsException;
import org.jutils.init.Property;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.protocol.I_CallbackDriver;

import java.util.Properties;

import java.applet.Applet;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;


/**
 * Global variables to avoid singleton. 
 */
public class Global
{
   private final static String ME = "Global";

   private String[] args;
   protected XmlBlasterProperty property = new XmlBlasterProperty();
   protected Log log = new Log();

   private Map nativeCallbackDriverMap = Collections.synchronizedMap(new HashMap());


   public Global()
   {
      this.args = new String[0];
   }

   public Global(String[] args)
   {
      init(args);
   }

   public final XmlBlasterProperty getProperty()
   {
      return property;
   }

   public final Log getLog()
   {
      return log;
   }

   /**
    * The command line arguments. 
    * @return the arguments, is never null
    */
   public final String[] getArgs()
   {
      return this.args;
   }

   /**
    * @return 1 Show usage, 0 OK, -1 error
    */
   public int init(String[] args)
   {
      this.args = args;
      if (this.args == null)
         this.args = new String[0];
      try {
         // XmlBlasterProperty.addArgs2Props(this.args); // enforce that the args are added to the xmlBlaster.properties hash table
         boolean showUsage = XmlBlasterProperty.init(this.args);  // initialize
         if (showUsage) return 1;
         return 0;
      } catch (JUtilsException e) {
         System.err.println(ME + " ERROR: " + e.toString()); // Log probably not initialized yet.
         return -1;
      }
   }

   /**
    * The key is the protocol and the address to access the callback instance. 
    *
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getAddress()'
    * @return The instance of the protocol callback driver or null if not known
    */
   public final I_CallbackDriver getNativeCallbackDriver(String key)
   {
      return (I_CallbackDriver)nativeCallbackDriverMap.get(key);
   }

   /**
    * The key is the protocol and the address to access the callback instance. 
    *
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getAddress()'
    * @param The instance of the protocol callback driver
    */
   public final void addNativeCallbackDriver(String key, I_CallbackDriver driver)
   {
      nativeCallbackDriverMap.put(key, driver);
   }

   /**
    * The key is the protocol and the address to access the callback instance. 
    *
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getAddress()'
    * @param The instance of the protocol callback driver
    */
   public final void removeNativeCallbackDriver(String key)
   {
      nativeCallbackDriverMap.remove(key);
   }

   /**
    * For testing only
    * <p />
    * java org.xmlBlaster.util.Global -Persistence.Dummy true -info true
    */
   public static void main(String args[])
   {
      String ME = "Global";
      Global glob = new Global(args);
      Log.info(ME, "Persistence.Dummy=" + glob.getProperty().get("Persistence.Dummy", false));
   }
}
