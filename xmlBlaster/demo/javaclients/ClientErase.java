/*------------------------------------------------------------------------------
Name:      ClientErase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientErase.java,v 1.3 1999/12/14 12:19:54 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients;

import org.xmlBlaster.util.*;
import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.clientIdl.*;
import jacorb.naming.NameServer;
import org.omg.CosNaming.*;


/**
 * This client demonstrates the method erase().
 * <p>
 * It doesn't implement a Callback server.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    ${JacORB_HOME}/bin/jaco javaclients.ClientErase
 *
 *    ${JacORB_HOME}/bin/jaco javaclients.ClientErase -name "Jeff"
 * </pre>
 */
public class ClientErase
{
   private Server xmlBlaster = null;
   private static String ME = "Heidi";

   public ClientErase(String args[])
   {
      try {
         // check if parameter -name <userName> is given at startup of client
         ME = Args.getArg(args, "-name", ME);
         String loginName = ME;

         //----------- Find orb ----------------------------------
         CorbaConnection corbaConnection = new CorbaConnection(args);

         //----------- Login to xmlBlaster -----------------------
         String qos = "";
         String passwd = "some";
         xmlBlaster = corbaConnection.login(loginName, passwd, (BlasterCallback)null, qos);

         StopWatch stop = new StopWatch();


         String publishOid = "";
         //----------- Construct a message and publish it ---------
         {
            String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                            "<key oid='' contentMime='text/xml'>" +
                            "   <AGENT id='192.168.124.20' subId='1' type='generic'>" +
                            "      <DRIVER id='FileProof' pollingFreq='10'>" +
                            "      </DRIVER>"+
                            "   </AGENT>" +
                            "</key>";
            String content = "<file><size>1024 kBytes</size><creation>1.1.2000</creation></file>";
            MessageUnit messageUnit = new MessageUnit(xmlKey, content.getBytes());
            Log.trace(ME, "Publishing ...");
            stop.restart();
            try {
               publishOid = xmlBlaster.publish(messageUnit, "<qos></qos>");
               Log.info(ME, "   Returned oid=" + publishOid);
            } catch(XmlBlasterException e) {
               Log.warning(ME, "XmlBlasterException: " + e.reason);
            }
            Log.trace(ME, "Publishing done" + stop.nice());
         }


         //----------- erase() the previous message OID -------
         {
            Log.trace(ME, "erase() using the exact oid ...");
            String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                            "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                            "</key>";
            stop.restart();
            String[] strArr = null;
            try {
               strArr = xmlBlaster.erase(xmlKey, qos);
            } catch(XmlBlasterException e) {
               Log.error(ME, "XmlBlasterException: " + e.reason);
            }

            if (strArr.length == 1)
               Log.info(ME, "Erased " + strArr.length + " message:");
            else
               Log.error(ME, "Erased " + strArr.length + " messages:");
            for (int ii=0; ii<strArr.length; ii++) {
               Log.info(ME, "Erased message oid=" + strArr[ii]);
            }
         }

         Log.warning(ME, "Testcase for XPath erase() is still missing");


         //----------- logout() -----------------------------
         Util.ask("logout()");
         corbaConnection.logout(xmlBlaster);
      }
      catch (Exception e) {
          e.printStackTrace();
      }
      
      // corbaConnection.getOrb().run(); // Usually your client won't exit after this, uncomment the run() method
   }


   public static void main(String args[])
   {
      new ClientErase(args);
      Log.exit(ClientErase.ME, "Good bye");
   }
}
