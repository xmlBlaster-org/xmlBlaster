/*------------------------------------------------------------------------------
Name:      SimpleJmsDemo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package javaclients.jms;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;

import org.jutils.log.LogChannel;
import org.xmlBlaster.jms.XBConnectionFactory;
import org.xmlBlaster.jms.XBTopic;
import org.xmlBlaster.util.Global;

/**
 * SimpleTest
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class SimpleJmsDemo implements MessageListener {

   private Global global;
   private LogChannel log;

   public SimpleJmsDemo(Global global) {
      this.global = global;
      this.log = this.global.getLog("jms-test");
   }
   
   public void prepare() throws JMSException {
      try {
         // create a factory (normally retreived by naming service)
         TopicConnectionFactory factory = new XBConnectionFactory(this.global);
         // should be retreived via jndi
         Topic topic = new XBTopic("jms-test");
      
         TopicConnection connection = factory.createTopicConnection();
         connection.start();
         TopicSession session = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
         TopicSubscriber subscriber = session.createSubscriber(topic);
         subscriber.setMessageListener(this);
      
         TopicPublisher publisher = session.createPublisher(topic);
//         connection.start();
      
         TextMessage msg = session.createTextMessage();
         msg.setText("this is a simple jms test message");
         publisher.publish(msg);
      
         Thread.sleep(3000L);
         connection.stop();
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }
   

   public void onMessage(Message message) {
      try {
         if (message instanceof TextMessage) {
            System.out.println(((TextMessage)message).getText());
            // message.acknowledge();
         }
      }
      catch (JMSException ex) {
         System.err.println(ex.getMessage());
         ex.printStackTrace();
      }
   }

   public static void main(String[] args) {
      Global global = new Global(args);
      SimpleJmsDemo test = new SimpleJmsDemo(global);
      try {
         test.prepare();
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }
}
