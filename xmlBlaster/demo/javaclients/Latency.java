// xmlBlaster/demo/javaclients/Latency.java
package javaclients;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.*;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;


/**
 * Measure the brutto roundtrip latency of a message publish and update. 
 * <p />
 * Invoke examples (put xmlBlaster.jar in your CLASSPATH):
 * <br />
 * On localhost:
 * <pre>
 *   java org.xmlBlaster.Main
 *   java javaclients.Latency -numSend 100
 * </pre>
 * <br />
 * Over the internet with CORBA:
 * <pre>
 *   java javaclients.Latency -numSend 100 -bootstrapHostname server.xmlBlaster.org
 *  or if you have a dynamic IP:
 *   java javaclients.Latency -numSend 100 -bootstrapHostname server.xmlBlaster.org -dispatch/callback/plugin/ior/hostname <myCurrentIP>
 * </pre>
 * <br />
 * Over the internet with XmlRpc:
 * <pre>
 *   java javaclients.Latency -numSend 100 -dispatch/connection/protocol XMLRPC -dispatch/connection/plugin/xmlrpc/hostname server.xmlBlaster.org -dispatch/callback/plugin/xmlrpc/hostname <myCurrentIP>
 * </pre>
 * <p />
 * Results, for one round trip including publish -> processing in xmlBlaster -> update -> parsing in client (600 MHz AMD Linux):
 * <br />
 * <ul>
 *   <li>CORBA in intranet: ~ 6 milliseconds</li>
 *   <li>XmlRpc in intranet: ~ 16 milliseconds</li>
 *   <li>CORBA over internet: ~ 105 milliseconds</li>
 *   <li>XmlRpc over internet: ~ 320 milliseconds</li>
 * </ul>
 */
public class Latency implements I_Callback
{
   private long startTime = 0L;
   private boolean messageArrived = false;
   private final LogChannel log;

   public Latency(Global glob) {
      log = glob.getLog("client");
      try {
         I_XmlBlasterAccess con = glob.getXmlBlasterAccess();

         ConnectQos qos = new ConnectQos(glob);
         con.connect(qos, this);  // Login to xmlBlaster, register for updates

         PublishKey pk = new PublishKey(glob, "Latency", "text/xml", "1.0");
         PublishQos pq = new PublishQos(glob);
         MsgUnit msgUnit = new MsgUnit(pk.toXml(), "Hi".getBytes(), pq.toXml());

         SubscribeKey sk = new SubscribeKey(glob, "Latency");
         SubscribeQos sq = new SubscribeQos(glob);
         con.subscribe(sk.toXml(), sq.toXml()).getSubscriptionId();

         int numSend = glob.getProperty().get("numSend", 10);

         for (int ii=0; ii<numSend; ii++) {
            startTime = System.currentTimeMillis();
            con.publish(msgUnit);
            while (true) {
               try { Thread.sleep(50); } catch( InterruptedException i) {}
               if (messageArrived) {
                  messageArrived = false;
                  break;
               }
            }
         }

         try { Thread.sleep(1000); } catch( InterruptedException i) {} // wait a second to receive update()

         DisconnectQos dq = new DisconnectQos(glob);
         con.disconnect(dq);
      }
      catch (Exception e) {
         log.error("", e.toString());
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                        UpdateQos updateQos)
   {
      long endTime = System.currentTimeMillis();
      messageArrived = true;
      log.info("", "Received asynchronous message '" + updateKey.getOid() +
                   "' from xmlBlaster - latency=" + (endTime - startTime) + " millis");
      return "";
   }

   /**
    * Try
    * <pre>
    *   java javaclients.Latency -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         Global.instance().usage();
         System.err.println("Example: java javaclients.Latency -loginName Jeff\n");
         System.exit(1);
      }

      new Latency(glob);
   }
}
