// xmlBlaster/demo/javaclients/HelloWorldVolatile2.java
package javaclients;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.GetReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;


/**
 * This client connects to xmlBlaster and publishes a volatile message. 
 * <p>
 * Additionally the topic destroyDelay is reduced to 4 seconds, we can
 * see in the xmlBlaster dump that the topic disappeared 4 seconds later.
 * <pre>
 * &lt;qos>
 *   &lt;expiration lifeTime='0' forceDestroy='true'/>
 * &lt;/qos>
 * </pre>
 * </p>
 * <p />
 * Invoke: java javaclients.HelloWorldVolatile2
 * <p />
 * Invoke: java javaclients.HelloWorldVolatile2 -session.name joe -passwd secret
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.message.lifecycle.html" target="others">engine.message.lifecycle</a>
 */
public class HelloWorldVolatile2 implements I_Callback
{
   private final Global glob;
   private static Logger log = Logger.getLogger(HelloWorldVolatile2.class.getName());

   public HelloWorldVolatile2(Global glob) {
      this.glob = glob;

      try {
         I_XmlBlasterAccess con = glob.getXmlBlasterAccess();

         // Check if other name or password was given on command line:
         String name = glob.getProperty().get("session.name", "HelloWorldVolatile2");
         String passwd = glob.getProperty().get("passwd", "secret");

         ConnectQos connectQos = new ConnectQos(glob, name, passwd);
         con.connect(connectQos, this);  // Login to xmlBlaster, register for updates

         // Subscribe for the volatile message
         SubscribeKey sk = new SubscribeKey(glob, "HelloWorldVolatile2");
         SubscribeQos sq = new SubscribeQos(glob);
         SubscribeReturnQos subRet = con.subscribe(sk, sq);

         // Publish a volatile message
         PublishKey pk = new PublishKey(glob, "HelloWorldVolatile2", "text/xml", "1.0");
         PublishQos pq = new PublishQos(glob);
         pq.setVolatile(true);
         // Configure the topic to our needs
         TopicProperty topicProperty = new TopicProperty(glob);
         topicProperty.setDestroyDelay(4000L);
         topicProperty.setCreateDomEntry(false);
         pq.setTopicProperty(topicProperty);
         MsgUnit msgUnit = new MsgUnit(pk, "Hi", pq);
         con.publish(msgUnit);

         // This should not be possible as the message was volatile
         try {
            GetKey gk = new GetKey(glob, "HelloWorldVolatile2");
            GetQos gq = new GetQos(glob);
            MsgUnit[] msgs = con.get(gk, gq);
            if (msgs.length > 0) {
               GetReturnQos grq = new GetReturnQos(glob, msgs[0].getQos());
               log.severe("Did not expect any message as it was volatile");
            }
         }
         catch (XmlBlasterException e) {
            log.severe("Didn't expect an exception in get(): " + e.getMessage());
         }

         DisconnectQos dq = new DisconnectQos(glob);
         con.disconnect(dq);
      }
      catch (XmlBlasterException e) {
         log.severe(e.getMessage());
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                        UpdateQos updateQos) {
      if (updateKey.isInternal()) {
         log.info("Received unexpected internal message '" +
              updateKey.getOid() + " from xmlBlaster");
         return "";
      }

      log.info("Received asynchronous message '" + updateKey.getOid() +
                   "' state=" + updateQos.getState() +
                   " content=" + new String(content) + " from xmlBlaster");

      UpdateReturnQos uq = new UpdateReturnQos(glob);
      return uq.toXml();
   }

   /**
    * Try
    * <pre>
    *   java javaclients.HelloWorldVolatile2 -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.err.println("Example: java javaclients.HelloWorldVolatile2 -session.name Jeff\n");
         System.exit(1);
      }

      try {
         new HelloWorldVolatile2(glob);
      }
      catch (Throwable e) {
         e.printStackTrace();
         System.err.println("Unexpected problem: " + e.getMessage());
      }
   }
}
