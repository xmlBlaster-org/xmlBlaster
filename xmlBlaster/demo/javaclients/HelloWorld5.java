// xmlBlaster/demo/javaclients/HelloWorld5.java
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;


/**
 * A sender and a receiver client connect to xmlBlaster,
 * the sender sends a PtP (point to point) message to the receiver
 * and the receiver responds with an ACK message.
 * <p />
 * Invoke: java HelloWorld5
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class HelloWorld5
{
   private final String ME = "HelloWorld5";
   private XmlBlasterConnection sender = null;
   private final String senderName = "sender";
   private XmlBlasterConnection receiver = null;
   private final String receiverName = "receiver";

   public HelloWorld5(final Global glob) {
      
      final LogChannel log = glob.getLog(null);

      try {

         {  // setup the sender client ...
            sender = new XmlBlasterConnection(glob);

            ConnectQos qos = new ConnectQos(glob, senderName, "secret");
            ConnectReturnQos conRetQos = sender.connect(qos, new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.info(senderName, "Receiving asynchronous message '" + updateKey.getOid() + "' in sender default handler");
                  return "";
               }
            });  // Login to xmlBlaster, default handler for updates

            log.info(senderName, "Sender connected to xmlBlaster.");
         }


         {  // setup the receiver client ...
            receiver = new XmlBlasterConnection(glob);

            ConnectQos qos = new ConnectQos(glob, receiverName, "secret");
            ConnectReturnQos conRetQos = receiver.connect(qos, new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.info(receiverName, "Receiving asynchronous message '" + updateKey.getOid() + "' in receiver default handler");
                  log.info(receiverName, updateKey.toXml() + "\n" + updateQos.toXml());

                  if (updateKey.isInternal()) return "";
                  if (updateQos.isErased()) return "";

                  try {
                     // Send an ACK back ...
                     PublishKey pk = new PublishKey(glob, "HelloWorld5:ACK", "text/plain", "1.0");
                     PublishQos pq = new PublishQos(glob);
                     pq.addDestination(new Destination(updateQos.getSender()));
                     MsgUnit msgUnit = new MsgUnit(glob, pk, "ACK", pq);
                     boolean oneway = false; // just for demo, you can try a variant with never blocking oneway
                     if (oneway) {
                        MsgUnit[] arr = new MsgUnit[1];
                        arr[0] = msgUnit;
                        receiver.publishOneway(arr);
                        log.info(receiverName, "Published message '" + pk.getOid() + "' to " + updateQos.getSender());
                     }
                     else {
                        PublishReturnQos retQos = receiver.publish(msgUnit);
                        log.info(receiverName, "Published message '" + pk.getOid() + "' to " + updateQos.getSender());
                     }
                  }
                  catch (XmlBlasterException e) {
                     log.error(receiverName, "Sending ACK to " + updateQos.getSender() + " failed: " + e.getMessage());
                  }

                  return "";
               }
            });  // Login to xmlBlaster, default handler for updates

            log.info(receiverName, "Receiver connected to xmlBlaster.");
         }

         // Send a message to 'receiver'
         PublishKey pk = new PublishKey(glob, "HelloWorld5", "text/plain", "1.0");
         PublishQos pq = new PublishQos(glob);
         pq.addDestination(new Destination(new SessionName(glob, receiverName)));
         MsgUnit msgUnit = new MsgUnit(glob, pk, "Hi", pq);
         PublishReturnQos retQos = sender.publish(msgUnit);
         log.info(senderName, "Published message '" + retQos.getKeyOid() + "' to " + receiverName);

      }
      catch (XmlBlasterException e) {
         log.error(ME, "Houston, we have a problem: " + e.getMessage());
      }
      finally {
         // Wait a second for messages to arrive before we logout
         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {}
         log.info(ME, "Success, hit a key to exit");
         try { System.in.read(); } catch(java.io.IOException e) {}
         
         if (sender != null) { sender.disconnect(new DisconnectQos(glob)); }
         if (receiver != null) { receiver.disconnect(new DisconnectQos(glob)); }
      }
   }

   /**
    * Try
    * <pre>
    *   java HelloWorld5 -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         XmlBlasterConnection.usage();
         glob.getLog(null).info("HelloWorld5", "Example: java HelloWorld5\n");
         System.exit(1);
      }

      new HelloWorld5(glob);
   }
}
