/*------------------------------------------------------------------------------
Name:      ClientSub.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id$
------------------------------------------------------------------------------*/
package javaclients;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.util.MsgUnit;


/**
 * This client tests the method subscribe() with a later publish() with XPath query.<br />
 * The subscribe() should be recognized for this later arriving publish().
 * <p>
 * This demo uses the I_XmlBlasterAccess helper class, which hides the raw
 * CORBA/RMI/XMLRPC nastiness.<br />
 * I_XmlBlasterAccesss hides how to find the xmlBlaster server (see I_XmlBlasterAccess API).<br />
 * I_XmlBlasterAccess installs a callback server (for CORBA,RMI or XMLRPC) for you and informs
 * you about asynchronous callbacks using the I_Callback interface (method update() see below).
 * <p>
 * If you want to know step by step what happens with CORBA, study the corba/ClientRaw.java example.
 * Here we use all available Java helper classes.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java -cp ../../lib/xmlBlaster.jar javaclients.ClientSub
 *
 *    java javaclients.ClientSub -session.name Jeff -dispatch/connection/protocol RMI
 *
 *    java javaclients.ClientSub -help
 * </pre>
 */
public class ClientSub implements I_Callback
{
   private static String ME = "ClientSub";
   private final Global glob;
   private static Logger log = Logger.getLogger(ClientSub.class.getName());
   private int numReceived = 0;         // error checking
   public static long startTime;
   public static long elapsed;

   public ClientSub(Global glob) {
      this.glob = glob;

      try {
         I_XmlBlasterAccess blasterConnection = glob.getXmlBlasterAccess();
         blasterConnection.connect(null, this);
         // Now we are connected to xmlBlaster MOM server.

         int numTests = glob.getProperty().get("numTests", 1);
         for (int i=0; i<numTests; i++)
            sendSomeMessages(blasterConnection);

         /* // Run forever
         while (true) {
            try { Thread.currentThread().sleep(100000000L);
            } catch(InterruptedException e) { log.warning("Caught exception: " + e.toString()); }
         }
         */

         blasterConnection.disconnect(null);
      }
      catch (Exception e) {
         log.severe("Client failed: " + e.toString());
         // e.printStackTrace();
      }
   }

   private void sendSomeMessages(I_XmlBlasterAccess blasterConnection)
   {
      String subscriptionId="";
      try {
         // Subscribe to messages with XPATH using some helper classes
         {
            log.info("Subscribing using XPath syntax ...");

            // SubscribeKey helps us to create this string:
            //   "<key oid='' queryType='XPATH'>" +
            //   "   /xmlBlaster/key/ClientSub-AGENT" +
            //   "</key>";
            SubscribeKey key = new SubscribeKey(glob, "/xmlBlaster/key/ClientSub-AGENT", "XPATH");

            // SubscribeKey helps us to create "<qos></qos>":
            SubscribeQos qos = new SubscribeQos(glob);

            try {
               subscriptionId = blasterConnection.subscribe(key.toXml(), qos.toXml()).getSubscriptionId();
               log.info("Subscribe done, there should be no Callback, subcriptionId=" + subscriptionId);
            } catch(XmlBlasterException e) {
               log.warning("XmlBlasterException: " + e.getMessage());
            }
         }

         try { Thread.sleep(1000); } catch( InterruptedException i) {} // Wait a second

         if (numReceived == 0)
            log.info("Success, no Callback for a simple subscribe without a publish");
         else
            log.severe("Got Callback, but didn't expect one after a simple subscribe without a publish");
         numReceived = 0;


         //----------- Construct a message and publish it ---------
         PublishReturnQos pubRetQos = null;
         {
            // This time, as an example, we don't use the wrapper helper classes,
            // and create the string 'by hand':
            String xmlKey = // optional: "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                            "<key oid='' contentMime='text/xml'>\n" +
                            "   <ClientSub-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                            "      <ClientSub-DRIVER id='FileProof' pollingFreq='10'>" +
                            "      </ClientSub-DRIVER>"+
                            "   </ClientSub-AGENT>" +
                            "</key>";
            String content = "Yeahh, i'm the new content";
            MsgUnit msgUnit = new MsgUnit(xmlKey, content.getBytes(), "<qos></qos>");
            log.info("Publishing ...");
            try {
               startTime = System.currentTimeMillis();
               pubRetQos = blasterConnection.publish(msgUnit);
               log.info("Publishing done, returned oid=" + pubRetQos.getKeyOid());
            } catch(XmlBlasterException e) {
               log.severe("XmlBlasterException: " + e.getMessage());
               System.exit(1);
            }
         }

         try { Thread.sleep(1000); } catch( InterruptedException i) {} // Wait a second

         if (numReceived == 1)
            log.info("Success, got Callback after publishing");
         else
            log.severe(numReceived + " callbacks arrived, did expect one after a simple subscribe with a publish");
         numReceived = 0;

         log.info("Hit a key to exit");
         try { System.in.read(); } catch(java.io.IOException e) {}

         //----------- cleaning up .... unSubscribe() the previous message OID -------
         {
            String xmlKey = "<key oid='" + subscriptionId + "'/>";
            String qos = "<qos></qos>";
            numReceived = 0;
            try {
               blasterConnection.unSubscribe(xmlKey, qos);
               log.info("Success: UnSubscribe with " + subscriptionId + " done");
            } catch(XmlBlasterException e) {
               log.warning("XmlBlasterException: " + e.getMessage());
            }
         }


         //----------- cleaning up .... erase() the previous message OID -------
         {
            String xmlKey = "<key oid='" + pubRetQos.getKeyOid() + "' queryType='EXACT'/>";
            try {
               EraseReturnQos[] strArr = blasterConnection.erase(xmlKey, "<qos></qos>");
               if (strArr.length != 1) log.severe("Erased " + strArr.length + " messages:");
            } catch(XmlBlasterException e) { log.severe("XmlBlasterException: " + e.getMessage()); }
         }
      }
      catch (Exception e) {
         log.severe("Client failed: " + e.toString());
         //e.printStackTrace();
      }
   }


   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    *
    * @param cbSessionId The session ID specified by the client which registered the callback
    * @param updateKey   The arrived key
    * @param content     The arrived message content
    * @param qos         Quality of Service of the MsgUnit
    *
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      elapsed = System.currentTimeMillis() - startTime;
      numReceived++;
      log.info("Received asynchronous callback-update " + numReceived + " with cbSessionId='" + cbSessionId + "' from xmlBlaster from publisher " + updateQos.getSender() + " (latency=" + elapsed + " milli seconds):");
      System.out.println(updateKey.toXml());
      System.out.println((new String(content)).toString());
      System.out.println(updateQos.toXml());
      return "";
   }

   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.out.println(glob.usage());
         System.out.println("Get help: java javaclients.ClientSub -help\n");
         System.out.println("Example: java javaclients.ClientSub -session.name Jeff\n");
         System.exit(1);
      }
      new ClientSub(glob);
   }
} // ClientSub

