/*------------------------------------------------------------------------------
Name:      DirectoryManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.filepoller;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

/**
 * DirectoryManager
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class DirectoryManager {
   private String ME = "DirectoryManager";
   private Global global;
   private LogChannel log;
   private long maximumFileSize;
   private long delaySinceLastFileChange;
   private long warnOnEmptyFileDelay;

   private File directory;
   /** this is the the directory where files are moved to after successful publishing. If null they will be erased */
   private File sentDirectory;
   /** this is the name of the directory where files are moved if they could not be send (too big) */
   private File discardedDirectory;
   
   private Map directoryEntries; 
   /** all files matching the filter will be processed. Null means everything will be processed */
   private FileFilter fileFilter;
   /** if set, then files will only be published when the lock-file has been removed. */
   private String lockExtention;
   private Set lockFiles;
   
   public DirectoryManager(Global global, String name, String directoryName, long maximumFileSize, long delaySinceLastFileChange, long warnOnEmptyFileDelay, String filter, String sent, String discarded, String lockExtention) throws XmlBlasterException {
      ME += "-" + name;
      this.global = global;
      if (filter != null)
         this.fileFilter = new FilenameFilter(this.global, filter);
      this.log = this.global.getLog("filepoller");
      this.maximumFileSize = maximumFileSize; 
      this.delaySinceLastFileChange = delaySinceLastFileChange;
      this.warnOnEmptyFileDelay = warnOnEmptyFileDelay;
      this.directoryEntries = new HashMap();
      this.directory = initDirectory(null, "directory", directoryName);
      if (this.directory == null)
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_NULLPOINTER, ME + ".constructor", "the directory '" + directoryName + "' is null");
      this.sentDirectory = initDirectory(this.directory, "sent", sent);
      this.discardedDirectory = initDirectory(this.directory, "discarded", discarded);
      this.lockExtention = lockExtention;
      this.lockFiles = new HashSet();
   }

   /**
    * Returns the specified directory or null or if needed it will create one
    * @param propName
    * @param dirName
    * @return
    * @throws XmlBlasterException
    */
   private File initDirectory(File parent, String propName, String dirName) throws XmlBlasterException {
      File dir = null;
      if (dirName != null) {
         File tmp = new File(dirName);
         if (tmp.isAbsolute() || parent == null) {
            dir = new File(dirName);
         }
         else {
            dir = new File(parent, dirName);
         }
         if (!dir.exists()) {
            String absDirName  = null; 
            try {
               absDirName = dir.getCanonicalPath();
            }
            catch (IOException ex) {
               absDirName = dir.getAbsolutePath();
            }
            this.log.info(ME, "Constructor: directory '" + absDirName + "' does not yet exist. I will create it");
            boolean ret = dir.mkdir();
            if (!ret)
               throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME, "could not create directory '" + absDirName + "'");
         }
         if (!dir.isDirectory()) {
            throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME, "'" + dir.getAbsolutePath() + "' is not a directory");
         }
         if (!dir.canRead())
            throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".constructor", "no rights to read from the directory '" + dir.getAbsolutePath() + "'");
         if (!dir.canWrite())
            throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".constructor", "no rights to write to the directory '" + dir.getAbsolutePath() + "'");
      }
      else {
         this.log.info(ME, "Constructor: the '" + propName + "' property is not set. Instead of moving concerned entries they will be deleted");
      }
      return dir;
   }
   
   /**
    * Retrieves all files from the specified directory
    * @param directory
    * @return never returns null.
    * @throws XmlBlasterException
    */
   private Map getNewFiles(File directory) throws XmlBlasterException {
      if (this.lockExtention != null) // reset lockFile set
         this.lockFiles.clear();
      
      if (!directory.canRead())
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".scan", "I don't have rights to read from '" + directory.getName() + "'");
      if (!directory.canWrite())
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".scan", "I don't have rights to write to '" + directory.getName() + "'");
      File[] files = directory.listFiles(this.fileFilter);
      if (files == null || files.length == 0)
         return new HashMap();
      Map map = new HashMap(files.length);
      for (int i=0; i < files.length; i++) {
         try {
            String name = files[i].getCanonicalPath();
            if (files[i].isFile()) {
               if (this.lockExtention != null && name.endsWith(this.lockExtention)) {
                  int pos = name.length() - this.lockExtention.length();
                  String strippedName = name.substring(0, pos);
                  this.lockFiles.add(strippedName);
               }
               else {
                  map.put(name, files[i]);
               }
            }
         }
         catch (IOException ex) {
            throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".getNewFiles", " could not get the canonical name of file '" + files[i].getName() + "'");
         }
      }
      return map;
   }
   
   /**
    * Returns false if the info object is null, if the size is zero or
    * if it has not passed sufficient time since the last change.
    *  
    * @param info
    * @param currentTime
    * @return
    */
   private boolean isReady(FileInfo info, long currentTime) {
      if (info == null)
         return false;
      if (info.getSize() < 1L)
         return false;
      if (this.lockExtention != null) {
         return !this.lockFiles.contains(info.getName());
      }
      long delta = currentTime - info.getLastChange();
      if (this.log.DUMP) {
         this.log.dump(ME, "isReady '" + info.getName() + "' delta='" + delta + "' constant='" + this.delaySinceLastFileChange + "'");
      }
      return delta > this.delaySinceLastFileChange;
   }
   
   /**
    * Returns true if the file has been zero in size for too long.
    *  
    * @param info
    * @param currentTime
    * @return
    */
   private boolean isStale(FileInfo info, long currentTime) {
      if (info == null)
         return false;
      if (info.getSize() > 0L)
         return false;
      return currentTime - info.getLastChange() > this.warnOnEmptyFileDelay;
   }
   
   private TreeSet prepareEntries(File directory, Map existingFiles) {
      if (this.log.CALL)
         this.log.call(ME, "prepareEntries");
      
      TreeSet chronologicalSet = new TreeSet(new FileComparator());
      if (existingFiles == null || existingFiles.size() < 1) {
         if (this.log.DUMP) {
            this.log.dump(ME, "prepareEntries: nothing to do");
         }
      }
      Iterator iter = existingFiles.values().iterator();
      long currentTime = System.currentTimeMillis();
      while (iter.hasNext()) {
         FileInfo info = (FileInfo)iter.next();
         
         if (isReady(info, currentTime)) {
            chronologicalSet.add(info);
         }
         else {
            if (isStale(info, currentTime)) {
               this.log.warn(ME, "prepareEntries: the file '" + info.getName() + "' has been empty for too long: please remove it from directory '" + directory.getName() + "'");
            }
         }
      }
      return chronologicalSet;
   }

   /**
    * It updates the existing list of files:
    * 
    * - if a file which previously existed is not found in the new list anymore it is deleted
    * - new files are added to the list
    * - if something has changed (timestamp or size, then the corresponding info object is touched
    * 
    * @param existingFiles
    * @param newFiles
    */
   private void updateExistingFiles(Map existingFiles, Map newFiles) {
      Iterator iter = existingFiles.entrySet().iterator();
      Set toRemove = new HashSet();
      // scan all exising files: if some not found in new delete, otherwise 
      // update. At the end newFiles will only contain really new files
      while (iter.hasNext()) {
         Map.Entry existingEntry = (Map.Entry)iter.next();
         Object key = existingEntry.getKey();
         File newFile = (File)newFiles.get(key);
         if (newFile == null) { // the file has been deleted: remove it from the list
            if (toRemove == null)
               toRemove = new HashSet();
            toRemove.add(key);
         }
         else { // if still exists, then update
            FileInfo existingInfo = (FileInfo)existingEntry.getValue();
            existingInfo.update(newFile, this.log);
            newFiles.remove(key);
         }
      }
      // remove 
      if (toRemove != null && toRemove.size() > 0) {
         String[] keys = (String[])toRemove.toArray(new String[toRemove.size()]);
         for (int i=0; i < keys.length; i++) {
            this.log.warn(ME, "the file '" + keys[i] + "' has apparently been removed from the outside: will not send it. No further action required");
            existingFiles.remove(keys[i]);
         }
      }
      // now we only have new files to process
      iter = newFiles.values().iterator();
      while (iter.hasNext()) {
         File file = (File)iter.next();
         FileInfo info = new FileInfo(file, this.log);
         existingFiles.put(info.getName(), info);
      }
   }

   /**
    * Gets all entries which are ready to be sent (i.e. to be published)
    * 
    * @return all entries as a TreeSet. Elements in the set are of type
    * FileInfo
    * 
    * @throws XmlBlasterException if the application has no read or write 
    * rights on the directory 
    */
   Set getEntries() throws XmlBlasterException {
      if (this.log.CALL)
         this.log.call(ME, "getEntries");
      Map newFiles = getNewFiles(this.directory);
      updateExistingFiles(this.directoryEntries, newFiles);
      return prepareEntries(this.directory, this.directoryEntries);
   }

   /**
    * Removes the specified entry from the map. This method does also remove
    * the entry from the file system or it moves it to the requested directory. 
    * If for some reason this is not 
    * possible, then an exception is thrown.
    *  
    * @param entryName the name of the entry to remove. 
    * @return false if the entry was not found
    * @throws XmlBlasterException
    */
   void deleteOrMoveEntry(String entryName, boolean success) throws XmlBlasterException {
      try {
         if (this.log.CALL)
            this.log.call(ME, "removeEntry '" + entryName + "'");
         File file = new File(entryName);
         if (!file.exists()) {
            this.log.warn(ME, "removeEntry: '" + entryName + "' does not exist on the file system: I will only remove it from my list");
            this.directoryEntries.remove(entryName);
            return;
         }
         
         if (file.isDirectory())
            throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".removeEntry", "'" + entryName + "' is a directory");
         if (!file.canWrite())
            throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".removeEntry", "no rights to write to '" + entryName + "'");

         if (success && this.sentDirectory == null || !success && this.discardedDirectory == null) {
            if  (file.delete()) {
               this.directoryEntries.remove(entryName);
               return;
            }
            else {
               throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_UNKNOWN, ME + ".removeEntry", "could not remove entry '" + file.getName() + "': retrying");
            }
         }
         if (success) { // then do a move 
            moveTo(file, this.sentDirectory);
         }
         else {
            moveTo(file, this.discardedDirectory);
         }
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_UNKNOWN, ME + ".removeEntry", "", ex);
      }
   }
   
   private void moveTo(File file, File destinationDirectory) throws XmlBlasterException {
      if (!destinationDirectory.exists())
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".removeEntry", "'" + destinationDirectory.getName() + "' does not exist");
      if (!destinationDirectory.isDirectory())
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".removeEntry", "'" + destinationDirectory.getName() + "' is not a directory");
      if (!destinationDirectory.canRead())
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".removeEntry", "no rights to read to '" + destinationDirectory.getName() + "'");
      if (!destinationDirectory.canWrite())
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".removeEntry", "no rights to write to '" + destinationDirectory.getName() + "'");
      
      String relativeName = FileInfo.getRelativeName(file.getName());
      try {
         file.renameTo(new File(destinationDirectory, relativeName));
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".moveTo", "could not move the file '" + relativeName + "' to '" + destinationDirectory.getName() + "' reason: ", ex); 
      }
   }
   
   
   /**
    * Gets the content from the specified file as a byte[]. If this is 
    * not possible it will throw an exception.
    *  
    * @param info
    * @return
    * @throws XmlBlasterException
    */
   public byte[] getContent(FileInfo info) throws XmlBlasterException {
      String entryName = info.getName();
      if (this.log.CALL)
         this.log.call(ME, "getContent '" + entryName + "'");
      File file = new File(entryName);
      if (!file.exists()) {
         this.log.warn(ME, "getContent: '" + entryName + "' does not exist on the file system: not sending anything");
         this.directoryEntries.remove(entryName);
         return null;
      }
      if (file.isDirectory())
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".getContent", "'" + entryName + "' is a directory");
      if (!file.canWrite())
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".getContent", "no rights to write from '" + entryName + "'");

      try {
         int toRead = (int)info.getSize();
         int offset = 0;
         int tot = 0;
         
         byte[] ret = new byte[toRead];
         FileInputStream fis = new FileInputStream(entryName);
         BufferedInputStream bis = new BufferedInputStream(fis);
         
         while (tot < toRead) {
            int available = bis.available();
            if (available > 0) {
               int read = bis.read(ret, offset, available);
               tot += read;
            }
            else {
               try {
                  Thread.sleep(5L);
               }
               catch (Exception e) {}
            }
         }
         return ret;
      }
      catch (IOException ex) {
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".getContent", "", ex);
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_UNKNOWN, ME + ".removeEntry", "", ex);
      }
   }
   
}
