// xmlBlaster/demo/javaclients/HelloWorldErase.java
package javaclients;

import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.HistoryQos;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;

/**
 * This client connects to xmlBlaster and erases a configurable amount of messages. 
 * <p>
 * Try using 'java javaclients.HelloWorldPublish' in another window to create some messages.
 * </p>
 *
 * Invoke (after starting the xmlBlaster server):
 * <pre>
 *Erase all topics matching the given XPATH query:
 * java javaclients.HelloWorldErase -xpath /xmlBlaster/key/myToaster
 *
 *Erase topic "Sport"
 * java javaclients.HelloWorldErase -oid Sport
 * </pre>
 * @see javaclients.HelloWorldPublish
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class HelloWorldErase
{
   private final String ME = "HelloWorldErase";
   private final Global glob;
   private static Logger log = Logger.getLogger(HelloWorldErase.class.getName());

   public HelloWorldErase(Global glob) {
      this.glob = glob;

      try {
         boolean interactive = glob.getProperty().get("interactive", true);
         String oid = glob.getProperty().get("oid", "Hello");
         String xpath = glob.getProperty().get("xpath", (String)null);
         boolean forceDestroy = glob.getProperty().get("forceDestroy", false);
         boolean persistent = glob.getProperty().get("persistent", false);
         int historyNumErase = glob.getProperty().get("historyNumErase", -99);

         log.info("Used settings are:");
         log.info("   -interactive    " + interactive);
         log.info("   -oid            " + oid);
         log.info("   -xpath          " + xpath);
         log.info("   -forceDestroy   " + forceDestroy);
         log.info("   -persistent     " + persistent);

         I_XmlBlasterAccess con = glob.getXmlBlasterAccess();
         log.info("============= CreatingConnectQos");
         ConnectQos qos = new ConnectQos(glob);
         log.info("ConnectQos is " + qos.toXml());
         ConnectReturnQos crq = con.connect(qos, null);  // Login to xmlBlaster, register for updates
         log.info("Connect success as " + crq.toXml());

         if (interactive) {
            log.info("Hit a key to erase");
            try { System.in.read(); } catch(java.io.IOException e) {}
         }

         EraseKey ek;
         if (xpath != null)
            ek = new EraseKey(glob, xpath, Constants.XPATH);
         else
            ek = new EraseKey(glob, oid);
         EraseQos eq = new EraseQos(glob);
         eq.setForceDestroy(forceDestroy);
         eq.setPersistent(persistent);
         if (historyNumErase != -99) {
            eq.getData().setHistoryQos(new HistoryQos(glob, historyNumErase));
         }
         EraseReturnQos[] eraseArr = con.erase(ek, eq);
         for (int i=0; i < eraseArr.length; i++) {
            log.info(eraseArr[i].toXml());
         }
         log.info("Erased " + eraseArr.length + " topics");

         log.info("Hit a key to exit");
         try { System.in.read(); } catch(java.io.IOException e) {}

         DisconnectQos dq = new DisconnectQos(glob);
         con.disconnect(dq);
      }
      catch (XmlBlasterException e) {
         log.severe(e.getMessage());
      }
      catch (Exception e) {
         e.printStackTrace();
         log.severe(e.toString());
      }
   }

   /**
    * Try
    * <pre>
    *   java javaclients.HelloWorldErase -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.err.println("\nExample:");
         System.err.println("  java javaclients.HelloWorldErase -oid hello\n");
         System.exit(1);
      }

      new HelloWorldErase(glob);
   }
}
