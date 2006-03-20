// xmlBlaster/demo/javaclients/HelloWorldVolatile.java
package javaclients;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
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
 * Volatile messages are messages which expire instantly after they are received
 * by xmlBlaster. Subscribers which are there already will receive the message even
 * if the message is hanging in a clients callback queue for an hour.
 * The publish QoS settings for volatile messages are
 * <pre>
 * &lt;qos>
 *   &lt;expiration lifeTime='0' forceDestroy='true'/>
 * &lt;/qos>
 * </pre>
 * </p>
 * <p />
 * Invoke: java javaclients.HelloWorldVolatile
 * <p />
 * Invoke: java javaclients.HelloWorldVolatile -session.name joe -passwd secret
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.message.lifecycle.html" target="others">engine.message.lifecycle</a>
 */
public class HelloWorldVolatile implements I_Callback
{
   private final Global glob;
   private static Logger log = Logger.getLogger(HelloWorldVolatile.class.getName());

   public HelloWorldVolatile(Global glob) {
      this.glob = glob;

      try {
         I_XmlBlasterAccess con = glob.getXmlBlasterAccess();

         ConnectQos qos = new ConnectQos(glob);
         con.connect(qos, this);  // Login to xmlBlaster, register for updates

         // Subscribe for the volatile message
         SubscribeKey sk = new SubscribeKey(glob, "HelloWorldVolatile");
         SubscribeQos sq = new SubscribeQos(glob);
         SubscribeReturnQos subRet = con.subscribe(sk, sq);

         // Publish a volatile message
         PublishKey pk = new PublishKey(glob, "HelloWorldVolatile", "text/xml", "1.0");
         PublishQos pq = new PublishQos(glob);
         pq.setVolatile(true);
         MsgUnit msgUnit = new MsgUnit(pk, "Hi", pq);
         con.publish(msgUnit);

         // This should not be possible as the message was volatile
         try {
            GetKey gk = new GetKey(glob, "HelloWorldVolatile");
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
    *   java javaclients.HelloWorldVolatile -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.err.println("Example: java javaclients.HelloWorldVolatile -session.name Jeff\n");
         System.exit(1);
      }

      try {
         new HelloWorldVolatile(glob);
      }
      catch (Throwable e) {
         e.printStackTrace();
         System.err.println("Unexpected problem: " + e.getMessage());
      }
   }
}
