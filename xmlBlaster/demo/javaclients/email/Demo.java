// xmlBlaster/demo/javaclients/email/Demo.java
package javaclients.email;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.address.CallbackAddress;


/**
 * This client connects to xmlBlaster and subscribes to a message. 
 * We then publish the message and receive it asynchronous in the update() method. 
 * Invoke: java Demo
 */
public class Demo
{
   private Global glob;

   public Demo(Global glob) {
      this.glob = glob;
      try {
         XmlBlasterConnection con = new XmlBlasterConnection(glob);

         ConnectQos qos = new ConnectQos(glob, "simple", "1.0", "joe", "secret");

         String receiver = glob.getProperty().get("email.receiver", "xmlblaster@xmlblaster.org");
         CallbackAddress cbAddr =new CallbackAddress(glob, "EMAIL");
         cbAddr.setAddress(receiver);
         qos.addCallbackAddress(cbAddr);
         
         con.connect(qos, null);  // Login to xmlBlaster without callback instantiation

         con.subscribe("<key oid='EmailDemo'/>", "<qos/>");

         con.publish(new MsgUnit("<key oid='EmailDemo'/>", "Hi".getBytes(),
                                     "<qos/>"));

         try { Thread.currentThread().sleep(1000); } 
         catch( InterruptedException i) {} // wait a second

         con.disconnect(null);
      }
      catch (Exception e) {
         System.out.println(e.toString());
      }
   }

   /**
    * Invoke:
    * <pre>
    *    java javaclients.email.Demo -email.receiver xmlBlaster@marcelruff.info
    * </pre>
    */
   public static void main(String args[]) {
      Global glob = new Global(); // initializes args, properties etc.
      if (glob.init(args) < 0)  {
         System.out.println("EmailDemo wrong args, Bye");
         System.exit(1);
      }

      new Demo(glob);
   }
}
