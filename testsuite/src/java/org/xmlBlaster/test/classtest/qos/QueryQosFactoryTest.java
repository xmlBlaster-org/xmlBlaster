package org.xmlBlaster.test.classtest.qos;

import java.util.logging.Logger;

import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.HistoryQos;
import org.xmlBlaster.util.qos.I_QueryQosFactory;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.QueryQosJsonFactory;
import org.xmlBlaster.util.qos.QuerySpecQos;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Test I_QueryQosFactory implementations. 
 * <p />
 * <pre>
 * java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.qos.QueryQosFactoryTest
 * </pre>
 * @see org.xmlBlaster.util.qos.QueryQosSaxFactory
 * @see org.xmlBlaster.test.classtest.qos.QueryQosJsonFactoryTest additional JSON specific tests
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">the xmlBlaster access interface requirement</a>
 */
public class QueryQosFactoryTest extends TestCase {
   private String ME = "QueryQosFactoryTest";
   protected final Global glob;
   private static Logger log = Logger.getLogger(QueryQosFactoryTest.class.getName());
   private String currImpl;
   private I_QueryQosFactory factory;
   private I_QueryQosFactory jsonFactory; // additionally test some JSON functionality
   static I_QueryQosFactory[] IMPL = { 
                   new org.xmlBlaster.util.qos.QueryQosSaxFactory(Global.instance()),
                 };

   public QueryQosFactoryTest(Global glob, String name, int currImpl) {
      super(name);
      this.glob = glob;

      this.factory = IMPL[currImpl];
      this.jsonFactory = new QueryQosJsonFactory(glob);
   }

   protected void setUp() {
      log.info("Testing parser factory " + factory.getName());
   }

   /**
    * Tries with all known tags
    */
   public void testParse() {
      System.out.println("***QueryQosFactoryTest: testParse ...");
      
      try {
         String xml =
           "<qos>\n" +
           "   <subscribe id='_subId:1'/>\n" +
           "   <erase forceDestroy='true'/>\n" +
           "   <meta>false</meta>\n" +
           "   <content>false</content>\n" +
           "   <local>false</local>\n" +
           "   <subIdGeneratedIncludeClusterNodeId>true</subIdGeneratedIncludeClusterNodeId>\n" +
           "   <initialUpdate>false</initialUpdate>\n" +
           "   <updateOneway>true</updateOneway>\n" +
           "   <notify>false</notify>\n" +
           "   <filter type='myPlugin' version='1.0'>a!=100</filter>\n" +
           "   <filter type='anotherPlugin' version='1.1'><![CDATA[b<100|a[0]>10]]></filter>\n" +
           "   <multiSubscribe>false</multiSubscribe>" +
           "   <querySpec type='QueueQuery'><![CDATA[\n" +
           "     maxEntries=3&maxSize=-1&consumable=false&waitingDelay=0]]>\n" +
           "   </querySpec>" +
           "</qos>\n";

         QueryQosData qos = factory.readObject(xml);

         assertEquals("", "_subId:1", qos.getSubscriptionId());
         assertEquals("", true, qos.getForceDestroy());
         assertEquals("", true, qos.isSubIdGeneratedIncludeClusterNodeId());
         assertEquals("", false, qos.getWantMeta());
         assertEquals("", false, qos.getWantContent());
         assertEquals("", false, qos.getWantLocal());
         assertEquals("", false, qos.getWantInitialUpdate());
         assertEquals("updateOneway", true, qos.getWantUpdateOneway());
         assertEquals("notify", false, qos.getWantNotify());
         AccessFilterQos[] filterArr = qos.getAccessFilterArr();
         assertEquals("", 2, filterArr.length);
         assertEquals("", "myPlugin", filterArr[0].getType());
         assertEquals("", "1.0", filterArr[0].getVersion());
         assertEquals("", "a!=100", filterArr[0].getQuery().toString());
         assertEquals("", "anotherPlugin", filterArr[1].getType());
         assertEquals("", "1.1", filterArr[1].getVersion());
         assertEquals("", "b<100|a[0]>10", filterArr[1].getQuery().toString());
         assertEquals("Multisubscribe not beeing disabled", false, qos.getMultiSubscribe());
         assertEquals("queryspec type", "QueueQuery", qos.getQuerySpecArr()[0].getType());
         assertEquals("queryspec content", "maxEntries=3&maxSize=-1&consumable=false&waitingDelay=0", qos.getQuerySpecArr()[0].getQuery().toString());
      }
      catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      System.out.println("***QueryQosFactoryTest: testParse [SUCCESS]");
   }

