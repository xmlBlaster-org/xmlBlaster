package org.xmlBlaster;

import org.xmlBlaster.util.*;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.clientIdl.*;
import jacorb.naming.NameServer;
import org.omg.CosNaming.*;

public class Client
{
   public Client(String args[]) 
   { 
      final String ME = "Karl";

      org.omg.CORBA.ORB orb = org.omg.CORBA.ORB.init(args,null);
      try {
         Server xmlServer;

         if(args.length==1 ) {
             // args[0] is an IOR-string 

             xmlServer = ServerHelper.narrow(orb.string_to_object(args[0]));
         } 
         else {
            // CORBA compliant:
            NamingContext nc = NamingContextHelper.narrow(orb.resolve_initial_references("NameService"));
            NameComponent [] name = new NameComponent[1];
            name[0] = new NameComponent();
            name[0].id = "xmlBlaster";
            name[0].kind = "MOM";

            xmlServer = ServerHelper.narrow(nc.resolve(name));
         }

         // Getting the default POA implementation "RootPOA"
         org.omg.PortableServer.POA poa = 
            org.omg.PortableServer.POAHelper.narrow(orb.resolve_initial_references("RootPOA"));

         // Intializing my Callback interface:
         BlasterCallbackPOATie callbackTie = new BlasterCallbackPOATie(new BlasterCallbackImpl(ME));
         // callbackTie._orb( orb ); // necessary?
         BlasterCallback callback = BlasterCallbackHelper.narrow(poa.servant_to_reference( callbackTie ));

         /*
         try {
            Log.info("You are " + orb.default_principal().name(java.net.InetAddress.getLocalHost().toString().getBytes()));
         } catch (Exception e) {
         }
         */

         String qos = orb.object_to_string(callback);

         String sessionId = "";
         try {
            String loginName = "Karl";
            String passwd = "some";
            sessionId = xmlServer.login(loginName, passwd, callback, qos);
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }

         String xmlKey = "KEY_FOR_SMILEY";

         try {
            xmlServer.subscribe(sessionId, xmlKey, qos);
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }

         // Wait some time ...
         double val=2;
         for (int ii=0; ii<50000; ii++)
            val += 8;

         Log.trace(ME, "Sending some new Smiley data ...");
         try {
            String str = "Smiley changed";
            xmlServer.publish(sessionId, xmlKey, str.getBytes(), "");
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }

         Log.info(ME, "Sending done, waiting for response ...");

         Log.trace(ME, "Trying unsubscribe ...");
         try {
            xmlServer.unSubscribe(sessionId, xmlKey, qos);
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }
         Log.info(ME, "Unsubscribe done");

         try {
            String str = "Smiley changed again, but i'm not interested";
            xmlServer.publish(sessionId, xmlKey, str.getBytes(), "");
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }
         Log.info(ME, "Sending done, there shouldn't be a callback anymore ...");
         /*
         xmlServer._release();
         System.out.println("done. ");
         */

      }
      catch (Exception e) {
          e.printStackTrace();
      }
      orb.run();
   }

   public static void main(String args[]) 
   {
      new Client(args);
   }
}
