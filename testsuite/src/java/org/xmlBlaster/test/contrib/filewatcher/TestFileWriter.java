/*-----t-------------------------------------------------------------------------
Name:      TestFileWriter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.filewatcher;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import java.util.logging.Logger;
import java.util.zip.Adler32;
import java.util.zip.CheckedInputStream;

import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.filewatcher.Publisher;
import org.xmlBlaster.contrib.filewriter.FileWriter;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.qos.address.Address;

import junit.framework.TestCase;


/**
 * <p>
 * This is an interesting example, since it creates a XmlBlaster server instance
 * in the same JVM , but in a separate thread, talking over CORBA with it.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *   java junit.textui.TestRunner -noloading org.xmlBlaster.test.client.TestFileWriter
 *   java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.TestFileWriter
 * </pre>
 * @see org.xmlBlaster.client.I_XmlBlasterAccess
 */
public class TestFileWriter extends TestCase {
   private static String ME = "TestFileWriter";
   private Global global;
   private static Logger log = Logger.getLogger(TestFileWriter.class.getName());
   private String oid = "filepollerTest";
   private String baseDirName; // for both poller and writer
   private String pollerDirName;
   private String pollerDirNameSent;
   private String pollerDirNameDiscarded;
   private String writerDirName;
   private String writerTmpDirName;
   private FileWriter receiver;

   private class PluginProperties extends Properties implements I_PluginConfig {
      private final static long serialVersionUID = 1L;
      
      public PluginProperties() {
         super();
      }
      
      /**
       * @see org.xmlBlaster.util.plugin.I_PluginConfig#getParameters()
       */
      public Properties getParameters() {
         return this;
      }
      
      /**
       * @see org.xmlBlaster.util.plugin.I_PluginConfig#getPrefix()
       */
      public String getPrefix() {
         return "";
      }

      public String getType() {
         return "";
      }

      public String getVersion() {
         return "";
      }
   }
   
   public TestFileWriter() {
      this(null);
   }

