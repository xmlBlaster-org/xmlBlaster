package org.xmlBlaster.test.classtest;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.util.enum.Constants;

import junit.framework.*;

/**
 * Test EraseRetQos. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.EraseRetQosTest
 * @see org.xmlBlaster.client.EraseRetQos
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.erase.html" target="others">the interface.erase requirement</a>
 */
public class EraseRetQosTest extends TestCase {
   protected Global glob;
   int counter = 0;

   public EraseRetQosTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();
   }

   public void testParse() {
      System.out.println("***EraseRetQosTest: testParse ...");
      
      try {
         EraseRetQos qos = new EraseRetQos(glob, "<qos><state id='aaa' info='bbb'/><key oid='xxx'/></qos>");
         assertEquals("stateId failed", "aaa", qos.getStateId());
         assertEquals("stateInfo failed", "bbb", qos.getStateInfo());
         assertEquals("key oid failed", "xxx", qos.getOid());

         qos = new EraseRetQos(glob, "<qos/>");
         assertEquals("stateId failed", Constants.STATE_OK, qos.getStateId());
         assertEquals("stateInfo failed", null, qos.getStateInfo());
         assertEquals("key oid failed", null, qos.getOid());

         qos = new EraseRetQos(glob, null);
         assertEquals("stateId failed", Constants.STATE_OK, qos.getStateId());
         assertEquals("stateInfo failed", null, qos.getStateInfo());
         assertEquals("key oid failed", null, qos.getOid());

         qos = new EraseRetQos(glob, "");
         assertEquals("stateId failed", Constants.STATE_OK, qos.getStateId());
         assertEquals("stateInfo failed", null, qos.getStateInfo());
         assertEquals("key oid failed", null, qos.getOid());

         /* We don't care:
         try {
            qos = new EraseRetQos(glob, "<qos");
            assertEquals("stateId failed", Constants.STATE_OK, qos.getStateId());
            assertEquals("stateInfo failed", null, qos.getStateInfo());
            assertEquals("key oid failed", null, qos.getOid());
            fail("Expected exception");
         }
         catch (XmlBlasterException e) {
            System.out.println("***EraseRetQosTest: testParse SUCCESS, expected exception.");
         }
         */
      }
      catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      System.out.println("***EraseRetQosTest: testParse [SUCCESS]");
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.EraseRetQosTest
    * </pre>
    */
   public static void main(String args[])
   {
      EraseRetQosTest testSub = new EraseRetQosTest("EraseRetQosTest");
      testSub.setUp();
      testSub.testParse();
      //testSub.tearDown();
   }
}
