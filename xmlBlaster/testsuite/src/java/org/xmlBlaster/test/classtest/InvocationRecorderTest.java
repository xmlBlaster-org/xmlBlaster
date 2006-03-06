package org.xmlBlaster.test.classtest;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.jutils.time.StopWatch;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.recorder.ram.RamRecorder;
import org.xmlBlaster.util.recorder.file.FileRecorder;
import org.xmlBlaster.util.recorder.I_InvocationRecorder;
import org.xmlBlaster.client.protocol.I_XmlBlaster;
import org.xmlBlaster.client.I_CallbackRaw;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.client.qos.PublishQos;

import junit.framework.*;

/**
 * Test RamRecorder. 
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.classtest.InvocationRecorderTest
 * @see org.xmlBlaster.util.recorder.ram.RamRecorder
 * @see org.xmlBlaster.util.recorder.file.FileRecorder
 */
public class InvocationRecorderTest extends TestCase {
   private String ME = "InvocationRecorderTest";
   protected ServerScope glob;
   private static Logger log = Logger.getLogger(InvocationRecorderTest.class.getName());
   private int numSubscribe, numUnSubscribe, numGet, numPublish, numPublishOneway, numPublishArr, numErase, numUpdate, numUpdateOneway;
   private StopWatch stopWatch = new StopWatch();
   private boolean testDiscardOldest = false;

   public InvocationRecorderTest(String name) {
      super(name);
   }

   protected void setUp() {
      glob = new ServerScope();

   }

   /*
   public void testMsgUnitWrapper() {
      try {
         org.xmlBlaster.engine.Global global = new org.xmlBlaster.engine.Global();
         MsgUnit msgUnit = new MsgUnit("<key oid='aaaa'/>", "Hi".getBytes(), "<qos/>");
         MsgUnitWrapper wr = new MsgUnitWrapper(global, global.getRequestBroker(),
                                                     new XmlKey(glob, msgUnit.getKey(), true),
                                                     msgUnit,
                                                     new PublishQos(glob, msgUnit.getQos())); 
      }
      catch (XmlBlasterException e) {
         fail("Exception thrown: " + e.toString());
      }
   }
   */

   public void testPlayback() {
      playback(new FileRecorder());
      playback(new RamRecorder());
   }

