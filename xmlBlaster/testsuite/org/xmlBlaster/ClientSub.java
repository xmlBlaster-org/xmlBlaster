/*------------------------------------------------------------------------------
Name:      ClientSub.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientSub.java,v 1.4 1999/12/08 12:16:18 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.*;
import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.clientIdl.*;


/**
 * This client tests the method subscribe() with a later publish() with XPath query
 * The subscribe() should be recognized for this later arriving publish()
 * <p>
 * Invoke examples:
 *    ${JacORB_HOME}/bin/jaco testsuite.org.xmlBlaster.ClientSub
 *
 *    ${JacORB_HOME}/bin/jaco testsuite.org.xmlBlaster.ClientSub -name "Jeff"
 */
public class ClientSub
{
   private Server xmlBlaster = null;
   private static String ME = "Tim";

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
         // Getting the default POA implementation "RootPOA"
         org.omg.PortableServer.POA poa =
            org.omg.PortableServer.POAHelper.narrow(corbaConnection.getOrb().resolve_initial_references("RootPOA"));

         // Intialize my Callback interface:
         BlasterCallbackPOATie callbackTie = new BlasterCallbackPOATie(new SubCallback(ME));
         BlasterCallback callback = BlasterCallbackHelper.narrow(poa.servant_to_reference( callbackTie ));


         //----------- Login to xmlBlaster -----------------------
         String qos = "<qos></qos>";
         String passwd = "some";
         xmlBlaster = corbaConnection.login(loginName, passwd, callback, qos);


         //----------- Subscribe to messages with XPATH -------
         {
            Log.trace(ME, "Subscribing using XPath syntax ...");
            String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                           "<key oid='' queryType='XPATH'>\n" +
                           "/xmlBlaster/key/AGENT" +
                           "</key>";
            stop.restart();
            try {
               xmlBlaster.subscribe(xmlKey, qos);
            } catch(XmlBlasterException e) {
               Log.warning(ME, "XmlBlasterException: " + e.reason);
            }
            Log.trace(ME, "Subscribe done, there should be no Callback" + stop.nice());
         }


         Util.delay(2000); // Wait some time ...


         //----------- Construct a message and publish it ---------
         {
            String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                            "<key oid='' contentMime='text/xml'>\n" +
                            "<AGENT id='192.168.124.10' subId='1' type='generic'>" +
                            "<DRIVER id='FileProof' pollingFreq='10'>" +
                            "</DRIVER>"+
                            "</AGENT>" +
                            "</key>";
            String content = "Yeahh, i'm the new content";
            MessageUnit messageUnit = new MessageUnit(xmlKey, content.getBytes());
            Log.trace(ME, "Publishing ...");
            stop.restart();
            try {
               String publishOid = xmlBlaster.publish(messageUnit, "<qos></qos>");
               Log.info(ME, "   Returned oid=" + publishOid);
            } catch(XmlBlasterException e) {
               Log.warning(ME, "XmlBlasterException: " + e.reason);
            }
            Log.trace(ME, "Publishing done, there should be a callback now" + stop.nice());
         }


         Util.ask("logout()");
         corbaConnection.logout(xmlBlaster);
      }
      catch (Exception e) {
          e.printStackTrace();
      }
      //orb.run();
   }


   public static void main(String args[])
   {
      new ClientSub(args);
      Log.exit(ClientSub.ME, "Good bye");
   }
} // ClientSub



/**
 * Example for a callback implementation. 
 */
class SubCallback implements BlasterCallbackOperations
{
   final String ME;

   /**
    * Construct a persistently named object.
    */
   public SubCallback(java.lang.String name) {
      this.ME = "SubCallback-" + name;
      if (Log.CALLS) Log.trace(ME, "Entering constructor with argument");
   }


   /**
    * Construct a transient object.
    */
   public SubCallback() {
      super();
      this.ME = "SubCallback";
      if (Log.CALLS) Log.trace(ME, "Entering constructor without argument");
   }


   /**
    * This is the callback method invoked from the server
    * informing the client in an asynchronous mode about new messages
    */
   public void update(MessageUnit[] messageUnitArr, String[] qos_literal_Arr)
   {
      for (int ii=0; ii<messageUnitArr.length; ii++) {
         MessageUnit messageUnit = messageUnitArr[ii];
         XmlKeyBase xmlKey = null;
         try {
            xmlKey = new XmlKeyBase(messageUnit.xmlKey);
         } catch (XmlBlasterException e) {
            Log.error(ME, e.reason);
         }
         Log.info(ME, "================== BlasterCallback update START =============");
         Log.info(ME, "Callback invoked for " + xmlKey.toString() + " content length = " + messageUnit.content.length);
         Log.info(ME, new String(messageUnit.content));
         Log.info(ME, "================== BlasterCallback update END ===============");
      }
   }
} // SubCallback

