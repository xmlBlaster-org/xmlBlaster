/*------------------------------------------------------------------------------
Name:      ClientXml.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientXml.java,v 1.12 1999/12/08 12:16:18 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.*;
import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.clientIdl.*;


/**
 * This client tests the method subscribe()/publish() with XML syntax key
 * and XPath query.
 * <p>
 * Invoke examples:
 *    ${JacORB_HOME}/bin/jaco testsuite.org.xmlBlaster.ClientXml
 *
 *    ${JacORB_HOME}/bin/jaco testsuite.org.xmlBlaster.ClientXml -name "Jeff"
 */
public class ClientXml
{
   private Server xmlBlaster = null;
   private static String ME = "Tim";

   public ClientXml(String args[])
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
         org.omg.PortableServer.POA poa =
            org.omg.PortableServer.POAHelper.narrow(corbaConnection.getOrb().resolve_initial_references("RootPOA"));

         // Intializing my Callback interface:
         BlasterCallbackPOATie callbackTie = new BlasterCallbackPOATie(new BlasterCallbackImpl(ME));
         BlasterCallback callback = BlasterCallbackHelper.narrow(poa.servant_to_reference( callbackTie ));


         //----------- Login to xmlBlaster -----------------------
         String qos = "<qos></qos>";
         String passwd = "some";
         xmlBlaster = corbaConnection.login(loginName, passwd, callback, qos);


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
            MessageUnit messageUnit = new MessageUnit(xmlKey, content.getBytes());
            Log.trace(ME, "Publishing ...");
            stop.restart();
            try {
               publishOid = xmlBlaster.publish(messageUnit, "<qos></qos>");
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
            publishOid = xmlBlaster.subscribe(xmlKey, qos);
            Log.trace(ME, "Subscribed to '" + publishOid + "' ..." + stop.nice());
         } catch(XmlBlasterException e) {
            Log.error(ME, "Subscribe failed, XmlBlasterException: " + e.reason);
         }

         Util.delay(2000); // Wait some time ...


         //----------- Unsubscribe from the previous message --------
         Log.trace(ME, "Unsubscribe ...");
         stop.restart();
         try {
            xmlBlaster.unSubscribe(xmlKey, qos);
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
            xmlBlaster.subscribe(xmlKey, qos);
            Log.trace(ME, "Subscribe done, there should be a Callback");
         } catch(XmlBlasterException e) {
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
               MessageUnit messageUnit = new MessageUnit(xmlKey, content.getBytes());
               Log.trace(ME, "Publishing ...");
               stop.restart();
               try {
                  String str = xmlBlaster.publish(messageUnit, "");
                  Log.trace(ME, "Publishing done" + stop.nice());
               } catch(XmlBlasterException e) {
                  Log.error(ME, "Publishing failed, XmlBlasterException: " + e.reason);
               }
            }
         }


         Util.ask("logout()");
         corbaConnection.logout(xmlBlaster);
      }
      catch (Exception e) {
          e.printStackTrace();
      }
   }


   public static void main(String args[])
   {
      new ClientXml(args);
      Log.exit(ClientXml.ME, "Good bye");
   }
}
