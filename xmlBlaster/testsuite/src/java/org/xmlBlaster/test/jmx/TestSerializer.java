package org.xmlBlaster.test.jmx;

import junit.framework.*;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

import org.xmlBlaster.util.admin.extern.SerializeHelper;
import org.xmlBlaster.util.admin.extern.MethodInvocation;
import java.io.*;

public class TestSerializer  extends TestCase{
  private final static String ME = "TestSerializer";
  private Global glob = null;
  private LogChannel log = null;

  SerializeHelper sh = null;
  MethodInvocation mi = null;

  public TestSerializer(String testName)
   {
       super(testName);
   }


   protected void setUp()
   {
     this.glob = new Global().instance();
     this.log = this.glob.getLog("test");
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestSerializer("testSerializer"));
       return suite;
   }

   /**
    * TEST:
    * <p />
    */
   public void testSerializer()
   {
    try {
      sh = new SerializeHelper(glob);
    }
    catch (Exception ex) {
      log.error(ME, "Error when creating SerializeHelper" + ex.toString());
      assertTrue("Error when creating SerializeHelper" + ex.toString(),false);
    }
    mi = new MethodInvocation();
    try {
      mi.setMethodName("Test");
       log.info(ME,"Serializing MethodInvocation");
       byte[] testByte = sh.serializeObject(mi);
       log.info(ME,"Deserializing MethodInvocation...");
       MethodInvocation mi2 = (MethodInvocation) sh.deserializeObject(testByte);
       if (!(mi2.getMethodName().equals("Test"))) {
         log.error(ME,"Values are different");
         assertTrue("Values are different", false);
       }
    }
    catch (Exception ex) {
      assertTrue("Error when serializing or deserialiizing Object " + ex.toString(), false);
      log.error(ME,"Error when serializing or deserialiizing Object " + ex.toString());
    }
   }

   public static void main(String args[])
   {
     TestSerializer testSer = new TestSerializer("SerializerTest");
     testSer.setUp();
     testSer.testSerializer();
   }

}