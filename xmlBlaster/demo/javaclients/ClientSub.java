/*------------------------------------------------------------------------------
Name:      ClientSub.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientSub.java,v 1.25 2002/03/13 16:41:05 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients;

import org.xmlBlaster.util.Log;
import org.jutils.init.Args;

import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.client.SubscribeKeyWrapper;
import org.xmlBlaster.client.SubscribeQosWrapper;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
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
 *    java javaclients.ClientSub -name Jeff -client.protocol RMI
 *
 *    java javaclients.ClientSub -help
 * </pre>
 */
public class ClientSub implements I_Callback
{
   private static String ME = "ClientSub";
   private int numReceived = 0;         // error checking
   public static long startTime;
   public static long elapsed;

   public ClientSub(String args[])
   {
      initArgs(args); // Initialize command line argument handling (this is optional)

      try {
         // check if parameter -name <userName> is given at startup of client
         String loginName = Args.getArg(args, "-name", ME);
         String passwd = Args.getArg(args, "-passwd", "secret");
         ConnectQos loginQos = new ConnectQos(); // creates "<qos></qos>" string

         XmlBlasterConnection blasterConnection = new XmlBlasterConnection(args);
         blasterConnection.login(loginName, passwd, loginQos, this);
         // Now we are connected to xmlBlaster MOM server.

         int numTests = Args.getArg(args, "-numTests", 1);
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
         String publishOid = "";
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
               publishOid = blasterConnection.publish(msgUnit);
               Log.info(ME, "Publishing done, returned oid=" + publishOid);
            } catch(XmlBlasterException e) {
               Log.warn(ME, "XmlBlasterException: " + e.reason);
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
            String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'/>";
            String[] strArr = null;
            try {
               strArr = blasterConnection.erase(xmlKey, "<qos></qos>");
            } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
            if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");
         }
      }
      catch (Exception e) {
         Log.error(ME, "Client failed: " + e.toString());
         // e.printStackTrace();
      }
   }


   /**
    * This is the callback method (I_Callback) invoked from XmlBlasterConnection
    * informing the client in an asynchronous mode about a new message.
    * <p />
    * The raw CORBA-BlasterCallback.update() is unpacked and for each arrived message
    * this update is called.
    *
    * @param loginName The name to whom the callback belongs
    * @param updateKey The arrived key
    * @param content   The arrived message content
    * @param qos       Quality of Service of the MessageUnit
    */
   public void update(String loginName, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS)
   {
      elapsed = System.currentTimeMillis() - startTime;
      numReceived++;
      Log.info(ME, "Received asynchronous callback-update " + numReceived + " from xmlBlaster from publisher " + updateQoS.getSender() + " (latency=" + elapsed + "milli seconds):");
      Log.plain("UpdateKey", updateKey.toXml());
      Log.plain("content", (new String(content)).toString());
      Log.plain("UpdateQoS", updateQoS.toXml());
   }


   /**
    * Initialize command line argument handling (this is optional)
    */
   private void initArgs(String args[])
   {
      boolean showUsage = false;
      try {
         showUsage = XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         showUsage = true;
         Log.error(ME, e.toString());
      }
      if (showUsage) {
         Log.plain("\nAvailable options:");
         Log.plain("   -name               The login name [ClientSub].");
         Log.plain("   -passwd             The login name [secret].");
         XmlBlasterConnection.usage();
         Log.usage();
         Log.exit(ME, "Example: java javaclients.ClientSub -name Jeff\n");
      }
   }


   public static void main(String args[])
   {
      new ClientSub(args);
      Log.exit(ClientSub.ME, "Good bye");
   }
} // ClientSub

