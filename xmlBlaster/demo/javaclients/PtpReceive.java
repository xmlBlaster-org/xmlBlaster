// xmlBlaster/demo/javaclients/PtpReceive.java
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.*;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.address.Destination;


/**
 * Connects to xmlBlaster with name 'receiver' and waits for updates. 
 * <p />
 * After the third update (abortCount) it does a System.exit and aborts (without logout)
 * <p />
 * Use this client as a partner for PtpSend.java to play with xmlBlaster
 * <p />
 * Invoke:
 *  <pre>
 *  java PtpReceive
 *
 *  java PtpReceive  -abortCount 3
 *  </pre>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class PtpReceive
{
   private final String ME = "PtpReceive";
   private I_XmlBlasterAccess receiver = null;
   private final String receiverName = "receiver";
   private int counter = 0;
   private int abortCount = 3;

   public PtpReceive(final Global glob) {
      
      final LogChannel log = glob.getLog(null);
      abortCount = glob.getProperty().get("abortCount", 3);

      try {

         // setup the receiver client ...
         receiver = glob.getXmlBlasterAccess();

         ConnectQos qos = new ConnectQos(glob, receiverName, "secret");
         ConnectReturnQos conRetQos = receiver.connect(qos, new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               counter++;
               log.info(receiverName, "Receiving asynchronous message #" + counter + " '" + updateKey.getOid() + "' in default handler");
               if (counter == abortCount)
                  System.exit(-1);
               return "";
            }
         });  // Login to xmlBlaster, default handler for updates

         log.info(receiverName, "Receiver connected to xmlBlaster.");
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Houston, we have a problem: " + e.toString());
      }
      finally {
         // Wait a second for messages to arrive before we logout
         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {}
         log.info(ME, "Waiting on messages, aborting after " + abortCount + " messages, or hit a key to exit");
         try { System.in.read(); } catch(java.io.IOException e) {}
         
         if (receiver != null) { receiver.disconnect(new DisconnectQos(glob)); }
      }
   }

   /**
    * Try
    * <pre>
    *   java PtpReceive -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         glob.getLog(null).info("PtpReceive", "Example: java PtpReceive -abortCount 3\n");
         System.exit(1);
      }

      new PtpReceive(glob);
   }
}
