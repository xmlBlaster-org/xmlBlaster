/*
Hi,

Here's another volatile bug, which may be related to the earlier one
that David posted.  Here's the scenario:

-One publisher publishing messages.  
-More than three consumers of messages (all listening on the same XPATH)
-The messages are all volatile, non-persistent.
-This all occurs on the same xmlblaster connection.

Problem:
-Not all the subscribers are getting the message, *AND* there's an
erroneous message sent twice to a subscriber (there should be one each
in the output below).  The *NUMBER* of messages appears to always be
correct, but the receivers get messed up.  (The "MessageEater-1" never
received an update.).


Here's the output from the attached program which reproduces the error:
Subscribing using XPath syntax ...
Subscribe done:MessageEater-0  subcriptionId=__subId:XPATH8
Subscribe done:MessageEater-1  subcriptionId=__subId:XPATH9
Subscribe done:MessageEater-2  subcriptionId=__subId:XPATH10
Publishing ...
MessageEater-0:Received asynchronous callback-update :published message
number:0
Publishing done, returned oid=http_10_0_1_157_3412-1036011675905-8
MessageEater-2:Received asynchronous callback-update :published message
number:0
MessageEater-2:Received asynchronous callback-update :published message
number:0


I'm fairly sure that it isn't the client side of the equation causing
this right now, and it appears to be related to a threading issue in the
server.

Having traced through the server as this runs, I have also noticed
something strange, which I don't quite understand.  When  the one
publish runs through the server, the server makes two passes at the
publish, publishing first to (apparently) just one subscriber, and then
again for the remaining two (which end up being the same subscriber as
above).

I'm going to keep digging, but as usual, any help appreciated.

Russ





*/
package javaclients;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.util.MsgUnit;

public class VolatileTest
{
   private static String ME = "VolatileTest";
   private final Global glob;
   private final LogChannel log;
   private int numReceived = 0;         // error checking
   public static long startTime;
   public static long elapsed;
   public static final int NUM_LISTENERS=2;


        public class MessageEater implements I_Callback
        {
                String _name;
                public MessageEater(String name)
                {
                        _name = name;
                }

                public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
                {
                        elapsed = System.currentTimeMillis() - startTime;
                        numReceived++;
                        log.info(ME, _name+":Received asynchronous callback-update :"+new String(content));
                        //log.info(ME, _name+":Received asynchronous callback-update " + numReceived + " with cbSessionId='" + cbSessionId + "' from xmlBlaster from publisher " + updateQos.getSender() + " (latency=" + elapsed + " milli seconds):");
                        //log.plain(_name+":UpdateKey", updateKey.toXml());
                        //log.plain(_name+":content", (new String(content)).toString());
                        //log.plain(_name+":UpdateQos", updateQos.toXml());
                        return "";
                }
        }


   public VolatileTest(Global glob) {
      this.glob = glob;
      this.log = glob.getLog(null);
      try {
         XmlBlasterConnection blasterConnection = new XmlBlasterConnection(glob);
         blasterConnection.connect(new ConnectQos(glob),new MessageEater("subscriber"));
         // Now we are connected to xmlBlaster MOM server.

         int numTests = glob.getProperty().get("numTests", 1);
                 int numListeners = glob.getProperty().get("numListeners", 1);

                createSubscribers(blasterConnection,numListeners);

         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait a second

         for (int i=0; i<numTests; i++){
                        String message ="published message number:"+i;
            sendSomeMessages(blasterConnection,message);
                 }

                try { 
                        Thread.currentThread().sleep(100000000L);
        } 
                catch(InterruptedException e) 
                { 
                        log.warn(ME, "Caught exception: " + e.toString()); 
                }

         blasterConnection.disconnect(null);
      }
      catch (Exception e) {
         log.error(ME, "Client failed: " + e.toString());
         // e.printStackTrace();
      }
   }

   private void createSubscribers(XmlBlasterConnection blasterConnection, int numListeners)
   {
        log.info(ME, "Subscribing using XPath syntax ...");

         // SubscribeKey helps us to create this string:
         //   "<key oid='' queryType='XPATH'>" +
         //   "   /xmlBlaster/key/VolatileTest-AGENT" +
         //   "</key>";
                try {
         SubscribeQos qos = new SubscribeQos(glob);
         SubscribeKey key = new SubscribeKey(glob, "/xmlBlaster/key/VolatileTest-AGENT", "XPATH");

                 for (int i=0; i< numListeners;i++){
                                String eaterName = "MessageEater-"+i;
                                MessageEater me = new MessageEater(eaterName);
                                String subscriptionId = blasterConnection.subscribe(key.toXml(), qos.toXml(),me).getSubscriptionId();
                                log.info(ME, "Subscribe done:"+eaterName+"  subcriptionId=" + subscriptionId);
                        } 
                 }
                catch(XmlBlasterException e) {
                        log.warn(ME, "XmlBlasterException: " + e.getMessage());
                }
   }

   private void sendSomeMessages(XmlBlasterConnection blasterConnection, String message)
   {
      String subscriptionId="";
      try {

         //----------- Construct a message and publish it ---------
         PublishReturnQos pubRetQos = null;
         {
            // This time, as an example, we don't use the wrapper helper classes,
            // and create the string 'by hand':
            String xmlKey = // optional: "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                            "<key oid='' contentMime='text/xml'>\n" +
                            "   <VolatileTest-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                            "      <VolatileTest-DRIVER id='FileProof' pollingFreq='10'>" +
                            "      </VolatileTest-DRIVER>"+
                            "   </VolatileTest-AGENT>" +
                            "</key>";
            String content = message;
                        PublishQos pqos = new PublishQos(glob);       
                        pqos.setPersistent(false);
                        pqos.setVolatile(true);
            MsgUnit msgUnit = new MsgUnit(xmlKey, content.getBytes(), pqos.toXml());
            log.info(ME, "Publishing ...");
            try {
               startTime = System.currentTimeMillis();
               pubRetQos = blasterConnection.publish(msgUnit);
               log.info(ME, "Publishing done, returned oid=" + pubRetQos.getKeyOid());
            } catch(XmlBlasterException e) {
               log.error(ME, "XmlBlasterException: " + e.getMessage());
               System.exit(1);
            }
         }
      }
      catch (Exception e) {
         log.error(ME, "Client failed: " + e.toString());
         //e.printStackTrace();
      }
   }


   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         XmlBlasterConnection.usage();
         System.out.println("Get help: java javaclients.VolatileTest -help\n");
         System.out.println("Example: java javaclients.VolatileTest -session.name Jeff\n");
         System.exit(1);
      }
      new VolatileTest(glob);
   }
} // VolatileTest

