/*------------------------------------------------------------------------------
Name:      ClientGet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientGet.java,v 1.1 1999/11/22 23:32:34 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.*;
import org.xmlBlaster.authenticateIdl.*;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.clientIdl.*;
import jacorb.naming.NameServer;
import org.omg.CosNaming.*;

public class ClientGet
{
   private org.omg.CORBA.ORB orb = null;
   private Server xmlBlaster = null;
   private String ME = "Heidi";

   public ClientGet(String args[]) 
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

         //----------- Login to the server -----------------------
         String qos = "";
         try {
            String passwd = "some";
            xmlBlaster = authServer.login(loginName, passwd, (BlasterCallback)null, qos);
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }

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
            String content = "<person><firstName>heidi</firstName><age>36</age></person>";
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


         //----------- get() the previous message OID -------
         Log.trace(ME, "get() using the exact oid ...");
         xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                  "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                  "</key>";
         stop.restart();
         MessageUnit[] msgArr = null;
         try {
            msgArr = xmlBlaster.get(xmlKey, qos);
         } catch(XmlBlasterException e) {
            Log.error(ME, "XmlBlasterException: " + e.reason);
         }

         Log.info(ME, "Got " + msgArr.length + " messages:");
         for (int ii=0; ii<msgArr.length; ii++) {
            Log.plain(ME, msgArr[ii].xmlKey + "\n\n" + msgArr[ii].content.toString());
         }

         
         ask("logout()");


         //----------- logout() -----------------------------
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
      new ClientGet(args);
   }
}
