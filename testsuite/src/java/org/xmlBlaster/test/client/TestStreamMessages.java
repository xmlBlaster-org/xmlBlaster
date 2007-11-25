/*-----t-------------------------------------------------------------------------
Name:      TestStreamMessages.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import java.util.logging.Logger;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;

import org.xmlBlaster.client.I_StreamingCallback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.jms.XBConnectionMetaData;
import org.xmlBlaster.jms.XBDestination;
import org.xmlBlaster.jms.XBMessageProducer;
import org.xmlBlaster.jms.XBSession;
import org.xmlBlaster.jms.XBStreamingMessage;
import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.address.CallbackAddress;

import junit.framework.TestCase;


/**
 * <p>
 * This is an interesting example, since it creates a XmlBlaster server instance
 * in the same JVM , but in a separate thread, talking over CORBA with it.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *   java junit.textui.TestRunner -noloading org.xmlBlaster.test.client.TestStreamMessages
 *   java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.TestStreamMessages
 * </pre>
 * @see org.xmlBlaster.client.I_XmlBlasterAccess
 */
public class TestStreamMessages extends TestCase implements I_StreamingCallback {
   private static String ME = "TestStreamMessages";
   private Global global;
   private static Logger log = Logger.getLogger(TestStreamMessages.class.getName());
   private Global connGlobal;
   //private Global publisherGlobal;
   private String oid = "testStreamMessages";
   private MsgInterceptor updateInterceptor;
   private byte[] msgContent;
   private long delay = 5000000L;
   private boolean ignoreException;
   
   public TestStreamMessages() {
      this(null);
   }

