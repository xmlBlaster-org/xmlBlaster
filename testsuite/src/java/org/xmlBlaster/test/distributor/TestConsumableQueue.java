/*------------------------------------------------------------------------------
Name:      TestConsumableQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.distributor;


import java.util.ArrayList;

import org.xmlBlaster.test.util.Client;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

import junit.framework.*;

/**
 * Test JmsSubscribe. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.TestConsumableQueue
 * @see org.xmlBlaster.util.qos.ConnectQosData
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/jms.html" target="others">the jms requirement</a>
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class TestConsumableQueue extends TestCase {
   private Global global;
   // as a container and as a latch
   static ArrayList responses = new ArrayList();
   private static long WAIT_DELAY = 1000L;

   public TestConsumableQueue(String name) {
      super(name);
   }

   public void prepare(String[] args) {
      this.global = new Global(args);
   }

   protected void setUp() {
      this.global = Global.instance();

      responses.clear();
   }

   protected void tearDown() {
   }

   
   /**
    * Two subscribers log subscribe and then a publisher publishes
    * Only one of the subscribers should get the message.
    * This should test synchronous distribution
    */
   public void testSubSubPub() {
      try {
         boolean consumable = true;
         int session = 1;
         Client pub1 = new Client(this.global, "pub1", responses);
         pub1.init("testConsumableQueue", null, consumable, session);

         Client sub1 = new Client(this.global, "sub1", responses);
         sub1.init(null, "testConsumableQueue", consumable, session);
         Client sub2 = new Client(this.global, "sub2", responses);
         sub2.init(null, "testConsumableQueue", consumable, session);
         Client deadMsg = new Client(this.global, "deadMsg", responses);
         deadMsg.init(null, "__sys__deadMessage", !consumable, session);
         
         assertEquals("wrong number of initial responses", 0, responses.size());

         synchronized(responses) {
            pub1.publish("firstMessage");
            for (int i=0; i < 1; i++) responses.wait(WAIT_DELAY);
            Thread.sleep(200L); // wait in case an unexpected update comes in betweeen
            assertEquals("wrong number of updates", 1, responses.size());
         }
         responses.clear();         

         synchronized(responses) {
            sub1.setUpdateException(new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_ERROR, "testSubSubPub"));
            pub1.publish("firstMessage");
            for (int i=0; i < 2; i++) responses.wait(WAIT_DELAY);
            Thread.sleep(200L); // wait in case an unexpected update comes in betweeen
            assertEquals("wrong number of updates", 2, responses.size());
            assertEquals("update should be a dead message", "deadMsg", (String)responses.get(1));
         }

         responses.clear();         
         synchronized(responses) {
            sub1.setUpdateException(null);
            sub2.setUpdateException(new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_ERROR, "testSubSubPub"));
            pub1.publish("firstMessage");
            for (int i=0; i < 1; i++) responses.wait(WAIT_DELAY);
            Thread.sleep(200L); // wait in case an unexpected update comes in betweeen
            assertEquals("wrong number of updates, since the first sub receives, so it should not even try the second", 1, responses.size());
         }

         /** only one dead message here since the first gives up delivery */         
         responses.clear();         
         synchronized(responses) {
            sub1.setUpdateException(new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_ERROR, "testSubSubPub"));
            sub2.setUpdateException(new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_ERROR, "testSubSubPub"));
            pub1.publish("firstMessage");
            for (int i=0; i < 2; i++) responses.wait(WAIT_DELAY);
            Thread.sleep(200L); // wait in case an unexpected update comes in betweeen
            assertEquals("wrong number of updates", 2, responses.size());
            assertEquals("update should be a dead message", "deadMsg", (String)responses.get(1));
         }
         
         sub1.shutdown(false);
         sub2.shutdown(false);
         pub1.shutdown(true);
         deadMsg.shutdown(false);
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue(false);
      }
   }

   /**
    * A publisher publishes and then one subscriber subscribe 
    * The subscriber should get the message.
    * This should test asynchronous distribution
    */
   public void testPubSub() {
      try {
         boolean consumable = true;
         int session = 1; 
         Client pub1 = new Client(this.global, "pub1", responses);
         pub1.init("testConsumableQueue", null, consumable, session);

         pub1.publish("firstMessage");

         Client sub1 = new Client(this.global, "sub1", responses);
         assertEquals("wrong number of initial responses", 0, responses.size());

         synchronized(responses) {
            sub1.init(null, "testConsumableQueue", consumable, session);

            responses.wait(WAIT_DELAY);
            Thread.sleep(1000L); // wait in case an unexpected update comes in betweeen
            assertEquals("wrong number of updates", 1, responses.size());
         }
         
         sub1.shutdown(false);
         pub1.shutdown(true);
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue(false);
      }
   }

   /**
    * A publisher publishes and then two subscribers log subscribe 
    * Only one of the subscribers should get the message.
    * This should test asynchronous distribution
    */
   public void testPubSubSub() {
      try {
         boolean consumable = true; 
         int session = 1;
         Client pub1 = new Client(this.global, "pub1", responses);
         pub1.init("testConsumableQueue", null, consumable, session);

         pub1.publish("firstMessage");

         Client sub1 = new Client(this.global, "sub1", responses);
         Client sub2 = new Client(this.global, "sub2", responses);
         assertEquals("wrong number of initial responses", 0, responses.size());

         synchronized(responses) {
            sub1.init(null, "testConsumableQueue", consumable, session);
            sub2.init(null, "testConsumableQueue", consumable, session);

            responses.wait(WAIT_DELAY);
            Thread.sleep(1000L); // wait in case an unexpected update comes in betweeen
            assertEquals("wrong number of updates", 1, responses.size());
         }
         
         sub1.shutdown(false);
         sub2.shutdown(false);
         pub1.shutdown(true);
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue(false);
      }
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.TestConsumableQueue
    * </pre>
    */
   public static void main(String args[]) {
      TestConsumableQueue test = new TestConsumableQueue("TestConsumableQueue");
      test.prepare(args);

      test.setUp();
      test.testSubSubPub();
      test.tearDown();

      test.setUp();
      test.testPubSub();
      test.tearDown();

      test.setUp();
      test.testPubSubSub();
      test.tearDown();

   }
}
