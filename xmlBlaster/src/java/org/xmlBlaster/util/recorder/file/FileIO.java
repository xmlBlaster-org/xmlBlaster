/*------------------------------------------------------------------------------
Name:      FileIO.java
Project:   xmlBlaster.org
Comment:   Data is written to and from harddisk
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.recorder.file;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.io.SyncFailedException;
import org.xmlBlaster.util.XmlBlasterException;

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
 *
 * @see classtest.FileRecorderTest
 */
public class FileIO
{
   private long currReadPos;
   private File f;
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


   public FileIO(String fileName, I_UserDataHandler userDataHandler, long maxEntries, boolean useSync) {
      this.fileName = fileName;
      if (maxEntries < 1)
         this.maxEntries = Long.MAX_VALUE;
      else
         this.maxEntries = maxEntries;
      this.useSync = useSync;
      this.userDataHandler = userDataHandler;

      try {
         initialize();
      }
      catch (Exception e) {
         System.err.println("IO-ERROR: " + e.toString());
         e.printStackTrace();
      }
   }

   /**
    * Initializes on first startup or reloads an existing file. 
    */
   public void initialize() throws IOException, SyncFailedException {
      currReadPos = -1L;
      numUnread = 0L;
      numLost = 0L;

      f = new File(fileName);
      boolean created = f.createNewFile();
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
            ra = new RandomAccessFile(f, "rw");
      }
      catch (java.lang.IllegalArgumentException e) {
         ra = new RandomAccessFile(f, "rw");
      }
      if (f.length() <= 24L) {
         ra.writeLong(0L); // currReadPos
         ra.writeLong(0L); // numUnread  (for information only)
         ra.writeLong(0L); // numLost    (for information only)
         if (useSync) ra.getFD().sync();
      }
   }

   /**
    * You can call readNext(false) and need to commit when you have processed
    * the retrieved data successully with saveCurrReadPos(). If your
    * processing fails without a saveCurrReadPos() call the subsequent readNext()
    * gets the same data again.
    * @exception XmlBlasterException<br />
    * "FileRecorder.FileLost" If file disappeared, you can proceed
    */
   public synchronized String readNext(boolean autoCommit) throws IOException, XmlBlasterException {
      if (!f.exists()) {
         numFileDeleteLost = getNumUnread();
         initialize();
         throw new XmlBlasterException("FileRecorder.FileLost",
            fileName + " was lost, " + getNumUnread() + " are messages lost, no message retrieved");
      }

      long pos = getCurrReadPos();
      if (pos > 0L) {
         ra.seek(currReadPos);
         String data = (String)userDataHandler.readData(ra);
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
    * Write more data. 
    * @exception XmlBlasterException<br />
    *  "FileRecorder.FileLost" If file disappeared, we create a new and store the message<br />
    *  "FileRecorder.MaxEntries" Maximum size reached in Exception mode
    */
   public void writeNext(String data) throws IOException, XmlBlasterException {
      String errorText = null;
      if (!f.exists()) {
         numFileDeleteLost = getNumUnread();
         errorText = fileName + " was lost, " + getNumUnread() + " are messages lost, creating a new one and storing your message.";
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
            throw new XmlBlasterException("FileRecorder.MaxEntries", "Maximun size=" + maxEntries + " of '" + fileName + "' reached");
         }
      }

      ra.seek(f.length());
      userDataHandler.writeData(ra, data);
      if (useSync) ra.getFD().sync();
      numUnread++;
      ra.seek(UNREAD_POS);
      ra.writeLong(numUnread);

      if (errorText != null)
         throw new XmlBlasterException("FileRecorder.FileLost", errorText);
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
      else if (f.length() > 24L) {
         ra.seek(0);
         currReadPos = ra.readLong(); // on restart
         if (currReadPos == 0) currReadPos = 24L; // first time
      }
      else {
         currReadPos = -1L;
      }

      if (currReadPos >= f.length()) {
         currReadPos = -1L;  // EOF
         f.delete();
         System.out.println("EOF of '" + fileName + "' reached, all data read, initializing file");
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
            System.err.println("FileIO.getNumUnread()" + e.toString());
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
            System.err.println("FileIO.getNumLost()" + e.toString());
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
}


