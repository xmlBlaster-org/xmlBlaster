// xmlBlaster/demo/HelloWorld6.java
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.storage.ClientQueueProperty;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;


/**
 * This client connects to xmlBlaster in fail save mode and uses specific update handlers.
 * <p />
 * In fail save mode the client will poll for the xmlBlaster server and
 * queue messages until the server is available.<br />
 * Further you see how to configure the connection behavior hard coded.
 * <p />
 * Invoke: java HelloWorld6 -session.name jack/5
 * <p />
 * Invoke: java HelloWorld6 -session.name joe/2 -passwd secret -dispatch/connection/protocol XMLRPC
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class HelloWorld6
{
   private final String ME = "HelloWorld6";
   private final LogChannel log;
   private I_XmlBlasterAccess con = null;
   private ConnectReturnQos conRetQos = null;

   public HelloWorld6(final Global glob) {

      log = glob.getLog(null);

      try {
         con = glob.getXmlBlasterAccess();

         /*
         // Change hard-coded the protocol and server lookup:
         String[] args = { "-protocol", "SOCKET",
                           "-dispatch/connection/plugin/socket/hostname", "server.xmlBlaster.org",
                           "-dispatch/connection/plugin/socket/port", "9455",
                           //"-dispatch/connection/plugin/socket/localHostname", "myHost.com",
                           //"-dispatch/connection/plugin/socket/localPort", "8888"
                         };
         glob.init(args);
         */

         ConnectQos connectQos = new ConnectQos(glob);

         ClientQueueProperty prop = new ClientQueueProperty(glob, null);
         prop.setMaxEntries(10000);    // Client side queue up to 10000 entries if not connected

         Address address = new Address(glob);
         address.setDelay(4000L);      // retry connecting every 4 sec
         address.setRetries(-1);       // -1 == forever
         address.setPingInterval(2000L);  // ping every 2 sec

         // Example how to hardcode a XmlRpc server:
         //address.setType("XMLRPC");    // force XmlRpc protocol
         //address.setRawAddress("http://noty:9456/"); // Address to find the server

         // Example how to hardcode a SOCKET server:
         //address.setType("SOCKET");    // force SOCKET protocol
         //address.setRawAddress("socket://noty:9988"); // Address to find the server

         prop.setAddress(address);
         connectQos.addClientQueueProperty(prop);

         CallbackAddress cbAddress = new CallbackAddress(glob);
         cbAddress.setDelay(4000L);      // retry connecting every 4 sec
         cbAddress.setRetries(-1);       // -1 == forever
         cbAddress.setPingInterval(4000L); // ping every 4 seconds

         // Example how to hardcode a SOCKET server:
         //cbAddress.setType("SOCKET");    // force SOCKET protocol for callback

         connectQos.addCallbackAddress(cbAddress);

         connectQos.addClientQueueProperty(prop);

         // We want to be notified about connection states:
         con.registerConnectionListener(new I_ConnectionStateListener() {

               public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
                  conRetQos = connection.getConnectReturnQos();
                  log.info(ME, "I_ConnectionStateListener: We were lucky, connected to " + glob.getId() + " as " + conRetQos.getSessionName());
                  // we can access the queue via connectionHandler and for example erase the entries ...
               }
               public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
                  log.warn(ME, "I_ConnectionStateListener: No connection to " + glob.getId() + ", we are polling ...");
               }
               public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
                  log.warn(ME, "I_ConnectionStateListener: Connection to " + glob.getId() + " is DEAD -> Good bye");
                  System.exit(1);
               }
            });

         // We connect to xmlBlaster and register the callback handle:
         this.conRetQos = con.connect(connectQos, new I_Callback() {

            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               if (log.DUMP) log.dump(ME, "UpdateKey.toString()=" + updateKey.toString());
               if (updateKey.isInternal()) {
                  log.error(ME, "Receiving unexpected asynchronous internal message '" + updateKey.getOid() +
                                "' in default handler");
                  return "";
               }
               if (updateQos.isErased()) {
                  log.info(ME, "Message '" + updateKey.getOid() + "' is erased");
                  return "";
               }
               if (updateKey.getOid().equals("Banking"))
                  log.info(ME, "Receiving asynchronous message '" + updateKey.getOid() +
                               "' state=" + updateQos.getState() + " in default handler");
               else
                  log.error(ME, "Receiving unexpected asynchronous message '" + updateKey.getOid() +
                                   "' in default handler");
               return "";
            }

         });  // Login to xmlBlaster, default handler for updates


         if (con.isAlive())
            log.info(ME, "Connected as " + connectQos.getUserId() + " to xmlBlaster: " + this.conRetQos.getSessionName());
         else
            log.info(ME, "Not connected to xmlBlaster, proceeding in fail save mode ...");


         SubscribeKey sk = new SubscribeKey(glob, "Banking");
         SubscribeQos sq = new SubscribeQos(glob);
         SubscribeReturnQos sr1 = con.subscribe(sk, sq);


         sk = new SubscribeKey(glob, "HelloWorld6");
         sq = new SubscribeQos(glob);
         SubscribeReturnQos sr2 = con.subscribe(sk, sq, new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               if (updateKey.getOid().equals("HelloWorld6"))
                  log.info(ME, "Receiving asynchronous message '" + updateKey.getOid() +
                           "' state=" + updateQos.getState() + " in HelloWorld6 handler");
               else
                  log.error(ME, "Receiving unexpected asynchronous message '" + updateKey.getOid() +
                            "' with state '" + updateQos.getState() + "' in HelloWorld6 handler");
               return "";
            }
         });  // subscribe with our specific update handler


         PublishKey pk = new PublishKey(glob, "HelloWorld6", "text/plain", "1.0");
         PublishQos pq = new PublishQos(glob);
         MsgUnit msgUnit = new MsgUnit(pk, "Hi".getBytes(), pq);
         PublishReturnQos retQos = con.publish(msgUnit);
         log.info(ME, "Published message '" + pk.getOid() + "'");


         pk = new PublishKey(glob, "Banking", "text/plain", "1.0");
         pk.setClientTags("<Account><withdraw/></Account>"); // Add banking specific meta data
         pq = new PublishQos(glob);
         msgUnit = new MsgUnit(pk, "Ho".getBytes(), pq);
         retQos = con.publish(msgUnit);
         log.info(ME, "Published message '" + pk.getOid() + "'");
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Houston, we have a problem: " + e.toString());
      }
      finally {
         // Wait a second for messages to arrive before we logout
         try { Thread.sleep(1000); } catch( InterruptedException i) {}
         Global.waitOnKeyboardHit("Success, hit a key to exit");

         if (con != null && con.isConnected()) {
            try {
               EraseQos eq = new EraseQos(glob);

               EraseKey ek = new EraseKey(glob, "HelloWorld6");
               EraseReturnQos[] er = con.erase(ek, eq);

               ek = new EraseKey(glob, "Banking");
               er = con.erase(ek, eq);

               // Wait on message erase events
               try { Thread.sleep(1000); } catch( InterruptedException i) {}
            }
            catch (XmlBlasterException e) {
               log.error(ME, "Houston, we have a problem: " + e.toString());
               e.printStackTrace();
            }

            con.disconnect(new DisconnectQos(glob));
         }
      }
   }

   /**
    * Try
    * <pre>
    *   java HelloWorld6 -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();

      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.err.println("Example: java HelloWorld6 -session.name Jeff\n");
         System.exit(1);
      }

      new HelloWorld6(glob);
   }
}
