/*------------------------------------------------------------------------------
Name:      ClassLoaderFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Creates a new class loader for the pluginmanager.
Version:   $Id: ClassLoaderFactory.java,v 1.6 2002/08/23 21:34:45 ruff Exp $
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;

import org.jutils.text.StringHelper;
import java.io.File;
import java.util.ArrayList;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;

public class ClassLoaderFactory {
   public final String ME;
   private final Global glob;
   private final LogChannel log;
   private ArrayList classPath = null; // array containig all URL for the new classpath
   private int instanceCounter = 0;

   /**
    * We are a singleton in respect to a Global instance.
    */
   ClassLoaderFactory (Global glob) {
     ++instanceCounter;
     this.ME = "ClassLoaderFactory-" + instanceCounter;
     this.glob = glob;
     this.log = glob.getLog("classloader");
     if (log.CALL) log.call(ME, "ClassLoaderFactory constructor #" + instanceCounter);
   }

   /**
    * Creates and returns a new URL class loader based on the callers class loader and the
    * callers related additional classes which may exist in a specified path.
    * <br />
    * Example:
    *
    * Assuming a classpath like:
    * <pre>
    *   /home/goetzger/java/xmlBlaster/lib/jacorb.jar
    *   /home/goetzger/java/xmlBlaster/lib/idl.jar
    *   /home/goetzger/java/xmlBlaster/demo
    *   /home/goetzger/java/xmlBlaster/classes
    *   /home/goetzger/java/xmlBlaster/src/java
    *   /home/goetzger/java/xmlBlaster/lib/testsuite.jar
    *   /home/goetzger/java/xmlBlaster/lib/demo.jar
    *   /home/goetzger/java/xmlBlaster/lib/xmlBlaster.jar
    *   /home/goetzger/java/xmlBlaster/lib/batik/js.jar
    * </pre>
    * And assuming a callers class name <code>org.xmlBlaster.util.ClassLoaderFactory</code>
    * and provided that there is a path like
    * <code>/home/goetzger/java/xmlBlaster/lib/org/xmlBlaster/util/ClassLoaderFactory</code>
    * the new classpath may look like:
    * <pre>
    *    /home/goetzger/java/xmlBlaster/lib/org/xmlBlaster/util/ClassLoaderFactory/openorb-1.2.0.jar
    * </pre>
    *
    * @param caller Type of the calling class
    * @param plugin Name of the plugin to be loaded and for which the classpath have to be extended.
    * @return a XmlBlasterClassLoader which contains the URL of the parent class loader.
    *           and the URL related to the callers class.
    * @exception XmlBlasterException if the array of URLs can not be formed.
    */
   public XmlBlasterClassLoader getXmlBlasterClassLoader(Object caller, String plugin) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering getXmlBlasterClassLoader for plugin=" + plugin);
      String basePath = null;
      classPath = new ArrayList(); // new array containing all String URL for the new classpath

      if (log.TRACE) log.trace(ME, "caller: '" + caller.getClass().getName() + "' pluginName: '" + plugin + "'");

      // calling for object related base class path
      basePath = getBasePath(caller, plugin);
      if (log.TRACE) log.trace(ME, "Using base path '" + basePath + "' to scan for specific jar files ...");

      // scanning the basePath (and no deeper dirs!) for jars:
      if (basePath != null) {
         File baseDir = new File(basePath);

         if ( !baseDir.exists() || !baseDir.canRead() )
            return ( new XmlBlasterClassLoader(new URL[0], plugin ) );

         String list[] = baseDir.list(); // getting content

         for(int ii = 0; ii < list.length; ii++) {
            String filename = list[ii].toLowerCase();
            if (!filename.endsWith(".jar"))
               continue;
            // add it if it's a jar
            File file = new File(basePath, list[ii]);
            if (!file.isDirectory()) {
               classPath.add(file.toString());
               //if (log.TRACE) log.trace(ME, "Adding: '" + file.getAbsolutePath() + "'");
            }
         }
      }

      if (log.TRACE) log.trace(ME, "Found " + classPath.size() + " jar files in '" + basePath + "'");
      return new XmlBlasterClassLoader( stringToUrl(classPath), plugin );
   }

   /**
    * Retrievs the base path for the object related classpath.
    * Taking the base path from the line in the environment classpath
    * which contains the xmlBlaster.jar first!
    *
    * Adding the name of the calling class as path to the basepath.
    *
    * @param caller Type of the calling class
    * @return The base path for the caller specific additional classes.
    */
   private String getBasePath(Object caller, String plugin) {
      if (log.CALL) log.call(ME, "Entering getBasePath");

      URL callersURL[] = null; // all URLs of the callers classpath
      String basePath = ""; // i.e. '/home/developer/java/xmlBlaster/lib/'
      String callerClassName = null; // i.e. 'org.xmlBlaster.util.ClassLoaderFactory'

      String classResource = which(caller, plugin);
      if (log.TRACE) log.trace(ME, "plugin '" + plugin + "' has resource path " + classResource );

      // occures at the beginning of the return String of the getClass().toString() - call
      // i.e. for this class: className: 'class org.xmlBlaster.util.ClassLoaderFactory'
      String classType = "class ";

      if (plugin.equals("")) {
         callerClassName = caller.getClass().toString();
         callerClassName = callerClassName.substring(classType.length(), callerClassName.length());
      } else {
         callerClassName = plugin;
      }

      callerClassName = StringHelper.replaceAll(callerClassName, ".", "/"); // replace '.' by fileSeperator (windows want "/" as well)
      //callerClassName = callerClassName.replaceAll("\\.", fileSeparator); // since JDK 1.4 :-(

      if(classResource.indexOf('!') == -1) {
         // Determine the BasePath from classes
         // log.warn(ME, "Class not loaded from jar, don't know how to determine basePath");
         basePath = classResource.substring(0, classResource.lastIndexOf(callerClassName));
      }
      else {
         // Determine the BasePath from jar
         // 'file:/home/xmlblaster/work/xmlBlaster/lib/xmlBlaster.jar!/org/xmlBlaster/engine/cluster/simpledomain/RoundRobin.class'
         String jarFile = classResource.substring(classResource.indexOf("/"), classResource.indexOf("!"));
         String jarName = jarFile.substring(jarFile.lastIndexOf("/") + 1, jarFile.length()      );
         basePath = jarFile.substring(0, jarFile.lastIndexOf(jarName));
         if (log.TRACE) log.trace(ME, "jarFile = '" + jarFile + "' jarName = '" + jarName + "'");
      }

      // Return the base path combined with the caller specific path.
      if (log.TRACE) log.trace(ME, "basePath: '" + basePath + "' callerClassName: '" + callerClassName + "'");

      return (basePath + callerClassName);
   }


   /**
    * Returns an Array which contains all URL of the callers class loader.
    * @param caller The URLs for this objects class loader have to be retrieved.
    * @return An Array which contains all URL of the callers class loader.
    */
   private URL[] getClassLoaderURLs(Object caller) {
      URLClassLoader myCL = (URLClassLoader) caller.getClass().getClassLoader();
      return (myCL.getURLs());
   }

   /*
    * Change String class names to URL objects. 
    * @param stringUrls The array containing the array of URL for the specified class' class loader.
    * @return an array of URL containig the stringUrls array
    * @exception XmlBlasterException if the array of URLs can not be formed.
    */
   private URL[] stringToUrl(ArrayList stringUrls) throws XmlBlasterException {
      if (stringUrls == null)
         return new URL[0];
      URL[] url = new URL[stringUrls.size()];
      
      try {
         for(int ii=0; ii < stringUrls.size(); ii++) {
            url[ii] = new URL( "file", null, (String)stringUrls.get(ii) );
         }
      } catch (MalformedURLException e) {
         throw new XmlBlasterException("Malformed Url Exception occured: ", e.toString());
      }

      if (log.TRACE) {
         log.trace(ME, "New Classpath as URL before creating classloader:");
         for (int ii = 0; ii < url.length; ii++) {
              log.trace(ME, ">>" + ii +": " + url[ii].toString() + "<<");
        }
      }

      return (url);
   }

   /**
    * Prints the absolute pathname of the class file
    * containing the specified class name, as prescribed
    * by the current classpath.
    *
    * @param caller
    * @param className Name of the class.
    * @return Url of resource of className.
    * @author <a href="mailto:mike@clarkware.com">Mike Clark</a>
    * @author <a href="http://www.clarkware.com">Clarkware Consulting</a>
    */
   public String which(Object caller, String className) {

      if (!className.startsWith("/")) {
         className = "/" + className;
      }
      className = className.replace('.', '/');
      className = className + ".class";

      java.net.URL classUrl = caller.getClass().getResource(className);

      if (classUrl != null) {
         if (log.TRACE) log.trace(ME, "Class '" + className + "' found in '" + classUrl.getFile() + "'");
      } else {
         if (log.TRACE) log.trace(ME, "\nClass '" + className + "' not found in '" + System.getProperty("java.class.path") + "'");
      }
      return classUrl.getFile().toString();
   } // end of which
}
