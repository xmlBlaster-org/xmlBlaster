package org.xmlBlaster.test.classtest.key;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.key.I_MsgKeyFactory;
import org.xmlBlaster.client.key.GetReturnKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.util.def.Constants;

import junit.framework.*;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;


/**
 * Test I_MsgKeyFactory implementations. 
 * <p />
 * <pre>
 * java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.key.MsgKeyFactoryTest
 * </pre>
 * @see org.xmlBlaster.util.key.MsgKeySaxFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">the xmlBlaster access interface requirement</a>
 */
public class MsgKeyFactoryTest extends XMLTestCase {
   private String ME = "MsgKeyFactoryTest";
   protected final Global glob;
   private static Logger log = Logger.getLogger(MsgKeyFactoryTest.class.getName());
   private String currImpl;
   private I_MsgKeyFactory factory;
   static I_MsgKeyFactory[] IMPL = { 
                   new org.xmlBlaster.util.key.MsgKeySaxFactory(Global.instance()),
                 };

   public MsgKeyFactoryTest(Global glob, String name, int currImpl) {
      super(name);
      this.glob = glob;

      this.factory = IMPL[currImpl];
      XMLUnit.setIgnoreWhitespace(true);
   }

   protected void setUp() {
      log.info("Testing parser factory " + factory.getName());
   }

   /**
    * Tries with all known tags
    */
   public void testParse() {
      System.out.println("***MsgKeyFactoryTest: testParse ...");
      
      try {
         String xml = 
           "<key oid='HELLO' contentMime='image/gif' contentMimeExtended='2.0' domain='RUGBY'>\n" +
           "   <filter><subtag></subtag></filter>\n" +
           "</key>\n";
         MsgKeyData key = factory.readObject(xml);

         assertEquals("", "HELLO", key.getOid());
         assertEquals("", "image/gif", key.getContentMime());
         assertEquals("", "2.0", key.getContentMimeExtended());
         assertEquals("", "RUGBY", key.getDomain());
         assertEquals("", "<filter><subtag></subtag></filter>", key.getClientTags());
         assertEquals("", false, key.isInternal());
         assertEquals("", false, key.isPluginInternal());
         assertEquals("", false, key.isDeadMessage());
      }
      catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      try {
         String xml = "<key oid='__HELLO'/>";
         MsgKeyData key = factory.readObject(xml);

         assertEquals("", "__HELLO", key.getOid());
         assertEquals("", true, key.isInternal());
         assertEquals("", false, key.isPluginInternal());
         assertEquals("", false, key.isDeadMessage());
      }
      catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }


