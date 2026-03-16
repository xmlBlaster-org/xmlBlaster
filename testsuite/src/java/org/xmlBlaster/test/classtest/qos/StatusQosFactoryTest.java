package org.xmlBlaster.test.classtest.qos;

import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.qos.StatusQosJsonFactory;
import org.xmlBlaster.util.qos.I_StatusQosFactory;
import org.xmlBlaster.util.qos.StatusQosSaxFactory;
import org.xmlBlaster.util.qos.StatusQosQuickParseFactory;
import org.xmlBlaster.client.qos.GetReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.MethodName;

import junit.framework.*;

/**
 * Test I_StatusQosFactory implementations. 
 * <p />
 * <pre>
 * java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * </pre>
 * @see org.xmlBlaster.util.qos.StatusQosSaxFactory
 * @see org.xmlBlaster.util.qos.StatusQosQuickParseFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">the xmlBlaster access interface requirement</a>
 */
public class StatusQosFactoryTest extends TestCase {
   private String ME = "StatusQosFactoryTest";
   protected final Global glob;
   private static Logger log = Logger.getLogger(StatusQosFactoryTest.class.getName());
   private String currImpl;
   private I_StatusQosFactory factory;
   static I_StatusQosFactory[] IMPL = { 
                   new org.xmlBlaster.util.qos.StatusQosSaxFactory(Global.instance()),
                   new org.xmlBlaster.util.qos.StatusQosQuickParseFactory(Global.instance())
                 };
   private StatusQosJsonFactory jsonFactory;

   public StatusQosFactoryTest(Global glob, String name, int currImpl) {
      super(name);
      this.glob = glob;

      this.factory = IMPL[currImpl];
      this.jsonFactory = new StatusQosJsonFactory(glob);
   }

   protected void setUp() {
      log.info("Testing parser factory " + factory.getName());
   }

   /**
    * Tries with all known tags
    */
   public void testParse() {
      System.out.println("***StatusQosFactoryTest: testParse ...");
      
      try {
         String xml =
            "<qos>\n" +
            "   <state id='ERASED' info='QUEUED[bilbo]'/>\n" +
            "   <key oid='yourMessageOid'/>\n" +
            "   <subscribe id='_subId:1'/>\n" +
            "	 <rcvTimestamp nanos='1013346248150000001'>" +
            "   	2002-02-10 14:04:08.150000001" +
            "	 </rcvTimestamp>" +
            "</qos>\n";

         StatusQosData qos = factory.readObject(xml);
         assertParse(qos);

         String json = """
               {
                 "state": {
                   "id": "ERASED",
                   "info": "QUEUED[bilbo]"
                 },
                 "key": {
                   "oid": "yourMessageOid"
                 },
                 "subscribe": {
                   "id": "_subId:1"
                 },
                 "rcvTimestamp": "1013346248150000001"
               }
                              """;
         qos = jsonFactory.readObject(json);
         System.out.println(jsonFactory.writeObject(qos, "", null));
         assertParse(qos);

      }
      catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      System.out.println("***StatusQosFactoryTest: testParse [SUCCESS]");
   }
   
   private void assertParse(StatusQosData qos) {
      assertEquals("", Constants.STATE_ERASED, qos.getState());
      assertEquals("", false, qos.isOk());
      assertEquals("", true, qos.isErased());
      assertEquals("", false, qos.isTimeout());
      assertEquals("", false, qos.isForwardError());
      assertEquals("", "QUEUED[bilbo]", qos.getStateInfo());
      assertEquals("", "yourMessageOid", qos.getKeyOid());
      assertEquals("", "_subId:1", qos.getSubscriptionId());
      assertEquals((Long) 1013346248150000001L, qos.getRcvTimestamp().getTimestampLong());
   }

