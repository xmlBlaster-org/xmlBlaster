/*------------------------------------------------------------------------------
Name:      ClientOid.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientOid.java,v 1.9 2000/06/13 13:03:56 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients;

import org.xmlBlaster.util.*;
import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.LoginQosWrapper;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.corba.clientIdl.*;


/**
 * This client shows how to send/receive messages using a unique message name (oid).
 * <p>
 * It uses the callback implementation in the external file BlasterCallbackImpl.java.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    ${JacORB_HOME}/bin/jaco javaclients.ClientOid
 *
 *    ${JacORB_HOME}/bin/jaco javaclients.ClientOid -name "Jeff"
 * </pre>
 */
public class ClientOid
{
   private Server xmlBlaster = null;
   private static String ME = "Ben";

   public ClientOid(String args[])
   {
      StopWatch stop = new StopWatch();
      try {
         // check if parameter -name <userName> is given at startup of client
         ME = Args.getArg(args, "-name", ME);
         String loginName = ME;

         //----------- Find orb ----------------------------------
         CorbaConnection corbaConnection = new CorbaConnection(args);

         //---------- Building a Callback server ----------------------
         // Getting the default POA implementation "RootPOA"
         org.omg.PortableServer.POA rootPOA =
            org.omg.PortableServer.POAHelper.narrow(corbaConnection.getOrb().resolve_initial_references("RootPOA"));
         rootPOA.the_POAManager().activate();

         // Intializing my Callback interface:
         BlasterCallbackPOATie callbackTie = new BlasterCallbackPOATie(new BlasterCallbackImpl(ME));
         BlasterCallback callback = BlasterCallbackHelper.narrow(rootPOA.servant_to_reference( callbackTie ));
         Log.trace(ME, "Exported Callback Server interface" + stop.nice());


         //----------- Login to xmlBlaster -----------------------
         CallbackAddress addr = new CallbackAddress("IOR", corbaConnection.getOrb().object_to_string(callback));
         LoginQosWrapper qos = new LoginQosWrapper(addr); // == "<qos><callback type='IOR'>IOR:00113220001...</callback></qos>";
         String passwd = "some";
         xmlBlaster = corbaConnection.login(loginName, passwd, qos);


         //----------- Subscribe to a message with known oid -------
         String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                         "<key oid=\"KEY_FOR_SMILEY\" queryType='EXACT'></key>";
         try {
            xmlBlaster.subscribe(xmlKey, "<qos></qos>");
         } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
            Log.error(ME, "XmlBlasterException: " + e.reason);
         }
         Log.trace(ME, "Subscribed to Smiley data ..." + stop.nice());


         //----------- Subscribe to a message with known oid -------
         // subscribing twice: this second subscribe is ignored
         try {
            xmlBlaster.subscribe(xmlKey, "<qos></qos>");
         } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
            Log.error(ME, "XmlBlasterException: " + e.reason);
         }
         Log.trace(ME, "Subscribed to Smiley data ..." + stop.nice());


         //----------- Construct a message and publish it ---------
         xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                  "<key oid=\"KEY_FOR_SMILEY\" contentMime='text/plain'></key>";
         String str = "Yeahh, i'm the new content - Smiley changed";
         MessageUnit msg = new MessageUnit(xmlKey, str.getBytes());
         Log.trace(ME, "Sending some new Smiley data ...");
         try {
            xmlBlaster.publish(msg, "");
         } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
            Log.error(ME, "XmlBlasterException: " + e.reason);
         }
         Log.info(ME, "Sending done, waiting for response ..." + stop.nice());

         Util.delay(1000); // Wait some time ...


         //----------- Unsubscribe from the message --------
         Log.trace(ME, "Trying unsubscribe ...");
         xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                  "<key oid=\"KEY_FOR_SMILEY\" queryType='EXACT'></key>";
         stop.restart();
         try {
            xmlBlaster.unSubscribe(xmlKey, "<qos></qos>");
         } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
            Log.error(ME, "XmlBlasterException: " + e.reason);
         }
         Log.info(ME, "Unsubscribe done" + stop.nice());


         Log.trace(ME, "Trying publishArr ...");
         MessageUnit[] marr = new MessageUnit[1];
         marr[0] = new MessageUnit(xmlKey, ((String)("Smiley changed again, but i'm not interested")).getBytes());
         String[] qarr = new String[1];
         qarr[0] = "";
         try {
            String[] returnArr = xmlBlaster.publishArr(marr, qarr);
            for (int ii=0; ii<returnArr.length; ii++) {
               Log.info(ME, "   Returned oid=" + returnArr[ii]);
            }
         } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
            Log.error(ME, "XmlBlasterException: " + e.reason);
         }
         Log.info(ME, "Sending done, there shouldn't be a callback anymore ..." + stop.nice());


         //----------- Logout --------------------------------------
         corbaConnection.logout();


         //----------- Trying to send some data after logout -------
         Log.trace(ME, "Sending some new Smiley data after logout ...");
         try {
            xmlBlaster.publishArr(marr, qarr);
         } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
            Log.info(ME, "XmlBlasterException: " + e.reason);
         }

         // corbaConnection.getOrb().run(); // Usually your client won't exit after this, uncomment the run() method
      }
      catch (Exception e) {
          e.printStackTrace();
      }
   }


   public static void main(String args[])
   {
      Log.setLogLevel(args);
      new ClientOid(args);
      Log.exit(ClientOid.ME, "Good bye");
   }
}
