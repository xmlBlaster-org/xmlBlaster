/*------------------------------------------------------------------------------
Name:      ClientRaw.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package javaclients.corba;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.key.UpdateKey; // To SAX parse the received XML key
import org.xmlBlaster.client.qos.UpdateQos; // To SAX parse the received XML QoS
import org.jutils.init.Args;
import org.jutils.time.StopWatch;
import org.jutils.io.FileUtil;

import org.xmlBlaster.protocol.corba.authenticateIdl.AuthServer;
import org.xmlBlaster.protocol.corba.authenticateIdl.AuthServerHelper;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackOperations;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackPOATie;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackHelper;

import org.omg.CosNaming.*;


/**
 * Demo code how to access xmlBlaster using CORBA, step by step without any client helper classes.
 * <p>
 * It uses the method subscribe() with a later publish() with XPath query.<br />
 * The subscribe() should be recognized for this later arriving publish()
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    jaco org.xmlBlaster.Main -plugin/ior/iorFile /tmp/NS_Ref
 *
 *    ${JacORB_HOME}/bin/jaco javaclients.corba.ClientRaw -dispatch/connection/plugin/ior/iorFile /tmp/NS_Ref
 *
 *    ${JacORB_HOME}/bin/jaco javaclients.corba.ClientRaw -dispatch/connection/plugin/ior/iorString `cat /tmp/NS_Ref`
 *
 *    ${JacORB_HOME}/bin/jaco javaclients.corba.ClientRaw -loginName "Jeff" `cat /tmp/NS_Ref`
 * </pre>
 */
public class ClientRaw
{
   private static String ME = "ClientRaw";
   
   private final org.omg.CORBA.ORB orb;
   private final String[] args;
   private final LogChannel log;

   private Server xmlBlaster = null;

