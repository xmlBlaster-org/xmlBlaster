/*------------------------------------------------------------------------------
Name:      ClassLoaderFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Creates a new class loader for the pluginmanager.
Version:   $Id: ClassLoaderFactory.java,v 1.3 2002/07/13 18:59:22 ruff Exp $
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
   /*
      log.call(ME, "Entering constructor");
      String basePath = null;
      XmlBlasterClassLoader cl = null;

      if (log.TRACE) {
         try {
            cl = getXmlBlasterClassLoader((Object)this, "org.xmlBlaster.util.ClassLoaderFactory");
         } catch (Exception e) {
            log.error(ME, "Exception occured: " + e.toString() );
         }

         if (cl != null) {
            URL mu[] = cl.getURLs();
            for (int ii = 0; ii < mu.length; ii++) {
                 log.trace(ME, ii +": " + mu[ii].toString() );
           }
         }
      } // end if TRACE

      log.call(ME, "Leaving constructor");
   */
   } // end of Constructor

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
      if (log.CALL) log.call(ME, "Entering getXmlBlasterClassLoader");
      //XmlBlasterClassLoader URLclassLoader = null;
      String basePath = null;
      classPath = new ArrayList(); // new array containing all URL for the new classpath

      if (log.TRACE) log.trace(ME, "pluginName: '" + plugin + "'");

      // calling for object related base class path
      basePath = getBasePath(caller, plugin);
      if (log.TRACE) log.trace(ME, "BasePath: '" + basePath + "'");

      // scanning the basePath (and no deeper dirs!) for jars:
      if (basePath != null) {
         File baseDir = new File(basePath);

         if ( !baseDir.exists() || !baseDir.canRead() )
            return ( new XmlBlasterClassLoader( appendURLtoClassPath(null, caller), plugin ) );

         String list[] = baseDir.list(); // getting content

         for(int ii = 0; ii < list.length; ii++) {
            String filename = list[ii].toLowerCase();
            if (log.TRACE) log.trace(ME, ii +": '" + filename + "'");
            // add it if it's a jar
            if (!filename.endsWith(".jar"))
               continue; // if no jar
            File file = new File(basePath, list[ii]);
            if (!file.isDirectory()) {
               classPath.add(file.toString());
               if (log.TRACE) log.trace(ME, "Adding: '" + file.getAbsolutePath() + "'");
            } // not directory
         } // end of for
      } // end of if != null

      if (log.CALL) log.call(ME, "Leaving getClassLoader");
      // return ( new XmlBlasterClassLoader( null, plugin ) );
      return ( new XmlBlasterClassLoader( appendURLtoClassPath(classPath, caller), plugin ) );
   }

   /*
   Retrievs the base path for the object related classpath.
   Taking the base path from the line in the environment classpath
   which contains the xmlBlaster.jar first!

   Adding the name of the calling class as path to the basepath.

   This may be critical because one may rename xmlBlaster.jar.

   !!! TODO: How do I now which jar-file my(this) class came from ??? !!!

   @param caller Type of the calling class
   @return The base path for the caller specific additional classes.
   */
   private String getBasePath(Object caller, String plugin) {
      if (log.CALL) log.call(ME, "Entering getBasePath");

      URL callersURL[] = null; // all URLs of the callers classpath
      String basePath = ""; // i.e. '/home/goetzger/java/xmlBlaster/lib/'
      String callerClassName = null; // i.e. 'org.xmlBlaster.util.ClassLoaderFactory'

      String jarName = "xmlBlaster.jar"; // !!! critical if on renames xmlBlaster.jar
      int jarIndex = -1;

      // occures at the beginning of the return String of the getClass().toString() - call
      // i.e. for this class: className: 'class org.xmlBlaster.util.ClassLoaderFactory'
      String classType = "class ";

      String fileSeparator = System.getProperty("file.separator");

      callersURL = getClassLoaderURLs((Object) caller); // all URLs of the callers classpath
      // check for null!?

      for (int ii=0; ii < callersURL.length; ii++) {
           if (log.TRACE)
            log.trace(ME, ii +": " + callersURL[ii].getFile() + "[" + callersURL[ii].toString() + "]");
         // getting the path from CP where jarName is contained
         jarIndex = callersURL[ii].getFile().lastIndexOf(jarName);
         if (jarIndex != -1) {
            basePath = callersURL[ii].getFile().substring(0, jarIndex);
            break; // no further search, taking the first hit
         } // end of if -1
      } // end of for
      if (log.TRACE) log.trace(ME, "BasePath: '" + basePath + "'" );

      if (plugin.equals("")) {
         callerClassName = caller.getClass().toString();
           callerClassName = callerClassName.substring(classType.length(), callerClassName.length());
      } else {
         callerClassName = plugin;
         if (log.TRACE) log.trace(ME, "taking pluginName");
      }

      if (log.TRACE) log.trace(ME, "className: '" + callerClassName + "'");

      // Now we need to replace the '.' from the package name to the '/' for a path name.
      // vi compliant ;-) :s/\./\//cg
      // Or even better: replace it by the property file.delimiter of the desired OS.
      callerClassName = StringHelper.replaceAll(callerClassName, ".", fileSeparator); // replace . by fileSeperator
      // callerClassName = callerClassName.replaceAll("\\.", fileSeparator); // since JDK 1.4 :-(
      if (log.TRACE) log.trace(ME, "className: '" + callerClassName + "'");

      // Return the base path combined with the caller specific path.
      if (log.TRACE) log.trace(ME, "baseLibPath: '" + basePath + callerClassName + "'");

      return (basePath + callerClassName);
   } // end of getBasePath


   /*
   Returns an Array which contains all URL of the callers class loader.
   @param caller The URLs for this objects class loader have to be retrieved.
   @return An Array which contains all URL of the callers class loader.
   */
   private URL[] getClassLoaderURLs(Object caller) {

      URLClassLoader myCL = (URLClassLoader) caller.getClass().getClassLoader();
      return (myCL.getURLs());
   }

   /*
   Returns an array of URLs containg the URL first and the
   callers class loader URLs second in the list.
   @param newURL The array containing the array of URL for the specified class' class loader.
   @param caller the caller objects which URL of the class loader have to be appended
                 to the newURL list
   @return an array of URL containig the newURL array first and the callers class loader
           URLs second in the list.
   @exception XmlBlasterException if the array of URLs can not be formed.
   */
   private URL[] appendURLtoClassPath(ArrayList newURL, Object caller) throws XmlBlasterException {

      /*
      URL[] callerURL = getClassLoaderURLs( caller );

      // just to see the original classloader URLs
      if (log.TRACE) {
         for (int ii = 0; ii < callerURL.length; ii++) {
              log.trace(ME, ii +": " + callerURL[ii].toString() );
        }
      } // end if TRACE
      */
      if (newURL == null)
         return new URL[0];
         //return (callerURL);

      int arraySize = newURL.size();
      // int arraySize = callerURL.length + newURL.size();
      int index = 0;

      URL[] url = new URL[arraySize]; // new array to be returned

      try {
         for(int ii=0; ii < newURL.size(); ii++) {
            index++;
            //url[ii] = new URL( "file", null, (String)newURL.get(ii).toString() );
            url[ii] = new URL( "file", null, (String)newURL.get(ii) );
         }
         /*
         for(int ii=0; ii < callerURL.length; ii++) {
            url[index] = callerURL[ii];
            //url[index] = new URL( callerURL[ii].toString() );
            index++;
         }*/
      } catch (MalformedURLException e) {
         throw new XmlBlasterException("Malformed Url Exception occured: ", e.toString());
      }

      if (log.TRACE) {
         log.trace(ME, "New Classpath as URL before creating classloader:");
         for (int ii = 0; ii < url.length; ii++) {
              log.trace(ME, ">>" + ii +": " + url[ii].toString() + "<<");
        }
      } // end if TRACE

      return (url);

   }// end of  addURLtoClassPath

   public void listClassPath(Object caller) {
      if (log.TRACE) {
         URLClassLoader myCL = (URLClassLoader) caller.getClass().getClassLoader();

         URL[] callerURL = myCL.getURLs();

         for (int ii = 0; ii < callerURL.length; ii++)
            log.trace(ME, ii +": " + callerURL[ii].toString() );
      }

   } // end of listClassPath


