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
import org.xmlBlaster.util.XmlBlasterException;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;

/**
 * This class loader changes the loading precedence of the JVM
 * to prefer locally found classes and only if not found delegate
 * to the JVM class loader
 */
public class XmlBlasterClassLoader extends URLClassLoader {

   private String ME = "XmlBlasterClassLoader";
   private final LogChannel log;

   public XmlBlasterClassLoader(Global glob, URL[] urls) {
      super(urls);
      log = glob.getLog("classloader");
   }

   public Class loadClass(String name) throws ClassNotFoundException {
      ClassLoader parent = getClass().getClassLoader();
      if ( name.startsWith("java.") ) {
         if (log.TRACE) log.trace(ME, "Using default JVM class loader for java.* and javax.* class " + name);
         return parent.loadClass(name);
      }

      Class clazz = findLoadedClass(name);
      if (clazz != null) {
          if (log.TRACE) log.trace(ME, "Using specific class loader from cache for " + name);
          return clazz;
      }

      try {
         clazz = findClass(name);
         if (log.TRACE) log.trace(ME, "Using specific class loader for " + name);
         return clazz;
      }
      catch (ClassNotFoundException e) {
          if (log.TRACE) log.trace(ME, "Using default JVM class loader for " + name + " as not found in specific class loader");
          return parent.loadClass(name);
      }

   } // end of loadClass
}