   public TestStreamMessages(Global global) {
      super("TestStreamMessages");
      this.global = global;
      if (this.global == null) {
         this.global = new Global();
         this.global.init((String[])null);
      }
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
      try {
         this.connGlobal = this.global.getClone(null);
         // this.publisherGlobal = this.global.getClone(null);
         // this.publisherGlobal.getXmlBlasterAccess().connect(new ConnectQos(this.publisherGlobal, "one/2", "secret"), null);
         
         this.updateInterceptor = new MsgInterceptor(this.connGlobal, log, null, this);
         boolean withQueue = true;
         // we need failsafe behaviour to enable holdback messages on client update exceptions
         ConnectQos connectQos = new ConnectQos(this.connGlobal, "streamingMsgTester/1", "secret");
         connectQos.getAddress().setDelay(5000L);
         connectQos.getAddress().setPingInterval(5000L);
         connectQos.getAddress().setRetries(-1);
         CallbackAddress cbAddr = new CallbackAddress(this.global);
         cbAddr.setDelay(5000L);
         cbAddr.setPingInterval(5000L);
         cbAddr.setRetries(-1);
         connectQos.addCallbackAddress(cbAddr);
         XmlBlasterAccess access = (XmlBlasterAccess)this.connGlobal.getXmlBlasterAccess();
         ConnectReturnQos retQos = access.connect(connectQos, this.updateInterceptor, withQueue);
         log.info("connect return qos: " + retQos.toXml());
         
         SubscribeQos subQos = new SubscribeQos(this.connGlobal);
         subQos.setWantInitialUpdate(false);
         subQos.setMultiSubscribe(false);
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
      try {
         Thread.sleep(1000L); // since the cb could be too fast
         this.connGlobal.getXmlBlasterAccess().unSubscribe(new UnSubscribeKey(this.connGlobal, this.oid), new UnSubscribeQos(this.connGlobal));
         this.connGlobal.getXmlBlasterAccess().disconnect(new DisconnectQos(this.connGlobal));
         this.connGlobal.shutdown();
         this.connGlobal = null;
         // this.publisherGlobal.getXmlBlasterAccess().disconnect(new DisconnectQos(this.publisherGlobal));
         // this.publisherGlobal.shutdown();
         // this.publisherGlobal = null;
      }
      catch (InterruptedException ex) {
         ex.printStackTrace();
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         fail("aborting since exception ex: " + ex.getMessage());
      }
   }

   private final String getMemInfo() {
      StringBuffer buf = new StringBuffer(256);
      final int MEGA = 1024 * 1024;
      buf.append("MEMORY: total='").append(Runtime.getRuntime().totalMemory()/MEGA).append("' ");
      buf.append("max='").append(Runtime.getRuntime().maxMemory()/MEGA).append("' ");
      buf.append("free='").append(Runtime.getRuntime().freeMemory()/MEGA).append("' MB");
      return buf.toString();
   }
   
   public String update(String cbSessionId, UpdateKey updateKey, InputStream is, UpdateQos updateQos) throws XmlBlasterException, IOException {
      
      ClientProperty prop = updateQos.getClientProperty(Constants.addJmsPrefix("interrupted", log));
      boolean doInterrupt = false;
      if (prop != null)
         doInterrupt = prop.getBooleanValue();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] buf = new byte[300];
      int count = 0;
      String name = updateQos.getClientProperty("nameOfTest", "");
      boolean isException = "testException".equals(name);
      log.info("test '" + name + "' before reading: " + getMemInfo());
      while(true) {
         int ret = is.read(buf);
         if (ret == -1 || doInterrupt)
            break;
         baos.write(buf, 0, ret);
         count += ret;
         if (isException && count > 600 && !ignoreException) { // it must pass the second time
            this.ignoreException = true;
            throw new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_ERROR, "fake exception to be hold back (dispatcher must go to false)", "fake");
         }
      }
      log.info("test '" + name + "' before closing input stream: " + getMemInfo());
      is.close();
      log.info("test '" + name + "' after closing: " + getMemInfo());
      this.msgContent = baos.toByteArray();
      byte[] content = this.msgContent;
      log.info("Receiving update of a message oid=" + updateKey.getOid() +
                        " priority=" + updateQos.getPriority() +
                        " state=" + updateQos.getState() +
                        " contentSize=" + content.length);
      this.updateInterceptor.setMsgContent(content);
      return "OK";
   }


   private void doPublish(byte[] content, int maxChunkSize, boolean doInterrupt, String name) throws XmlBlasterException {
      log.info("Publishing for '" + name + "'");
      // Global glob = this.global.getClone(null);
      Global glob = this.connGlobal;
      I_XmlBlasterAccess conn = glob.getXmlBlasterAccess();
      PublishKey key = new PublishKey(glob, this.oid);
      PublishQos qos = new PublishQos(glob);
      qos.setPersistent(true);
      if (doInterrupt)
         qos.addClientProperty("interrupted", true);
      qos.addClientProperty("nameOfTest", name);
      qos.addClientProperty(Constants.addJmsPrefix(XBConnectionMetaData.JMSX_MAX_CHUNK_SIZE, log), maxChunkSize);
      ByteArrayInputStream bais = new ByteArrayInputStream(content);
      conn.publishStream(bais, key.getData(), qos.getData(), maxChunkSize, null);
   }
   
   private void doPublishJMS(byte[] content, int maxChunkSize, boolean doInterrupt, String name) throws JMSException {
      // Global glob = this.global.getClone(null);
      // XBSession session = new XBSession(this.publisherGlobal, XBSession.AUTO_ACKNOWLEDGE, false);
      log.info("Publishing for '" + name + "'");
      XBSession session = new XBSession(this.connGlobal, XBSession.AUTO_ACKNOWLEDGE, false);
      XBMessageProducer producer = new XBMessageProducer(session, new XBDestination(this.oid, null));
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);
      XBStreamingMessage msg = session.createStreamingMessage(null);
      if (doInterrupt)
         msg.setBooleanProperty("interrupted", true);
      msg.setIntProperty(XBConnectionMetaData.JMSX_MAX_CHUNK_SIZE, maxChunkSize);
      msg.setStringProperty("nameOfTest", name); // to recognize it in '__sys__deadMessage'
      ByteArrayInputStream bais = new ByteArrayInputStream(content);
      msg.setInputStream(bais);
      producer.send(msg);
   }
   
   private byte[] createRandomContent(int size) {
      byte[] ret = new byte[size];
      Random random = new Random();
      random.nextBytes(ret);
      return ret;
   }
   
   public void testManyChunks() {
      int maxChunkSize = 128;
      byte[] content = createRandomContent(maxChunkSize*5 - 1);
      try {
         this.updateInterceptor.clear();
         doPublish(content, maxChunkSize, false, "testManyChunks");
         int ret = this.updateInterceptor.waitOnUpdate(this.delay, 1);
         assertEquals("wrong number of updates when testing testManyChunks", 1, ret);
         Msg[] msgs = this.updateInterceptor.getMsgs();
         assertEquals("wrong number of msg entries when testing testManyChunks", 1, msgs.length);
         assertEquals("Wrong size of returned buffer", content.length, msgs[0].getContent().length);
         assertTrue("", compareContent(content, msgs[0].getContent()));
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         fail();
      }
   }

   /**
    * This test is to check that we don't have a problem in the buffer of the Pipes due to 
    * large chunks of messages.
    */
   public void testManyBigChunks() {
      String name = "testManyBigChunks";
      int maxChunkSize = 1000 * 1000; // since JMS implementation does not allow more
      byte[] content = createRandomContent(maxChunkSize*3 -1);
      try {
         this.updateInterceptor.clear();
         doPublish(content, maxChunkSize, false, name);
         int ret = this.updateInterceptor.waitOnUpdate(this.delay, 1);
         assertEquals("wrong number of updates when testing " + name, 1, ret);
         Msg[] msgs = this.updateInterceptor.getMsgs();
         assertEquals("wrong number of msg entries when testing " + name, 1, msgs.length);
         assertEquals("Wrong size of returned buffer", content.length, msgs[0].getContent().length);
         assertTrue("", compareContent(content, msgs[0].getContent()));
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         fail();
      }
   }

   public void testManyChunksTwoMessages() {
      int maxChunkSize = 64;
      byte[] content = createRandomContent(maxChunkSize*5 - 1);
      try {
         this.updateInterceptor.clear();
         doPublish(content, maxChunkSize, false, "testManyChunksTwoMessages1");
         content = createRandomContent(maxChunkSize*5 - 1);
         doPublish(content, maxChunkSize, false, "testManyChunksTwoMessages2");
         int ret = this.updateInterceptor.waitOnUpdate(this.delay, 2);
         assertEquals("wrong number of updates when testing testManyChunksTwoMessages", 2, ret);
         Msg[] msgs = this.updateInterceptor.getMsgs();
         assertEquals("wrong number of msg entries when testing testManyChunksTwoMessages", 2, msgs.length);
         assertEquals("Wrong size of returned buffer", content.length, msgs[1].getContent().length);
         assertTrue("", compareContent(content, msgs[1].getContent()));
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         fail();
      }
   }

   public void testSingleChunk() {
      int maxChunkSize = 200;
      byte[] content = createRandomContent(maxChunkSize-10);
      try {
         this.updateInterceptor.clear();
         doPublish(content, maxChunkSize, false, "testSingleChunk");
         int ret = this.updateInterceptor.waitOnUpdate(this.delay, 1);
         assertEquals("wrong number of updates when testing testSingleChunk", 1, ret);
         Msg[] msgs = this.updateInterceptor.getMsgs();
         assertEquals("wrong number of msg entries when testing testSingleChunk", 1, msgs.length);
         assertEquals("Wrong size of returned buffer", content.length, msgs[0].getContent().length);
         assertTrue("", compareContent(content, msgs[0].getContent()));
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         fail();
      }
   }

   public void testException() {
      int maxChunkSize = 200;
      byte[] content = createRandomContent(900);
      try {
         this.updateInterceptor.clear();
         doPublish(content, maxChunkSize, false, "testException");
         int ret = this.updateInterceptor.waitOnUpdate(2000L, 1);
         assertEquals("wrong number of updates when testing testSingleChunk", 0, ret);
         this.updateInterceptor.clear();
         ((XmlBlasterAccess)this.connGlobal.getXmlBlasterAccess()).setCallbackDispatcherActive(true);
         ret = this.updateInterceptor.waitOnUpdate(this.delay, 1);
         Msg[] msgs = this.updateInterceptor.getMsgs();
         assertEquals("wrong number of msg entries when testing testSingleChunk", 1, msgs.length);
         assertEquals("Wrong size of returned buffer", content.length, msgs[0].getContent().length);
         assertTrue("", compareContent(content, msgs[0].getContent()));
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         fail();
      }
   }

   public void testInterruptedRead() {
      int maxChunkSize = 256;
      byte[] content = createRandomContent(maxChunkSize*5-10);
      try {
         this.updateInterceptor.clear();
         doPublish(content, maxChunkSize, true /* interruped */, "testSingleChunk");
         int ret = this.updateInterceptor.waitOnUpdate(this.delay, 1);
         assertEquals("wrong number of updates when testing testSingleChunk", 1, ret);
         Msg[] msgs = this.updateInterceptor.getMsgs();
         assertEquals("wrong number of msg entries when testing testSingleChunk", 1, msgs.length);
         assertTrue("", content.length > msgs[0].getContent().length);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         fail();
      }
   }

   public void testNormalMessage() {
      int maxChunkSize = 500;
      byte[] content = createRandomContent(maxChunkSize);
      try {
         this.updateInterceptor.clear();
         this.connGlobal.getXmlBlasterAccess().publish(new MsgUnit(new PublishKey(this.connGlobal, this.oid), content, new PublishQos(this.connGlobal)));
         int ret = this.updateInterceptor.waitOnUpdate(this.delay, 1);
         assertEquals("wrong number of updates when testing testSingleChunk", 1, ret);
         Msg[] msgs = this.updateInterceptor.getMsgs();
         assertEquals("wrong number of msg entries when testing testSingleChunk", 1, msgs.length);
         assertEquals("Wrong size of returned buffer", content.length, msgs[0].getContent().length);
         assertTrue("", compareContent(content, msgs[0].getContent()));
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         fail();
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
   
   /**
    * Invoke: java org.xmlBlaster.test.client.TestStreamMessages
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.client.TestStreamMessages</pre>
    */
   public static void main(String args[]) {
      Global global = new Global();
      if (global.init(args) != 0) {
         System.out.println(ME + ": Init failed");
         System.exit(1);
      }

      TestStreamMessages test = new TestStreamMessages(global);

      test.setUp();
      test.testManyBigChunks();
      test.tearDown();

      test.setUp();
      test.testManyChunks();
      test.tearDown();

      test.setUp();
      test.testException();
      test.tearDown();
      
      test.setUp();
      test.testSingleChunk();
      test.tearDown();

      test.setUp();
      test.testInterruptedRead();
      test.tearDown();

      test.setUp();
      test.testNormalMessage();
      test.tearDown();

      test.setUp();
      test.testManyChunksTwoMessages();
      test.tearDown();
   }
}

