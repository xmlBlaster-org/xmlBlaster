/*------------------------------------------------------------------------------
Name:      XmlBlasterClassLoader.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Creates a new class loader for the pluginmanager.
Version:   $Id: XmlBlasterClassLoader.java,v 1.1 2002/07/13 12:03:18 goetzger Exp $
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;

public class XmlBlasterClassLoader extends URLClassLoader {

   private String ME = "XmlBlasterClassLoader";
   private final String pluginName;
   private String pluginPackage;

   public XmlBlasterClassLoader(URL[] urls, String pluginName) {
      super(urls);
      this.pluginName = pluginName;
      this.pluginPackage = pluginName.substring(0, pluginName.lastIndexOf("."));
      this.ME = "Loader for " + pluginName.substring(pluginName.lastIndexOf('.') + 1);
   } //

   public Class loadClass(String name) throws ClassNotFoundException {
      Log.trace(ME, "name: " + name);

      ClassLoader parent = getClass().getClassLoader();
      if (name.startsWith("java.") ) {
         return parent.loadClass(name);
      }
      if (name.startsWith("org.xmlBlaster") ) {
         if (!name.startsWith(pluginPackage)) {
            return parent.loadClass(name);
         }
      }

      Class clazz = findLoadedClass(name);
      if (clazz != null) {
         return clazz;
      }

      try {
         return findClass(name);
      }
      catch (ClassNotFoundException e) {
         return parent.loadClass(name);
      }

   } // end of loadClass

} // end of class

// end of file