   private void playback(I_InvocationRecorder recorder) {
      ME = "InvocationRecorderTest.testPlayback()";
      System.out.println("***InvocationRecorderTest: testPlayback ...");
      try {
         Tester tester = new Tester();

         long maxEntries = 1000L;
         recorder.initialize(glob, "test.txt", maxEntries, tester); //, tester);

         {
            MethodName methodName = MethodName.SUBSCRIBE;
            log.info("Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               recorder.subscribe("<key oid='"+methodName+"'/>", "<qos/>");
            }
            recorder.pullback(0, 0, 0);
            log.info(methodName + " round trip performance: " + stopWatch.nice(maxEntries));
            assertEquals("Wrong number of "+methodName, maxEntries, numSubscribe);
            numSubscribe = 0;
         }

         {
            MethodName methodName = MethodName.GET;
            log.info("Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               recorder.get("<key oid='"+methodName+"'/>", "<qos/>");
            }
            recorder.pullback(0, 0, 0);
            log.info(methodName + " round trip performance: " + stopWatch.nice(maxEntries));
            assertEquals("Wrong number of "+methodName, maxEntries, numGet);
            numGet = 0;
         }

         {
            MethodName methodName = MethodName.UNSUBSCRIBE;
            log.info("Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               recorder.unSubscribe("<key oid='"+methodName+"'/>", "<qos/>");
            }
            recorder.pullback(0, 0, 0);
            log.info(methodName + " round trip performance: " + stopWatch.nice(maxEntries));
            assertEquals("Wrong number of "+methodName, maxEntries, numUnSubscribe);
            numUnSubscribe = 0;
         }

         {
            MethodName methodName = MethodName.PUBLISH;
            log.info("Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               MsgUnit msgUnit = new MsgUnit("<key oid='"+methodName+"'/>", "Ho-"+ii, "<qos/>");
               recorder.publish(msgUnit);
            }
            recorder.pullback(0, 0, 0);
            log.info(methodName + " round trip performance: " + stopWatch.nice(maxEntries));
            assertEquals("Wrong number of "+methodName, maxEntries, numPublish);
            numPublish = 0;
         }

         {
            MethodName methodName = MethodName.PUBLISH_ONEWAY;
            log.info("Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               MsgUnit[] msgs = new MsgUnit[2];
               msgs[0] = new MsgUnit("<key oid='"+methodName+"'/>", "Ha-"+ii, "<qos/>");
               msgs[1] = new MsgUnit("<key oid='"+methodName+"'/>", "Hu-"+ii, "<qos/>");
               recorder.publishOneway(msgs);
            }
            recorder.pullback(0, 0, 0);
            log.info(methodName + " round trip performance: " + stopWatch.nice(maxEntries*2));
            assertEquals("Wrong number of "+methodName, maxEntries, numPublishOneway);
            numPublishOneway = 0;
         }

         {
            MethodName methodName = MethodName.PUBLISH; // PUBLISH_ARR
            log.info("Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               MsgUnit[] msgs = new MsgUnit[2];
               msgs[0] = new MsgUnit("<key oid='"+methodName+"'/>", "Ha-"+ii, "<qos/>");
               msgs[1] = new MsgUnit("<key oid='"+methodName+"'/>", "Hu-"+ii, "<qos/>");
               recorder.publishArr(msgs);
            }
            recorder.pullback(0, 0, 0);
            log.info(methodName + " round trip performance: " + stopWatch.nice(maxEntries*2));
            assertEquals("Wrong number of "+methodName, maxEntries, numPublishArr);
            numPublishArr = 0;
         }

         {
            MethodName methodName = MethodName.ERASE;
            log.info("Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               recorder.erase("<key oid='"+methodName+"'/>", "<qos/>");
            }
            recorder.pullback(0, 0, 0);
            log.info(methodName + " round trip performance: " + stopWatch.nice(maxEntries));
            assertEquals("Wrong number of "+methodName, maxEntries, numErase);
            numErase = 0;
         }

         /*
         {
            MethodName methodName = MethodName.UPDATE;
            log.info("Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               MsgUnit[] msgs = new MsgUnit[2];
               msgs[0] = new MsgUnit("<key oid='"+methodName+"'/>", "Ha-"+ii, "<qos/>");
               msgs[1] = new MsgUnit("<key oid='"+methodName+"'/>", "Hu-"+ii, "<qos/>");
               recorder.update("dummy", msgs);
            }
            recorder.pullback(0, 0, 0);
            log.info(methodName + " round trip performance: " + stopWatch.nice(maxEntries*2));
            assertEquals("Wrong number of "+methodName, maxEntries, numUpdate);
            numUpdate = 0;
         }

         {
            MethodName methodName = MethodName.UPDATE_ONEWAY;
            log.info("Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               MsgUnit[] msgs = new MsgUnit[2];
               msgs[0] = new MsgUnit("<key oid='"+methodName+"'/>", "Ha-"+ii, "<qos/>");
               msgs[1] = new MsgUnit("<key oid='"+methodName+"'/>", "Hu-"+ii, "<qos/>");
               recorder.updateOneway("dummy", msgs);
            }
            recorder.pullback(0, 0, 0);
            log.info(methodName + " round trip performance: " + stopWatch.nice(maxEntries*2));
            assertEquals("Wrong number of "+methodName, maxEntries, numUpdateOneway);
            numUpdateOneway = 0;
         }
         */

         //assertEquals("XPath is different", xmlKey.getQueryString(), xpath);
         System.out.println("***InvocationRecorderTest: testPlayback [SUCCESS]");
      }
      catch(XmlBlasterException e) {
         fail("Exception thrown: " + e.toString());
      }
      finally {
         recorder.destroy();
      }
   }

   public void testOnOverflowException() {
      onOverflowException(new FileRecorder());
      onOverflowException(new RamRecorder());
   }

