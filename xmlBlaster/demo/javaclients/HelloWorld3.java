// xmlBlaster/demo/javaclients/HelloWorld3.java
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.util.DisconnectQos;
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
import org.xmlBlaster.engine.helper.MessageUnit;


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
 * Invoke: java HelloWorld3 -loginName joe -passwd secret
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class HelloWorld3 implements I_Callback
{
   private final Global glob;
   private final LogChannel log;

   public HelloWorld3(Global glob) {
      this.glob = glob;
      this.log = glob.getLog(null);
      try {
         XmlBlasterConnection con = new XmlBlasterConnection(glob);

         // Check if other name or password was given on command line:
         String name = glob.getProperty().get("loginName", "HelloWorld3");
         String passwd = glob.getProperty().get("passwd", "secret");

         ConnectQos qos = new ConnectQos(glob, name, passwd);
         con.connect(qos, this);  // Login to xmlBlaster, register for updates


         PublishKey pk = new PublishKey(glob, "HelloWorld3", "text/xml", "1.0");
         pk.setClientTags("<org.xmlBlaster><demo/></org.xmlBlaster>");
         PublishQos pq = new PublishQos(glob);
         MessageUnit msgUnit = new MessageUnit(pk.toXml(), "Hi".getBytes(), pq.toXml());
         con.publish(msgUnit);


         GetKey gk = new GetKey(glob, "HelloWorld3");
         GetQos gq = new GetQos(glob);
         MessageUnit[] msgs = con.get(gk.toXml(), gq.toXml());
         GetReturnQos grq = new GetReturnQos(glob, msgs[0].getQos());

         log.info("", "Accessed xmlBlaster message with content '" + new String(msgs[0].getContent()) +
                      "' and status=" + grq.getState());


         SubscribeKey sk = new SubscribeKey(glob, "HelloWorld3");
         SubscribeQos sq = new SubscribeQos(glob);
         SubscribeReturnQos subRet = con.subscribe(sk.toXml(), sq.toXml());


         msgUnit = new MessageUnit(pk.toXml(), "Ho".getBytes(), pq.toXml());
         PublishReturnQos prq = con.publish(msgUnit);

         log.info("", "Got status='" + prq.getState() + "' for published message '" + prq.getKeyOid());

         try { Thread.currentThread().sleep(1000); } 
         catch( InterruptedException i) {} // wait a second to receive update()


         UnSubscribeKey uk = new UnSubscribeKey(glob, subRet.getSubscriptionId());
         UnSubscribeQos uq = new UnSubscribeQos(glob);
         con.unSubscribe(uk.toXml(), uq.toXml());

         EraseKey ek = new EraseKey(glob, "HelloWorld3");
         EraseQos eq = new EraseQos(glob);
         EraseReturnQos[] eraseArr = con.erase(ek.toXml(), eq.toXml());

         DisconnectQos dq = new DisconnectQos();
         con.disconnect(dq);
      }
      catch (Exception e) {
         log.error("", e.toString());
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
    *   java HelloWorld3 -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         XmlBlasterConnection.usage();
         System.err.println("Example: java HelloWorld3 -loginName Jeff\n");
         System.exit(1);
      }

      new HelloWorld3(glob);
   }
}