   /**
    * Test toXml and test toJson ((parse - createXml - parse again - createJSON - parse again - test)
    * Two Birds with one Stone
    */
   public void testToXmlAndToJson() {
      System.out.println("***QueryQosFactoryTest: testToXmlAndToJson ...");
      
      try {
         String xml =
           "<qos>\n" +
           "   <subscribe id='_subId:1'/>\n" +
           "   <erase forceDestroy='true'/>\n" +
           "   <meta>false</meta>\n" +
           "   <content>false</content>\n" +
           "   <local>false</local>\n" +
           "   <subIdGeneratedIncludeClusterNodeId>true</subIdGeneratedIncludeClusterNodeId>\n" +
           "   <initialUpdate>false</initialUpdate>\n" +
           "   <updateOneway>true</updateOneway>\n" +
           "   <notify>false</notify>\n" +
           "   <history numEntries='20' newestFirst='false'/>\n" +
           "   <filter type='myPlugin' version='1.0'>a!=100</filter>\n" +
           "   <filter type='anotherPlugin' version='1.1'><![CDATA[b<100|a[0]>10]]></filter>\n" +
           "   <multiSubscribe>false</multiSubscribe>" +
           "   <querySpec type='QueueQuery'><![CDATA[\n" +
           "     maxEntries=3&maxSize=-1&consumable=false&waitingDelay=0]]>\n" +
           "   </querySpec>" +
           "   <clientProperty name='intKey' type='int'>123</clientProperty>\n" +
           "   <clientProperty name='StringKey' type='String' encoding='" + Constants.ENCODING_BASE64 + "'>QmxhQmxhQmxh</clientProperty>\n" +

           "</qos>\n";
         QueryQosData qos = factory.readObject(xml);
         String newXml = qos.serialize();
         log.info("New XML=" + newXml);
         qos = factory.readObject(newXml);
         String newJson = jsonFactory.writeObject(qos, null, null);
         log.info("New JSON=\n" + newJson);
         qos = jsonFactory.readObject(newJson);

         assertEquals("", "_subId:1", qos.getSubscriptionId());
         assertEquals("", true, qos.getForceDestroy());
         assertEquals("", true, qos.isSubIdGeneratedIncludeClusterNodeId());
         assertEquals("", false, qos.getWantMeta());
         assertEquals("", false, qos.getWantContent());
         assertEquals("", false, qos.getWantLocal());
         assertEquals("", false, qos.getWantInitialUpdate());
         assertEquals("updateOneway", true, qos.getWantUpdateOneway());
         assertEquals("notify", false, qos.getWantNotify());
         assertEquals("", 20, qos.getHistoryQos().getNumEntries());
         assertEquals("", false, qos.getHistoryQos().getNewestFirst());
         AccessFilterQos[] filterArr = qos.getAccessFilterArr();
         assertEquals("", 2, filterArr.length);
         assertEquals("", "myPlugin", filterArr[0].getType());
         assertEquals("", "1.0", filterArr[0].getVersion());
         assertEquals("", "a!=100", filterArr[0].getQuery().toString());
         assertEquals("", "anotherPlugin", filterArr[1].getType());
         assertEquals("", "1.1", filterArr[1].getVersion());
         assertEquals("", "b<100|a[0]>10", filterArr[1].getQuery().toString());
         assertEquals("Multisubscribe not beeing disabled", false, qos.getMultiSubscribe());
         assertEquals("queryspec type", "QueueQuery", qos.getQuerySpecArr()[0].getType());
         assertEquals("queryspec content", "maxEntries=3&maxSize=-1&consumable=false&waitingDelay=0", qos.getQuerySpecArr()[0].getQuery().toString());

         assertEquals("Wrong number of clientProperties", 2, qos.getClientPropertyArr().length);
         {
            String prop = qos.getClientProperty("StringKey", (String)null);
            assertTrue("Missing client property", prop != null);
            assertEquals("Wrong base64 decoding", "BlaBlaBla", prop); // Base64: QmxhQmxhQmxh -> BlaBlaBla
         }

         {
            int prop = qos.getClientProperty("intKey", -1);
            assertEquals("Wrong value", 123, prop);
         }

      }
      catch (XmlBlasterException e) {
         fail("testToXmlAndToJson failed: " + e.getMessage());
      }

      System.out.println("***QueryQosFactoryTest: testToXmlAndToJson [SUCCESS]");
   }

