/*------------------------------------------------------------------------------
Name:      ClientXml.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientXml.java,v 1.6 1999/11/22 16:12:21 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.*;
import org.xmlBlaster.authenticateIdl.*;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.clientIdl.*;
import jacorb.naming.NameServer;
import org.omg.CosNaming.*;

public class ClientXml
{
   private org.omg.CORBA.ORB orb = null;
   private Server xmlBlaster = null;
   private String ME = "Tim";

   public ClientXml(String args[]) 
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
         /*
         else {
            String host = localhost;
            String port = 80;
            String authIOR = getAuthenticationServiceIOR(host, port);
            authServer = AuthServerHelper.narrow(authIOR);
         }
         */

         StopWatch stop = new StopWatch();

         //---------- Building a Callback server ----------------------
         // Getting the default POA implementation "RootPOA"
         org.omg.PortableServer.POA poa = 
            org.omg.PortableServer.POAHelper.narrow(orb.resolve_initial_references("RootPOA"));

         // Intializing my Callback interface:
         BlasterCallbackPOATie callbackTie = new BlasterCallbackPOATie(new BlasterCallbackImpl(ME));
         BlasterCallback callback = BlasterCallbackHelper.narrow(poa.servant_to_reference( callbackTie ));
         // A dummy implementation of the Callback is in:
         //    xmlBlaster/src/java/org/xmlBlaster/clientIdl/BlasterCallbackImpl.java



         //----------- Login to the server -----------------------
         String qos = "";
         try {
            String passwd = "some";
            xmlBlaster = authServer.login(loginName, passwd, callback, qos);
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }

         String publishOid = "";

         String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                         "<key oid=''>\n" +
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
               publishOid = xmlBlaster.publish(messageUnit, "QOS:");
               Log.info(ME, "   Returned oid=" + publishOid);
            } catch(XmlBlasterException e) {
               Log.warning(ME, "XmlBlasterException: " + e.reason);
            }
            Log.trace(ME, "Publishing done" + stop.nice());
         }


         //----------- Subscribe to the previous message OID -------
         Log.trace(ME, "Subscribing using the exact oid ...");
         xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                  "<key oid='" + publishOid + "'>\n" +
                  "</key>";
         stop.restart();
         try {
            xmlBlaster.subscribe(xmlKey, qos);
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }
         Log.trace(ME, "Subscribed to '" + publishOid + "' ..." + stop.nice());

         delay(2000); // Wait some time ...


         //----------- Unsubscribe from the previous message --------
         Log.trace(ME, "Unsubscribe ...");
         stop.restart();
         try {
            xmlBlaster.unSubscribe(xmlKey, qos);
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }
         Log.info(ME, "Unsubscribe done" + stop.nice());


         //----------- Subscribe to the previous message XPATH -------
         Log.trace(ME, "Subscribing using XPath syntax ...");
         xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                  "<key oid='' queryType='XPATH'>\n" +
                  "/xmlBlaster/key/AGENT" +
                  "</key>";
         stop.restart();
         try {
            xmlBlaster.subscribe(xmlKey, qos);
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }
         Log.trace(ME, "Subscribe done, there should be a Callback");

         delay(2000); // Wait some time ...


         Log.trace(ME, "Publishing 10 times ...");
         {
            for (int ii=0; ii<10; ii++) {
               //----------- Construct a message and publish it ---------
               String content = "Yeahh, i'm the new content " + ii + ", ";
               xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                           "<key oid='" + publishOid + "'>\n" +
                           "</key>";
               MessageUnit messageUnit = new MessageUnit(xmlKey, content.getBytes());
               Log.trace(ME, "Publishing ...");
               stop.restart();
               try {
                  String str = xmlBlaster.publish(messageUnit, "");
               } catch(XmlBlasterException e) {
                  Log.warning(ME, "XmlBlasterException: " + e.reason);
               }
               Log.trace(ME, "Publishing done" + stop.nice());
            }
         }

         ask("logout()");
         // delay(10000); // Wait some time ...

         Log.trace(ME, "Logout ...");
         try {
            authServer.logout(xmlBlaster);
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }
      }
      catch (Exception e) {
          e.printStackTrace();
      }
      orb.run();
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


   /**
    * To avoid the name service, one can access the Auhtenticate IOR directly
    * using a http connection
    */
   public String getAuthenticationServiceIOR(String host, int port)
   {
      try {
         java.net.URL nsURL = new java.net.URL("http", host, port, "/AuthenticationService.ior");
         java.io.InputStream nsis = nsURL.openStream();
         byte[] bytes = new byte[4096];
         java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
         int numbytes;
         while (nsis.available() > 0 && (numbytes = nsis.read(bytes)) > 0) {
             bos.write(bytes, 0, numbytes);
         }
         nsis.close();
         return bos.toString();
      }
      catch (Exception ex) {
         Log.panic(ME, "Caught exception: " + ex);
         return null;
      }
   }


   public static void main(String args[]) 
   {
      new ClientXml(args);
   }
}
