/*------------------------------------------------------------------------------
Name:      TestInvocationRecorder.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the InvocationRecorder
Version:   $Id: TestInvocationRecorder.java,v 1.17 2002/05/11 10:07:54 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.InvocationRecorder;
import org.xmlBlaster.util.I_InvocationRecorder;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.client.I_CallbackRaw;
import org.xmlBlaster.engine.helper.MessageUnit;

import junit.framework.*;


/**
 * This client tests the InvocationRecorder.
 * <p />
 * This test invokes every method, and compares the values of the playback
 * messages with their originals.
 * This test needs no running xmlBlaster.
 * <br />
 * Invoke examples:<br />
 * <pre>
 *    java test.textui.TestRunner testsuite.org.xmlBlaster.TestInvocationRecorder
 *    java test.ui.TestRunner testsuite.org.xmlBlaster.TestInvocationRecorder
 * </pre>
 */
public class TestInvocationRecorder extends TestCase implements I_InvocationRecorder, I_CallbackRaw
{
   private static String ME = "TestInvocationRecorder";
   private final Global glob;

   private InvocationRecorder recorder = null;

   private String subscribeOid;
   private String publishOid = "";

   private int numSubscribe, numUnSubscribe, numPublish, numPublishArr, numErase, numGet, numUpdate;
   private MessageUnit[] dummyMArr = new MessageUnit[0];
   private String[] dummySArr = new String[0];
   private String dummyS = "";

   private String xmlKey_get = "<key oid='HelloGet' queryType='EXACT'>\n</key>";
   private String qos_get = "<qos><get /></qos>";

   private String xmlKey_subscribe = "<key oid='HelloSubscribe' queryType='EXACT'>\n</key>";
   private String qos_subscribe = "<qos><subscribe /></qos>";

   /**
    * Constructs the TestInvocationRecorder object.
    * <p />
    * @param testName  The name used in the test suite
    */
   public TestInvocationRecorder(Global glob, String testName)
   {
       super(testName);
       this.glob = glob;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      Log.info(ME, "setup test");
      numSubscribe = numUnSubscribe = numPublish = numPublishArr = numErase = numGet = numUpdate = 0;
      recorder = new InvocationRecorder(1000, this, this);
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      Log.info(ME, "testing done");
   }


   /**
    * This test invokes every method, and compares the values of the playback
    * messages with their originals
    */
   public void test()
   {
      String xmlKey = "<key oid='Hello' queryType='XPATH'>\n   //TestInvocationRecorder-AGENT\n</key>";
      String qos = "<qos></qos>";
      String content = "The content";
      MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), qos);
      MessageUnit[] msgUnitArr = new MessageUnit[1];
      msgUnitArr[0] = msgUnit;
      String clientName = "Gonzales";
      String xmlAttr = "";

      try {
         recorder.subscribe(xmlKey_subscribe, qos_subscribe);
         recorder.get(xmlKey_get, qos_get);
         recorder.unSubscribe(xmlKey, qos);
         recorder.publish(msgUnit);
         recorder.publishArr(msgUnitArr);
         recorder.erase(xmlKey, qos);
         recorder.update(clientName, msgUnitArr);
      }
      catch(XmlBlasterException e) {
         Log.error(ME, "problems feeding the recorder: " + e.reason);
         assertTrue("problems feeding the recorder: " + e.reason, false);
      }

      try {
         recorder.pullback(0L, 0L, (float)1.0);
      }
      catch(XmlBlasterException e) {
         Log.error(ME, "problems with recorder.pullback: " + e.reason);
         assertTrue("problems recorder pullback: " + e.reason, false);
      }

      assertEquals("numSubscribe: ", 1, numSubscribe);
      assertEquals("numUnSubscribe: ", 1, numUnSubscribe);
      assertEquals("numPublish: ", 1, numPublish);
      assertEquals("numPublishArr: ", 1, numPublishArr);
      assertEquals("numErase: ", 1, numErase);
      assertEquals("numGet: ", 1, numGet);
      assertEquals("numUpdate: ", 1, numUpdate);
   }


   /**
    * @return dummy to match I_InvocationRecorder interface
    * @see xmlBlaster.idl
    */
   public String subscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "subscribe() ...");
      numSubscribe++;
      assertEquals("subscribe(xmlKey): ", xmlKey_subscribe, xmlKey_literal);
      assertEquals("subscribe(xmlKey): ", qos_subscribe, qos_literal);
      return dummyS;
   }


   /**
    * For I_InvocationRecorder interface
    * @see xmlBlaster.idl
    */
   public void unSubscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "unSubscribe() ...");
      numUnSubscribe++;
   }


   /**
    * @return dummy to match I_InvocationRecorder interface
    * @see xmlBlaster.idl
    */
   public String publish(MessageUnit msgUnit) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "publish() ...");
      numPublish++;
      return dummyS;
   }


   /**
    * @return dummy to match I_InvocationRecorder interface
    * @see xmlBlaster.idl
    */
   public String[] publishArr(MessageUnit [] msgUnitArr) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "publishArr() ...");
      numPublishArr++;
      return dummySArr;
   }


   /**
    * @return dummy to match I_InvocationRecorder interface
    * @see xmlBlaster.idl
    */
   public String[] erase(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "erase() ...");
      numErase++;
      return dummySArr;
   }


   /**
    * @return dummy to match I_InvocationRecorder interface
    * @see xmlBlaster.idl
    */
   public MessageUnit[] get(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "get() ...");
      numGet++;
      assertEquals("get(xmlKey): ", xmlKey_get, xmlKey_literal);
      assertEquals("get(xmlKey): ", qos_get, qos_literal);
      return dummyMArr;
   }


   /**
    * This is the callback method enforced by interface I_CallbackRaw.
    * <p />
    * @param MessageUnit Container for the Message
    */
   public String[] update(String cbSessionId, org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr)
   {
      if (Log.CALL) Log.call(ME, "update(" + cbSessionId + ") ...");
      numUpdate++;
      String[] retArr = new String[msgUnitArr.length];
      for (int ii=0; ii<retArr.length; ii++) retArr[ii] = "";
      return retArr;
   }

   public void updateOneway(String cbSessionId, org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr)
   {
      if (Log.CALL) Log.call(ME, "update(" + cbSessionId + ") ...");
      numUpdate++;
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestInvocationRecorder(new Global(), "test"));
       return suite;
   }


   /**
    * Invoke: java testsuite.org.xmlBlaster.TestInvocationRecorder -calls true
    */
   public static void main(String args[])
   {
      TestInvocationRecorder testSub = new TestInvocationRecorder(new Global(args), "test");
      testSub.setUp();
      testSub.test();
      testSub.tearDown();
      Log.exit(TestInvocationRecorder.ME, "Good bye");
   }
}

