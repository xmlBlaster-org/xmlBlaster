// xmlBlaster/demo/javaclients/HelloWorld3.java
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
 * Invoke: java HelloWorld3 -name joe -passwd secret
 */
public class HelloWorld3 implements I_Callback
{
   public HelloWorld3(Global glob) {
      try {
         XmlBlasterConnection con = new XmlBlasterConnection(glob);

         // Check if other name or password was given on command line:
         String name = glob.getProperty().get("name", "HelloWorld3");
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

         Log.info("", "Accessed xmlBlaster message with content '" + new String(msgs[0].getContent()) + "'");


         SubscribeKeyWrapper sk = new SubscribeKeyWrapper("HelloWorld3");
         SubscribeQosWrapper sq = new SubscribeQosWrapper();
         String subId = con.subscribe(sk.toXml(), sq.toXml());


         msgUnit = new MessageUnit(pk.toXml(), "Ho".getBytes(), pq.toXml());
         con.publish(msgUnit);


         try { Thread.currentThread().sleep(1000); } 
         catch( InterruptedException i) {} // wait a second to receive update()


         UnSubscribeKeyWrapper uk = new UnSubscribeKeyWrapper(subId);
         UnSubscribeQosWrapper uq = new UnSubscribeQosWrapper();
         con.unSubscribe(uk.toXml(), uq.toXml());

         EraseKeyWrapper ek = new EraseKeyWrapper("HelloWorld3");
         EraseQosWrapper eq = new EraseQosWrapper();
         con.erase(ek.toXml(), uq.toXml());

         DisconnectQos dq = new DisconnectQos();
         con.disconnect(dq);
      }
      catch (Exception e) {
         Log.panic("", e.toString());
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                        UpdateQos updateQos)
   {
      Log.info("", "Received asynchronous message '" + updateKey.getOid() +
                   "' from xmlBlaster");
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
         Log.plain("\nAvailable options:");
         Log.plain("   -name               The login name [HelloWorld3].");
         Log.plain("   -passwd             The login name [secret].");
         XmlBlasterConnection.usage();
         Log.usage();
         Log.exit("", "Example: java HelloWorld3 -name Jeff\n");
      }

      new HelloWorld3(glob);
   }
}
