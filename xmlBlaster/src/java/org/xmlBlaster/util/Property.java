/*------------------------------------------------------------------------------
Name:      Property.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Properties for xmlBlaster, see xmlBlaster.property
Version:   $Id: Property.java,v 1.1 2000/01/19 22:21:41 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.io.*;
import java.util.Properties;


/**
 * Properties for xmlBlaster, see $HOME/xmlBlaster.properties
 */
public class Property
{
   private final static String ME = "Property";

   public final static String separator = System.getProperty("file.separator");
   /** The users home directory */
   public final static String userHome = System.getProperty("user.home") + separator;
   /** current directory */
   public       static String currentPath = System.getProperty("user.dir") + separator;
   /** command line (servlets using jrun/jserv property file) */
   public       static String xmlBlasterPath = System.getProperty("XMLBLASTER_HOME");

   private static Properties xmlBlasterProperties = null;


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
      }
      catch (Exception e) {
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
    *         else false
    */
   public static final boolean toBool(String token)
   {
      if (token == null) return false;
      if (token.equalsIgnoreCase("true") ||
         token.equalsIgnoreCase("1") ||
         token.equalsIgnoreCase("ok") ||
         token.equalsIgnoreCase("yes"))
         return true;
      return false;
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
      String path = null;
      File f = null;

      path = userHome + fileName;
      f = new File(path);
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

         path = xmlBlasterPath + fileName;
         f = new File(path);
         if (f.exists()){
            return xmlBlasterPath;
         }
         else
            Log.info(ME, "File '" + fileName + "' is not in directory " + xmlBlasterPath );
      }


      path = currentPath + fileName;
      f = new File(path);
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
      path = guess  + fileName;
      f = new File(path);
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

      String fullName = path + fileName;
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
      Properties props = Property.getProps();
      Log.exit(ME, "Found xmlBlaster.properties:\n" + props);
   }
}
