package org.xmlBlaster.test.classtest.msgstore;

import org.jutils.log.LogChannel;
import org.jutils.time.StopWatch;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.queue.I_EntryFactory;
import org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin;
import org.xmlBlaster.util.queue.cache.CacheQueueInterceptorPlugin;
import org.xmlBlaster.util.queue.jdbc.JdbcManager;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.msgstore.ram.MapPlugin;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.engine.qos.PublishQosServer;
import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.qos.PublishQosServer;
import org.xmlBlaster.client.key.PublishKey;

import java.util.ArrayList;
import java.util.StringTokenizer;

import junit.framework.*;

/**
 * Tests (de)serialize of MsgUnitWrapper. 
 * <p>
 * Invoke: java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.classtest.msgstore.MsgUnitWrapperTest
 * </p>
 * @see org.xmlBlaster.engine.MsgUnitWrapper
 * @see org.xmlBlaster.engine.queuemsg.ServerEntryFactory
 */
public class MsgUnitWrapperTest extends TestCase {
   private String ME = "MsgUnitWrapperTest";
   protected Global glob;
   protected LogChannel log;
   private StopWatch stopWatch = new StopWatch();

   public MsgUnitWrapperTest(String name) {
      super(name);
      this.glob = new Global();
      this.log = this.glob.getLog("test");
   }

   protected void setUp() {
   }

