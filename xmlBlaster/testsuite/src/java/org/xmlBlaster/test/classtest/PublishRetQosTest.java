package org.xmlBlaster.test.classtest;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.PublishRetQos;
import org.xmlBlaster.util.enum.Constants;

import junit.framework.*;

/**
 * @deprecated Is deprecated because of PublishRetQos is deprecated
 */
public class PublishRetQosTest extends TestCase {
   protected Global glob;
   int counter = 0;

   public PublishRetQosTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();
   }

   public void testParse() {
      System.out.println("***PublishRetQosTest: testParse ...");
      
      try {
         PublishRetQos qos = new PublishRetQos(glob, "<qos><state id='aaa' info='bbb'/><key oid='xxx'/></qos>");
         assertEquals("stateId failed", "aaa", qos.getStateId());
         assertEquals("stateInfo failed", "bbb", qos.getStateInfo());
         assertEquals("key oid failed", "xxx", qos.getOid());

         qos = new PublishRetQos(glob, "<qos/>");
         assertEquals("stateId failed", Constants.STATE_OK, qos.getStateId());
         assertEquals("stateInfo failed", null, qos.getStateInfo());
         assertEquals("key oid failed", null, qos.getOid());

         /*
         qos = new PublishRetQos(glob, "<qos><state id='" + Constants.STATE_ERASED + "'/></qos>");
         assertEquals("stateId failed", Constants.STATE_ERASED, qos.getStateId());
         assertEquals("stateInfo failed", null, qos.getStateInfo());
         assertEquals("key oid failed", null, qos.getOid());
         */

         qos = new PublishRetQos(glob, null);
         assertEquals("stateId failed", Constants.STATE_OK, qos.getStateId());
         assertEquals("stateInfo failed", null, qos.getStateInfo());
         assertEquals("key oid failed", null, qos.getOid());

         qos = new PublishRetQos(glob, "");
         assertEquals("stateId failed", Constants.STATE_OK, qos.getStateId());
         assertEquals("stateInfo failed", null, qos.getStateInfo());
         assertEquals("key oid failed", null, qos.getOid());

         /* We don't care:
         try {
            qos = new PublishRetQos(glob, "<qos");
            assertEquals("stateId failed", Constants.STATE_OK, qos.getStateId());
            assertEquals("stateInfo failed", null, qos.getStateInfo());
            assertEquals("key oid failed", null, qos.getOid());
            fail("Expected exception");
         }
         catch (XmlBlasterException e) {
            System.out.println("***PublishRetQosTest: testParse SUCCESS, expected exception.");
         }
         */
      }
      catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      System.out.println("***PublishRetQosTest: testParse [SUCCESS]");
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.PublishRetQosTest
    * </pre>
    */
   public static void main(String args[])
   {
      PublishRetQosTest testSub = new PublishRetQosTest("PublishRetQosTest");
      testSub.setUp();
      testSub.testParse();
      //testSub.tearDown();
   }
}
