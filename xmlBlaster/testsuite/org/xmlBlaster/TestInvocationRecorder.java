/*------------------------------------------------------------------------------
Name:      TestInvocationRecorder.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the InvocationRecorder
Version:   $Id: TestInvocationRecorder.java,v 1.5 2000/02/29 16:54:20 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.client.*;
import org.xmlBlaster.util.*;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;
import test.framework.*;


/**
 * This client tests the InvocationRecorder.
 * <p />
 * This test invokes every method, and compares the values of the playback
 * messages with their originals.
 * This test needs no running xmlBlaster.
 * <br />
 * Invoke examples:<br />
 * <pre>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestInvocationRecorder
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestInvocationRecorder
 * </pre>
 */
public class TestInvocationRecorder extends TestCase implements ServerOperations, BlasterCallbackOperations
{
   private Server xmlBlaster = null;
   private static String ME = "TestInvocationRecorder";

   private InvocationRecorder recorder = null;

   private String subscribeOid;
   private String publishOid = "";

   private int numSubscribe, numUnSubscribe, numPublish, numPublishArr, numErase, numGet, numSetClientAttributes, numUpdate;
   private MessageUnitContainer[] dummyMArr = new MessageUnitContainer[0];
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
   public TestInvocationRecorder(String testName)
   {
       super(testName);
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      Log.info(ME, "setup test");
      numSubscribe = numUnSubscribe = numPublish = numPublishArr = numErase = numGet = numSetClientAttributes = numUpdate = 0;
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
      String[] qosArr = new String[1];
      qosArr[0] = qos;
      String content = "The content";
      MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes());
      MessageUnit[] msgUnitArr = new MessageUnit[1];
      msgUnitArr[0] = msgUnit;
      String clientName = "Gonzales";
      String xmlAttr = "";

      try {
         recorder.subscribe(xmlKey_subscribe, qos_subscribe);
         recorder.get(xmlKey_get, qos_get);
         recorder.unSubscribe(xmlKey, qos);
         recorder.publish(msgUnit, qos);
         recorder.publishArr(msgUnitArr, qosArr);
         recorder.erase(xmlKey, qos);
         recorder.setClientAttributes(clientName, xmlAttr, qos);
         recorder.update(msgUnitArr, qosArr);
      }
      catch(XmlBlasterException e) {
         Log.error(ME, "problems feeding the recorder: " + e.reason);
         assert("problems feeding the recorder: " + e.reason, false);
      }

      try {
         recorder.pullback(0L, 0L, (float)1.0);
      }
      catch(XmlBlasterException e) {
         Log.error(ME, "problems with recorder.pullback: " + e.reason);
         assert("problems recorder pullback: " + e.reason, false);
      }

      assertEquals("numSubscribe: ", 1, numSubscribe);
      assertEquals("numUnSubscribe: ", 1, numUnSubscribe);
      assertEquals("numPublish: ", 1, numPublish);
      assertEquals("numPublishArr: ", 1, numPublishArr);
      assertEquals("numErase: ", 1, numErase);
      assertEquals("numGet: ", 1, numGet);
      assertEquals("numSetClientAttributes: ", 1, numSetClientAttributes);
      assertEquals("numUpdate: ", 1, numUpdate);
   }


   /**
    * @return dummy to match ServerOperations interface
    * @see xmlBlaster.idl
    */
   public String subscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "subscribe() ...");
      numSubscribe++;
      assertEquals("subscribe(xmlKey): ", xmlKey_subscribe, xmlKey_literal);
      assertEquals("subscribe(xmlKey): ", qos_subscribe, qos_literal);
      return dummyS;
   }


   /**
    * For ServerOperations interface
    * @see xmlBlaster.idl
    */
   public void unSubscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "unSubscribe() ...");
      numUnSubscribe++;
   }


   /**
    * @return dummy to match ServerOperations interface
    * @see xmlBlaster.idl
    */
   public String publish(MessageUnit msgUnit, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "publish() ...");
      numPublish++;
      return dummyS;
   }


   /**
    * @return dummy to match ServerOperations interface
    * @see xmlBlaster.idl
    */
   public String[] publishArr(MessageUnit [] msgUnitArr, String [] qos_literal_Arr) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "publishArr() ...");
      numPublishArr++;
      return dummySArr;
   }


   /**
    * @return dummy to match ServerOperations interface
    * @see xmlBlaster.idl
    */
   public String[] erase(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "erase() ...");
      numErase++;
      return dummySArr;
   }


   /**
    * @return dummy to match ServerOperations interface
    * @see xmlBlaster.idl
    */
   public MessageUnitContainer[] get(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "get() ...");
      numGet++;
      assertEquals("get(xmlKey): ", xmlKey_get, xmlKey_literal);
      assertEquals("get(xmlKey): ", qos_get, qos_literal);
      return dummyMArr;
   }


   /**
    * For ServerOperations interface
    * @see xmlBlaster.idl
    */
   public void setClientAttributes(String clientName, String xmlAttr_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "setClientAttributes() ...");
      numSetClientAttributes++;
   }


   /**
    * Enforced by ServerOperations interface
    * @see xmlBlaster.idl
    */
   public void ping() {}


   /**
    * This is the callback method (I_Callback) invoked from CorbaConnection
    * informing the client in an asynchronous mode about a new message.
    * <p />
    * The raw CORBA-BlasterCallback.update() is unpacked and for each arrived message
    * this update is called.
    *
    * @param loginName The name to whom the callback belongs
    * @param updateKey The arrived key
    * @param content   The arrived message content
    * @param qos       Quality of Service of the MessageUnit
    */
   public void update(org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] msgUnitArr, java.lang.String[] qosArr)
   {
      if (Log.CALLS) Log.calls(ME, "update() ...");
      numUpdate++;
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestInvocationRecorder("test"));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestInvocationRecorder +calls
    */
   public static void main(String args[])
   {
      Log.setLogLevel(args);
      TestInvocationRecorder testSub = new TestInvocationRecorder("test");
      testSub.setUp();
      testSub.test();
      testSub.tearDown();
      Log.exit(TestInvocationRecorder.ME, "Good bye");
   }
}

