package org.xmlBlaster.test.classtest;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.jutils.time.StopWatch;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.recorder.file.FileIO;
import org.xmlBlaster.util.recorder.file.I_UserDataHandler;

import java.io.*;

import junit.framework.*;

/**
 * Test FileIO class. 
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.classtest.FileIOTest
 *
 * @see org.xmlBlaster.util.recorder.file.FileIO
 */
public class FileIOTest extends TestCase {
   private String ME = "FileIOTest";
   protected ServerScope glob;
   private static Logger log = Logger.getLogger(FileIOTest.class.getName());
   private String fileName = null;
   private StopWatch stopWatch = new StopWatch();
   private boolean testDiscardOldest = false;

   public FileIOTest(String name) {
      super(name);
   }

   protected void setUp() {
      glob = new ServerScope();

   }

   protected void tearDown() {
      File file;
      file = new File("testSync.txt");
      file.delete();
      file = new File("testNoSync.txt");
      file.delete();
      if (fileName != null) {
         file = new File(fileName);
         file.delete();
      }
   }

   public void testBasic() {
      testSync(false);
      testSync(true);
   }

   public void testSync(boolean sync) {
      String testName = sync ? "testSync" : "testNoSync";
      System.out.println("***** Test " + testName + " write ...");

      fileName = testName + ".txt";
      I_UserDataHandler userDataHandler = new UserDataHandler();
      long num = 2000;
      if (sync) num = 100;
      FileIO fileIO = null;

      try {
         log.info("Opening file '" + fileName + "' num=" + num + " sync=" + sync + " ...");
         fileIO = new FileIO(glob, fileName, userDataHandler, num, sync);
         File f = new File(fileName);
         long emptyLength = f.length();

         {
            System.out.println("Write " + num + " ...");
            long start = System.currentTimeMillis();
            for (int ii=0; ii<num; ii++)
               fileIO.writeNext("World-" + ii);
            long elapsed = System.currentTimeMillis() - start;
            if (num > 0L && elapsed > 0L)
               System.out.println("For num=" + num + " writes numLost=" + fileIO.getNumLost() + " numUnread=" + fileIO.getNumUnread() + " we needed " + elapsed + " millis -> " + (num*1000L)/elapsed + " writes/sec " + elapsed/num + " millis/write");

            assertEquals("NumUnread", num, fileIO.getNumUnread());
            assertEquals("NumLost", 0L, fileIO.getNumLost());
            File file = new File(fileName);
            assertTrue("File size emptyLength=" + emptyLength + " file.length()=" + file.length(), file.length() > emptyLength);
         }

         {
            System.out.println("***** Test " + testName + " read ...");
            long count = 0L;
            long start = System.currentTimeMillis();
            while (true) {
               String data = (String)fileIO.readNext(true);
               if (data == null)
                  break;
               count++;
            }
            long elapsed = System.currentTimeMillis() - start;
            if (count > 0L && elapsed > 0L)
               System.out.println("For count=" + count + " numLost=" + fileIO.getNumLost() + " numUnread=" + fileIO.getNumUnread() +
                 " reades we needed " + elapsed + " millis -> " + (count*1000L)/elapsed + " reads/sec " + elapsed/count + " millis/read");

            assertEquals("NumUnread", 0L, fileIO.getNumUnread());
            assertEquals("NumLost", 0L, fileIO.getNumLost());
            File file = new File(fileName);
            assertEquals("File size emptyLength=" + emptyLength + " file.length()=" + file.length(), emptyLength, file.length());
         }
      }
      catch(IOException e) {
         fail(testName + " failed: " + e.toString());
      }
      catch(XmlBlasterException e) {
         fail(testName + " failed: " + e.toString());
      }
      finally {
         if (fileIO != null) { fileIO.destroy(); fileIO = null; }
      }
   }

