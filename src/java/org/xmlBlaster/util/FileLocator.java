/*------------------------------------------------------------------------------
Name:      FileLocator.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util;

import org.xmlBlaster.util.Global;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.Vector;
import org.xmlBlaster.util.def.ErrorCode;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.net.URL;


public class FileLocator
{
   private final static String ME = "FileLocator";
   private Global glob;
   private static Logger log = Logger.getLogger(FileLocator.class.getName());

   /**
    * Constructor. It does nothing but initializing the log and assigning the global.
    */
   public FileLocator(Global glob) {
      this.glob = glob;

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
         log.warning("the filename '" + filename + "' is absolute, I will ignore the given search path '" + path + "'");
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
      if (log.isLoggable(Level.FINER)) log.finer("findFileInSinglePath with path='" +
         path + "' and filename='" + filename + "'");
      File file = null;
      if (path != null) file = new File(path, filename);
      else file = new File(filename);
      if (file.exists()) {
         if (file.isDirectory()) {
            log.warning("findFileInSinglePath: the given name '" + file.getAbsolutePath() + "' is not a file, it is a directory");
            return null;
         }
         if (!file.canRead()) {
            log.warning("findFileInSinglePath: don't have the rights to read the file '" + file.getAbsolutePath() + "'");
            return null;
         }
         try {
            return file.toURL();
         }
         catch (java.net.MalformedURLException ex) {
            log.warning("findFileInSinglePath: path='" + path + "', filename='" + filename + " exception: " + ex.getMessage());
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
         if (log.isLoggable(Level.FINE)) log.fine("findFileInXmlBlasterSearchPath: the path: '" + path + "' and the filename to search: '" + filename + "'");
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
         log.warning("findFileInXmlBlasterSearchPath: " + ex.getMessage());
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

   /**
    * Read a file into <code>byte[]</code>.
    * <br><b>Example:</b><br>
    *    <code>byte[] data=FileLocator.readFile("/tmp", "hello.txt");</code>
    *
    * @param      parent Path to the file, can be null
    * @param      fileName  Name of file
    * @return     data from the file
    * @exception  XmlBlasterException
    *             if the file is not readable or any error occurred while reading the file.
    */
   public static final byte[] readFile(String parent, String fileName)
      throws XmlBlasterException
   {
      File f = (parent==null) ? new File(fileName) : new File(parent, fileName);
      return readFile(f);
   }
   
   public static final byte[] readFile(File f)
      throws XmlBlasterException
   {
      byte[] fileBlob = null;
      String fileName = f.getAbsolutePath();
      if (!f.exists()) {
         throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME, "Sorry, can't find file " + fileName);
      }
      if (!f.isFile()) {
         throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME, "Sorry, doesn't seem to be a file " + fileName);
      }
      if (!f.canRead()) {
         throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME, "Sorry, no access permissions for file " + fileName);
      }
      FileInputStream from = null;
      try {
         from = new FileInputStream(f);
         fileBlob = new byte[ (int) f.length()];
         int bytes_read = from.read(fileBlob);
         if (bytes_read != f.length()) {
            throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME, "File read error in " + fileName + ": Excpected " + f.length() + " bytes, but only found " + bytes_read + "bytes");
         }
      }
      catch (FileNotFoundException e) {
         throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME, e.toString());
      }
      catch (IOException e2) {
         throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME, e2.toString());
      }
      finally {
         if (from != null)
            try {
            from.close();
         }
         catch (IOException e) {
            ;
         }
      }
      if (f.length() != fileBlob.length) {
         throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME, "Read file " + fileName + " with size=" + f.length() + " but only got " + fileBlob.length + " bytes");
      }
      return fileBlob;
   }

   /**
    * Write data from <code>byte[]</code> into a file.
    *
    * @param      parent the path, can be null
    * @param      child the name
    * @param      arr data
    */
   public static final void writeFile(String parent, String child, byte[] arr)
      throws XmlBlasterException
   {
      try {
         File to_file = (parent==null) ? new File(child) : new File(parent, child);
         FileOutputStream to = new FileOutputStream(to_file);
         to.write(arr);
         to.close();
      }
      catch (Exception e) {
         throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME, "Can't write file " + child + ":" + e.toString());
      }
   }

   /**
   * Write data from <code>StringBuffer</code> into a file.
   * @param outName  name of file including path
   * @param str      data
   */
   public static final void writeFile(String name, String str) throws XmlBlasterException {
      writeFile(null, name, str.getBytes());
   }

   /**
    * Write data from <code>StringBuffer</code> into a file.
    * @param outName  name of file including path
    * @param str      some binary data
    */
    public static final void writeFile(String name, byte[] arr) throws XmlBlasterException {
       writeFile(null, name, arr);
    }

   /**
   * Append data from into a file.
   * @param outName  name of file including path
   * @param str      Text
   */
   public static final void appendToFile(String outName, String str) throws XmlBlasterException {
      try {
         boolean append = true;
         FileOutputStream to = new FileOutputStream(outName, append);
         to.write(str.getBytes());
         to.close();
      }
      catch (Exception e) {
         throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE, ME, "Can't write file " + e.toString());
      }
   }

   /**
   * Read a file into <code>String</code>.
   * @param fileName Complete name of file
   * @return ASCII data from the file<br />
   *         null on error
   */
   public static final String readAsciiFile(String fileName) throws XmlBlasterException {
      return readAsciiFile(null, fileName);
   }


   /**
   * Read a file into <code>String</code>.
   * <br><b>Example:</b><br>
   *    <code>String data=FileUtil.readAsciiFile("/tmp/hello");</code>
   * @param parent Path to the file
   * @param fileName name of file
   * @return ASCII data from the file<br />
   *         null on error
   */
   public static final String readAsciiFile(String parent, String child) throws XmlBlasterException {
      byte[] bb = readFile(parent, child);
      if (bb == null)
         return null;
      return new String(bb);
   }


   /**
    * Read a file into <code>byte[]</code>.
    *
    * @param      fileName
    *             Complete name of file
    * @return     data from the file
    * @exception  JUtilsException
    *             if the file is not readable or any error occured while reading the file.
    */
   public static final byte[] readFile(String fileName)
      throws XmlBlasterException
   {
      return readFile(null, fileName);
   }

   public static final void deleteFile(String parent, String fileName) {
      File f = new File(parent, fileName);
      if (f.exists())
         f.delete();
   }

    /**
     * Concatenate a filename to a path (DOS and UNIX, checks for separator).
     * @param path for example "/tmp"
     * @param name for example "hello.txt"
     * @return "/tmp/hello.txt"
     */
   public static String concatPath(String path, String name) {
      if (path == null)
         return name;
      if (name == null)
         return path;
      if (path.endsWith(File.separator) && name.startsWith(File.separator))
         return path + name.substring(1);
      if (path.endsWith(File.separator))
         return path + name;
      if (name.startsWith(File.separator))
         return path + name;
      return path + File.separator + name;
   }

   /**
   * Return the file name extension.
   * @param fileName for example "/tmp/hello.txt"
   * @return extension of the filename "txt"
   */
   public static String getExtension(String fileName) {
      if (fileName == null)
         return null;
      int dot = fileName.lastIndexOf(".");
      if (dot == -1)
         return null;
      return fileName.substring(dot + 1);
   }

   /**
   * Strip the path and the file name extension.
   * @param fileName for example "/tmp/hello.txt"
   * @return filename without extension "hello"
   */
   public static String getBody(String fileName) {
      if (fileName == null)
         return null;
      int dot = fileName.lastIndexOf(".");
      String body = null;
      if (dot == -1)
         body = fileName;
      else
         body = fileName.substring(0, dot);
      int sep = body.lastIndexOf(File.separator);
      if (sep == -1)
         return body;
      return body.substring(sep + 1);
   }
   
   /**
    * Deletes all files and subdirectories under dir.
    * Returns true if all deletions were successful.
    * If a deletion fails, the method stops attempting to delete and returns false.
    * Thanks to "The Java Developers Almanac 1.4"
    */
   public static boolean deleteDir(File dir) {
       if (dir.isDirectory()) {
           String[] children = dir.list();
           for (int i=0; i<children.length; i++) {
               boolean success = deleteDir(new File(dir, children[i]));
               if (!success) {
                   return false;
               }
           }
       }
   
       // The directory is now empty so delete it
       return dir.delete();
   }   

   /**
   * Convert some file extensions to MIME types.
   * <p />
   * A candidate for a property file like /etc/httpd/mime.types
   * @param extension for example "xml"
   * @param defaultVal for example "text/plain"
   * @return for example "text/xml"
   */
   public static String extensionToMime(String extension, String defaultVal) {
      if (extension == null)
         return defaultVal;
      if (extension.equalsIgnoreCase("xml"))
         return "text/xml";
      if (extension.equalsIgnoreCase("html"))
         return "text/html";
      if (extension.equalsIgnoreCase("gml"))
         return "text/gml"; // grafic markup language http://infosun.fmi.uni-passau.de/Graphlet/GML
      if (extension.equalsIgnoreCase("sgml"))
         return "text/sgml";
      if (extension.equalsIgnoreCase("gif"))
         return "image/gif";
      if (extension.equalsIgnoreCase("png"))
         return "image/png";
      if (extension.equalsIgnoreCase("jpeg"))
         return "image/jpeg";
      if (extension.equalsIgnoreCase("jpg"))
         return "image/jpg";
      if (extension.equalsIgnoreCase("pdf"))
         return "application/pdf";
      if (extension.equalsIgnoreCase("rtf"))
         return "text/rtf";
      return defaultVal;
   }
}

