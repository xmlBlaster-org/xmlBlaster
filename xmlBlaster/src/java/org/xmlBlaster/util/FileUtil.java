/*------------------------------------------------------------------------------
Name:      FileUtil.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: FileUtil.java,v 1.12 2000/06/13 13:04:02 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.io.*;


/**
 * Some helper methods for file handling.
 * <p />
 */
public class FileUtil
{
   private final static String ME = "FileUtil";


   /**
    * Return the file name extension.
    * @param fileName for example "/tmp/hello.txt"
    * @return extension of the filename "txt"
    */
   public static String getExtension(String fileName)
   {
      if (fileName == null) return null;
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
   public static String getBody(String fileName)
   {
      if (fileName == null) return null;
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
    * Concatenate a filename to a path (DOS and UNIX, checks for separator).
    * @param path for example "/tmp"
    * @param name for example "hello.txt"
    * @return "/tmp/hello.txt"
    */
   public static String concatPath(String path, String name)
   {
      if (path == null) return name;
      if (name == null) return path;
      if (path.endsWith(File.separator) && name.startsWith(File.separator))
         return path + name.substring(1);
      if (path.endsWith(File.separator))
         return path + name;
      if (name.startsWith(File.separator))
         return path + name;
      return path + File.separator + name;
   }


   /**
    * Convert some file extensions to MIME types.
    * <p />
    * A candidate for a property file :-)
    * @param extension for example "xml"
    * @param defaultVal for example "text/plain"
    * @return for example "text/xml"
    */
   public static String extensionToMime(String extension, String defaultVal)
   {
      if (extension == null) return defaultVal;
      if (extension.equalsIgnoreCase("xml")) return "text/xml";
      if (extension.equalsIgnoreCase("html")) return "text/html";
      if (extension.equalsIgnoreCase("gml")) return "text/gml";  // grafic markup language http://infosun.fmi.uni-passau.de/Graphlet/GML
      if (extension.equalsIgnoreCase("sgml")) return "text/sgml";
      if (extension.equalsIgnoreCase("gif")) return "image/gif";
      if (extension.equalsIgnoreCase("png")) return "image/png";
      if (extension.equalsIgnoreCase("jpeg")) return "image/jpeg";
      if (extension.equalsIgnoreCase("jpg")) return "image/jpg";
      if (extension.equalsIgnoreCase("pdf")) return "application/pdf";
      if (extension.equalsIgnoreCase("rtf")) return "text/rtf";
      return defaultVal;
   }


   /**
    * Read a file into <code>String</code>.
    * @param fileName Complete name of file
    * @return ASCII data from the file<br />
    *         null on error
    */
   public static final String readAsciiFile(String fileName) throws XmlBlasterException
   {
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
   public static final String readAsciiFile(String parent, String child) throws XmlBlasterException
   {
      byte[] bb = FileUtil.readFile(parent, child);
      if (bb == null) return null;
      return new String(bb);
   }


   /**
    * Read a file into <code>byte[]</code>.
    * @param fileName Complete name of file
    * @return data from the file<br />
    *         null on error
    */
   public static final byte[] readFile(String fileName) throws XmlBlasterException
   {
      return readFile(null, fileName);
   }


   /**
    * Read a file into <code>byte[]</code>.
    * <br><b>Example:</b><br>
    *    <code>byte[] data=FileUtil.readFile("/tmp/hello");</code>
    * @param parent Path to the file
    * @param fileName name of file
    * @return
    *       data from the file<br />
    *       null on error
    */
   public static final byte[] readFile(String parent, String fileName) throws XmlBlasterException
   {
      byte[] fileBlob = null;
      if (Log.TRACE) Log.trace(ME, "Accessing given file " + fileName + " ...");

      File f = new File(parent, fileName);
      if (!f.exists()) {
         Log.error(ME, "Sorry, can't find file " + fileName);
         throw new XmlBlasterException(ME, "Sorry, can't find file " + fileName);
      }
      if (!f.isFile() || f.length() < 1) {
         Log.error(ME, "Sorry, doesn't seem to be a file " + fileName + " or is empty");
         throw new XmlBlasterException(ME, "Sorry, doesn't seem to be a file " + fileName + " or is empty");
      }
      if (!f.canRead()) {
         Log.error(ME, "Sorry, no access permissions for file " + fileName);
         throw new XmlBlasterException(ME, "Sorry, no access permissions for file " + fileName);
      }

      FileInputStream from = null;
      try
      {
         from = new FileInputStream( f );
         fileBlob = new byte[(int)f.length()];
         int bytes_read = from.read(fileBlob);
         if (bytes_read != f.length()) {
            Log.error(ME, "File read error in " + fileName + ": Excpected " + f.length() + " bytes, but only found " + bytes_read + "bytes");
            throw new XmlBlasterException(ME, "File read error in " + fileName + ": Excpected " + f.length() + " bytes, but only found " + bytes_read + "bytes");
         }
      }
      catch (FileNotFoundException e) {
         Log.error(ME, e.toString());
         throw new XmlBlasterException(ME, e.toString());
      }
      catch (IOException e2) {
         Log.error(ME, e2.toString());
         throw new XmlBlasterException(ME, e2.toString());
      }
      finally {
         if (from != null) try { from.close(); } catch (IOException e) { ; }
      }
      if (f.length() != fileBlob.length) {
         Log.error(ME, "Read file " + fileName + " with size=" + f.length() + " but only got " + fileBlob.length + " bytes");
         throw new XmlBlasterException(ME, "Read file " + fileName + " with size=" + f.length() + " but only got " + fileBlob.length + " bytes");
      }
      else {
         if (Log.TRACE) Log.trace(ME, "Successfully read file " + fileName + " with size=" + fileBlob.length);
      }
      return fileBlob;
   }


   /**
    * Delete a file.
    * @param parent Path to the file
    * @param fileName name of file
    */
   public static final void deleteFile(String parent, String fileName)
   {
      File f = new File(parent, fileName);
      if (f.exists())
         f.delete();
   }


   /**
    * Write data from <code>byte[]</code> into a file.
    * <p />
    * @param outName  name of file including path
    * @param arr      data
    */
   public static final void writeFile(String outName, byte[] arr) throws XmlBlasterException
   {
      writeFile(null, outName, arr);
   }


   /**
    * Write data from <code>byte[]</code> into a file.
    * @param parent   the path
    * @param child    the name
    * @param arr      data
    */
   public static final void writeFile(String parent, String child, byte[] arr) throws XmlBlasterException
   {
      try {
         File to_file = new File(parent, child);
         FileOutputStream to = new FileOutputStream(to_file);
         to.write(arr);
         to.close();
         if (Log.TRACE) Log.trace(ME, "Wrote " + to_file.toString() + " with size = " + arr.length + " bytes.");
      }
      catch (Exception e) {
         Log.error(ME, "Can't write file " + e.toString());
         throw new XmlBlasterException(ME, "Can't write file " + e.toString());
      }
   }


   /**
    * Write data from <code>StringBuffer</code> into a file.
    * @param outName  name of file including path
    * @param str      data
    */
   public static final void writeFile(String parent, String child, String str) throws XmlBlasterException
   {
      writeFile(parent, child, str.getBytes() );
   }


   /**
    * Write data from <code>StringBuffer</code> into a file.
    * @param outName  name of file including path
    * @param str      data
    */
   public static final void writeFile(String name, String str) throws XmlBlasterException
   {
      writeFile(null, name, str.getBytes() );
   }


   /**
    * Append data from into a file.
    * @param outName  name of file including path
    * @param str      Text
    */
   public static final void appendToFile(String outName, String str) throws XmlBlasterException
   {
      try {
         boolean append = true;
         FileOutputStream to = new FileOutputStream(outName, true);
         to.write(str.getBytes());
         to.close();
      }
      catch (Exception e) {
         Log.error(ME, "Can't write file " + e.toString());
         throw new XmlBlasterException(ME, "Can't write file " + e.toString());
      }
   }


   /**
    * Rename a file.
    * <p />
    * @param oldName
    * @param newName
    * @return true if succeeded
    */
   public static boolean rename(String oldName, String newName)
   {
      File f_old = new File(oldName);
      File f_new = new File(newName);
      boolean ret = f_old.renameTo(f_new);
      return ret;
   }


   /**
    * Invoke for testing: jaco org.xmlBlaster.util.FileUtil
    */
   public static void main(String args[])
   {
      String ME = "FileUtil";
      String name = "Hello.txt";
      Log.info(ME, name + " -> <"  + FileUtil.getBody(name) + "> and <" + FileUtil.getExtension(name) + ">");
      name = ".";
      Log.info(ME, name + " -> <"  + FileUtil.getBody(name) + "> and <" + FileUtil.getExtension(name) + ">");
      name = "Hello";
      Log.info(ME, name + " -> <"  + FileUtil.getBody(name) + "> and <" + FileUtil.getExtension(name) + ">");
      name = "....";
      Log.info(ME, name + " -> <"  + FileUtil.getBody(name) + "> and <" + FileUtil.getExtension(name) + ">");
      name = ".xml";
      Log.info(ME, name + " -> <"  + FileUtil.getBody(name) + "> and <" + FileUtil.getExtension(name) + ">");
      name = "";
      Log.info(ME, name + " -> <"  + FileUtil.getBody(name) + "> and <" + FileUtil.getExtension(name) + ">");
      name = null;
      Log.info(ME, name + " -> <"  + FileUtil.getBody(name) + "> and <" + FileUtil.getExtension(name) + ">");

      name = File.separator + "home" + File.separator + "joe" + File.separator + "Hello.txt";
      Log.info(ME, name + " -> <"  + FileUtil.getBody(name) + "> and <" + FileUtil.getExtension(name) + ">");
      name = File.separator + File.separator + File.separator;
      Log.info(ME, name + " -> <"  + FileUtil.getBody(name) + "> and <" + FileUtil.getExtension(name) + ">");
   }
}


