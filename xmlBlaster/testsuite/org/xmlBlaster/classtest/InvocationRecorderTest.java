package classtest;

import org.jutils.log.LogChannel;
import org.jutils.time.StopWatch;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.recorder.ram.RamRecorder;
import org.xmlBlaster.util.recorder.file.FileRecorder;
import org.xmlBlaster.util.recorder.I_InvocationRecorder;
import org.xmlBlaster.client.protocol.I_XmlBlaster;
import org.xmlBlaster.client.I_CallbackRaw;
import org.xmlBlaster.client.PublishRetQos;
import org.xmlBlaster.client.SubscribeRetQos;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.MessageUnit;

import junit.framework.*;

/**
 * Test RamRecorder. 
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner classtest.InvocationRecorderTest
 * @see org.xmlBlaster.util.recorder.ram.RamRecorder
 * @see org.xmlBlaster.util.recorder.file.FileRecorder
 */
public class InvocationRecorderTest extends TestCase {
   private String ME = "InvocationRecorderTest";
   protected Global glob;
   protected LogChannel log;
   private int numSubscribe, numUnSubscribe, numGet, numPublish, numPublishOneway, numPublishArr, numErase, numUpdate, numUpdateOneway;
   private StopWatch stopWatch = new StopWatch();
   private boolean testDiscardOldest = false;

   public InvocationRecorderTest(String name) {
      super(name);
   }

   protected void setUp() {
      glob = new Global();
      log = glob.getLog(null);
   }

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
         recorder.initialize(glob, "test.txt", maxEntries, tester, tester);

         {
            String methodName = "subscribe";
            log.info(ME, "Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               recorder.subscribe("<key oid='"+methodName+"'/>", "<qos/>");
            }
            recorder.pullback(0, 0, 0);
            log.info(ME, methodName + " round trip performance: " + stopWatch.nice(maxEntries));
            assertEquals("Wrong number of "+methodName, maxEntries, numSubscribe);
            numSubscribe = 0;
         }

         {
            String methodName = "get";
            log.info(ME, "Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               recorder.get("<key oid='"+methodName+"'/>", "<qos/>");
            }
            recorder.pullback(0, 0, 0);
            log.info(ME, methodName + " round trip performance: " + stopWatch.nice(maxEntries));
            assertEquals("Wrong number of "+methodName, maxEntries, numGet);
            numGet = 0;
         }

         {
            String methodName = "unSubscribe";
            log.info(ME, "Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               recorder.unSubscribe("<key oid='"+methodName+"'/>", "<qos/>");
            }
            recorder.pullback(0, 0, 0);
            log.info(ME, methodName + " round trip performance: " + stopWatch.nice(maxEntries));
            assertEquals("Wrong number of "+methodName, maxEntries, numUnSubscribe);
            numUnSubscribe = 0;
         }

         {
            String methodName = "publish";
            log.info(ME, "Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               MessageUnit msgUnit = new MessageUnit("<key oid='"+methodName+"'/>", "Ho-"+ii, "<qos/>");
               recorder.publish(msgUnit);
            }
            recorder.pullback(0, 0, 0);
            log.info(ME, methodName + " round trip performance: " + stopWatch.nice(maxEntries));
            assertEquals("Wrong number of "+methodName, maxEntries, numPublish);
            numPublish = 0;
         }

         {
            String methodName = "publishOneway";
            log.info(ME, "Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               MessageUnit[] msgs = new MessageUnit[2];
               msgs[0] = new MessageUnit("<key oid='"+methodName+"'/>", "Ha-"+ii, "<qos/>");
               msgs[1] = new MessageUnit("<key oid='"+methodName+"'/>", "Hu-"+ii, "<qos/>");
               recorder.publishOneway(msgs);
            }
            recorder.pullback(0, 0, 0);
            log.info(ME, methodName + " round trip performance: " + stopWatch.nice(maxEntries*2));
            assertEquals("Wrong number of "+methodName, maxEntries, numPublishOneway);
            numPublishOneway = 0;
         }

         {
            String methodName = "publishArr";
            log.info(ME, "Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               MessageUnit[] msgs = new MessageUnit[2];
               msgs[0] = new MessageUnit("<key oid='"+methodName+"'/>", "Ha-"+ii, "<qos/>");
               msgs[1] = new MessageUnit("<key oid='"+methodName+"'/>", "Hu-"+ii, "<qos/>");
               recorder.publishArr(msgs);
            }
            recorder.pullback(0, 0, 0);
            log.info(ME, methodName + " round trip performance: " + stopWatch.nice(maxEntries*2));
            assertEquals("Wrong number of "+methodName, maxEntries, numPublishArr);
            numPublishArr = 0;
         }

