/*------------------------------------------------------------------------------
Name:      ClientRawSecurity.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code how to access xmlBlaster using CORBA
Version:   $Id$
------------------------------------------------------------------------------*/
package javaclients.corba;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.jutils.init.Args;
import org.jutils.time.StopWatch;
import org.jutils.io.FileUtil;

import org.xmlBlaster.protocol.corba.authenticateIdl.AuthServer;
import org.xmlBlaster.protocol.corba.authenticateIdl.AuthServerHelper;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.protocol.corba.serverIdl.ServerHelper;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackOperations;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackPOATie;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackHelper;
import org.xmlBlaster.client.qos.ConnectReturnQos;

import org.omg.CosNaming.*;


/**
 * Demo code how to access xmlBlaster using CORBA, step by step without any client helper classes.
 * <p>
 * It demonstrates how to specify a security plugin for authentication.
 * <p>
 * It uses the method subscribe() with a later publish() with XPath query.<br />
 * The subscribe() should be recognized for this later arriving publish()
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java org.xmlBlaster.Main -plugin/ior/iorFile /tmp/NS_Ref
 *
 *    ${JacORB_HOME}/bin/jaco javaclients.corba.ClientRawSecurity -dispatch/connection/plugin/ior/iorFile /tmp/NS_Ref
 *
 *    ${JacORB_HOME}/bin/jaco javaclients.corba.ClientRawSecurity -dispatch/connection/plugin/ior/iorString `cat /tmp/NS_Ref`
 *
 *    ${JacORB_HOME}/bin/jaco javaclients.corba.ClientRawSecurity -loginName "Jeff" `cat /tmp/NS_Ref`
 * </pre>
 * NOTE: You need to use 'jaco' to switch off the native JDK library (or use
 *       any other way as described in INSTALL file).
 */
public class ClientRawSecurity
{
   private static String ME = "ClientRawSecurity";

   private final Global glob;
   private static Logger log = Logger.getLogger(ClientRawSecurity.class.getName());
   private final org.omg.CORBA.ORB orb;

   private Server xmlBlaster = null;

   public ClientRawSecurity(String args[])
   {
      glob = new Global(args);

      orb = org.omg.CORBA.ORB.init(args,null);
      try {
         AuthServer authServer;
         ME = Args.getArg(args, "-loginName", ME);
         String loginName = ME;

         String fileName = Args.getArg(args, "-dispatch/connection/plugin/ior/iorFile", (String)null); // a file with the IOR string
         String authServerIOR = Args.getArg(args, "-dispatch/connection/plugin/ior/iorString", (String)null); // the IOR string

         if (fileName != null) authServerIOR = FileUtil.readAsciiFile(fileName);

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
            if (nc == null) {
               System.out.println("\nSorry, please pass the server IOR string to the client, e.g.:\n"
                           + "Start the server:\n"
                           + "   jaco org.xmlBlaster.Main -dispatch/connection/plugin/ior/iorFile /tmp/NS_Ref\n"
                           + "Start this client:\n"
                           + "   jaco javaclients.corba.ClientRawSecurity -dispatch/connection/plugin/ior/iorFile /tmp/NS_Ref\n");
               usage();
               log.severe("Read xmlBlaster/INSTALL for help");
               System.exit(1);
            }
            authServer = AuthServerHelper.narrow(nc.resolve(name));
         }

         StopWatch stop = new StopWatch();

         //---------- Building a Callback server ----------------------
         // Getting the default POA implementation "RootPOA"
         org.omg.PortableServer.POA rootPOA =
            org.omg.PortableServer.POAHelper.narrow(orb.resolve_initial_references("RootPOA"));

         // Intialize my Callback interface:
         BlasterCallbackPOATie callbackTie = new BlasterCallbackPOATie(new RawCallback(glob, ME));
         BlasterCallback callback = BlasterCallbackHelper.narrow(rootPOA.servant_to_reference( callbackTie ));

         rootPOA.the_POAManager().activate();

         //----------- Login to the server (the new way) ---------
         try {
            String passwd = "some";

            // The xmlBlaster server takes this IOR string and uses it to connect
            // to our client-side callback interface to deliver updates back
            String callbackIOR = orb.object_to_string(callback);

            // Create a XML based qos (quality of service) which hold the IOR (the CORBA
            // address of our callback server)
            String qos = "<qos>\n" +
                         "   <securityService type=\"simple\" version=\"1.0\">\n" +
                         "      <user>" + loginName + "</user>\n" +
                         "      <passwd>" + passwd + "</passwd>\n" +
                         "   </securityService>\n" +
                         "   <callback type='IOR'>\n" +
                         callbackIOR + "\n" +
                         "   </callback>\n" + 
                         "</qos>\n";

            String retXml = authServer.connect(qos);

            // Parse the returned string, it contains the server IOR
            ConnectReturnQos returnQos = new ConnectReturnQos(glob, retXml);

            log.info("Login (Connect) done.");
            log.info("Used QoS=\n" + qos);
            log.info("Returned QoS=\n" + returnQos.toXml());

            // Get the CORBA handle of xmlBlaster ...
            String xmlBlasterIOR = returnQos.getServerRef().getAddress();
            xmlBlaster = ServerHelper.narrow(orb.string_to_object(xmlBlasterIOR));

         } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
            log.warning("XmlBlasterException: " + e.getMessage());
         }


