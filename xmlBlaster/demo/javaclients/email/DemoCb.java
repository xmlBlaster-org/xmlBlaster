// xmlBlaster/demo/javaclients/email/Demo.java
package javaclients.email;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import java.util.logging.Logger;

/**
 * This client connects to xmlBlaster and subscribes to a message. We then
 * publish the message and receive it asynchronous in the update() method.
 * Invoke: java Demo Configure on client side xmlBlaster.properties:
 * CbProtocolPlugin[EMAIL][1.0]=org.xmlBlaster.protocol.email.CallbackEmailDriver,mail.user=demo,mail.password=demo,mail.pop3.url=pop3://demo:demo@localhost:110/INBOX
 */
public class DemoCb implements I_Callback {
   private static Logger log = Logger.getLogger(DemoCb.class.getName());
   private Global glob;

   public DemoCb(Global glob) {
      this.glob = glob;
      try {
         I_XmlBlasterAccess con = glob.getXmlBlasterAccess();

         ConnectQos qos = new ConnectQos(glob, "joe", "secret");

         String receiver = glob.getProperty().get("mail.pop3.user", "demo@localhost");
         receiver = glob.getProperty().get("myCallbackEmailAddress", receiver);

         CallbackAddress cbAddr = new CallbackAddress(glob, "EMAIL");
         cbAddr.setRawAddress(receiver);
         qos.addCallbackAddress(cbAddr);

         con.connect(qos, this); // Login to xmlBlaster without callback
                                 // instantiation

         con.subscribe("<key oid='EmailDemo'/>", "<qos/>");

         while (true) {
            int ch = Global.waitOnKeyboardHit("[" + receiver
                  + "] Hit a key to publish a message >");
            if (ch == 'q')
               break;
            con.publish(new MsgUnit("<key oid='EmailDemo'/>", "Hi".getBytes(),
               "<qos/>"));
         }
         con.disconnect(null);
      } catch (Exception e) {
         System.out.println(e.toString());
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey,
         byte[] content, UpdateQos updateQos) {
      if (updateKey.isInternal()) {
         log.info("Received unexpected internal message '"
               + updateKey.getOid() + " from xmlBlaster");
         return "";
      }
      log.info("Received asynchronous message '" + updateKey.getOid()
            + "' state=" + updateQos.getState() + " content='"
            + new String(content) + "' from xmlBlaster");

      UpdateReturnQos uq = new UpdateReturnQos(glob);
      return uq.toXml();
   }

   /**
    * Invoke:
    * 
    * <pre>
    *     java javaclients.email.Demo -email.receiver xmlBlaster@marcelruff.info
    * </pre>
    */
   public static void main(String args[]) {
      Global glob = new Global(); // initializes args, properties etc.
      if (glob.init(args) < 0) {
         System.out.println("EmailDemo wrong args, Bye");
         System.exit(1);
      }

      new DemoCb(glob);
   }
}
