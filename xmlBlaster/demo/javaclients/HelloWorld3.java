// xmlBlaster/demo/javaclients/HelloWorld3.java
import org.xmlBlaster.util.*;
import org.xmlBlaster.client.*;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * This client connects to xmlBlaster and invokes all available methods.
 * Invoke: java HelloWorld3
 */
public class HelloWorld3 implements I_Callback
{
   public HelloWorld3(String[] args) {
      try {
         XmlBlasterConnection con = new XmlBlasterConnection(args);

         ConnectQos qos = new ConnectQos("simple", "1.0", "joe", "secret");
         con.connect(qos, this);  // Login to xmlBlaster, register for updates


         PublishKeyWrapper pk = new PublishKeyWrapper("HelloWorld3", "text/xml");
         PublishQosWrapper pq = new PublishQosWrapper();
         MessageUnit msgUnit = new MessageUnit(pk.toXml(), "Hi".getBytes(), pq.toXml());
         con.publish(msgUnit);


         GetKeyWrapper gk = new GetKeyWrapper("HelloWorld3");
         MessageUnit[] msgs = con.get(gk.toXml(), null);

         Log.info("", "xmlBlaster has currently " + new String(msgs[0].getContent()) +
                      " bytes of free memory");


         SubscribeKeyWrapper sk = new SubscribeKeyWrapper("HelloWorld3");
         SubscribeQosWrapper sq = new SubscribeQosWrapper();
         con.subscribe(sk.toXml(), sq.toXml());


         msgUnit = new MessageUnit(pk.toXml(), "Ho".getBytes(), pq.toXml());
         con.publish(msgUnit);


         try { Thread.currentThread().sleep(1000); } 
         catch( InterruptedException i) {} // wait a second


         UnSubscribeKeyWrapper uk = new UnSubscribeKeyWrapper("HelloWorld3");
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
                        UpdateQoS updateQoS)
   {
      Log.info("", "Received asynchronous message '" + updateKey.getOid() +
                   "' from xmlBlaster");
      return "";
   }

   public static void main(String args[]) {
      new HelloWorld3(args);
   }
}
