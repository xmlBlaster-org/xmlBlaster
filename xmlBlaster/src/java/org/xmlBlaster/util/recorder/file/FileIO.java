/*------------------------------------------------------------------------------
Name:      FileIO.java
Project:   xmlBlaster.org
Comment:   Data is written to and from harddisk
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.recorder.file;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

/**
 * This is the generic interface to the harddisk. 
 * <p />
 * It recovers on restart as the current situation is stored as well.
 * <p />
 * To use this, you supply an implementation of
 * <p />
 * Performance is on a standard harddisk and 600 MHz intel server:
 * <ul>
 *  <li>With SYNC mode: 90 write/sec or 90 reads/sec</li>
 *  <li>None SYNC mode: 7000 write/sec or 5000 reads/sec</li>
 * </ul>
 * In SYNC mode, the harddisk is synced after any data written.
 * SYNC is usually left off, as an application crash can be recovered without.
 * <p />
 * You can call readNext(false) and need to commit when you have processed
 * the retrieved data successully with saveCurrReadPos(). If your
 * processing fails without a saveCurrReadPos() call the subsequent readNext()
 * gets the same data again.
 * <p />
 * File Format: 
 *   "<current read filedescriptor pos><numUnread><numLost>data....."
 * <p />
 *
 * TODO: <br />
 *  - If the file is never read empty it grows and grows ...<br />
 *  - The mode DISCARD_OLDEST does not shrink the file size
 * <p />
 * See the <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/util.recorder.html">util.recorder</a> requirement.
 * @see org.xmlBlaster.test.classtest.FileIOTest
 */
public class FileIO
{
   private static Logger log = Logger.getLogger(FileIO.class.getName());
   private final String ME = "FileIO";
   private final Global glob;
   private long currReadPos;
   private long lastReadPos;
   private File file;
   private RandomAccessFile ra;

   private long numUnread;
   private boolean firstUnread = true;
   private final long UNREAD_POS = 8L;

   private boolean firstLost = true;
   private long numLost;
   private final long LOST_POS = 16L;
   /** Remember number of lost data when file was killed by somebody */
   private long numFileDeleteLost = 0;

   // Syncs every write to HD, reduced performance
   // from 7000 writes/sec to 90 writes/sec
   private boolean useSync;

   private final I_UserDataHandler userDataHandler;

   private String fileName;
   private long maxEntries;

   private final int modeException = 0;
   private final int modeDiscardOldest = 1;
   private final int modeDiscard = 2;
   private int mode = modeException;


   /**
    * @param maxEntries < 0 sets it to unlimited
    * @param filename An absolute or relative path including the fileName, missing directories will be created
    * @param userDataHandler Your implementation of your data format marshalling
    */
   public FileIO(Global glob, String fileName, I_UserDataHandler userDataHandler, long maxEntries, boolean useSync) throws IOException {
      this.glob = glob;
      this.fileName = fileName;
      if (maxEntries < 0)
         this.maxEntries = Long.MAX_VALUE;
      else
         this.maxEntries = maxEntries;
      this.useSync = useSync;
      this.userDataHandler = userDataHandler;

      initialize();
   }

   /**
    * Initializes on first startup or reloads an existing file. 
    */
   public void initialize() throws IOException {
      currReadPos = -1L;
      lastReadPos = currReadPos;
      numUnread = 0L;
      numLost = 0L;

      file = mkfile(fileName);
      try {
         /*
           Implementation note:<br />
           Writing the fields LOST_POS, LOST_UNREAD with JDK1.4 in sync mode ("d" option)
           makes this very slow (10 writes/sec), but these fields are only
           informative and not essential ->
           We don't use JDK1.4 sync mode and do manually sync (like we do with JDK 1.2 and 1.3)
           then in sync mode we have 30 writes/sec (on a fast HD 90 write/sec).
         if (useSync) {
            ra = new RandomAccessFile(f, "rwd");
            System.out.println("Using JDK1.4 auto sync support");
            useSync = false; // is handled automatically with "d" option
         }
         else
         */
            ra = new RandomAccessFile(file, "rw");
      }
      catch (java.lang.IllegalArgumentException e) {
         ra = new RandomAccessFile(file, "rw");
      }
      if (ra.length() <= 24L) {
         ra.writeLong(0L); // currReadPos
         ra.writeLong(0L); // numUnread  (for information only)
         ra.writeLong(0L); // numLost    (for information only)
         if (useSync) ra.getFD().sync();
      }
   }

   /**
    * Creates recursive all directories, assuming file name is the last part of the path
    * @return The new file handle
    */
   public File mkfile(String fullName) throws IOException {
      File f = new File(fullName);
      File dir = f.getAbsoluteFile().getParentFile();
      if (dir != null)
         dir.mkdirs();
      f.createNewFile();
      return f;
   }

