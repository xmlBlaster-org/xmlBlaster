// xmlBlaster/demo/javaclients/HelloWorld3.java
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.*;
import org.xmlBlaster.client.*;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * This client connects to xmlBlaster and invokes all available methods. 
 * <p />
 * We use java client helper classes to generate the raw xml strings, e.g.:
 * <pre>
 *   PublishKeyWrapper pk = new PublishKeyWrapper("HelloWorld3", "text/xml");
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
   private final LogChannel log;

   public HelloWorld3(Global glob) {
      log = glob.getLog(null);
      try {
         XmlBlasterConnection con = new XmlBlasterConnection(glob);

         // Check if other name or password was given on command line:
         String name = glob.getProperty().get("loginName", "HelloWorld3");
         String passwd = glob.getProperty().get("passwd", "secret");

         ConnectQos qos = new ConnectQos(glob, name, passwd);
         con.connect(qos, this);  // Login to xmlBlaster, register for updates


         PublishKeyWrapper pk = new PublishKeyWrapper("HelloWorld3", "text/xml");
         pk.wrap("<org.xmlBlaster><demo/></org.xmlBlaster>");
         PublishQosWrapper pq = new PublishQosWrapper();
         MessageUnit msgUnit = new MessageUnit(pk.toXml(), "Hi".getBytes(), pq.toXml());
         con.publish(msgUnit);


         GetKeyWrapper gk = new GetKeyWrapper("HelloWorld3");
         MessageUnit[] msgs = con.get(gk.toXml(), null);

         log.info("", "Accessed xmlBlaster message with content '" + new String(msgs[0].getContent()) + "'");


         SubscribeKeyWrapper sk = new SubscribeKeyWrapper("HelloWorld3");
         SubscribeQosWrapper sq = new SubscribeQosWrapper();
         SubscribeRetQos subRet = con.subscribe(sk.toXml(), sq.toXml());


         msgUnit = new MessageUnit(pk.toXml(), "Ho".getBytes(), pq.toXml());
         con.publish(msgUnit);


         try { Thread.currentThread().sleep(1000); } 
         catch( InterruptedException i) {} // wait a second to receive update()


         UnSubscribeKeyWrapper uk = new UnSubscribeKeyWrapper(subRet.getSubscriptionId());
         UnSubscribeQosWrapper uq = new UnSubscribeQosWrapper();
         con.unSubscribe(uk.toXml(), uq.toXml());

         EraseKeyWrapper ek = new EraseKeyWrapper("HelloWorld3");
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
