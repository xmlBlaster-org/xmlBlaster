// xmlBlaster/demo/javaclients/cluster/PublishToSlave.java
package javaclients.cluster;

import org.xmlBlaster.util.*;
import org.xmlBlaster.client.*;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * This client connects to xmlBlaster node "avalon.mycomp.com" and publishes
 * a "RUGBY_NEWS" message, which is routed to the master node "heron.mycomp.com". 
 *
 * Invoke: java javaclients.cluster.PublishToSlave -client.protocol SOCKET -socket.port 7601
 */
public class PublishToSlave implements I_Callback
{
   public PublishToSlave(Global glob) {
      try {
         XmlBlasterConnection con = new XmlBlasterConnection(glob);

         // Check if other name or password was given on command line:
         String name = glob.getProperty().get("name", "PublishToSlave");
         String passwd = glob.getProperty().get("passwd", "secret");

         ConnectQos qos = new ConnectQos(glob, "simple", "1.0", name, passwd);
         con.connect(qos, this);  // Login to xmlBlaster, register for updates

         PublishKeyWrapper pk = new PublishKeyWrapper("PublishToSlave", "text/xml", "1.0", "RUGBY_NEWS");
         PublishQosWrapper pq = new PublishQosWrapper();
         MessageUnit msgUnit = new MessageUnit(pk.toXml(), "We won".getBytes(), pq.toXml());
         con.publish(msgUnit);

         Log.info("PublishToSlave", "Published message of domain='" + pk.getDomain() + "' to xmlBlaster node");

         try { Thread.currentThread().sleep(1000); } 
         catch( InterruptedException i) {} // wait a second to receive update()

         System.out.println("Hit a key to quit ...");
         System.in.read();

         /*
         EraseKeyWrapper ek = new EraseKeyWrapper("PublishToSlave");
         EraseQosWrapper eq = new EraseQosWrapper();
         con.erase(ek.toXml(), uq.toXml());
         */

         DisconnectQos dq = new DisconnectQos();
         con.disconnect(dq);
      }
      catch (Exception e) {
         Log.panic("PublishToSlave", e.toString());
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                        UpdateQoS updateQoS)
   {
      Log.info("PublishToSlave", "Received asynchronous message '" + updateKey.getOid() +
                                 "' from xmlBlaster");
      return "";
   }

   /**
    * Try
    * <pre>
    *   java javaclients.cluster.PublishToSlave -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         Log.plain("\nAvailable options:");
         Log.plain("   -name               The login name [PublishToSlave].");
         Log.plain("   -passwd             The login name [secret].");
         XmlBlasterConnection.usage();
         Log.usage();
         Log.exit("PublishToSlave", "Example: java PublishToSlave -name Jeff\n");
      }

      new PublishToSlave(glob);
   }
}