   public ClientRaw(String args[]) {
      this.args = args;
      System.out.println("   -dispatch/connection/plugin/ior/iorString  The raw IOR string from xmlBlaster.");
      System.out.println("   -dispatch/connection/plugin/ior/iorFile    The raw IOR string from a file.");
      Global glob = new Global(args);
      this.log = glob.getLog("client");
      orb = org.omg.CORBA.ORB.init(this.args,null);
      try {
         AuthServer authServer;
         ME = Args.getArg(this.args, "-loginName", ME);
         String loginName = ME;

         String fileName = Args.getArg(this.args, "-dispatch/connection/plugin/ior/iorFile", (String)null); // a file with the IOR string
         String authServerIOR = Args.getArg(this.args, "-dispatch/connection/plugin/ior/iorString", (String)null); // the IOR string "IOR:00405..."

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
               log.plain(ME, "\nSorry, please pass the server IOR string to the client, e.g.:\n"
                           + "Start the server:\n"
                           + "   jaco org.xmlBlaster.Main -dispatch/connection/plugin/ior/iorFile /tmp/NS_Ref\n"
                           + "Start this client:\n"
                           + "   jaco javaclients.corba.ClientRaw -dispatch/connection/plugin/ior/iorFile /tmp/NS_Ref\n");
               usage();
               System.err.println(ME + ": Read xmlBlaster/INSTALL for help");
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
         BlasterCallbackPOATie callbackTie = new BlasterCallbackPOATie(new RawCallback(args, ME));
         BlasterCallback callback = BlasterCallbackHelper.narrow(rootPOA.servant_to_reference( callbackTie ));

         rootPOA.the_POAManager().activate();

         //----------- Login to the server -----------------------
         try {
            String passwd = "some";

            // Create a XML based qos (quality of service) which hold the IOR (the CORBA
            // address of our callback server)
            String qos = "<qos><callback type='IOR'>";
            qos += orb.object_to_string(callback);
            qos += "</callback></qos>";

            // The xmlBlaster server takes this IOR string and uses it to connect
            // to our client-side callback interface to deliver updates back

            xmlBlaster = authServer.login(loginName, passwd, qos);
            log.info(ME, "Login done");
         } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
            log.warn(ME, "XmlBlasterException: " + e.getMessage());
         }


         //----------- Subscribe to messages with XPATH -------
         {
            log.trace(ME, "Subscribing using XPath syntax ...");
            String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                           "<key oid='' queryType='XPATH'>\n" +
                           "/xmlBlaster/key/AGENT" +
                           "</key>";
            stop.restart();
            try {
               xmlBlaster.subscribe(xmlKey, "<qos></qos>");
            } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
               log.warn(ME, "XmlBlasterException: " + e.getMessage());
            }
            log.info(ME, "Subscribe done, there should be no Callback" + stop.nice());
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
            log.info(ME, "Publishing ...");
            stop.restart();
            try {
               String publishOid = xmlBlaster.publish(msgUnit);
               log.trace(ME, "Returned oid=" + publishOid);
            } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
               log.warn(ME, "XmlBlasterException: " + e.getMessage());
            }
            log.info(ME, "Publishing done, there should be a callback now" + stop.nice());
         }

         delay(1000); // Wait some time ...

         // orb.run(); // Usually your client won't exit after this, uncomment the run() method

         ask("logout()");

         //----------- Logout --------------------------------------
         log.info(ME, "Logout ...");
         try {
            authServer.logout(xmlBlaster);
         } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
            log.warn(ME, "XmlBlasterException: " + e.getMessage());
         }

         //----------- Shutdown my callback server -----------------
         try {
            rootPOA.deactivate_object(rootPOA.reference_to_id(callback));
         } catch(Exception e) { log.warn(ME, "POA deactivate callback failed"); }


         //----------- Stop the POA --------------------------------
         try {
            rootPOA.the_POAManager().deactivate(false, true);
         } catch(Exception e) { log.warn(ME, "POA deactivate failed"); }

         //----------- Shutdown the ORB ----------------------------
         orb.shutdown(true);
      }
      catch (Exception e) {
          log.error(ME, e.toString());
          e.printStackTrace();
      }
   }


   private void delay(long millis) {
      try {
          Thread.currentThread().sleep(millis);
      }
      catch( InterruptedException i)
      {}
   }


   private void ask(String text) {
      log.plain(ME, text);
      log.plain(ME, "################### Hit a key to continue ###################");
      try {
         System.in.read();
      } catch (java.io.IOException e) {}
   }

   static void usage() {
      System.out.println("\nAvailable options:");
      System.out.println("   -loginName                               The login name [ClientRaw].");
      System.out.println("   -dispatch/connection/plugin/ior/iorFile  File with the IOR string from xmlBlaster.");
      System.out.println("   -dispatch/callback/plugin/ior/iorString  The raw IOR string from xmlBlaster.");
      System.out.println("Example: jaco javaclients.corba.ClientRaw -dispatch/connection/plugin/ior/iorFile /tmp/NS_Ref\n");
      System.exit(1);
   }

   public static void main(String args[]) {
      new ClientRaw(args);
   }
} // ClientRaw


/**
 * Example for a callback implementation, used by the demo ClientRaw.
 */
class RawCallback implements BlasterCallbackOperations
{
   final String ME;
   final Global glob;
   final LogChannel log;

   /**
    * Construct it.
    */
   public RawCallback(String[] args, java.lang.String name) {
      this.ME = "RawCallback-" + name;
      glob = new Global(args);
      log = glob.getLog("client");
      if (log.CALL) log.trace(ME, "Entering constructor with argument");
   }


   /**
    * This is the callback method invoked from the server
    * informing the client in an asynchronous mode about new messages
    */
   public String[] update(String cbSessionId, MessageUnit[] msgUnitArr)
   {
      String[] ret = new String[msgUnitArr.length];
      for (int ii=0; ii<msgUnitArr.length; ii++) {
         MessageUnit msgUnit = msgUnitArr[ii];
         UpdateKey key = null;
         UpdateQos qos = null;
         try { // SAX parse the received message key and QoS:
            key = new UpdateKey(glob, msgUnit.xmlKey);
            qos = new UpdateQos(glob, msgUnit.qos);
         } catch (org.xmlBlaster.util.XmlBlasterException e) {
            log.error(ME, e.getMessage());
         }
         log.plain(ME, "\n================== BlasterCallback update START =============");
         log.plain(ME, "Callback invoked for " + key.toString() + " content length = " + msgUnit.content.length);
         log.plain(ME, new String(msgUnit.content));
         log.plain(ME, "================== BlasterCallback update END ===============\n");
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
         log.error(ME, "updateOneway() failed, exception is not sent to xmlBlaster: " + e.toString());
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
      return "";
   }
} // RawCallback

