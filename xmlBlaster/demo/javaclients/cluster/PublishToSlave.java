// xmlBlaster/demo/javaclients/cluster/PublishToSlave.java
package javaclients.cluster;

import org.jutils.log.LogChannel;
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
 *  java javaclients.cluster.PublishToSlave -port 7601
 *  java javaclients.cluster.PublishToSlave -port 7604
 * </pre>
 * We expect avalon to run on 7601, the domain is RUGBY_NEWS if not changed.<br />
 * If we set -port 7604 we expect to find bilbo which routes the message over
 * several node hops to the master. The message content is "We win".
 *
 * <pre>
 *  java javaclients.cluster.PublishToSlave -port 7601 -domain STOCK_EXCHANGE
 * </pre>
 * Now our message is sent with domain='STOCK_EXCHANGE', it should be routed to avalon
 *
 * <pre>
 *  java javaclients.cluster.PublishToSlave -port 7604 -content "We loose" -numPublish 5 -interactivePublish true
 * </pre>
 * Now our message is sent with the content "We loose" to bilbo, the message
 * is not forwarded as bilbo filters with a regular expression contents containing the word 'loose'.
 * We send 5 messages, and ask before sending.
 */
public class PublishToSlave implements I_Callback
{
   private final LogChannel log;

   public PublishToSlave(Global glob) {
      log = glob.getLog("client");
      XmlBlasterConnection con = null;
      try {
         con = new XmlBlasterConnection(glob);

         log.info("", "Usage example: java javaclients.cluster.PublishToSlave -port 7601 -numPublish 1000 -interactivePublish false");

         String domain = glob.getProperty().get("domain", "RUGBY_NEWS");
         String content = glob.getProperty().get("content", "We win");
         int numPublish = glob.getProperty().get("numPublish", 1);
         boolean interactivePublish = glob.getProperty().get("interactivePublish", true);

         ConnectQos qos = new ConnectQos(glob);
         ConnectReturnQos conRetQos = con.connect(qos, this);  // Login to xmlBlaster, register for updates
         log.info("PublishToSlave", "Connected to xmlBlaster.");

         PublishKeyWrapper pk = new PublishKeyWrapper("PublishToSlave."+domain, "text/xml", "1.0", domain);
         PublishQosWrapper pq = new PublishQosWrapper();
         pq.setPriority(Constants.LOW_PRIORITY);
         for (int i=0; i<numPublish; i++) {
            if (interactivePublish) {
               System.out.println("Hit a key to publish ...");
               try { System.in.read(); } catch(Exception e2) { }
            }
            MessageUnit msgUnit = new MessageUnit(pk.toXml(), content.getBytes(), pq.toXml());
            PublishRetQos retQos = con.publish(msgUnit);
            log.info("PublishToSlave", "Published #" + (i+1) + " message oid=" + pk.getOid() + " of domain='" + pk.getDomain() + "' and content='" + content +
                                    "' to xmlBlaster node with IP=" + glob.getProperty().get("port",0) +
                                    ", the returned QoS is: " + retQos.getOid());
         }
      }
      catch (Exception e) {
         log.error("PublishToSlave-Exception", e.toString());
      }
      finally {
         System.out.println("Hit a key to quit ...");
         try { System.in.read(); } catch(Exception e2) { }

         /*
         EraseKeyWrapper ek = new EraseKeyWrapper("PublishToSlave");
         EraseQosWrapper eq = new EraseQosWrapper();
         con.erase(ek.toXml(), uq.toXml());
         */
         
         if (con != null) {
            DisconnectQos dq = new DisconnectQos();
            con.disconnect(dq);
         }
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                        UpdateQos updateQos)
   {
      log.info("PublishToSlave", "Received asynchronous message '" + updateKey.getOid() +
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
         XmlBlasterConnection.usage();
         System.err.println("Example: java javaclients.cluster.PublishToSlave -port 7601 -domain STOCK_EXCHANGE -content 'We win'\n");
         System.exit(1);
      }

      new PublishToSlave(glob);
   }
}
