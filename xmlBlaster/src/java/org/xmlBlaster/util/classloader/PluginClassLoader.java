/*------------------------------------------------------------------------------
Name:      PluginClassLoader.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Creates a new class loader for the pluginmanager.
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.classloader;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.XmlBlasterException;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;

/**
 * This class loader changes the loading precedence of the JVM
 * to prefer locally found classes and only if not found delegate
 * to the JVM class loader
 */
public class PluginClassLoader extends URLClassLoader {

   private String ME = "PluginClassLoader";
   private final String pluginName;
   private String pluginPackage;
   private final LogChannel log;

   public PluginClassLoader(Global glob, URL[] urls, PluginInfo pluginInfo) {
      super(urls);
      log = glob.getLog("classloader");
      this.pluginName = pluginInfo.getClassName();
      this.pluginPackage = pluginName.substring(0, pluginName.lastIndexOf("."));
      this.ME = "PluginClassLoader-" + pluginName.substring(pluginName.lastIndexOf('.') + 1);
   }

   public Class loadClass(String name) throws ClassNotFoundException {
      ClassLoader parent = getClass().getClassLoader();
      if (name.startsWith("java.") ) {
         if (log.TRACE) log.trace(ME, "Using default JVM class loader for java class " + name);
         return parent.loadClass(name);
      }
      if (name.startsWith("org.xmlBlaster") || name.startsWith("org.jutils") || name.startsWith("org.omg")) {
         if (!name.startsWith(pluginPackage)) {
            if (log.TRACE) log.trace(ME, "Using default JVM class loader for " + name);
            return parent.loadClass(name);
         }
      }
         
      Class clazz = findLoadedClass(name);
      if (clazz != null) {
          if (log.TRACE) log.trace(ME, "Using specific class loader from cache for " + name);
          return clazz;
      }

      try {
         clazz = findClass(name);
         resolveClass(clazz);
         if (log.TRACE) log.trace(ME, "Using specific class loader for " + name);
         return clazz;
      }
      catch (ClassNotFoundException e) {
          if (log.TRACE) log.trace(ME, "Using default JVM class loader for " + name + " as not found in specific class loader");
          clazz = parent.loadClass(name);
          resolveClass(clazz);
          return clazz;
      }

   } // end of loadClass
}
