// xmlBlaster/demo/HelloWorld8.java
import java.util.logging.Logger;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.address.Destination;


/**
 * Shows the request/reply pattern. 
 * <p />
 * A sender and a receiver client connect to xmlBlaster,
 * the sender sends a request message to the receiver and blocks until a reply arrives.
 * The receiver responds with a reply message.
 * <p />
 * Invoke: java HelloWorld8
 * <p />
 * Note: This does not work with our 'email' protocol unless we configure
 *       two separate email accounts for each client connection.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class HelloWorld8
{
   private static Logger log = Logger.getLogger(HelloWorld8.class.getName());

   private I_XmlBlasterAccess sender = null;
   private final String senderName = "TheDesperate";
   private I_XmlBlasterAccess receiver = null;
   private final String receiverName = "TheKnowing";

   public HelloWorld8(final Global glob) {
      
      try {

         {  // setup the sender client ...
            sender = glob.getXmlBlasterAccess();

            ConnectQos qos = new ConnectQos(sender.getGlobal(), senderName, "secret");
            ConnectReturnQos conRetQos = sender.connect(qos, null); // Login to xmlBlaster

            log.info("Sender connected to xmlBlaster " + conRetQos.getSessionName().getRelativeName());
         }


         {  // setup the receiver client which processes the request (usually another process) ...
            Global globReceiver = glob.getClone(null);
            receiver = globReceiver.getXmlBlasterAccess();

            ConnectQos qos = new ConnectQos(receiver.getGlobal(), receiverName, "secret");
            ConnectReturnQos conRetQos = receiver.connect(qos, new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.info("receiver: Receiving asynchronous message '" + updateKey.getOid() + "' in receiver default handler");
                  log.info("receiver: Received: " + updateKey.toXml() + "\n <content>" + new String(content) + "</content>" + updateQos.toXml());

                  if (updateKey.isInternal()) return "";
                  if (updateQos.isErased()) return "";
                  try {
                     String tempTopicOid = updateQos.getClientProperty(Constants.JMS_REPLY_TO, ""); // __jms:JMSReplyTo

                     // Send reply back ...
                     PublishKey pk = new PublishKey(receiver.getGlobal(), tempTopicOid, "text/plain", "1.0");
                     PublishQos pq = new PublishQos(receiver.getGlobal());
                     MsgUnit msgUnit = new MsgUnit(pk, "On doubt no ultimate truth, my dear.", pq);
                     //try { Thread.sleep(8000); } catch (InterruptedException e) { e.printStackTrace(); }
                     PublishReturnQos retQos = receiver.publish(msgUnit);
                     log.info("Published reply message using temporary topic " + retQos.getKeyOid());
                  }
                  catch (XmlBlasterException e) {
                     log.severe("Sending reply to " + updateQos.getSender() + " failed: " + e.getMessage());
                  }
                  return "";
               }
            });  // Login to xmlBlaster, default handler for updates

            log.info("Receiver connected to xmlBlaster " + conRetQos.getSessionName().getRelativeName());
         }

         // Send a message to 'receiver' and block for the reply
         PublishKey pk = new PublishKey(sender.getGlobal(), "requestForEnlightenment");
         PublishQos pq = new PublishQos(sender.getGlobal());
         pq.addDestination(new Destination(new SessionName(sender.getGlobal(), receiverName)));
         MsgUnit msgUnit = new MsgUnit(pk, "Tell me the truth!", pq);
         MsgUnit[] replies = sender.request(msgUnit, 6000, 1);
         log.info("sender: Got " + replies.length + " reply :\n" + ((replies.length>0)?replies[0].toXml():""));
      }
      catch (XmlBlasterException e) {
         log.severe("We have a problem: " + e.getMessage());
      }
      finally {
         try { Thread.sleep(1000); } catch( InterruptedException i) {}
         Global.waitOnKeyboardHit("Success, hit a key to exit");
         
         if (sender != null && sender.isConnected()) { sender.disconnect(new DisconnectQos(sender.getGlobal())); }
         if (receiver != null && receiver.isConnected()) { receiver.disconnect(new DisconnectQos(receiver.getGlobal())); }
      }
   }

   /**
    * Try
    * <pre>
    *   java HelloWorld8 -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.out.println("Example: java HelloWorld8\n");
         System.exit(1);
      }

      new HelloWorld8(glob);
   }
}
