// xmlBlaster/demo/javaclients/HelloWorldPublish.java
package javaclients;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
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
import org.xmlBlaster.client.protocol.XmlBlasterConnection;


/**
 * This client connects to xmlBlaster and invokes all available methods. 
 * <p />
 * We use java client helper classes to generate the raw xml strings, e.g.:
 * <pre>
 *   PublishKey pk = new PublishKey(glob, "HelloWorldPublish", "text/xml");
 * 
 * generates:
 *
 *   &lt;key oid='HelloWorldPublish' contentMime='text/xml'/>
 * </pre>
 *
 * Invoke: java javaclients.HelloWorldPublish -persistent true -erase true
 * <p />
 * Invoke: java javaclients.HelloWorldPublish -session.name joe -passwd secret -persistent true
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class HelloWorldPublish implements I_Callback
{
   private final String ME = "HelloWorldPublish";
   private final Global glob;
   private final LogChannel log;

   public HelloWorldPublish(Global glob) {
      this.glob = glob;
      this.log = glob.getLog(null);
      try {
         XmlBlasterConnection con = new XmlBlasterConnection(glob);

         // ConnectQos checks -session.name and -passwd from command line
         ConnectQos qos = new ConnectQos(glob);
         con.connect(qos, this);  // Login to xmlBlaster, register for updates

         log.info(ME, "Connect success, hit a key to publish");
         try { System.in.read(); } catch(java.io.IOException e) {}


         PublishKey pk = new PublishKey(glob, "HelloWorldPublish", "text/xml", "1.0");
         pk.setClientTags("<org.xmlBlaster><demo/></org.xmlBlaster>");
         PublishQos pq = new PublishQos(glob);
         pq.setPersistent(glob.getProperty().get("persistent", true));
         MsgUnit msgUnit = new MsgUnit(glob, pk, "Hi", pq);
         PublishReturnQos prq = con.publish(msgUnit);

         log.info("", "Got status='" + prq.getState() + "' for published message '" + prq.getKeyOid());

         if (glob.getProperty().get("erase", true)) {
            log.info(ME, "Publish success, hit a key to erase");
            try { System.in.read(); } catch(java.io.IOException e) {}

            EraseKey ek = new EraseKey(glob, "HelloWorldPublish");
            EraseQos eq = new EraseQos(glob);
            EraseReturnQos[] eraseArr = con.erase(ek.toXml(), eq.toXml());
            log.info(ME, "Erase success");
         }

         log.info(ME, "Hit a key to exit");
         try { System.in.read(); } catch(java.io.IOException e) {}

         DisconnectQos dq = new DisconnectQos(glob);
         con.disconnect(dq);
      }
      catch (XmlBlasterException e) {
         log.error(ME, e.getMessage());
      }
      catch (Exception e) {
         e.printStackTrace();
         log.error(ME, e.toString());
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                        UpdateQos updateQos)
   {
      if (updateKey.isInternal()) {
         log.info("", "Received unexpected internal message '" +
              updateKey.getOid() + " from xmlBlaster");
         return "";
      }

      log.info("", "Received asynchronous message '" + updateKey.getOid() +
                   "' state=" + updateQos.getState() +
                   " content=" + new String(content) + " from xmlBlaster");

      UpdateReturnQos uq = new UpdateReturnQos(glob);
      return uq.toXml();
   }

   /**
    * Try
    * <pre>
    *   java javaclients.HelloWorldPublish -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         XmlBlasterConnection.usage();
         System.err.println("Example: java HelloWorldPublish -session.name Jeff\n");
         System.exit(1);
      }

      new HelloWorldPublish(glob);
   }
}
