/*------------------------------------------------------------------------------
Name:      ClientSubEmail.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientSubEmail.java,v 1.2 2001/11/26 09:26:05 ruff Exp $
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
import org.xmlBlaster.engine.helper.CallbackAddress;
                                                       

/**
 * This client tests the method subscribe() with a later publish() with XPath query.<br />
 * The given EMAIL callback should receive an email as well.
 * <p>
 * TODO: email support is currently very simple (demo only)
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java -cp ../../lib/xmlBlaster.jar javaclients.ClientSubEmail -email ruff@swand.lake.de
 *
 *    java javaclients.ClientSubEmail -help
 * </pre>
 * Activate the email callback driver in xmlBlaster.properies first,
 * for example:
 * <pre>
 *    Protocol.CallbackDrivers=IOR:org.xmlBlaster.protocol.corba.CallbackCorbaDriver,\
 *                             EMAIL:org.xmlBlaster.protocol.email.CallbackEmailDriver
 * 
 *    EmailDriver.smtpHost=192.1.1.1
 *    EmailDriver.from=xmlblast@localhost
 * </pre>
 * @see org.xmlBlaster.protocol.email.CallbackEmailDriver
 */
public class ClientSubEmail implements I_Callback
{
   private static String ME = "ClientSubEmail";
   private int numReceived = 0;         // error checking


   public ClientSubEmail(String args[])
   {
      initArgs(args); // Initialize command line argument handling (this is optional)

      try {
         // check if parameter -name <userName> is given at startup of client
         String loginName = Args.getArg(args, "-name", ME);
         String passwd = Args.getArg(args, "-passwd", "secret");
         ConnectQos loginQos = new ConnectQos(); // creates "<qos></qos>" string
         loginQos.addCallbackAddress(new CallbackAddress("EMAIL", Args.getArg(args, "-email", "ruff@swand.lake.de")));

         XmlBlasterConnection blasterConnection = new XmlBlasterConnection(args);
         blasterConnection.login(loginName, passwd, loginQos, this);
         // Now we are connected to xmlBlaster MOM server.


         // Subscribe to messages with XPATH using some helper classes
         {
            Log.info(ME, "Subscribing using XPath syntax ...");

            // SubscribeKeyWrapper helps us to create this string:
            //   "<key oid='' queryType='XPATH'>" +
            //   "   /xmlBlaster/key/ClientSubEmail-AGENT" +
            //   "</key>";
            SubscribeKeyWrapper key = new SubscribeKeyWrapper("/xmlBlaster/key/DemoMail", "XPATH");

            // SubscribeKeyWrapper helps us to create "<qos></qos>":
            SubscribeQosWrapper qos = new SubscribeQosWrapper();

            try {
               blasterConnection.subscribe(key.toXml(), qos.toXml());
               Log.info(ME, "Subscribe done, there should be no Callback");
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
                            "<key oid='EDDI.RWY1.mail' contentMime='text/plain'>\n" +
                            "   <DemoMail>\n" +
                            "   </DemoMail>\n" +
                            "</key>";
            String content = Args.getArg(args, "-email.content", "Hello world");
            MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), "<qos></qos>");
            Log.info(ME, "Publishing ...");
            try {
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


         //----------- cleaning up .... erase() the previous message OID -------
         {
            String xmlKey = // optional: "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                            "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                            "</key>";
            String[] strArr = null;
            try {
               strArr = blasterConnection.erase(xmlKey, "<qos></qos>");
            } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
            if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");
         }

         blasterConnection.logout();

         // blasterConnection.getOrb().run(); // Usually your client won't exit after this, uncomment the run() method
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
      numReceived++;
      Log.info(ME, "Received asynchronous callback-update " + numReceived + " from xmlBlaster from publisher " + updateQoS.getSender() + ":");
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
         Log.plain("   -name               The login name [ClientSubEmail].");
         Log.plain("   -passwd             The login name [secret].");
         Log.plain("   -email              An email address to send updates [ruff@swand.lake.de].");
         Log.plain("   -email.content      The content of the email [Hello world].");
         Log.plain("NOTE:");
         Log.plain("   Activate the email callback plugin in xmlBlaster.properies first, for example:");
         Log.plain("   Protocol.CallbackDrivers=IOR:org.xmlBlaster.protocol.corba.CallbackCorbaDriver,\\");
         Log.plain("                            EMAIL:org.xmlBlaster.protocol.email.CallbackEmailDriver");
         Log.plain("   EmailDriver.smtpHost=localhost");
         Log.plain("   EmailDriver.from=xmlblast@localhost");
         XmlBlasterConnection.usage();
         Log.usage();
         Log.exit(ME, "Example: java javaclients.ClientSubEmail -name Jeff -email et@universe.xy\n");
      }
   }


   public static void main(String args[])
   {
      new ClientSubEmail(args);
      Log.exit(ClientSubEmail.ME, "Good bye");
   }
} // ClientSubEmail

