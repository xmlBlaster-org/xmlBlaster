// xmlBlaster/demo/javaclients/HelloWorld5.java
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.*;
import org.xmlBlaster.client.*;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Destination;


/**
 * A sender and a receiver client connect to xmlBlaster,
 * the sender sends a PtP (point to point) message to the receiver
 * and the receiver responds with an ACK message.
 * <p />
 * Invoke: java HelloWorld5
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>" target="others">CORBA xmlBlaster.idl</a>
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
                  log.info(senderName, "Reveiving asynchronous message '" + updateKey.getOid() + "' in default handler");
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
                  log.info(receiverName, "Reveiving asynchronous message '" + updateKey.getOid() + "' in default handler");

                  try {
                     // Send an ACK back ...
                     PublishKeyWrapper pk = new PublishKeyWrapper("HelloWorld5:ACK", "text/plain");
                     PublishQosWrapper pq = new PublishQosWrapper();
                     pq.addDestination(new Destination(updateQos.getSender()));
                     MessageUnit msgUnit = new MessageUnit(pk.toXml(), "ACK".getBytes(), pq.toXml());
                     String retQos = receiver.publish(msgUnit);
                     log.info(receiverName, "Published message '" + pk.getOid() + "' to " + updateQos.getSender());
                  }
                  catch (XmlBlasterException e) {
                     log.error(receiverName, "Sending ACK to " + updateQos.getSender() + " failed");
                  }

                  return "";
               }
            });  // Login to xmlBlaster, default handler for updates

            log.info(receiverName, "Receiver connected to xmlBlaster.");
         }

         // Send a message to 'receiver'
         PublishKeyWrapper pk = new PublishKeyWrapper("HelloWorld5", "text/plain", "1.0");
         PublishQosWrapper pq = new PublishQosWrapper();
         pq.addDestination(new Destination(receiverName));
         MessageUnit msgUnit = new MessageUnit(pk.toXml(), "Hi".getBytes(), pq.toXml());
         String retQos = sender.publish(msgUnit);
         log.info(senderName, "Published message '" + pk.getOid() + "' to " + receiverName);

      }
      catch (XmlBlasterException e) {
         log.error(ME, "Houston, we have a problem: " + e.toString());
      }
      finally {
         // Wait a second for messages to arrive before we logout
         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {}
         log.info(ME, "Success, hit a key to exit");
         try { System.in.read(); } catch(java.io.IOException e) {}
         
         if (sender != null) { sender.disconnect(new DisconnectQos()); }
         if (receiver != null) { receiver.disconnect(new DisconnectQos()); }
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
         Log.usage();
         Log.exit("HelloWorld5", "Example: java HelloWorld5\n");
      }

      new HelloWorld5(glob);
   }
}