         {
            String methodName = "erase";
            log.info(ME, "Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               recorder.erase("<key oid='"+methodName+"'/>", "<qos/>");
            }
            recorder.pullback(0, 0, 0);
            log.info(ME, methodName + " round trip performance: " + stopWatch.nice(maxEntries));
            assertEquals("Wrong number of "+methodName, maxEntries, numErase);
            numErase = 0;
         }

         {
            String methodName = "update";
            log.info(ME, "Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               MessageUnit[] msgs = new MessageUnit[2];
               msgs[0] = new MessageUnit("<key oid='"+methodName+"'/>", "Ha-"+ii, "<qos/>");
               msgs[1] = new MessageUnit("<key oid='"+methodName+"'/>", "Hu-"+ii, "<qos/>");
               recorder.update("dummy", msgs);
            }
            recorder.pullback(0, 0, 0);
            log.info(ME, methodName + " round trip performance: " + stopWatch.nice(maxEntries*2));
            assertEquals("Wrong number of "+methodName, maxEntries, numUpdate);
            numUpdate = 0;
         }

         {
            String methodName = "updateOneway";
            log.info(ME, "Testing '" + methodName + "' ...");
            stopWatch = new StopWatch();
            for (int ii=0; ii<maxEntries; ii++) {
               MessageUnit[] msgs = new MessageUnit[2];
               msgs[0] = new MessageUnit("<key oid='"+methodName+"'/>", "Ha-"+ii, "<qos/>");
               msgs[1] = new MessageUnit("<key oid='"+methodName+"'/>", "Hu-"+ii, "<qos/>");
               recorder.updateOneway("dummy", msgs);
            }
            recorder.pullback(0, 0, 0);
            log.info(ME, methodName + " round trip performance: " + stopWatch.nice(maxEntries*2));
            assertEquals("Wrong number of "+methodName, maxEntries, numUpdateOneway);
            numUpdateOneway = 0;
         }

