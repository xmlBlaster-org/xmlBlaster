package org.xmlBlaster.test.classtest;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.SubscribeRetQos;
import org.xmlBlaster.util.enum.Constants;

import junit.framework.*;

/**
 * Test SubscribeRetQos. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.SubscribeRetQosTest
 * @see org.xmlBlaster.client.SubscribeRetQos
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html" target="others">the interface.subscribe requirement</a>
 */
public class SubscribeRetQosTest extends TestCase {
   protected Global glob;
   int counter = 0;

   public SubscribeRetQosTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();
   }

   public void testParse() {
      System.out.println("***SubscribeRetQosTest: testParse ...");
      
      try {
         SubscribeRetQos qos = new SubscribeRetQos(glob, "<qos><state id='aaa' info='bbb'/><subscribe id='xxx'/></qos>");
         assertEquals("stateId failed", "aaa", qos.getStateId());
         assertEquals("stateInfo failed", "bbb", qos.getStateInfo());
         assertEquals("subscribe id failed", "xxx", qos.getSubscriptionId());

         qos = new SubscribeRetQos(glob, "<qos/>");
         assertEquals("stateId failed", Constants.STATE_OK, qos.getStateId());
         assertEquals("stateInfo failed", null, qos.getStateInfo());
         assertEquals("subscribe id failed", null, qos.getSubscriptionId());

         qos = new SubscribeRetQos(glob, "<qos><state id='" + Constants.STATE_ERASED + "'/></qos>");
         assertEquals("stateId failed", Constants.STATE_ERASED, qos.getStateId());
         assertEquals("stateInfo failed", null, qos.getStateInfo());
         assertEquals("subscribe id failed", null, qos.getSubscriptionId());

         qos = new SubscribeRetQos(glob, null);
         assertEquals("stateId failed", Constants.STATE_OK, qos.getStateId());
         assertEquals("stateInfo failed", null, qos.getStateInfo());
         assertEquals("subscribe id failed", null, qos.getSubscriptionId());

         qos = new SubscribeRetQos(glob, "");
         assertEquals("stateId failed", Constants.STATE_OK, qos.getStateId());
         assertEquals("stateInfo failed", null, qos.getStateInfo());
         assertEquals("subscribe id failed", null, qos.getSubscriptionId());

         /* We don't care:
         try {
            qos = new SubscribeRetQos(glob, "<qos");
            assertEquals("stateId failed", Constants.STATE_OK, qos.getStateId());
            assertEquals("stateInfo failed", null, qos.getStateInfo());
            assertEquals("subscribe id failed", null, qos.getSubscriptionId());
            fail("Expected exception");
         }
         catch (XmlBlasterException e) {
            System.out.println("***SubscribeRetQosTest: testParse SUCCESS, expected exception.");
         }
         */
      }
      catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      System.out.println("***SubscribeRetQosTest: testParse [SUCCESS]");
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.SubscribeRetQosTest
    * </pre>
    */
   public static void main(String args[])
   {
      SubscribeRetQosTest testSub = new SubscribeRetQosTest("SubscribeRetQosTest");
      testSub.setUp();
      testSub.testParse();
      //testSub.tearDown();
   }
}