   /**
    * You can call readNext(false) and need to commit when you have processed
    * the retrieved data successully with saveCurrReadPos(). If your
    * processing fails without a saveCurrReadPos() call the subsequent readNext()
    * gets the same data again.
    * @exception XmlBlasterException<br />
    * "FileRecorder.FileLost" If file disappeared, you can proceed<br />
    * Of an XmlBlasterExeption from the user data handler
    */
   public synchronized Object readNext(boolean autoCommit) throws IOException, XmlBlasterException {
      if (file == null) {
         initialize(); // after a call to destroy
      }
      if (!file.exists()) {
         numFileDeleteLost = getNumUnread();
         initialize();
         throw new XmlBlasterException("FileRecorder.FileLost",
            fileName + " has disappeared, " + numFileDeleteLost + " messages are lost.");
      }

      currReadPos = getCurrReadPos();
      if (currReadPos > 0L) {
         ra.seek(currReadPos);
         Object data = userDataHandler.readData(ra);

         lastReadPos = currReadPos;
         currReadPos = ra.getFilePointer();

         if (autoCommit) saveCurrReadPos();
         numUnread--;
         ra.seek(UNREAD_POS);
         ra.writeLong(numUnread);
         return data;
      }
      else {
         ra.seek(24L);
         return null;
      }
   }

   /**
    * Undo the last read, not that this is not thread save! 
    * <p />
    * Only one single undo is supported
    * @return true if undo was possible
    */
   public synchronized boolean undo() {
      boolean ret = (lastReadPos != currReadPos);
      currReadPos = lastReadPos;
      try {
         saveCurrReadPos();
      }
      catch (IOException e) {
         return false;
      }
      return ret;
   }

   /**
    * Write more data. 
    * @exception XmlBlasterException<br />
    *  "ErrorCode.FileRecorder.FileLost" If file disappeared, we create a new and store the message<br />
    *  "ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES" Maximum size reached in Exception mode<br />
    *  Of an XmlBlasterExeption from the user data handler
    */
   public void writeNext(Object data) throws IOException, XmlBlasterException {
      String errorText = null;
      if (file == null) {
         initialize(); // after a call to destroy
      }
      if (!file.exists()) {
         numFileDeleteLost = getNumUnread();
         errorText = fileName + " disappeared, " + getNumUnread() + " messages are lost, creating a new one and storing your message.";
         initialize();
      }

      if (numUnread >= maxEntries) {
         if (this.mode == modeDiscardOldest) {
            readNext(true);
            numLost++;
            ra.seek(LOST_POS);
            ra.writeLong(numLost);
         }
         else if (this.mode == modeDiscard) {
            numLost++;
            ra.seek(LOST_POS);
            ra.writeLong(numLost);
            return;
         }
         else {
            String text = "Maximum size=" + maxEntries + " of '" + fileName + "' reached, message rejected.";
            log.warning(text);
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME, text);
         }
      }

      ra.seek(ra.length());
      userDataHandler.writeData(ra, data);
      if (useSync) ra.getFD().sync();
      numUnread++;
      ra.seek(UNREAD_POS);
      ra.writeLong(numUnread);

      if (errorText != null) {
         log.severe(errorText);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_FILEIO_FILELOST, ME, errorText);
      }
   }

   /** Write the first 8 bytes containing the offset to data to be read */
   public final void saveCurrReadPos() throws IOException {
      ra.seek(0);
      ra.writeLong(currReadPos); // remember if program crashes
      if (useSync) ra.getFD().sync();
   }

   /** Read the first 8 bytes contain the offset to data to be read */
   public final long getCurrReadPos() throws IOException {
      if (currReadPos > 0L) {
         // cached
      }
      else if (ra.length() > 24L) {
         ra.seek(0);
         currReadPos = ra.readLong(); // on restart
         if (currReadPos == 0) {
            currReadPos = 24L; // first time
            lastReadPos = currReadPos;
         }
      }
      else {
         currReadPos = -1L;
         lastReadPos = currReadPos;
      }

      if (currReadPos >= ra.length()) {
         file.delete(); // EOF
         //System.out.println("FileIO: EOF of '" + fileName + "' reached, all data read, initializing file");
         initialize();
      }

      return currReadPos;
   }

   /**
    * The number of unread data entities. 
    * <p />
    */
   public final long getNumUnread() {
      if (firstUnread) {
         firstUnread = false;
         try {
            ra.seek(UNREAD_POS);
            numUnread = ra.readLong(); // on restart
         }
         catch(java.io.IOException e) {
            log.severe(e.toString());
         }
      }
      return this.numUnread;
   }

   /**
    * Counter for lost messages in 'discard' or 'discardOldest' mode
    * <p />
    */
   public long getNumLost() {
      if (firstLost) {
         firstLost = false;
         try {
            ra.seek(LOST_POS);
            numLost = ra.readLong(); // on restart
         }
         catch(java.io.IOException e) {
            log.severe(e.toString());
         }
      }
      return this.numLost;
   }

   /**
    * Returns the number of lost data objects when the file was deleted by somebody. 
    */
   public final long getNumFileDeleteLost() { 
      return numFileDeleteLost;
   }

   /** Throw the message away if queue is full - the message is silently lost! */
   public void setModeDiscard() {
      this.mode = modeDiscard;
   }
   /** Throw the oldest message away if queue is full - the message is silently lost! */
   public void setModeDiscardOldest() {
      this.mode = modeDiscardOldest;
   }
   /** Default you get an Exception if queue is full */
   public void setModeException() {
      this.mode = modeException;
   }

   /** Don't loose data */
   public void shutdown() {
      if (ra != null) {
         try {
            ra.close();
         }
         catch (IOException e) {}
         ra = null;
      }
      currReadPos = -1L;
      lastReadPos = currReadPos;
      numUnread = 0L;
      numLost = 0L;
   }

   /** Destroy data */
   public void destroy() {
      shutdown();
      if (file != null) {
         file.delete();
         file = null;
      }
   }
}