   /**
    * Test toXml (parse - createXml - parse again - createJson - parse - test)
    */
   public void testToXmlAndToJson() {
      System.out.println("***StatusQosFactoryTest: testToXml ...");
      
      try {
         String xml =
            "<qos>\n" +
            "   <state id='ERASED' info='QUEUED[bilbo]'/>\n" +
            "   <key oid='yourMessageOid'/>\n" +
            "   <subscribe id='_subId:1'/>\n" +
            "   <clientProperty name='aKey' type='boolean'>true</clientProperty>\n" +
            "   <rcvTimestamp nanos='1013346248150000001'>" +
            "     2002-02-10 14:04:08.150000001" +
            "   </rcvTimestamp>" +
            "</qos>\n";

         StatusQosData qos = factory.readObject(xml);
         String newXml = qos.serialize(null, null, true);
         log.info("New XML=" + newXml);
         qos = factory.readObject(newXml);
         String json = jsonFactory.writeObject(qos, "", null, true);
         log.info("Json=\n" + newXml);
         qos = jsonFactory.readObject(json);

         assertEquals("", Constants.STATE_ERASED, qos.getState());
         assertEquals("", false, qos.isOk());
         assertEquals("", true, qos.isErased());
         assertEquals("", false, qos.isTimeout());
         assertEquals("", false, qos.isForwardError());
         assertEquals("", "QUEUED[bilbo]", qos.getStateInfo());
         assertEquals("", "yourMessageOid", qos.getKeyOid());
         assertEquals("", "_subId:1", qos.getSubscriptionId());
         assertEquals((Long) 1013346248150000001L, qos.getRcvTimestamp().getTimestampLong());
         // TODO quickparse does not parse clientProperty
         if (factory.getName() == "StatusQosSaxFactory") assertTrue(qos.getClientProperty("aKey", false));
      }
      catch (XmlBlasterException e) {
         fail("testToXml failed: " + e.toString());
      }

      System.out.println("***StatusQosFactoryTest: testToXml [SUCCESS]");
   }
   
   public void testToXmlCp() {
	      System.out.println("***StatusQosFactoryTest: testToXmlCp ...");
	      
	      try {
	         String xml =
	            "<qos>\n" +
	            "   <state id='ERASED' info='QUEUED[bilbo]'/>\n" +
	            "   <key oid='yourMessageOid'/>\n" +
	            "   <subscribe id='_subId:1'/>\n" +
	            "   <clientProperty name='aKey' type='boolean'>true</clientProperty>\n" +
	            "   <clientProperty name='aKey2'>Bla</clientProperty>\n" +
	            "</qos>\n";

	         I_StatusQosFactory f = new org.xmlBlaster.util.qos.StatusQosSaxFactory(Global.instance());
	         StatusQosData qos = f.readObject(xml);
	         Properties props = new java.util.Properties();
            props.setProperty("propsKey", "propsValue");
            props.setProperty("aKey2", "Blub"); // same key as XML - different value
	         String newXml = qos.serialize(null, props, true);
	         log.info("New XML=" + newXml);
	         qos = f.readObject(newXml);
	         assertProperties(qos);
	         String json = jsonFactory.writeObject(qos, "", null, true);
	         log.info("Json=\n" + json);
	         qos = jsonFactory.readObject(json);
	         assertProperties(qos);
	      }
	      catch (XmlBlasterException e) {
	         fail("testToXml failed: " + e.toString());
	      }

	      System.out.println("***StatusQosFactoryTest: testToXml [SUCCESS]");
	   }
   
   private void assertProperties(StatusQosData qos) {
      assertEquals("", Constants.STATE_ERASED, qos.getState());
      assertEquals("", false, qos.isOk());
      assertEquals("", true, qos.isErased());
      assertEquals("", false, qos.isTimeout());
      assertEquals("", false, qos.isForwardError());
      assertEquals("", "QUEUED[bilbo]", qos.getStateInfo());
      assertEquals("", "yourMessageOid", qos.getKeyOid());
      assertEquals("", "_subId:1", qos.getSubscriptionId());
      assertTrue(qos.getClientProperty("aKey", false));
      assertEquals("propsValue", qos.getClientProperty("propsKey", ""));
      // the properties from the class StatusQosData overwrite 'writeObject' parameter Properties
      assertEquals("Bla", qos.getClientProperty("aKey2", ""));
   }

   /**
    * Tests empty xml string
    */
   public void testDefault() {
      System.out.println("***StatusQosFactoryTest: testDefault ...");
      
      try {
         StatusQosData qos = factory.readObject((String)null);
         assertDefault(qos);
         qos = jsonFactory.readObject(null);
         assertDefault(qos);
      }
      catch (XmlBlasterException e) {
         fail("testDefault failed: " + e.toString());
      }

      System.out.println("***StatusQosFactoryTest: testDefault [SUCCESS]");
   }
   
   private void assertDefault(StatusQosData qos) {
      assertEquals("", Constants.STATE_OK, qos.getState());
      assertEquals("", true, qos.isOk());
      assertEquals("", false, qos.isErased());
      assertEquals("", false, qos.isTimeout());
      assertEquals("", false, qos.isForwardError());
      assertEquals("", null, qos.getStateInfo());
      assertEquals("", null, qos.getKeyOid());
      assertEquals("", null, qos.getSubscriptionId());
   }

