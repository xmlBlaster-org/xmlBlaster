/*------------------------------------------------------------------------------
Name:      ClientSub.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientSub.java,v 1.13 2000/06/18 15:21:57 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients;

import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.LoginQosWrapper;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.jutils.log.Log;
import org.jutils.init.Args;
import org.jutils.time.StopWatch;
import org.xmlBlaster.util.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnitContainer;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackOperations;


/**
 * This client tests the method subscribe() with a later publish() with XPath query.<br />
 * The subscribe() should be recognized for this later arriving publish().
 * <p>
 * It may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * This demo implements an own BlasterCallback implementation, see class SubCallback
 * in this file.<br />
 * You may use this as an example for your own callback implementations.<br />
 * Note that you could use the DefaultCallback implementation in CorbaConnection.java as well.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    ${JacORB_HOME}/bin/jaco javaclients.ClientSub
 *
 *    ${JacORB_HOME}/bin/jaco javaclients.ClientSub -name "Jeff"
 * </pre>
 */
public class ClientSub
{
   private Server xmlBlaster = null;
   private static String ME = "Tim";
   private int numReceived = 0;         // error checking


   /**
    */
   public ClientSub(String args[])
   {
      StopWatch stop = new StopWatch();
      try {
         // check if parameter -name <userName> is given at startup of client
         ME = Args.getArg(args, "-name", ME);
         String loginName = ME;

         //----------- Find orb ----------------------------------
         CorbaConnection corbaConnection = new CorbaConnection(args);

         //---------- Building a Callback server ----------------------
         BlasterCallback callback = corbaConnection.createCallbackServer(new SubCallback(ME, this));

         //----------- Login to xmlBlaster -----------------------
         CallbackAddress addr = new CallbackAddress("IOR", corbaConnection.getOrb().object_to_string(callback));
         LoginQosWrapper qos = new LoginQosWrapper(addr); // == "<qos><callback type='IOR'>IOR:00113220001...</callback></qos>";
         String passwd = "some";
         xmlBlaster = corbaConnection.login(loginName, passwd, qos);


         //----------- Subscribe to messages with XPATH -------
         {
            Log.trace(ME, "Subscribing using XPath syntax ...");
            String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                           "<key oid='' queryType='XPATH'>\n" +
                           "   /xmlBlaster/key/ClientSub-AGENT" +
                           "</key>";
            stop.restart();
            try {
               xmlBlaster.subscribe(xmlKey, "<qos></qos>");
               Log.trace(ME, "Subscribe done, there should be no Callback" + stop.nice());
            } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
               Log.warning(ME, "XmlBlasterException: " + e.reason);
            }
         }

         Util.delay(1000); // Wait some time ...
         if (numReceived == 0)
            Log.info(ME, "Success, no Callback for a simple subscribe without a publish");
         else
            Log.error(ME, "Got Callback, but didn't expect one after a simple subscribe without a publish");
         numReceived = 0;


         //----------- Construct a message and publish it ---------
         String publishOid = "";
         {
            String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                            "<key oid='' contentMime='text/xml'>\n" +
                            "   <ClientSub-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                            "      <ClientSub-DRIVER id='FileProof' pollingFreq='10'>" +
                            "      </ClientSub-DRIVER>"+
                            "   </ClientSub-AGENT>" +
                            "</key>";
            String content = "Yeahh, i'm the new content";
            MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes());
            Log.trace(ME, "Publishing ...");
            stop.restart();
            try {
               publishOid = xmlBlaster.publish(msgUnit, "<qos></qos>");
               Log.info(ME, "Publishing done, returned oid=" + publishOid + stop.nice());
            } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
               Log.warning(ME, "XmlBlasterException: " + e.reason);
            }
         }

         Util.delay(1000); // Wait some time ...
         if (numReceived == 1)
            Log.info(ME, "Success, got Callback after publishing");
         else
            Log.error(ME, numReceived + " callbacks arrived, did expect one after a simple subscribe with a publish");
         numReceived = 0;


         //----------- cleaning up .... erase() the previous message OID -------
         {
            String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                            "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                            "</key>";
            String[] strArr = null;
            try {
               strArr = xmlBlaster.erase(xmlKey, "<qos></qos>");
            } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
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
    * The SubCallback.update calls this method, to allow some error checking
    */
   public void update(MessageUnit[] msgUnitArr, String[] qos_literal_Arr)
   {
      if (msgUnitArr.length != 0)
         numReceived += msgUnitArr.length;
      else
         numReceived = -1;       // error


      for (int ii=0; ii<msgUnitArr.length; ii++) {
         MessageUnit msgUnit = msgUnitArr[ii];
         UpdateKey updateKey = null;
         UpdateQoS updateQoS = null;
         byte[] content = msgUnit.content;
         try {
            updateKey = new UpdateKey();
            updateKey.init(msgUnit.xmlKey);
            updateQoS = new UpdateQoS(qos_literal_Arr[ii]);
         } catch (XmlBlasterException e) {
            Log.error(ME, e.reason);
         }

         // Now we know all about the received message, dump it or do some checks
         Log.plain("UpdateKey", updateKey.printOn().toString());
         Log.plain("content", (new String(content)).toString());
         Log.plain("UpdateQoS", updateQoS.printOn().toString());
         Log.info(ME, "Received message from publisher " + updateQoS.getSender());
      }
   }


   public static void main(String args[])
   {
      Log.setLogLevel(args);
      new ClientSub(args);
      Log.exit(ClientSub.ME, "Good bye");
   }
} // ClientSub



/**
 * Example for a callback implementation, used by the demo ClientSub.
 */
class SubCallback implements BlasterCallbackOperations
{
   private final String ME;
   private final ClientSub boss;

   /**
    * Construct a persistently named object.
    */
   public SubCallback(java.lang.String name, ClientSub boss)
   {
      this.ME = "SubCallback-" + name;
      this.boss = boss;
      if (Log.CALLS) Log.trace(ME, "Entering constructor with argument");
   }


   /**
    * This is the callback method invoked from the server
    * informing the client in an asynchronous mode about new messages
    */
   public void update(MessageUnit[] msgUnitArr, String[] qos_literal_Arr)
   {
      boss.update(msgUnitArr, qos_literal_Arr); // Call my boss, so she can check for errors
   }
} // SubCallback

