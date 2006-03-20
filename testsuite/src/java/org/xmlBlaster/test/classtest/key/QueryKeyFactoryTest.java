package org.xmlBlaster.test.classtest.key;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.key.I_QueryKeyFactory;
import org.xmlBlaster.util.key.QueryKeySaxFactory;
//import org.xmlBlaster.client.key.GetKey;
//import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.key.SubscribeKey;
//import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.key.KeyData;

import junit.framework.*;

/**
 * Test I_QueryKeyFactory implementations. 
 * <p />
 * <pre>
 * java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.key.QueryKeyFactoryTest
 * </pre>
 * @see org.xmlBlaster.util.key.QueryKeySaxFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">the xmlBlaster access interface requirement</a>
 */
public class QueryKeyFactoryTest extends TestCase {
   private String ME = "QueryKeyFactoryTest";
   protected final Global glob;
   private static Logger log = Logger.getLogger(QueryKeyFactoryTest.class.getName());
   private String currImpl;
   private I_QueryKeyFactory factory;
   static I_QueryKeyFactory[] IMPL = { 
                   new org.xmlBlaster.util.key.QueryKeySaxFactory(Global.instance()),
                 };

   public QueryKeyFactoryTest(Global glob, String name, int currImpl) {
      super(name);
      this.glob = glob;

      this.factory = IMPL[currImpl];
   }

   protected void setUp() {
      log.info("Testing parser factory " + factory.getName());
   }

   /**
    * Tries with all known tags
    */
   public void testParse() {
      System.out.println("***QueryKeyFactoryTest: testParse ...");
      
      try {
         String xml = 
           "<key oid='HELLO' contentMime='image/gif' contentMimeExtended='1.0' domain='RUGBY' queryType='" + Constants.XPATH + "'>\n" +
           "   <filter type='myPlugin' version='1.0'>a!=100</filter>\n" +
           "   <filter type='anotherPlugin' version='1.1'><![CDATA[b<100|a[0]>10]]></filter>\n" +
           "   //key\n" +
           "</key>\n";
         QueryKeyData key = factory.readObject(xml);

         assertEquals("", "HELLO", key.getOid());
         assertEquals("", Constants.XPATH, key.getQueryType());
         assertEquals("Got '" + key.getQueryString() + "'", "//key", key.getQueryString().trim());
         assertEquals("", "image/gif", key.getContentMime());
         assertEquals("", "1.0", key.getContentMimeExtended());
         assertEquals("", "RUGBY", key.getDomain());
         AccessFilterQos[] filterArr = key.getAccessFilterArr();
         assertEquals("", 2, filterArr.length);
         assertEquals("", "myPlugin", filterArr[0].getType());
         assertEquals("", "1.0", filterArr[0].getVersion());
         assertEquals("", "a!=100", filterArr[0].getQuery().toString());
         assertEquals("", "anotherPlugin", filterArr[1].getType());
         assertEquals("", "1.1", filterArr[1].getVersion());
         assertEquals("", "b<100|a[0]>10", filterArr[1].getQuery().toString());
      }
      catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      System.out.println("***QueryKeyFactoryTest: testParse [SUCCESS]");
   }

   /**
    * Tries with all known tags
    */
   public void testCdata() {
      System.out.println("***QueryKeyFactoryTest: testCdata ...");
      
      try {
         //String queryData = "<adapter>\n\n<![CDATA[\nHi\n \nworld\n\n]]>\n\n</adapter>";
         String queryData = "<database:adapter xmlns:database='http://www.xmlBlaster.org/jdbc'>"+
                            "<database:url>jdbc:dbfFile:.</database:url>"+
                            "<database:username>joe</database:username>"+
                            "<database:password>secret</database:password>"+
                            "<database:interaction type='update'></database:interaction>"+
                            "<database:command><![CDATA[ DROP TABLE IF EXISTS cars < 100 ]]></database:command>"+
                            "<database:connectionlifespan ttl='1'></database:connectionlifespan>"+
                            "<database:rowlimit max='1'></database:rowlimit>"+
                            "<database:confirmation confirm='true'></database:confirmation>"+
                            "</database:adapter>";

         String xml = 
           "<key oid='__sys__jdbc'>\n" +
           queryData +
           "</key>\n";
         QueryKeyData key = factory.readObject(xml);

         assertEquals("", "__sys__jdbc", key.getOid());
         assertEquals("queryData expected:\n'"+queryData.trim()+"'\n but is\n'"+key.getQueryString().trim()+"'\n",
                      queryData.trim(), key.getQueryString().trim());

         log.info("Parsed and recreated successfully CDATA section:\n" + key.getQueryString().trim() + "");
      }
      catch (XmlBlasterException e) {
         fail("testCdata failed: " + e.toString());
      }

      System.out.println("***QueryKeyFactoryTest: testCdata [SUCCESS]");
   }

