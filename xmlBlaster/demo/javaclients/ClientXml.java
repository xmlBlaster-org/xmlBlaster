/*------------------------------------------------------------------------------
Name:      ClientXml.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientXml.java,v 1.22 2002/06/03 09:39:24 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.jutils.time.StopWatch;

import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * This client tests the method subscribe()/publish() with XML syntax key
 * and XPath query using the DefaultCallback implementation.
 * <p>
 * It is a nice example using the DefaultCallback implementation from XmlBlasterConnection.java
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

   public ClientXml(String args[])
   {
      // Initialize command line argument handling (this is optional)
      Global glob = new Global();
      if (glob.init(args) != 0) {
         XmlBlasterConnection.usage();
         Log.usage();
         Log.exit(ME,"Example: java javaclients.ClientXml -loginName Jeff\n");
      }

      StopWatch stop = new StopWatch();
      try {
         XmlBlasterConnection blasterConnection = new XmlBlasterConnection(glob);

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
            MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), "");
            Log.trace(ME, "Publishing ...");
            stop.restart();
            try {
               publishOid = blasterConnection.publish(msgUnit).getOid();
               Log.info(ME, "   Returned oid=" + publishOid);
               Log.trace(ME, "Publishing done" + stop.nice());
            } catch(XmlBlasterException e) {
               Log.error(ME, "Punlishing failed, XmlBlasterException: " + e.reason);
            }
         }


         //----------- Subscribe to the previous message OID -------
         Log.trace(ME, "Subscribing using the exact oid ...");
         xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                  "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                  "</key>";
         stop.restart();
         try {
            String subId = blasterConnection.subscribe(xmlKey, "<qos></qos>").getSubscriptionId();
            Log.trace(ME, "Subscribed to '" + subId + "' ..." + stop.nice());
         } catch(XmlBlasterException e) {
            Log.error(ME, "Subscribe failed, XmlBlasterException: " + e.reason);
         }

         try { Thread.currentThread().sleep(2000); } catch( InterruptedException i) {} // Wait a second


         //----------- Unsubscribe from the previous message --------
         Log.trace(ME, "Unsubscribe ...");
         stop.restart();
         try {
            blasterConnection.unSubscribe(xmlKey, "<qos></qos>");
            Log.info(ME, "Unsubscribe done" + stop.nice());
         } catch(XmlBlasterException e) {
            Log.error(ME, "Unsubscribe failed, XmlBlasterException: " + e.reason);
         }


         //----------- Subscribe to the previous message XPATH -------
         Log.trace(ME, "Subscribing using XPath syntax ...");
         xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                  "<key oid='' queryType='XPATH'>\n" +
                  "/xmlBlaster/key/AGENT" +
                  "</key>";
         stop.restart();
         try {
            blasterConnection.subscribe(xmlKey, "<qos></qos>");
            Log.trace(ME, "Subscribe done, there should be a Callback");
         } catch(XmlBlasterException e) {
            Log.error(ME, "subscribe failed, XmlBlasterException: " + e.reason);
         }

         try { Thread.currentThread().sleep(2000); } catch( InterruptedException i) {} // Wait a second

         Log.trace(ME, "Publishing 10 times ...");
         {
            for (int ii=0; ii<10; ii++) {
               //----------- Construct a message and publish it ---------
               String content = "<person><name>Castro</name><age>" + ii + "</age></person>";
               xmlKey = "<key oid='" + publishOid + "' contentMime='text/xml'>\n</key>";
               MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), "");
               Log.trace(ME, "Publishing ...");
               stop.restart();
               try {
                  String str = blasterConnection.publish(msgUnit).getOid();
                  Log.trace(ME, "Publishing done" + stop.nice());
               } catch(XmlBlasterException e) {
                  Log.error(ME, "Publishing failed, XmlBlasterException: " + e.reason);
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
      Log.info(ME, "Receiving update of message [" + updateKey.getUniqueKey() + "]");
      return "";
   }

   public static void main(String args[])
   {
      new ClientXml(args);
      Log.exit(ClientXml.ME, "Good bye");
   }
}