   private void getBaseDir() {
      try {
         File dummy = File.createTempFile("dummy", null);
         String path = dummy.getCanonicalPath();
         dummy.delete();
         int pos = path.lastIndexOf(File.separator);
         if (pos < 0)
            fail("the temporary path is not absolute '" + path + "'");
         this.baseDirName = path.substring(0, pos) + File.separator + "testFileWriter"; 
         this.pollerDirName = baseDirName + File.separator + "poller";
         this.writerDirName = baseDirName + File.separator + "writer";
         this.writerTmpDirName = writerDirName + File.separator + "tmp";
         
         log.info("WILL USE THE DIRECTORY '" + this.pollerDirName + "' AS THE BASE DIRECTORY");
         this.pollerDirNameSent = this.pollerDirName + "/Sent";
         this.pollerDirNameDiscarded = this.pollerDirName + "/Discarded";

      }
      catch(Exception ex) {
         ex.printStackTrace();
         fail("exception occured when trying to find out temporary path");
      }
   }
   
   
   public TestFileWriter(Global global) {
      super("TestFileWriter");
      this.global = global;
      if (this.global == null) {
         this.global = new Global();
         this.global.init((String[])null);
      }

      getBaseDir();
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
      try {
         PluginProperties prop = new PluginProperties();
         prop.put("mom.topicName", "dummy");
         // prop.put("mom.subscribeKey", "");
         prop.put("mom.topicName", oid); // must be the same as on poller
         prop.put("mom.subscribeQos", "<qos/>");
         // prop.put("connectQos", "<qos/>");
         prop.put("mom.loginName", "testFileWriter");
         prop.put("mom.password", "secret");
         prop.put("filewriter.directoryName", writerDirName);
         prop.put("filewriter.tmpDirectoryName", writerTmpDirName);
         prop.put("filewriter.overwrite", "true");
         prop.put("filewriter.lockExtention", ".lck");
         prop.put("__useNativeCfg", "false"); // we don't want to set native stuff for testing
         receiver = new FileWriter(this.global, "fileWriter", prop);
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         fail("aborting since exception ex: " + ex.getMessage());
      }
   }
   
   
   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      log.info("Entering tearDown(), test is finished");
      cleanUpDirs();
      try {
         receiver.shutdown();
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         fail("aborting since exception ex: " + ex.getMessage());
      }
   }

   /**
    * Tests the creation of the necessary directories
    *
    */
   public void testDirectories() {
      // absolute path
      I_Info prop = new PropertiesInfo(new Properties());
      prop.put("mom.topicName", "dummy");
      prop.put("filewatcher.directoryName", this.pollerDirName);
      prop.put("filewatcher.sent", this.pollerDirNameSent);
      prop.put("filewatcher.discarded", this.pollerDirNameDiscarded);
      try {
         new Publisher(this.global, "test", prop);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue("An exception should not occur here " + ex.getMessage(), false);
      }
      checkDirs();

      // repeat that with already existing directories
      prop = new PropertiesInfo(new Properties());
      prop.put("mom.topicName", "dummy");
      prop.put("filewatcher.directoryName", this.pollerDirName);
      prop.put("filewatcher.sent", this.pollerDirNameSent);
      prop.put("filewatcher.discarded", this.pollerDirNameDiscarded);
      try {
         new Publisher(this.global, "test", prop);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue("An exception should not occur here " + ex.getMessage(), false);
      }
      checkDirs();
      cleanUpDirs();

      // relative path are added to the 'directoryName'
      prop = new PropertiesInfo(new Properties());
      prop.put("mom.topicName", "dummy");
      prop.put("filewatcher.directoryName", this.pollerDirName);
      prop.put("filewatcher.sent", "Sent");
      prop.put("filewatcher.discarded", "Discarded");
      try {
         new Publisher(this.global, "test", prop);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue("An exception should not occur here " + ex.getMessage(), false);
      }
      checkDirs();
      // relative path are added to the 'directoryName' repeat with existing directories
      prop = new PropertiesInfo(new Properties());
      prop.put("mom.topicName", "dummy");
      prop.put("filewatcher.directoryName", this.pollerDirName);
      prop.put("filewatcher.filewatcher.sent", "Sent");
      prop.put("filewatcher.discarded", "Discarded");
      try {
         new Publisher(this.global, "test", prop);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue("An exception should not occur here " + ex.getMessage(), false);
      }
      checkDirs();
      
      cleanUpDirs();
      
      // now some which should fail:
      // existing file but not a directory
      File file = new File(this.pollerDirName);
      try {
         file.createNewFile();
      }
      catch (IOException ex) {
         assertTrue("could not create the file '" + this.pollerDirName + "'", false);
      }
      
      prop = new PropertiesInfo(new Properties());
      prop.put("mom.topicName", "dummy");
      prop.put("filewatcher.directoryName", this.pollerDirName);
      prop.put("filewatcher.sent", "Sent");
      prop.put("filewatcher.discarded", "Discarded");
      try {
         new Publisher(this.global, "test", prop);
         assertTrue("an exception should occur since '" + this.pollerDirName + "' is a file and should be a directory", false);
      }
      catch (XmlBlasterException ex) {
         log.info("Exception is OK here");
      }
      cleanUpDirs();
      
      try {
         file = new File(this.pollerDirName);
         boolean ret = file.mkdir();
         assertTrue("could not create directory '" + this.pollerDirName + "'", ret);
         file = new File(this.pollerDirNameSent);
         file.createNewFile();
      }
      catch (IOException ex) {
         assertTrue("could not create the file '" + this.pollerDirNameSent + "'", false);
      }
      
      prop = new PropertiesInfo(new Properties());
      prop.put("mom.topicName", "dummy");
      prop.put("filewatcher.directoryName", this.pollerDirName);
      prop.put("filewatcher.sent", "Sent");
      prop.put("filewatcher.discarded", "Discarded");
      try {
         new Publisher(this.global, "test", prop);
         assertTrue("an exception should occur since '" + this.pollerDirName + "' is a file and should be a directory", false);
      }
      catch (XmlBlasterException ex) {
         log.info("Exception is OK here");
      }
      cleanUpDirs();
   }

   private void singleDump(String filename, int filesize, String lockExt, long delay, boolean deliver, boolean absSubPath, String movedDir) {
      String okFile = this.pollerDirName + File.separator + filename;
      byte[] okBuf = writeFile(okFile, filesize, lockExt);
      // TODO WAIT FOR UPDATE HERE !!!!
      boolean exist = false;
      String txt = "";
      if (!deliver) {
         exist = true;
         txt = "not ";
      }
      doWait(delay);
      File tmp = new File(okFile);
      assertEquals("the file '" + okFile + "' should " + txt + "have been removed", exist, tmp.exists());
      if (deliver) {
         checkMoved(filename, absSubPath, movedDir);
         String receivedFile = this.writerDirName + File.separator + filename;
         boolean sameContent = compareContent(okBuf, receivedFile);
         assertTrue("the content of the file is not the same as the arrived content of the update method", sameContent);
      }
   }
   
   private void checkMoved(String name, boolean absSubPath, String subDirName) {
      if (subDirName == null)
         return;
      File discDir = null;
      if (absSubPath) {
         discDir = new File(subDirName);
      }
      else {
         discDir = new File(new File(this.pollerDirName), subDirName);
      }
      File tmp = new File(discDir, name);
      assertTrue("The directory '" + subDirName + "' must exist", discDir.exists());
      assertTrue("The file '" + name + "' must exist in '" + subDirName + "' directory", tmp.exists());
   }

   private void doWait(long delay) {
      try {
         Thread.sleep(delay + 500L);
      }
      catch (InterruptedException ex) {
         ex.printStackTrace();
      }
   }

   private void doPublish(I_Info prop, boolean deliverFirst, boolean deliverSecond, boolean absSubPath) {
      String lockExt = prop.get("filewriter.lockExtention", null);
      
      prop.put("mom.topicName", this.oid);
      prop.put("filewatcher.directoryName", this.pollerDirName);

      int maximumSize = 10000;
      long delaySinceLastChange = 1000L;
      long pollInterval = 600L;
      prop.put("filewatcher.maximumFileSize", "" + maximumSize);
      prop.put("filewatcher.delaySinceLastFileChange", "" + delaySinceLastChange);
      prop.put("filewatcher.pollInterval", "" + pollInterval);
      prop.put("filewatcher.warnOnEmptyFileDelay", "1000");
      
      String sent = prop.get("filewatcher.sent", null);
      String discarded = prop.get("filewatcher.discarded", null);
      
      org.xmlBlaster.engine.ServerScope engineGlobal = new org.xmlBlaster.engine.ServerScope();
      prop.put("mom.connectQos", this.getConnectQos(engineGlobal));
      
      Publisher publisher = null;
      try {
         publisher = new Publisher(engineGlobal, "test", prop);
         publisher.init();
         // too big
         String tooBig = this.pollerDirName + File.separator + "tooBig.dat";
         writeFile(tooBig, maximumSize+1, lockExt);
         doWait(delaySinceLastChange*2);
         File tmp = new File(tooBig);
         if (deliverFirst) {
            assertFalse("the file '" + tooBig + "' should have been removed", tmp.exists());
            checkMoved("tooBig.dat", absSubPath, discarded);
         }
         else {
            assertTrue("the file '" + tooBig + "' should still be here", tmp.exists());
         }

         singleDump("ok.dat", maximumSize-1, lockExt, delaySinceLastChange* 2, deliverFirst, absSubPath, sent);
         singleDump("ok.gif", maximumSize-1, lockExt, delaySinceLastChange* 2, deliverSecond, absSubPath, sent);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue("An exception should not occur here " + ex.getMessage(), false);
      }
      finally {
         if (publisher != null) {
            try {
               publisher.shutdown();
            }
            catch (Throwable ex) {
               ex.printStackTrace();
               fail("exception when shutting down the poller " + ex.getMessage());
            }
         }
      }
   }
   
   public void testSimplePublish() {
      boolean deliverDat = true;
      boolean deliverGif = true;
      boolean absSubPath = true;
      I_Info prop = new PropertiesInfo(new Properties());
      doPublish(prop, deliverDat, deliverGif, absSubPath);
   }

   public void testSimplePublishWithFilter() {
      boolean deliverDat = false;
      boolean deliverGif = true;
      boolean absSubPath = true;
      I_Info prop = new PropertiesInfo(new Properties());
      prop.put("filewatcher.fileFilter", "*.gif");
      doPublish(prop, deliverDat, deliverGif, absSubPath);
   }
   
   public void testSimplePublishWithFilterRegex() {
      boolean deliverDat = true;
      boolean deliverGif = true;
      boolean absSubPath = true;
      I_Info prop = new PropertiesInfo(new Properties());
      prop.put("filewatcher.filterType", "regex");
      // note that the double backslash would be simple if read from the configuration file
      prop.put("filewatcher.fileFilter", "(.*\\.dat)|(.*\\.gif)");
      doPublish(prop, deliverDat, deliverGif, absSubPath);
   }
   
   public void testPublishWithMoveAbsolute() {
      boolean deliverDat = true;
      boolean deliverGif = true;
      boolean absSubPath = true;
      I_Info prop = new PropertiesInfo(new Properties());
      prop.put("filewatcher.sent", this.pollerDirName + File.separator + "Sent");
      prop.put("filewatcher.discarded", this.pollerDirName + File.separator + "Discarded");
      doPublish(prop, deliverDat, deliverGif, absSubPath);
   }

   public void testPublishWithMoveRelative() {
      boolean deliverDat = true;
      boolean deliverGif = true;
      boolean absSubPath = false;
      I_Info prop = new PropertiesInfo(new Properties());
      prop.put("filewatcher.sent", "Sent");
      prop.put("filewatcher.discarded", "Discarded");
      doPublish(prop, deliverDat, deliverGif, absSubPath);
   }

   public void testPublishWithMoveRelativeLockMode() {
      boolean deliverDat = true;
      boolean deliverGif = true;
      boolean absSubPath = false;
      I_Info prop = new PropertiesInfo(new Properties());
      prop.put("filewatcher.sent", "Sent");
      prop.put("filewatcher.discarded", "Discarded");
      prop.put("filewatcher.lockExtention", "*.lck");
      doPublish(prop, deliverDat, deliverGif, absSubPath);
   }

   public void testSimplePublishWithFilterLockMode() {
      boolean deliverDat = false;
      boolean deliverGif = true;
      boolean absSubPath = true;
      I_Info prop = new PropertiesInfo(new Properties());
      prop.put("filewatcher.fileFilter", "*.gif");
      prop.put("filewatcher.lockExtention", "*.lck");
      doPublish(prop, deliverDat, deliverGif, absSubPath);
   }
   
   long getChecksum(InputStream is) throws IOException {
      CheckedInputStream cis = new CheckedInputStream(is, new Adler32());
      byte[] tempBuf = new byte[128];
      while (cis.read(tempBuf) >= 0) {
      }
      return cis.getChecksum().getValue();
   }
   
   private boolean compareContent(byte[] buf1, String filename) {
      try {
         File file = new File(filename);
         if (!file.exists())
            return false;
         long byteChecksum = getChecksum(new ByteArrayInputStream(buf1));
         long fileChecksum = getChecksum(new FileInputStream(file));
         return byteChecksum == fileChecksum;
     } 
     catch (IOException ex) {
        ex.printStackTrace();
        return false;
     }
   }
   
   private byte[] writeFile(String filename, int size, String lockExt) {
      try {
         File lock = null;
         if (lockExt != null) {
            String tmp = filename + lockExt.substring(1);
            lock = new File(tmp);
            boolean ret = lock.createNewFile();
            assertTrue("could not create lock file '" + tmp + "'", ret);
         }
         byte[] buf = new byte[size];
         for (int i=0; i < size; i++) {
            buf[i] = (byte)i;
         }
         FileOutputStream fos = new FileOutputStream(filename);
         fos.write(buf);
         fos.close();
         
         if (lock != null) {
            boolean ret = lock.delete();
            assertTrue("could not remove lock file '" + filename + lockExt.substring(1) + "'", ret);
         }
         return buf;
      }
      catch (IOException ex) {
         ex.printStackTrace();
         fail("could not write to file '" + filename + "'");
         return null; // fake return to make compiler happy
      }
   }
   
   
   private void checkDirs() {
      File file = new File(this.pollerDirName);
      assertTrue("file '" + this.pollerDirName + "' does not exist", file.exists());
      file = new File(this.pollerDirNameSent);
      assertTrue("file '" + this.pollerDirNameSent + "' does not exist", file.exists());
      file = new File(this.pollerDirNameDiscarded);
      assertTrue("file '" + this.pollerDirNameDiscarded + "' does not exist", file.exists());
   }
   
   private String getConnectQos(Global glob) {
      try {
         ConnectQos connQos = new ConnectQos(glob, "filePollerTestUser", "secret");
         connQos.setMaxSessions(100);
         Address address = connQos.getAddress();
         address.setPingInterval(0L);
         address.setCollectTime(0L);
         connQos.getClientQueueProperty().setType("RAM");
         connQos.getClientQueueProperty().setVersion("1.0");
         return connQos.toXml();
      }
      catch (XmlBlasterException ex) {
         fail("an exception when building the connect qos: " + ex.getMessage());
         return null;
      }
   }

   private void delete(String filename) {
      try {
         (new File(filename)).delete();
      }
      catch (Throwable ex) {
      }
   }
   
   private void cleanUpDirs() {
      delete(this.pollerDirNameSent + File.separator + "ok.dat");
      delete(this.pollerDirNameSent + File.separator + "ok.gif");
      delete(this.pollerDirNameSent);
      delete(this.pollerDirNameDiscarded + File.separator + "tooBig.dat");
      delete(this.pollerDirNameDiscarded);
      delete(this.pollerDirName + File.separator + "ok.dat");
      delete(this.pollerDirName + File.separator + "ok.dat.lck");
      delete(this.pollerDirName + File.separator + "ok.gif");
      delete(this.pollerDirName + File.separator + "ok.gif.lck");
      delete(this.pollerDirName + File.separator + "tooBig.dat");
      delete(this.pollerDirName + File.separator + "tooBig.dat.lck");
      
      delete(this.pollerDirName);
   }
   
   
   /**
    * Invoke: java org.xmlBlaster.test.client.TestFileWriter
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.client.TestFileWriter</pre>
    */
   public static void main(String args[]) {
      Global global = new Global();
      if (global.init(args) != 0) {
         System.out.println(ME + ": Init failed");
         System.exit(1);
      }

      TestFileWriter test = new TestFileWriter(global);

      test.setUp();
      test.testSimplePublish();
      test.tearDown();

      test.setUp();
      test.testDirectories();
      test.tearDown();

      test.setUp();
      test.testSimplePublishWithFilter();
      test.tearDown();

      test.setUp();
      test.testSimplePublishWithFilterRegex();
      test.tearDown();

      test.setUp();
      test.testPublishWithMoveAbsolute();
      test.tearDown();

      test.setUp();
      test.testPublishWithMoveRelative();
      test.tearDown();

      test.setUp();
      test.testPublishWithMoveRelativeLockMode();
      test.tearDown();

      test.setUp();
      test.testSimplePublishWithFilterLockMode();
      test.tearDown();
   }
}

