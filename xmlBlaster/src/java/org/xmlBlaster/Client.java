package org.xmlBlaster;

import org.xmlBlaster.util.*;
import org.xmlBlaster.authenticateIdl.*;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.clientIdl.*;
import jacorb.naming.NameServer;
import org.omg.CosNaming.*;

public class Client
{
   private org.omg.CORBA.ORB orb = null;
   private Server xmlServer = null;

   public Client(String args[]) 
   { 
      String ME = "Karl";

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
         org.omg.PortableServer.POA poa = 
            org.omg.PortableServer.POAHelper.narrow(orb.resolve_initial_references("RootPOA"));

         // Intializing my Callback interface:
         BlasterCallbackPOATie callbackTie = new BlasterCallbackPOATie(new BlasterCallbackImpl(ME));
         // callbackTie._orb( orb ); // necessary?
         BlasterCallback callback = BlasterCallbackHelper.narrow(poa.servant_to_reference( callbackTie ));
         Log.trace(ME, "Exported Callback Server interface" + stop.nice());


         String qos = orb.object_to_string(callback);

         //----------- Login to the server -----------------------
         try {
            String passwd = "some";
            xmlServer = authServer.login(loginName, passwd, callback, qos);
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }

         //------------ Use the returned IOR as Server Reference ------
         Log.info(ME, "Got xmlBlaster server IOR" + stop.nice());

         Log.trace(ME, "Server IOR= " + orb.object_to_string(xmlServer) + stop.nice());

         String xmlKey = "KEY_FOR_SMILEY";

         try {
            xmlServer.subscribe(xmlKey, qos);
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }
         Log.trace(ME, "Subscribed to Smiley data ..." + stop.nice());

         try {
            xmlServer.subscribe(xmlKey, qos);
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }
         Log.trace(ME, "Subscribed to Smiley data ..." + stop.nice());

         // Construct a message
         String str = "Smiley changed";
         MessageUnit[] marr = new MessageUnit[1];
         marr[0] = new MessageUnit(xmlKey, str.getBytes());
         String[] qarr = new String[1];
         qarr[0] = "";

         Log.trace(ME, "Sending some new Smiley data ...");
         try {
            xmlServer.publish(marr, qarr);

         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }

         Log.info(ME, "Sending done, waiting for response ..." + stop.nice());
         delay(); // Wait some time ...


         Log.trace(ME, "Trying unsubscribe ...");
         stop.restart();
         try {
            xmlServer.unSubscribe(xmlKey, qos);
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }
         Log.info(ME, "Unsubscribe done" + stop.nice());


         try {
            marr[0] = new MessageUnit(xmlKey, ((String)("Smiley changed again, but i'm not interested")).getBytes());
            xmlServer.publish(marr, qarr);
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }
         Log.info(ME, "Sending done, there shouldn't be a callback anymore ..." + stop.nice());

         //----------- Logout -----------------------
         try {
            authServer.logout(xmlServer);
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }


         Log.trace(ME, "Sending some new Smiley data after logout ...");
         try {
            xmlServer.publish(marr, qarr);

         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }

         Log.info(ME, "Sending done, waiting for response ..." + stop.nice());
      }
      catch (Exception e) {
          e.printStackTrace();
      }
      orb.run();
   }

   private static final int _delay = 2000;

   private void delay()
   {
      try
      {
          Thread.currentThread().sleep(_delay);
      }
      catch( InterruptedException i)
      {}
   }


   public static void main(String args[]) 
   {
      new Client(args);
   }
}
