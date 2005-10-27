/*------------------------------------------------------------------------------
Name:      TestJmsSubscribe.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.jms;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.naming.NamingService;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

import org.xmlBlaster.jms.XBConnectionFactory;
import org.xmlBlaster.jms.XBDestination;

import junit.framework.*;

/**
 * Test JmsSubscribe. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.TestJmsSubscribe
 * @see org.xmlBlaster.util.qos.ConnectQosData
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/jms.html" target="others">the jms requirement</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class TestJmsSubscribe extends TestCase implements MessageListener {
   private final static String ME = "TestJmsSubscribe";
   private final static String CONNECTION_FACTORY = "connectionFactory";
   private final static String TOPIC = "jms-test";
   protected Global glob;
   protected LogChannel log;
   int counter = 0, nmax;
   private Throwable ex;
   
   private ConnectionFactory factory;
   private Destination topic;
   private Connection connection;
   private Message msg;
   
   private String[] args;
   private NamingService namingService;

   class PublisherThread extends Thread {
         
      private MessageProducer producer;
      private int numOfPublishes;
      private long delayBetweenPublishes;
      private Message msg;
      
      public PublisherThread(MessageProducer producer, Message msg, int numOfPublishes, long delayBetweenPublishes) {
         this.producer = producer;
         this.numOfPublishes = numOfPublishes;
         this.delayBetweenPublishes = delayBetweenPublishes;
         this.msg = msg;
      }
      
      public void run() {
         for (int i=0; i < this.numOfPublishes; i++) {
            try {
               Thread.sleep(this.delayBetweenPublishes);
               this.producer.send(this.msg);
            }
            catch (Exception ex) {
               ex.printStackTrace();
               assertTrue("Exception in publisher thread " + ex.getMessage() , false);
            }
         }
      }
   }

   public TestJmsSubscribe(String name) {
      super(name);
      try {
         this.namingService = new NamingService();
         this.namingService.start(); 
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("exception in constructor when starting naming service", false);
      }
   }

   public void finalize() {
      this.namingService.stop(); 
   }

   public void prepare(String[] args) {
      this.args = args;
      this.glob = new Global(args);
      this.glob.getLog("test");
   }

   public void onMessage(Message message) {
      try {
         if (this.log.CALL) this.log.call(ME, "onMessage start");
         if (message instanceof TextMessage) {
            this.counter++;
            this.log.trace(ME, ((TextMessage)message).getText());
            this.msg = message;
            // message.acknowledge();
         }
         if (this.log.CALL) this.log.call(ME, "onMessage stop");
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         this.ex = ex;
      }
   }


   protected void setUp() {
      this.glob = Global.instance();
      this.log = this.glob.getLog("test");
      try {
         adminJmsStart();
         this.ex = null;
         try {
            // TODO Re-introduce these. It seems that the serialization of Global is not
            // working properly yet.
            
            //InitialContext ctx = new InitialContext();
            //this.factory = (XBConnectionFactory)ctx.lookup(CONNECTION_FACTORY);
            //this.topic = (XBTopic)ctx.lookup(TOPIC);
         }
         catch (Exception ex) {
            ex.printStackTrace();
            assertTrue("naming exception", false);
         }

         this.connection = this.factory.createConnection();
         this.connection.start();
         this.nmax = 5;
         this.counter = 0;

      }
      catch (JMSException ex) {
         ex.printStackTrace();
         assertTrue(false);
      }
   }

   protected void tearDown() {
      try {
         this.connection.close();
         InitialContext ctx = new InitialContext();
         ctx.unbind(CONNECTION_FACTORY);
         ctx.unbind(TOPIC);
         this.connection = null;
      }
      catch (JMSException ex) {
         ex.printStackTrace();
         assertTrue(false);
      }
      catch (NamingException ex) {
         ex.printStackTrace();
         assertTrue("exception when unbinding", false);
      }
   }
   
   protected void adminJmsStart() {
      try {
         // System.setProperty("java.naming.factory.initial", "org.apache.naming.modules.memory.MemoryURLContextFactory");
         // System.setProperty("java.naming.factory.url.pkgs", "org.apache.naming.modules");
         InitialContext ctx = new InitialContext();
         String connQosTxt = null;
         boolean forQueues = false;
         this.factory = new XBConnectionFactory(connQosTxt, this.args, forQueues);
         this.topic = new XBDestination(TOPIC, null, false);
         ctx.bind(CONNECTION_FACTORY, this.factory);            
         ctx.bind(TOPIC, this.topic);
      }
      catch (NamingException ex) {
         ex.printStackTrace();
         assertTrue("exception occured in testJndi", false);
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("exception when starting naming service", false);
      }
   }
   
   private void async(int ackMode, String type) {
      // Session.AUTO_ACKNOWLEDGE
      try {
         boolean transacted = false;
         Session consumerSession = connection.createSession(transacted, ackMode);
         MessageConsumer subscriber = consumerSession.createConsumer(this.topic);
         subscriber.setMessageListener(this);
         Session producerSession = connection.createSession(transacted, ackMode);
         MessageProducer publisher = producerSession.createProducer(this.topic);

         for (int i=0; i < this.nmax; i++) {
            TextMessage msg = producerSession.createTextMessage();
            msg.setText("this is a " + type + " jms message nr. " + i);
            publisher.send(this.topic, msg);
         }
         
         if (ackMode == Session.CLIENT_ACKNOWLEDGE) {
            for (int i=0; i < this.nmax; i++) {
               Thread.sleep(250L);
               if (this.ex != null) {
                  assertTrue("An exception occured in the onMessage method. It should not. " + this.ex.getMessage(), false);
               }
               assertEquals("wrong number of " + type + " messages arrived", i+1, this.counter);
               this.msg.acknowledge(); // now it should continue
            }
         }
         else {
            Thread.sleep(1000L);
            if (this.ex != null) {
               assertTrue("An exception occured in the onMessage method. It should not. " + this.ex.getMessage(), false);
            }   
            assertEquals("wrong number of " + type + " messages arrived", this.nmax, this.counter);
         }
         this.counter = 0;
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("Exception occured when it should not. " + ex.getMessage(), false);
      }
   }

   public void testSubClientAck() {
      async(Session.CLIENT_ACKNOWLEDGE, "clientAcknowledge");
   }

   public void testSubAutoAck() {
      async(Session.AUTO_ACKNOWLEDGE, "autoAcknowledge");
   }
   
   public void testSubDupsOk() {
      // TODO remove this comment once DUPS_OK_ACKNOWLEDGE works
      // async(Session.DUPS_OK_ACKNOWLEDGE, "dupsOkAcknowledge");
   }
   
   public void testSyncReceiver() {
      try {
         Session consumerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSession.createConsumer(this.topic);
         Session publisherSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer publisher = publisherSession.createProducer(this.topic);
         int nmax = 3;

         // test receiveNoWait()
         TextMessage[] msgIn = new TextMessage[nmax];
         Message msg2 = null;
         for (int i=0; i < nmax; i++) {
            msgIn[i] = publisherSession.createTextMessage();
            msgIn[i].setText("msg " + i);
            publisher.send(this.topic, msgIn[i]);
         }
         for (int i=0; i < nmax; i++) {
            msg2 = consumer.receiveNoWait();
            if (!(msg2 instanceof TextMessage)) {
               assertTrue("received message if of wrong type, should be TextMessage but is '" + msg2.getClass().getName() + "'", false);
            }
            assertEquals("receive(): messages are not the same", msgIn[i].getText(), ((TextMessage)msg2).getText());         
         }
         msg2 = consumer.receiveNoWait();
         if (msg2 != null) {
            assertTrue("no message was sent, so null should have been returned here but it was " + msg.toString(), false);
         }
         
         // test receive(long)
         msgIn = new TextMessage[nmax];
         for (int i=0; i < nmax; i++) {
            msgIn[i] = publisherSession.createTextMessage();
            msgIn[i].setText("msg " + i);
            publisher.send(this.topic, msgIn[i]);
         }
         for (int i=0; i < nmax; i++) {
            msg2 = consumer.receive(200L);
            if (!(msg2 instanceof TextMessage)) {
               assertTrue("received message if of wrong type, should be TextMessage but is '" + msg2.getClass().getName() + "'", false);
            }
            assertEquals("receive(): messages are not the same", msgIn[i].getText(), ((TextMessage)msg2).getText());         
         }
         msg2 = consumer.receive(200L);
         if (msg2 != null) {
            assertTrue("no message was sent, so null should have been returned here but it was " + msg.toString(), false);
         }

         // test receive()
         msgIn = new TextMessage[nmax];
         for (int i=0; i < nmax; i++) {
            msgIn[i] = publisherSession.createTextMessage();
            msgIn[i].setText("msg " + i);
            publisher.send(this.topic, msgIn[i]);
         }
         for (int i=0; i < nmax; i++) {
            msg2 = consumer.receive();
            if (!(msg2 instanceof TextMessage)) {
               assertTrue("received message if of wrong type, should be TextMessage but is '" + msg2.getClass().getName() + "'", false);
            }
            assertEquals("receive(): messages are not the same", msgIn[i].getText(), ((TextMessage)msg2).getText());         
         }
         //PublisherThread pub = new PublisherThread(publisher, msg, 6, 100L);
         //pub.start();
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
      test.testSubDupsOk();
      test.tearDown();
      
      test.setUp();
      test.testSubAutoAck();
      test.tearDown();
      
      test.setUp();
      test.testSubClientAck();
      test.tearDown();
      
      test.setUp();
      test.testSyncReceiver();
      test.tearDown();
   }
}
