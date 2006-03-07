/*------------------------------------------------------------------------------
Name:      XmlBlasterClassLoader.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Creates a new class loader for the XmlBlaster Serverthread.
Author:    konrad.krafft@doubleslash.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.classloader;

import java.net.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;

/**
 * This class loader changes the loading precedence of the JVM
 * to prefer locally found classes and only if not found delegate
 * to the JVM class loader
 */
public class XmlBlasterClassLoader extends URLClassLoader {

   private String ME = "XmlBlasterClassLoader";
   private static Logger log = Logger.getLogger(XmlBlasterClassLoader.class.getName());
   private final boolean useXmlBlasterClassloader;

   public XmlBlasterClassLoader(Global glob, URL[] urls) {
      super(urls);

      useXmlBlasterClassloader = glob.getProperty().get("classloader.xmlBlaster", false);
   }

   public Class loadClass(String name) throws ClassNotFoundException {
      //debugState(name);
      ClassLoader parent = getClass().getClassLoader();

      if (!useXmlBlasterClassloader) {
         return parent.loadClass(name);
      }

      if (name.startsWith("java.")) {
         if (log.isLoggable(Level.FINE)) log.fine("Using default JVM class loader for java class " + name);
         return parent.loadClass(name);
      }

      if (name.startsWith("org.xmlBlaster.I_Main") || name.startsWith("org.omg") || name.startsWith("org.w3c")) {
         if (log.isLoggable(Level.FINE)) log.fine("Using default JVM class loader for " + name);
         return parent.loadClass(name);
      }
         
      Class clazz = findLoadedClass(name);
      if (clazz != null) {
            if (log.isLoggable(Level.FINE)) log.fine("Using specific class loader from cache for " + name);
            return clazz;
      }

      try {
         clazz = findClass(name);
         //resolveClass(clazz);
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

   public void appendURL(URL url) {
      addURL(url);
   }

   /**
    * Helper for debugging classpath.
    */
   private void debugState(String name) {
     log.fine("Looking up class: " + name);
     log.fine("Local path is: " + getURLPath());
     
   }

   String getURLPath() {
      URL[] urls = getURLs();
      StringBuffer buff = new StringBuffer();
      if (urls != null && urls.length > 0) {
         for (int i = 0;i<urls.length;i++) {
            buff.append(urls[i].toString()).append(";");
         }
      }
      return buff.toString();

   }
}
