/*------------------------------------------------------------------------------
Name:      ClientSub.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientSub.java,v 1.33 2002/06/02 21:35:59 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.PublishRetQos;
import org.xmlBlaster.client.SubscribeKeyWrapper;
import org.xmlBlaster.client.SubscribeQosWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * This client tests the method subscribe() with a later publish() with XPath query.<br />
 * The subscribe() should be recognized for this later arriving publish().
 * <p>
 * This demo uses the XmlBlasterConnection helper class, which hides the raw
 * CORBA/RMI/XML-RPC nastiness.<br />
 * XmlBlasterConnections hides how to find the xmlBlaster server (see XmlBlasterConnection API).<br />
 * XmlBlasterConnection installs a callback server (for CORBA,RMI or XML-RPC) for you and informs
 * you about asynchronous callbacks using the I_Callback interface (method update() see below).
 * <p>
 * If you want to know step by step what happens with CORBA, study the corba/ClientRaw.java example.
 * Here we use all available Java helper classes.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java -cp ../../lib/xmlBlaster.jar javaclients.ClientSub
 *
 *    java javaclients.ClientSub -loginName Jeff -client.protocol RMI
 *
 *    java javaclients.ClientSub -help
 * </pre>
 */
public class ClientSub implements I_Callback
{
   private static String ME = "ClientSub";
   private Global glob = null;
   private int numReceived = 0;         // error checking
   public static long startTime;
   public static long elapsed;

   public ClientSub(Global glob) {
      this.glob = glob;
      try {
         XmlBlasterConnection blasterConnection = new XmlBlasterConnection(glob);
         blasterConnection.connect(null, this);
         // Now we are connected to xmlBlaster MOM server.

         int numTests = glob.getProperty().get("numTests", 1);
         for (int i=0; i<numTests; i++)
            sendSomeMessages(blasterConnection);

         /* // Run forever
         while (true) {
            try { Thread.currentThread().sleep(100000000L);
            } catch(InterruptedException e) { Log.warn(ME, "Caught exception: " + e.toString()); }
         }
         */

         blasterConnection.logout();
      }
      catch (Exception e) {
         Log.error(ME, "Client failed: " + e.toString());
         // e.printStackTrace();
      }
   }

   private void sendSomeMessages(XmlBlasterConnection blasterConnection)
   {
      String subscriptionId="";
      try {
         // Subscribe to messages with XPATH using some helper classes
         {
            Log.info(ME, "Subscribing using XPath syntax ...");

            // SubscribeKeyWrapper helps us to create this string:
            //   "<key oid='' queryType='XPATH'>" +
            //   "   /xmlBlaster/key/ClientSub-AGENT" +
            //   "</key>";
            SubscribeKeyWrapper key = new SubscribeKeyWrapper("/xmlBlaster/key/ClientSub-AGENT", "XPATH");

            // SubscribeKeyWrapper helps us to create "<qos></qos>":
            SubscribeQosWrapper qos = new SubscribeQosWrapper();

            try {
               subscriptionId = blasterConnection.subscribe(key.toXml(), qos.toXml());
               Log.info(ME, "Subscribe done, there should be no Callback, subcriptionId=" + subscriptionId);
            } catch(XmlBlasterException e) {
               Log.warn(ME, "XmlBlasterException: " + e.reason);
            }
         }

         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait a second

         if (numReceived == 0)
            Log.info(ME, "Success, no Callback for a simple subscribe without a publish");
         else
            Log.error(ME, "Got Callback, but didn't expect one after a simple subscribe without a publish");
         numReceived = 0;


         //----------- Construct a message and publish it ---------
         PublishRetQos pubRetQos = null;
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
            MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), "<qos></qos>");
            Log.info(ME, "Publishing ...");
            try {
               startTime = System.currentTimeMillis();
               pubRetQos = blasterConnection.publish(msgUnit);
               Log.info(ME, "Publishing done, returned oid=" + pubRetQos.getOid());
            } catch(XmlBlasterException e) {
               Log.panic(ME, "XmlBlasterException: " + e.reason);
            }
         }

         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait a second

         if (numReceived == 1)
            Log.info(ME, "Success, got Callback after publishing");
         else
            Log.error(ME, numReceived + " callbacks arrived, did expect one after a simple subscribe with a publish");
         numReceived = 0;


         //----------- cleaning up .... unSubscribe() the previous message OID -------
         {
            String xmlKey = "<key oid='" + subscriptionId + "'/>";
            String qos = "<qos></qos>";
            numReceived = 0;
            try {
               blasterConnection.unSubscribe(xmlKey, qos);
               Log.info(ME, "Success: UnSubscribe with " + subscriptionId + " done");
            } catch(XmlBlasterException e) {
               Log.warn(ME, "XmlBlasterException: " + e.reason);
            }
         }


         //----------- cleaning up .... erase() the previous message OID -------
         {
            String xmlKey = "<key oid='" + pubRetQos.getOid() + "' queryType='EXACT'/>";
            String[] strArr = new String[0];
            try {
               strArr = blasterConnection.erase(xmlKey, "<qos></qos>");
            } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
            if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");
         }
      }
      catch (Exception e) {
         Log.error(ME, "Client failed: " + e.toString());
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
    * @param qos         Quality of Service of the MessageUnit
    *
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      elapsed = System.currentTimeMillis() - startTime;
      numReceived++;
      Log.info(ME, "Received asynchronous callback-update " + numReceived + " with cbSessionId='" + cbSessionId + "' from xmlBlaster from publisher " + updateQos.getSender() + " (latency=" + elapsed + " milli seconds):");
      Log.plain("UpdateKey", updateKey.toXml());
      Log.plain("content", (new String(content)).toString());
      Log.plain("UpdateQos", updateQos.toXml());
      return "";
   }

   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         XmlBlasterConnection.usage();
         Log.usage();
         Log.info(ME, "Get help: java javaclients.ClientSub -help\n");
         Log.exit(ME, "Example: java javaclients.ClientSub -loginName Jeff\n");
      }
      new ClientSub(glob);
      Log.exit(ClientSub.ME, "Good bye");
   }
} // ClientSub

