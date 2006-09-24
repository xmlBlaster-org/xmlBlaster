// xmlBlaster/demo/HelloWorld9.java
import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;


/**
 * This client connects to xmlBlaster and blocks on a receive() call. 
 * <p />
 * Please invoke a publisher first to test it.
 * <pre>
 * Invoke: 
 *   java javaclients.HelloWorldPublish -oid hello -numPublish 10
 *   java HelloWorld9
 * </pre>
 * 
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class HelloWorld9
{
   private final Global glob;
   private static Logger log = Logger.getLogger(HelloWorld9.class.getName());
   private boolean stopThis;
   private long timeout = 60000; // Avoid unlimited = -1 to prevent a potential thread leak
   private boolean consuming = true;
   private String queueOid;

   public HelloWorld9(Global glob) {
      this.glob = glob;
      final I_XmlBlasterAccess con = this.glob.getXmlBlasterAccess();
      this.timeout = this.glob.getProperty().get("timeout", timeout);
      this.consuming = this.glob.getProperty().get("consuming", consuming);
      this.queueOid = this.glob.getProperty().get("queueOid", "topic/Hello");
      
      try {
         ConnectQos qos = new ConnectQos(glob);
         con.connect(qos, null);  // Login to xmlBlaster
         
         Thread thread = new Thread(new Runnable() {
            public void run() {
               while (true) {
                  try {
                     int ret = Global.waitOnKeyboardHit("Hit a key to call receive() or 'q' to quit> ");
                     if (ret == 'q') {
                        stopThis = true;
                        break;
                     }
                     int count = 1;
                     if (ret > '0' && ret <= '9') count = ret - '0';
                     System.out.println("Waiting on '" + queueOid + "' maxEntries=" + count + " timeout=" + timeout + " consumable=" + consuming + " ...");
                     MsgUnit[] msgs = con.receive(queueOid, count, timeout, consuming);
                     System.out.println("Received " + msgs.length + " messages");
                     for (int i=0; i<msgs.length; i++)
                        System.out.println("#" + i + ": " + msgs[i].getContentStr());
                  }
                  catch (XmlBlasterException e) {
                     log.severe(e.getMessage());
                  }
               }
            }
         });
         thread.start();

         while (!stopThis)
            try { Thread.sleep(100); } catch (InterruptedException e) {}

         con.disconnect(new DisconnectQos(glob));
         glob.shutdown(); // free resources
      }
      catch (XmlBlasterException e) {
         log.severe(e.getMessage());
      }
   }

   /**
    * Try
    * <pre>
    *   java HelloWorld9 -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.err.println("Example: java HelloWorld9 -session.name Jeff\n");
         System.exit(1);
      }

      new HelloWorld9(glob);
   }
}
