/*------------------------------------------------------------------------------
Name:      Property.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Properties for xmlBlaster, see xmlBlaster.property
Version:   $Id: Property.java,v 1.14 2000/05/26 20:46:38 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.io.*;
import java.util.Properties;
import java.util.Enumeration;


/**
 * Properties for xmlBlaster, see $HOME/xmlBlaster.properties.
 * <p />
 * The variables following variables may be used in the properties file and are replaced on occurrence:
 * <ul>
 *    <li>$user.dir =   The current directory </i>
 *    <li>$user.home =  The users home directory</i>
 *    <li>$XMLBLASTER_HOME = The xmlBlaster home directory</i>
 * </ul>
 * A user may specify the XMLBLASTER_HOME directory and use it later as a variable as follows:<br />
 * <pre>   java -DXMLBLASTER_HOME=/home/joe ...</pre><br />
 * or in the xmlBlaster.properties file:<br />
 * <pre>   $XMLBLASTER_HOME=/home/joe</pre>
 * <p />
 * NOTE: user.dir and user.home are without a path separator at the end
 */
public class Property
{
   private final static String ME = "Property";

   public final static String separator = System.getProperty("file.separator");
   /** The users home directory */
   public final static String userHome = System.getProperty("user.home");
   /** current directory */
   public       static String currentPath = System.getProperty("user.dir");
   /** command line (servlets using jrun/jserv property file) */
   public       static String xmlBlasterPath = System.getProperty("XMLBLASTER_HOME", null);
   /** The java home directory, e.g. /opt/jdk1.2.2/jre/lib */
   public final static String javaHome = System.getProperty("java.home");

   private static Properties xmlBlasterProperties = null;
   private static final Properties dummyProperties = new Properties();
   private static boolean veryFirst = true;


   /**
    * Try to find the given key in xmlBlaster.properties
    * @param key the key to look for
    * @param defaultVal the default value to return if key is not found
    * @return The String value for the given key
    */
   public static final String getProperty(String key, String defaultVal)
   {
      return getProps().getProperty(key, defaultVal);
   }


   /**
    * Try to find the given key in xmlBlaster.properties
    * @param key the key to look for
    * @param defaultVal the default value to return if key is not found
    * @return The int value for the given key
    */
   public final static int getProperty(String key, int defaultVal)
   {
      String str = getProps().getProperty(key);
      if (str == null)
         return defaultVal;
      try {
         return Integer.parseInt(str);
      } catch (Exception e) {
         return defaultVal;
      }
   }


   /**
    * Try to find the given key in xmlBlaster.properties
    * @param key the key to look for
    * @param defaultVal the default value to return if key is not found
    * @return The long value for the given key
    */
   public final static long getProperty(String key, long defaultVal)
   {
      String str = getProps().getProperty(key);
      if (str == null)
         return defaultVal;
      try {
         return Long.parseLong(str);
      } catch (Exception e) {
         return defaultVal;
      }
   }


   /**
    * Try to find the given key in xmlBlaster.properties
    * <p />
    * See toBool() for a list of recognized strings.
    * @param key the key to look for
    * @param defaultVal the default value to return if key is not found
    * @return The boolean value for the given key
    */
   public final static boolean getProperty(String key, boolean defaultVal)
   {
      String str = getProps().getProperty(key);
      if (str == null)
         return defaultVal;
      try {
         return toBool(str);
      } catch (Exception e) {
         return defaultVal;
      }
   }


   /**
    * Try to find the given key in xmlBlaster.properties or on command line
    * <p />
    * @param key the parameter key to look for
    * @return true if the property exists
    */
   public final static boolean propertyExists(String key)
   {
      String str = getProps().getProperty(key);
      if (str == null)
         return false;
      return true;
   }


   /**
    * Remove the given property.
    * <p />
    * This method does nothing if the key is not in the property hashtable.
    * @param key the key to remove
    */
   public final static Object removeProperty(String key)
   {
      return getProps().remove(key);
   }


   /**
    * Returns the xmlBlaster.properties properties from the cache.
    * <p />
    * If xmlBlaster.properties is not parsed yet, it will be initialized
    * automatically. Note that you should prefer loadProps() to initialize
    * as you can pass your command line parameters with that method.
    * @return the initialized Properties
    */
   public static final Properties getProps()
   {
      if (veryFirst && xmlBlasterProperties == null)
         loadProps((String[]) null);

      if (xmlBlasterProperties == null)
         return dummyProperties;

      return xmlBlasterProperties;
   }