/*
 * <code>JWhich</code> is a utility that takes a Java class name
 * and displays the absolute pathname of the class file that would
 * be loaded first by the class loader, as prescribed by the
 * class path.
 * <p>
 * Usage is similar to the UNIX <code>which</code> command.
 * <p>
 * Example uses:
 * <p>
 * <blockquote>
 *      To find the absolute pathname of <code>MyClass.class</code>
 *      not in a package:
 *      <pre>java JWhich MyClass</pre>
 *
 *      To find the absolute pathname of <code>MyClass.class</code>
 *      in the <code>my.package</code> package:
 *      <pre>java JWhich my.package.MyClass</pre>
 * </blockquote>
 *
 * @author <a href="mailto:mike@clarkware.com">Mike Clark</a>
 * @author <a href="http://www.clarkware.com">Clarkware Consulting</a>
 */

   /**
   * Prints the absolute pathname of the class file
   * containing the specified class name, as prescribed
   * by the current classpath.
   *
   * @param className Name of the class.
   */
   public String which(Object caller, String className) {

      if (!className.startsWith("/")) {
         className = "/" + className;
      }
      className = className.replace('.', '/');
      className = className + ".class";

      java.net.URL classUrl = caller.getClass().getResource(className);

      if (classUrl != null) {
         if (log.TRACE) log.trace(ME, "Class '" + className + "' found in \n'" + classUrl.getFile() + "'");
      } else {
         if (log.TRACE) log.trace(ME, "\nClass '" + className + "' not found in \n'" + System.getProperty("java.class.path") + "'");
      }
      return classUrl.toString();
   } // end of which


   /*
   public static void main(String args[]) {
      ClassLoaderFactory clf = new ClassLoaderFactory();
   } // end of main
   */

} // end of class

// end of file