   /**
    * Tests initialize(), getProperties(), setProperties() and capacity()
    * @param queue !!!Is not initialized in this case!!!!
    */
   public void testSerialize() {
      ME = "MsgUnitWrapperTest.testSerialize()";
      System.out.println("***" + ME);

      try {
         PublishKey publishKey = new PublishKey(glob, "HA", "text/xy", "1.7");
         Timestamp timestamp = new Timestamp();
         String xml =
            "<qos>\n" +
            "   <destination queryType='EXACT' forceQueuing='true'>\n" +
            "      Tim\n" +
            "   </destination>\n" +
            "   <destination queryType='EXACT'>\n" +
            "      Ben\n" +
            "   </destination>\n" +
            "   <sender>\n" +
            "      Gesa\n" +
            "   </sender>\n" +
            "   <priority>7</priority>\n" +
            "   <expiration lifeTime='2400' remainingLife='12000'/>\n" +
            "   <rcvTimestamp nanos='" + timestamp.getTimestamp() + "'/>\n" + // if from persistent store
            "   <isDurable/>\n" +
            "   <forceUpdate>false</forceUpdate>\n" +
            "   <route>\n" +
            "      <node id='bilbo' stratum='2' timestamp='9408630500' dirtyRead='true'/>\n" +
            "      <node id='frodo' stratum='1' timestamp='9408630538' dirtyRead='false'/>\n" +
            "      <node id='heron' stratum='0' timestamp='9408630564'/>\n" +
            "   </route>\n" +
            "   <topic readonly='true'/>\n" +
            "</qos>\n";

         PublishQosServer publishQosServer = new PublishQosServer(glob, xml, true); // true prevents new timestamp
         MsgUnit msgUnit = new MsgUnit(glob, publishKey.getData(), "HO".getBytes(), publishQosServer.getData());
         StorageId storageId = new StorageId("mystore", "someid");
         MsgUnitWrapper msgUnitWrapper = new MsgUnitWrapper(glob, msgUnit, storageId);

         I_EntryFactory factory = glob.getEntryFactory(storageId.getStrippedId());

         /* MsgUnitWrapper.getOwnerCache() fails with this test as RequestBroker is unknown
         msgUnitWrapper.incrementReferenceCounter(4);
         assertEquals("", 4, msgUnitWrapper.getReferenceCounter());
         msgUnitWrapper.incrementReferenceCounter(-4);
         assertEquals("", 0, msgUnitWrapper.getReferenceCounter());
         */

         int priority = msgUnitWrapper.getPriority();
         long uniqueId = msgUnitWrapper.getUniqueId();
         String type = msgUnitWrapper.getEmbeddedType();
         boolean isDurable = msgUnitWrapper.isDurable();
         byte[] blob = factory.toBlob(msgUnitWrapper);

         MsgUnitWrapper newWrapper = (MsgUnitWrapper)factory.createEntry(priority,
                                        uniqueId, type, isDurable, blob, storageId);
 
         assertEquals("", msgUnitWrapper.getPriority(), newWrapper.getPriority());
         assertEquals("", msgUnitWrapper.getReferenceCounter(), newWrapper.getReferenceCounter()); // A reference counter is reset to 0 when loaded from persistence
         assertEquals("", msgUnitWrapper.isExpired(), newWrapper.isExpired());
         assertEquals("", msgUnitWrapper.isDurable(), newWrapper.isDurable());
         assertEquals("", msgUnitWrapper.getMsgUnit().getContentStr(), newWrapper.getMsgUnit().getContentStr());
         assertEquals("", msgUnitWrapper.getKeyOid(), newWrapper.getKeyOid());
         assertEquals("", msgUnitWrapper.getContentMime(), newWrapper.getContentMime());
         assertEquals("", msgUnitWrapper.getContentMimeExtended(), newWrapper.getContentMimeExtended());
         assertEquals("", msgUnitWrapper.getDomain(), newWrapper.getDomain());
         assertEquals("", msgUnitWrapper.getSizeInBytes(), newWrapper.getSizeInBytes());
         assertEquals("", msgUnitWrapper.getUniqueId(), newWrapper.getUniqueId());
         assertEquals("", msgUnitWrapper.getLogId(), newWrapper.getLogId());
         assertEquals("", msgUnitWrapper.isInternal(), newWrapper.isInternal());
         assertEquals("", msgUnitWrapper.getEmbeddedType(), newWrapper.getEmbeddedType());

         MsgQosData qos = newWrapper.getMsgQosData();
         assertEquals("", false, qos.isVolatile());
         assertEquals("", true, qos.isDurable());
         assertEquals("", true, qos.isReadonly());
         assertEquals("", "Gesa", qos.getSender().getLoginName());

         assertEquals("", 3, qos.getRouteNodes().length);
         assertEquals("", 2, qos.getRouteNodes()[0].getStratum());
         assertEquals("", 0, qos.getRouteNodes()[2].getStratum());
         assertEquals("", 9408630500L, qos.getRouteNodes()[0].getTimestamp().getTimestamp());
         assertEquals("", true, qos.getRouteNodes()[0].getDirtyRead());
         assertEquals("", false, qos.getRouteNodes()[1].getDirtyRead());
         assertEquals("", false, qos.getRouteNodes()[2].getDirtyRead());

         assertEquals("", PriorityEnum.HIGH_PRIORITY.toString(), qos.getPriority().toString());
         assertEquals("", timestamp.getTimestamp(), qos.getRcvTimestamp().getTimestamp());
 
         System.out.println("SUCESS BEFORE: " + msgUnitWrapper.toXml());
         System.out.println("SUCESS AFTER: " + newWrapper.toXml());

         // The remaing life changes so we can't compare the XML strings directly:
         //assertEquals("OLD="+msgUnitWrapper.getMsgQosData().toXml()+"\nNEW="+newWrapper.getMsgQosData().toXml(), msgUnitWrapper.getMsgQosData().toXml(), newWrapper.getMsgQosData().toXml());
         assertEquals("", msgUnitWrapper.getMsgQosData().toXml().length(), newWrapper.getMsgQosData().toXml().length());
         assertEquals("", msgUnitWrapper.getMsgKeyData().toXml(), newWrapper.getMsgKeyData().toXml());

         assertTrue("Not different instances", msgUnitWrapper != newWrapper);

         log.info(ME, "*** [SUCCESS]");
      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }

   public void tearDown() {
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
      TestSuite suite= new TestSuite();
      suite.addTest(new MsgUnitWrapperTest("testSerialize"));
      return suite;
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.msgstore.MsgUnitWrapperTest
    * </pre>
    */
   public static void main(String args[]) {
      Global glob = new Global(args);
      MsgUnitWrapperTest testSub = new MsgUnitWrapperTest("MsgUnitWrapperTest");
      long startTime = System.currentTimeMillis();

      testSub.setUp();
      testSub.testSerialize();
      testSub.tearDown();

      long usedTime = System.currentTimeMillis() - startTime;
      System.out.println("time used for tests: " + usedTime/1000 + " seconds");
   }
}

