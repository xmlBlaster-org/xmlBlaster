/*------------------------------------------------------------------------------
Name:      ClientXml.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id$
------------------------------------------------------------------------------*/
package javaclients;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.StopWatch;
import java.util.logging.Logger;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;


/**
 * This client tests the method subscribe()/publish() with XML syntax key
 * and XPath query using the DefaultCallback implementation.
 * <p>
 * It is a nice example using the DefaultCallback implementation from I_XmlBlasterAccess.java
 * which calls the update() method using I_Callback interface when messages arrive.
 * <p>
 * Have a look into the testsuite for other possibilities.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java javaclients.ClientXml
 *
 *    java javaclients.ClientXml -loginName "Jeff"
 * </pre>
 */
public class ClientXml implements I_Callback
{
   private static String ME = "ClientXml";
   private static Logger log = Logger.getLogger(ClientXml.class.getName());

   public ClientXml(String args[])
   {
      // Initialize command line argument handling (this is optional)
      Global glob = new Global();

      if (glob.init(args) != 0) {
         System.out.println(glob.usage());
         log.info("Example: java javaclients.ClientXml -loginName Jeff\n");
         System.exit(1);
      }

      StopWatch stop = new StopWatch();
      try {
         I_XmlBlasterAccess blasterConnection = glob.getXmlBlasterAccess();

         // Login and install the Callback server
         ConnectQos qos = new ConnectQos(glob);
         blasterConnection.connect(qos, this);


         String publishOid = "";
         String xmlKey = "<key oid='' contentMime='text/xml'>\n" +
                         "<AGENT id='192.168.124.10' subId='1' type='generic'>" +
                         "<DRIVER id='FileProof' pollingFreq='10'>" +
                         "</DRIVER>"+
                         "</AGENT>" +
                         "</key>";


         //----------- Construct a message and publish it ---------
         {
            String content = "<person><name>Ghandi</name></person>";
            MsgUnit msgUnit = new MsgUnit(xmlKey, content.getBytes(), "");
            log.fine("Publishing ...");
            stop.restart();
            try {
               publishOid = blasterConnection.publish(msgUnit).getKeyOid();
               log.info("   Returned oid=" + publishOid);
               log.fine("Publishing done" + stop.nice());
            } catch(XmlBlasterException e) {
               log.severe("Punlishing failed, XmlBlasterException: " + e.getMessage());
            }
         }


         //----------- Subscribe to the previous message OID -------
         log.fine("Subscribing using the exact oid ...");
         xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                  "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                  "</key>";
         stop.restart();
         try {
            String subId = blasterConnection.subscribe(xmlKey, "<qos></qos>").getSubscriptionId();
            log.fine("Subscribed to '" + subId + "' ..." + stop.nice());
         } catch(XmlBlasterException e) {
            log.severe("Subscribe failed, XmlBlasterException: " + e.getMessage());
         }

         try { Thread.currentThread().sleep(2000); } catch( InterruptedException i) {} // Wait a second


         //----------- Unsubscribe from the previous message --------
         log.fine("Unsubscribe ...");
         stop.restart();
         try {
            blasterConnection.unSubscribe(xmlKey, "<qos></qos>");
            log.info("Unsubscribe done" + stop.nice());
         } catch(XmlBlasterException e) {
            log.severe("Unsubscribe failed, XmlBlasterException: " + e.getMessage());
         }


         //----------- Subscribe to the previous message XPATH -------
         log.fine("Subscribing using XPath syntax ...");
         xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                  "<key oid='' queryType='XPATH'>\n" +
                  "/xmlBlaster/key/AGENT" +
                  "</key>";
         stop.restart();
         try {
            blasterConnection.subscribe(xmlKey, "<qos></qos>");
            log.fine("Subscribe done, there should be a Callback");
         } catch(XmlBlasterException e) {
            log.severe("subscribe failed, XmlBlasterException: " + e.getMessage());
         }

         try { Thread.currentThread().sleep(2000); } catch( InterruptedException i) {} // Wait a second

         log.fine("Publishing 10 times ...");
         {
            for (int ii=0; ii<10; ii++) {
               //----------- Construct a message and publish it ---------
               String content = "<person><name>Castro</name><age>" + ii + "</age></person>";
               xmlKey = "<key oid='" + publishOid + "' contentMime='text/xml'>\n</key>";
               MsgUnit msgUnit = new MsgUnit(xmlKey, content.getBytes(), "");
               log.fine("Publishing ...");
               stop.restart();
               try {
                  String str = blasterConnection.publish(msgUnit).getKeyOid();
                  log.fine("Publishing done" + stop.nice());
               } catch(XmlBlasterException e) {
                  log.severe("Publishing failed, XmlBlasterException: " + e.getMessage());
               }
            }
         }

         try { Thread.currentThread().sleep(2000); } catch( InterruptedException i) {} // Wait a second
         blasterConnection.disconnect(null);
      }
      catch (Exception e) {
          e.printStackTrace();
      }

      // blasterConnection.getOrb().run(); // Usually your client won't exit after this, uncomment the run() method
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info("Receiving update of message [" + updateKey.getOid() + "]");
      return "";
   }

   public static void main(String args[])
   {
      new ClientXml(args);
   }
}