         //----------- Subscribe to messages with XPATH -------
         {
            log.fine("Subscribing using XPath syntax ...");
            String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                           "<key oid='' queryType='XPATH'>\n" +
                           "/xmlBlaster/key/AGENT" +
                           "</key>";
            stop.restart();
            try {
               xmlBlaster.subscribe(xmlKey, "<qos></qos>");
            } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
               log.warning("XmlBlasterException: " + e.getMessage());
            }
            log.info("Subscribe done, there should be no Callback" + stop.nice());
         }


         delay(2000); // Wait some time ...


         //----------- Construct a message and publish it ---------
         {
            String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                            "<key oid='' contentMime='text/xml'>\n" +
                            "  <AGENT id='192.168.124.10' subId='1' type='generic'>" +
                            "    <DRIVER id='FileProof' pollingFreq='10'>" +
                            "    </DRIVER>"+
                            "  </AGENT>" +
                            "</key>";
            String content = "Yeahh, i'm the new content";
            MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), "<qos></qos>");
            log.info("Publishing ...");
            stop.restart();
            try {
               String publishOid = xmlBlaster.publish(msgUnit);
               log.fine("Returned oid=" + publishOid);
            } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
               log.warning("XmlBlasterException: " + e.getMessage());
            }
            log.info("Publishing done, there should be a callback now" + stop.nice());
         }

         delay(1000); // Wait some time ...

         // orb.run(); // Usually your client won't exit after this, uncomment the run() method

         ask("logout()");

         //----------- Logout --------------------------------------
         log.info("Logout ...");
         try {
            authServer.logout(xmlBlaster);
            authServer._release();
            xmlBlaster._release();
         } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
            log.warning("XmlBlasterException: " + e.getMessage());
         }

         //----------- Shutdown my callback server -----------------
         try {
            callback._release();
            rootPOA.deactivate_object(rootPOA.reference_to_id(callback));
         } catch(Exception e) { log.warning("POA deactivate callback failed"); }


         //----------- Stop the POA --------------------------------
         try {
            rootPOA.the_POAManager().deactivate(false, true);
         } catch(Exception e) { log.warning("POA deactivate failed"); }

         //----------- Shutdown the ORB ----------------------------
         orb.shutdown(true);
      }
      catch (Exception e) {
          log.severe(e.toString());
          e.printStackTrace();
      }
   }

   private void delay(long millis)
   {
      try {
          Thread.sleep(millis);
      }
      catch( InterruptedException i)
      {}
   }

   private void ask(String text)
   {
      System.out.println(text);
      System.out.println("################### Hit a key to continue ###################");
      try {
         System.in.read();
      } catch (java.io.IOException e) {}
   }

   static void usage()
   {
      System.out.println("\nAvailable options:");
      System.out.println("   -loginName                                The login name [ClientRawSecurity].");
      System.out.println("   -dispatch/connection/plugin/ior/iorFile   File with the IOR string from xmlBlaster.");
      System.out.println("   -dispatch/callback/plugin/ior/iorString   The raw IOR string from xmlBlaster.");
      Global.instance().usage();
      System.err.println("Example: jaco javaclients.corba.ClientRawSecurity -dispatch/connection/plugin/ior/iorFile /tmp/NS_Ref\n");
      System.exit(1);
   }

   public static void main(String args[])
   {
      new ClientRawSecurity(args);
   }


   /**
    * Example for a callback implementation, used by the demo ClientRawSecurity.
    */
   private class RawCallback implements BlasterCallbackOperations
   {
      final String ME;
      final Global glob;

      /**
       * Construct it.
       */
      public RawCallback(Global glob, java.lang.String name) {
         this.glob = glob;

         this.ME = "RawCallback-" + name;
         if (log.isLoggable(Level.FINER)) log.fine("Entering constructor with argument");
      }

      /**
       * This is the callback method invoked from the server
       * informing the client in an asynchronous mode about new messages
       */
      public String[] update(String sessionId, MessageUnit[] msgUnitArr)
      {
         String[] ret = new String[msgUnitArr.length];
         for (int ii=0; ii<msgUnitArr.length; ii++) {
            MessageUnit msgUnit = msgUnitArr[ii];
            System.out.println("\n================== BlasterCallback update START =============");
            System.out.println("Callback invoked for " + msgUnit.xmlKey + " content length = " + msgUnit.content.length);
            System.out.println(new String(msgUnit.content));
            System.out.println("================== BlasterCallback update END ===============\n");
            ret[ii] = "<qos><state id='OK'/></qos>";
         }
         return ret;
      }

      /**
       * This oneway method does not return something, it is high performing but
       * you loose the application level hand shake.
       * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
       */
      public void updateOneway(String cbSessionId, org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] msgUnitArr)
      {
         try {
            update(cbSessionId, msgUnitArr);
         }
         catch (Throwable e) {
            log.severe("updateOneway() failed, exception is not sent to xmlBlaster: " + e.toString());
            e.printStackTrace();
         }
      }

      /**
       * Ping to check if the callback server is alive.
       * @param qos ""
       * @return ""
       */
      public String ping(String qos)
      {
         if (log.isLoggable(Level.FINER)) log.finer("Entering ping() ...");
         return "";
      }
   } // RawCallback

} // ClientRawSecurity