   /**
    * Tries with all known tags
    */
   public void testPerformance() {
      System.out.println("***StatusQosFactoryTest: testPerformance ...");
      
      try {
         String xml =
               "<qos>\n" +
               "   <state id='ERASED' info='QUEUED[bilbo]'/>\n" +
               "   <key oid='yourMessageOid'/>\n" +
               "   <subscribe id='_subId:1'/>\n" +
               "   <clientProperty name='aKey' type='boolean'>true</clientProperty>\n" +
               "   <clientProperty name='aKey2'>Bla</clientProperty>\n" +
               "</qos>\n";
         performanceTest(xml, factory);
         log.info("---------------- Json -------------");
         String json = """
               {
                 "state": {
                   "id": "ERASED",
                   "info": "QUEUED[bilbo]"
                 },
                 "key": {
                   "oid": "yourMessageOid"
                 },
                 "subscribe": {
                   "id": "_subId:1"
                 },
                 "rcvTimestamp": "1013346248150000001"
               }
                              """;
         performanceTest(json, jsonFactory);


      }
      catch (XmlBlasterException e) {
         fail("testPerformance failed: " + e.toString());
      }

      System.out.println("***StatusQosFactoryTest: testPerformance [SUCCESS]");
   }
   
   private void performanceTest(String msg, I_StatusQosFactory fac) throws XmlBlasterException {
      for (int j=0; j<5; j++) {
         int num = 1000;
         long start = System.currentTimeMillis();
         for (int i=0; i<num; i++) {
            StatusQosData qos = fac.readObject(msg);
         }
         long elapsed = System.currentTimeMillis() - start;
         log.info(num + " parses for " + fac.getName() + ": " + elapsed + " millisec -> " +
                  ((((double)elapsed)*1000.*1000.)/((double)num)) + " nanosec/parse");
      }
   }

   public void testMethodTags() {
      if (factory.getName() == "StatusQosQuickParseFactory") {
         System.out.print("***StatusQosFactoryTest: Skipping testMethodTags for 'StatusQosQuickParseFactory'");
         return;
      }
      try {
         System.out.println("***StatusQosFactoryTest: testMethodTags ...");

         // Test all method tags and the MethodName they must map to
         Map<String, MethodName> cases = new HashMap<>();
         cases.put("<isPublish/>",     MethodName.PUBLISH);
         cases.put("<isUpdate/>",      MethodName.UPDATE);
         cases.put("<isErase/>",       MethodName.ERASE);
         cases.put("<isSubscribe/>",   MethodName.SUBSCRIBE);
         cases.put("<isUnSubscribe/>", MethodName.UNSUBSCRIBE);

         for (Map.Entry<String, MethodName> entry : cases.entrySet()) {
            String tag = entry.getKey();
            MethodName expectedMethod = entry.getValue();

            String xml = "<qos>" + tag + "</qos>";

            // XML parsing
            StatusQosData qos = factory.readObject(xml);

            // roundtrip XML
            String newXml = factory.writeObject(qos, null, null);
            log.info("newXml of (" + tag + ") is:\n" + newXml);

            // roundtrip JSON
            String json = jsonFactory.writeObject(qos, "", null, true);
            log.info("json of (" + tag + ") is:\n" + json);

            qos = jsonFactory.readObject(json);

            assertEquals(
                  "Method mismatch for tag: " + tag,
                  expectedMethod,
                  qos.getMethod()
            );
         }

      } catch (XmlBlasterException e) {
         fail("testMethodTags failed: " + e.toString());
      }

      System.out.println("***StatusQosFactoryTest: testMethodTags [SUCCESS]");
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
      TestSuite suite= new TestSuite();
      Global glob = new Global();
      for (int i=0; i<IMPL.length; i++) {
         suite.addTest(new StatusQosFactoryTest(glob, "testDefault", i));
         suite.addTest(new StatusQosFactoryTest(glob, "testParse", i));
         suite.addTest(new StatusQosFactoryTest(glob, "testToXmlAndToJson", i));
         suite.addTest(new StatusQosFactoryTest(glob, "testPerformance", i));
         suite.addTest(new StatusQosFactoryTest(glob, "testToXmlCp", i));
         suite.addTest(new StatusQosFactoryTest(glob, "testMethodTags", i));
      }
      return suite;
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
    * </pre>
    */
   public static void main(String args[]) {
      Global glob = new Global(args);
      for (int i=0; i<IMPL.length; i++) {
         StatusQosFactoryTest testSub = new StatusQosFactoryTest(glob, "StatusQosFactoryTest", i);
         testSub.setUp();
         testSub.testDefault();
         testSub.testParse();
         testSub.testPerformance();
         testSub.testToXmlAndToJson();
         testSub.testToXmlCp();
         testSub.testMethodTags();
         //testSub.tearDown();
      }
   }
}
