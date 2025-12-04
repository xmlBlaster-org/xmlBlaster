package org.xmlBlaster.test.classtest.qos;

import junit.framework.TestCase;

import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.qos.I_QueryQosFactory;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.QueryQosJsonFactory;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Test I_QueryQosFactory implementations in JSON<br />
 * NOTE: Additionnal Tests can be found in QueryQosFacotryTest
 * <p />
 * 
 * <pre>
 * java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.qos.QueryQosJsonFactoryTest
 * </pre>
 * 
 * @see org.xmlBlaster.test.classtest.qos.QueryQosFactoryTest
 * @see org.xmlBlaster.util.qos.QueryQosSaxFactory
 * @see <a href=
 *      "http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html"
 *      target="others">the xmlBlaster access interface requirement</a>
 */
public class QueryQosJsonFactoryTest extends TestCase {
   protected final Global glob;
   private static Logger log = Logger.getLogger(QueryQosJsonFactoryTest.class.getName());
   private I_QueryQosFactory factory;

   public QueryQosJsonFactoryTest(Global glob, String name) {
      super(name);
      this.glob = glob;

      this.factory = new QueryQosJsonFactory(glob);
   }

   protected void setUp() {
      log.info("Testing JSON parser factory " + factory.getName());
   }

   /**
    * Tries with all known keys
    */
   public void testParse() {
      System.out.println("***QueryQosJsonFactoryTest: testParse ...");

      try {
         String json = """
                  {
                       "subscribe": "_subId:1",
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
                         }, {
                           "type": "anotherPluginString",
                           "version": "1.1",
                           "value": "\\\"lolIwillescape\\\", {\\\"thisisnotafield\\\": \\\"thisisnotavalue\\\"}"
                         }, {
                           "type": "anotherPluginObject",
                           "version": "1.1",
                           "value": {"a": 1, "b": true}
                         }, {
                           "type": "anotherPluginBool",
                           "version": "1.1",
                           "value": true
                         },  {
                           "type": "anotherPluginNumber",
                           "version": "1.1",
                           "value": 100
                         }
                       ]
                     }
               """;

         System.out.println("QueryQuosJson" + json);
         QueryQosJsonFactory jsonFactory = new QueryQosJsonFactory(glob);
         QueryQosData qos = jsonFactory.readObject(json);
         System.out.println("QueryQuosJson after parsing: " + qos.toJson());

         assertEquals("", "_subId:1", qos.getSubscriptionId());
         assertEquals("", true, qos.getForceDestroy());
         assertEquals("", true, qos.isSubIdGeneratedIncludeClusterNodeId());
         assertEquals("", false, qos.getWantMeta());
         assertEquals("", false, qos.getWantContent());
         assertEquals("", false, qos.getWantLocal());
         assertEquals("", false, qos.getWantInitialUpdate());
         AccessFilterQos[] filterArr = qos.getAccessFilterArr();
         assertEquals("", 5, filterArr.length);
         assertEquals("", "myPlugin", filterArr[0].getType());
         assertEquals("", "1.0", filterArr[0].getVersion());
         assertEquals("", "a!=100", filterArr[0].getQuery().toString());
         assertEquals("", "anotherPluginString", filterArr[1].getType());
         assertEquals("", "1.1", filterArr[1].getVersion());
         assertEquals("", "\"lolIwillescape\", {\"thisisnotafield\": \"thisisnotavalue\"}",
               filterArr[1].getQuery().toString());
         assertEquals("", "anotherPluginObject", filterArr[2].getType());
         assertEquals("", "{\"a\":1,\"b\":true}", filterArr[2].getQuery().toString());
         assertEquals("", "anotherPluginBool", filterArr[3].getType());
         assertEquals("", "true", filterArr[3].getQuery().toString());
         assertEquals("", "anotherPluginNumber", filterArr[4].getType());
         assertEquals("", "100", filterArr[4].getQuery().toString());
      } catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      System.out.println("***QueryQosJsonFactoryTest: testParse [SUCCESS]");
   }

   /**
    * Test toJson (parse - createJson- parse again - test) this additionally tests
    * some value fields in FileterQueryQosvunique to JSON
    */
   public void testToJson() {
      System.out.println("***QueryQosJsonFactoryTest: testToJson ...");

      try {
         String json = """
               {
                   "subscribe": "_subId:1",
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
                         }, {
                           "type": "anotherPluginString",
                           "version": "1.1",
                           "value": "\\\"lolIwillescape\\\", {\\\"thisisnotafield\\\": \\\"thisisnotavalue\\\"}"
                         }, {
                           "type": "anotherPluginObject",
                           "version": "1.1",
                           "value": {"a": 1, "b": true}
                         }, {
                           "type": "anotherPluginBool",
                           "version": "1.1",
                           "value": true
                         },  {
                           "type": "anotherPluginNumber",
                           "version": "1.1",
                           "value": 100
                         }
                   ]
                 }
               """;
         log.info("Old JSON: " + json);
         QueryQosData qos = factory.readObject(json);
         String newJson = qos.toJson();
         log.info("New Json=" + newJson);
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
         assertEquals("", 5, filterArr.length);
         assertEquals("", "myPlugin", filterArr[0].getType());
         assertEquals("", "1.0", filterArr[0].getVersion());
         assertEquals("", "a!=100", filterArr[0].getQuery().toString());
         assertEquals("", "anotherPluginString", filterArr[1].getType());
         assertEquals("", "1.1", filterArr[1].getVersion());
         assertEquals("", "\"lolIwillescape\", {\"thisisnotafield\": \"thisisnotavalue\"}",
               filterArr[1].getQuery().toString());
         assertEquals("", "anotherPluginObject", filterArr[2].getType());
         assertEquals("", "{\"a\":1,\"b\":true}", filterArr[2].getQuery().toString());
         assertEquals("", "anotherPluginBool", filterArr[3].getType());
         assertEquals("", "true", filterArr[3].getQuery().toString());
         assertEquals("", "anotherPluginNumber", filterArr[4].getType());
         assertEquals("", "100", filterArr[4].getQuery().toString());
      } catch (XmlBlasterException e) {
         fail("testToJson failed: " + e.toString());
      }

      System.out.println("***QueryQosJsonFactoryTest: testToJson [SUCCESS]");
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
      TestSuite suite = new TestSuite();
      Global glob = new Global();
      suite.addTest(new QueryQosJsonFactoryTest(glob, "testParse"));
      suite.addTest(new QueryQosJsonFactoryTest(glob, "testToJson"));

      return suite;
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.qos.QueryQosJsonFactoryTest
    * </pre>
    */
   public static void main(String args[]) {
      Global glob = new Global(args);
      QueryQosJsonFactoryTest testSub = new QueryQosJsonFactoryTest(glob, "QueryQosJsonFactoryTest");
      testSub.setUp();
      testSub.testParse();
      testSub.testToJson();

   }
}
