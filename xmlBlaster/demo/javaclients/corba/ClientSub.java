/*------------------------------------------------------------------------------
Name:      ClientSub.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientSub.java,v 1.1 2000/06/25 18:32:39 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients.corba;

import org.jutils.log.Log;
import org.jutils.init.Args;

import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.LoginQosWrapper;
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
 * This demo uses the CorbaConnection helper class, which hides the raw CORBA nastiness.<br />
 * CorbaConnections hides how to find the CORBA server (see CorbaConnection API).<br />
 * CorbaConnection installs a callback server (class DefaultCallback) for you and informs
 * you about asynchronous callbacks using the I_Callback interface (method update() see below).
 * <p>
 * If you want to know step by step what happens, study the ClientRaw example.
 * Here we use all available Java helper classes.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    ${JacORB_HOME}/bin/jaco javaclients.ClientSub
 *
 *    ${JacORB_HOME}/bin/jaco javaclients.ClientSub -name "Jeff"
 * </pre>
 */
public class ClientSub implements I_Callback
{
   private CorbaConnection corbaConnection = null;
   private static String ME = "ClientSub";
   private int numReceived = 0;         // error checking


   public ClientSub(String args[])
   {
      // Initialize command line argument handling (this is optional)
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.plain("\nAvailable options:");
         Log.plain("   -name               The login name [ClientSub].");
         CorbaConnection.usage();
         Log.plain("Example: jaco javaclients.ClientXml -name Jeff\n");
         Log.panic(ME, e.toString());
      }

      try {
         // check if parameter -name <userName> is given at startup of client
         String loginName = Args.getArg(args, "-name", ME);
         String passwd = Args.getArg(args, "-passwd", "secret");
         LoginQosWrapper loginQos = new LoginQosWrapper(); // creates "<qos></qos>" string

         CorbaConnection corbaConnection = new CorbaConnection(args);
         corbaConnection.login(loginName, passwd, loginQos, this);
         // Now we are connected to xmlBlaster MOM server.


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
               corbaConnection.subscribe(key.toXml(), qos.toXml());
               Log.info(ME, "Subscribe done, there should be no Callback");
            } catch(XmlBlasterException e) {
               Log.warning(ME, "XmlBlasterException: " + e.reason);
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
               publishOid = corbaConnection.publish(msgUnit);
               Log.info(ME, "Publishing done, returned oid=" + publishOid);
            } catch(XmlBlasterException e) {
               Log.warning(ME, "XmlBlasterException: " + e.reason);
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
               strArr = corbaConnection.erase(xmlKey, "<qos></qos>");
            } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
            if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");
         }

         corbaConnection.logout();

         // corbaConnection.getOrb().run(); // Usually your client won't exit after this, uncomment the run() method
      }
      catch (Exception e) {
          e.printStackTrace();
      }
   }


   /**
    * This is the callback method (I_Callback) invoked from CorbaConnection
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
      Log.plain("UpdateKey", updateKey.printOn().toString());
      Log.plain("content", (new String(content)).toString());
      Log.plain("UpdateQoS", updateQoS.printOn().toString());
      Log.info(ME, "Received message from publisher " + updateQoS.getSender());
   }


   public static void main(String args[])
   {
      new ClientSub(args);
      Log.exit(ClientSub.ME, "Good bye");
   }
} // ClientSub

