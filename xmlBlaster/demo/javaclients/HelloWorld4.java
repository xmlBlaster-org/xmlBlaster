// xmlBlaster/demo/javaclients/HelloWorld4.java
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.I_ConnectionProblems;
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
import org.xmlBlaster.client.protocol.XmlBlasterConnection;


/**
 * This client connects to xmlBlaster in fail save mode and uses specific update handlers. 
 * <p />
 * In fail save mode the client will poll for the xmlBlaster server and
 * queue messages until the server is available.
 * <p />
 * Invoke: java HelloWorld4
 * <p />
 * Invoke: java HelloWorld4 -session.name joe -passwd secret
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class HelloWorld4
{
   private final String ME = "HelloWorld4";
   private final LogChannel log;
   private XmlBlasterConnection con = null;
   private ConnectReturnQos conRetQos = null;
   private boolean connected;

   public HelloWorld4(final Global glob) {
      
      log = glob.getLog(null);

      try {
         con = new XmlBlasterConnection(glob);

         con.initFailSave(new I_ConnectionProblems() {
               
               public void reConnected() {
                  connected = true;
                  conRetQos = con.getConnectReturnQos();
                  log.info(ME, "I_ConnectionProblems: We were lucky, connected to " + glob.getId() + " as " + conRetQos.getSessionName());
                  //initClient();    // initialize subscription etc. again
                  try {
                     con.flushQueue();    // send all tailback messages
                     // con.resetQueue(); // or discard them (it is our choice)
                  } catch (XmlBlasterException e) {
                     log.error(ME, "Exception during reconnection recovery: " + e.getMessage());
                  }
               }

               public void lostConnection() {
                  log.warn(ME, "I_ConnectionProblems: No connection to " + glob.getId());
                  connected = false;
               }
            });


         ConnectQos qos = new ConnectQos(glob);
         conRetQos = con.connect(qos, new I_Callback() {

            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               if (log.DUMP) log.dump(ME, "UpdateKey.toString()=" + updateKey.toString() +
                                          "UpdateQos.toString()=" + updateQos.toString());
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


         connected = (conRetQos != null);
         if (connected)
            log.info(ME, "Connected as " + qos.getUserId() + " to xmlBlaster, your public session ID is " + conRetQos.getSessionName());
         else
            log.info(ME, "Not connected to xmlBlaster, proceeding in fail save mode ...");


         SubscribeKey sk = new SubscribeKey(glob, "Banking");
         SubscribeQos sq = new SubscribeQos(glob);
         SubscribeReturnQos sr1 = con.subscribe(sk.toXml(), sq.toXml());


         sk = new SubscribeKey(glob, "HelloWorld4");
         sq = new SubscribeQos(glob);
         SubscribeReturnQos sr2 = con.subscribe(sk.toXml(), sq.toXml(), new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               if (updateKey.getOid().equals("HelloWorld4"))
                  log.info(ME, "Receiving asynchronous message '" + updateKey.getOid() +
                           "' state=" + updateQos.getState() + " in HelloWorld4 handler");
               else
                  log.error(ME, "Receiving unexpected asynchronous message '" + updateKey.getOid() +
                            "' with state '" + updateQos.getState() + "' in HelloWorld4 handler");
               return "";
            }
         });  // subscribe with our specific update handler


         PublishKey pk = new PublishKey(glob, "HelloWorld4", "text/plain", "1.0");
         PublishQos pq = new PublishQos(glob);
         MsgUnit msgUnit = new MsgUnit(glob, pk, "Hi", pq);
         PublishReturnQos retQos = con.publish(msgUnit);
         log.info(ME, "Published message '" + pk.getOid() + "'");


         pk = new PublishKey(glob, "Banking", "text/plain", "1.0");
         pk.setClientTags("<Account><withdraw/></Account>"); // Add banking specific meta data
         pq = new PublishQos(glob);
         msgUnit = new MsgUnit(glob, pk, "Ho".getBytes(), pq);
         retQos = con.publish(msgUnit);
         log.info(ME, "Published message '" + pk.getOid() + "'");
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Houston, we have a problem: " + e.getMessage());
      }
      finally {
         // Wait a second for messages to arrive before we logout
         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {}
         log.info(ME, "Success, hit a key to exit");
         try { System.in.read(); } catch(java.io.IOException e) {}
         
         if (con != null) {
            try {
               EraseQos eq = new EraseQos(glob);

               EraseKey ek = new EraseKey(glob, "HelloWorld4");
               EraseReturnQos[] er = con.erase(ek.toXml(), eq.toXml());
               
               ek = new EraseKey(glob, "Banking");
               er = con.erase(ek.toXml(), eq.toXml());

               // Wait on message erase events
               try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {}
            }
            catch (XmlBlasterException e) {
               log.error(ME, "Houston, we have a problem: " + e.getMessage());
               e.printStackTrace();
            }
            
            con.disconnect(new DisconnectQos(glob));
         }
      }
   }

   /**
    * Try
    * <pre>
    *   java HelloWorld4 -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         XmlBlasterConnection.usage();
         System.err.println("Example: java HelloWorld4 -session.name Jeff\n");
         System.exit(1);
      }

      new HelloWorld4(glob);
   }
}