   /**
    * Tests empty xml string
    */
   public void testDefault() {
      System.out.println("***QueryKeyFactoryTest: testDefault ...");
      
      try {
         QueryKeyData key = factory.readObject((String)null);
         assertEquals("", (String)null, key.getOid());
         assertEquals("", QueryKeyData.QUERYTYPE_DEFAULT, key.getQueryType());
         assertEquals("", (String)null, key.getQueryString());
         assertEquals("", QueryKeyData.CONTENTMIME_DEFAULT, key.getContentMime());
         assertEquals("", (String)null, key.getContentMimeExtended());
         assertEquals("", (String)null, key.getDomain());
         AccessFilterQos[] filterArr = key.getAccessFilterArr();
         assertTrue("", null == filterArr);
      }
      catch (XmlBlasterException e) {
         fail("testDefault failed: " + e.toString());
      }

      System.out.println("***QueryKeyFactoryTest: testDefault [SUCCESS]");
   }

   /**
    * Test toXml (parse - createXml - parse again - test)
    */
   public void testToXml() {
      System.out.println("***QueryKeyFactoryTest: testToXml ...");
      
      try {
         String xml = 
           "<key oid='HELLO' contentMime='image/gif' contentMimeExtended='1.0' domain='RUGBY' queryType='" + Constants.XPATH + "'>\n" +
           "   //key\n" +
           "   <filter type='myPlugin' version='1.0'>a!=100</filter>\n" +
           "   <filter type='anotherPlugin' version='1.1'><![CDATA[b<100|a[0]>10]]></filter>\n" +
           "</key>\n";
         QueryKeyData key = factory.readObject(xml);
         String newXml = key.toXml();
         log.info("New XML=" + newXml);
         key = factory.readObject(newXml);

         assertEquals("", "HELLO", key.getOid());
         assertEquals("", Constants.XPATH, key.getQueryType());
         assertEquals("", "//key", key.getQueryString());
         assertEquals("", "image/gif", key.getContentMime());
         assertEquals("", "1.0", key.getContentMimeExtended());
         assertEquals("", "RUGBY", key.getDomain());
         AccessFilterQos[] filterArr = key.getAccessFilterArr();
         assertEquals("", 2, filterArr.length);
         assertEquals("", "myPlugin", filterArr[0].getType());
         assertEquals("", "1.0", filterArr[0].getVersion());
         assertEquals("", "a!=100", filterArr[0].getQuery().toString());
         assertEquals("", "anotherPlugin", filterArr[1].getType());
         assertEquals("", "1.1", filterArr[1].getVersion());
         assertEquals("", "b<100|a[0]>10", filterArr[1].getQuery().toString());
      }
      catch (XmlBlasterException e) {
         fail("testToXml failed: " + e.toString());
      }

      System.out.println("***QueryKeyFactoryTest: testToXml [SUCCESS]");
   }

