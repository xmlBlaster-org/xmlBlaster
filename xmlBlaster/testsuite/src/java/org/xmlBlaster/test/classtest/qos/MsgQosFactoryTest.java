package org.xmlBlaster.test.classtest.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.MsgQosSaxFactory;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.client.qos.GetReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.qos.storage.HistoryQueueProperty;
import org.xmlBlaster.util.qos.storage.TopicCacheProperty;
import org.xmlBlaster.engine.qos.PublishQosServer;

import junit.framework.*;

/**
 * Test MsgQosSaxFactory. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.qos.MsgQosFactoryTest
 * @see org.xmlBlaster.util.qos.MsgQosSaxFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html" target="others">the interface.publish requirement</a>
 */
public class MsgQosFactoryTest extends TestCase {
   private final String ME = "MsgQosFactoryTest";
   protected Global glob;
   protected LogChannel log;
   int counter = 0;

   public MsgQosFactoryTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();
      this.log = glob.getLog("test");
   }

   /**
    * Tries with all known tags
    */
   public void testParse() {
      System.out.println("***MsgQosFactoryTest: testParse ...");
      
      try {
         String xml =
            "<qos>\n" +
            "   <state id='AA' info='SOMETHING'/>\n" +
            "   <subscribeable>true</subscribeable>\n" +
            "   <destination queryType='EXACT' forceQueuing='true'>\n" +
            "      Tim\n" +
            "   </destination>\n" +
            "   <destination queryType='EXACT'>\n" +
            "      Ben\n" +
            "   </destination>\n" +
            /*
            "   <destination queryType='XPATH'>\n" +
            "      //[GROUP='Manager']\n" +
            "   </destination>\n" +
            "   <destination queryType='XPATH'>\n" +
            "      //ROLE/[@id='Developer']\n" +
            "   </destination>\n" +
            */
            "   <sender>\n" +
            "      Gesa\n" +
            "   </sender>\n" +
            "   <priority>MIN</priority>\n" +
            //"   <expiration lifeTime='2400' remainingLife='12000'/>\n" + // uncomment as it is in concurrence to isVolatile
            "   <isDurable/>\n" +
            "   <forceUpdate>false</forceUpdate>\n" +
            "   <route>\n" +
            "      <node id='bilbo' stratum='2' timestamp='9408630500' dirtyRead='true'/>\n" +
            "      <node id='frodo' stratum='1' timestamp='9408630538' dirtyRead='false'/>\n" +
            "      <node id='heron' stratum='0' timestamp='9408630564'/>\n" +
            "   </route>\n" +
            "</qos>\n";

         MsgQosSaxFactory factory = new MsgQosSaxFactory(glob);
         MsgQosData qos = factory.readObject(xml);

         assertEquals("", "AA", qos.getState());
         assertEquals("", "SOMETHING", qos.getStateInfo());
         assertEquals("", true, qos.isPtp());
         assertEquals("", true, qos.isDurable());
         assertEquals("", false, qos.isForceUpdate());
         assertEquals("", false, qos.isReadonly());
         assertEquals("", "Gesa", qos.getSender().getLoginName());

         assertEquals("", 0L, qos.getLifeTime()); // isVolatile=true
         assertEquals("", 0L, qos.getRemainingLifeStatic());

         assertEquals("", 3, qos.getRouteNodes().length);
         assertEquals("", 2, qos.getRouteNodes()[0].getStratum());
         assertEquals("", 0, qos.getRouteNodes()[2].getStratum());
         assertEquals("", 9408630500L, qos.getRouteNodes()[0].getTimestamp().getTimestamp());
         assertEquals("", true, qos.getRouteNodes()[0].getDirtyRead());
         assertEquals("", false, qos.getRouteNodes()[1].getDirtyRead());
         assertEquals("", false, qos.getRouteNodes()[2].getDirtyRead());

         assertEquals("", PriorityEnum.MIN_PRIORITY, qos.getPriority());
         assertEquals("", false, qos.isFromPersistenceStore());
         assertTrue("no receive timestamp expected", qos.getRcvTimestamp() == null);
         assertEquals("", true, qos.isSubscribeable());
         assertEquals("", 2, qos.getDestinations().size());
         assertEquals("", true, ((Destination)qos.getDestinations().get(0)).forceQueuing());
         assertEquals("", true, ((Destination)qos.getDestinations().get(0)).isExactAddress());
         assertEquals("", false, ((Destination)qos.getDestinations().get(0)).isXPathQuery());
         // XPath is currently not supported
         //assertEquals("", false, ((Destination)qos.getDestinations().get(2)).isExactAddress());
         //assertEquals("", true, ((Destination)qos.getDestinations().get(2)).isXPathQuery());
      }
      catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      System.out.println("***MsgQosFactoryTest: testParse [SUCCESS]");
   }

   /**
    * Tries with all known tags
    */
   public void testToXml() {
      System.out.println("***MsgQosFactoryTest: testToXml ...");
      
      try {
         String xml =
            "<qos>\n" +
            "   <state id='AA' info='SOMETHING'/>\n" +
            "   <subscribeable>false</subscribeable>\n" +
            "   <destination queryType='EXACT' forceQueuing='true'>\n" +
            "      Tim\n" +
            "   </destination>\n" +
            "   <destination queryType='EXACT'>\n" +
            "      Ben\n" +
            "   </destination>\n" +
            /*
            "   <destination queryType='XPATH'>\n" +
            "      //[GROUP='Manager']\n" +
            "   </destination>\n" +
            "   <destination queryType='XPATH'>\n" +
            "      //ROLE/[@id='Developer']\n" +
            "   </destination>\n" +
            */
            "   <sender>\n" +
            "      Gesa\n" +
            "   </sender>\n" +
            "   <priority>MIN</priority>\n" +
            "   <expiration lifeTime='2400' remainingLife='12000' forceDestroy='true'/>\n" +
            "   <rcvTimestamp nanos='1234'/>\n" +
            "   <isDurable/>\n" +
            "   <forceUpdate>false</forceUpdate>\n" +
            "   <route>\n" +
            "      <node id='bilbo' stratum='2' timestamp='9408630500' dirtyRead='true'/>\n" +
            "      <node id='frodo' stratum='1' timestamp='9408630538' dirtyRead='false'/>\n" +
            "      <node id='heron' stratum='0' timestamp='9408630564'/>\n" +
            "   </route>\n" +
            "   <topic readonly='true' destroyDelay='120000' createDomEntry='true'>\n" +
            "      <msgstore relating='topic' type='TO' version='3.0' maxMsg='4' maxBytes='40' onOverflow='deadMessage'/>\n" +
            "      <queue relating='history' type='HI' version='2.0' maxMsg='3' maxBytes='30' onOverflow='deadMessage'/>\n" +
            "   </topic>\n" +
            "</qos>\n";

         MsgQosSaxFactory factory = new MsgQosSaxFactory(glob);
         MsgQosData qos = factory.readObject(xml);
         String newXml = qos.toXml();
         log.info(ME, "lifeTime=" + qos.getLifeTimeProp().toXml());
         log.info(ME, "New XML=" + newXml);
         qos = factory.readObject(newXml);

         assertEquals("", "AA", qos.getState());
         assertEquals("", "SOMETHING", qos.getStateInfo());
         assertEquals("", true, qos.isPtp());
         assertEquals("", false, qos.isVolatile());
         assertEquals("", true, qos.isDurable());
         assertEquals("", false, qos.isForceUpdate());
         assertEquals("", true, qos.isReadonly());
         assertEquals("", "Gesa", qos.getSender().getLoginName());

         assertEquals("", 2400L, qos.getLifeTime());
         assertEquals("", 12000L, qos.getRemainingLifeStatic());
         assertEquals("", 1234L, qos.getRcvTimestamp().getTimestamp());
         assertEquals("", true, qos.isForceDestroy());
         //assertTrue("no receive timestamp expected", qos.getRcvTimestamp() == null);

         assertEquals("", 3, qos.getRouteNodes().length);
         assertEquals("", 2, qos.getRouteNodes()[0].getStratum());
         assertEquals("", 0, qos.getRouteNodes()[2].getStratum());
         assertEquals("", 9408630500L, qos.getRouteNodes()[0].getTimestamp().getTimestamp());
         assertEquals("", true, qos.getRouteNodes()[0].getDirtyRead());
         assertEquals("", false, qos.getRouteNodes()[1].getDirtyRead());
         assertEquals("", false, qos.getRouteNodes()[2].getDirtyRead());

         assertEquals("", PriorityEnum.MIN_PRIORITY, qos.getPriority());
         assertEquals("", false, qos.isFromPersistenceStore());
         assertEquals("", false, qos.isSubscribeable());
         assertEquals("", 2, qos.getDestinations().size());
         assertEquals("", true, ((Destination)qos.getDestinations().get(0)).forceQueuing());
         assertEquals("", true, ((Destination)qos.getDestinations().get(0)).isExactAddress());
         assertEquals("", false, ((Destination)qos.getDestinations().get(0)).isXPathQuery());
         // XPATH is currently not supported
         //assertEquals("", false, ((Destination)qos.getDestinations().get(2)).isExactAddress());
         //assertEquals("", true, ((Destination)qos.getDestinations().get(2)).isXPathQuery());

         assertEquals("", true, qos.hasTopicProperty());
         TopicProperty topicProperty = qos.getTopicProperty();
         assertEquals("", true, topicProperty.isReadonly());
         assertEquals("", 120000, topicProperty.getDestroyDelay());
         assertEquals("", true, topicProperty.createDomEntry());

         assertEquals("", true, topicProperty.hasTopicCacheProperty());
         TopicCacheProperty cache = topicProperty.getTopicCacheProperty();
         assertEquals("", "topic", cache.getRelating());
         assertEquals("", "TO", cache.getType());
         assertEquals("", "3.0", cache.getVersion());
         assertEquals("", 4L, cache.getMaxMsg());
         assertEquals("", 40L, cache.getMaxBytes());
         assertEquals("", "deadMessage", cache.getOnOverflow());

         assertEquals("", true, topicProperty.hasHistoryQueueProperty());
         HistoryQueueProperty hist = topicProperty.getHistoryQueueProperty();
         assertEquals("", "history", hist.getRelating());
         assertEquals("", "HI", hist.getType());
         assertEquals("", "2.0", hist.getVersion());
         assertEquals("", 3L, hist.getMaxMsg());
         assertEquals("", 30L, hist.getMaxBytes());
         assertEquals("", "deadMessage", hist.getOnOverflow());
      }
      catch (XmlBlasterException e) {
         fail("testToXml failed: " + e.toString());
      }

      System.out.println("***MsgQosFactoryTest: testToXml [SUCCESS]");
   }

   /**
    * Tries with all known tags
    */
   public void testPublishQosServer() {
      System.out.println("***MsgQosFactoryTest: testPublishQosServer ...");
      
      try {
         String xml =
            "<qos>\n" +
            "   <destination queryType='EXACT' forceQueuing='true'>\n" +
            "      Tim\n" +
            "   </destination>\n" +
            "   <destination queryType='EXACT'>\n" +
            "      Ben\n" +
            "   </destination>\n" +
            /*
            "   <destination queryType='XPATH'>\n" +
            "      //[GROUP='Manager']\n" +
            "   </destination>\n" +
            "   <destination queryType='XPATH'>\n" +
            "      //ROLE/[@id='Developer']\n" +
            "   </destination>\n" +
            */
            "   <sender>\n" +
            "      Gesa\n" +
            "   </sender>\n" +
            "   <priority>7</priority>\n" +
            "   <expiration lifeTime='2400' remainingLife='12000'/>\n" +
            "   <isDurable/>\n" +
            "   <forceUpdate>false</forceUpdate>\n" +
            "   <route>\n" +
            "      <node id='bilbo' stratum='2' timestamp='9408630500' dirtyRead='true'/>\n" +
            "      <node id='frodo' stratum='1' timestamp='9408630538' dirtyRead='false'/>\n" +
            "      <node id='heron' stratum='0' timestamp='9408630564'/>\n" +
            "   </route>\n" +
            "</qos>\n";

         PublishQosServer qos = new PublishQosServer(new org.xmlBlaster.engine.Global(), xml);

         assertEquals("", true, qos.isSubscribeable());
         assertEquals("", true, qos.isPtp());
         assertEquals("", false, qos.isVolatile());
         assertEquals("", true, qos.isDurable());
         assertEquals("", false, qos.isForceUpdate());
         assertEquals("", false, qos.isReadonly());
         assertEquals("", "Gesa", qos.getSender().getLoginName());

         assertEquals("", 3, qos.getRouteNodes().length);
         assertEquals("", 2, qos.getRouteNodes()[0].getStratum());
         assertEquals("", 0, qos.getRouteNodes()[2].getStratum());
         assertEquals("", 9408630500L, qos.getRouteNodes()[0].getTimestamp().getTimestamp());
         assertEquals("", true, qos.getRouteNodes()[0].getDirtyRead());
         assertEquals("", false, qos.getRouteNodes()[1].getDirtyRead());
         assertEquals("", false, qos.getRouteNodes()[2].getDirtyRead());

         assertEquals("", PriorityEnum.HIGH_PRIORITY, qos.getPriority());
         assertEquals("", false, qos.isFromPersistenceStore());
         Timestamp timestamp = new Timestamp();
         assertTrue("timestamp.getTimestamp()="+timestamp.getTimestamp()+" qos.getRcvTimestamp().getTimestamp()="+qos.getRcvTimestamp().getTimestamp() , timestamp.getTimestamp() > qos.getRcvTimestamp().getTimestamp());
         assertTrue("timestamp.getTimestamp()="+timestamp.getTimestamp()+" qos.getRcvTimestamp().getTimestamp()="+qos.getRcvTimestamp().getTimestamp() , timestamp.getTimestamp() < (qos.getRcvTimestamp().getTimestamp()+10000000));
         assertEquals("", 2, qos.getDestinations().size());
         assertEquals("", true, ((Destination)qos.getDestinations().get(0)).forceQueuing());
      }
      catch (XmlBlasterException e) {
         fail("testPublishQosServer failed: " + e.toString());
      }

      System.out.println("***MsgQosFactoryTest: testPublishQosServer [SUCCESS]");
   }

   /**
    * Tries with all known tags
    */
   public void testGetReturnQos() {
      System.out.println("***MsgQosFactoryTest: testGetReturnQos ...");
      
      try {
         Timestamp timestamp = new Timestamp();
         String xml =
            "<qos>\n" +
            "   <destination queryType='EXACT' forceQueuing='true'>\n" +
            "      Tim\n" +
            "   </destination>\n" +
            "   <destination queryType='EXACT'>\n" +
            "      Ben\n" +
            "   </destination>\n" +
            /*
            "   <destination queryType='XPATH'>\n" +
            "      //[GROUP='Manager']\n" +
            "   </destination>\n" +
            "   <destination queryType='XPATH'>\n" +
            "      //ROLE/[@id='Developer']\n" +
            "   </destination>\n" +
            */
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
            "</qos>\n";

         GetReturnQos qos = new GetReturnQos(glob, xml);

         assertEquals("", false, qos.isVolatile());
         assertEquals("", true, qos.isDurable());
         assertEquals("", false, qos.isReadonly());
         assertEquals("", "Gesa", qos.getSender().getLoginName());

         assertEquals("", 3, qos.getRouteNodes().length);
         assertEquals("", 2, qos.getRouteNodes()[0].getStratum());
         assertEquals("", 0, qos.getRouteNodes()[2].getStratum());
         assertEquals("", 9408630500L, qos.getRouteNodes()[0].getTimestamp().getTimestamp());
         assertEquals("", true, qos.getRouteNodes()[0].getDirtyRead());
         assertEquals("", false, qos.getRouteNodes()[1].getDirtyRead());
         assertEquals("", false, qos.getRouteNodes()[2].getDirtyRead());

         assertEquals("", PriorityEnum.HIGH_PRIORITY, qos.getPriority());
         assertEquals("", timestamp.getTimestamp(), qos.getRcvTimestamp().getTimestamp());
      }
      catch (XmlBlasterException e) {
         fail("testGetReturnQos failed: " + e.toString());
      }

      System.out.println("***MsgQosFactoryTest: testGetReturnQos [SUCCESS]");
   }

   /**
    * Tries with all known tags
    */
   public void testUpdateQos() {
      System.out.println("***MsgQosFactoryTest: testUpdateQos ...");
      
      try {
         Timestamp timestamp = new Timestamp();
         String xml =
            "<qos>\n" +
            "   <destination queryType='EXACT' forceQueuing='true'>\n" +
            "      Tim\n" +
            "   </destination>\n" +
            "   <destination queryType='EXACT'>\n" +
            "      Ben\n" +
            "   </destination>\n" +
            /*
            "   <destination queryType='XPATH'>\n" +
            "      //[GROUP='Manager']\n" +
            "   </destination>\n" +
            "   <destination queryType='XPATH'>\n" +
            "      //ROLE/[@id='Developer']\n" +
            "   </destination>\n" +
            */
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

         UpdateQos qos = new UpdateQos(glob, xml);

         assertEquals("", true, qos.isSubscribeable());
         assertEquals("", true, qos.isPtp());
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

         assertEquals("", PriorityEnum.HIGH_PRIORITY, qos.getPriority());
         assertEquals("", timestamp.getTimestamp(), qos.getRcvTimestamp().getTimestamp());
      }
      catch (XmlBlasterException e) {
         fail("testUpdateQos failed: " + e.toString());
      }

      System.out.println("***MsgQosFactoryTest: testUpdateQos [SUCCESS]");
   }

   /**
    * Tests given rcvTimestamp
    */
   public void testFromPersistentStore() {
      System.out.println("***MsgQosFactoryTest: testFromPersistentStore ...");
      
      try {
         Timestamp timestamp = new Timestamp();
         String xml =
            "<qos>\n" +
            "   <rcvTimestamp nanos='" + timestamp.getTimestamp() + "'/>\n" + // if from persistent store
            "</qos>\n";

         MsgQosSaxFactory factory = new MsgQosSaxFactory(glob);
         MsgQosData qos = factory.readObject(xml);

         assertEquals("", timestamp.getTimestamp(), qos.getRcvTimestamp().getTimestamp());
      }
      catch (XmlBlasterException e) {
         fail("testFromPersistentStore failed: " + e.toString());
      }

      System.out.println("***MsgQosFactoryTest: testFromPersistentStore [SUCCESS]");
   }

   /**
    * Tests empty xml string
    */
   public void testDefault() {
      System.out.println("***MsgQosFactoryTest: testDefault ...");
      
      try {
         MsgQosSaxFactory factory = new MsgQosSaxFactory(glob);
         MsgQosData qos = factory.readObject((String)null);
         //qos.addRouteInfo(new RouteInfo(new NodeId("master"), 0, new Timestamp(9408630587L)));
         assertEquals("", true, qos.isSubscribeable());
         assertEquals("", false, qos.isPtp());
         assertEquals("", false, qos.isVolatile());
         assertEquals("", false, qos.isDurable());
         assertEquals("", true, qos.isForceUpdate());
         assertEquals("", false, qos.isReadonly());
         assertEquals("", null, qos.getSender());
         assertEquals("", 0, qos.getRouteNodes().length);
         assertEquals("", PriorityEnum.NORM_PRIORITY, qos.getPriority());
         assertEquals("", false, qos.isFromPersistenceStore());
         assertTrue("", qos.getRcvTimestamp() == null);
         assertEquals("", null, qos.getDestinations());
      }
      catch (XmlBlasterException e) {
         fail("testDefault failed: " + e.toString());
      }

      System.out.println("***MsgQosFactoryTest: testDefault [SUCCESS]");
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.qos.MsgQosFactoryTest
    * </pre>
    */
   public static void main(String args[])
   {
      MsgQosFactoryTest testSub = new MsgQosFactoryTest("MsgQosFactoryTest");
      testSub.setUp();
      testSub.testParse();
      testSub.testToXml();
      testSub.testFromPersistentStore();
      testSub.testPublishQosServer();
      testSub.testGetReturnQos();
      testSub.testUpdateQos();
      testSub.testDefault();
      //testSub.tearDown();
   }
}
