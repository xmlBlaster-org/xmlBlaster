// xmlBlaster/demo/HelloWorld4.java
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.error.I_MsgErrorInfo;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.GetReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;


/**
 * This client connects to xmlBlaster in failsafe mode and uses specific update handlers. 
 * <p />
 * In fail save mode the client will poll for the xmlBlaster server and
 * queue messages until the server is available.
 * We show all available control of a client in failsafe mode.
 * <p />
 * Invoke: java HelloWorld4 -session.name joe/2 -passwd secret
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class HelloWorld4
{
   private final Global glob;
   private static Logger log = Logger.getLogger(HelloWorld4.class.getName());
   private I_XmlBlasterAccess con = null;
   private ConnectReturnQos conRetQos = null;

   public HelloWorld4(final Global glob) {
      this.glob = glob;


      try {
         con = glob.getXmlBlasterAccess();

         // Do all client side error handling our self
         // this error handler is called when we are/were polling for the server:
         con.setClientErrorHandler(new I_MsgErrorHandler() {

               public void handleError(I_MsgErrorInfo msgErrorInfo) {
                  if (msgErrorInfo == null) return;
                  XmlBlasterException ex = msgErrorInfo.getXmlBlasterException();
                  if (ex.isUser()) {
                     log.severe("Connection failed: " + msgErrorInfo.getXmlBlasterException().getMessage());
                     if (msgErrorInfo.getDispatchManager() != null) {
                        msgErrorInfo.getDispatchManager().toDead(ConnectionStateEnum.UNDEF, msgErrorInfo.getXmlBlasterException());
                        if (msgErrorInfo.getQueue() != null)
                           msgErrorInfo.getQueue().clear();
                        msgErrorInfo.getDispatchManager().shutdown();
                        return;
                     }
                  }
                  MsgQueueEntry[] entries = msgErrorInfo.getMsgQueueEntries();
                  for (int i=0; i<entries.length; i++)
                     log.severe("Message '" + entries[i].getEmbeddedType() + "' '" +
                                   entries[i].getLogId() + "' is lost: " + msgErrorInfo.getXmlBlasterException().getMessage());
                  if (msgErrorInfo.getQueue() != null)
                     msgErrorInfo.getQueue().clear();
               }

               public void handleErrorSync(I_MsgErrorInfo msgErrorInfo) throws XmlBlasterException {
                  if (msgErrorInfo.getXmlBlasterException().isCommunication()) {
                     handleError(msgErrorInfo);
                     return;
                  }
                  throw msgErrorInfo.getXmlBlasterException(); // Throw back to client
               }

               public void shutdown() {
               }
            }
         );


         // Listen on status changes of our connection to xmlBlaster
         con.registerConnectionListener(new I_ConnectionStateListener() {
               
               public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
                  log.info("I_ConnectionStateListener.reachedAlive(): We were lucky, connected to " +
                           connection.getConnectReturnQos().getSessionName());
                  if (connection.getQueue().getNumOfEntries() > 0) {
                     log.info("I_ConnectionStateListener.reachedAlive(): Queue contains " +
                              connection.getQueue().getNumOfEntries() + " messages: " +
                              connection.getQueue().toXml(""));
                     try {
                        java.util.ArrayList list = connection.getQueue().peek(-1, -1);
                        for (int i=0; i<list.size(); i++) {
                           log.info(((MsgQueueEntry)list.get(i)).toXml());
                        }
                        /*
                        MsgQueueEntry entry = (MsgQueueEntry)connection.getQueue().peek();
                        log.info("I_ConnectionStateListener.reachedAlive(): Discarding messages from queue");
                        connection.getQueue().clear(); // e.g. discard all msgs (it is our choice)
                        if (MethodName.CONNECT == entry.getMethodName()) {
                           connection.getQueue().put(entry, false);
                        }
                        */
                     }
                     catch (XmlBlasterException e) {
                     }
                  }
                  if (!connection.getConnectReturnQos().isReconnected()) {
                     log.info("I_ConnectionStateListener.reachedAlive(): New server instance found");
                     if (connection.isConnected())
                        initClient();    // initialize subscription etc. again
                  }
                  else {
                     log.info("I_ConnectionStateListener.reachedAlive(): Same server instance found");
                  }
               }

               public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
                  log.warning("I_ConnectionStateListener.reachedPolling(): No connection to " + glob.getId() + ", we are polling ...");
                  if (!connection.isConnected())
                     initClient();
               }

               public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
                  log.severe("I_ConnectionStateListener.reachedDead(): Connection to " + glob.getId() + " is dead, good bye");
                  System.exit(1);
               }
            });


         ConnectQos qos = new ConnectQos(glob);
         conRetQos = con.connect(qos, new I_Callback() {

            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               if (log.isLoggable(Level.FINEST)) log.finest("UpdateKey.toString()=" + updateKey.toString() +
                                          "UpdateQos.toString()=" + updateQos.toString());
               if (updateKey.isInternal()) {
                  log.severe("Receiving unexpected asynchronous internal message '" + updateKey.getOid() +
                                "' in default handler");
                  return "";
               }
               if (updateQos.isErased()) {
                  log.info("Message '" + updateKey.getOid() + "' is erased");
                  return "";
               }
               if (updateKey.getOid().equals("Banking"))
                  log.info("Receiving asynchronous message '" + updateKey.getOid() +
                               "' state=" + updateQos.getState() + " in default handler");
               else
                  log.severe("Receiving unexpected asynchronous message '" + updateKey.getOid() +
                                   "' in default handler");
               return "";
            }

         });  // Login to xmlBlaster, default handler for updates


         if (con.isAlive())
            log.info("Connected as " + qos.getUserId() + " to xmlBlaster, your public session ID is " + conRetQos.getSessionName());
         else
            log.info("Not connected to xmlBlaster, proceeding in fail save mode ...");

         while (true) {
            // Wait a second for messages to arrive before we logout
            try { Thread.sleep(1000); } catch( InterruptedException i) {}
            int key = Global.waitOnKeyboardHit("Hit a key: 'p'=publish, 'g'=get, 'q'=exit");
            if (key == 'p') {
               publishMessages();
               continue;
            }
            else if (key == 'g') {
               GetKey gk = new GetKey(glob, "Banking");
               GetQos gq = new GetQos(glob);
               try {
                  MsgUnit[] msgs = con.get(gk, gq);
                  if (msgs.length > 0) {
                     GetReturnQos grq = new GetReturnQos(glob, msgs[0].getQos());
                     log.info("Accessed xmlBlaster message with content '" + new String(msgs[0].getContent()) +
                               "' and status=" + grq.getState());
                  }
                  else {
                     log.info("No message matched get() call on " + gk.getOid());
                  }
               }
               catch (XmlBlasterException e) {
                  log.warning("get() failed:" + e.getMessage());
               }
               continue;
            }
            else if (key == 'q') {
               break;
            }
         }
      }
      catch (XmlBlasterException e) {
         log.severe("Houston, we have a problem: " + e.getMessage());
      }
      finally {
         if (con != null) {
            if (con.isConnected()) {
               try {
                  EraseQos eq = new EraseQos(glob);

                  EraseKey ek = new EraseKey(glob, "HelloWorld4");
                  con.erase(ek, eq);
                  
                  ek = new EraseKey(glob, "Banking");
                  con.erase(ek, eq);

                  // Wait on message erase events
                  try { Thread.sleep(1000); } catch( InterruptedException i) {}
               }
               catch (XmlBlasterException e) {
                  log.severe("Houston, we have a problem: " + e.getMessage());
                  e.printStackTrace();
               }
            }
            con.disconnect(new DisconnectQos(glob));
         }
      }
   }

   /**
    * We subscribe to some messages on startup or on reconnect
    * to a new server instance. 
    */
   private void initClient() {
      log.info("Entering initClient() and doing subscribes");
      try {   
         SubscribeKey sk = new SubscribeKey(glob, "Banking");
         SubscribeQos sq = new SubscribeQos(glob);
         sq.setWantInitialUpdate(false);
         con.subscribe(sk, sq);


         sk = new SubscribeKey(glob, "HelloWorld4");
         sq = new SubscribeQos(glob);
         sq.setWantInitialUpdate(false);
         con.subscribe(sk, sq, new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               if (updateKey.getOid().equals("HelloWorld4"))
                  log.info("Receiving asynchronous message '" + updateKey.getOid() +
                           "' state=" + updateQos.getState() + " in HelloWorld4 handler");
               else
                  log.severe("Receiving unexpected asynchronous message '" + updateKey.getOid() +
                            "' with state '" + updateQos.getState() + "' in HelloWorld4 handler");
               return "";
            }
         });  // subscribe with our specific update handler
      }
      catch (XmlBlasterException e) {
         log.severe("Client initialization failed: " + e.getMessage());
      }
   }

   /**
    * We publish some messages. 
    */
   private void publishMessages() {
      try {

         PublishKey pk = new PublishKey(glob, "HelloWorld4", "text/plain", "1.0");
         PublishQos pq = new PublishQos(glob);
         MsgUnit msgUnit = new MsgUnit(pk, "Hi", pq);
         con.publish(msgUnit);
         log.info("Published message '" + pk.getOid() + "'");


         pk = new PublishKey(glob, "Banking", "text/plain", "1.0");
         pk.setClientTags("<Account><withdraw/></Account>"); // Add banking specific meta data
         pq = new PublishQos(glob);
         msgUnit = new MsgUnit(pk, "Ho".getBytes(), pq);
         con.publish(msgUnit);
         log.info("Published message '" + pk.getOid() + "'");

      }
      catch (XmlBlasterException e) {
         log.severe("Houston, we have a problem: " + e.getMessage());
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
         System.out.println(glob.usage());
         System.err.println("Example: java HelloWorld4 -session.name Jeff\n");
         System.exit(1);
      }

      new HelloWorld4(glob);
   }
}
