/*------------------------------------------------------------------------------
 Name:      xmlBlaster/demo/javaclients/email/DemoCb.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package javaclients.email;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;
//import org.xmlBlaster.util.qos.address.CallbackAddress;
import java.util.logging.Logger;

/**
 * This client connects to xmlBlaster and subscribes to a message. We then
 * publish the message and receive it asynchronous in the update() method.
 * <p />
 * Invoke:
 * <pre>
 *   java javaclients.email.DemoCb -protocol EMAIL -mail.from demo@localhost
 * </pre>
 * 
 * @see <a
 *      href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.email.html">The
 *      protocol.email requirement</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class DemoCb implements I_Callback {
   private static Logger log = Logger.getLogger(DemoCb.class.getName());

   private Global glob;

   public DemoCb(Global glob) {
      this.glob = glob;
      try {
         I_XmlBlasterAccess con = glob.getXmlBlasterAccess();

         ConnectQos qos = new ConnectQos(glob);

         //CallbackAddress cbAddr = new CallbackAddress(glob, "EMAIL");
         //cbAddr.setRawAddress("demo@localhost");
         //qos.addCallbackAddress(cbAddr);
         
         con.connect(qos, this);

         con.subscribe("<key oid='EmailDemo'/>", "<qos/>");

         while (true) {
            int ch = Global.waitOnKeyboardHit("Hit a key to publish a message (type 'q' to quit)>");
            if (ch == 'q')
               break;
            con.publish(new MsgUnit("<key oid='EmailDemo'/>", "Hi".getBytes(),
                  "<qos/>"));
            Thread.sleep(1000);
         }
         con.disconnect(null);
      } catch (Exception e) {
         System.out.println(e.toString());
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey,
         byte[] content, UpdateQos updateQos) {
      if (updateKey.isInternal()) {
         log.info("Received unexpected internal message '" + updateKey.getOid()
               + " from xmlBlaster");
         return "";
      }
      log.info("Received asynchronous message '" + updateKey.getOid()
            + "' state=" + updateQos.getState() + " content='"
            + new String(content) + "' from xmlBlaster");

      UpdateReturnQos uq = new UpdateReturnQos(glob);
      return uq.toXml();
   }

   public static void main(String args[]) {
      Global glob = new Global(); // initializes args, properties etc.
      if (glob.init(args) < 0) {
         System.out.println("EmailDemo wrong args, Bye");
         System.exit(1);
      }

      new DemoCb(glob);
   }
}
