// xmlBlaster/demo/javaclients/cluster/PublishToSlave.java
package javaclients.cluster;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.*;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.enum.Constants;


/**
 * This client connects to xmlBlaster node "avalon.mycomp.com" or any other specified by -port
 * and subscribes to "PublishToSlave" message. 
 *
 * Invoke examples:
 * <pre>
 *  java javaclients.cluster.Subscribe -port 7601
 *  java javaclients.cluster.Subscribe -port 7604
 * </pre>
 * We expect avalon to run on 7601.<br />
 * If we set -port 7604 we expect to find bilbo which routes the subscribe over
 * several node hops to the master.
 *
 * <pre>
 *  java javaclients.cluster.Subscribe -port 7601 -updateSleep 20000
 * </pre>
 * Now we sleep 20 sec on every update, simulating a slow client
 */
public class Subscribe implements I_Callback
{
   private final String ME = "Subscribe";
   private final LogChannel log;
   /** To simulate a slow subscriber client: millis to sleep when update arrives */
   private long updateSleep = 0L;

   public Subscribe(Global glob) {
      log = glob.getLog("client");
      I_XmlBlasterAccess con = null;
      updateSleep = glob.getProperty().get("updateSleep", 0L);
      try {
         con = glob.getXmlBlasterAccess();

         ConnectQos qos = new ConnectQos(glob);
         ConnectReturnQos conRetQos = con.connect(qos, this);  // Login to xmlBlaster, register for updates
         log.info(ME, "Connected to xmlBlaster.");


         String domain = glob.getProperty().get("domain", "RUGBY_NEWS");

         SubscribeKey sk = new SubscribeKey(glob, "PublishToSlave."+domain, Constants.EXACT);
         sk.setDomain(domain);
         SubscribeQos sq = new SubscribeQos(glob);
         SubscribeReturnQos subId = con.subscribe(sk.toXml(), sq.toXml());
         log.info(ME, "Subscribed message of domain='" + sk.toXml() + //domain +
                      "' from xmlBlaster node with IP=" + glob.getProperty().get("port",0) +
                      ", the returned subscriptionId is: " + subId.getSubscriptionId());
      }
      catch (Exception e) {
         log.error("Subscribe-Exception", e.toString());
      }
      finally {
         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {}
         System.out.println("Hit a key to quit ...");
         try { System.in.read(); } catch(Exception e2) { }

         if (con != null) {
            DisconnectQos dq = new DisconnectQos(glob);
            con.disconnect(dq);
         }
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                        UpdateQos updateQos)
   {
      if (updateSleep > 0L) {
         log.info(ME, "Received asynchronous message '" + updateKey.getOid() +
                                 "' '" + updateKey.getDomain() + " from xmlBlaster, sleeping for " + updateSleep + " millis ...");
         try { Thread.currentThread().sleep(updateSleep); } catch( InterruptedException i) {}
         log.info(ME, "Waking up.");
      } else {
         log.info(ME, "Received asynchronous message '" + updateKey.getOid() +
                                 "' from xmlBlaster");
      }
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
         System.out.println(glob.usage());
         System.out.println("Example: java javaclients.cluster.Subscribe -port 7601 -domain STOCK_EXCHANGE\n");
         System.exit(1);
      }

      new Subscribe(glob);
   }
}
