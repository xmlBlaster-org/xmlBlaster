package org.xmlBlaster.test.classtest.qos;

import junit.framework.TestCase;

import java.util.logging.Logger;

import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.qos.HistoryQos;
import org.xmlBlaster.util.qos.I_QueryQosFactory;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.QueryQosJsonFactory;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Test I_QueryQosFactory implementations.
 * <p />
 * 
 * <pre>
 * java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.qos.QueryQosJsonFactoryTest
 * </pre>
 * 
 * @see org.xmlBlaster.util.qos.QueryQosSaxFactory
 * @see <a href=
 *      "http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html"
 *      target="others">the xmlBlaster access interface requirement</a>
 */
public class QueryQosJsonFactoryTest extends TestCase {
   private String ME = "QueryQosJsonFactoryTest";
   protected final Global glob;
   private static Logger log = Logger.getLogger(QueryQosJsonFactoryTest.class.getName());
   private String currImpl;
   private I_QueryQosFactory factory;
   static I_QueryQosFactory[] IMPL = { new org.xmlBlaster.util.qos.QueryQosSaxFactory(Global.instance()), };

   public QueryQosJsonFactoryTest(Global glob, String name, int currImpl) {
      super(name);
      this.glob = glob;

      this.factory = new QueryQosJsonFactory(glob);
   }

   protected void setUp() {
      // TODO: change name
      log.info("Testing parser factory " + factory.getName());
   }

   /**
    * Tries with all known tags
    */
   public void testParse() {
      System.out.println("***QueryQosJsonFactoryTest: testParse ...");

      try {
         String json = """
                  {
                     "qos": {
                       "subscribe": {
                         "id": "_subId:1"
                       },
                       "erase": {
                         "forceDestroy": true
                       },
                       "meta": false,
                       "content": false,
                       "local": false,
                       "subIdGeneratedIncludeClusterNodeId": true,
                       "initialUpdate": false,
                       "filter": [
                         {
                           "type": "myPlugin",
                           "version": "1.0",
                           "value": "a!=100"
                         },
                         {
                           "type": "anotherPlugin",
                           "version": "1.1",
                           "value": "b<100|a[0]>10"
                         }
                       ]
                     }
                   }
               """;

         System.out.println("QueryQuosJson" + json);
         QueryQosJsonFactory jsonFactory = new QueryQosJsonFactory(glob);
         QueryQosData qos = jsonFactory.readObject(json);

         assertEquals("", "_subId:1", qos.getSubscriptionId());
         assertEquals("", true, qos.getForceDestroy());
         assertEquals("", true, qos.isSubIdGeneratedIncludeClusterNodeId());
         assertEquals("", false, qos.getWantMeta());
         assertEquals("", false, qos.getWantContent());
         assertEquals("", false, qos.getWantLocal());
         assertEquals("", false, qos.getWantInitialUpdate());
         AccessFilterQos[] filterArr = qos.getAccessFilterArr();
         assertEquals("", 2, filterArr.length);
         assertEquals("", "myPlugin", filterArr[0].getType());
         assertEquals("", "1.0", filterArr[0].getVersion());
         assertEquals("", "a!=100", filterArr[0].getQuery().toString());
         assertEquals("", "anotherPlugin", filterArr[1].getType());
         assertEquals("", "1.1", filterArr[1].getVersion());
         assertEquals("", "b<100|a[0]>10", filterArr[1].getQuery().toString());
      } catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      System.out.println("***QueryQosJsonFactoryTest: testParse [SUCCESS]");
   }

