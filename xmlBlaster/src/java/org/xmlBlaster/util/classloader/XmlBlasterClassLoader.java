/*------------------------------------------------------------------------------
Name:      XmlBlasterClassLoader.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Creates a new class loader for the XmlBlaster Serverthread.
Author:    konrad.krafft@doubleslash.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.classloader;

import java.net.*;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

/**
 * This class loader changes the loading precedence of the JVM
 * to prefer locally found classes and only if not found delegate
 * to the JVM class loader
 */
public class XmlBlasterClassLoader extends URLClassLoader {

   private String ME = "XmlBlasterClassLoader";
   private final LogChannel log;
   private final boolean useXmlBlasterClassloader;

   public XmlBlasterClassLoader(URL[] urls) {
      super(urls);
      log = Global.instance().getLog("classloader");
      useXmlBlasterClassloader = Global.instance().getProperty().get("classloader.xmlBlaster", false);
   }

   public Class loadClass(String name) throws ClassNotFoundException {

      ClassLoader parent = getClass().getClassLoader();

      if (!useXmlBlasterClassloader) {
         return parent.loadClass(name);
      }

      if (name.startsWith("java.")) {
         if (log.TRACE) log.trace(ME, "Using default JVM class loader for java class " + name);
         return parent.loadClass(name);
      }

      if (name.startsWith("org.xmlBlaster.I_Main") || name.startsWith("org.omg") || name.startsWith("org.w3c")) {
         if (log.TRACE) log.trace(ME, "Using default JVM class loader for " + name);
         return parent.loadClass(name);
      }
      /*
      if (name.startsWith("org.xmlBlaster.I_Main") || name.startsWith("org.jutils") ||
            name.startsWith("org.xmlBlaster.util.Global")) {
         if (log.TRACE) log.trace(ME, "Using default JVM class loader for " + name);
         return parent.loadClass(name);
      }
      */
         
      Class clazz = findLoadedClass(name);
      if (clazz != null) {
            if (log.TRACE) log.trace(ME, "Using specific class loader from cache for " + name);
            return clazz;
      }

      try {
         clazz = findClass(name);
         //resolveClass(clazz);
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
