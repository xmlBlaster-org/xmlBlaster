/*------------------------------------------------------------------------------
Name:      ClientPubDestination.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster and publishing to destinations
Version:   $Id: ClientPubDestination.java,v 1.1 1999/12/02 17:06:18 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.*;
import org.xmlBlaster.authenticateIdl.*;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.clientIdl.*;
import jacorb.naming.NameServer;
import org.omg.CosNaming.*;


/**
 * This client tests the method publish() with a destination specified
 * <p>
 * Invoke examples:
 *    ${JacORB_HOME}/bin/jaco testsuite.org.xmlBlaster.ClientPubDestination `cat /tmp/NS_Ref`
 *
 *    ${JacORB_HOME}/bin/jaco testsuite.org.xmlBlaster.ClientPubDestination -name "Jeff" `cat /tmp/NS_Ref`
 */
public class ClientPubDestination
{
   private org.omg.CORBA.ORB orb = null;
   private Server xmlBlaster = null;
   private static String ME = "Tim";

   public ClientPubDestination(String args[])
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

         // Intialize my Callback interface:
         BlasterCallbackPOATie callbackTie = new BlasterCallbackPOATie(new SubCallback(ME));
         BlasterCallback callback = BlasterCallbackHelper.narrow(poa.servant_to_reference( callbackTie ));


         //----------- Login to the server -----------------------
         try {
            String passwd = "some";
            String qos = "";
            xmlBlaster = authServer.login(loginName, passwd, callback, qos);
         } catch(XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }


         delay(2000); // Wait some time ...


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


         ask("logout()");


         //----------- Logout --------------------------------------
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
      new ClientPubDestination(args);
      Log.exit(ClientPubDestination.ME, "Good bye");
   }
} // ClientPubDestination


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