   /**
    * Test toXml (parse - createXml - parse again - test)
    */
   public void testToJson() {
      System.out.println("***QueryQosJsonFactoryTest: testToJson ...");

      try {
         String json = """
               {
                  "qos": {
                     "subscribe": {
                       "id": "_subId:1"
                     },
                     "erase": {
                       "forceDestroy": true
                     },
                     "meta": false,
                     "content": false,
                     "local": false,
                     "subIdGeneratedIncludeClusterNodeId": true,
                     "initialUpdate": false,
                     "history": {
                       "numEntries": 20,
                       "newestFirst": false
                     },
                     "filter": [
                       {
                         "type": "myPlugin",
                         "version": "1.0",
                         "value": "a!=100"
                       },
                       {
                         "type": "anotherPlugin",
                         "version": "1.1",
                         "value": "\"lolIwillescape\", {\"thisisnotafield\": \"thisisnotavalue\"}"
                       }
                     ]
                   }
                 }
                  """;
         QueryQosData qos = factory.readObject(json);
         String newJson = qos.toJson();
         log.info("New XML=" + newJson);
         qos = factory.readObject(newJson);

         assertEquals("", "_subId:1", qos.getSubscriptionId());
         assertEquals("", true, qos.getForceDestroy());
         assertEquals("", true, qos.isSubIdGeneratedIncludeClusterNodeId());
         assertEquals("", false, qos.getWantMeta());
         assertEquals("", false, qos.getWantContent());
         assertEquals("", false, qos.getWantLocal());
         assertEquals("", false, qos.getWantInitialUpdate());
         assertEquals("", 20, qos.getHistoryQos().getNumEntries());
         assertEquals("", false, qos.getHistoryQos().getNewestFirst());
         AccessFilterQos[] filterArr = qos.getAccessFilterArr();
         assertEquals("", 2, filterArr.length);
         assertEquals("", "myPlugin", filterArr[0].getType());
         assertEquals("", "1.0", filterArr[0].getVersion());
         assertEquals("", "a!=100", filterArr[0].getQuery().toString());
         assertEquals("", "anotherPlugin", filterArr[1].getType());
         assertEquals("", "1.1", filterArr[1].getVersion());
         assertEquals("", "\"lolIwillescape\", {\"thisisnotafield\": \"thisisnotavalue\"}", filterArr[1].getQuery().toString());
      } catch (XmlBlasterException e) {
         fail("testToJson failed: " + e.toString());
      }

      System.out.println("***QueryQosJsonFactoryTest: testToJson [SUCCESS]");
   }

   /**
    * Tests empty xml string
    */
   public void testDefault() {
      System.out.println("***QueryQosJsonFactoryTest: testDefault ...");

      try {
         QueryQosData qos = factory.readObject((String) null);
         assertEquals("", null, qos.getSubscriptionId());
         assertEquals("", true, qos.getWantMeta());
         assertEquals("", true, qos.getWantContent());
         assertEquals("", true, qos.getWantLocal());
         assertEquals("", false, qos.isSubIdGeneratedIncludeClusterNodeId());
         assertEquals("", true, qos.getWantInitialUpdate());
         assertEquals("", 1, qos.getHistoryQos().getNumEntries());
         assertEquals("", true, qos.getHistoryQos().getNewestFirst());
         AccessFilterQos[] filterArr = qos.getAccessFilterArr();
         assertTrue("", null == filterArr);
      } catch (XmlBlasterException e) {
         fail("testDefault failed: " + e.toString());
      }

      System.out.println("***QueryQosJsonFactoryTest: testDefault [SUCCESS]");
   }