   /**
    * This loads xmlBlaster.properties file.
    * <p />
    * Use this method only the first time to initialize everything.
    * @args This key/value parameter array is added to the poperties object (see addArgs2Props()).
    * @return the initialized Properties
    */
   public static final Properties loadProps(String[] args)
   {
      veryFirst = false;
      if (xmlBlasterProperties != null && args == null) {
         return xmlBlasterProperties;
      }
      else if (xmlBlasterProperties != null) {
         Log.info(ME, "Reloading Property file ... args=" + args);
      }

      // set default, the Log class calls getProperty, and this class uses Log
      // this leads for a missing xmlBlaster.properties to a never ending loop
      xmlBlasterProperties = new Properties();

      String fileName = findFile("xmlBlaster.properties");
      if (fileName == null) {
         // Log.error(ME, "Couldn't find file xmlBlaster.properties");
         Log.warning(ME, "Please copy xmlBlaster.properties to your home directory, there is a template in the xmlBlaster distribution. We continue with default settings!");
         return xmlBlasterProperties;
      }
      File file = new File(fileName);

      try {
         FileInputStream fis = new FileInputStream(file);
         xmlBlasterProperties.load(fis);
         fis.close();

         // a user may specify the XMLBLASTER_HOME directory and use it later as a variable
         String tmp = (String)xmlBlasterProperties.getProperty("$XMLBLASTER_HOME", null);
         if (tmp != null) {
            xmlBlasterPath = tmp;
         }

         // replace $user.dir and $user.home
         for (Enumeration e = xmlBlasterProperties.keys(); e.hasMoreElements() ;) {
            String key = (String)e.nextElement();
            String value = (String)xmlBlasterProperties.get(key);
            if (value.indexOf("$user.dir") != -1) {
               xmlBlasterProperties.put(key, StringHelper.replace(value, "$user.dir", currentPath));
               continue;
            }
            if (value.indexOf("$user.home") != -1) {
               xmlBlasterProperties.put(key, StringHelper.replace(value, "$user.home", userHome));
               continue;
            }
            if (value.indexOf("$XMLBLASTER_HOME") != -1) {
               if (xmlBlasterPath == null) {
                  Log.error(ME, "$XMLBLASTER_HOME is unknown, can't replace it in xmlBlaster.properties file!\n" +
                                "Set it as environment 'java -DXMLBLASTER_HOME=/home/joe ...' or at the beginning of the file.");
                  continue;
               }
               xmlBlasterProperties.put(key, StringHelper.replace(value, "$XMLBLASTER_HOME", xmlBlasterPath));
               continue;
            }
         }

         addArgs2Props(xmlBlasterProperties, args);

         // hack: Who is first: the Log output in this class or the Log which needs Properties?
         Log.initialize();
      }
      catch (Exception e) {
         e.printStackTrace();
         Log.error(ME, "Unable to initilize xmlBlaster.properties: " + e);
         Log.panic(ME, "Good bye");
      }

      return xmlBlasterProperties;
   }


   /**
    * Parse a string to boolean.
    * <p />
    * @param token for example "false"
    * @return true for one of "true", "yes", "1", "ok"<br />
    *         false for "false", "0", "no"
    * @exception if none of the above strings
    */
   public static final boolean toBool(String token) throws Exception
   {
      if (token == null)
         throw new Exception("Can't parse <null> to true or false");

      if (token.equalsIgnoreCase("true") ||
         token.equalsIgnoreCase("1") ||
         token.equalsIgnoreCase("ok") ||
         token.equalsIgnoreCase("yes"))
         return true;

      if (token.equalsIgnoreCase("false") ||
         token.equalsIgnoreCase("0") ||
         token.equalsIgnoreCase("no"))
         return false;

      throw new Exception("Can't parse <" + token + "> to true or false");
   }


   /**
    * Look for properties file. 
    * <p />
    * 1) In user.home - $HOME<br />
    * 2) In $XMLBLASTER_HOME<br />
    * 3) Local directory user.dir<br />
    * 4) In java.home directory, e.g. /opt/jdk1.2.2/jre/lib<br />
    *    You may use this path for Servlets, demons etc.<br />
    * 5) Fallback: \xmlBlaster oder /usr/local/xmlBlaster
    * <p />
    * @param fileName e.g. "xmlBlaster.properties"
    * @return The path to file, e.g. "\xmlBlaster\"
    */
   public final static String findPath(String fileName)
   {
      File f = null;

      f = new File(userHome, fileName);
      if (f.exists()) {
         return userHome;
      }
      else
         Log.info(ME, "File '" + fileName + "' is not in user.home directory " + userHome);


      if (xmlBlasterPath == null) {
         Log.info(ME, "File '" + fileName + "' not found in XMLBLASTER_HOME, 'java -DXMLBLASTER_HOME=...' is not set ...");
      }
      else {
         if (!xmlBlasterPath.endsWith(separator))
            xmlBlasterPath += separator;

         f = new File(xmlBlasterPath, fileName);
         if (f.exists()){
            return xmlBlasterPath;
         }
         else
            Log.info(ME, "File '" + fileName + "' is not in directory XMLBLASTER_HOME=" + xmlBlasterPath );
      }


      f = new File(currentPath, fileName);
      if (f.exists()) {
         return currentPath;
      }
      else
         Log.info(ME, "File '" + fileName + "' is not in current directory " + currentPath);


      f = new File(javaHome, fileName);
      if (f.exists()) {
         return javaHome;
      }
      else
         Log.info(ME, "File '" + fileName + "' is not in java.home directory " + javaHome);


      String guess;
      if (separator.equals("/"))
         guess = "/usr/local/xmlBlaster/";
      else
         guess = "\\xmlBlaster\\";
      f = new File(guess, fileName);
      if (f.exists()) {
         return guess;
      }
      else
         Log.info(ME, "File '" + fileName + "' is not in directory " + guess );

      return null;
   }


