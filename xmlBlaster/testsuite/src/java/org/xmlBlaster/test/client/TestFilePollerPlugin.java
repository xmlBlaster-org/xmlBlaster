/*------------------------------------------------------------------------------
Name:      TestPollerPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.jutils.log.LogChannel;
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
   private LogChannel log;
   private Global connGlobal;
   private String oid = "filepollerTest";
   private String dirName = "/tmp/testsuitePoller";
   private String dirNameSent = "/tmp/testsuitePoller/Sent";
   private String dirNameDiscarded = "/tmp/testsuitePoller/Discarded";
   
   private MsgInterceptor updateInterceptor;

   public TestFilePollerPlugin() {
      this(null);
   }

   public TestFilePollerPlugin(Global global) {
      super("TestFilePollerPlugin");
      this.global = global;
      if (this.global == null) {
         this.global = new Global();
         this.global.init((String[])null);
      }
      this.log = this.global.getLog("test");
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
      try {
         this.connGlobal = this.global.getClone(null);
         this.updateInterceptor = new MsgInterceptor(this.connGlobal, this.log, null);
         this.connGlobal.getXmlBlasterAccess().connect(new ConnectQos(this.connGlobal), this.updateInterceptor);
         this.connGlobal.getXmlBlasterAccess().subscribe(new SubscribeKey(this.connGlobal, this.oid), new SubscribeQos(this.connGlobal));
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
      log.info(ME, "Entering tearDown(), test is finished");
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
      this.log.info(ME, "Receiving update of a message oid=" + updateKey.getOid() +
                        " priority=" + updateQos.getPriority() +
                        " state=" + updateQos.getState() +
                        " content=" + cont);
      this.log.info(ME, "further log for receiving update of a message cbSessionId=" + cbSessionId +
                     updateKey.toXml() + "\n" + new String(content) + updateQos.toXml());
      this.log.error(ME, "update: should never be invoked (msgInterceptors take care of it since they are passed on subscriptions)");

      return "OK";
   }

   /**
    * Tests the creation of the necessary directories
    *
    */
   public void testDirectories() {
      // absolute path
      Properties prop = new Properties();
      prop.put("topicName", "dummy");
      prop.put("directoryName", this.dirName);
      prop.put("sent", this.dirNameSent);
      prop.put("discarded", this.dirNameDiscarded);
      try {
         Publisher publisher = new Publisher(this.global, "test", prop);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue("An exception should not occur here " + ex.getMessage(), false);
      }
      checkDirs();

      // repeat that with already existing directories
      prop = new Properties();
      prop.put("topicName", "dummy");
      prop.put("directoryName", this.dirName);
      prop.put("sent", this.dirNameSent);
      prop.put("discarded", this.dirNameDiscarded);
      try {
         Publisher publisher = new Publisher(this.global, "test", prop);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue("An exception should not occur here " + ex.getMessage(), false);
      }
      checkDirs();
      cleanUpDirs();

      // relative path are added to the 'directoryName'
      prop = new Properties();
      prop.put("topicName", "dummy");
      prop.put("directoryName", this.dirName);
      prop.put("sent", "Sent");
      prop.put("discarded", "Discarded");
      try {
         Publisher publisher = new Publisher(this.global, "test", prop);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue("An exception should not occur here " + ex.getMessage(), false);
      }
      checkDirs();
      // relative path are added to the 'directoryName' repeat with existing directories
      prop = new Properties();
      prop.put("topicName", "dummy");
      prop.put("directoryName", this.dirName);
      prop.put("sent", "Sent");
      prop.put("discarded", "Discarded");
      try {
         Publisher publisher = new Publisher(this.global, "test", prop);
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
      
      prop = new Properties();
      prop.put("topicName", "dummy");
      prop.put("directoryName", this.dirName);
      prop.put("sent", "Sent");
      prop.put("discarded", "Discarded");
      try {
         Publisher publisher = new Publisher(this.global, "test", prop);
         assertTrue("an exception should occur since '" + this.dirName + "' is a file and should be a directory", false);
      }
      catch (XmlBlasterException ex) {
         this.log.info(ME, "Exception is OK here");
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
      
      prop = new Properties();
      prop.put("topicName", "dummy");
      prop.put("directoryName", this.dirName);
      prop.put("sent", "Sent");
      prop.put("discarded", "Discarded");
      try {
         Publisher publisher = new Publisher(this.global, "test", prop);
         assertTrue("an exception should occur since '" + this.dirName + "' is a file and should be a directory", false);
      }
      catch (XmlBlasterException ex) {
         this.log.info(ME, "Exception is OK here");
      }
      cleanUpDirs();
   }

   
   
   public void testSimplePublish() {
      Properties prop = new Properties();
      prop.put("topicName", this.oid);
      prop.put("directoryName", this.dirName);
/*
      prop.put("sent", "Sent");
      prop.put("discarded", "Discarded");
      prop.put("publishKey", "");
      prop.put("publishQos", "");
      prop.put("connectQos", "");
      prop.put("loginName", "");
      prop.put("password", "");
      prop.put("fileFilter", "");
      prop.put("lockExtention", ".lck");
*/
      int maximumSize = 10000;
      long delaySinceLastChange = 500L;
      long pollInterval = 250L;
      prop.put("maximumFileSize", "" + maximumSize);
      prop.put("delaySinceLastFileChange", "" + delaySinceLastChange);
      prop.put("pollInterval", "" + pollInterval);
      prop.put("warnOnEmptyFileDelay", "1000L");
      
      org.xmlBlaster.engine.Global engineGlobal = new org.xmlBlaster.engine.Global();
      prop.put("connectQos", this.getConnectQos(engineGlobal));
      
      Publisher publisher = null;
      try {
         publisher = new Publisher(engineGlobal, "test", prop);
         publisher.init();

         this.updateInterceptor.clear();
         // too big
         String tooBig = this.dirName + File.separator + "tooBig.dat";
         writeFile(tooBig, maximumSize+1);
         int ret = this.updateInterceptor.waitOnUpdate(delaySinceLastChange* 2);
         assertEquals("expected no updates", 0, ret);
         File tmp = new File(tooBig);
         assertFalse("the file '" + tooBig + "' should have been removed", tmp.exists());
         
         this.updateInterceptor.clear();
         // too big
         String okFile = this.dirName + File.separator + "ok.dat";
         byte[] okBuf = writeFile(okFile, maximumSize-1);
         ret = this.updateInterceptor.waitOnUpdate(delaySinceLastChange* 2);
         assertEquals("expected one update", 1, ret);
         tmp = new File(okFile);
         assertFalse("the file '" + okFile + "' should have been removed", tmp.exists());
         
         boolean sameContent = compareContent(okBuf, this.updateInterceptor.getMsgs()[0].getContent());
         assertTrue("the content of the file is not the same as the arrived content of the update method", sameContent);
         String fileName = this.updateInterceptor.getMsgs()[0].getUpdateQos().getClientProperty("_fileName", (String)null);
         assertNotNull("The fileName is null", fileName);
         assertEquals("", "ok.dat", fileName);
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
   
   private byte[] writeFile(String filename, int size) {
      try {
         byte[] buf = new byte[size];
         for (int i=0; i < size; i++) {
            buf[i] = (byte)i;
         }
         FileOutputStream fos = new FileOutputStream(filename);
         fos.write(buf);
         fos.close();
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
      delete(this.dirNameSent);
      delete(this.dirNameDiscarded);
      delete(this.dirName + File.separator + "ok.dat");
      delete(this.dirName + File.separator + "tooBig.dat");
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

      test.setUp();
      test.testDirectories();
      test.tearDown();

      test.setUp();
      test.testSimplePublish();
      test.tearDown();

   }
}

