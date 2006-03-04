/*------------------------------------------------------------------------------
Name:      TestInvocationRecorder.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the InvocationRecorder
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.recorder.ram.RamRecorder;
import org.xmlBlaster.util.recorder.file.FileRecorder;
import org.xmlBlaster.util.recorder.I_InvocationRecorder;
import org.xmlBlaster.client.protocol.I_XmlBlaster;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.I_CallbackRaw;
import org.xmlBlaster.util.MsgUnit;

import junit.framework.*;


/**
 * This client tests the RamRecorder.
 * <p />
 * This test invokes every method, and compares the values of the playback
 * messages with their originals.
 * This test needs no running xmlBlaster.
 * <br />
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestInvocationRecorder
 *    java junit.swingui.TestRunner org.xmlBlaster.test.qos.TestInvocationRecorder
 * </pre>
 */
public class TestInvocationRecorder extends TestCase implements I_XmlBlaster//, I_CallbackRaw
{
   private static String ME = "TestInvocationRecorder";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestInvocationRecorder.class.getName());

   private RamRecorder recorder = null;

   private String subscribeOid;
   private String publishOid = "";

   private int numSubscribe, numUnSubscribe, numPublish, numPublishArr, numErase, numGet, numUpdate;
   private MsgUnit[] dummyMArr = new MsgUnit[0];
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
      log.info("setup test");
      numSubscribe = numUnSubscribe = numPublish = numPublishArr = numErase = numGet = numUpdate = 0;
      recorder = new RamRecorder();
      recorder.initialize(glob, (String)null, 1000, this);//, this);
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      log.info("testing done");
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
      String clientName = "Gonzales";
      String xmlAttr = "";

      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, content.getBytes(), qos);
         MsgUnit[] msgUnitArr = { msgUnit };
         recorder.subscribe(xmlKey_subscribe, qos_subscribe);
         recorder.get(xmlKey_get, qos_get);
         recorder.unSubscribe(xmlKey, qos);
         recorder.publish(msgUnit);
         recorder.publishArr(msgUnitArr);
         recorder.erase(xmlKey, qos);
         //recorder.update(clientName, msgUnitArr);
      }
      catch(XmlBlasterException e) {
         log.severe("problems feeding the recorder: " + e.getMessage());
         assertTrue("problems feeding the recorder: " + e.getMessage(), false);
      }

      try {
         recorder.pullback(0L, 0L, (float)1.0);
      }
      catch(XmlBlasterException e) {
         log.severe("problems with recorder.pullback: " + e.getMessage());
         assertTrue("problems recorder pullback: " + e.getMessage(), false);
      }

      assertEquals("numSubscribe: ", 1, numSubscribe);
      assertEquals("numUnSubscribe: ", 1, numUnSubscribe);
      assertEquals("numPublish: ", 1, numPublish);
      assertEquals("numPublishArr: ", 1, numPublishArr);
      assertEquals("numErase: ", 1, numErase);
      assertEquals("numGet: ", 1, numGet);
      //assertEquals("numUpdate: ", 1, numUpdate);
   }


   /**
    * @return dummy to match I_InvocationRecorder interface
    */
   public SubscribeReturnQos subscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("subscribe() ...");
      numSubscribe++;
      assertEquals("subscribe(xmlKey): ", xmlKey_subscribe, xmlKey_literal);
      assertEquals("subscribe(xmlKey): ", qos_subscribe, qos_literal);
      return null;
   }


   /**
    * For I_InvocationRecorder interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public UnSubscribeReturnQos[] unSubscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("unSubscribe() ...");
      numUnSubscribe++;
      return null;
   }


   /**
    * @return dummy to match I_InvocationRecorder interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public PublishReturnQos publish(MsgUnit msgUnit) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("publish() ...");
      numPublish++;
      return null;
   }


   /**
    * @return dummy to match I_InvocationRecorder interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public void publishOneway(MsgUnit [] msgUnitArr)
   {
      if (log.isLoggable(Level.FINER)) log.finer("publishOneway() ...");
      numPublishArr++;
   }


   /**
    * @return dummy to match I_InvocationRecorder interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public PublishReturnQos[] publishArr(MsgUnit [] msgUnitArr) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("publishArr() ...");
      numPublishArr++;
      return new PublishReturnQos[0];
   }


   /**
    * @return dummy to match I_InvocationRecorder interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public EraseReturnQos[] erase(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("erase() ...");
      numErase++;
      return new EraseReturnQos[0];
   }


   /**
    * @return dummy to match I_InvocationRecorder interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public MsgUnit[] get(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("get() ...");
      numGet++;
      assertEquals("get(xmlKey): ", xmlKey_get, xmlKey_literal);
      assertEquals("get(xmlKey): ", qos_get, qos_literal);
      return dummyMArr;
   }


   /**
    * This is the callback method enforced by interface I_CallbackRaw.
    * <p />
    * @param MsgUnit Container for the Message
   public String[] update(String cbSessionId, org.xmlBlaster.util.MsgUnit[] msgUnitArr)
   {
      if (log.isLoggable(Level.FINER)) log.finer("update(" + cbSessionId + ") ...");
      numUpdate++;
      String[] retArr = new String[msgUnitArr.length];
      for (int ii=0; ii<retArr.length; ii++) retArr[ii] = "";
      return retArr;
   }

   public void updateOneway(String cbSessionId, org.xmlBlaster.util.MsgUnit[] msgUnitArr)
   {
      if (log.isLoggable(Level.FINER)) log.finer("update(" + cbSessionId + ") ...");
      numUpdate++;
   }
    */

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
    * Invoke: java org.xmlBlaster.test.qos.TestInvocationRecorder -calls true
    */
   public static void main(String args[])
   {
      TestInvocationRecorder testSub = new TestInvocationRecorder(new Global(args), "test");
      testSub.setUp();
      testSub.test();
      testSub.tearDown();
   }
}