   /**
    * Tests client side EraseQos.
    */
   public void testEraseQos() {
      System.out.println("***QueryQosJsonFactoryTest: EraseQos ...");

      try {
         EraseQos eraseQos = new EraseQos(glob);
         eraseQos.setForceDestroy(true);
         System.out.println("EraseQos: " + eraseQos.toXml());
         QueryQosData qos = factory.readObject(eraseQos.toXml());
         assertEquals("", true, qos.getForceDestroy());
      } catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }
      System.out.println("***QueryQosJsonFactoryTest: EraseQos [SUCCESS]");
   }

   /**
    * Tests client side SubscribeQos.
    */
   public void testSubscribeQos() {
      System.out.println("***QueryQosJsonFactoryTest: SubscribeQos ...");

      try {
         SubscribeQos subscribeQos = new SubscribeQos(glob);
         subscribeQos.setWantContent(false);
         subscribeQos.setSubscriptionId("MyOwnSentSubscribeId");
         HistoryQos hh = new HistoryQos(glob, 33);
         hh.setNewestFirst(false);
         subscribeQos.setHistoryQos(hh);
         subscribeQos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "1.0", new Query(glob, "800")));
         subscribeQos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter2", "3.2", new Query(glob, "a<10")));
         subscribeQos.setPersistent(true);
         System.out.println("SubscribeQos: " + subscribeQos.toXml());

         QueryQosData qos = factory.readObject(subscribeQos.toXml());

         assertEquals("", false, qos.getWantContent());
         assertEquals("", "MyOwnSentSubscribeId", qos.getSubscriptionId());
         assertEquals("", 33, qos.getHistoryQos().getNumEntries());
         assertEquals("", false, qos.getHistoryQos().getNewestFirst());
         AccessFilterQos[] filterArr = qos.getAccessFilterArr();
         assertEquals("", 2, filterArr.length);
         assertEquals("", "ContentLenFilter", filterArr[0].getType());
         assertEquals("", "1.0", filterArr[0].getVersion());
         assertEquals("", "800", filterArr[0].getQuery().toString());
         assertEquals("", "ContentLenFilter2", filterArr[1].getType());
         assertEquals("", "3.2", filterArr[1].getVersion());
         assertEquals("", "a<10", filterArr[1].getQuery().toString());
         assertEquals("", true, qos.getPersistentProp().getValue());
      } catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }
      System.out.println("***QueryQosJsonFactoryTest: SubscribeQos [SUCCESS]");
   }

   /**
    * Tests client side GetQos.
    */
   public void testGetQos() {
      System.out.println("***QueryQosJsonFactoryTest: GetQos ...");

      try {
         GetQos getQos = new GetQos(glob);
         getQos.setWantContent(false);
         HistoryQos hh = new HistoryQos(glob, 33);
         hh.setNewestFirst(false);
         getQos.setHistoryQos(hh);
         getQos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "1.0", new Query(glob, "800")));
         getQos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter2", "3.2", new Query(glob, "a<10")));
         System.out.println("GetQos: " + getQos.toXml());
         QueryQosData qos = factory.readObject(getQos.toXml());
         assertEquals("", false, qos.getWantContent());
         assertEquals("", 33, qos.getHistoryQos().getNumEntries());
         assertEquals("", false, qos.getHistoryQos().getNewestFirst());
         AccessFilterQos[] filterArr = qos.getAccessFilterArr();
         assertEquals("", 2, filterArr.length);
         assertEquals("", "ContentLenFilter", filterArr[0].getType());
         assertEquals("", "1.0", filterArr[0].getVersion());
         assertEquals("", "800", filterArr[0].getQuery().toString());
         assertEquals("", "ContentLenFilter2", filterArr[1].getType());
         assertEquals("", "3.2", filterArr[1].getVersion());
         assertEquals("", "a<10", filterArr[1].getQuery().toString());
      } catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }
      System.out.println("***QueryQosJsonFactoryTest: GetQos [SUCCESS]");
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
      TestSuite suite = new TestSuite();
      Global glob = new Global();
      for (int i = 0; i < IMPL.length; i++) {
         suite.addTest(new QueryQosJsonFactoryTest(glob, "testDefault", i));
         suite.addTest(new QueryQosJsonFactoryTest(glob, "testParse", i));
         suite.addTest(new QueryQosJsonFactoryTest(glob, "testToJson", i));
         suite.addTest(new QueryQosJsonFactoryTest(glob, "testEraseQos", i));
         suite.addTest(new QueryQosJsonFactoryTest(glob, "testSubscribeQos", i));
         suite.addTest(new QueryQosJsonFactoryTest(glob, "testGetQos", i));
      }
      return suite;
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.qos.QueryQosJsonFactoryTest
    * </pre>
    */
   public static void main(String args[]) {
      Global glob = new Global(args);
      for (int i = 0; i < IMPL.length; i++) {
         QueryQosJsonFactoryTest testSub = new QueryQosJsonFactoryTest(glob, "QueryQosJsonFactoryTest", i);
         testSub.setUp();
         testSub.testDefault();
         testSub.testParse();
         testSub.testToJson();
         testSub.testEraseQos();
         testSub.testSubscribeQos();
         testSub.testGetQos();
         // testSub.tearDown();
      }
   }
}
