/*------------------------------------------------------------------------------
Name:      FileLocator.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util;

import org.xmlBlaster.util.Global;
import java.io.File;
import java.util.StringTokenizer;
import java.util.Vector;
import org.xmlBlaster.util.def.ErrorCode;
import org.jutils.log.LogChannel;
import java.net.URL;


public class FileLocator
{
   private final static String ME = "FileLocator";
   private Global glob;
   private LogChannel log;

   /**
    * Constructor. It does nothing but initializing the log and assigning the global.
    */
   public FileLocator(Global glob) {
      this.glob = glob;
      this.log = glob.getLog("core");
   }

   /**
    * Searches in the given path for the specified filename. If the file has not been found in the given 
    * path, then null is returned. Otherwise the complete (absolute) path of the file is returned. 
    *
    * @param path the path on which to search for the given file.
    * @param filename the name of the file to search. NOTE: if it is an absolute filename, then the path
    *        is ignored and a warning is written to the log.
    * @throws XmlBlasterException with error code resource.configuration if either the file has been found
    *         but it can not be read, or if it is a directory. Note that if there are several files in the
    *         given path and the first one found is either read protected or is a directory, then the second
    *         is taken and no exception is thrown.
    */
   public final String findFile(String[] path, String filename) throws XmlBlasterException {
      File file = new File(filename);
      if (file.isAbsolute()) {
         this.log.warn(ME, "the filename '" + filename + "' is absolute, I will ignore the given search path '" + path + "'");
         if (file.exists()) {
            if (file.isDirectory()) {
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".findFile", "the given name '" + file.getAbsolutePath() + "' is a directory");
            }
            if (!file.canRead()) {
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".findFile", "don't have the rights to read the file '" + file.getAbsolutePath() + "'");
            }
            return file.getAbsolutePath();
         }
      }

      XmlBlasterException ex = null;
      for (int i=0; i< path.length; i++) {
         File tmp = new File(path[i], filename);
         if (tmp.exists()) {
            if (tmp.isDirectory()) {
               ex = new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".findFile", "the given name '" + tmp.getAbsolutePath() + "' is a directory");
            }
            else {
               if (!tmp.canRead()) {
                  ex = new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".findFile", "don't have the rights to read the file '" + tmp.getAbsolutePath() + "'");
               }
               else return tmp.getAbsolutePath();
            }
         }
      }
      if (ex != null) throw ex;
      return null;
   }

   /**
    * Parses the given Path into an array of String. If the input path was null, null is returned. If the
    * input path was empty null is returned. Otherwise all path are returned. If the separator is null, 
    * null is returned.
    */
   public final String[] parsePath(String pathAsString, String separator) {
      if (pathAsString == null || separator == null) return null;
      if (pathAsString.trim().length() < 1) return null;

      StringTokenizer tokenizer = new StringTokenizer(pathAsString, separator);
      int size = tokenizer.countTokens();
      String[] ret = new String[size];
      for (int i=0; i < ret.length; i++) {
         ret[i] = tokenizer.nextToken();
      }
      return ret;
   }

   /**
    * finds the file in the given path. The separator for the path is given explicitly.
    */
    public final String findFile(String path, String separator, String filename)
       throws XmlBlasterException {
       String[] parsedPath = parsePath(path, separator);
       return findFile(parsedPath, filename);
    }

    /**
     * finds the file in the given path. The path separator is implicitly set to ':'.
     */
    public final String findFile(String path, String filename)
       throws XmlBlasterException {
       return findFile(path, ":", filename);
    }

    public final String[] createXmlBlasterSearchPath() {
       Vector vec = new Vector();
       vec.add(".");
       String projectHome = System.getProperty("PROJECT_HOME");
       if (projectHome != null && projectHome.length() > 0 ) vec.add(projectHome);
       String home = System.getProperty("user.home");
       if (home != null && home.length() > 0 ) vec.add(home);
       String javaExtDirs = System.getProperty("java.ext.dirs");
       if (javaExtDirs != null && javaExtDirs.length() > 0 ) vec.add(javaExtDirs);
       String javaHome = System.getProperty("java.home");
       if (javaHome != null && javaHome.length() > 0 )  vec.add(javaHome);

       String[] ret = (String[])vec.toArray(new String[vec.size()]);
       return ret;
    }


   /**
    * checks if the file exists in the given path (only one path).
    * @param path the path in which the file should reside. If it is null, then
    *        filename will be considered an absolute filename.
    * @param filename the name of the file to lookup
    * @return URL the URL for the given file or null if no file found.
    */
   private final URL findFileInSinglePath(String path, String filename) {
      if (this.log.CALL) this.log.call(ME, "findFileInSinglePath with path='" +
         path + "' and filename='" + filename + "'");
      File file = null;
      if (path != null) file = new File(path, filename);
      else file = new File(filename);
      if (file.exists()) {
         if (file.isDirectory()) {
            this.log.warn(ME, "findFileInSinglePath: the given name '" + file.getAbsolutePath() + "' is not a file, it is a directory");
            return null;
         }
         if (!file.canRead()) {
            this.log.warn(ME, "findFileInSinglePath: don't have the rights to read the file '" + file.getAbsolutePath() + "'");
            return null;
         }
         try {
            return file.toURL();
         }
         catch (java.net.MalformedURLException ex) {
            this.log.warn(ME, "findFileInSinglePath: path='" + path + "', filename='" + filename + " exception: " + ex.getMessage());
            return null;
         }
      }
      return null;
   }

   /**
    * tries to find a file according to the xmlBlaster Strategy.
    * The strategy is:
    * <ul>
    *   <li>given value of the specified property</li>
    *   <li>user.dir</li>
    *   <li>full name (complete with path)</li>
    *   <li>PROJECT_HOME global property</li>
    *   <li>user.home</li>
    *   <li>classpath</li>
    *   <li>java.ext.dirs</li>
    *   <li>java.home</li>
    * </ul>
    * @paran propertyName The key to look into Global, for example
    *        <tt>locator.findFileInXmlBlasterSearchPath("pluginsFile", "/tmp/xmlBlasterPlugins.xml").getFile();</tt>
    *        looks for the key "pluginsFile" in global scope, if found the file of the keys value is chosen, else
    *        the above lookup applies.
    *  @param filename
    *  @return URL the URLfrom which to read the content or null if
    *          the file/resource has not been found. Note that we return the
    *          url instead of the filename since it could be a resource and
    *          therefore it could not be opened as a normal file.
    * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/util.property.html">The util.property requirement</a>
    */
   public final URL findFileInXmlBlasterSearchPath(String propertyName, String filename) {
      String path = null;
      URL ret = null;
      path = this.glob.getProperty().get(propertyName, (String)null);
      if (path != null) {
         if (this.log.TRACE) this.log.trace(ME, "findFileInXmlBlasterSearchPath: the path: '" + path + "' and the filename to search: '" + filename + "'");
//         ret = findFileInSinglePath(path, filename);
         ret = findFileInSinglePath(null, path);
         if (ret != null) return ret;
      }
      // user.dir
      path = System.getProperty("user.dir", ".");
      ret = findFileInSinglePath(path, filename);
      if (ret != null) return ret;

      // full name (complete with path)
      ret = findFileInSinglePath(null, filename);
      if (ret != null) return ret;

      // PROJECT_HOME global property
      path = this.glob.getProperty().get("PROJECT_HOME", (String)null);
      if (path != null) {
         ret = findFileInSinglePath(path, filename);
         if (ret != null) return ret;
      }

      // user.home
      path = System.getProperty("user.home", (String)null);
      if (path != null) {
         ret = findFileInSinglePath(path, filename);
         if (ret != null) return ret;
      }

      // classpath
      try {
         URL url = this.glob.getClassLoaderFactory().getXmlBlasterClassLoader().getResource(filename);
         if (url != null) return url;
      }
      catch (XmlBlasterException ex) {
         this.log.warn(ME, "findFileInXmlBlasterSearchPath: " + ex.getMessage());
      }

      // java.ext.dirs
      path = System.getProperty("java.ext.dirs", (String)null);
      if (path != null) {
         ret = findFileInSinglePath(path, filename);
         if (ret != null) return ret;
      }

      // java.home
      path = System.getProperty("java.home", (String)null);
      if (path != null) {
         return findFileInSinglePath(path, filename);
      }
      return null;
    }

    public static void main(String[] args) {
       Global glob = Global.instance();
       glob.init(args);
       LogChannel log = glob.getLog("test");

       FileLocator locator = new FileLocator(glob);

       try {
          String ret = locator.findFileInXmlBlasterSearchPath("pluginsFile", "xmlBlasterPlugins.xml").getFile();
          if (ret != null) {
             System.out.println("The file 'xmlBlasterPlugins.xml' has been found");
             System.out.println("Its complete path is: '" + ret + "'");
          }
          else {
             System.out.println("The file 'xmlBlasterPlugins.xml' has not been found");
          }
       }
       catch (Exception ex) {
          System.err.println("Error occured: " + ex.toString());
       }


    }   

}

