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
import org.xmlBlaster.util.enum.ErrorCode;
import org.jutils.log.LogChannel;


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
     * Finds a file according to the xmlBlaster file finder strategy which is the
     * following search order:
     *<ul>
     * <li>given property (for example 'pluginFile'</li>
     * <li>local directory</li>
     * <li>$PROECT_HOME</li>
     * <li>$HOME</li>
     * <li>java.ext.dirs</li>
     * <li>java.home</li>
     *</ul>
     *<p/>
     * If no file is found, then null is returned, otherwise the first file found is
     * returned.
     *
     * @param filePropertyName the name of the property to which the filename could
     *        be associated to (the first place to look at).
     * @param filename the name of the file to search.
     *
     */
    public final String findXmlBlasterFile(String filePropertyName, String filename)
       throws XmlBlasterException {
       if (this.log.CALL) this.log.call(ME, "findXmlBlasterFile with propertyName='" + filePropertyName + "' and filename='" + filename + "'");
       String nameFromProperty = this.glob.getProperty().get(filePropertyName, (String)null);
       if (nameFromProperty != null) {
          if (this.log.TRACE) this.log.trace(ME, ".findXmlBlasterFile nameFromProperty = '" + nameFromProperty + "'");
          File file = new File(nameFromProperty);
          if (file.exists() && (!file.isDirectory())) return nameFromProperty;
       }
       return findFile(createXmlBlasterSearchPath(), filename);
    }


    public static void main(String[] args) {
       Global glob = Global.instance();
       glob.init(args);
       LogChannel log = glob.getLog("test");

       FileLocator locator = new FileLocator(glob);

       try {
          String ret = locator.findXmlBlasterFile("pluginsFile", "xmlBlasterPlugins.xml");
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

