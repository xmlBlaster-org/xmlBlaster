/*------------------------------------------------------------------------------
Name:      ClientPubDestination.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster and publishing to destinations
Version:   $Id: ClientPubDestination.java,v 1.2 1999/12/09 00:22:05 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.*;
import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.clientIdl.*;


/**
 * This client tests the method get().
 * <p>
 * Invoke examples:
 *    ${JacORB_HOME}/bin/jaco testsuite.org.xmlBlaster.ClientPubDestination
 *
 *    ${JacORB_HOME}/bin/jaco testsuite.org.xmlBlaster.ClientPubDestination -name "Jeff"
 */
public class ClientPubDestination
{
   private Server xmlBlaster = null;
   private static String ME = "Paul";

   public ClientPubDestination(String args[])
   {
      StopWatch stop = new StopWatch();
      try {
         // check if parameter -name <userName> is given at startup of client
         ME = Args.getArg(args, "-name", ME);
         String loginName = ME;

         //----------- Find orb ----------------------------------
         CorbaConnection corbaConnection = new CorbaConnection(args);


         //---------- Building a Callback server ----------------------
         org.omg.PortableServer.POA poa = org.omg.PortableServer.POAHelper.narrow(corbaConnection.getOrb().resolve_initial_references("RootPOA"));
         BlasterCallbackPOATie callbackTie = new BlasterCallbackPOATie(new BlasterCallbackImpl(ME));
         BlasterCallback callback = BlasterCallbackHelper.narrow(poa.servant_to_reference( callbackTie ));
         Log.trace(ME, "Exported Callback Server interface" + stop.nice());


         String passwd = "some";
         xmlBlaster = corbaConnection.login(loginName, passwd, callback, "<qos></qos>");


         //----------- Construct a message and publish it ---------
         {
            String xmlKey = "<key oid='' contentMime='text/xml'>\n" +
                            "   <AGENT id='192.168.124.10' subId='1' type='generic'>" +
                            "      <DRIVER id='FileProof' pollingFreq='10'>" +
                            "      </DRIVER>"+
                            "   </AGENT>" +
                            "</key>";

            String qos = "<qos>" +
                         "   <destination queryType='EXACT'>" +
                                ME +
                         "   </destination>" +
                         "</qos>";

            String content = "Yeahh, i'm the new content, directly send from " + ME;
            MessageUnit messageUnit = new MessageUnit(xmlKey, content.getBytes());
            Log.trace(ME, "Publishing ...");
            stop.restart();
            try {
               String publishOid = xmlBlaster.publish(messageUnit, qos);
               Log.info(ME, "Returned oid=" + publishOid);
               Log.trace(ME, "Publishing done, there should be a callback now, since it was addressed to myself" + stop.nice());
            } catch(XmlBlasterException e) {
               Log.warning(ME, "XmlBlasterException: " + e.reason);
            }
         }


         corbaConnection.logout(xmlBlaster);
      }
      catch (Exception e) {
          e.printStackTrace();
          Log.panic(ME, "Error: " + e.toString());
      }
   }


   public static void main(String args[])
   {
      new ClientPubDestination(args);
      Log.exit(ClientPubDestination.ME, "Good bye");
   }
}
