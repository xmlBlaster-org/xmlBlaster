/*------------------------------------------------------------------------------
Name:      ClientXml.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientXml.java,v 1.17 2002/03/18 00:30:22 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients;

import org.xmlBlaster.util.Log;
import org.jutils.init.Args;
import org.jutils.time.StopWatch;

import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
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
 *    ${JacORB_HOME}/bin/jaco javaclients.ClientXml
 *
 *    ${JacORB_HOME}/bin/jaco javaclients.ClientXml -name "Jeff"
 * </pre>
 */
public class ClientXml implements I_Callback
{
   private static String ME = "Tim";

   public ClientXml(String args[])
   {
      // Initialize command line argument handling (this is optional)
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.plain("\nAvailable options:");
         Log.plain("   -name               The login name [" + ME + "].");
         Log.plain("   -passwd             The password [secret].");
         XmlBlasterConnection.usage();
         Log.usage();
         Log.plain("Example: jaco javaclients.ClientXml -name Jeff\n");
         Log.panic(ME, e.toString());
      }

      StopWatch stop = new StopWatch();
      try {
         // check if parameter -name <userName> is given at startup of client
         ME = Args.getArg(args, "-name", ME);
         String loginName = ME;

         //----------- Find orb ----------------------------------
         XmlBlasterConnection blasterConnection = new XmlBlasterConnection(args);

         //----------- Login to xmlBlaster -----------------------
         String passwd = Args.getArg(args, "-passwd", "secret");
         blasterConnection.login(loginName, passwd, null, this); // installs the Callback server as well!


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
               publishOid = blasterConnection.publish(msgUnit);
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
            publishOid = blasterConnection.subscribe(xmlKey, "<qos></qos>");
            Log.trace(ME, "Subscribed to '" + publishOid + "' ..." + stop.nice());
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
                  String str = blasterConnection.publish(msgUnit);
                  Log.trace(ME, "Publishing done" + stop.nice());
               } catch(XmlBlasterException e) {
                  Log.error(ME, "Publishing failed, XmlBlasterException: " + e.reason);
               }
            }
         }

         try { Thread.currentThread().sleep(2000); } catch( InterruptedException i) {} // Wait a second
         blasterConnection.logout();
      }
      catch (Exception e) {
          e.printStackTrace();
      }

      // blasterConnection.getOrb().run(); // Usually your client won't exit after this, uncomment the run() method
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQoS)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS)
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
