// xmlBlaster/demo/javaclients/PtpSend.java
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.address.Destination;


/**
 * A sender client connect to xmlBlaster,
 * the sender sends PtP (point to point) messages to the client "receiver"
 * <p />
 * Use this client as a partner for PtpReceive.java to play with xmlBlaster
 * <p />
 * Invoke:
 * <pre>
 * Start this sender:
 *
 *  java PtpSend
 *     (get exception if message is not delivered)
 *
 *  java PtpSend -numSend 1000 -delay 2000
 *     (send 1000 messages, sleep 2 sec in between)
 *
 *  java PtpSend -forceQueuing true
 *     (message is queued if user 'receiver' is offline)
 *
 * Start a receiver:
 *
 *  java PtpReceive
 *
 * </pre>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class PtpSend
{
   private final String ME = "PtpSend";
   private XmlBlasterConnection sender = null;
   private final String senderName = "sender";
   private final String receiverName = "receiver";

   public PtpSend(final Global glob) {
      
      final LogChannel log = glob.getLog(null);

      try {

         // setup the sender client ...
         sender = new XmlBlasterConnection(glob);

         ConnectQos qos = new ConnectQos(glob, senderName, "secret");
         ConnectReturnQos conRetQos = sender.connect(qos, new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               log.info(senderName, "Receiving asynchronous message '" + updateKey.getOid() + "' in default handler");
               return "";
            }
         });  // Login to xmlBlaster, default handler for updates

         log.info(senderName, "Sender connected to xmlBlaster.");

         int numSend = glob.getProperty().get("numSend", 100);
         long delay = glob.getProperty().get("delay", 2000);
         log.info(ME, "Send " + numSend + " messages to '" + receiverName + "' sleeping " + delay + " millis inbetween");
         for (int ii=0; ii<numSend; ii++) {
            try {
               PublishKey pk = new PublishKey(glob, "PtpSend", "text/plain", "1.0");

               PublishQos pq = new PublishQos(glob);
               Destination dest = new Destination(new SessionName(glob, receiverName));
               dest.forceQueuing(glob.getProperty().get("forceQueuing", false));
               pq.addDestination(dest);

               MsgUnit msgUnit = new MsgUnit(pk.toXml(), "Hi".getBytes(), pq.toXml());
               
               PublishReturnQos retQos = sender.publish(msgUnit);
               log.info(senderName, "Published message '" + retQos.getKeyOid() + "' to " + receiverName);
            }
            catch (XmlBlasterException e) {
               log.warn(ME, "We have a problem: " + e.toString());
            }
            finally {
               try { Thread.currentThread().sleep(delay); } catch( InterruptedException i) {}
            }
         }
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Houston, we have a problem: " + e.toString());
      }
      finally {
         if (sender != null) { sender.disconnect(new DisconnectQos()); }
      }
   }

   /**
    * Try
    * <pre>
    *   java PtpSend -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         XmlBlasterConnection.usage();
         glob.getLog(null).info("PtpSend", "Example: java PtpSend -forceQueuing true\n");
         System.exit(1);
      }

      new PtpSend(glob);
   }
}