   public void testOverflow() {
      String testName = "testOverflow";
      System.out.println("***** Test " + testName);

      fileName = testName + ".txt";
      I_UserDataHandler userDataHandler = new UserDataHandler();
      long num = 10;
      long numOverflow = 2;
      FileIO fileIO = null;

      try {
         {
            fileIO = new FileIO(glob, fileName, userDataHandler, num-numOverflow, false);
            fileIO.setModeDiscardOldest();

            System.out.println("Write " + num + " data objects (DISCARD_OLDEST) ...");
            for (int ii=0; ii<num; ii++)
               fileIO.writeNext("World-" + ii);

            assertEquals("NumUnread", num-numOverflow, fileIO.getNumUnread());
            assertEquals("NumLost", numOverflow, fileIO.getNumLost());
         }
         {
            fileIO = new FileIO(glob, fileName, userDataHandler, num-numOverflow, false);
            fileIO.setModeDiscard();

            System.out.println("Write " + num + " data objects (DISCARD) ...");
            for (int ii=0; ii<num; ii++)
               fileIO.writeNext("World-" + ii);

            assertEquals("NumUnread", num-numOverflow, fileIO.getNumUnread());
            assertEquals("NumLost", numOverflow, fileIO.getNumLost());
         }
         {
            fileIO = new FileIO(glob, fileName, userDataHandler, num-numOverflow, false);
            fileIO.setModeException();

            System.out.println("Write " + num + " data objects (EXCEPTION) ...");
            int numExceptions = 0;
            for (int ii=0; ii<num; ii++) {
               try {
                  fileIO.writeNext("World-" + ii);
               }
               catch(XmlBlasterException e) {
                  numExceptions++;
               }
            }

            assertEquals("NumUnread", num-numOverflow, fileIO.getNumUnread());
            assertEquals("NumLost", numOverflow, fileIO.getNumLost());
            assertEquals("NumExceptions", numOverflow, numExceptions);
         }
      }
      catch(IOException e) {
         fail(testName + " failed: " + e.toString());
      }
      catch(XmlBlasterException e) {
         fail(testName + " failed: " + e.toString());
      }
      finally {
         if (fileIO != null) { fileIO.destroy(); fileIO = null; }
      }
   }

   public void testDataCorruption() {
      String testName = "testDataCorruption";
      System.out.println("***** Test " + testName);

      fileName = testName + ".txt";
      I_UserDataHandler userDataHandler = new UserDataHandler();
      long num = 20;
      FileIO fileIO = null;

      try {
         fileIO = new FileIO(glob, fileName, userDataHandler, num, false);
         fileIO.writeNext("AFirstValue");
         fileIO.writeNext("ASecondValue");
         assertEquals("Data corrupted", "AFirstValue", (String)fileIO.readNext(true));
         assertEquals("Data corrupted", "ASecondValue", (String)fileIO.readNext(true));
      }
      catch(IOException e) {
         fail(testName + " failed: " + e.toString());
      }
      catch(XmlBlasterException e) {
         fail(testName + " failed: " + e.toString());
      }
      finally {
         if (fileIO != null) { fileIO.destroy(); fileIO = null; }
      }
   }

   public void testKilledFileOnWrite() {
      String testName = "testKilledFileOnWrite";
      System.out.println("***** Test " + testName);

      fileName = testName + ".txt";
      I_UserDataHandler userDataHandler = new UserDataHandler();
      long num = 20;
      long numKill = 5;
      FileIO fileIO = null;

      try {
         fileIO = new FileIO(glob, fileName, userDataHandler, num, false);
         for (int ii=0; ii<num; ii++) {
            if (ii == numKill) {
               File ff = new File(fileName);
               ff.delete();
            }
            try {
               fileIO.writeNext("World-" + ii);
            }
            catch (XmlBlasterException e) {
               if (e.getErrorCode() == ErrorCode.RESOURCE_FILEIO)
                  fail("Wrong exception thrown: " + e.toString());
               else
                  System.out.println(e.getMessage());
                  
            }
         }

         assertEquals("NumUnread", num-numKill, fileIO.getNumUnread());

         int count = 0;
         while (true) {
            String data = (String)fileIO.readNext(true);
            if (data == null)
               break;
            count++;
         }

         assertEquals("num after kill", num-numKill, count);
         assertEquals("NumUnread", 0L, fileIO.getNumUnread());
         assertEquals("NumKillLost", numKill, fileIO.getNumFileDeleteLost());
         assertEquals("NumLost", 0L, fileIO.getNumLost());
      }
      catch(IOException e) {
         fail(testName + " failed: " + e.toString());
      }
      catch(XmlBlasterException e) {
         fail(testName + " failed: " + e.toString());
      }
      finally {
         if (fileIO != null) { fileIO.destroy(); fileIO = null; }
      }
   }

