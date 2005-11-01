/*------------------------------------------------------------------------------
Name:      FileWriterCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE filep
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.filewriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

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
   }
   
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
