/*------------------------------------------------------------------------------
Name:      FileWriterCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE filep
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.filewriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.xmlBlaster.client.filepoller.FilenameFilter;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.ClientProperty;

/**
 * FileWriterCallback stores messages to the file system.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class FileWriterCallback implements I_Update {
   private static Logger log = Logger.getLogger(FileWriterCallback.class.getName());
   private String dirName;
   private String lockExtention;
   private File directory;
   private boolean overwrite;
   private String tmpDir;
   
   /**
    * Creates a callback
    * @param dirName The name of the directory where to store the files.
    * @param lockExtention The extention to use to lock the reading of the file. This is used if the
    * entries have to be retrieved by the filepoller. Until such a file exists, the entry is not 
    * processed by the file poller.
    * @param overwrite if set to true it will overwrite existing files.
    * @throws Exception
    */
   public FileWriterCallback(String dirName, String lockExtention, boolean overwrite) throws Exception {
      this.dirName = dirName;
      if (dirName == null)
         throw new Exception ("The directory where to store the files is null, can not continue");
      this.lockExtention = lockExtention;
      this.directory = new File(this.dirName);
      if (this.directory == null)
         throw new Exception("The created directory '" + dirName + "' resulted in a null File object");
      if (!this.directory.exists())
         throw new Exception(" '" + dirName + "'");
      if (!this.directory.canWrite())
         throw new Exception("Can not write to the directory '" + dirName + "'");
      if (!this.directory.isDirectory())
         throw new Exception("'" + dirName + "' is not a directory, can not use it to store files");
      this.tmpDir = "tmpChunks";
      File subDir = new File(this.directory, this.tmpDir);
      boolean ret = subDir.mkdir();
      if (!ret)
         throw new Exception("Could not create directory 'tmpChunks' as a subdirectory of '" + this.directory + "'");
      this.overwrite = overwrite;
   }

   
   private static void storeChunk(String tmpDir, String fileName, long chunkNumber, char sep, boolean overwrite, byte[] content) throws Exception {
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
         log.info("storing file '" + fileName + "' on directory '" + tmpDir + "', size: " + content.length + " bytes");
         
         FileOutputStream fos = new FileOutputStream(file);
         fos.write(content);
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
      File dir = new File(this.tmpDir);
      if (dir == null)
         throw new Exception("the directory where the chunks should be stored does not exist");
      if (!dir.isDirectory())
         throw new Exception("the directory '" + this.tmpDir + "' is not a directory");

      // scan for all chunks:
      String prefix = fileName + sep;
      String expression = prefix + '*';
      File[] files = dir.listFiles(new FilenameFilter(expression, false));
      
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

   
   private void putAllChunksTogheter(String fileName) throws Exception {
      File file = new File(this.directory, fileName);
      if (file == null)
         throw new Exception("the file for '" + fileName + "' was null");
      if (file.exists()) {
         if (file.isDirectory())
            throw new Exception("can not write on '" + fileName + "' in directory '" + this.directory + "' since it is a directory");
         if (!this.overwrite) {
            log.warning("file '" + fileName + "' in directory '" + this.directory + "' exists already. Will keep the old one");
            return;
         }
         else
            log.warning("file '" + fileName + "' in directory '" + this.directory + "' exists already. Will overwrite it");
      }
      try {
         File lock = null;
         String lockName = null;
         if (this.lockExtention != null) {
            lockName = fileName + this.lockExtention;
            lock = new File(this.directory, lockName);
            lock.createNewFile();
         }
         log.info("storing file '" + fileName + "' on directory '" + this.directory + "'");
         
         // retrieve all chunks here
         File[] files = getChunkFilenames(fileName, '.');

         // FIXME add here the code to chunk all togheter
         
         if (lock != null) {
            boolean deleted = lock.delete();
            if (!deleted)
               throw new Exception("can not delete lock file '" + lockName + "' in directory '" + this.directory + "'");
         }
      }
      catch (IOException ex) {
         throw new Exception("update: an exception occured when storing the file '" + fileName + "'", ex);
      }
      
      
   }
   
   public void updateNEW(String topic, byte[] content, Map attrMap) throws Exception {
      String fileName = null;
      if (attrMap != null) {
         ClientProperty prop = (ClientProperty)attrMap.get("_filename"); 
         if (prop != null)
            fileName = prop.getStringValue(); 
      }
      if (fileName == null) {
         ClientProperty prop = (ClientProperty)attrMap.get("_timestamp"); 
         if (prop != null) {
            String timestamp = prop.getStringValue();
            fileName = "xbl" + timestamp + ".msg";
         }
         else
            throw new Exception("update: the message '" + topic + "' should contain either the filename or the timestamp in the properties, but none was found. Can not create a filename to store the data on.");
      }
      log.fine("storing file '" + fileName + "' on directory '" + this.directory + "', size: " + content.length + " bytes");

      boolean isLastMsg = false;
      String exMsg = null;
      long chunkCount = 0L;
      ClientProperty prop = (ClientProperty)attrMap.get(Constants.CHUNK_SEQ_NUM);
      if (prop != null) {
         chunkCount = prop.getLongValue();
         prop = (ClientProperty)attrMap.get(Constants.CHUNK_EOF);
         if (prop != null) {
            isLastMsg = prop.getBooleanValue();
            prop = (ClientProperty)attrMap.get(Constants.CHUNK_EXCEPTION);
            if (prop != null)
               exMsg = prop.getStringValue();
         }
      }
      else
         isLastMsg = true;
      
      storeChunk(tmpDir, fileName, chunkCount, '.', this.overwrite, content);
      if (isLastMsg) {
         // put all chunks together
      }
      
      
      File file = new File(this.directory, fileName);
      if (file == null)
         throw new Exception("the file for '" + fileName + "' was null");
      if (file.exists()) {
         if (file.isDirectory())
            throw new Exception("can not write on '" + fileName + "' in directory '" + this.directory + "' since it is a directory");
         if (chunkCount == 0L && !this.overwrite)
            throw new Exception("can not write on '" + fileName + "' in directory '" + this.directory + "' since it exists already and the 'overwrite' flag is set to 'false'");
      }
      try {
         File lock = null;
         String lockName = null;
         if (this.lockExtention != null && chunkCount == 0L) {
            lockName = fileName + this.lockExtention;
            lock = new File(this.directory, lockName);
            lock.createNewFile();
         }

         log.info("storing file '" + fileName + "' on directory '" + this.directory + "', size: " + content.length + " bytes msgNr.='" + chunkCount + "' isLastMsg='" + isLastMsg + "'");
         
         if (exMsg == null) {
            boolean doAppend = !(chunkCount == 0L);
            FileOutputStream fos = new FileOutputStream(file, doAppend);
            fos.write(content);
            fos.close();
         }
         else {
            log.severe("An exception occured '" + exMsg + "' will delete the file and interrupt initial update");
            file.delete();
         }
         
         if (lock != null && (isLastMsg || exMsg != null)) {
            boolean deleted = lock.delete();
            if (!deleted)
               throw new Exception("can not delete lock file '" + lockName + "' in directory '" + this.directory + "'");
         }
      }
      catch (IOException ex) {
         throw new Exception("update: an exception occured when storing the file '" + fileName + "'", ex);
      }
   }

   /**
    * @deprecated
    */
   public void update(String topic, byte[] content, Map attrMap) throws Exception {
      String fileName = null;
      if (attrMap != null) {
         ClientProperty prop = (ClientProperty)attrMap.get("_filename"); 
         if (prop != null)
            fileName = prop.getStringValue(); 
      }
      if (fileName == null) {
         ClientProperty prop = (ClientProperty)attrMap.get("_timestamp"); 
         if (prop != null) {
            String timestamp = prop.getStringValue();
            fileName = "xbl" + timestamp + ".msg";
         }
         else
            throw new Exception("update: the message '" + topic + "' should contain either the filename or the timestamp in the properties, but none was found. Can not create a filename to store the data on.");
      }
      log.fine("storing file '" + fileName + "' on directory '" + this.directory + "', size: " + content.length + " bytes");

      boolean isLastMsg = false;
      String exMsg = null;
      long chunkCount = 0L;
      ClientProperty prop = (ClientProperty)attrMap.get(Constants.CHUNK_SEQ_NUM);
      if (prop != null) {
         chunkCount = prop.getLongValue();
         prop = (ClientProperty)attrMap.get(Constants.CHUNK_EOF);
         if (prop != null) {
            isLastMsg = prop.getBooleanValue();
            prop = (ClientProperty)attrMap.get(Constants.CHUNK_EXCEPTION);
            if (prop != null)
               exMsg = prop.getStringValue();
         }
      }
      
      File file = new File(this.directory, fileName);
      if (file == null)
         throw new Exception("the file for '" + fileName + "' was null");
      if (file.exists()) {
         if (file.isDirectory())
            throw new Exception("can not write on '" + fileName + "' in directory '" + this.directory + "' since it is a directory");
         if (chunkCount == 0L && !this.overwrite)
            throw new Exception("can not write on '" + fileName + "' in directory '" + this.directory + "' since it exists already and the 'overwrite' flag is set to 'false'");
      }
      try {
         File lock = null;
         String lockName = null;
         if (this.lockExtention != null && chunkCount == 0L) {
            lockName = fileName + this.lockExtention;
            lock = new File(this.directory, lockName);
            lock.createNewFile();
         }

         log.info("storing file '" + fileName + "' on directory '" + this.directory + "', size: " + content.length + " bytes msgNr.='" + chunkCount + "' isLastMsg='" + isLastMsg + "'");
         
         if (exMsg == null) {
            boolean doAppend = !(chunkCount == 0L);
            FileOutputStream fos = new FileOutputStream(file, doAppend);
            fos.write(content);
            fos.close();
         }
         else {
            log.severe("An exception occured '" + exMsg + "' will delete the file and interrupt initial update");
            file.delete();
         }
         
         if (lock != null && (isLastMsg || exMsg != null)) {
            boolean deleted = lock.delete();
            if (!deleted)
               throw new Exception("can not delete lock file '" + lockName + "' in directory '" + this.directory + "'");
         }
      }
      catch (IOException ex) {
         throw new Exception("update: an exception occured when storing the file '" + fileName + "'", ex);
      }
   }
   
}
