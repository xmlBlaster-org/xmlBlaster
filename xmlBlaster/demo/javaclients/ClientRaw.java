/*------------------------------------------------------------------------------
Name:      ClientRaw.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code how to access xmlBlaster using CORBA
Version:   $Id: ClientRaw.java,v 1.6 2000/02/20 17:38:48 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients;

import org.xmlBlaster.util.*;
import org.xmlBlaster.protocol.corba.authenticateIdl.*;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;
import jacorb.naming.NameServer;
import org.omg.CosNaming.*;


/**
 * Demo code how to access xmlBlaster using CORBA, step by step without any client helper classes.
 * <p>
 * It uses the method subscribe() with a later publish() with XPath query.<br />
 * The subscribe() should be recognized for this later arriving publish()
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    ${JacORB_HOME}/bin/jaco javaclients.ClientRaw `cat /tmp/NS_Ref`
 *
 *    ${JacORB_HOME}/bin/jaco javaclients.ClientRaw -name "Jeff" `cat /tmp/NS_Ref`
 * </pre>
 */
public class ClientRaw
{
   private org.omg.CORBA.ORB orb = null;
   private Server xmlBlaster = null;
   private static String ME = "Tim";

   public ClientRaw(String args[])
   {
      orb = org.omg.CORBA.ORB.init(args,null);
      try {
         AuthServer authServer;
         String authServerIOR = null;

         if (args.length == 1) {
            authServerIOR = args[0];  // args[0] is an IOR-string
         }
         else if (args.length > 1) {
            String argv = args[0];
            if (argv.equals("-name")) {
               ME = args[1];
            }
         }

         String loginName = ME;

         if (authServerIOR != null) {
            authServer = AuthServerHelper.narrow(orb.string_to_object(authServerIOR));
         }
         else {
            // asking Name Service CORBA compliant:
            NamingContext nc = NamingContextHelper.narrow(orb.resolve_initial_references("NameService"));
            NameComponent [] name = new NameComponent[1];
            name[0] = new NameComponent();
            name[0].id = "xmlBlaster-Authenticate";
            name[0].kind = "MOM";

            authServer = AuthServerHelper.narrow(nc.resolve(name));
         }

         StopWatch stop = new StopWatch();

         //---------- Building a Callback server ----------------------
         // Getting the default POA implementation "RootPOA"
         org.omg.PortableServer.POA rootPOA =
            org.omg.PortableServer.POAHelper.narrow(orb.resolve_initial_references("RootPOA"));

         // Intialize my Callback interface:
         BlasterCallbackPOATie callbackTie = new BlasterCallbackPOATie(new RawCallback(ME));
         BlasterCallback callback = BlasterCallbackHelper.narrow(rootPOA.servant_to_reference( callbackTie ));

         rootPOA.the_POAManager().activate();

         //----------- Login to the server -----------------------
         String qos = "";
         try {
            String passwd = "some";
            xmlBlaster = authServer.login(loginName, passwd, callback, qos);
            Log.info(ME, "Login done");
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }


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
            Log.info(ME, "Subscribe done, there should be no Callback" + stop.nice());
         }


         delay(2000); // Wait some time ...


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
            Log.info(ME, "Publishing ...");
            stop.restart();
            try {
               String publishOid = xmlBlaster.publish(messageUnit, "<qos></qos>");
               Log.trace(ME, "Returned oid=" + publishOid);
            } catch(XmlBlasterException e) {
               Log.warning(ME, "XmlBlasterException: " + e.reason);
            }
            Log.info(ME, "Publishing done, there should be a callback now" + stop.nice());
         }

         delay(1000); // Wait some time ...

         ask("logout()");


         //----------- Logout --------------------------------------
         Log.info(ME, "Logout ...");
         try {
            authServer.logout(xmlBlaster);
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }

         // corbaConnection.getOrb().run(); // Usually your client won't exit after this, uncomment the run() method
      }
      catch (Exception e) {
          e.printStackTrace();
      }
      //orb.run();
   }


   private void delay(long millis)
   {
      try {
          Thread.currentThread().sleep(millis);
      }
      catch( InterruptedException i)
      {}
   }


   private void ask(String text)
   {
      Log.plain(ME, text);
      Log.plain(ME, "################### Hit a key to continue ###################");
      try {
         System.in.read();
      } catch (java.io.IOException e) {}
   }


   public static void main(String args[])
   {
      Log.setLogLevel(args);
      new ClientRaw(args);
      Log.exit(ClientRaw.ME, "Good bye");
   }
} // ClientRaw


/**
 * Example for a callback implementation, used by the demo ClientRaw.
 */
class RawCallback implements BlasterCallbackOperations
{
   final String ME;

   /**
    * Construct a persistently named object.
    */
   public RawCallback(java.lang.String name) {
      this.ME = "RawCallback-" + name;
      if (Log.CALLS) Log.trace(ME, "Entering constructor with argument");
   }


   /**
    * Construct a transient object.
    */
   public RawCallback() {
      super();
      this.ME = "RawCallback";
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
} // RawCallback

