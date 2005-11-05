// xmlBlaster/demo/javaclients/email/Demo.java
package javaclients.email;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.address.CallbackAddress;


/**
 * This client connects to xmlBlaster and subscribes to a message. 
 * We then publish the message and receive it asynchronous in the update() method. 
 * Invoke: java Demo
 * Configure on client side xmlBlaster.properties:
 * CbProtocolPlugin[EMAIL][1.0]=org.xmlBlaster.protocol.email.CallbackEmailDriver,mail.user=demo,mail.password=demo,mail.pop3.url=pop3://demo:demo@localhost:110/INBOX
 */
public class Demo
{
   private Global glob;

   public Demo(Global glob) {
      this.glob = glob;
      try {
         I_XmlBlasterAccess con = glob.getXmlBlasterAccess();

         ConnectQos qos = new ConnectQos(glob, "joe", "secret");

         String receiver = glob.getProperty().get("mail.from", (String)null);
         if (receiver != null) {
            CallbackAddress cbAddr =new CallbackAddress(glob, "EMAIL");
            cbAddr.setRawAddress(receiver);
            qos.addCallbackAddress(cbAddr);
         }
         
         con.connect(qos, null);  // Login to xmlBlaster without callback instantiation

         con.subscribe("<key oid='EmailDemo'/>", "<qos/>");

         con.publish(new MsgUnit("<key oid='EmailDemo'/>", "Hi".getBytes(),
                                     "<qos/>"));

         try { Thread.sleep(1000000); } 
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
    *    java javaclients.email.Demo -mail.from demo@localhost
    * </pre>
    * 'mail.from' is the callback address.
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