   public void testKilledFileOnRead() {
      String testName = "testKilledFileOnRead";
      System.out.println("***** Test " + testName);

      fileName = testName + ".txt";
      I_UserDataHandler userDataHandler = new UserDataHandler();
      long num = 20;
      long numKill = 5;
      FileIO fileIO = null;

      try {
         fileIO = new FileIO(glob, fileName, userDataHandler, num, false);
         for (int ii=0; ii<num; ii++) {
            fileIO.writeNext("World-" + ii);
         }

         assertEquals("NumUnread", num, fileIO.getNumUnread());

         int count = 0;
         while (true) {
            if (count == numKill) {
               File ff = new File(fileName);
               ff.delete();
            }

            try {
               String data = (String)fileIO.readNext(true);
               if (data == null)
                  break;
               count++;
            }
            catch (XmlBlasterException e) {
               if (e.getErrorCode() == ErrorCode.RESOURCE_FILEIO_FILELOST)
                  fail("Wrong exception thrown: " + e.toString());
               else {
                  System.out.println(e.getMessage());
                  break; // no more data to read
               }
                  
            }
         }

         assertEquals("num after kill", numKill, count);
         assertEquals("NumUnread", 0L, fileIO.getNumUnread());
         assertEquals("NumKillLost", num-numKill, fileIO.getNumFileDeleteLost());
         assertEquals("NumLost", 0L, fileIO.getNumLost());
      }
      catch(IOException e) {
         fail(testName + " failed: " + e.toString());
      }
      catch(XmlBlasterException e) {
         fail(testName + " failed: " + e.toString());
      }
      finally {
         if (fileIO != null) { fileIO.destroy(); fileIO = null; }
      }
   }

   public void testRestart() {
      String testName = "testRestart";
      System.out.println("***** Test " + testName);

      fileName = testName + ".txt";
      I_UserDataHandler userDataHandler = new UserDataHandler();
      long num = 20;
      long numRead = 8;
      FileIO fileIO = null;

      try {

         {
            fileIO = new FileIO(glob, fileName, userDataHandler, num, false);

            for (int ii=0; ii<num; ii++)
               fileIO.writeNext("World-" + ii);

            assertEquals("NumUnread", num, fileIO.getNumUnread());
            assertEquals("NumLost", 0L, fileIO.getNumLost());

            for (int ii=0; ii<numRead; ii++)
               fileIO.readNext(true);

            assertEquals("NumUnread", num-numRead, fileIO.getNumUnread());
            assertEquals("NumLost", 0L, fileIO.getNumLost());
         }

         // Simulates restart of software ...

         {
            fileIO = new FileIO(glob, fileName, userDataHandler, num, false);

            int count = 0;
            while (true) {
               String data = (String)fileIO.readNext(true);
               if (data == null)
                  break;
               count++;
            }

            assertEquals("num after restart", num-numRead, count);
            assertEquals("NumUnread", 0L, fileIO.getNumUnread());
            assertEquals("NumLost", 0L, fileIO.getNumLost());
         }
      }
      catch(IOException e) {
         fail(testName + " failed: " + e.toString());
      }
      catch(XmlBlasterException e) {
         fail(testName + " failed: " + e.toString());
      }
      finally {
         if (fileIO != null) { fileIO.destroy(); fileIO = null; }
      }
   }

   /** Simulate some data format to be recorded */
   class UserDataHandler implements I_UserDataHandler
   {
      public final void writeData(final RandomAccessFile ra, final Object userData) throws IOException {
         ra.writeUTF((String)userData);
      }
      public final Object readData(final RandomAccessFile ra) throws IOException {
         return ra.readUTF();
      }
   }

   /**   
    * Invoke: java org.xmlBlaster.test.qos.FileIOTest
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.classtest.FileIOTest</pre>
    */
   public static void main(String args[])
   {
      ServerScope glob = new ServerScope();
      if (glob.init(args) != 0) {
         System.err.println("******* FileIOTest: Init failed");
      }
      FileIOTest testSub = new FileIOTest("FileIOTest");
      testSub.setUp();
      testSub.testBasic();
      testSub.tearDown();
   }
}
