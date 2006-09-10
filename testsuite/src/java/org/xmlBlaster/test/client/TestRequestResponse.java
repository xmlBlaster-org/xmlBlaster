/**
 * 
 */
package org.xmlBlaster.test.client;

import java.util.logging.Logger;

import junit.framework.TestCase;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.test.Util;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.address.Destination;

/**
 * Testing XmlBlasterAccess.request() method.
 * <br /> 
 * We send a request and expect a response.
 * @author Marcel Ruff
 */
public class TestRequestResponse extends TestCase {
   private static Logger log = Logger.getLogger(TestRequestResponse.class.getName());

   private Global glob;
   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 9583;
   private boolean startEmbedded = true;
   
   private I_XmlBlasterAccess sender = null;
   private final String senderName = "TheDesperate";
   private I_XmlBlasterAccess receiver = null;
   private final String receiverName = "TheKnowing";

   public void testRequestResponse() {
      Global glob = Global.instance();
      
      try {

         {  // setup the sender client ...
            sender = glob.getXmlBlasterAccess();

            ConnectQos qos = new ConnectQos(sender.getGlobal(), senderName, "secret");
            ConnectReturnQos conRetQos = sender.connect(qos, null); // Login to xmlBlaster

            log.info("Sender connected to xmlBlaster " + conRetQos.getSessionName().getRelativeName());
         }


         {  // setup the receiver client which processes the request (usually another process) ...
            Global globReceiver = glob.getClone(null);
            receiver = globReceiver.getXmlBlasterAccess();

            ConnectQos qos = new ConnectQos(receiver.getGlobal(), receiverName, "secret");
            ConnectReturnQos conRetQos = receiver.connect(qos, new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.info(receiverName+": Receiving asynchronous message '" + updateKey.getOid() + "' in receiver default handler");
                  log.info(receiverName+": Received: " + updateKey.toXml() + "\n <content>" + new String(content) + "</content>" + updateQos.toXml());

                  if (updateKey.isInternal()) return "";
                  if (updateQos.isErased()) return "";
                  try {
                     String tempTopicOid = updateQos.getClientProperty(Constants.JMS_REPLY_TO, ""); // __jms:JMSReplyTo
                     log.info(receiverName+": Got request, using topic '" + tempTopicOid + "' for response");

                     // Send reply back ...
                     PublishKey pk = new PublishKey(receiver.getGlobal(), tempTopicOid, "text/plain", "1.0");
                     PublishQos pq = new PublishQos(receiver.getGlobal());
                     MsgUnit msgUnit = new MsgUnit(pk, "On doubt no ultimate truth, my dear.", pq);
                     //try { Thread.sleep(8000); } catch (InterruptedException e) { e.printStackTrace(); }
                     PublishReturnQos retQos = receiver.publish(msgUnit);
                     log.info(receiverName+": Published reply message using temporary topic " + retQos.getKeyOid());
                  }
                  catch (XmlBlasterException e) {
                     log.severe(receiverName+": Sending reply to " + updateQos.getSender() + " failed: " + e.getMessage());
                  }
                  return "";
               }
            });  // Login to xmlBlaster, default handler for updates

            log.info("Receiver connected to xmlBlaster " + conRetQos.getSessionName().getRelativeName());
         }

         // Send a message to 'receiver' and block for the reply
         PublishKey pk = new PublishKey(sender.getGlobal(), "requestForEnlightenment");
         PublishQos pq = new PublishQos(sender.getGlobal());
         pq.addDestination(new Destination(new SessionName(sender.getGlobal(), receiverName)));
         MsgUnit msgUnit = new MsgUnit(pk, "Tell me the truth!", pq);
         MsgUnit[] replies = sender.request(msgUnit, 3000, 1);
         assertEquals(senderName+": Missing reply message.", 1, replies.length);
         log.info(senderName+": Got " + replies.length + " reply :\n" + replies[0].toXml());
      }
      catch (XmlBlasterException e) {
         fail("We have a problem: " + e.getMessage());
      }
      finally {
         if (sender != null && sender.isConnected()) { sender.disconnect(new DisconnectQos(sender.getGlobal())); }
         if (receiver != null && receiver.isConnected()) { receiver.disconnect(new DisconnectQos(receiver.getGlobal())); }
      }
   }

   /**
    * @param arg0
    */
   public TestRequestResponse(String arg0) {
      super(arg0);
   }

   /* (non-Javadoc)
    * @see junit.framework.TestCase#setUp()
    */
   protected void setUp() throws Exception {
      super.setUp();
      this.glob = Global.instance();
      this.startEmbedded = this.glob.getProperty().get("startEmbedded", this.startEmbedded);
      if (this.startEmbedded) {
         glob.init(Util.getOtherServerPorts(serverPort));
         String[] args = { };
         glob.init(args);
         this.serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
         log.info("XmlBlaster is ready for testing");
      }

   }

   /* (non-Javadoc)
    * @see junit.framework.TestCase#tearDown()
    */
   protected void tearDown() throws Exception {
      super.tearDown();
      if (this.startEmbedded) {
         try { Thread.sleep(500L); } catch( InterruptedException i) {} // Wait some time
         EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
         this.serverThread = null;
      }

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }
}