   /**
    * Tests empty XML and JSON string
    */
   public void testDefault() {
      System.out.println("***QueryQosFactoryTest: testDefault ...");
      
      try {
         // test XML
         QueryQosData qos = factory.readObject((String)null);
         assertDefault(qos);
         // test JSON
         qos = jsonFactory.readObject((String)null);
         assertDefault(qos);
      }
      catch (XmlBlasterException e) {
         fail("testDefault failed: " + e.toString());
      }

      System.out.println("***QueryQosFactoryTest: testDefault [SUCCESS]");
   }

   /**
    * @param qos: this objects fields will be tested tested
    */
   private void assertDefault(QueryQosData qos) {
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
   }

   /**
    * Tests client side EraseQos. 
    */
   public void testEraseQos() {
      System.out.println("***QueryQosFactoryTest: EraseQos ...");
      
      try {
         EraseQos eraseQos = new EraseQos(glob);
         eraseQos.setForceDestroy(true);
         System.out.println("EraseQos (XML): " + eraseQos.serialize());
         QueryQosData qos = factory.readObject(eraseQos.serialize());
         assertEquals("", true, qos.getForceDestroy());
         System.out.println("EraseQos (JSON): " + eraseQos.toJson());
         qos = jsonFactory.readObject(eraseQos.toJson());
         assertEquals("", true, qos.getForceDestroy());
      }
      catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }
      System.out.println("***QueryQosFactoryTest: EraseQos [SUCCESS]");
   }

   /**
    * Tests client side SubscribeQos (Both XML and JSON).
    */
   public void testSubscribeQos() {
      System.out.println("***QueryQosFactoryTest: SubscribeQos ...");
      
      try {
         SubscribeQos subscribeQos = createSubscribeQos();
         String xml = subscribeQos.serialize();

         // Test XML
         System.out.println("SubscribeQos (XML): " + xml);
         QueryQosData qosFromXml = factory.readObject(xml);
         assertSubscribeQos(qosFromXml);

         // Test JSON
         String json = subscribeQos.toJson();
         System.out.println("SubscribeQos (JSON):\n" + json);
         QueryQosData qosFromJson = jsonFactory.readObject(json);
         assertSubscribeQos(qosFromJson);

      } catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }
      System.out.println("***QueryQosFactoryTest: SubscribeQos [SUCCESS]");
   }
   /**
    * @return SubscibeQos object for Testing
    */
   private SubscribeQos createSubscribeQos() {
      SubscribeQos subscribeQos = new SubscribeQos(glob);
      subscribeQos.setWantContent(false);
      subscribeQos.setSubscriptionId("MyOwnSentSubscribeId");
      HistoryQos hh = new HistoryQos(glob, 33);
      hh.setNewestFirst(false);
      subscribeQos.setHistoryQos(hh);
      subscribeQos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "1.0", new Query(glob, "800")));
      subscribeQos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter2", "3.2", new Query(glob, "a<10")));
      subscribeQos.setPersistent(true);
      return subscribeQos;
   }

   /**
    * @param qos: this objects fields will be tested tested
    */
   private void assertSubscribeQos(QueryQosData qos) {
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
   }
   
   /**
    * Tests client side GetQos. 
    * <br>
    * both xml and json 
    */
   public void testGetQos() {
      System.out.println("***QueryQosFactoryTest: GetQos ...");
      
      try {
         GetQos getQos = new GetQos(glob);
         getQos.setWantContent(false);
         HistoryQos hh = new HistoryQos(glob, 33);
         hh.setNewestFirst(false);
         getQos.setHistoryQos(hh);
         getQos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "1.0", new Query(glob, "800")));
         getQos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter2", "3.2", new Query(glob, "a<10")));

         System.out.println("GetQos: " + getQos.serialize());
         QueryQosData qos = factory.readObject(getQos.serialize());
         assertGetQos(qos);
         qos = jsonFactory.readObject(getQos.toJson());
         System.out.println("GetQos: " + getQos.toJson());
         assertGetQos(qos);

      }
      catch (Throwable e) {
        fail("Test failed: " + e.toString());
      }
      System.out.println("***QueryQosFactoryTest: GetQos [SUCCESS]");
   }
   
   /**
    * @param qos: this objects fields will be tested tested
    */
   private void assertGetQos(QueryQosData qos) {
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
   }
   
   public void testUnsubscribeQos() {
      System.out.println("***QueryQosFactoryTest: UnSubscribeQos ...");

      try {
          UnSubscribeQos unsubscribeQos = createUnsubscribeQos();
          String xml = unsubscribeQos.serialize();

          // Test XML
          System.out.println("UnSubscribeQos (XML): " + xml);
          QueryQosData qosFromXml = factory.readObject(xml);
          assertUnsubscribeQos(qosFromXml);

          // Test JSON
          String json = unsubscribeQos.toJson();
          System.out.println("UnSubscribeQos (JSON):\n" + json);
          QueryQosData qosFromJson = jsonFactory.readObject(json);
          assertUnsubscribeQos(qosFromJson);

      } catch (Throwable e) {
          System.out.println("Test failed: " + e.toString());
          fail("UnSubscribeQos test failed: " + e.toString());
      }

      System.out.println("***QueryQosFactoryTest: UnSubscribeQos [SUCCESS]");
  }

  /**
   * @return UnSubscribeQos object for testing
   */
  private UnSubscribeQos createUnsubscribeQos() {
      UnSubscribeQos uq = new UnSubscribeQos(glob);

      uq.setPersistent(false); // test persistence flag
      uq.addClientProperty(UnSubscribeQos.CP_ASYNC_UNSUBSCRIBE_WITHOUT_SUBSCRIPTIONID_ALLOWED, true);

      return uq;
  }

  /**
   * Validate the UnSubscribeQos data extracted from XML/JSON
   */
  private void assertUnsubscribeQos(QueryQosData qos) {
      assertEquals("", MethodName.UNSUBSCRIBE, qos.getMethod());
      assertEquals("", false, qos.getPersistentProp().getValue());

      ClientProperty cp = qos.getClientProperty(UnSubscribeQos.CP_ASYNC_UNSUBSCRIBE_WITHOUT_SUBSCRIPTIONID_ALLOWED);
      assertNotNull("Client property missing", cp);
      assertEquals("", true, cp.getBooleanValue());
  }



   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
      TestSuite suite= new TestSuite();
      Global glob = new Global();
      for (int i=0; i<IMPL.length; i++) {
         suite.addTest(new QueryQosFactoryTest(glob, "testDefault", i));
         suite.addTest(new QueryQosFactoryTest(glob, "testParse", i));
         suite.addTest(new QueryQosFactoryTest(glob, "testToXmlAndToJson", i));
         suite.addTest(new QueryQosFactoryTest(glob, "testEraseQos", i));
         suite.addTest(new QueryQosFactoryTest(glob, "testSubscribeQos", i));
         suite.addTest(new QueryQosFactoryTest(glob, "testGetQos", i));
         suite.addTest(new QueryQosFactoryTest(glob, "testUnsubscribeQos", i));
      }
      return suite;
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.qos.QueryQosFactoryTest
    * </pre>
    */
   public static void main(String args[]) {
      Global glob = new Global(args);
      for (int i=0; i<IMPL.length; i++) {
         QueryQosFactoryTest testSub = new QueryQosFactoryTest(glob, "QueryQosFactoryTest", i);
         testSub.setUp();
         testSub.testDefault();
         testSub.testParse();
         testSub.testToXmlAndToJson();
         testSub.testEraseQos();
         testSub.testSubscribeQos();
         testSub.testGetQos();
         testSub.testUnsubscribeQos();
         //testSub.tearDown();
      }
   }
}
