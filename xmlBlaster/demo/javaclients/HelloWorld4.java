// xmlBlaster/demo/javaclients/HelloWorld4.java
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.*;
import org.xmlBlaster.client.*;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * This client connects to xmlBlaster in fail save mode and uses specific update handlers. 
 * <p />
 * In fail save mode the client will poll for the xmlBlaster server and
 * queue messages until the server is available.
 * <p />
 * Invoke: java HelloWorld4
 * <p />
 * Invoke: java HelloWorld4 -loginName joe -passwd secret
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 */
public class HelloWorld4
{
   private final String ME = "HelloWorld4";
   private XmlBlasterConnection con = null;

   public HelloWorld4(final Global glob) {
      
      final LogChannel log = glob.getLog(null);

      try {
         con = new XmlBlasterConnection(glob);

         con.initFailSave(new I_ConnectionProblems() {
               
               public void reConnected() {
                  log.info(ME, "I_ConnectionProblems: We were lucky, reconnected to " + glob.getId());
                  //initClient();    // initialize subscription etc. again
                  try {
                     con.flushQueue();    // send all tailback messages
                     // con.resetQueue(); // or discard them (it is our choice)
                  } catch (XmlBlasterException e) {
                     log.error(ME, "Exception during reconnection recovery: " + e.reason);
                  }
               }

               public void lostConnection() {
                  log.warn(ME, "I_ConnectionProblems: Lost connection to " + glob.getId());
               }
            });

         ConnectQos qos = new ConnectQos(glob);
         ConnectReturnQos conRetQos = con.connect(qos, new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               if (!updateKey.getOid().equals("HelloWorld4"))
                  log.info(ME, "Reveiving asynchronous message '" + updateKey.getOid() + "' in default handler");
               else
                  log.error(ME, "Reveiving unexpected asynchronous message '" + updateKey.getOid() + "' in default handler");
               return "";
            }
         });  // Login to xmlBlaster, default handler for updates

         log.info(ME, "Connected to xmlBlaster.");

         SubscribeKeyWrapper sk = new SubscribeKeyWrapper("SomeOtherMessage");
         SubscribeQosWrapper sq = new SubscribeQosWrapper();
         String subId1 = con.subscribe(sk.toXml(), sq.toXml());

         sk = new SubscribeKeyWrapper("HelloWorld4");
         sq = new SubscribeQosWrapper();
         String subId2 = con.subscribe(sk.toXml(), sq.toXml(), new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               if (updateKey.getOid().equals("HelloWorld4"))
                  log.info(ME, "Reveiving asynchronous message '" + updateKey.getOid() + "' in HelloWorld4 handler");
               else
                  log.error(ME, "Reveiving unexpected asynchronous message '" + updateKey.getOid() + "' in HelloWorld4 handler");
               return "";
            }
         });  // subscribe with our specific update handler


         PublishKeyWrapper pk = new PublishKeyWrapper("HelloWorld4", "text/plain", "1.0");
         PublishQosWrapper pq = new PublishQosWrapper();
         MessageUnit msgUnit = new MessageUnit(pk.toXml(), "Hi".getBytes(), pq.toXml());
         String retQos = con.publish(msgUnit);
         log.info(ME, "Published message '" + pk.getOid() + "'");

         pk = new PublishKeyWrapper("SomeOtherMessage", "text/plain", "1.0");
         pq = new PublishQosWrapper();
         msgUnit = new MessageUnit(pk.toXml(), "Ho".getBytes(), pq.toXml());
         retQos = con.publish(msgUnit);
         log.info(ME, "Published message '" + pk.getOid() + "'");
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Houston, we have a problem: " + e.toString());
      }
      finally {
         // Wait a second for messages to arrive before we logout
         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {}
         log.info(ME, "Success, hit a key to exit");
         try { System.in.read(); } catch(java.io.IOException e) {}
         
         if (con != null) {
            try {
               EraseQosWrapper eq = new EraseQosWrapper();

               EraseKeyWrapper ek = new EraseKeyWrapper("HelloWorld4");
               con.erase(ek.toXml(), eq.toXml());
               
               ek = new EraseKeyWrapper("SomeOtherMessage");
               con.erase(ek.toXml(), eq.toXml());
            }
            catch (XmlBlasterException e) {
               log.error(ME, "Houston, we have a problem: " + e.toString());
            }
            
            con.disconnect(new DisconnectQos());
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
         Log.usage();
         Log.exit("", "Example: java HelloWorld4 -loginName Jeff\n");
      }

      new HelloWorld4(glob);
   }
}
