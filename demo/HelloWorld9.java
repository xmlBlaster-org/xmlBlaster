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
public class HelloWorld9 implements I_Callback
{
   private final Global glob;
   private static Logger log = Logger.getLogger(HelloWorld9.class.getName());
   private boolean stopThis;
   private boolean consuming = true;
   private String queueOid;

   public HelloWorld9(Global glob) {
      this.glob = glob;
      final I_XmlBlasterAccess con = this.glob.getXmlBlasterAccess();
      this.consuming = this.glob.getProperty().get("consuming", consuming);
      this.queueOid = this.glob.getProperty().get("queueOid", "topic/hello");
      
      try {
         ConnectQos qos = new ConnectQos(glob);
         con.connect(qos, this);  // Login to xmlBlaster, register for updates
         
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
                     MsgUnit[] msgs = con.receive(queueOid, count, -1, consuming);
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

         DisconnectQos dq = new DisconnectQos(glob);
         con.disconnect(dq);
         glob.shutdown(); // free resources
      }
      catch (XmlBlasterException e) {
         log.severe(e.getMessage());
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                        UpdateQos updateQos)
   {
      log.info("Received asynchronous message '" + updateKey.getOid() +
                   "' state=" + updateQos.getState() +
                   " content=" + new String(content) + " from xmlBlaster");
      UpdateReturnQos uq = new UpdateReturnQos(glob);
      return uq.toXml();
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
