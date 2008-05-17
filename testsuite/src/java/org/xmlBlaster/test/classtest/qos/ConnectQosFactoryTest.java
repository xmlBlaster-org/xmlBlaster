package org.xmlBlaster.test.classtest.qos;

import java.util.logging.Logger;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.I_ConnectQosFactory;
import org.xmlBlaster.util.qos.address.Address;

/**
 * Test I_ConnectQosFactory implementations.
 * <p />
 * 
 * <pre>
 * java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.qos.ConnectQosFactoryTest
 * </pre>
 * 
 * @see org.xmlBlaster.util.qos.ConnectQosSaxFactory
 * @see org.xmlBlaster.util.qos.ConnectQosQuickParseFactory
 * @see <a
 *      href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html"
 *      target="others">the xmlBlaster access interface requirement</a>
 */
public class ConnectQosFactoryTest extends TestCase {
   protected final Global glob;

   private static Logger log = Logger.getLogger(ConnectQosFactoryTest.class
         .getName());

   private I_ConnectQosFactory factory;

   static I_ConnectQosFactory[] IMPL = { new org.xmlBlaster.util.qos.ConnectQosSaxFactory(
         Global.instance()) };

   public ConnectQosFactoryTest() {
      this(Global.instance(), "ConnectQosFactoryTest", 0);
   }

   public ConnectQosFactoryTest(Global glob, String name, int currImpl) {
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
      System.out.println("***ConnectQosFactoryTest: testParse ...");

      try {
         String xml = "<qos>\n"
               + "   <persistent/>\n"
               + "   <address type='SOCKET'>\n"
               + "      socket://:7501\n"
               + "      <attribute name='useRemoteLoginAsTunnel'>true</attribute>\n"
               + "   </address>\n" + "</qos>\n";

         ConnectQosData qos = factory.readObject(xml);

         String xml2 = qos.toXml();
         qos = factory.readObject(xml2);
         log.info(xml2);

         assertTrue(qos.isPersistent());
         Address address = qos.getAddress();
         assertEquals(true, address.getEnv("useRemoteLoginAsTunnel", false)
               .getValue());
         // assertEquals(3412, address.getBootstrapPort());
         assertEquals("socket://:7501", address.getRawAddress());
      } catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      System.out.println("***ConnectQosFactoryTest: testParse [SUCCESS]");
   }

   /**
    * Test toXml (parse - createXml - parse again - test)
    */
   public void testToXml() {
      System.out.println("***ConnectQosFactoryTest: testToXml ...");
      /*
       * try { String xml = "<qos>\n" + " <state id='ERASED'
       * info='QUEUED[bilbo]'/>\n" + " <key oid='yourMessageOid'/>\n" + "
       * <subscribe id='_subId:1'/>\n" + "</qos>\n";
       * 
       * ConnectQosData qos = factory.readObject(xml); String newXml =
       * qos.toXml(); log.info("New XML=" + newXml); qos =
       * factory.readObject(newXml);
       * 
       * assertEquals("", Constants.STATE_ERASED, qos.getState());
       * assertEquals("", false, qos.isOk()); assertEquals("", true,
       * qos.isErased()); assertEquals("", false, qos.isTimeout());
       * assertEquals("", false, qos.isForwardError()); } catch
       * (XmlBlasterException e) { fail("testToXml failed: " + e.toString()); }
       * 
       * System.out.println("***ConnectQosFactoryTest: testToXml [SUCCESS]");
       */
   }

   /**
    * Tests empty xml string
    */
   public void testDefault() {
      System.out.println("***ConnectQosFactoryTest: testDefault ...");
      /*
       * try { ConnectQosData qos = factory.readObject((String)null);
       * //assertEquals("", Constants.STATE_OK, qos.getState()); TODO } catch
       * (XmlBlasterException e) { fail("testDefault failed: " + e.toString()); }
       * 
       * System.out.println("***ConnectQosFactoryTest: testDefault [SUCCESS]");
       */
   }

   /**
    * Tries with all known tags
    */
   public void testPerformance() {
      System.out.println("***ConnectQosFactoryTest: testPerformance ...");
      /*
       * try { String xml = "<qos>\n" + "</qos>\n";
       * 
       * for (int j=0; j<5; j++) { int num = 1000; long start =
       * System.currentTimeMillis(); for (int i=0; i<num; i++) { ConnectQosData
       * qos = factory.readObject(xml); } long elapsed =
       * System.currentTimeMillis() - start; log.info(num + " parses for " +
       * factory.getName() + ": " + elapsed + " millisec -> " +
       * ((((double)elapsed)*1000.*1000.)/((double)num)) + " nanosec/parse"); } }
       * catch (XmlBlasterException e) { fail("testPerformance failed: " +
       * e.toString()); }
       * 
       * System.out.println("***ConnectQosFactoryTest: testPerformance
       * [SUCCESS]");
       */
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
      TestSuite suite = new TestSuite();
      Global glob = new Global();
      for (int i = 0; i < IMPL.length; i++) {
         suite.addTest(new ConnectQosFactoryTest(glob, "testDefault", i));
         suite.addTest(new ConnectQosFactoryTest(glob, "testParse", i));
         suite.addTest(new ConnectQosFactoryTest(glob, "testToXml", i));
         suite.addTest(new ConnectQosFactoryTest(glob, "testPerformance", i));
      }
      return suite;
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.qos.ConnectQosFactoryTest
    * </pre>
    */
   public static void main(String args[]) {
      Global glob = new Global(args);
      for (int i = 0; i < IMPL.length; i++) {
         ConnectQosFactoryTest testSub = new ConnectQosFactoryTest(glob,
               "ConnectQosFactoryTest", i);
         testSub.setUp();
         testSub.testDefault();
         testSub.testParse();
         testSub.testPerformance();
         testSub.testToXml();
         // testSub.tearDown();
      }
   }
}
