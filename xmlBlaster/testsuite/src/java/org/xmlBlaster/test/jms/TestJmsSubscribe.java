package org.xmlBlaster.test.jms;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicConnection;
import javax.jms.Topic;
import javax.jms.TopicSession;
import javax.jms.TopicPublisher;
import javax.jms.TopicSubscriber;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

import org.xmlBlaster.jms.XBConnectionFactory;
import org.xmlBlaster.jms.XBTopic;

import junit.framework.*;

/**
 * Test JmsSubscribe. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.TestJmsSubscribe
 * @see org.xmlBlaster.util.qos.ConnectQosData
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/jms.html" target="others">the jms requirement</a>
 */
public class TestJmsSubscribe extends TestCase implements MessageListener {
   private final static String ME = "TestJmsSubscribe";
   protected Global glob;
   protected LogChannel log;
   int counter = 0, nmax;

   private TopicConnectionFactory factory;
   private Topic topic;
   private TopicConnection connection;
   private Object latch = new Object();
   private long[] timestamps;


   public TestJmsSubscribe(String name) {
      super(name);
   }

   public void prepare(String[] args) {
      this.glob = new Global(args);
      // this.glob.init(args);
      this.glob.getLog("test");
   }

   public void onMessage(Message message) {
      if (this.log.CALL) this.log.call(ME, "onMessage start");
      try {
         if (message instanceof TextMessage) {
            this.timestamps[this.counter] = System.currentTimeMillis();
            this.counter++;
            System.out.println(((TextMessage)message).getText());
            Thread.sleep(200L);
            if (this.log.TRACE) this.log.trace(ME, "onMessage before ack");
            message.acknowledge();
            if (this.log.TRACE) this.log.trace(ME, "onMessage after ack");
            Thread.sleep(300L);
            if (this.log.TRACE) this.log.trace(ME, "onMessage after final sleeping");
            if (this.counter == this.nmax) {
               synchronized (this.latch) {
                  this.latch.notify();
               }
            } 
         }
         if (this.log.CALL) this.log.call(ME, "onMessage stop");
      }
      catch (JMSException ex) {
         System.err.println(ex.getMessage());
         ex.printStackTrace();
      }
      catch (InterruptedException ex) {
         System.err.println(ex.getMessage());
         ex.printStackTrace();
      }
   }


   protected void setUp() {
      this.glob = Global.instance();
      this.log = this.glob.getLog("test");
      try {
         // create a factory (normally retreived by naming service)
         this.factory = new XBConnectionFactory(this.glob);
         // should be retreived via jndi
         this.topic = new XBTopic("jms-test");
         this.connection = this.factory.createTopicConnection();
         this.connection.start();
         this.nmax = 5;
         this.timestamps = new long[this.nmax];
         this.counter = 0;
      }
      catch (JMSException ex) {
         ex.printStackTrace();
         assertTrue(false);
      }
   }

   protected void tearDown() {
      try {
         this.connection.stop();
      }
      catch (JMSException ex) {
         ex.printStackTrace();
         assertTrue(false);
      }
   }

   public void testSubAutoAck() {
      try {
         TopicSession session = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
         TopicSubscriber subscriber = session.createSubscriber(topic);
         subscriber.setMessageListener(this);
         TopicPublisher publisher = session.createPublisher(topic);
         TextMessage msg = session.createTextMessage();
         msg.setText("this is a simple jms AUTO acknowlegded test message");
         
         for (int i=0; i < this.nmax; i++) {
            publisher.publish(msg);
            Thread.sleep(50L);
         }
         synchronized(this.latch) {
            this.latch.wait(this.nmax * 700L);
            Thread.sleep(100L);
            assertEquals("number of onMessage invocations is wrong", this.nmax, this.counter);
         }
         double expDt = 500.0;
         double dt = 1.0 * (this.timestamps[this.nmax-1] - this.timestamps[0]) / (this.nmax - 1.0);
         this.log.info("", "The processing time is '" + dt + "' ms and expected is '" + expDt + "' ms");
         assertEquals("The expected processing time wrong", 1.0*expDt, 1.0*dt, 0.2*dt);
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue(false);
      }
   }

   public void testSubClientAck() {
      try {
         TopicSession session = connection.createTopicSession(false, Session.CLIENT_ACKNOWLEDGE);
         TopicSubscriber subscriber = session.createSubscriber(topic);
         subscriber.setMessageListener(this);
         TopicPublisher publisher = session.createPublisher(topic);
         TextMessage msg = session.createTextMessage();
         msg.setText("this is a simple jms CLIENT acknowlegded test message");
         
         long t1 = System.currentTimeMillis();
         for (int i=0; i < this.nmax; i++) {
            publisher.publish(msg);
            Thread.sleep(50L);
         }
         synchronized(this.latch) {
            this.latch.wait(this.nmax * 700L);
            Thread.sleep(100L);
            assertEquals("number of onMessage invocations is wrong", this.nmax, this.nmax);
         }
         double expDt = 200.0;
         double dt = 1.0 * (this.timestamps[this.nmax-1] - this.timestamps[0]) / (this.nmax - 1.0);
         this.log.info("", "The processing time is '" + dt + "' ms and expected is '" + expDt + "' ms");
         assertEquals("The expected processing time wrong", 1.0*expDt, 1.0*dt, 0.2*dt);
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue(false);
      }
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.TestJmsSubscribe
    * </pre>
    */
   public static void main(String args[])
   {
      TestJmsSubscribe test = new TestJmsSubscribe("TestJmsSubscribe");
      test.prepare(args);
      test.setUp();
      test.testSubAutoAck();
      test.tearDown();
      test.setUp();
      test.testSubClientAck();
      test.tearDown();
   }
}
