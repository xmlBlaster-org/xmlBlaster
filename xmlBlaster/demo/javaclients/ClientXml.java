/*------------------------------------------------------------------------------
Name:      ClientXml.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientXml.java,v 1.13 2000/06/19 15:48:35 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients;

import org.jutils.log.Log;
import org.jutils.init.Args;
import org.jutils.time.StopWatch;

import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnitContainer;


/**
 * This client tests the method subscribe()/publish() with XML syntax key
 * and XPath query using the DefaultCallback implementation.
 * <p>
 * It is a nice example using the DefaultCallback implementation from CorbaConnection.java
 * which calls the update() method using I_Callback interface when messages arrive.
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
   private Server xmlBlaster = null;
   private static String ME = "Tim";

   public ClientXml(String args[])
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      Log.setLogLevel(XmlBlasterProperty.getProperty());
      StopWatch stop = new StopWatch();
      try {
         // check if parameter -name <userName> is given at startup of client
         ME = Args.getArg(args, "-name", ME);
         String loginName = ME;

         //----------- Find orb ----------------------------------
         CorbaConnection corbaConnection = new CorbaConnection(args);

         //----------- Login to xmlBlaster -----------------------
         String passwd = "some";
         xmlBlaster = corbaConnection.login(loginName, passwd, null, this); // installs the Callback server as well!


         String publishOid = "";
         String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                         "<key oid='' contentMime='text/xml'>\n" +
                         "<AGENT id='192.168.124.10' subId='1' type='generic'>" +
                         "<DRIVER id='FileProof' pollingFreq='10'>" +
                         "</DRIVER>"+
                         "</AGENT>" +
                         "</key>";


         //----------- Construct a message and publish it ---------
         {
            String content = "Yeahh, i'm the new content";
            MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes());
            Log.trace(ME, "Publishing ...");
            stop.restart();
            try {
               publishOid = xmlBlaster.publish(msgUnit, "<qos></qos>");
               Log.info(ME, "   Returned oid=" + publishOid);
               Log.trace(ME, "Publishing done" + stop.nice());
            } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
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
            publishOid = xmlBlaster.subscribe(xmlKey, "<qos></qos>");
            Log.trace(ME, "Subscribed to '" + publishOid + "' ..." + stop.nice());
         } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
            Log.error(ME, "Subscribe failed, XmlBlasterException: " + e.reason);
         }

         Util.delay(2000); // Wait some time ...


         //----------- Unsubscribe from the previous message --------
         Log.trace(ME, "Unsubscribe ...");
         stop.restart();
         try {
            xmlBlaster.unSubscribe(xmlKey, "<qos></qos>");
            Log.info(ME, "Unsubscribe done" + stop.nice());
         } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
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
            xmlBlaster.subscribe(xmlKey, "<qos></qos>");
            Log.trace(ME, "Subscribe done, there should be a Callback");
         } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
            Log.error(ME, "subscribe failed, XmlBlasterException: " + e.reason);
         }

         Util.delay(2000); // Wait some time ...


         Log.trace(ME, "Publishing 10 times ...");
         {
            for (int ii=0; ii<10; ii++) {
               //----------- Construct a message and publish it ---------
               String content = "Yeahh, i'm the new content " + ii + ", ";
               xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                           "<key oid='" + publishOid + "' contentMime='text/xml'>\n" +
                           "</key>";
               MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes());
               Log.trace(ME, "Publishing ...");
               stop.restart();
               try {
                  String str = xmlBlaster.publish(msgUnit, "");
                  Log.trace(ME, "Publishing done" + stop.nice());
               } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
                  Log.error(ME, "Publishing failed, XmlBlasterException: " + e.reason);
               }
            }
         }


         Util.ask("logout()");
         corbaConnection.logout();
      }
      catch (Exception e) {
          e.printStackTrace();
      }

      // corbaConnection.getOrb().run(); // Usually your client won't exit after this, uncomment the run() method
   }


   /**
    * This is the callback method (update() from I_Callback) invoked from class CorbaConnection
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
      Log.info(ME, "Receiving update of message [" + updateKey.getUniqueKey() + "]");
   }


   public static void main(String args[])
   {
      new ClientXml(args);
      Log.exit(ClientXml.ME, "Good bye");
   }
}
