// xmlBlaster/demo/javaclients/HelloWorldMime.java
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.*;
import org.xmlBlaster.client.*;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.AccessFilterQos;


/**
 * This client connects to xmlBlaster and invokes all available methods, further we
 * show how to do a full text message filtering by looking into the xml message content
 * and filter with XPath.
 * <p />
 * We use java client helper classes to generate the raw xml strings, e.g.:
 * <pre>
 *   PublishKeyWrapper pk = new PublishKeyWrapper("HelloWorldMime", "text/xml");
 * 
 * generates:
 *
 *   &lt;key oid='HelloWorldMime' contentMime='text/xml'/>
 * </pre>
 *
 * Invoke: java HelloWorldMime
 * <p />
 * Invoke: java HelloWorldMime -loginName joe -passwd secret
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


         PublishKeyWrapper pk = new PublishKeyWrapper("HelloWorldMime", "text/xml");
         pk.wrap("<org.xmlBlaster><demo/></org.xmlBlaster>");
         PublishQosWrapper pq = new PublishQosWrapper();
         MessageUnit msgUnit = new MessageUnit(pk.toXml(), "<news type='sport'/>".getBytes(), pq.toXml());
         con.publish(msgUnit);


         GetKeyWrapper gk = new GetKeyWrapper("HelloWorldMime");
         GetQosWrapper gq = new GetQosWrapper();
         gq.addAccessFilter(new AccessFilterQos(glob, "XPathFilter", "1.0", "/news[@type='sport']"));
         MessageUnit[] msgs = con.get(gk.toXml(), gq.toXml());

         log.info("", "Accessed xmlBlaster message synchronous with get() with content '" + new String(msgs[0].getContent()) + "'");


         SubscribeKeyWrapper sk = new SubscribeKeyWrapper("HelloWorldMime");
         SubscribeQosWrapper sq = new SubscribeQosWrapper();
         sq.addAccessFilter(new AccessFilterQos(glob, "XPathFilter", "1.0", "/news[@type='fishing']"));
         SubscribeRetQos subRet = con.subscribe(sk.toXml(), sq.toXml());


         msgUnit = new MessageUnit(pk.toXml(), "<news type='fishing'/>".getBytes(), pq.toXml());
         con.publish(msgUnit);


         try { Thread.currentThread().sleep(1000); } 
         catch( InterruptedException i) {} // wait a second to receive update()


         UnSubscribeKeyWrapper uk = new UnSubscribeKeyWrapper(subRet.getSubscriptionId());
         UnSubscribeQosWrapper uq = new UnSubscribeQosWrapper();
         con.unSubscribe(uk.toXml(), uq.toXml());

         EraseKeyWrapper ek = new EraseKeyWrapper("HelloWorldMime");
         EraseQosWrapper eq = new EraseQosWrapper();
         EraseRetQos[] eraseArr = con.erase(ek.toXml(), eq.toXml());

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
         System.err.println("Example: java HelloWorldMime -loginName Jeff\n");
         System.exit(1);
      }

      new HelloWorldMime(glob);
   }
}
