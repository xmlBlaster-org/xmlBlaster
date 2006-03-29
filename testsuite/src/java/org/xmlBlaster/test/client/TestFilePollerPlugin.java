/*-----t-------------------------------------------------------------------------
Name:      TestPollerPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import java.util.logging.Logger;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.filepoller.Publisher;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.test.MsgInterceptor;
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
 *   java junit.textui.TestRunner -noloading org.xmlBlaster.test.client.TestFilePollerPlugin
 *   java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.TestFilePollerPlugin
 * </pre>
 * @see org.xmlBlaster.client.I_XmlBlasterAccess
 */
public class TestFilePollerPlugin extends TestCase implements I_Callback {
   private static String ME = "TestFilePollerPlugin";
   private Global global;
   private static Logger log = Logger.getLogger(TestFilePollerPlugin.class.getName());
   private Global connGlobal;
   private String oid = "filepollerTest";
   private String dirName;
   private String dirNameSent;
   private String dirNameDiscarded;
   private MsgInterceptor updateInterceptor;

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
   
   public TestFilePollerPlugin() {
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
         this.dirName = path.substring(0, pos) + "/testsuitePoller";
         log.info("WILL USE THE DIRECTORY '" + this.dirName + "' AS THE BASE DIRECTORY");
         this.dirNameSent = this.dirName + "/Sent";
         this.dirNameDiscarded = this.dirName + "/Discarded";
      }
      catch(Exception ex) {
         ex.printStackTrace();
         fail("exception occured when trying to find out temporary path");
      }
   }
   
   
   public TestFilePollerPlugin(Global global) {
      super("TestFilePollerPlugin");
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
      /*
      File file = new File(this.dirName);
      if (file.exists())
         FileLocator.deleteDir(file);
      */
      try {
         this.connGlobal = this.global.getClone(null);
         this.updateInterceptor = new MsgInterceptor(this.connGlobal, log, null);
         this.connGlobal.getXmlBlasterAccess().connect(new ConnectQos(this.connGlobal), this.updateInterceptor);
         SubscribeQos subQos = new SubscribeQos(this.connGlobal);
         subQos.setWantInitialUpdate(false);
         this.connGlobal.getXmlBlasterAccess().subscribe(new SubscribeKey(this.connGlobal, this.oid), subQos);
      }
      catch (XmlBlasterException ex) {
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
         this.connGlobal.getXmlBlasterAccess().unSubscribe(new UnSubscribeKey(this.connGlobal, this.oid), new UnSubscribeQos(this.connGlobal));
         this.connGlobal.getXmlBlasterAccess().disconnect(new DisconnectQos(this.connGlobal));
         this.connGlobal.shutdown();
         this.connGlobal = null;
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         fail("aborting since exception ex: " + ex.getMessage());
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      String contentStr = new String(content);
      String cont = (contentStr.length() > 10) ? (contentStr.substring(0,10)+"...") : contentStr;
      log.info("Receiving update of a message oid=" + updateKey.getOid() +
                        " priority=" + updateQos.getPriority() +
                        " state=" + updateQos.getState() +
                        " content=" + cont);
      log.info("further log for receiving update of a message cbSessionId=" + cbSessionId +
                     updateKey.toXml() + "\n" + new String(content) + updateQos.toXml());
      log.severe("update: should never be invoked (msgInterceptors take care of it since they are passed on subscriptions)");

      return "OK";
   }

   /**
    * Tests the creation of the necessary directories
    *
    */
   public void testDirectories() {
      // absolute path
      PluginProperties prop = new PluginProperties();
      prop.put("topicName", "dummy");
      prop.put("directoryName", this.dirName);
      prop.put("sent", this.dirNameSent);
      prop.put("discarded", this.dirNameDiscarded);
      try {
         new Publisher(this.global, "test", prop);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue("An exception should not occur here " + ex.getMessage(), false);
      }
      checkDirs();

      // repeat that with already existing directories
      prop = new PluginProperties();
      prop.put("topicName", "dummy");
      prop.put("directoryName", this.dirName);
      prop.put("sent", this.dirNameSent);
      prop.put("discarded", this.dirNameDiscarded);
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
      prop = new PluginProperties();
      prop.put("topicName", "dummy");
      prop.put("directoryName", this.dirName);
      prop.put("sent", "Sent");
      prop.put("discarded", "Discarded");
      try {
         new Publisher(this.global, "test", prop);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue("An exception should not occur here " + ex.getMessage(), false);
      }
      checkDirs();
      // relative path are added to the 'directoryName' repeat with existing directories
      prop = new PluginProperties();
      prop.put("topicName", "dummy");
      prop.put("directoryName", this.dirName);
      prop.put("sent", "Sent");
      prop.put("discarded", "Discarded");
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
      File file = new File(this.dirName);
      try {
         file.createNewFile();
      }
      catch (IOException ex) {
         assertTrue("could not create the file '" + this.dirName + "'", false);
      }
      
      prop = new PluginProperties();
      prop.put("topicName", "dummy");
      prop.put("directoryName", this.dirName);
      prop.put("sent", "Sent");
      prop.put("discarded", "Discarded");
      try {
         new Publisher(this.global, "test", prop);
         assertTrue("an exception should occur since '" + this.dirName + "' is a file and should be a directory", false);
      }
      catch (XmlBlasterException ex) {
         log.info("Exception is OK here");
      }
      cleanUpDirs();
      
      try {
         file = new File(this.dirName);
         boolean ret = file.mkdir();
         assertTrue("could not create directory '" + this.dirName + "'", ret);
         file = new File(this.dirNameSent);
         file.createNewFile();
      }
      catch (IOException ex) {
         assertTrue("could not create the file '" + this.dirNameSent + "'", false);
      }
      
      prop = new PluginProperties();
      prop.put("topicName", "dummy");
      prop.put("directoryName", this.dirName);
      prop.put("sent", "Sent");
      prop.put("discarded", "Discarded");
      try {
         new Publisher(this.global, "test", prop);
         assertTrue("an exception should occur since '" + this.dirName + "' is a file and should be a directory", false);
      }
      catch (XmlBlasterException ex) {
         log.info("Exception is OK here");
      }
      cleanUpDirs();
   }

   private void singleDump(String filename, int filesize, String lockExt, long delay, boolean deliver, boolean absSubPath, String movedDir) {
      String okFile = this.dirName + File.separator + filename;
      byte[] okBuf = writeFile(okFile, filesize, lockExt, delay);
      int ret = this.updateInterceptor.waitOnUpdate(delay);
      boolean exist = false;
      int sent = 1;
      String txt = "";
      if (!deliver) {
         exist = true;
         sent = 0;
         txt = "not ";
      }
      assertEquals("expected '" + sent + "' update", sent, ret);
      File tmp = new File(okFile);
      assertEquals("the file '" + okFile + "' should " + txt + "have been removed", exist, tmp.exists());
      if (deliver) {
         checkMoved(filename, absSubPath, movedDir);
         boolean sameContent = compareContent(okBuf, this.updateInterceptor.getMsgs()[0].getContent());
         assertTrue("the content of the file is not the same as the arrived content of the update method", sameContent);
         String fileName = this.updateInterceptor.getMsgs()[0].getUpdateQos().getClientProperty("_fileName", (String)null);
         assertNotNull("The fileName is null", fileName);
         assertEquals("", filename, fileName);
      }
      this.updateInterceptor.clear();
   }
   
   private void checkMoved(String name, boolean absSubPath, String subDirName) {
      if (subDirName == null)
         return;
      File discDir = null;
      if (absSubPath) {
         discDir = new File(subDirName);
      }
      else {
         discDir = new File(new File(this.dirName), subDirName);
      }
      File tmp = new File(discDir, name);
      assertTrue("The directory '" + subDirName + "' must exist", discDir.exists());
      assertTrue("The file '" + name + "' must exist in '" + subDirName + "' directory", tmp.exists());
   }
   
   
   private void doPublish(PluginProperties prop, boolean deliverFirst, boolean deliverSecond, boolean absSubPath) {
      String lockExt = prop.getProperty("lockExtention", null);
      
      prop.put("topicName", this.oid);
      prop.put("directoryName", this.dirName);

      int maximumSize = 10000;
      long delaySinceLastChange = 1000L;
      long pollInterval = 600L;
      prop.put("maximumFileSize", "" + maximumSize);
      prop.put("delaySinceLastFileChange", "" + delaySinceLastChange);
      prop.put("pollInterval", "" + pollInterval);
      prop.put("warnOnEmptyFileDelay", "1000");
      
      String sent = prop.getProperty("sent", null);
      String discarded = prop.getProperty("discarded", null);
      
      org.xmlBlaster.engine.ServerScope engineGlobal = new org.xmlBlaster.engine.ServerScope();
      prop.put("connectQos", this.getConnectQos(engineGlobal));
      
      Publisher publisher = null;
      try {
         publisher = new Publisher(engineGlobal, "test", prop);
         publisher.init();

         this.updateInterceptor.clear();
         // too big
         String tooBig = this.dirName + File.separator + "tooBig.dat";
         writeFile(tooBig, maximumSize+1, lockExt, delaySinceLastChange* 2);
         int ret = this.updateInterceptor.waitOnUpdate(delaySinceLastChange* 2);
         assertEquals("expected no updates", 0, ret);

         File tmp = new File(tooBig);
         if (deliverFirst) {
            assertFalse("the file '" + tooBig + "' should have been removed", tmp.exists());
            checkMoved("tooBig.dat", absSubPath, discarded);
         }
         else {
            assertTrue("the file '" + tooBig + "' should still be here", tmp.exists());
         }

         this.updateInterceptor.clear();

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
      PluginProperties prop = new PluginProperties();
      doPublish(prop, deliverDat, deliverGif, absSubPath);
   }

   public void testSimplePublishWithFilter() {
      boolean deliverDat = false;
      boolean deliverGif = true;
      boolean absSubPath = true;
      PluginProperties prop = new PluginProperties();
      prop.put("fileFilter", "*.gif");
      doPublish(prop, deliverDat, deliverGif, absSubPath);
   }
   
   public void testSimplePublishWithFilterRegex() {
      boolean deliverDat = true;
      boolean deliverGif = true;
      boolean absSubPath = true;
      PluginProperties prop = new PluginProperties();
      prop.put("filterType", "regex");
      // note that the double backslash would be simple if read from the configuration file
      prop.put("fileFilter", "(.*\\.dat)|(.*\\.gif)");
      doPublish(prop, deliverDat, deliverGif, absSubPath);
   }
   
   public void testPublishWithMoveAbsolute() {
      boolean deliverDat = true;
      boolean deliverGif = true;
      boolean absSubPath = true;
      PluginProperties prop = new PluginProperties();
      prop.put("sent", this.dirName + File.separator + "Sent");
      prop.put("discarded", this.dirName + File.separator + "Discarded");
      doPublish(prop, deliverDat, deliverGif, absSubPath);
   }

   public void testPublishWithMoveRelative() {
      boolean deliverDat = true;
      boolean deliverGif = true;
      boolean absSubPath = false;
      PluginProperties prop = new PluginProperties();
      prop.put("sent", "Sent");
      prop.put("discarded", "Discarded");
      doPublish(prop, deliverDat, deliverGif, absSubPath);
   }

   public void testPublishWithMoveRelativeLockMode() {
      boolean deliverDat = true;
      boolean deliverGif = true;
      boolean absSubPath = false;
      PluginProperties prop = new PluginProperties();
      prop.put("sent", "Sent");
      prop.put("discarded", "Discarded");
      prop.put("lockExtention", "*.lck");
      doPublish(prop, deliverDat, deliverGif, absSubPath);
   }

   public void testSimplePublishWithFilterLockMode() {
      boolean deliverDat = false;
      boolean deliverGif = true;
      boolean absSubPath = true;
      PluginProperties prop = new PluginProperties();
      prop.put("fileFilter", "*.gif");
      prop.put("lockExtention", "*.lck");
      doPublish(prop, deliverDat, deliverGif, absSubPath);
   }
   
   /*
      prop.put("sent", "Sent");
      prop.put("discarded", "Discarded");
      prop.put("publishKey", "");
      prop.put("publishQos", "");
      prop.put("connectQos", "");
      prop.put("loginName", "");
      prop.put("password", "");
      prop.put("fileFilter", "");
      prop.put("lockExtention", "*.lck");
*/

   private boolean compareContent(byte[] buf1, byte[] buf2) {
      if (buf1 == null && buf2 == null)
         return true;

      if (buf1 == null || buf2 == null)
         return false;
      
      if (buf1.length != buf2.length)
         return false;
      for (int i=0; i < buf1.length; i++) {
         if (buf1[i] != buf2[i])
            return false;
      }
      return true;
   }
   
   private byte[] writeFile(String filename, int size, String lockExt, long timeToWait) {
      try {
         File lock = null;
         if (lockExt != null) {
            String tmp = filename + lockExt.substring(1);
            lock = new File(tmp);
            boolean ret = lock.createNewFile();
            assertTrue("could not create lock file '" + tmp + "'", ret);
            int upd = this.updateInterceptor.waitOnUpdate(timeToWait);
            assertEquals("when writing lock file should not update", 0, upd);
         }
         else
            this.updateInterceptor.waitOnUpdate(timeToWait);

         byte[] buf = new byte[size];
         for (int i=0; i < size; i++) {
            buf[i] = (byte)i;
         }
         FileOutputStream fos = new FileOutputStream(filename);
         fos.write(buf);
         fos.close();
         
         if (lockExt != null) {
            int upd = this.updateInterceptor.waitOnUpdate(timeToWait);
            assertEquals("when still locked by lockfile should not update", 0, upd);
            File tmp = new File(filename);
            assertTrue("file '" + filename + "' should still exist since lock file exists", tmp.exists());
         }
         else
            this.updateInterceptor.waitOnUpdate(timeToWait);
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
      File file = new File(this.dirName);
      assertTrue("file '" + this.dirName + "' does not exist", file.exists());
      file = new File(this.dirNameSent);
      assertTrue("file '" + this.dirNameSent + "' does not exist", file.exists());
      file = new File(this.dirNameDiscarded);
      assertTrue("file '" + this.dirNameDiscarded + "' does not exist", file.exists());
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
      delete(this.dirNameSent + File.separator + "ok.dat");
      delete(this.dirNameSent + File.separator + "ok.gif");
      delete(this.dirNameSent);
      delete(this.dirNameDiscarded + File.separator + "tooBig.dat");
      delete(this.dirNameDiscarded);
      delete(this.dirName + File.separator + "ok.dat");
      delete(this.dirName + File.separator + "ok.dat.lck");
      delete(this.dirName + File.separator + "ok.gif");
      delete(this.dirName + File.separator + "ok.gif.lck");
      delete(this.dirName + File.separator + "tooBig.dat");
      delete(this.dirName + File.separator + "tooBig.dat.lck");
      
      delete(this.dirName);
   }
   
   
   /**
    * Invoke: java org.xmlBlaster.test.client.TestFilePollerPlugin
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.client.TestFilePollerPlugin</pre>
    */
   public static void main(String args[]) {
      Global global = new Global();
      if (global.init(args) != 0) {
         System.out.println(ME + ": Init failed");
         System.exit(1);
      }

      TestFilePollerPlugin test = new TestFilePollerPlugin(global);
/*
      test.setUp();
      test.testDirectories();
      test.tearDown();

      test.setUp();
      test.testSimplePublish();
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
*/
      test.setUp();
      test.testSimplePublishWithFilterLockMode();
      test.tearDown();
   }
}

