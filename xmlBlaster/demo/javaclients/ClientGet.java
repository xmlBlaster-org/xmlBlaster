/*------------------------------------------------------------------------------
Name:      ClientGet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientGet.java,v 1.5 2000/01/30 18:44:51 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients;

import org.xmlBlaster.util.*;
import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.clientIdl.*;


/**
 * This client demonstrates the method get().
 * <p>
 * It doesn't implement a Callback server, since it only access xmlBlaster
 * using the synchronous get() method.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    ${JacORB_HOME}/bin/jaco javaclients.ClientGet -iorFile /tmp/NS_Ref
 *
 *    ${JacORB_HOME}/bin/jaco javaclients.ClientGet -name "Jeff" -iorFile /tmp/NS_Ref
 * </pre>
 */
public class ClientGet
{
   private Server xmlBlaster = null;
   private static String ME = "Heidi";

   public ClientGet(String args[])
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


         //----------- get() the previous message OID -------
         {
            Log.trace(ME, "get() using the exact oid ...");
            String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                            "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                            "</key>";
            stop.restart();
            MessageUnitContainer[] msgArr = null;
            try {
               msgArr = xmlBlaster.get(xmlKey, qos);
            } catch(XmlBlasterException e) {
               Log.error(ME, "XmlBlasterException: " + e.reason);
            }

            Log.info(ME, "Got " + msgArr.length + " messages:");
            for (int ii=0; ii<msgArr.length; ii++) {
               Log.plain(ME, msgArr[ii].messageUnit.xmlKey +
                          "\n################### RETURN CONTENT: ##################\n\n" +
                           new String(msgArr[ii].messageUnit.content) +
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


         //----------- get() with XPath -------
         Log.trace(ME, "get() using the Xpath query syntax ...");
         String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                         "<key oid='' queryType='XPATH'>\n" +
                         "   //DRIVER[@id='ProgramExecute']" +
                         "</key>";
         stop.restart();
         MessageUnitContainer[] msgArr = null;
         try {
            msgArr = xmlBlaster.get(xmlKey, qos);
         } catch(XmlBlasterException e) {
            Log.error(ME, "XmlBlasterException: " + e.reason);
         }

         if (msgArr.length == 1)
            Log.info(ME, "Got " + msgArr.length + " messages:");
         else
            Log.error(ME, "Got " + msgArr.length + " messages:");
         for (int ii=0; ii<msgArr.length; ii++) {
            Log.plain(ME, msgArr[ii].messageUnit.xmlKey +
                          "\n################### RETURN CONTENT: ##################\n\n" +
                          new String(msgArr[ii].messageUnit.content) +
                          "\n\n#######################################");
         }


         Util.ask("logout()");
         corbaConnection.logout(xmlBlaster);

         // corbaConnection.getOrb().run(); // Usually your client won't exit after this, uncomment the run() method
      }
      catch (XmlBlasterException e) {
          Log.error(ME, "Error occurred: " + e.toString());
          e.printStackTrace();
      }
   }


   /**
    */
   public static void main(String args[])
   {
      Log.setLogLevel(args);
      new ClientGet(args);
      Log.exit(ClientGet.ME, "Good bye");
   }
}
