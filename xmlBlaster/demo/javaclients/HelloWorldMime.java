// xmlBlaster/demo/javaclients/HelloWorldMime.java
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;


/**
 * This client connects to xmlBlaster and invokes all available methods, further we
 * show how to do a full text message filtering by looking into the xml message content
 * and filter with XPath.
 * <p />
 * We use java client helper classes to generate the raw xml strings, e.g.:
 * <pre>
 *   PublishKey pk = new PublishKey(glob, "HelloWorldMime", "text/xml");
 * 
 * generates:
 *
 *   &lt;key oid='HelloWorldMime' contentMime='text/xml'/>
 * </pre>
 *
 * Invoke: java HelloWorldMime
 * <p />
 * Invoke: java HelloWorldMime -session.name joe -passwd secret
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/mime.plugin.access.xpath.html" target="others">xmlBlaster mime.plugin.access.xpath requirement</a>
 */
public class HelloWorldMime implements I_Callback
{
   private final LogChannel log;

   public HelloWorldMime(Global glob) {
      log = glob.getLog(null);
      try {
         XmlBlasterConnection con = new XmlBlasterConnection(glob);

         ConnectQos qos = new ConnectQos(glob); // name, passwd can be set on command line, try -help
         con.connect(qos, this);  // Login to xmlBlaster, register for updates


         PublishKey pk = new PublishKey(glob, "HelloWorldMime", "text/xml");
         pk.setClientTags("<org.xmlBlaster><demo/></org.xmlBlaster>");
         PublishQos pq = new PublishQos(glob);
         MsgUnit msgUnit = new MsgUnit(pk.toXml(), "<news type='sport'/>".getBytes(), pq.toXml());
         con.publish(msgUnit);


         GetKey gk = new GetKey(glob, "HelloWorldMime");
         GetQos gq = new GetQos(glob);
         gq.addAccessFilter(new AccessFilterQos(glob, "XPathFilter", "1.0", "/news[@type='sport']"));
         MsgUnit[] msgs = con.get(gk.toXml(), gq.toXml());

         log.info("", "Accessed xmlBlaster message synchronous with get() with content '" + new String(msgs[0].getContent()) + "'");


         SubscribeKey sk = new SubscribeKey(glob, "HelloWorldMime");
         SubscribeQos sq = new SubscribeQos(glob);
         sq.addAccessFilter(new AccessFilterQos(glob, "XPathFilter", "1.0", "/news[@type='fishing']"));
         SubscribeReturnQos subRet = con.subscribe(sk.toXml(), sq.toXml());


         msgUnit = new MsgUnit(pk.toXml(), "<news type='fishing'/>".getBytes(), pq.toXml());
         con.publish(msgUnit);


         try { Thread.currentThread().sleep(1000); } 
         catch( InterruptedException i) {} // wait a second to receive update()


         UnSubscribeKey uk = new UnSubscribeKey(glob, subRet.getSubscriptionId());
         UnSubscribeQos uq = new UnSubscribeQos(glob);
         con.unSubscribe(uk.toXml(), uq.toXml());

         EraseKey ek = new EraseKey(glob, "HelloWorldMime");
         EraseQos eq = new EraseQos(glob);
         EraseReturnQos[] eraseArr = con.erase(ek.toXml(), eq.toXml());

         DisconnectQos dq = new DisconnectQos(glob);
         con.disconnect(dq);
      }
      catch (Exception e) {
         log.error("", e.getMessage());
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
      return "";
   }

   /**
    * Try
    * <pre>
    *   java HelloWorldMime -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         XmlBlasterConnection.usage();
         System.err.println("Example: java HelloWorldMime -session.name Jeff\n");
         System.exit(1);
      }

      new HelloWorldMime(glob);
   }
}