      try {
         String xml = "<key oid='_HELLO'/>";
         MsgKeyData key = factory.readObject(xml);

         assertEquals("", "_HELLO", key.getOid());
         assertEquals("", false, key.isInternal());
         assertEquals("", true, key.isPluginInternal());
         assertEquals("", false, key.isDeadMessage());
      }
      catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      try {
         String xml = "<key oid='" + Constants.OID_DEAD_LETTER + "'/>";
         MsgKeyData key = factory.readObject(xml);

         assertEquals("", Constants.OID_DEAD_LETTER, key.getOid());
         assertEquals("", true, key.isInternal());
         assertEquals("", false, key.isPluginInternal());
         assertEquals("", true, key.isDeadMessage());
      }
      catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      try {
         String xml = "<key/>";
         MsgKeyData key = factory.readObject(xml);

         assertTrue("", (String)null != key.getOid());
         assertEquals("", false, key.isInternal());
         assertEquals("", false, key.isPluginInternal());
         assertEquals("", false, key.isDeadMessage());
      }
      catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      System.out.println("***MsgKeyFactoryTest: testParse [SUCCESS]");
   }

   /**
    * Tests empty xml string
    */
   public void testDefault() {
      System.out.println("***MsgKeyFactoryTest: testDefault ...");
      
      try {
         MsgKeyData key = factory.readObject((String)null);
         assertTrue("", (String)null != key.getOid());  // should be generated
         assertEquals("", (String)null, key.getClientTags());
         assertEquals("", MsgKeyData.CONTENTMIME_DEFAULT, key.getContentMime());
         assertEquals("", (String)null, key.getContentMimeExtended());
         assertEquals("", (String)null, key.getDomain());
         assertEquals("", false, key.isInternal());
         assertEquals("", false, key.isPluginInternal());
         assertEquals("", false, key.isDeadMessage());
      }
      catch (XmlBlasterException e) {
         fail("testDefault failed: " + e.toString());
      }

      System.out.println("***MsgKeyFactoryTest: testDefault [SUCCESS]");
   }

   /**
    * Test toXml (parse - createXml - parse again - test)
    */
   public void testToXml() {
      System.out.println("***MsgKeyFactoryTest: testToXml ...");
      
      try {
         String xml = 
           "<key oid='HELLO' contentMime='image/gif' contentMimeExtended='1.0' domain='RUGBY'>\n" +
           "   <filter><subtag></subtag></filter>\n" +
           "</key>\n";
         MsgKeyData key = factory.readObject(xml);
         String newXml = key.toXml();
         log.info("New XML=" + newXml);
         key = factory.readObject(newXml);

         assertEquals("", "HELLO", key.getOid());
         assertEquals("", "image/gif", key.getContentMime());
         assertEquals("", "1.0", key.getContentMimeExtended());
         assertEquals("", "RUGBY", key.getDomain());
         assertEquals("", "<filter><subtag></subtag></filter>", key.getClientTags());
      }
      catch (XmlBlasterException e) {
         fail("testToXml failed: " + e.toString());
      }

      System.out.println("***MsgKeyFactoryTest: testToXml [SUCCESS]");
   }

   /**
    * Tests client side PublishKey. 
    */
   public void testPublishKey() {
      System.out.println("***MsgKeyFactoryTest: PublishKey ...");
      
      try {
         String clientTags = "<a><b></b></a><c></c>";
         PublishKey publishKey = new PublishKey(glob, "oid");
         publishKey.setDomain("domain");
         publishKey.setClientTags(clientTags);
         publishKey.setContentMime("image/png");
         publishKey.setContentMimeExtended("2");

         System.out.println("PublishKey: " + publishKey.toXml());

         MsgKeyData key = factory.readObject(publishKey.toXml());

         assertEquals("", "oid", key.getOid());
         assertEquals("", "domain", key.getDomain());
         assertEquals("Input='"+clientTags+"' output='"+key.getClientTags()+"'", clientTags, key.getClientTags());
         assertEquals("", "image/png", key.getContentMime());
         assertEquals("", "2", key.getContentMimeExtended());
      }
      catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }
      System.out.println("***MsgKeyFactoryTest: PublishKey [SUCCESS]");
   }

   /**
    * Tests client side GetReturnKey. 
    */
   public void testGetReturnKey() {
      System.out.println("***MsgKeyFactoryTest: GetReturnKey ...");
      
      try {
         String clientTags = "   <filter><subtag></subtag></filter>\n";
         String xml = 
           "<key oid='HELLO' contentMime='image/png' contentMimeExtended='2.5' domain='RUGBY'>\n" +
           clientTags +
           "</key>\n";
         MsgKeyData key = factory.readObject(xml);
         GetReturnKey getKey = new GetReturnKey(glob, xml);

         System.out.println("GetReturnKey: " + getKey.toXml());

         assertEquals("", "HELLO", getKey.getOid());
         assertEquals("", "RUGBY", getKey.getDomain());
         assertEquals("", "image/png", getKey.getContentMime());
         assertEquals("", "2.5", getKey.getContentMimeExtended());
         assertEquals("Input='"+clientTags+"' output='"+key.getClientTags()+"'", clientTags.trim(), getKey.getClientTags());
         assertEquals("", false, getKey.isInternal());
         assertEquals("", false, getKey.isPluginInternal());
         assertEquals("", false, getKey.isDeadMessage());
      }
      catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }
      System.out.println("***MsgKeyFactoryTest: GetReturnKey [SUCCESS]");
   }

   /**
    * Tests client side UpdateKey. 
    */
   public void testUpdateKey() {
      System.out.println("***MsgKeyFactoryTest: UpdateKey ...");
      try {
         String clientTags = "   <filter><subtag></subtag></filter>\n";
         String xml = 
           "<key oid='HELLO' contentMime='image/png' contentMimeExtended='2.5' domain='RUGBY'>\n" +
           clientTags +
           "</key>\n";
         MsgKeyData key = factory.readObject(xml);
         UpdateKey updateKey = new UpdateKey(glob, xml);

         System.out.println("UpdateKey: " + updateKey.toXml());

         assertEquals("", "HELLO", updateKey.getOid());
         assertEquals("", "RUGBY", updateKey.getDomain());
         assertEquals("", "image/png", updateKey.getContentMime());
         assertEquals("", "2.5", updateKey.getContentMimeExtended());
         assertEquals("Input='"+clientTags+"' output='"+key.getClientTags()+"'", clientTags.trim(), updateKey.getClientTags());
         assertEquals("", false, updateKey.isInternal());
         assertEquals("", false, updateKey.isPluginInternal());
         assertEquals("", false, updateKey.isDeadMessage());
      }
      catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }
      System.out.println("***MsgKeyFactoryTest: UpdateKey [SUCCESS]");
   }

   /**
    * Tests client side PublishKey. 
    */
   public void testEmbeddedKeyTag() {
      System.out.println("***MsgKeyFactoryTest: embeddedKeyTag ...");
      
      try {
         String keyLiteral = "<key oid='oid' ><client><xkey><xqos><xkey>xxx</xkey></xqos></xkey></client></key>";
         MsgKeyData key = factory.readObject(keyLiteral);
         log.info("testEmbeddedKeyTag: key (should)='" + keyLiteral);
         log.info("testEmbeddedKeyTag: key (is)    ='" + key.toXml());         
         assertXMLEqual(keyLiteral, key.toXml());
         keyLiteral = "<key oid='oid' ><client><key><qos><key>xxx</key></qos></key></client></key>";
         key = factory.readObject(keyLiteral);
         log.info("testEmbeddedKeyTag: key (should)='" + keyLiteral);
         log.info("testEmbeddedKeyTag: key (is)    ='" + key.toXml());
         assertXMLEqual(keyLiteral, key.toXml());
      }
      catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }
      System.out.println("***MsgKeyFactoryTest: EmbeddedKeyTag [SUCCESS]");
   }

   protected void tearDown() {
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
      TestSuite suite= new TestSuite();
      Global glob = new Global();
      for (int i=0; i<IMPL.length; i++) {
         suite.addTest(new MsgKeyFactoryTest(glob, "testDefault", i));
         suite.addTest(new MsgKeyFactoryTest(glob, "testParse", i));
         suite.addTest(new MsgKeyFactoryTest(glob, "testToXml", i));
         suite.addTest(new MsgKeyFactoryTest(glob, "testPublishKey", i));
         suite.addTest(new MsgKeyFactoryTest(glob, "testUpdateKey", i));
         suite.addTest(new MsgKeyFactoryTest(glob, "testGetReturnKey", i));
         suite.addTest(new MsgKeyFactoryTest(glob, "testEmbeddedKeyTag", i));
      }
      return suite;
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.key.MsgKeyFactoryTest
    * </pre>
    */
   public static void main(String args[]) {
      Global glob = new Global(args);
      for (int i=0; i<IMPL.length; i++) {
         MsgKeyFactoryTest testSub = new MsgKeyFactoryTest(glob, "MsgKeyFactoryTest", i);
         testSub.setUp();
         testSub.testDefault();
         testSub.testParse();
         testSub.testToXml();
         testSub.testPublishKey();
         testSub.testUpdateKey();
         testSub.testGetReturnKey();
         testSub.testEmbeddedKeyTag();
         testSub.tearDown();
      }
   }
}

