/*------------------------------------------------------------------------------
Name:      TestErase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.QueryQosJsonFactory;
import org.xmlBlaster.util.qos.StatusQosData;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;


public class TestQosData extends TestCase
{
   public TestQosData(String testName)
   {
      super(testName);
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
   }


   /**
    * TEST: Subscribe to a message, publish it, erase it and check if we are notified
    * about the erased message
    * <br />
    * 
    * See {@link SubscribeQos#KEY_BOUNCE_CP}
    */
   public void testClientProperty() throws Exception {
      Global glob = Global.instance();  
      StatusQosData statusQosData = new StatusQosData(glob, MethodName.SUBSCRIBE);
      statusQosData.addClientProperty("bla", "blub");
      
      
      QueryQosData queryQosData = new QueryQosData(glob, MethodName.SUBSCRIBE);
      //SubscribeQosServer sub = new SubscribeQosServer(glob, queryQosData);
      queryQosData.addClientProperty("_bounceId", "XX");
      queryQosData.addClientProperty("_bounceId:", "0"); // Ignored
      queryQosData.addClientProperty("_bounceId:oid", "1");
      queryQosData.addClientProperty("_bounceId:special", "2");
      queryQosData.addClientProperty("blabla", "blubblub");
      
      String jsonStr = queryQosData.toJson();
      QueryQosJsonFactory factory = new QueryQosJsonFactory(glob);
      queryQosData = factory.readObject(jsonStr);
      
      boolean trimPrefix = true;
      statusQosData.addClientProperties("_bounceId:", queryQosData.getClientPropertyArr(), trimPrefix);
      assertEquals("addClientProperties", 3, statusQosData.getClientPropertyArr().length);
      assertEquals("addClientProperties", "1", statusQosData.getClientProperty("oid", ""));
      assertEquals("addClientProperties", "2", statusQosData.getClientProperty("special", ""));
      assertEquals("addClientProperties", "blub", statusQosData.getClientProperty("bla", ""));

      trimPrefix = false;
      statusQosData.clearClientProperties();
      statusQosData.addClientProperties("_bounceId:", queryQosData.getClientPropertyArr(), trimPrefix);
      assertEquals("addClientProperties", 3, statusQosData.getClientPropertyArr().length);
      assertEquals("addClientProperties", "0", statusQosData.getClientProperty("_bounceId:", ""));
      assertEquals("addClientProperties", "1", statusQosData.getClientProperty("_bounceId:oid", ""));
      assertEquals("addClientProperties", "2", statusQosData.getClientProperty("_bounceId:special", ""));
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestQosData("testClientProperty"));
       return suite;
   }
   
   public static void main(String args[]) throws Exception
   {
      TestQosData testSub = new TestQosData("TestQosData");
      testSub.setUp();
      testSub.testClientProperty();
      testSub.tearDown();
   }
}