   /**
    * Find xmlBlaster.properties.
    * <p />
    * See findPath() for search - logic
    *
    * @param fileName e.g. "xmlBlaster.properties"
    * @return The path to file, e.g. "\xmlBlaster\"
   */
   public final static String findFile(String fileName)
   {
      String path = findPath(fileName);
      if (path == null) {
         Log.warning(ME, "File '" + fileName + "' not found");
         return null;
      }

      String fullName = FileUtil.concatPath(path, fileName);
      Log.info(ME, "Using file '" + fullName + "'");
      return fullName;
   }


   /**
    * @see #addArgs2Props(Properties, args)
    */
   public static void addArgs2Props(String[] args)
   {
      addArgs2Props(getProps(), args);
   }


   /**
    * Add key/values, for example from startup command line args to
    * the property variable.
    * <p />
    * Args parameters are stronger and overwrite the property file variables.
    * <p />
    * The arg key must have a leading - or + (as usual on command line).<br />
    * The leading - are stripped (to match the property variable)
    * args must be a tuple (key / value pairs) or
    * if args has no value, the value will be set to "true" (is a flag)
    * <p />
    * Example:
    * <pre>
    *   jaco org.xmlBlaster.Main  -isCool  -iorPort 3400
    * </pre>
    * This would overwrite a xmlBlaster.properties variable "iorPort" (if there was one)
    * and set the variable isCool to "true"
    */
   public static void addArgs2Props(Properties props, String[] args)
   {
      if (args == null) return;

      for (int ii=0; ii<args.length; ii++) {
         String arg = args[ii];
         if (arg.startsWith("-") || arg.startsWith("+")) { // only parameters starting with "-" or "+" are recognized
            String key = arg;
            if (arg.startsWith("-")) key = arg.substring(1); // strip "-", but not "+"
            String value = "true";
            if ((ii+1) < args.length) {
               String arg2 = args[ii+1];
               // Problem: How can we specify for example a negative number? "-number -200"
               if (!arg2.startsWith("-") && !arg2.startsWith("+")) { // parameter with a given value?
                  value = arg2;
                  ii++;
               }
            }
            props.put(key, value);
            // Log.info(ME, "Setting command line argument " + key + " with value " + value);
         }
         else {
            Log.warning(ME, "Ignoring unknown argument <" + arg + ">");
         }
      }
   }


   static String toXml()
   {
      StringBuffer buf = new StringBuffer();
      buf.append("<Property>\n");
      for (Enumeration e = getProps().propertyNames(); e.hasMoreElements() ;) {
         String key = (String)e.nextElement();
         buf.append("   <").append(key).append(">");
         buf.append(getProps().getProperty(key));
         buf.append("</").append(key).append(">\n");
      }
      buf.append("</Property>\n");
      return buf.toString();
   }


   /**
    * For testing only
    * <p />
    * java org.xmlBlaster.util.Property +calls -isNice -Persistence.Driver myDriver -isCool
    */
   public static void main(String args[])
   {
      String ME = "Property";
      try {
         Log.info(ME, "yes=" + Property.toBool("yes"));
         Log.info(ME, "no=" + Property.toBool("no"));
         Log.info(ME, "aaa=" + Property.toBool("aaa"));
      }
      catch (Exception e) {
         Log.info(ME, "OK, aaa=ERROR");
      }

      Property.loadProps(args);  // initialize

      Log.info(ME, "Persistence=" + Property.getProperty("Persistence", false));
      Log.info(ME, "Persistence.Dummy=" + Property.getProperty("Persistence.Dummy", false));
      Log.info(ME, "Persistence.Driver=" + Property.getProperty("Persistence.Driver", "NONE"));
      Log.info(ME, "Persistence.Dummy=" + Property.getProperty("Persistence.Dummy", "NONE"));
      Properties props = Property.getProps();
      Log.info(ME, "All properties:\n" + props);
      Log.info(ME, "All properties as XML:\n" + Property.toXml());
      Log.exit(ME, "Found xmlBlaster.properties:\n" + props);
   }
}
