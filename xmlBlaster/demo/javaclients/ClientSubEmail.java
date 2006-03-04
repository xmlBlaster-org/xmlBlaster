/*------------------------------------------------------------------------------
Name:      ClientSubEmail.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id$
------------------------------------------------------------------------------*/
package javaclients;

import org.xmlBlaster.util.Global;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.jutils.init.Args;

import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.address.CallbackAddress;
                                                       

/**
 * This client tests the method subscribe() with a later publish() with XPath query.<br />
 * The given EMAIL callback should receive an email as well.
 * <p>
 * TODO: email support is currently very simple (demo only)
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java -cp ../../lib/xmlBlaster.jar javaclients.ClientSubEmail -email xmlBlaster@marcelruff.info
 *
 *    java javaclients.ClientSubEmail -help
 * </pre>
 * Activate the email callback driver in xmlBlaster.properies first,
 * for example:
 * <pre>
 *    CbProtocolPlugin[EMAIL][1.0]=org.xmlBlaster.protocol.email.CallbackEmailDriver
 * 
 *    EmailDriver.smtpHost=192.1.1.1
 *    EmailDriver.from=xmlblast@localhost
 * </pre>
 * The QoS on login looks typically like this:
 * <pre>
 *     &lt;qos>
 *        &lt;securityService type="simple" version="1.0">
 *          &lt;![CDATA[
 *            &lt;user>michele&lt;/user>
 *            &lt;passwd>secret&lt;/passwd>
 *          ]]>
 *        &lt;/securityService>
 *        &lt;callback type='EMAIL'>
 *           et@mars.universe
 *        &lt;/callback>
 *        &lt;callback type='EMAIL'>
 *           root@localhost
 *        &lt;/callback>
 *        &lt;callback type='EMAIL'>
 *           spam@xy.z
 *        &lt;/callback>
 *     &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.protocol.email.CallbackEmailDriver
 */
public class ClientSubEmail implements I_Callback
{
   private static String ME = "ClientSubEmail";
   private int numReceived = 0;         // error checking
   private static Logger log = Logger.getLogger(ClientSubEmail.class.getName());


   public ClientSubEmail(String args[])
   {
      Global glob = initArgs(args); // Initialize command line argument handling (this is optional)


      try {
         // check if parameter -loginName <userName> is given at startup of client
         String loginName = Args.getArg(args, "-loginName", ME);
         String passwd = Args.getArg(args, "-passwd", "secret");
         ConnectQos loginQos = new ConnectQos(glob, loginName, passwd); // creates "<qos></qos>" string

         CallbackAddress c = new CallbackAddress(glob, "EMAIL");
         c.setRawAddress(Args.getArg(args, "-email", "et@xyz.org"));
         loginQos.addCallbackAddress(c);

         c = new CallbackAddress(glob, "EMAIL");
         c.setRawAddress(Args.getArg(args, "-email2", "root@localhost"));
         loginQos.addCallbackAddress(c);

         c = new CallbackAddress(glob, "EMAIL");
         c.setRawAddress(Args.getArg(args, "-email3", "et@xyz.org"));
         loginQos.addCallbackAddress(c);

         I_XmlBlasterAccess blasterConnection = glob.getXmlBlasterAccess();
         blasterConnection.connect(loginQos, this);
         // Now we are connected to xmlBlaster MOM server.


         // Subscribe to messages with XPATH using some helper classes
         {
            log.info("Subscribing using XPath syntax ...");

            // SubscribeKey helps us to create this string:
            //   "<key oid='' queryType='XPATH'>" +
            //   "   /xmlBlaster/key/ClientSubEmail-AGENT" +
            //   "</key>";
            SubscribeKey key = new SubscribeKey(glob, "/xmlBlaster/key/DemoMail", "XPATH");

            // SubscribeKey helps us to create "<qos></qos>":
            SubscribeQos qos = new SubscribeQos(glob);

            try {
               blasterConnection.subscribe(key.toXml(), qos.toXml());
               log.info("Subscribe done, there should be no Callback");
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
            MsgUnit msgUnit = new MsgUnit(xmlKey, content.getBytes(), "<qos></qos>");
            log.info("Publishing ...");
            try {
               publishOid = blasterConnection.publish(msgUnit).getKeyOid();
               log.info("Publishing done, returned oid=" + publishOid);
            } catch(XmlBlasterException e) {
               log.warning("XmlBlasterException: " + e.getMessage());
            }
         }

         try { Thread.sleep(1000); } catch( InterruptedException i) {} // Wait a second

         if (numReceived == 1)
            log.info("Success, got Callback after publishing");
         else
            log.severe(numReceived + " callbacks arrived, did expect one after a simple subscribe with a publish");
         numReceived = 0;


         //----------- cleaning up .... erase() the previous message OID -------
         {
            String xmlKey = // optional: "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                            "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                            "</key>";
            try {
               EraseReturnQos[] arr = blasterConnection.erase(xmlKey, "<qos></qos>");
               if (arr.length != 1) log.severe("Erased " + arr.length + " messages:");
            } catch(XmlBlasterException e) { log.severe("XmlBlasterException: " + e.getMessage()); }
         }

         blasterConnection.disconnect(null);

         // blasterConnection.getOrb().run(); // Usually your client won't exit after this, uncomment the run() method
      }
      catch (Exception e) {
         log.severe("Client failed: " + e.toString());
         // e.printStackTrace();
      }
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      numReceived++;
      log.info("Received asynchronous callback-update " + numReceived + " from xmlBlaster from publisher " + updateQos.getSender() + ":");
      System.out.println(updateKey.toXml());
      System.out.println((new String(content)).toString());
      System.out.println(updateQos.toXml());
      return "";
   }

   /**
    * Initialize command line argument handling (this is optional)
    */
   private Global initArgs(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.out.println("\nAvailable options:");
         System.out.println("   -loginName          The login name [ClientSubEmail].");
         System.out.println("   -passwd             The login name [secret].");
         System.out.println("   -email              An email address to send updates [xmlBlaster@marcelruff.info].");
         System.out.println("   -email.content      The content of the email [Hello world].");
         System.out.println("NOTE:");
         System.out.println("   Activate the email callback plugin in xmlBlaster.properies first, for example:");
         System.out.println("   CbProtocolPlugin[EMAIL][1.0]=org.xmlBlaster.protocol.email.CallbackEmailDriver");
         System.out.println("   EmailDriver.smtpHost=localhost");
         System.out.println("   EmailDriver.from=xmlblast@localhost");
         System.out.println(glob.usage());
         log.info("Example: java javaclients.ClientSubEmail -loginName Jeff -email et@universe.xy\n");
         System.exit(1);
      }
      return glob;
   }

   public static void main(String args[]) {
      new ClientSubEmail(args);
   }
} // ClientSubEmail

