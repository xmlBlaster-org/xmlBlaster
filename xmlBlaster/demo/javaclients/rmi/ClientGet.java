/*------------------------------------------------------------------------------
Name:      ClientGet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster with RMI
Version:   $Id: ClientGet.java,v 1.6 2000/06/26 06:47:01 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients.rmi;

import org.jutils.log.Log;
import org.jutils.init.Args;
import org.jutils.time.StopWatch;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.MessageUnit;

import org.xmlBlaster.protocol.rmi.I_XmlBlaster;
import org.xmlBlaster.protocol.rmi.I_AuthServer;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.RMISecurityManager;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.net.MalformedURLException;


/**
 * This client demonstrates the method get() with RMI access.
 * <p>
 * It doesn't implement a Callback server, since it only access xmlBlaster
 * using the synchronous get() method.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *  ${JacORB_HOME}/bin/jaco javaclients.rmi.ClientGet
 *
 *  ${JacORB_HOME}/bin/jaco javaclients.rmi.ClientGet -name "Jeff"
 *
 *  Options:
 *     -rmi.hostname localhost       // Where the server rmi registry is
 *     -rmi.RegistryPort 1099        // Port of server rmi registry
 *
 *  or directly:
 *     -rmi.AuthServer.url "rmi://localhost:1099/I_AuthServer"
 *     -rmi.XmlBlaster.url "rmi://localhost:1099/I_XmlBlaster"
 *
 * </pre>
 * @see http://java.sun.com/products/jdk/1.2/docs/guide/rmi/faq.html
 * @see http://archives.java.sun.com/archives/rmi-users.html
 */
public class ClientGet
{
   private static String ME = "Heidi";
   private I_AuthServer authServer = null;
   private I_XmlBlaster blasterServer = null;
   private String sessionId = null;

   public ClientGet(String args[])
   {
      // Initialize command line argument handling (this is optional)
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.plain("\nAvailable options:");
         Log.plain("   -name               The login name [ClientSub].");
         Log.plain("   -passwd             The password [secret].");
         // !!! RmiConnection.usage();
         Log.usage();
         Log.plain("Example: jaco javaclients.ClientXml -name Jeff\n");
         Log.panic(ME, e.toString());
      }