   private void onOverflowException(I_InvocationRecorder recorder) {
      ME = "InvocationRecorderTest.testOnOverflowException()";
      System.out.println("***InvocationRecorderTest: testOnOverflowException ...");
      try {
         Tester tester = new Tester();

         int maxInvoke = 4;
         int maxQueueSize = maxInvoke/2;
         recorder.initialize(glob, "testOverflow.txt", maxQueueSize, tester); //, tester);

         {
            MethodName methodName = MethodName.PUBLISH;
            log.info("Testing '" + methodName + "' ...");
            for (int ii=0; ii<maxInvoke; ii++) {
               MsgUnit msgUnit = new MsgUnit("<key oid='"+methodName+"'/>", "Ho-"+ii, "<qos/>");
               try {
                  log.info("Publish ii=" + ii);
                  recorder.publish(msgUnit);
                  if (ii >= maxQueueSize)
                     fail(ME + " Expected exception because of full queue ii=" + ii);
               }
               catch (XmlBlasterException e) {
                  if (ii >= maxQueueSize && e.getErrorCode() == ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES) {
                     log.info("OK, expected exception ii=" + ii);
                  }
                  else {
                     fail(ME + " ii=" + ii + " : " + e.toString());
                  }
               }
            }
            recorder.pullback(0, 0, 0);
            assertEquals("Wrong number of "+methodName, maxQueueSize, numPublish);
            numPublish = 0;
         }

         {
            MethodName methodName = MethodName.PUBLISH; // PUBLISH_ARR;
            log.info("Testing '" + methodName + "' ...");
            for (int ii=0; ii<maxInvoke; ii++) {
               MsgUnit[] msgs = new MsgUnit[2];
               msgs[0] = new MsgUnit("<key oid='"+methodName+"'/>", "Ha-"+ii, "<qos/>");
               msgs[1] = new MsgUnit("<key oid='"+methodName+"'/>", "Hu-"+ii, "<qos/>");
               try {
                  recorder.publishArr(msgs);
                  if (ii >= maxQueueSize)
                     fail(ME + " Expected exception because of full queue ii=" + ii);
               }
               catch (XmlBlasterException e) {
                  if (ii >= maxQueueSize && e.getErrorCode() == ErrorCode.RESOURCE_OVERFLOW_QUEUE_ENTRIES) {
                     log.info("OK, expected exception ii=" + ii);
                  }
                  else {
                     fail(ME + " ii=" + ii + " : " + e.toString());
                  }
               }
            }
            recorder.pullback(0, 0, 0);
            assertEquals("Wrong number of "+methodName, maxQueueSize, numPublishArr);
            numPublishArr = 0;
         }

         System.out.println("***InvocationRecorderTest: testOnOverflowException [SUCCESS]");
      }
      catch(XmlBlasterException e) {
         fail("Exception thrown: " + e.toString());
      }
      finally {
         recorder.destroy();
      }
   }

   public void testOnOverflowDiscardOldest() {
      onOverflowDiscardOldest(new FileRecorder());
      onOverflowDiscardOldest(new RamRecorder());
   }

   private void onOverflowDiscardOldest(I_InvocationRecorder recorder) {
      ME = "InvocationRecorderTest.testOnOverflowDiscardOldest()";
      System.out.println("***InvocationRecorderTest: testOnOverflowDiscardOldest ...");

      testDiscardOldest = true;

      try {
         Tester tester = new Tester();

         int maxInvoke = 4;
         int maxQueueSize = maxInvoke/2;
         recorder.initialize(glob, (String)null, maxQueueSize, tester); //, tester);
         recorder.setMode(Constants.ONOVERFLOW_DISCARDOLDEST);

         {
            MethodName methodName = MethodName.PUBLISH;
            log.info("Testing '" + methodName + "' ...");
            for (int ii=0; ii<maxInvoke; ii++) {
               MsgUnit msgUnit = new MsgUnit("<key oid='"+methodName+"'/>", "Ho-"+ii, "<qos/>");
               try {
                  recorder.publish(msgUnit);
               }
               catch (XmlBlasterException e) {
                  fail(ME + " ii=" + ii + " : " + e.toString());
               }
            }
            assertEquals("Wrong number of lost messages in "+methodName, maxInvoke-maxQueueSize, recorder.getNumLost());
            recorder.pullback(0, 0, 0);
            assertEquals("Wrong number of "+methodName, maxQueueSize, numPublish);
            numPublish = 0;
         }

         System.out.println("***InvocationRecorderTest: testOnOverflowDiscardOldest [SUCCESS]");
      }
      catch(XmlBlasterException e) {
         fail("Exception thrown: " + e.toString());
      }
      finally {
         recorder.destroy();
      }
   }

