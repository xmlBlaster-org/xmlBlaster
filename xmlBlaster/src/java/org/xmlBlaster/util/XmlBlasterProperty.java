/*------------------------------------------------------------------------------
Name:      XmlBlasterProperty.java
Project:   jutils.org
Copyright: jutils.org, see jutils-LICENSE file
Comment:   Properties for jutils, see jutils.property
Version:   $Id: XmlBlasterProperty.java,v 1.1 2000/06/19 20:35:12 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.jutils.JUtilsException;
import org.jutils.init.Property;
import org.jutils.log.Log;

import java.util.Properties;


/**
 * xmlBlaster.properties and command line handling.
 * <p />
 * @see org.jutils.init.Property
 */
public class XmlBlasterProperty
{
   private final static String ME = "XmlBlasterProperty";

   private static Property property = null;


   /**
    * Try to find the given key in xmlBlaster.properties or from command line.
    * @param key the key to look for
    * @param defaultVal the default value to return if key is not found
    * @return The String value for the given key
    * @see org.jutils.init.Property
    */
   public static final String get(String key, String defaultVal)
   {
      return getProps().get(key, defaultVal);
   }


   /**
    * Try to find the given key in xmlBlaster.properties or from command line.
    * @param key the key to look for
    * @param defaultVal the default value to return if key is not found
    * @return The int value for the given key
    * @see org.jutils.init.Property
    */
   public final static int get(String key, int defaultVal)
   {
      return getProps().get(key, defaultVal);
   }


   /**
    * Try to find the given key in xmlBlaster.properties or from command line.
    * @param key the key to look for
    * @param defaultVal the default value to return if key is not found
    * @return The long value for the given key
    */
   public final static long get(String key, long defaultVal)
   {
      return getProps().get(key, defaultVal);
   }


   /**
    * Try to find the given key in xmlBlaster.properties or from command line.
    * <p />
    * See toBool() for a list of recognized strings.
    * @param key the key to look for
    * @param defaultVal the default value to return if key is not found
    * @return The boolean value for the given key
    */
   public final static boolean get(String key, boolean defaultVal)
   {
      return getProps().get(key, defaultVal);
   }


   /**
    * Try to find the given key in xmlBlaster.properties or on command line
    * <p />
    * @param key the parameter key to look for
    * @return true if the property exists
    */
   public final static boolean exists(String key)
   {
      return getProps().propertyExists(key);
   }


   /**
    * Remove the given property.
    * <p />
    * This method does nothing if the key is not in the property hashtable.
    * @param key the key to remove
    */
   public final static Object remove(String key)
   {
      return getProps().removeProperty(key);
   }


   public final static String toXml() {
      return getProps().toXml();
   }


   /**
    * Access the internal Property handle, e.g. for Log.setLogLevel()
    */
   public final static Property getProperty()
   {
      return getProps();
   }


   private final static Property getProps()
   {
      if (property == null) {
         try {
            property = new Property("xmlBlaster.properties", true, null, true);  // initialize without args!
         }
         catch (JUtilsException e) {
            System.err.println(ME + ": " + e.toString());
            try {
               property = new Property(null, true, null, true);  // initialize without args and properties file!
            }
            catch (JUtilsException e2) {
               System.err.println(ME + ": " + e2.toString());
               Log.panic(ME, e2.toString());
            }
         }
      }
      return property;
   }


   public final static void init(String[] args) throws JUtilsException
   {
      property = new Property("xmlBlaster.properties", true, args, true);  // initialize
      // System.out.println(toXml());
   }

   public final static void addArgs2Props(String[] args) throws JUtilsException
   {
      getProps().addArgs2Props(args);
   }

   /*
    * Low level access.
    */
    /*
   public final static Properties getProperties()
   {
      return property.getProperties();
   }
      */

   /**
    * For testing only
    * <p />
    * java org.xmlBlaster.util.XmlBlasterProperty -isNice true -Persistence.Driver myDriver -isCool yes
    */
   public static void main(String args[])
   {
      String ME = "XmlBlasterProperty";

      try {
         XmlBlasterProperty.init(args);  // initialize
      } catch (JUtilsException e) {
         Log.panic(ME, e.toString());
      }

      System.out.println("Persistence=" + XmlBlasterProperty.get("Persistence", false));
      System.out.println("Persistence.Dummy=" + XmlBlasterProperty.get("Persistence.Dummy", false));
      System.out.println("Persistence.Driver=" + XmlBlasterProperty.get("Persistence.Driver", "NONE"));
      System.out.println("Persistence.Dummy=" + XmlBlasterProperty.get("Persistence.Dummy", "NONE"));
      /*
      Properties props = XmlBlasterProperty.getProperties();
      System.out.println("All properties:\n" + props);
      */
      System.out.println("All properties as XML:\n" + XmlBlasterProperty.toXml());
   }
}
