// xmlBlaster/demo/javaclients/HelloWorld2.java
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * This client connects to xmlBlaster and subscribes to a message. 
 * We then publish the message and receive it asynchronous in the update() method. 
 * Invoke: java HelloWorld2
 */
public class HelloWorld2 implements I_Callback
{
   public HelloWorld2(String[] args) {
      try {
         XmlBlasterConnection con = new XmlBlasterConnection(args);

         ConnectQos qos = new ConnectQos(null, "joe", "secret");
         con.connect(qos, this);  // Login to xmlBlaster, register for updates

         con.subscribe("<key oid='HelloWorld2'/>", "<qos/>");

         con.publish(new MessageUnit("<key oid='HelloWorld2'/>", "Hi".getBytes(),
                                     "<qos/>"));

         try {
            Thread.currentThread().sleep(1000); // wait a second
             Log.info("", "Hit a key to logout and terminate ...");
            System.in.read();
         } catch(Exception e) { }

         con.disconnect(null);
      }
      catch (Exception e) {
         Log.panic("", e.toString());
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                        UpdateQos updateQos)
   {
      Log.info("HelloWorld2", "Received asynchronous message '" +
               updateKey.getOid() + "' from xmlBlaster");
      return "";
   }

   public static void main(String args[]) {
      new HelloWorld2(args);
   }
}
