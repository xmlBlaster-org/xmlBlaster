// xmlBlaster/demo/javaclients/HelloWorld2.java
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * This client connects to xmlBlaster and subscribes to a message.
 * <p />
 * We then publish the message and receive it asynchronous in the update() method.
 * <p />
 * Invoke: java HelloWorld2
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class HelloWorld2 implements I_Callback
{
   public HelloWorld2(final Global glob) {
      try {
         XmlBlasterConnection con = new XmlBlasterConnection(glob);

         ConnectQos qos = new ConnectQos(glob, "joe", "secret");
         con.connect(qos, this);  // Login to xmlBlaster, register for updates

         con.subscribe("<key oid='HelloWorld2'/>", "<qos/>");

         con.publish(new MessageUnit("<key oid='HelloWorld2'/>", "Hi".getBytes(),
                                     "<qos/>"));

         try {
            Thread.currentThread().sleep(1000); // wait a second
            System.out.println("\nHit a key to logout and terminate ...");
            System.in.read();
         } catch(Exception e) { }

         con.erase("<key oid='HelloWorld2'/>", null);
         con.disconnect(null);
      }
      catch (Exception e) {
         System.err.println(e.toString());
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                        UpdateQos updateQos)
   {
      System.out.println("\nHelloWorld: Received asynchronous message '" +
         updateKey.getOid() + "' state=" + updateQos.getState() + " from xmlBlaster");
      return "";
   }

   /**
    * Try
    * <pre>
    *   java HelloWorld2 -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();

      if (glob.init(args) != 0) { // Get help with -help
         XmlBlasterConnection.usage();
         glob.getLog(null).info("HelloWorld2", "Example: java HelloWorld2\n");
         System.exit(1);
      }

      new HelloWorld2(glob);
   }
}