         //assertEquals("XPath is different", xmlKey.getQueryString(), xpath);
         System.out.println("***InvocationRecorderTest: testPlayback [SUCCESS]");
      }
      catch(XmlBlasterException e) {
         fail("Exception thrown: " + e.toString());
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
         recorder.initialize(glob, "testOverflow.txt", maxQueueSize, tester, tester);

         {
            String methodName = "publish";
            log.info(ME, "Testing '" + methodName + "' ...");
            for (int ii=0; ii<maxInvoke; ii++) {
               MessageUnit msgUnit = new MessageUnit("<key oid='"+methodName+"'/>", "Ho-"+ii, "<qos/>");
               try {
                  log.info(ME, "Publish ii=" + ii);
                  recorder.publish(msgUnit);
                  if (ii >= maxQueueSize)
                     fail(ME + " Expected exception because of full queue ii=" + ii);
               }
               catch (XmlBlasterException e) {
                  if (ii >= maxQueueSize && e.id.indexOf("MaxSize") >= 0) {
                     log.info(ME, "OK, expected exception ii=" + ii);
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
            String methodName = "publishArr";
            log.info(ME, "Testing '" + methodName + "' ...");
            for (int ii=0; ii<maxInvoke; ii++) {
               MessageUnit[] msgs = new MessageUnit[2];
               msgs[0] = new MessageUnit("<key oid='"+methodName+"'/>", "Ha-"+ii, "<qos/>");
               msgs[1] = new MessageUnit("<key oid='"+methodName+"'/>", "Hu-"+ii, "<qos/>");
               try {
                  recorder.publishArr(msgs);
                  if (ii >= maxQueueSize)
                     fail(ME + " Expected exception because of full queue ii=" + ii);
               }
               catch (XmlBlasterException e) {
                  if (ii >= maxQueueSize && e.id.indexOf("MaxSize") >= 0) {
                     log.info(ME, "OK, expected exception ii=" + ii);
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
         recorder.initialize(glob, (String)null, maxQueueSize, tester, tester);
         recorder.setMode(Constants.ONOVERFLOW_DISCARDOLDEST);

         {
            String methodName = "publish";
            log.info(ME, "Testing '" + methodName + "' ...");
            for (int ii=0; ii<maxInvoke; ii++) {
               MessageUnit msgUnit = new MessageUnit("<key oid='"+methodName+"'/>", "Ho-"+ii, "<qos/>");
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
         recorder.initialize(glob, (String)null, maxQueueSize, tester, tester);
         recorder.setMode(Constants.ONOVERFLOW_DISCARD);

         {
            String methodName = "publish";
            log.info(ME, "Testing '" + methodName + "' ...");
            for (int ii=0; ii<maxInvoke; ii++) {
               MessageUnit msgUnit = new MessageUnit("<key oid='"+methodName+"'/>", "Ho-"+ii, "<qos/>");
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
   }

   /**
    * This plays the role of a user of the recoder
    */
   class Tester implements I_XmlBlaster, I_CallbackRaw
   {
      public SubscribeRetQos subscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException {
         assertEquals("Wrong message key", "<key oid='subscribe'/>", xmlKey);
         assertEquals("Wrong message qos", "<qos/>", qos);
         numSubscribe++;
         return null;
      }
      
      public org.xmlBlaster.engine.helper.MessageUnit[] get(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException {
         assertEquals("Wrong message key", "<key oid='get'/>", xmlKey);
         assertEquals("Wrong message qos", "<qos/>", qos);
         numGet++;
         return new org.xmlBlaster.engine.helper.MessageUnit[0];
      }
      
      public void unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException {
         assertEquals("Wrong message key", "<key oid='unSubscribe'/>", xmlKey);
         assertEquals("Wrong message qos", "<qos/>", qos);
         numUnSubscribe++;
      }
      
      public PublishRetQos publish(org.xmlBlaster.engine.helper.MessageUnit msgUnit) throws XmlBlasterException {
         assertEquals("Wrong message key", "<key oid='publish'/>", msgUnit.getXmlKey());
         if (testDiscardOldest)
            assertEquals("Wrong message content", "Ho-"+(numPublish+2), msgUnit.getContentStr());
         else
            assertEquals("Wrong message content", "Ho-"+numPublish, msgUnit.getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnit.getQos());
         numPublish++;
         return null;
      }
      
      public void publishOneway(org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr) {
         assertEquals("Wrong message array length", 2, msgUnitArr.length);

         assertEquals("Wrong message key", "<key oid='publishOneway'/>", msgUnitArr[0].getXmlKey());
         assertEquals("Wrong message content", "Ha-"+numPublishOneway, msgUnitArr[0].getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnitArr[0].getQos());

         assertEquals("Wrong message key", "<key oid='publishOneway'/>", msgUnitArr[1].getXmlKey());
         assertEquals("Wrong message content", "Hu-"+numPublishOneway, msgUnitArr[1].getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnitArr[1].getQos());

         numPublishOneway++;
      }

      public PublishRetQos[] publishArr(org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr) throws XmlBlasterException {
         assertEquals("Wrong message array length", 2, msgUnitArr.length);

         assertEquals("Wrong message key", "<key oid='publishArr'/>", msgUnitArr[0].getXmlKey());
         assertEquals("Wrong message content", "Ha-"+numPublishArr, msgUnitArr[0].getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnitArr[0].getQos());

         assertEquals("Wrong message key", "<key oid='publishArr'/>", msgUnitArr[1].getXmlKey());
         assertEquals("Wrong message content", "Hu-"+numPublishArr, msgUnitArr[1].getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnitArr[1].getQos());

         numPublishArr++;
         return new PublishRetQos[0];
      }
      
      public EraseRetQos[] erase(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException {
         assertEquals("Wrong message key", "<key oid='erase'/>", xmlKey);
         assertEquals("Wrong message qos", "<qos/>", qos);
 
         numErase++;
         return new EraseRetQos[0];
      }

      public String[] update(String cbSessionId, org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr) throws XmlBlasterException {
         assertEquals("Wrong message array length", 2, msgUnitArr.length);
         assertEquals("Wrong cbSessionId", "dummy", cbSessionId);

         assertEquals("Wrong message key", "<key oid='update'/>", msgUnitArr[0].getXmlKey());
         assertEquals("Wrong message content", "Ha-"+numUpdate, msgUnitArr[0].getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnitArr[0].getQos());

         assertEquals("Wrong message key", "<key oid='update'/>", msgUnitArr[1].getXmlKey());
         assertEquals("Wrong message content", "Hu-"+numUpdate, msgUnitArr[1].getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnitArr[1].getQos());

         numUpdate++;
         return new String[0];
      }
      
      public void updateOneway(String cbSessionId, org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr) {
         assertEquals("Wrong message array length", 2, msgUnitArr.length);
         assertEquals("Wrong cbSessionId", "dummy", cbSessionId);

         assertEquals("Wrong message key", "<key oid='updateOneway'/>", msgUnitArr[0].getXmlKey());
         assertEquals("Wrong message content", "Ha-"+numUpdateOneway, msgUnitArr[0].getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnitArr[0].getQos());

         assertEquals("Wrong message key", "<key oid='updateOneway'/>", msgUnitArr[1].getXmlKey());
         assertEquals("Wrong message content", "Hu-"+numUpdateOneway, msgUnitArr[1].getContentStr());
         assertEquals("Wrong message qos", "<qos/>", msgUnitArr[1].getQos());

         numUpdateOneway++;
      }
   }

}
