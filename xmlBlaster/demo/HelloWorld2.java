// xmlBlaster/demo/HelloWorld2.java
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;


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
         I_XmlBlasterAccess con = glob.getXmlBlasterAccess();

         ConnectQos qos = new ConnectQos(glob);
         con.connect(qos, this);  // Login to xmlBlaster, register for updates

         con.subscribe("<key oid='HelloWorld2'/>", "<qos/>");
         
         // A similar subscription with XPATH:
         //con.subscribe("<key oid='' queryType='XPATH'>//key[@oid='HelloWorld2']</key>", "<qos/>");

         con.publish(new MsgUnit(glob, "<key oid='HelloWorld2'/>", "Hi".getBytes(),
                                     "<qos/>"));

         // wait a second
         try { Thread.sleep(1000); } catch(Exception e) { }
         Global.waitOnKeyboardHit("\nHit a key to logout and terminate ...");

         con.erase("<key oid='HelloWorld2'/>", null);
         try { Thread.sleep(100); } catch(Exception e) { }  // To process erase event
         con.disconnect(null);
      }
      catch (Exception e) {
         System.err.println(e.getMessage());
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
         System.out.println(glob.usage());
         System.out.println("Example: java HelloWorld2 -session.name Jack");
         System.exit(1);
      }

      new HelloWorld2(glob);
   }
}
