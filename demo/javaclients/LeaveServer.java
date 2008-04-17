// xmlBlaster/demo/javaclients/LeaveServer.java
package javaclients;

import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;

/**
 * Test to leave the server without destroying the server side session. 
 * <p>
 * Invoke (after starting the xmlBlaster server):
 * </p>
 * <pre>
 * Test leaving 10 times
 * java javaclients.LeaveServer -interactive true -count 10
 * </pre>
 * 
 * @see <a
 *      href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html"
 *      target="others">xmlBlaster interface</a>
 */
public class LeaveServer {
   private static Logger log = Logger.getLogger(LeaveServer.class.getName());

   public LeaveServer(Global glob) {

      try {
         int countRuns = glob.getProperty().get("count", 10);
         boolean interactive = glob.getProperty().get("interactive", true);
         boolean connectPersistent = glob.getProperty().get(
               "connect/qos/persistent", false);
         Map connectQosClientPropertyMap = glob.getProperty().get(
               "connect/qos/clientProperty", (Map) null);

         log.info("Used settings are:");
         log.info("   -interactive    " + interactive);
         log.info(" ConnectQos settings");
         log.info("   -connect/qos/persistent " + connectPersistent);
         if (connectQosClientPropertyMap != null) {
            Iterator it = connectQosClientPropertyMap.keySet().iterator();
            while (it.hasNext()) {
               String key = (String) it.next();
               log.info("   -connect/qos/clientProperty[" + key + "]   "
                     + connectQosClientPropertyMap.get(key).toString());
            }
         } else {
            log.info("   -connect/qos/clientProperty[]   ");
         }
         log.info("For more info please read:");
         log
               .info("   http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html");

         for (int count = 0; count < countRuns; count++) {

            char ret = 0;
            if (interactive) {
               //while (ret != 'l' && ret != 'd' && ret != 'q')
                  ret = (char) Global
                        .waitOnKeyboardHit("Hit 'l' to connect and leave server (default), 'd' to connect and disconnect, 'q' to quit");
            }
            
            if (ret == 'q')
               break;

            I_XmlBlasterAccess con = new XmlBlasterAccess(glob.getClone(null));

            con.registerConnectionListener(new I_ConnectionStateListener() {

               public void reachedAlive(ConnectionStateEnum oldState,
                     I_XmlBlasterAccess connection) {
                  log.info("I_ConnectionStateListener: Connected");
               }

               public void reachedPolling(ConnectionStateEnum oldState,
                     I_XmlBlasterAccess connection) {
                  log
                        .warning("I_ConnectionStateListener: No connection to xmlBlaster server, we are polling ...");
               }

               public void reachedDead(ConnectionStateEnum oldState,
                     I_XmlBlasterAccess connection) {
                  log.warning("I_ConnectionStateListener: Connection from "
                        + connection.getGlobal().getId()
                        + " to xmlBlaster is DEAD, doing exit.");
               }
            });

            ConnectQos qos = new ConnectQos(glob);
            if (connectPersistent) {
               qos.setPersistent(connectPersistent);
            }
            if (connectQosClientPropertyMap != null) {
               Iterator it = connectQosClientPropertyMap.keySet().iterator();
               while (it.hasNext()) {
                  String key = (String) it.next();
                  qos.addClientProperty(key, connectQosClientPropertyMap.get(
                        key).toString());
               }
            }
            //log.info("ConnectQos is " + qos.toXml());
            /* ConnectReturnQos crq = */con.connect(qos, new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey,
                     byte[] content, UpdateQos updateQos)
                     throws XmlBlasterException {
                  try {
                     log.info("Received '" + updateKey.getOid() + "':"
                           + new String(content, "UTF-8"));
                  } catch (UnsupportedEncodingException e) {
                     log.severe("Update failed: " + e.toString());
                  }
                  return "";
               }
            }); // Login to xmlBlaster, register for updates
            log.info("Connect success");

            if (ret == 'd') {
               DisconnectQos dq = new DisconnectQos(glob);
               con.disconnect(dq);
               log.info("Disconnected from server, all resources released");
            } else {
               con.leaveServer(null);
               ret = 0;
               log.info("Left server, our server side session remains, bye");
            }
            con = null;

            Global.gc(2, 10L);
            log.info("Count=" + count + ": " + Global.getMemoryStatistic());
         }
         log.info("Bye");
      } catch (XmlBlasterException e) {
         log.severe(e.getMessage());
      } catch (Exception e) {
         e.printStackTrace();
         log.severe(e.toString());
      }
   }

   /**
    * Try
    * 
    * <pre>
    *   java javaclients.LeaveServer -help
    * </pre>
    * 
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();

      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.err.println("\nExample:");
         System.err
               .println("  java javaclients.LeaveServer -interactive false\n");
         System.exit(1);
      }

      new LeaveServer(glob);
   }
}