   /**
    * Tests client side SubscribeKey. 
    */
   public void testSubscribeKey() {
      System.out.println("***QueryKeyFactoryTest: SubscribeKey ...");
      
      try {
         SubscribeKey subscribeKey = new SubscribeKey(glob, "oid");
         subscribeKey.setDomain("domain");
         subscribeKey.setQueryString("//query");

         System.out.println("SubscribeKey: " + subscribeKey.toXml());

         QueryKeyData key = factory.readObject(subscribeKey.toXml());

         assertEquals("", "oid", key.getOid());
         assertEquals("", "domain", key.getDomain());
         assertEquals("", "//query", key.getQueryString());
         assertEquals("", "XPATH", key.getQueryType()); // The last was setQueryString() so this is the used type
      }
      catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }
      System.out.println("***QueryKeyFactoryTest: SubscribeKey [SUCCESS]");
   }

   /**
    * Tests client side SubscribeKey. 
    */
   public void testExactSubscribeKey() {
      System.out.println("***QueryKeyFactoryTest: testExactSubscribeKey ...");
      
      try {
         SubscribeKey subscribeKey = new SubscribeKey(glob, "myOid", "EXACT");

         assertEquals("", "myOid", subscribeKey.getOid());
         System.out.println("SubscribeKey: " + subscribeKey.toXml());

         QueryKeyData key = factory.readObject(subscribeKey.toXml());

         assertEquals("", "myOid", key.getOid());
         assertEquals("", KeyData.CONTENTMIME_DEFAULT, key.getContentMime());
         assertEquals("", KeyData.CONTENTMIMEEXTENDED_DEFAULT, key.getContentMimeExtended());
         assertEquals("", (String)null, key.getDomain());
         assertEquals("", "EXACT", key.getQueryType());
         assertEquals("", (String)null, key.getQueryString());
      }
      catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }

      try {
         SubscribeKey subscribeKey = new SubscribeKey(glob, "//myTag", Constants.XPATH);
         subscribeKey.getData().setContentMime("AAA");
         subscribeKey.getData().setContentMimeExtended("BBB");
         subscribeKey.setDomain("CCC");

         assertEquals("", "myOid", subscribeKey.getOid());
         System.out.println("SubscribeKey: " + subscribeKey.toXml());

         QueryKeyData key = factory.readObject(subscribeKey.toXml());

         assertEquals("", (String)null, key.getOid());
         assertEquals("", "AAA", key.getContentMime());
         assertEquals("", "BBB", key.getContentMimeExtended());
         assertEquals("", "CCC", key.getDomain());
         assertEquals("", Constants.XPATH, key.getQueryType());
         assertEquals("", "//myTag", key.getQueryString());
      }
      catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }
      System.out.println("***QueryKeyFactoryTest: testExactSubscribeKey [SUCCESS]");
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
      TestSuite suite= new TestSuite();
      Global glob = new Global();
      for (int i=0; i<IMPL.length; i++) {
         suite.addTest(new QueryKeyFactoryTest(glob, "testDefault", i));
         suite.addTest(new QueryKeyFactoryTest(glob, "testParse", i));
         suite.addTest(new QueryKeyFactoryTest(glob, "testCdata", i));
         suite.addTest(new QueryKeyFactoryTest(glob, "testToXml", i));
         suite.addTest(new QueryKeyFactoryTest(glob, "testSubscribeKey", i));
         suite.addTest(new QueryKeyFactoryTest(glob, "testExactSubscribeKey", i));
         /* TODO:
         suite.addTest(new QueryKeyFactoryTest(glob, "testEraseKey", i));
         suite.addTest(new QueryKeyFactoryTest(glob, "testGetKey", i));
         */
      }
      return suite;
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.key.QueryKeyFactoryTest
    * </pre>
    */
   public static void main(String args[]) {
      Global glob = Global.instance();
      glob.init(args);
      for (int i=0; i<IMPL.length; i++) {
         QueryKeyFactoryTest testSub = new QueryKeyFactoryTest(glob, "QueryKeyFactoryTest", i);
         testSub.setUp();
         testSub.testDefault();
         testSub.testParse();
         testSub.testCdata();
         testSub.testToXml();
         testSub.testSubscribeKey();
         testSub.testExactSubscribeKey();
         /* TODO:
         testSub.testEraseKey();
         testSub.testGetKey();
         */
         //testSub.tearDown();
      }
   }
}

