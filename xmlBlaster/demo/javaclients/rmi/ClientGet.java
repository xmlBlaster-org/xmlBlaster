/*------------------------------------------------------------------------------
Name:      ClientGet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster with RMI
Version:   $Id: ClientGet.java,v 1.15 2002/11/26 12:36:30 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients.rmi;

import org.jutils.time.StopWatch;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
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
 *  java -Djava.security.policy=${XMLBLASTER_HOME}/config/xmlBlaster.policy  javaclients.rmi.ClientGet -loginName Jeff
 *
 *  Options:
 *     -rmi.hostname localhost       // Where the server rmi registry is
 *     -rmi.registryPort 1099        // Port of server rmi registry
 *
 *  or directly:
 *     -rmi.AuthServer.url "rmi://localhost:1099/I_AuthServer"
 *     -rmi.XmlBlaster.url "rmi://localhost:1099/I_XmlBlaster"
 *
 * </pre>
 * @see <a href="http://java.sun.com/products/jdk/1.2/docs/guide/rmi/faq.html" target="others">RMI FAQ</a>
 * @see <a href="http://archives.java.sun.com/archives/rmi-users.html" target="others">RMI USERS</a>
 */
public class ClientGet
{
   private static String ME = "Heidi";
   private final Global glob;
   private final LogChannel log;
   private I_AuthServer authServer = null;
   private I_XmlBlaster blasterServer = null;
   private String sessionId = null;