   public void testOnOverflowDiscard() {
      onOverflowDiscard(new FileRecorder());
      onOverflowDiscard(new RamRecorder());
   }

   private void onOverflowDiscard(I_InvocationRecorder recorder) {
      ME = "InvocationRecorderTest.testOnOverflowDiscard()";
      System.out.println("***InvocationRecorderTest: testOnOverflowDiscard ...");

      try {
         Tester tester = new Tester();

         int maxInvoke = 4;
         int maxQueueSize = maxInvoke/2;
         recorder.initialize(glob, (String)null, maxQueueSize, tester); //, tester);
         recorder.setMode(Constants.ONOVERFLOW_DISCARD);

         {
            MethodName methodName = MethodName.PUBLISH;
            log.info("Testing '" + methodName + "' ...");
            for (int ii=0; ii<maxInvoke; ii++) {
               MsgUnit msgUnit = new MsgUnit("<key oid='"+methodName+"'/>", "Ho-"+ii, "<qos/>");
               try {
                  recorder.publish(msgUnit);
               }
               catch (XmlBlasterException e) {
                  fail(ME + " ii=" + ii + " : " + e.toString());
               }
            }
            assertEquals("Wrong number of lost messages in "+methodName, maxInvoke-maxQueueSize, recorder.getNumLost());
            recorder.pullback(0, 0, 0);
            assertEquals("Wrong number of "+methodName, maxQueueSize, numPublish);
            numPublish = 0;
         }

         System.out.println("***InvocationRecorderTest: testOnOverflowDiscard [SUCCESS]");
      }
      catch(XmlBlasterException e) {
         fail("Exception thrown: " + e.toString());
      }
      finally {
         recorder.destroy();
      }
   }

   /**
    * This plays the role of a user of the recoder
    */
   class Tester implements I_XmlBlaster//, I_CallbackRaw
   {
      public SubscribeReturnQos subscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException {
         assertEquals("Wrong message key", "<key oid='subscribe'/>", xmlKey);
         assertEquals("Wrong message qos", "<qos/>", qos);
         numSubscribe++;
         return null;
      }
      
