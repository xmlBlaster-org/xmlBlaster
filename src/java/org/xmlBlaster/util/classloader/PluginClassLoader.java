/*------------------------------------------------------------------------------
Name:      PluginClassLoader.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Creates a new class loader for the pluginmanager.
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.classloader;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.PluginInfo;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * This class loader changes the loading precedence of the JVM
 * to prefer locally found classes and only if not found delegate
 * to the JVM class loader
 */
public class PluginClassLoader extends URLClassLoader {

   private final String pluginName;
   private String pluginPackage;
   private static Logger log = Logger.getLogger(PluginClassLoader.class.getName());

   public PluginClassLoader(Global glob, URL[] urls, PluginInfo pluginInfo) {
      super(urls);

      this.pluginName = pluginInfo.getClassName();
      int pos = pluginName.lastIndexOf(".");
      if (pos >= 0) {
         this.pluginPackage = pluginName.substring(0, pos);
      }
      else
         this.pluginPackage = "";
   }

   public Class loadClass(String name) throws ClassNotFoundException {
      ClassLoader parent = getClass().getClassLoader();
      if (name.startsWith("java.") ) {
         if (log.isLoggable(Level.FINE)) log.fine("Using default JVM class loader for java class " + name);
         return parent.loadClass(name);
      }
      if (name.startsWith("org.xmlBlaster") || name.startsWith("org.jutils") || name.startsWith("org.omg")) {
         if (!name.startsWith(pluginPackage)) { // matches for empty packages "" as well
            if (log.isLoggable(Level.FINE)) log.fine("Using default JVM class loader for " + name);
            return parent.loadClass(name);
         }
      }
         
      Class clazz = findLoadedClass(name);
      if (clazz != null) {
          if (log.isLoggable(Level.FINE)) log.fine("Using specific class loader from cache for " + name);
          return clazz;
      }

      try {
         clazz = findClass(name);
         resolveClass(clazz);
         if (log.isLoggable(Level.FINE)) log.fine("Using specific class loader for " + name);
         return clazz;
      }
      catch (ClassNotFoundException e) {
          if (log.isLoggable(Level.FINE)) log.fine("Using default JVM class loader for " + name + " as not found in specific class loader");
          clazz = parent.loadClass(name);
          resolveClass(clazz);
          return clazz;
      }

   } // end of loadClass
}
