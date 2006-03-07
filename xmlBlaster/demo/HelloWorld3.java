// xmlBlaster/demo/HelloWorld3.java
import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
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
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;


/**
 * This client connects to xmlBlaster and invokes all available methods. 
 * <p />
 * We use java client helper classes to generate the raw xml strings, e.g.:
 * <pre>
 *   PublishKey pk = new PublishKey(glob, "HelloWorld3", "text/xml");
 * 
 * generates:
 *
 *   &lt;key oid='HelloWorld3' contentMime='text/xml'/>
 * </pre>
 *
 * Invoke: java HelloWorld3
 * <p />
 * Invoke: java HelloWorld3 -session.name joe -passwd secret
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class HelloWorld3 implements I_Callback
{
   private final Global glob;
   private static Logger log = Logger.getLogger(HelloWorld3.class.getName());

   public HelloWorld3(Global glob) {
      this.glob = glob;

      
      I_XmlBlasterAccess con = this.glob.getXmlBlasterAccess();
      
      try {
         // Check if other login name or password was given on command line:
         // (This is redundant as it is done by ConnectQos already)
         String name = glob.getProperty().get("session.name", "HelloWorld3");
         String passwd = glob.getProperty().get("passwd", "secret");

         ConnectQos qos = new ConnectQos(glob, name, passwd);
         con.connect(qos, this);  // Login to xmlBlaster, register for updates


         PublishKey pk = new PublishKey(glob, "HelloWorld3", "text/xml", "1.0");
         pk.setClientTags("<org.xmlBlaster><demo/></org.xmlBlaster>");
         PublishQos pq = new PublishQos(glob);
         pq.addClientProperty("myAge", 84);
         MsgUnit msgUnit = new MsgUnit(pk, "Hi", pq);
         con.publish(msgUnit);


         GetKey gk = new GetKey(glob, "HelloWorld3");
         GetQos gq = new GetQos(glob);
         MsgUnit[] msgs = con.get(gk, gq);
         if (msgs.length > 0) {
            GetReturnQos grq = new GetReturnQos(glob, msgs[0].getQos());
            log.info("Accessed xmlBlaster message with content '" + new String(msgs[0].getContent()) +
                      "' and status=" + grq.getState());
         }


         SubscribeKey sk = new SubscribeKey(glob, "HelloWorld3");
         SubscribeQos sq = new SubscribeQos(glob);
         SubscribeReturnQos subRet = con.subscribe(sk, sq);


         msgUnit = new MsgUnit(pk, "Ho".getBytes(), pq);
         PublishReturnQos prq = con.publish(msgUnit);

         log.info("Got status='" + prq.getState() + "' for published message '" + prq.getKeyOid());

         try { Thread.sleep(1000); } 
         catch( InterruptedException i) {} // wait a second to receive update()


         UnSubscribeKey uk = new UnSubscribeKey(glob, subRet.getSubscriptionId());
         UnSubscribeQos uq = new UnSubscribeQos(glob);
         UnSubscribeReturnQos[] urq = con.unSubscribe(uk, uq);
         if (urq.length > 0) log.info("Unsubscribed from topic");

         EraseKey ek = new EraseKey(glob, "HelloWorld3");
         EraseQos eq = new EraseQos(glob);
         EraseReturnQos[] eraseArr = con.erase(ek, eq);
         if (eraseArr.length > 0) log.info("Erased topic");

         DisconnectQos dq = new DisconnectQos(glob);
         con.disconnect(dq);
         glob.shutdown(); // free resources
      }
      catch (XmlBlasterException e) {
         log.severe(e.getMessage());
      }
      catch (Throwable e) {
         log.severe(e.toString());
         e.printStackTrace();
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                        UpdateQos updateQos)
   {
      if (updateKey.isInternal()) {
         log.info("Received unexpected internal message '" +
              updateKey.getOid() + " from xmlBlaster");
         return "";
      }

      int myAge = updateQos.getClientProperty("myAge", 0);
      log.info("Received asynchronous message '" + updateKey.getOid() +
                   "' state=" + updateQos.getState() +
                   " content=" + new String(content) + 
                   " clientProperty myAge=" + myAge + " from xmlBlaster");

      UpdateReturnQos uq = new UpdateReturnQos(glob);
      return uq.toXml();
   }

   /**
    * Try
    * <pre>
    *   java HelloWorld3 -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.err.println("Example: java HelloWorld3 -session.name Jeff\n");
         System.exit(1);
      }

      new HelloWorld3(glob);
   }
}