   public ClientGet(Global glob) {
      this.glob = glob;
      this.log = glob.getLog("client");
      try {
         initRmi();

         String loginName = glob.getProperty().get("loginName", "RMIClient");
         String passwd = glob.getProperty().get("passwd", "secret");
         ConnectQos qos = new ConnectQos(glob, loginName, passwd);
         log.info(ME, "Trying login to '" + loginName + "'");
         sessionId = authServer.connect(qos.toXml());

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
            log.trace(ME, "Publishing ...");
            stop.restart();
            try {
               publishOid = blasterServer.publish(sessionId, msgUnit);
               log.info(ME, "   Returned oid=" + publishOid);
            } catch(XmlBlasterException e) {
               log.warn(ME, "XmlBlasterException: " + e.getMessage());
            }
            log.trace(ME, "Publishing done" + stop.nice());
         }


         //----------- get() the previous message OID -------
         {
            log.trace(ME, "get() using the exact oid ...");
            String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                            "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                            "</key>";
            stop.restart();
            MessageUnit[] msgArr = null;
            try {
               msgArr = blasterServer.get(sessionId, xmlKey, "<qos></qos>");
            } catch(XmlBlasterException e) {
               log.error(ME, "XmlBlasterException: " + e.getMessage());
            }

            log.info(ME, "Got " + msgArr.length + " messages:");
            for (int ii=0; ii<msgArr.length; ii++) {
               log.plain(ME, msgArr[ii].getKey() +
                          "\n################### RETURN CONTENT: ##################\n\n" +
                           new String(msgArr[ii].getContent()) +
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
            log.trace(ME, "Publishing ...");
            stop.restart();
            try {
               publishOid = blasterServer.publish(sessionId, msgUnit);
               log.info(ME, "   Returned oid=" + publishOid);
            } catch(XmlBlasterException e) {
               log.warn(ME, "XmlBlasterException: " + e.getMessage());
            }
            log.trace(ME, "Publishing done" + stop.nice());
         }


         //----------- get() with XPath -------
         log.trace(ME, "get() using the Xpath query syntax ...");
         String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                         "   //DRIVER[@id='ProgramExecute']" +
                         "</key>";
         stop.restart();
         MessageUnit[] msgArr = null;
         try {
            msgArr = blasterServer.get(sessionId, xmlKey, "<qos></qos>");
         } catch(XmlBlasterException e) {
            log.error(ME, "XmlBlasterException: " + e.getMessage());
         }

         if (msgArr.length == 1)
            log.info(ME, "Got " + msgArr.length + " messages:");
         else
            log.error(ME, "Got " + msgArr.length + " messages:");
         for (int ii=0; ii<msgArr.length; ii++) {
            log.plain(ME, msgArr[ii].getKey() +
                          "\n################### RETURN CONTENT: ##################\n\n" +
                          new String(msgArr[ii].getContent()) +
                          "\n\n#######################################");
         }


         authServer.disconnect(sessionId, "<qos/>");
      }
      catch (RemoteException e) {
         log.error(ME, "Error occurred: " + e.toString());
         e.printStackTrace();
      }
      catch (XmlBlasterException e) {
         log.error(ME, "XmlBlaster error occurred: " + e.toString());
         e.printStackTrace();
      }
   }


   /**
    * Connect to RMI server.
    * @param args
    */
   private void initRmi() throws XmlBlasterException {
      // Create and install a security manager
      if (System.getSecurityManager() == null) {
         System.setSecurityManager(new RMISecurityManager());
         if (log.TRACE) log.trace(ME, "Started RMISecurityManager");
      }

      String hostname;
      try  {
         java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
         hostname = addr.getHostName();
      } catch (Exception e) {
         log.warn(ME, "Can't determin your hostname");
         hostname = "localhost";
      }
      hostname = glob.getProperty().get("rmi.hostname", hostname);

      // default xmlBlaster RMI publishing port is 1099
      int registryPort = glob.getProperty().get("rmi.registryPort",
                         org.xmlBlaster.protocol.rmi.RmiDriver.DEFAULT_REGISTRY_PORT);
      String prefix = "rmi://" + hostname + ":" + registryPort + "/";


      String authServerUrl = prefix + "I_AuthServer";
      String addr = glob.getProperty().get("rmi.AuthServer.url", authServerUrl);
      Remote rem = lookup(addr);
      if (rem instanceof org.xmlBlaster.protocol.rmi.I_AuthServer) {
         authServer = (I_AuthServer)rem;
         log.info(ME, "Accessing reference using given '" + addr + "' string");
      }
      else {
         throw new XmlBlasterException("InvalidRmiCallback", "No to '" + addr + "' possible, class needs to implement interface I_AuthServer.");
      }


      String xmlBlasterUrl = prefix + "I_XmlBlaster";
      addr = glob.getProperty().get("rmi.XmlBlaster.url", xmlBlasterUrl);
      rem = lookup(addr);
      if (rem instanceof org.xmlBlaster.protocol.rmi.I_XmlBlaster) {
         blasterServer = (I_XmlBlaster)rem;
         log.info(ME, "Accessing reference using given '" + addr + "' string");
      }
      else {
         throw new XmlBlasterException("InvalidRmiCallback", "No to '" + addr + "' possible, class needs to implement interface I_XmlBlaster.");
      }
   }


   /**
    * Connect to RMI server.
    * @param args
    */
   private Remote lookup(String addr) throws XmlBlasterException {
      try {
         return Naming.lookup(addr);
      }
      catch (RemoteException e) {
         log.error(ME, "Can't access address ='" + addr + "', no rmi registry running");
         throw new XmlBlasterException("CallbackHandleInvalid", "Can't access address ='" + addr + "', no rmi registry running");
      }
      catch (NotBoundException e) {
         log.error(ME, "The given address ='" + addr + "' is not bound to rmi registry: " + e.toString());
         throw new XmlBlasterException("CallbackHandleInvalid", "The given address '" + addr + "' is not bound to rmi registry: " + e.toString());
      }
      catch (MalformedURLException e) {
         log.error(ME, "The given address ='" + addr + "' is invalid: " + e.toString());
         throw new XmlBlasterException("CallbackHandleInvalid", "The given address '" + addr + "' is invalid: " + e.toString());
      }
      catch (Throwable e) {
         log.error(ME, "The given address ='" + addr + "' is invalid : " + e.toString());
         throw new XmlBlasterException("CallbackHandleInvalid", "The given address '" + addr + "' is invalid : " + e.toString());
      }
   }


   /**
    */
   public static void main(String args[]) {
      new ClientGet(new Global(args));
   }
}
