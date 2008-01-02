/*------------------------------------------------------------------------------
Name:      FileWriterCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.filewriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.ContribConstants;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.filewatcher.FilenameFilter;
import org.xmlBlaster.jms.XBConnectionMetaData;
import org.xmlBlaster.jms.XBMessage;
import org.xmlBlaster.util.qos.ClientProperty;

/**
 * FileWriterCallback stores messages to the file system.
 *
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class FileWriterCallback implements I_Update, ContribConstants {

   private static Logger log = Logger.getLogger(FileWriterCallback.class.getName());
   private final static int BUF_SIZE = 300000;
   private String dirName;
   private String lockExtention;
   private File directory;
   private boolean overwrite;
   private String tmpDirName;
   private File tmpDirectory;
   /** if true it cleans up the chunks after processing, otherwise it leaves them. */
   private boolean keepDumpFiles;

   /**
    * Creates a callback
    * @param dirName The name of the directory where to store the files.
    * @param tmpDirName the name of the directory where to store the temporary files.
    * @param lockExtention The extention to use to lock the reading of the file. This is used if the
    * entries have to be retrieved by the filepoller. Until such a file exists, the entry is not
    * processed by the file poller.
    * @param overwrite if set to true it will overwrite existing files.
    * @throws Exception
    */
   public FileWriterCallback(String dirName, String tmpDirName, String lockExtention, boolean overwrite, boolean keepDumpFiles) throws Exception {
      this.dirName = dirName;
      if (dirName == null)
         throw new Exception ("The directory where to store the files is null, can not continue");
      this.lockExtention = lockExtention;
      this.directory = new File(this.dirName);
      if (this.directory == null)
         throw new Exception("The created directory '" + dirName + "' resulted in a null File object");
      if (!this.directory.exists()) {
         if (this.directory.mkdir())
            throw new Exception("The directory '" + dirName + "' could not be created");
      }
      if (!this.directory.canWrite())
         throw new Exception("Can not write to the directory '" + dirName + "'");
      if (!this.directory.isDirectory())
         throw new Exception("'" + dirName + "' is not a directory, can not use it to store files");
      this.tmpDirName = tmpDirName;

      this.tmpDirectory = new File(this.tmpDirName);

      if (this.tmpDirectory == null)
         throw new Exception("The created temporary directory '" + tmpDirName + "' resulted in a null File object");
      if (!this.tmpDirectory.exists()) {
         if (!this.tmpDirectory.mkdir())
            throw new Exception("The temporary directory '" + tmpDirName + "' could not be created");
      }
      if (!this.tmpDirectory.canWrite())
         throw new Exception("Can not write to the temporary directory '" + tmpDirName + "'");
      if (!this.tmpDirectory.isDirectory())
         throw new Exception("The temporary '" + tmpDirName + "' is not a directory, can not use it to store files");

      this.overwrite = overwrite;
      this.keepDumpFiles = keepDumpFiles;
   }


   private static void storeChunk(File tmpDir, String fileName, long chunkNumber, char sep, boolean overwrite, InputStream is) throws Exception {
      fileName = fileName + sep + chunkNumber;
      File file = new File(tmpDir, fileName);
      if (file == null)
         throw new Exception("the file for '" + fileName + "' was null");
      if (file.exists()) {
         if (file.isDirectory())
            throw new Exception("'" + fileName + "' exists already and is a directory, can not continue");
         if (overwrite) {
            log.warning("file '" + fileName + "' exists already. Will overwrite it.");
         }
         else {
            log.warning("file '" + fileName + "' exists already. Will not overwrite it.");
            return;
         }
      }
      try {
         log.info("storing file '" + fileName + "' on directory '" + tmpDir.getName() + "' and chunk number '" + chunkNumber + "'");

         FileOutputStream fos = new FileOutputStream(file);
         int ret = 0;
         byte[] buf = new byte[BUF_SIZE];
         while ( (ret=is.read(buf)) > -1) {
            fos.write(buf, 0, ret);
         }
         // fos.write(content);
         fos.close();
      }
      catch (IOException ex) {
         throw new Exception("update: an exception occured when storing the file '" + fileName + "'", ex);
      }
   }

   /**
    * Returns the number used as the postfix of this file. If there is no such number, -1 is
    * returned. If the content is null or empty, an exception is thrown. If there is a postfix
    * but it is not a number, a -1 is returned.
    *
    * @param filename the filename to check.
    * @param prefix the prefix of the filename (the part before the number)
    * @return a long specifying the postfix number of the file.
    * @throws Exception if the content is null or empty.
    */
   private long extractNumberPostfixFromFile(String filename, String prefix) throws Exception {
      if (filename == null || filename.trim().length() < 1)
         throw new Exception("The filename is empty");
      int prefixLength = prefix.length();
      if (filename.startsWith(prefix)) {
         String postfix = filename.substring(prefixLength).trim();
         if (postfix.length() < 1)
            return -1L;
         try {
            return Long.parseLong(postfix);
         }
         catch (Throwable ex) {
            return -1L;
         }
      }
      return -1L;
   }

   /**
    * The filename prefix for which to retrieve all chunks.
    * @param fileName
    * @param sep
    * @return
    * @throws Exception
    */
   private File[] getChunkFilenames(String fileName, char sep) throws Exception {
      // scan for all chunks:
      String prefix = fileName + sep;
      String expression = prefix + '*';
      File[] files = this.tmpDirectory.listFiles(new FilenameFilter(expression, false));

      if (files.length > 0) {
         TreeMap map = new TreeMap();
         for (int i=0; i < files.length; i++) {
            long postfix = extractNumberPostfixFromFile(files[i].getName(), prefix);
            if (postfix > -1L) {
               if (files[i].exists() && files[i].canRead() && files[i].isFile())
                  map.put(new Long(postfix), files[i]);
            }
         }
         File[] ret = new File[map.size()];
         Iterator iter = map.keySet().iterator();
         int i = 0;
         while (iter.hasNext()) {
            ret[i] = (File)map.get(iter.next());
            i++;
         }
         return ret;
      }
      return new File[0];
   }

   /**
    * Puts all chunks stored in separate files together in one single one.
    *
    * @param fileName The name of the complete (destination) file.
    * @param expectedChunks The number of expected chunks. If the number of chunks found is
    * bigger, the exceeding ones are ignored and a warning is written to the logs. If the number
    * is too low it checks if the file already exists. If it exists a warning is written and
    * the method returns, otherwise an exception is thrown. Keeps also track of locking the file.
    * @param lastContent the content of the last chunk of the message (can be null).
    * @param isCompleteMsg is true if the content of the message is contained in one single
    * chunk (i.e. if the message is not chunked).
    * @throws Exception If an error occurs when writing / reading the files. This method tries
    * to clean up the destination file in case of an exception when writing.
    */
   private void putAllChunksTogether(String fileName, String subDir, long expectedChunks, InputStream is, boolean isCompleteMsg) throws Exception {
      File dir = null;
      if (subDir == null)
         dir = this.directory;
      else {
         List list = new ArrayList();
         
         // check if the directory exists, if not go back recursively until it exists.
         dir = new File(this.directory, subDir);
         File tmp = dir;
         while (tmp != null && !tmp.exists()) {
            list.add(0, tmp.getName());
            tmp = tmp.getParentFile();
         }
         if (tmp == null)
            throw new Exception("Directory '" + subDir + "' not found as subdirectory of '" + directory.getAbsolutePath() + "'");
         if (!tmp.isDirectory())
            throw new Exception("File '" + subDir + "' in '" + directory.getAbsolutePath() + "' is not a directory");
         if (!tmp.canWrite())
            throw new Exception("Can not write in directory '" + subDir + "' in '" + directory.getAbsolutePath() + "'.");
         
         for (int i=0; i < list.size(); i++) {
            tmp = new File(tmp, (String)list.get(i));
            tmp.mkdir();
         }
         dir = tmp;
      }
      
      File file = new File(dir, fileName);
      if (file == null)
         throw new Exception("the file for '" + fileName + "' was null");
      if (file.exists()) {
         if (file.isDirectory())
            throw new Exception("can not write on '" + fileName + "' in directory '" + dir + "' since it is a directory");
         if (!this.overwrite) {
            log.warning("file '" + fileName + "' in directory '" + dir + "' exists already. Will keep the old one");
            return;
         }
         else
            log.warning("file '" + fileName + "' in directory '" + dir + "' exists already. Will overwrite it unless the chunks are not there anymore");
      }
      try {
         File lock = null;
         String lockName = null;
         if (this.lockExtention != null) {
            lockName = fileName + this.lockExtention;
            lock = new File(dir, lockName);
            lock.createNewFile();
         }
         log.info("storing file '" + fileName + "' on directory '" + dir + "'");

         File[] files = null;
         long numChunks = 0L;
         if (!isCompleteMsg) {
            files = getChunkFilenames(fileName, '.'); // retrieves the chunks in correct order
            if (files.length > expectedChunks)
               log.warning("Too many chunks belonging to '" + fileName + "' are found. They are '" + files.length + "' but should be '" + expectedChunks + "'");
            else if (files.length < expectedChunks) {
               if (file.exists()) {
                  log.warning("The number of chunks is '" + files.length + "' which is less than the expected '" + expectedChunks + "' but the file '" + file.getAbsolutePath() + "' exists. So we will use the exisiting file (the chunks where probably already deleted)");
                  return;
               }
               else
                  throw new Exception("Too few chunks belonging to '" + fileName + "' are found. They are '" + files.length + "' but should be '" + expectedChunks + "'");
            }
            numChunks = files.length > expectedChunks ? expectedChunks : files.length;
         }
         // put all chunks together in one single file
         int bufSize = BUF_SIZE;
         byte[] buf = new byte[bufSize];

         FileOutputStream fos = null;
         try {
            fos = new FileOutputStream(file);
            for (int i=0; i < numChunks; i++) {
               log.info("adding chunk '" + i + "' to file '" + fileName + "'");
               FileInputStream fis = new FileInputStream(files[i]);
               int ret = 0;
               while ( (ret=fis.read(buf)) > -1) {
                  fos.write(buf, 0, ret);
               }
               fis.close();
            }

            if (is != null) {
               int ret = 0;
               while ( (ret=is.read(buf)) > -1) {
                  fos.write(buf, 0, ret);
               }
               is.close();
            }
            /*
            if (lastContent != null && lastContent.length != 0)
               fos.write(lastContent);
            */
            fos.close();
         }
         catch (Throwable ex) {
            if (fos != null) {
               if (file.exists()) {
                  if (file.canWrite()) {
                     if (!file.delete())
                        log.warning("An exception occured when putting all chunks together for '" + fileName + "' but could not delete the file for an unknown reason. The original exception was '" + ex.getMessage() + "'");
                  }
                  else
                     log.warning("An exception occured when putting all chunks together for '" + fileName + "' but could not delete the file since it is not writable. The original exception was '" + ex.getMessage() + "'");
               }
            }
            if (ex instanceof Exception)
               throw (Exception)ex;
            else
               throw new Exception(ex);
         }

         // clean up all chunks since complete file created
         if (!isCompleteMsg && !this.keepDumpFiles) {
            for (int i=0; i < files.length; i++)
               deleteFile(files[i]);
         }

         if (lock != null) {
            boolean deleted = lock.delete();
            if (!deleted)
               throw new Exception("can not delete lock file '" + lockName + "' in directory '" + dir + "'");
         }
      }
      catch (IOException ex) {
         throw new Exception("update: an exception occured when storing the file '" + fileName + "'", ex);
      }
   }

   /**
    * Deletes the specified file from the file system.
    * Never throws Exceptions
    * @param file the file object to be deleted.
    * @return if it can not delete the file it returns false, true otherwise.
    */
   private final static boolean deleteFile(File file) {
      if (file.exists()) {
         if (file.canWrite()) {
            try {
               if (!file.delete())
                  log.warning("The file '" + file.getName() + "' could not be deleted (unknown reason)");
               else
                  return true;
            }
            catch (Throwable ex) {
               log.warning("An Exception occured when trying to delete file '" + file.getName() + "': " + ex.getMessage());
            }
         }
         else
            log.warning("The file '" + file.getName() + "' could not be deleted since it does not exist");
      }
      else
         log.warning("The file '" + file.getName() + "' could not be deleted since it does not exist");
      return false;
   }


   public void update(String topic, InputStream is, Map attrMap) throws Exception {
      String filename = null;
      String subDir = null;
      boolean isLastMsg = true;
      String exMsg = null;
      long chunkCount = 0L;

      if (attrMap != null) {
         ClientProperty prop = (ClientProperty)attrMap.get(FILENAME_ATTR);
         if (prop == null) {
            prop = (ClientProperty)attrMap.get(FILENAME_ATTR_OLD_FASHION);
         }
         if (prop != null)
            filename = prop.getStringValue();
         if (filename == null || filename.length() < 1) {
            prop = (ClientProperty)attrMap.get(TIMESTAMP_ATTR);
            if (prop != null) {
               String timestamp = prop.getStringValue();
               filename = "xbl" + timestamp + ".msg";
            }
            else
               throw new Exception("update: the message '" + topic + "' should contain either the filename or the timestamp in the properties, but none was found. Can not create a filename to store the data on.");
         }
         prop = (ClientProperty)attrMap.get(SUBDIR_ATTR);
         if (prop != null) {
            subDir = prop.getStringValue();
         }

         prop = XBMessage.get(XBConnectionMetaData.JMSX_GROUP_SEQ, attrMap);
         if (prop != null) {
            isLastMsg = false;
            chunkCount = prop.getLongValue();
            prop = XBMessage.get(XBConnectionMetaData.JMSX_GROUP_EOF, attrMap);
            if (prop != null) {
               isLastMsg = prop.getBooleanValue();
               prop = XBMessage.get(XBConnectionMetaData.JMSX_GROUP_EX, attrMap);
               if (prop != null)
                  exMsg = prop.getStringValue();
            }
         }
         else
            isLastMsg = true;
      }
      if (filename == null) {
         // fileName = topic + (new Timestamp()).getTimestamp() + ".msg";
         filename = topic;
         log.warning("The message did not contain any filename nor timestamp. Will write to '" + filename + "'");
      }
      log.fine("storing file '" + filename + "' on directory '" + this.directory.getName() + "'");

      boolean isCompleteMsg = isLastMsg && chunkCount == 0L;
      if (exMsg == null) { // no exception
         if (isLastMsg)
            putAllChunksTogether(filename, subDir, chunkCount, is, isCompleteMsg);
         else
            storeChunk(this.tmpDirectory, filename, chunkCount, '.', this.overwrite, is);
      }
      else if (!isCompleteMsg) { // clean up old chunks
         File[] files = getChunkFilenames(filename, '.'); // retrieves the chunks in correct order
         for (int i=0; i < files.length; i++)
            deleteFile(files[i]);
      }
   }

}
