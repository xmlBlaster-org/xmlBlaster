/*------------------------------------------------------------------------------
Name:      ClassLoaderFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.classloader;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

import org.xmlBlaster.util.ReplaceVariable;
import java.io.File;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;

public class StandaloneClassLoaderFactory implements ClassLoaderFactory {
   public String ME;
   private Global glob;
   private static Logger log = Logger.getLogger(StandaloneClassLoaderFactory.class.getName());
   private ArrayList classPath = null; // array containig all URL for the new classpath
   private int instanceCounter = 0;

   public StandaloneClassLoaderFactory() {

   }
   /**
    * We are a singleton in respect to a Global instance.
    */
   public StandaloneClassLoaderFactory (Global glob) {
      init(glob);
   }

   public void init(Global glob) {
     ++instanceCounter;
     this.ME = "ClassLoaderFactory-" + instanceCounter;
     this.glob = glob;

     if (log.isLoggable(Level.FINER)) log.finer("ClassLoaderFactory constructor #" + instanceCounter);
   }

   /**
    * Creates and returns a new URL class loader based on the callers class loader and the
    * callers related additional classes which may exist in a specified path.
    */
   public URLClassLoader getPluginClassLoader(PluginInfo pluginInfo) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering getPluginClassLoader for plugin=" + pluginInfo.getClassName());

      java.util.Properties pluginParams = pluginInfo.getParameters();
      LoaderInfo loaderInfo = getLoaderInfo(this, pluginInfo.getClassName());
      if (log.isLoggable(Level.FINE)) log.fine(loaderInfo.toString());

      // In xmlBlaster.properties e.g.
      // ProtocolPlugin[IOR][1.0]=org.xmlBlaster.protocol.soap.SoapDriver,classpath=soap.jar:xerces.jar
      String classPathStr = (String)pluginParams.get("classpath");
      ArrayList classPath = new ArrayList();
      if (classPathStr != null) {
         log.info("Analyzing classpath=" + classPathStr + " for plugin " + pluginInfo.getClassName());
         StringTokenizer st = new StringTokenizer(classPathStr, ";:");
         while (st.hasMoreElements()) {
            String jar = (String)st.nextElement();  // e.g. "soap/soap.jar"
            if (log.isLoggable(Level.FINE)) log.fine("Looking for jar '" + jar + "' ...");
            File f = new File(jar); // 1. check absolute path
            if (f.canRead()) {
               classPath.add(jar);
               continue;
            }
            String jarStripped = f.getName();      // e.g. "soap.jar"
            if (log.isLoggable(Level.FINE)) log.fine("Looking for jarStripped '" + jarStripped + "' ...");
            f = new File(jarStripped);      // 2. check local directory
            if (f.canRead()) {
               classPath.add(jarStripped);
               continue;
            }
            String resourceJar = loaderInfo.rootPath +jar; // e.g. "/home/xmlblast/xmlBlaster/lib/soap/soap.jar"
            if (log.isLoggable(Level.FINE)) log.fine("Looking for resourceJar=" + resourceJar + " ...");
            f = new File(resourceJar);      // 3. check resource path of this instance
            if (f.canRead()) {
               classPath.add(resourceJar);
               continue;
            }
            String resourceJarStripped = loaderInfo.rootPath +jar;   // e.g. "/home/xmlblast/xmlBlaster/lib/soap.jar"
            if (log.isLoggable(Level.FINE)) log.fine("Looking for resourceJarStripped=" + resourceJarStripped + " ...");
            f = new File(resourceJarStripped);      // 3. check resource path of this instance
            if (f.canRead()) {
               classPath.add(resourceJarStripped);
               continue;
            }
            log.info("Plugin '" + pluginInfo.getClassName() + "' specific jar file '" + jar + "' not found, using JVM default CLASSPATH");

                                    // 4. check JVM classpath
            URL[] urls = ((URLClassLoader)this.getClass().getClassLoader()).getURLs();
            for (int j=0; j<urls.length; j++) {
               if (urls[j].getFile().equalsIgnoreCase(jar)) {
                  classPath.add(urls[j].toString());
                  continue;
               }
            }
         }
      }

      // The plugin itself needs to be loaded by our ClassLoader to inherit it
      // to all children classes - add the classpath to the plugin class:
      if (classPath.size() > 0) {
         if (loaderInfo.jarPath != null)
            classPath.add(loaderInfo.jarPath); // Attach to end e.g. xmlBlaster.jar
         else
            classPath.add(loaderInfo.rootPath); // Attach to end e.g. xmlBlaster/classes
      }

      if (log.isLoggable(Level.FINE)) log.fine("Found " + classPath.size() + " plugin specific jar files");
      return new PluginClassLoader(glob, stringToUrl(classPath), pluginInfo );
   }


   /**
    * Creates and returns a new URL class loader based on the callers class loader and the
    * callers related additional classes which may exist in a specified path.
    */
   public URLClassLoader getXmlBlasterClassLoader() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering getXmlBlasterClassLoader ...");

      LoaderInfo loaderInfo = getLoaderInfo(this, "org.xmlBlaster.Main");
      if (log.isLoggable(Level.FINE)) log.fine(loaderInfo.toString());

      ArrayList classPath = new ArrayList();

      if (loaderInfo.jarPath != null)
         classPath.add(loaderInfo.jarPath); // Attach to end e.g. xmlBlaster.jar
      else
         classPath.add(loaderInfo.rootPath); // Attach to end e.g. xmlBlaster/classes

      URL[] urls = ((URLClassLoader)this.getClass().getClassLoader()).getURLs();
      for (int i=0; i<urls.length; i++) {
         String xmlBlasterJar = (String)classPath.get(0);
         if( urls[i].getFile().indexOf(xmlBlasterJar) < 0 ) {
            classPath.add(urls[i].getFile());
         }
      }

      if (log.isLoggable(Level.FINE)) {
         String text = "Build new classpath with " + classPath.size() + " entries:";
         for(int i = 0; i < classPath.size(); i++ ) {
            text += (String)classPath.get(i);
            if(i < (classPath.size() - 1) )
               text += ":";
         }
         log.fine(text);
      }
      return new XmlBlasterClassLoader(this.glob, stringToUrl(classPath) );
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
      String ME = "ClassLoaderFactory";
      //if (log.isLoggable(Level.FINER)) log.call(ME, "Entering getLoaderInfo");
      if (plugin == null || plugin.length() < 1) {
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(Global.instance(), ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "getLoaderInfo() with plugin=null");
      }

      String classResource = which(caller, plugin); // e.g. "/home/xmlblast/xmlBlaster/classes/org/xmlBlaster/protocol/corba/CorbaDriver.class"
      if (classResource == null) {
         String text = "Can't find class " + plugin + ", please check your plugin name and your CLASSPATH";
         //if (log.isLoggable(Level.FINE)) log.trace(ME, text);
         throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, text);
      }
      //if (log.isLoggable(Level.FINE)) log.trace(ME, "plugin '" + plugin + "' has resource path " + classResource );

      String pluginSlashed = ReplaceVariable.replaceAll(plugin, ".", "/"); // replace '.' by fileSeperator (windows wants "/" as well)
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
      //if (log.isLoggable(Level.FINE)) log.trace(ME, loaderInfo.toString());
      return loaderInfo;
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
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "Malformed Url Exception occured: ", e);
      }

      if (log.isLoggable(Level.FINE)) {
         log.fine("New Classpath as URL before creating classloader:");
         for (int ii = 0; ii < url.length; ii++) {
              log.fine(">>" + ii +": " + url[ii].toString() + "<<");
        }
      }

      return (url);
   }

   /**
    * Prints the absolute pathname of the class file
    * containing the specified class name, as prescribed
    * by the current classpath.
    * <pre>
    * author <a href="mailto:mike@clarkware.com">Mike Clark</a>
    * author <a href="http://www.clarkware.com">Clarkware Consulting</a>
    * </pre>
    *
    * @param caller
    * @param className Name of the class, e.g. "org.xmlBlaster.protocol.corba.CorbaDriver"
    * @return Url of resource of className.
    *   e.g. "/home/xmlblast/xmlBlaster/classes/org/xmlBlaster/protocol/corba/CorbaDriver.class"
    */
   public static String which(Object caller, String className) {

      if (!className.startsWith("/")) {
         className = "/" + className;
      }
      className = className.replace('.', '/');
      className = className + ".class";

      java.net.URL classUrl = caller.getClass().getResource(className);

      if (classUrl != null) {
         //if (log.isLoggable(Level.FINE)) log.trace(ME, "Class '" + className + "' found in '" + classUrl.getFile() + "'");
         return classUrl.getFile().toString();
      }
      else {
         //if (log.isLoggable(Level.FINE)) log.trace(ME, "Class '" + className + "' not found in '" + System.getProperty("java.class.path") + "'");
         return null;
      }
   }
}

