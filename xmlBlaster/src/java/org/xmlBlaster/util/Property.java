/*------------------------------------------------------------------------------
Name:      Property.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Properties for xmlBlaster, see xmlBlaster.property
Version:   $Id: Property.java,v 1.3 2000/01/20 19:41:41 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.io.*;
import java.util.Properties;
import java.util.Enumeration;


/**
 * Properties for xmlBlaster, see $HOME/xmlBlaster.properties. 
 * <p />
 * The variables following variables may be used and are replaced on occurrence:
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

   private static Properties xmlBlasterProperties = null;


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
    * This loads xmlBlaster.properties file.
    * @return the initialized Properties
    */
   public static final Properties getProps()
   {
      if (xmlBlasterProperties != null) return xmlBlasterProperties;

      String fileName = findFile("xmlBlaster.properties");
      if (fileName == null) {
         Log.error(ME, "Couldn't find file xmlBlaster.properties");
         Log.panic(ME, "Please copy xmlBlaster.properties to your home directory, there is a template in the xmlBlaster distribution");
      }
      File file = new File(fileName);

      try {
         xmlBlasterProperties = new Properties();
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
               xmlBlasterProperties.put(key, replace(value, "$user.dir", currentPath)); 
               continue;
            }
            if (value.indexOf("$user.home") != -1) {
               xmlBlasterProperties.put(key, replace(value, "$user.home", userHome)); 
               continue;
            }
            if (value.indexOf("$XMLBLASTER_HOME") != -1) {
               if (xmlBlasterPath == null) {
                  Log.error(ME, "$XMLBLASTER_HOME is unknown, can't replace it in xmlBlaster.properties file!\n" +
                                "Set it as environment 'java -DXMLBLASTER_HOME=/home/joe ...' or at the beginning of the file.");
                  continue;
               }
               xmlBlasterProperties.put(key, replace(value, "$XMLBLASTER_HOME", xmlBlasterPath)); 
               continue;
            }
         }
      }
      catch (Exception e) {
         Log.error(ME, "Unable to initilize xmlBlaster.properties: " + e);
         Log.panic(ME, "Good bye");
      }

      return xmlBlasterProperties;
   }


   /**
    * Replace exactly one occurrence of 'from' with to 'to'
    */
   private final static String replace(String str, String from, String to)
   {
      int index = str.indexOf(from);
      if (index >= 0) {
         StringBuffer tmp = new StringBuffer("");
         if (index > 0)
            tmp.append(str.substring(0, index));
         tmp.append(to);
         tmp.append(str.substring(index + from.length()));
         return tmp.toString();
      }
      else
         return str;
   }


   /**
    * Parse a string to boolean.
    * <p />
    * @param token for example "false"
    * @return true for one of "true", "yes", "1", "ok"<br />
    *         else false
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
    * 1) In $HOME
    * 2) Im $XMLBLASTER_HOME
    * 3) Local directory
    * 4) Fallback: \xmlBlaster oder /usr/local/xmlBlaster
    *
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
         Log.info(ME, "File '" + fileName + "' is not in directory " + userHome);


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
            Log.info(ME, "File '" + fileName + "' is not in directory " + xmlBlasterPath );
      }


      f = new File(currentPath, fileName);
      if (f.exists()) {
         return currentPath;
      }
      else
         Log.info(ME, "File '" + fileName + "' is not in directory " + currentPath);


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
         Log.error(ME, "File '" + fileName + "' is not in directory " + guess );

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
         Log.error(ME, "File '" + fileName + "' not found");
         return null;
      }

      String fullName = FileUtil.concatPath(path, fileName);
      Log.info(ME, "Using file '" + fullName + "'");
      return fullName;
   }


   /**
    * For testing only
    * <p />
    * java org.xmlBlaster.util.Property
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

      Log.info(ME, "Persistence=" + Property.getProperty("Persistence", false));
      Log.info(ME, "Persistence.Dummy=" + Property.getProperty("Persistence.Dummy", false));
      Log.info(ME, "Persistence.Driver=" + Property.getProperty("Persistence.Driver", "NONE"));
      Log.info(ME, "Persistence.Dummy=" + Property.getProperty("Persistence.Dummy", "NONE"));
      Properties props = Property.getProps();
      Log.info(ME, "All properties: " + props);
      Log.exit(ME, "Found xmlBlaster.properties:\n" + props);
   }
}
