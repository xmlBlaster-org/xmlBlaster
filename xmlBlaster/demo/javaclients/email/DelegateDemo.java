/*------------------------------------------------------------------------------
Name:      xmlBlaster/demo/javaclients/email/DelegateDemo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package javaclients.email;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.address.CallbackAddress;

/**
 * This client connects to xmlBlaster (for example with IOR or XMLRPC or EMAIL)
 * and subscribes for somebody else to a message to be delivered by email.
 * <p />
 * We then publish a message and the configured email address
 * receives the message. The message is send oneway, so that the receiver does
 * not need to ACK the mail.
 * <p />
 * Invoke:
 * <pre>
 *  java javaclients.email.DelegateDemo -dispatch/connection/protocol XMLRPC -emailDestination blue@localhost
 * </pre>
 * 
 * @see <a
 *      href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.email.html">The
 *      protocol.email requirement</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class DelegateDemo {
   private Global glob;

   public DelegateDemo(Global glob) {
      this.glob = glob;
      try {
         I_XmlBlasterAccess con = this.glob.getXmlBlasterAccess();

         ConnectQos qos = new ConnectQos(this.glob);

         String receiver = this.glob.getProperty().get("emailDestination",
               (String) null);
         if (receiver == null) {
            System.out.println("Usage:");
            System.out
                  .println("  java javaclients.email.DelegateDemo -protocol EMAIL -emailDestination blue@localhost");
            System.exit(1);
         }

         CallbackAddress cbAddr = new CallbackAddress(this.glob, "EMAIL");
         cbAddr.setRawAddress(receiver);
         cbAddr.setOneway(true);
         qos.addCallbackAddress(cbAddr);

         // null: Login to xmlBlaster without callback instantiation!
         con.connect(qos, null);

         con.subscribe("<key oid='EmailDemo'/>", "<qos/>");
         System.out
               .println("Subscribed topic 'EmailDemo' for to email destination '"
                     + receiver + "'");

         while (true) {
            int ch = Global
                  .waitOnKeyboardHit("["
                        + receiver
                        + "] Hit a key 'p' to publish, 'u' to unSubscribe or 'q' to quit >");
            if (ch == 'q')
               break;
            if (ch == 'p') {
               con.publish(new MsgUnit("<key oid='EmailDemo'/>", "Hi"
                     .getBytes(), "<qos/>"));
            }
            if (ch == 'u') {
               con.unSubscribe("<key oid='EmailDemo'/>", "<qos/>");
               break;
            }
         }

         con.disconnect(null);
      } catch (Exception e) {
         System.out.println(e.toString());
      }
   }

   public static void main(String args[]) {
      Global glob = new Global(); // initializes args, properties etc.
      if (glob.init(args) < 0) {
         System.out.println("EmailDemo wrong args, Bye");
         System.exit(1);
      }

      new DelegateDemo(glob);
   }
}
