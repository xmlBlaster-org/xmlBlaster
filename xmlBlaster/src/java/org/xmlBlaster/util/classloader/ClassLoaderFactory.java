/*------------------------------------------------------------------------------
Name:      ClassLoaderFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Creates a new class loader for the pluginmanager.
Version:   $Id: ClassLoaderFactory.java,v 1.1 2002/08/25 15:17:21 ruff Exp $
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.classloader;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
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
   public ClassLoaderFactory (Global glob) {
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
    *   /home/goetzger/java/xmlBlaster/demo
    *   /home/goetzger/java/xmlBlaster/classes
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
    * @return a PluginClassLoader which contains the URL of the parent class loader.
    *           and the URL related to the callers class.
    * @exception XmlBlasterException if the array of URLs can not be formed.
    */
   public PluginClassLoader getPluginClassLoader(Object caller, String plugin) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering getPluginClassLoader for plugin=" + plugin);
      String basePath = null;
      classPath = new ArrayList(); // new array containing all String URL for the new classpath

      if (log.TRACE) log.trace(ME, "caller: '" + caller.getClass().getName() + "' pluginName: '" + plugin + "'");

      // calling for object related base class path
      LoaderInfo loaderInfo = getLoaderInfo(caller, plugin);
      if (log.TRACE) log.trace(ME, loaderInfo.toString());

      basePath = loaderInfo.basePath;
      if (log.TRACE) log.trace(ME, "Using base path '" + basePath + "' to scan for specific jar files ...");

      // scanning the basePath (and no deeper dirs!) for jars:
      if (basePath != null) {
         File baseDir = new File(basePath);

         if ( !baseDir.exists() || !baseDir.canRead() )
            return ( new PluginClassLoader(glob, new URL[0], plugin ) );

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

      // The plugin itself needs to be loaded by our ClassLoader to inherit it
      // to all children classes - add the classpath to the plugin class:
      if (loaderInfo.jarPath != null)
         classPath.add(loaderInfo.jarPath); // Attach to end e.g. xmlBlaster.jar
      else
         classPath.add(loaderInfo.rootPath); // Attach to end e.g. xmlBlaster/classes

      if (log.TRACE) log.trace(ME, "Found " + (classPath.size()-1) + " jar files in '" + basePath + "'");
      return new PluginClassLoader(glob, stringToUrl(classPath), plugin );
   }

   /**
    * Retrievs the base path for the object related classpath.
    * Taking the base path from the line in the environment classpath
    * which contains the xmlBlaster.jar first!
    *
    * Adding the name of the calling class as path to the basepath.
    *
    * @param caller Type of the calling class
    * @param plugin The plugin name e.g. "org.xmlBlaster.protocol.corba.CorbaDriver"
    *               or null
    * @return The base path for the caller specific additional classes, is never null
    * @exception On failure
    */
   public static LoaderInfo getLoaderInfo(Object caller, String plugin) throws XmlBlasterException {
      //if (log.CALL) log.call(ME, "Entering getLoaderInfo");
      if (plugin == null || plugin.length() < 1) {
         Thread.currentThread().dumpStack();
         throw new IllegalArgumentException("ClassLoaderFactory.getLoaderInfo() with plugin=null");
      }

      String classResource = which(caller, plugin); // e.g. "/home/xmlblast/xmlBlaster/classes/org/xmlBlaster/protocol/corba/CorbaDriver.class"
      if (classResource == null) {
         String text = "Can't find class " + plugin + ", please check your plugin name and your CLASSPATH";
         //if (log.TRACE) log.trace(ME, text);
         throw new XmlBlasterException("ClassLoaderFactory", text);
      }
      //if (log.TRACE) log.trace(ME, "plugin '" + plugin + "' has resource path " + classResource );

      String pluginSlashed = StringHelper.replaceAll(plugin, ".", "/"); // replace '.' by fileSeperator (windows wants "/" as well)
      // plugin.replaceAll("\\.", "/"); // since JDK 1.4 :-(

      String jarPath = null;
      String jarName = null;
      String rootPath = ""; // i.e. '/home/developer/java/xmlBlaster/lib/'

      if(classResource.indexOf('!') == -1) {
         // Determine the BasePath from classes
         // log.warn(ME, "Class not loaded from jar, don't know how to determine rootPath");
         rootPath = classResource.substring(0, classResource.lastIndexOf(pluginSlashed));
      }
      else {
         // Determine the BasePath from jar
         // 'file:/home/xmlblaster/work/xmlBlaster/lib/xmlBlaster.jar!/org/xmlBlaster/engine/cluster/simpledomain/RoundRobin.class'
         jarPath = classResource.substring(classResource.indexOf("/"), classResource.indexOf("!"));
         jarName = jarPath.substring(jarPath.lastIndexOf("/") + 1, jarPath.length()      );
         rootPath = jarPath.substring(0, jarPath.lastIndexOf(jarName));
         // jarPath = '/home/xmlblast/xmlBlaster/lib/xmlBlaster.jar' jarName = 'xmlBlaster.jar'
      }

      LoaderInfo loaderInfo = new LoaderInfo(plugin, rootPath, jarPath, jarName, pluginSlashed);
      //if (log.TRACE) log.trace(ME, loaderInfo.toString());
      return loaderInfo;
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
    * @param className Name of the class, e.g. "org.xmlBlaster.protocol.corba.CorbaDriver"
    * @return Url of resource of className.
    *   e.g. "/home/xmlblast/xmlBlaster/classes/org/xmlBlaster/protocol/corba/CorbaDriver.class"
    * @author <a href="mailto:mike@clarkware.com">Mike Clark</a>
    * @author <a href="http://www.clarkware.com">Clarkware Consulting</a>
    */
   public static String which(Object caller, String className) {

      if (!className.startsWith("/")) {
         className = "/" + className;
      }
      className = className.replace('.', '/');
      className = className + ".class";

      java.net.URL classUrl = caller.getClass().getResource(className);

      if (classUrl != null) {
         //if (log.TRACE) log.trace(ME, "Class '" + className + "' found in '" + classUrl.getFile() + "'");
         return classUrl.getFile().toString();
      }
      else {
         //if (log.TRACE) log.trace(ME, "Class '" + className + "' not found in '" + System.getProperty("java.class.path") + "'");
         return null;
      }
   }
}