      public org.xmlBlaster.util.MsgUnit[] get(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException {
         assertEquals("Wrong message key", "<key oid='get'/>", xmlKey);
         assertEquals("Wrong message qos", "<qos/>", qos);
         numGet++;
         return new org.xmlBlaster.util.MsgUnit[0];
      }
      
      public UnSubscribeReturnQos[] unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException {
         assertEquals("Wrong message key", "<key oid='unSubscribe'/>", xmlKey);
         assertEquals("Wrong message qos", "<qos/>", qos);
         numUnSubscribe++;
         return null;
      }
      
      public PublishReturnQos publish(org.xmlBlaster.util.MsgUnit msgUnit) throws XmlBlasterException {
         //log.severe("Received '" + msgUnit.getKey().trim() + "' from\n" + msgUnit.toXml() );
         assertEquals("Wrong message key", "<key oid='publish'/>", msgUnit.getKey().trim());
         if (testDiscardOldest)
            assertEquals("Wrong message content", "Ho-"+(numPublish+2), msgUnit.getContentStr());
         else
            assertEquals("Wrong message content", "Ho-"+numPublish, msgUnit.getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnit.getQos());
         numPublish++;
         return null;
      }
      
      public void publishOneway(org.xmlBlaster.util.MsgUnit[] msgUnitArr) {
         assertEquals("Wrong message array length", 2, msgUnitArr.length);

         assertEquals("Wrong message key", "<key oid='publishOneway'/>", msgUnitArr[0].getKey().trim());
         assertEquals("Wrong message content", "Ha-"+numPublishOneway, msgUnitArr[0].getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnitArr[0].getQos());

         assertEquals("Wrong message key", "<key oid='publishOneway'/>", msgUnitArr[1].getKey().trim());
         assertEquals("Wrong message content", "Hu-"+numPublishOneway, msgUnitArr[1].getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnitArr[1].getQos());

         numPublishOneway++;
      }

      public PublishReturnQos[] publishArr(org.xmlBlaster.util.MsgUnit[] msgUnitArr) throws XmlBlasterException {
         assertEquals("Wrong message array length", 2, msgUnitArr.length);

         //log.severe("Received '" + msgUnitArr[0].getKey().trim() + "' from\n" + msgUnitArr[0].toXml() );
         assertEquals("Wrong message key", "<key oid='"+ MethodName.PUBLISH + "'/>", msgUnitArr[0].getKey().trim()); // PUBLISH_ARR
         assertEquals("Wrong message content", "Ha-"+numPublishArr, msgUnitArr[0].getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnitArr[0].getQos());

         assertEquals("Wrong message key", "<key oid='"+ MethodName.PUBLISH + "'/>", msgUnitArr[1].getKey().trim());  // PUBLISH_ARR
         assertEquals("Wrong message content", "Hu-"+numPublishArr, msgUnitArr[1].getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnitArr[1].getQos());

         numPublishArr++;
         return new PublishReturnQos[0];
      }
      
      public EraseReturnQos[] erase(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException {
         assertEquals("Wrong message key", "<key oid='erase'/>", xmlKey);
         assertEquals("Wrong message qos", "<qos/>", qos);
 
         numErase++;
         return new EraseReturnQos[0];
      }

      /*
      public String[] update(String cbSessionId, org.xmlBlaster.util.MsgUnit[] msgUnitArr) throws XmlBlasterException {
         assertEquals("Wrong message array length", 2, msgUnitArr.length);
         assertEquals("Wrong cbSessionId", "dummy", cbSessionId);

         assertEquals("Wrong message key", "<key oid='update'/>", msgUnitArr[0].getKey().trim());
         assertEquals("Wrong message content", "Ha-"+numUpdate, msgUnitArr[0].getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnitArr[0].getQos());

         assertEquals("Wrong message key", "<key oid='update'/>", msgUnitArr[1].getKey().trim());
         assertEquals("Wrong message content", "Hu-"+numUpdate, msgUnitArr[1].getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnitArr[1].getQos());

         numUpdate++;
         return new String[0];
      }
      
      public void updateOneway(String cbSessionId, org.xmlBlaster.util.MsgUnit[] msgUnitArr) {
         assertEquals("Wrong message array length", 2, msgUnitArr.length);
         assertEquals("Wrong cbSessionId", "dummy", cbSessionId);

         assertEquals("Wrong message key", "<key oid='updateOneway'/>", msgUnitArr[0].getKey().trim());
         assertEquals("Wrong message content", "Ha-"+numUpdateOneway, msgUnitArr[0].getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnitArr[0].getQos());

         assertEquals("Wrong message key", "<key oid='updateOneway'/>", msgUnitArr[1].getKey().trim());
         assertEquals("Wrong message content", "Hu-"+numUpdateOneway, msgUnitArr[1].getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnitArr[1].getQos());

         numUpdateOneway++;
      }
      */
   }

   protected void tearDown() {
   }

   /**
    * For debugging, invoke: 
    * <pre>
    *  java org.xmlBlaster.test.classtest.InvocationRecorderTest -trace[dispatch] true -call[core] true
    *  java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.InvocationRecorderTest
    * <pre>
    */
   public static void main(String args[]) {
      ServerScope glob = new ServerScope();
      if (glob.init(args) != 0) {
         System.exit(0);
      }
      InvocationRecorderTest testSub = new InvocationRecorderTest("InvocationRecorderTest");
      testSub.setUp();
      testSub.onOverflowDiscard(new FileRecorder());
      testSub.tearDown();
   }
}
