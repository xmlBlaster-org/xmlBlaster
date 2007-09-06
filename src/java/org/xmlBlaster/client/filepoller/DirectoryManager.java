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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

/**
 * DirectoryManager
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class DirectoryManager {
   private String ME = "DirectoryManager";
   private Global global;
   private static Logger log = Logger.getLogger(DirectoryManager.class.getName());
   private long delaySinceLastFileChange;

   private String directoryName;
   private File directory;
   /** this is the the directory where files are moved to after successful publishing. If null they will be erased */
   private File sentDirectory;
   /** this is the name of the directory where files are moved if they could not be send (too big) */
   private File discardedDirectory;
   
   private Map directoryEntries; 
   /** all files matching the filter will be processed. Null means everything will be processed */
   private FileFilter fileFilter;
   /** if set, then files will only be published when the lock-file has been removed. */
   private FileFilter lockExtention;
   /** convenience for performance: if lockExtention is '*.gif', then this will be '.gif' */
   private String lockExt; 
   
   private Set lockFiles;
   
   private boolean copyOnMove;
   
   public DirectoryManager(Global global, String name, String directoryName, long delaySinceLastFileChange, String filter, String sent, String discarded, String lockExtention, boolean trueRegex, boolean copyOnMove) throws XmlBlasterException {
      ME += "-" + name;
      this.global = global;
      if (filter != null)
         this.fileFilter = new FilenameFilter(filter, trueRegex);

      this.delaySinceLastFileChange = delaySinceLastFileChange;
      this.directoryEntries = new HashMap();
      this.directoryName = directoryName;
      this.directory = initDirectory(null, "directoryName", directoryName);
      if (this.directory == null)
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_NULLPOINTER, ME + ".constructor", "the directory '" + directoryName + "' is null");
      this.sentDirectory = initDirectory(this.directory, "sent", sent);
      this.discardedDirectory = initDirectory(this.directory, "discarded", discarded);
      if (lockExtention != null) {
         String tmp = lockExtention.trim();
         if (!tmp.startsWith("*.")) {
            throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_CONFIGURATION, ME, "lockExtention must start with '*.' and be of the kind '*.lck'");
         }
         this.lockExtention = new FilenameFilter(tmp, false);
         this.lockExt = tmp.substring(1); // '*.gif' -> '.gif' 
      }
      this.lockFiles = new HashSet();
      this.copyOnMove = copyOnMove;
   }

   /**
    * Returns the specified directory or null or if needed it will create one
    * @param propName
    * @param dirName
    * @return
    * @throws XmlBlasterException
    */
   private File initDirectory(File parent, String propNameForLogging, String dirName) throws XmlBlasterException {
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
            log.info(ME+": Constructor: directory '" + absDirName + "' does not yet exist. I will create it");
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
         log.info(ME+": Constructor: the '" + propNameForLogging + "' property is not set. Instead of moving concerned entries they will be deleted");
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
      if (this.lockExtention != null) { // reset lockFile set
         this.lockFiles.clear();
      }
      
      this.directory = initDirectory(null, "directoryName", this.directoryName);
      if (!directory.canRead())
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".scan", "I don't have rights to read from '" + directory.getName() + "'");
      if (!directory.canWrite())
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".scan", "I don't have rights to write to '" + directory.getName() + "'");
      File[] files = directory.listFiles(this.fileFilter);
      if (files == null || files.length == 0)
         return new HashMap();
      if (this.lockExtention != null) {
         // and then retrieve all lock files (this must be done after having got 'files' to avoid any gaps
         File[] lckFiles = directory.listFiles(this.lockExtention);
         if (lckFiles != null) {
            for (int i=0; i < lckFiles.length; i++) {
               String name = null;
               try {
                  name = lckFiles[i].getCanonicalPath();
               }
               catch (IOException ex) {
                  throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".getNewFiles", " could not get the canonical name of file '" + files[i].getName() + "'");
               }
               int pos = -1;
               if (this.lockExt != null)
                  pos = (isFileNameCasesensitive() ? name.lastIndexOf(this.lockExt) : name.toUpperCase().lastIndexOf(this.lockExt.toUpperCase()));
               if (pos < 0) 
                  throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_CONFIGURATION, ME, "can not handle lckExtention '*" + this.lockExt + "'");
               this.lockFiles.add(name.substring(0, pos));
            }
         }
      }
      
      Map map = new HashMap(files.length);
      for (int i=0; i < files.length; i++) {
         try {
            String name = files[i].getCanonicalPath();
            if (files[i].isFile()) {
               boolean endsWithLockExt = false;
               if (this.lockExt != null)
                  endsWithLockExt = (isFileNameCasesensitive() ? name.endsWith(this.lockExt) : name.toUpperCase().endsWith(this.lockExt.toUpperCase()));
               if (this.lockExtention == null || (!this.lockFiles.contains(name) && !endsWithLockExt))
                  map.put(name, files[i]);
            }
         }
         catch (IOException ex) {
            throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".getNewFiles", " could not get the canonical name of file '" + files[i].getName() + "'");
         }
      }
      return map;
   }
   
   /**
    * On Windows sometimes the file is not deleted (even if the stream.close() were called before)
    * We try as long until the file is away
    * See http://forum.java.sun.com/thread.jspa?forumID=4&threadID=158689
    * @param tempFile
    * @return true if successfully deleted
    */
   private boolean deleteFile(File tempFile) {
      if (!tempFile.exists())
         return true;
      final int MAX = 100;
      boolean warn = false;
      int i=0;
      for (i=0; i<MAX; i++) {
         if (!tempFile.delete()) {
            warn = true;
            if (!tempFile.exists()) // calling double delete fails, so check here
               break;
            if (i == 0)
               log.fine(ME+": Deleting file " + tempFile.getAbsolutePath() + " failed");
            System.gc();
            if (!tempFile.delete()) {
               if (i == 0)
                  log.warning(ME+": Deleting file " + tempFile.getAbsolutePath() + " failed even after GC");
               try {
                  Thread.sleep(100);
               } catch (InterruptedException e) {
               }
            }
            else
               break;
         }
         else
            break;
      }
      if (i >= MAX) {
         log.severe(ME+": Deleting file " + tempFile.getAbsolutePath() + " failed, giving up");
         return false;
      }
      else {
         if (warn)
            log.info(ME+": Deleting file " + tempFile.getAbsolutePath() + " finally succeeded after " + (i+1) + " tries");
         return true;
      }
   }
   
   public static boolean isFileNameCasesensitive() {
      String osName = System.getProperty("os.name");
      if (osName == null)
         return true;
      if (osName.startsWith("Windows"))
         return false;
      return true;
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
      //if (info.getSize() < 1L)
      //   return false;
      if (this.lockExtention != null) {
         return !this.lockFiles.contains(info.getName());
      }
      long delta = currentTime - info.getLastChange();
      if (log.isLoggable(Level.FINEST)) {
         log.finest(ME+": isReady '" + info.getName() + "' delta='" + delta + "' constant='" + this.delaySinceLastFileChange + "'");
      }
      return delta > this.delaySinceLastFileChange;
   }
   
   private TreeSet prepareEntries(File directory, Map existingFiles) {
      if (log.isLoggable(Level.FINER))
         log.finer(ME+": prepareEntries");
      
      TreeSet chronologicalSet = new TreeSet(new FileComparator());
      if (existingFiles == null || existingFiles.size() < 1) {
         if (log.isLoggable(Level.FINEST)) {
            log.finest(ME+": prepareEntries: nothing to do");
         }
      }
      Iterator iter = existingFiles.values().iterator();
      long currentTime = System.currentTimeMillis();
      while (iter.hasNext()) {
         FileInfo info = (FileInfo)iter.next();
         
         if (isReady(info, currentTime)) {
            chronologicalSet.add(info);
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
            existingInfo.update(newFile, log);
            newFiles.remove(key);
         }
      }
      // remove 
      if (toRemove != null && toRemove.size() > 0) {
         String[] keys = (String[])toRemove.toArray(new String[toRemove.size()]);
         for (int i=0; i < keys.length; i++) {
            log.warning(ME+": the file '" + keys[i] + "' has apparently been removed from the outside: will not send it. No further action required");
            existingFiles.remove(keys[i]);
         }
      }
      // now we only have new files to process
      iter = newFiles.values().iterator();
      while (iter.hasNext()) {
         File file = (File)iter.next();
         FileInfo info = new FileInfo(file, log);
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
      if (log.isLoggable(Level.FINER))
         log.finer(ME+": getEntries");
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
   void deleteOrMoveEntry(final String entryName, boolean success) throws XmlBlasterException {
      try {
         if (log.isLoggable(Level.FINER))
            log.finer(ME+": removeEntry '" + entryName + "'");
         File file = new File(entryName);
         if (!file.exists()) {
            log.warning(ME+": removeEntry: '" + entryName + "' does not exist on the file system: I will only remove it from my list");
            this.directoryEntries.remove(entryName);
            return;
         }
         
         if (file.isDirectory())
            throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".removeEntry", "'" + entryName + "' is a directory");
         if (!file.canWrite())
            throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".removeEntry", "no rights to write to '" + entryName + "'");

         if (success && this.sentDirectory == null || !success && this.discardedDirectory == null) {
            if  (deleteFile(file)) {
               this.directoryEntries.remove(entryName);
               return;
            }
            else {
               throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_UNKNOWN, ME + ".removeEntry", "could not remove entry '" + file.getName() + "': retrying");
            }
         }
         if (success) { // then do a move 
            moveTo(file, entryName, this.sentDirectory);
            this.directoryEntries.remove(entryName);
         }
         else {
            moveTo(file, entryName, this.discardedDirectory);
            this.directoryEntries.remove(entryName);
         }
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_UNKNOWN, ME + ".removeEntry", "", ex);
      }
   }
   
   private void moveTo(File file, String origName, File destinationDirectory) throws XmlBlasterException {
      if (!destinationDirectory.exists())
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".removeEntry", "'" + destinationDirectory.getName() + "' does not exist");
      if (!destinationDirectory.isDirectory())
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".removeEntry", "'" + destinationDirectory.getName() + "' is not a directory");
      if (!destinationDirectory.canRead())
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".removeEntry", "no rights to read to '" + destinationDirectory.getName() + "'");
      if (!destinationDirectory.canWrite())
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".removeEntry", "no rights to write to '" + destinationDirectory.getName() + "'");
      
      if (log.isLoggable(Level.FINE)) log.fine(ME+": File " + file.getAbsolutePath() + " moving to " + destinationDirectory.getAbsolutePath() + ", copyOnMove=" + copyOnMove);
      String relativeName = FileInfo.getRelativeName(file.getName());
      try {
         File destinationFile = new File(destinationDirectory, relativeName);
         if (destinationFile.exists()) {
            boolean ret = deleteFile(destinationFile);
            if (!ret)
               throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".moveTo", "could not delete the existing file '" + destinationFile.getCanonicalPath() + "' to '" + destinationDirectory.getName() + "' before moving avay '" + relativeName + "' after processing");
         }
         if (copyOnMove) {
            InputStream inputStream = file.toURL().openStream();
            BufferedInputStream bis = new BufferedInputStream(inputStream);
            try {
               FileOutputStream os = new FileOutputStream(destinationFile);
               try {
                  long length = file.length();
                  long remaining = length;
                  final int BYTE_LENGTH = 100000; // For the moment it is hardcoded
                  byte[] buf = new byte[BYTE_LENGTH];
                  while (remaining > 0) {
                     int tot = bis.read(buf);
                     remaining -= tot;
                     os.write(buf, 0, tot);
                  }
               }
               finally {
                  try { os.close(); } catch (Throwable e) {}
               }
            }
            finally {
               try { bis.close(); } catch (Throwable e) {}
               try { inputStream.close(); } catch (Throwable e) {}
            }
            String name = file.getAbsolutePath();
            boolean deleted = deleteFile(file);
            if (deleted) {
               if (log.isLoggable(Level.FINE)) log.fine(ME+": File " + name + " is successfully deleted, copyOnMove=" + copyOnMove);
            }
            else {
               log.warning(ME+": File " + name + " delete call failed: deleted=" + deleted + ", copyOnMove=" + copyOnMove + " exists=" + file.exists());
            }
         }
         else {
            boolean ret = file.renameTo(destinationFile);
            if (!ret) {
               File orig = new File(origName);
               if (orig.exists()) {
                  throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".moveTo", "Could not move the file '" + relativeName + "' to '" + destinationDirectory.getName() + "' reason: could it be that the destination is not a local file system ? try the flag 'copyOnMove='true' (see http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.filepoller.html");
               }
               else {
                  File dest = new File(destinationDirectory, relativeName);
                  if (!dest.exists()) {
                     log.warning(ME+": Removed published file '" + origName + "' but couldn't create backup '" + destinationDirectory.getName() + "' (see http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.filepoller.html");
                  }
                  else {
                     log.warning(ME+": Published file '" + origName + "' is already moved to backup '" + destinationDirectory.getName() + "' but java tells us it couldn't be moved, this is strange.");
                  }
               }
            }
         }
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable ex) {
         log.warning(ME + ": Could not move the file '" + relativeName + "' to '" + destinationDirectory.getName() + "' reason: " + ex.toString());
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
      if (log.isLoggable(Level.FINER))
         log.finer(ME+": getContent '" + entryName + "'");
      File file = new File(entryName);
      if (!file.exists()) {
         log.warning(ME+": getContent: '" + entryName + "' does not exist on the file system: not sending anything");
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
   
   /** java org.xmlBlaster.client.filepoller.DirectoryManager -path /tmp/filepoller -filter "*.xml" -filterType simple */
   public static void main(String[] args) {
      try {
         Global global = new Global(args);
         String path = global.get("path", ".", null, null);
         File directory = new File(path);
         String filter = global.get("filter", "*.txt", null, null);
         String filterType = global.get("filterType", "simple", null, null);
         boolean trueRegex = false;
         if ("regex".equalsIgnoreCase(filterType))
            trueRegex = true;
         System.out.println("-----------Configuration:-------------------------");
         System.out.println("Directory to look into: '" + directory.getAbsolutePath() + "'");
         System.out.println("The " + filterType + " filter is '" + filter + "'"); 
         System.out.println(""); 
         System.out.println("-----------Matching Results:----------------------");
         FilenameFilter fileFilter = new FilenameFilter(filter, trueRegex);
         File[] files = directory.listFiles(fileFilter);
         if (files == null || files.length < 1) {
            System.out.println(""); 
            System.out.println("WARN: no files found matching the " + filterType + " expression '" + filter + "'");
            System.out.println(""); 
            System.exit(0);
         }
         for (int i=0; i < files.length; i++) {
            System.out.println("file[" + i + "] = " + files[i].getName());
         }
         if (files.length > 0) {
            System.out.println("");
            System.out.println("no more files found");
         }
         
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }
   
   
}
