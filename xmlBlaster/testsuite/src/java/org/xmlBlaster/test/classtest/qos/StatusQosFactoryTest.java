package org.xmlBlaster.test.classtest.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.qos.I_StatusQosFactory;
import org.xmlBlaster.util.qos.StatusQosSaxFactory;
import org.xmlBlaster.util.qos.StatusQosQuickParseFactory;
import org.xmlBlaster.client.qos.GetReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.def.Constants;

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

   public StatusQosFactoryTest(Global glob, String name, int currImpl) {
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
      System.out.println("***StatusQosFactoryTest: testParse ...");
      
      try {
         String xml =
            "<qos>\n" +
            "   <state id='ERASED' info='QUEUED[bilbo]'/>\n" +
            "   <key oid='yourMessageOid'/>\n" +
            "   <subscribe id='_subId:1'/>\n" +
            "</qos>\n";

         StatusQosData qos = factory.readObject(xml);

         assertEquals("", Constants.STATE_ERASED, qos.getState());
         assertEquals("", false, qos.isOk());
         assertEquals("", true, qos.isErased());
         assertEquals("", false, qos.isTimeout());
         assertEquals("", false, qos.isForwardError());
         assertEquals("", "QUEUED[bilbo]", qos.getStateInfo());
         assertEquals("", "yourMessageOid", qos.getKeyOid());
         assertEquals("", "_subId:1", qos.getSubscriptionId());
      }
      catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      System.out.println("***StatusQosFactoryTest: testParse [SUCCESS]");
   }

   /**
    * Test toXml (parse - createXml - parse again - test)
    */
   public void testToXml() {
      System.out.println("***StatusQosFactoryTest: testToXml ...");
      
      try {
         String xml =
            "<qos>\n" +
            "   <state id='ERASED' info='QUEUED[bilbo]'/>\n" +
            "   <key oid='yourMessageOid'/>\n" +
            "   <subscribe id='_subId:1'/>\n" +
            "</qos>\n";

         StatusQosData qos = factory.readObject(xml);
         String newXml = qos.toXml();
         log.info("New XML=" + newXml);
         qos = factory.readObject(newXml);

         assertEquals("", Constants.STATE_ERASED, qos.getState());
         assertEquals("", false, qos.isOk());
         assertEquals("", true, qos.isErased());
         assertEquals("", false, qos.isTimeout());
         assertEquals("", false, qos.isForwardError());
         assertEquals("", "QUEUED[bilbo]", qos.getStateInfo());
         assertEquals("", "yourMessageOid", qos.getKeyOid());
         assertEquals("", "_subId:1", qos.getSubscriptionId());
      }
      catch (XmlBlasterException e) {
         fail("testToXml failed: " + e.toString());
      }

      System.out.println("***StatusQosFactoryTest: testToXml [SUCCESS]");
   }

   /**
    * Tests empty xml string
    */
   public void testDefault() {
      System.out.println("***StatusQosFactoryTest: testDefault ...");
      
      try {
         StatusQosData qos = factory.readObject((String)null);

         assertEquals("", Constants.STATE_OK, qos.getState());
         assertEquals("", true, qos.isOk());
         assertEquals("", false, qos.isErased());
         assertEquals("", false, qos.isTimeout());
         assertEquals("", false, qos.isForwardError());
         assertEquals("", null, qos.getStateInfo());
         assertEquals("", null, qos.getKeyOid());
         assertEquals("", null, qos.getSubscriptionId());
      }
      catch (XmlBlasterException e) {
         fail("testDefault failed: " + e.toString());
      }

      System.out.println("***StatusQosFactoryTest: testDefault [SUCCESS]");
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
            "</qos>\n";

         for (int j=0; j<5; j++) {
            int num = 1000;
            long start = System.currentTimeMillis();
            for (int i=0; i<num; i++) {
               StatusQosData qos = factory.readObject(xml);
            }
            long elapsed = System.currentTimeMillis() - start;
            log.info(num + " parses for " + factory.getName() + ": " + elapsed + " millisec -> " +
                     ((((double)elapsed)*1000.*1000.)/((double)num)) + " nanosec/parse");
         }
      }
      catch (XmlBlasterException e) {
         fail("testPerformance failed: " + e.toString());
      }

      System.out.println("***StatusQosFactoryTest: testPerformance [SUCCESS]");
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
         suite.addTest(new StatusQosFactoryTest(glob, "testToXml", i));
         suite.addTest(new StatusQosFactoryTest(glob, "testPerformance", i));
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
         testSub.testToXml();
         //testSub.tearDown();
      }
   }
}
