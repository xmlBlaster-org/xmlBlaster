/*------------------------------------------------------------------------------
Name:      FileUtil.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: FileUtil.java,v 1.6 2000/01/19 22:21:41 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.io.*;


/**
 * Some helper methods for file handling.
 * May be used for test clients etc.
 */
public class FileUtil
{
   private final static String ME = "FileUtil";


   /**
    * Read a file into <code>String</code>.
    * <p />
    * All error handling and reporting is done by this method<br>
    * Nice function for testing
    * <br><b>Example:</b><br>
    *    <code>String data=FileUtil.readAsciiFile("/tmp/hello");</code>
    * @param fileName name of file
    * @return String
    *       ASCII data from the file<br />
    *       null on error
    */
   public static final String readAsciiFile(String fileName)
   {
      byte[] bb = FileUtil.readFile(fileName);
      if (bb == null) return null;
      return new String(bb);
   }


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
    * Read a file into <code>byte[]</code>.
    * <p />
    * All error handling and reporting is done by this method<br>
    * Nice function for testing
    * <br><b>Example:</b><br>
    *    <code>byte[] data=FileUtil.readFile("/tmp/hello");</code>
    * @param fileName name of file
    * @return
    *       data from the file<br />
    *       null on error
    */
   public static final byte[] readFile(String fileName)
   {
      byte[] fileBlob = null;
      if (Log.TRACE) Log.trace(ME, "Accessing given file " + fileName + " ...");

      File f = new File(fileName);
      if (!f.exists()) {
         Log.error(ME, "Sorry, can't find file " + fileName);
         return fileBlob;
      }
      if (!f.isFile() || f.length() < 1) {
         Log.error(ME, "Sorry, doesn't seem to be a file " + fileName + " or is empty");
         return fileBlob;
      }
      if (!f.canRead()) {
         if (Log.TRACE) Log.trace(ME, "Sorry, no access permissions for file " + fileName);
         return fileBlob;
      }

      FileInputStream from = null;
      try
      {
         from = new FileInputStream( f );
         fileBlob = new byte[(int)f.length()];
         int bytes_read = from.read(fileBlob);
         if (bytes_read != f.length()) {
            Log.error(ME, "File read error in " + fileName + ": Excpected " + f.length() + " bytes, but only found " + bytes_read + "bytes");
            return fileBlob;
         }
      }
      catch (FileNotFoundException e) {
         Log.error(ME, e.toString());
         return fileBlob;
      }
      catch (IOException e2) {
         Log.error(ME, e2.toString());
         return fileBlob;
      }
      finally {
         if (from != null) try { from.close(); } catch (IOException e) { ; }
      }
      if (f.length() != fileBlob.length)
         Log.info(ME, "Read file " + fileName + " with size=" + f.length() + " but only got " + fileBlob.length + " bytes");
      else {
         if (Log.TRACE) Log.trace(ME, "Successfully read file " + fileName + " with size=" + fileBlob.length);
      }
      return fileBlob;
   }


   /**
    * Write data from <code>byte[]</code> into a file.
    * <p />
    * All error handling and reporting is done by this method<br>
    * Nice function for testing
    * <br><b>Example:</b><br>
    *    <code>FileUtil.writeFile(myBytes, "/tmp/hello");</code>
    * @param outName  name of file including path
    * @param arr      data
    * @return
    *       <code>true</code> successfully wrote data
    *       <code>false</code> error
    */
   public static final boolean writeFile(String outName, byte[] arr)
   {
      try {
         File to_file = new File(outName);
         FileOutputStream to = new FileOutputStream(to_file);
         to.write(arr);
         to.close();
         if (Log.TRACE) Log.trace(ME, "Wrote " + outName + " with size = " + arr.length + " bytes.");
         return true;
      }
      catch (Exception e) {
         Log.error(ME, "Can't write file " + e.toString());
      }
      return false;
   }


   /**
    * Write data from <code>StringBuffer</code> into a file.
    * <p />
    * All error handling and reporting is done by this method<br>
    * Nice function for testing
    * <br><b>Example:</b><br>
    *    <code>FileUtil.writeFile(myBytes, "/tmp/hello");</code>
    * @param outName  name of file including path
    * @param arr      data
    * @return
    *       <code>true</code> successfully wrote data
    *       <code>false</code> error
    */
   public static final boolean writeFile(String outName, StringBuffer arr)
   {
      return writeFile(outName, arr.toString().getBytes() );
   }


   /**
    * Append data from into a file.
    * <p />
    * Nice function for testing
    * @param outName  name of file including path
    * @param str      Text
    * @return
    *       <code>true</code> successfully wrote data
    *       <code>false</code> error
    */
   public static final boolean appendToFile(String outName, String str)
   {
      try {
         boolean append = true;
         FileOutputStream to = new FileOutputStream(outName, true);
         to.write(str.getBytes());
         to.close();
         return true;
      }
      catch (Exception e) {
         Log.error(ME, "Can't write file " + e.toString());
      }
      return false;
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


