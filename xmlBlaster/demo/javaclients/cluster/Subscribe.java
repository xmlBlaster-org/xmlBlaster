// xmlBlaster/demo/javaclients/cluster/PublishToSlave.java
package javaclients.cluster;

import org.xmlBlaster.util.*;
import org.xmlBlaster.client.*;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Constants;


/**
 * This client connects to xmlBlaster node "avalon.mycomp.com" and publishes
 * a "RUGBY_NEWS" message, which is routed to the master node "heron.mycomp.com". 
 *
 * Invoke examples:
 * <pre>
 *  java javaclients.cluster.Subscribe -port 7601
 *  java javaclients.cluster.Subscribe -port 7604
 * </pre>
 * We expect avalon to run on 7601, the domain is RUGBY_NEWS if not changed.<br />
 * If we set -port 7604 we expect to find bilbo which routes the message over
 * several node hops to the master. The message content is "We win".
 *
 * <pre>
 *  java javaclients.cluster.Subscribe -port 7601 -domain STOCK_EXCHANGE
 * </pre>
 * Now our message is sent with domain='STOCK_EXCHANGE', it should be routed to avalon
 *
 * <pre>
 *  java javaclients.cluster.Subscribe -port 7604 -content "We loose"
 * </pre>
 * Now our message is sent with the content "We loose" to bilbo, the message
 * is not forwarded as bilbo filters with a regular expression contents containing the word 'loose'.
 */
public class Subscribe implements I_Callback
{
   private final String ME = "Subscribe";

   public Subscribe(Global glob) {
      XmlBlasterConnection con = null;
      try {
         con = new XmlBlasterConnection(glob);

         ConnectQos qos = new ConnectQos(glob);
         ConnectReturnQos conRetQos = con.connect(qos, this);  // Login to xmlBlaster, register for updates
         Log.info(ME, "Connected to xmlBlaster.");


         String domain = glob.getProperty().get("domain", "RUGBY_NEWS");

         SubscribeKeyWrapper sk = new SubscribeKeyWrapper("PublishToSlave", Constants.EXACT);
         sk.setDomain(domain);
         SubscribeQosWrapper sq = new SubscribeQosWrapper();
         String subId = con.subscribe(sk.toXml(), sq.toXml());
         Log.info(ME, "Subscribed message of domain='" + sk.toXml() + //domain +
                      "' from xmlBlaster node with IP=" + glob.getProperty().get("port",0) +
                      ", the returned subscriptionId is: " + subId);
      }
      catch (Exception e) {
         Log.error("Subscribe-Exception", e.toString());
      }
      finally {
         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {}
         System.out.println("Hit a key to quit ...");
         try { System.in.read(); } catch(Exception e2) { }

         if (con != null) {
            DisconnectQos dq = new DisconnectQos();
            con.disconnect(dq);
         }
      }

      Log.exit(ME, "Bye");
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                        UpdateQos updateQos)
   {
      Log.info(ME, "Received asynchronous message '" + updateKey.getOid() +
                                 "' from xmlBlaster");
      return "";
   }

   /**
    * Try
    * <pre>
    *   java javaclients.cluster.Subscribe -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         XmlBlasterConnection.usage();
         Log.exit("Subscribe", "Example: java javaclients.cluster.Subscribe -port 7601 -domain STOCK_EXCHANGE\n");
      }

      new Subscribe(glob);
   }
}
