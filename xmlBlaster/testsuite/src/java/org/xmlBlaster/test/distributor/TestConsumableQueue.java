/*------------------------------------------------------------------------------
Name:      TestConsumableQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.distributor;


import java.util.ArrayList;

import org.jutils.log.LogChannel;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.qos.TopicProperty;

import junit.framework.*;

/**
 * Test JmsSubscribe. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.TestConsumableQueue
 * @see org.xmlBlaster.util.qos.ConnectQosData
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/jms.html" target="others">the jms requirement</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class TestConsumableQueue extends TestCase {
   private final static String ME = "TestConsumableQueue";
   private Global global;
   private LogChannel log;
   private Object latch = new Object();
   private String[] args;
   // as a container and as a latch
   static ArrayList responses = new ArrayList();

   class Client implements I_Callback {
      private String ME = "Client-";
      private Global global;
      private LogChannel log;
      private I_XmlBlasterAccess accessor;
      private String name;
      private String publishOid;
      private String subscribeOid;
      private boolean consumable;
      
      public Client(Global global, String name) {
         this.global = global.getClone(null);
         this.log = this.global.getLog("test");
         this.accessor = this.global.getXmlBlasterAccess();
         this.name = name;
         this.ME += this.name;
         if (this.log.CALL) this.log.call(ME, "constructor");
      }

      public void init(String publishOid, String subscribeOid, boolean consumable) throws XmlBlasterException {
         if (this.log.CALL) this.log.call(ME, "init");
         this.consumable = consumable;
         this.accessor.connect(new ConnectQos(this.global, name, "secret"), this);
         this.publishOid = publishOid;
         this.subscribeOid = subscribeOid;
         if (this.subscribeOid != null) 
            this.accessor.subscribe(new SubscribeKey(this.global, this.subscribeOid), new SubscribeQos(this.global));
      }

      public void publish(String content) throws XmlBlasterException {
         if (this.log.CALL) this.log.call(ME, "publish");
         if (this.publishOid == null)
            throw new XmlBlasterException(this.global, ErrorCode.USER_CLIENTCODE, ME, "no oid configured for publishing");
         if (content == null)
            throw new XmlBlasterException(this.global, ErrorCode.USER_CLIENTCODE, ME, "no content passed");
         
         PublishQos pubQos = new PublishQos(this.global);
         TopicProperty topicProp = new TopicProperty(this.global);
         topicProp.setMsgDistributor("ConsumableQueue,1.0");
         if (this.consumable) pubQos.setTopicProperty(topicProp);
         MsgUnit msgUnit = new MsgUnit(new PublishKey(this.global, this.publishOid), content, pubQos);
         this.accessor.publish(msgUnit);
      }

      public void shutdown(boolean doEraseTopic) throws XmlBlasterException {
         if (this.log.CALL) this.log.call(ME, "shutdown");
         if (this.publishOid != null && doEraseTopic) {
            this.accessor.erase(new EraseKey(this.global, this.publishOid), new EraseQos(this.global));
         }
         this.accessor.disconnect(new DisconnectQos(this.global));
      }


      public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
         throws XmlBlasterException {
         if (this.log.CALL) this.log.call(ME, "update '" + cbSessionId + "' content='" + new String(content) + "'");
         String clientProp = (String)updateQos.getData().getClientProperties().get("MsgDistributorPlugin");
         if (this.consumable) {
            assertNotNull("the client property 'MsgDistributorPlugin' has not been set on server side: plugin not invoked", clientProp);
            assertEquals("the client property 'MsgDistributorPlugin' has not been set by the wrong plugin", "ConsumableQueue,1.0", clientProp);
         }
         synchronized(responses) {
            responses.add(this.name);
            responses.notify();
         }
         return "OK";
      }
   }


   public TestConsumableQueue(String name) {
      super(name);
   }

   public void prepare(String[] args) {
      this.args = args;
      this.global = new Global(args);
      // this.glob.init(args);
      this.global.getLog("test");
   }

   protected void setUp() {
      this.global = Global.instance();
      this.log = this.global.getLog("test");
      responses.clear();
   }

   protected void tearDown() {
   }

   
   /**
    * Two subscribers log subcribe and then a publisher publishes
    * Only one of the subscribers should get the message.
    * This should test synchroneous distribution
    */
   public void testSubSubPub() {
      try {
         boolean consumable = true; 
         Client pub1 = new Client(this.global, "pub1");
         pub1.init("testConsumableQueue", null, consumable);

         Client sub1 = new Client(this.global, "sub1");
         sub1.init(null, "testConsumableQueue", consumable);
         Client sub2 = new Client(this.global, "sub2");
         sub2.init(null, "testConsumableQueue", consumable);
         
         assertEquals("wrong number of initial responses", 0, responses.size());

         synchronized(responses) {
            pub1.publish("firstMessage");
            responses.wait(5000L);
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
    * A publisher publishes and then one subscriber subcribe 
    * The subscriber should get the message.
    * This should test asynchroneous distribution
    */
   public void testPubSub() {
      try {
         boolean consumable = true; 
         Client pub1 = new Client(this.global, "pub1");
         pub1.init("testConsumableQueue", null, consumable);

         pub1.publish("firstMessage");

         Client sub1 = new Client(this.global, "sub1");
         assertEquals("wrong number of initial responses", 0, responses.size());

         synchronized(responses) {
            sub1.init(null, "testConsumableQueue", consumable);

            responses.wait(5000L);
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
    * A publisher publishes and then two subscribers log subcribe 
    * Only one of the subscribers should get the message.
    * This should test asynchroneous distribution
    */
   public void testPubSubSub() {
      try {
         boolean consumable = true; 
         Client pub1 = new Client(this.global, "pub1");
         pub1.init("testConsumableQueue", null, consumable);

         pub1.publish("firstMessage");

         Client sub1 = new Client(this.global, "sub1");
         Client sub2 = new Client(this.global, "sub2");
         assertEquals("wrong number of initial responses", 0, responses.size());

         synchronized(responses) {
            sub1.init(null, "testConsumableQueue", consumable);
            sub2.init(null, "testConsumableQueue", consumable);

            responses.wait(5000L);
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