      try {
         // check if parameter -name <userName> is given at startup of client
         String loginName = Args.getArg(args, "-name", ME);
         String passwd = Args.getArg(args, "-passwd", "secret");

         initRmi(args);

         Log.info(ME, "Trying login to '" + loginName + "'");
         sessionId = authServer.login(loginName, passwd, "<qos></qos>");

         String publishOid = "";
         StopWatch stop = new StopWatch();

         //----------- Construct a message and publish it ---------
         {
            String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                            "<key oid='' contentMime='text/xml'>" +
                            "   <AGENT id='192.168.124.10' subId='1' type='generic'>" +
                            "      <DRIVER id='FileProof' pollingFreq='10'>" +
                            "      </DRIVER>"+
                            "   </AGENT>" +
                            "</key>";
            String content = "<file><size>1024 kBytes</size><creation>1.1.2000</creation></file>";
            MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), "<qos></qos>");
            Log.trace(ME, "Publishing ...");
            stop.restart();
            try {
               publishOid = blasterServer.publish(sessionId, msgUnit);
               Log.info(ME, "   Returned oid=" + publishOid);
            } catch(XmlBlasterException e) {
               Log.warning(ME, "XmlBlasterException: " + e.reason);
            }
            Log.trace(ME, "Publishing done" + stop.nice());
         }


         //----------- get() the previous message OID -------
         {
            Log.trace(ME, "get() using the exact oid ...");
            String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                            "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                            "</key>";
            stop.restart();
            MessageUnit[] msgArr = null;
            try {
               msgArr = blasterServer.get(sessionId, xmlKey, "<qos></qos>");
            } catch(XmlBlasterException e) {
               Log.error(ME, "XmlBlasterException: " + e.reason);
            }

            Log.info(ME, "Got " + msgArr.length + " messages:");
            for (int ii=0; ii<msgArr.length; ii++) {
               Log.plain(ME, msgArr[ii].xmlKey +
                          "\n################### RETURN CONTENT: ##################\n\n" +
                           new String(msgArr[ii].content) +
                          "\n\n#######################################");
            }
         }


         //----------- Construct a second message and publish it ---------
         {
            String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                            "<key oid='Export-11' contentMime='text/plain'>" +
                            "<AGENT id='192.168.124.29' subId='1' type='generic'>" +
                               "<DRIVER id='ProgramExecute'>" +
                                  "<EXECUTABLE>export</EXECUTABLE>" +
                                  "<FILE>out.txt</FILE>" +
                               "</DRIVER>" +
                            "</AGENT>" +
                            "</key>";
            String content = "Export program started";
            MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), "<qos></qos>");
            Log.trace(ME, "Publishing ...");
            stop.restart();
            try {
               publishOid = blasterServer.publish(sessionId, msgUnit);
               Log.info(ME, "   Returned oid=" + publishOid);
            } catch(XmlBlasterException e) {
               Log.warning(ME, "XmlBlasterException: " + e.reason);
            }
            Log.trace(ME, "Publishing done" + stop.nice());
         }


         //----------- get() with XPath -------
         Log.trace(ME, "get() using the Xpath query syntax ...");
         String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                         "   //DRIVER[@id='ProgramExecute']" +
                         "</key>";
         stop.restart();
         MessageUnit[] msgArr = null;
         try {
            msgArr = blasterServer.get(sessionId, xmlKey, "<qos></qos>");
         } catch(XmlBlasterException e) {
            Log.error(ME, "XmlBlasterException: " + e.reason);
         }

         if (msgArr.length == 1)
            Log.info(ME, "Got " + msgArr.length + " messages:");
         else
            Log.error(ME, "Got " + msgArr.length + " messages:");
         for (int ii=0; ii<msgArr.length; ii++) {
            Log.plain(ME, msgArr[ii].xmlKey +
                          "\n################### RETURN CONTENT: ##################\n\n" +
                          new String(msgArr[ii].content) +
                          "\n\n#######################################");
         }


         authServer.logout(sessionId);
      }
      catch (RemoteException e) {
         Log.error(ME, "Error occurred: " + e.toString());
         e.printStackTrace();
      }
      catch (XmlBlasterException e) {
         Log.error(ME, "XmlBlaster error occurred: " + e.toString());
         e.printStackTrace();
      }
      catch (org.jutils.JUtilsException e) {
         Log.error(ME, e.toString());
      }
   }


   /**
    * Connect to RMI server.
    * @param args
    */
   private void initRmi(String[] args) throws XmlBlasterException
   {
      // Create and install a security manager
      if (System.getSecurityManager() == null) {
         System.setSecurityManager(new RMISecurityManager());
         if (Log.TRACE) Log.trace(ME, "Started RMISecurityManager");
      }

      String hostname;
      try  {
         java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
         hostname = addr.getHostName();
      } catch (Exception e) {
         Log.warning(ME, "Can't determin your hostname");
         hostname = "localhost";
      }
      hostname = XmlBlasterProperty.get("rmi.hostname", hostname);

      // default xmlBlaster RMI publishing port is 1099
      int registryPort = XmlBlasterProperty.get("rmi.RegistryPort",
                         org.xmlBlaster.protocol.rmi.RmiDriver.DEFAULT_REGISTRY_PORT);
      String prefix = "rmi://" + hostname + ":" + registryPort + "/";


      String authServerUrl = prefix + "I_AuthServer";
      String addr = XmlBlasterProperty.get("rmi.AuthServer.url", authServerUrl);
      Remote rem = lookup(addr);
      if (rem instanceof org.xmlBlaster.protocol.rmi.I_AuthServer) {
         authServer = (I_AuthServer)rem;
         Log.info(ME, "Accessing reference using given '" + addr + "' string");
      }
      else {
         throw new XmlBlasterException("InvalidRmiCallback", "No to '" + addr + "' possible, class needs to implement interface I_AuthServer.");
      }


      String xmlBlasterUrl = prefix + "I_XmlBlaster";
      addr = XmlBlasterProperty.get("rmi.XmlBlaster.url", xmlBlasterUrl);
      rem = lookup(addr);
      if (rem instanceof org.xmlBlaster.protocol.rmi.I_XmlBlaster) {
         blasterServer = (I_XmlBlaster)rem;
         Log.info(ME, "Accessing reference using given '" + addr + "' string");
      }
      else {
         throw new XmlBlasterException("InvalidRmiCallback", "No to '" + addr + "' possible, class needs to implement interface I_XmlBlaster.");
      }
   }


   /**
    * Connect to RMI server.
    * @param args
    */
   private Remote lookup(String addr) throws XmlBlasterException
   {
      try {
         return Naming.lookup(addr);
      }
      catch (RemoteException e) {
         Log.error(ME, "Can't access address ='" + addr + "', no rmi registry running");
         throw new XmlBlasterException("CallbackHandleInvalid", "Can't access address ='" + addr + "', no rmi registry running");
      }
      catch (NotBoundException e) {
         Log.error(ME, "The given address ='" + addr + "' is not bound to rmi registry: " + e.toString());
         throw new XmlBlasterException("CallbackHandleInvalid", "The given address '" + addr + "' is not bound to rmi registry: " + e.toString());
      }
      catch (MalformedURLException e) {
         Log.error(ME, "The given address ='" + addr + "' is invalid: " + e.toString());
         throw new XmlBlasterException("CallbackHandleInvalid", "The given address '" + addr + "' is invalid: " + e.toString());
      }
      catch (Throwable e) {
         Log.error(ME, "The given address ='" + addr + "' is invalid : " + e.toString());
         throw new XmlBlasterException("CallbackHandleInvalid", "The given address '" + addr + "' is invalid : " + e.toString());
      }
   }


   /**
    */
   public static void main(String args[])
   {
      new ClientGet(args);
      Log.exit(ClientGet.ME, "Good bye");
   }
}
