/*------------------------------------------------------------------------------
Name:      GetMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code to get from command line a message
Version:   $Id: GetMessage.java,v 1.8 2003/03/08 02:06:22 laghi Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.reader;

import org.jutils.log.LogChannel;
import org.jutils.init.Args;
import org.jutils.JUtilsException;

import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;


/**
 * Get from command line a message.
 * <br />
 * Use this as a command line tool to get for messages from xmlBlaster,
 * for example for debugging reasons.
 * Invoke examples:<br />
 * <pre>
 *    java org.xmlBlaster.client.reader.GetMessage  -loginName  Tim  -passwd  secret  -oid  __cmd:?totalMem
 * </pre>
 * For other supported options type
 * <pre>
 *    java org.xmlBlaster.client.reader.GetMessage -?
 * </pre>
 */
public class GetMessage
{
   private static final String ME = "GetMessage";
   private final Global glob;
   private final LogChannel log;
   private XmlBlasterConnection xmlBlasterConnection;

   /**
    * Constructs the GetMessage object.
    * <p />
    * Start with parameter -? to get a usage description.<br />
    * These command line parameters are not merged with xmlBlaster.property properties.
    * @param args      Command line arguments
    */
   public GetMessage(Global glob) {
      this.glob = glob;
      this.log = glob.getLog(null);
   }

   /**
    * Get the message from xmlBlaster. 
    */
   public void get() throws Exception {
      String oidString = glob.getProperty().get("oid", (String)null);
      String xpathString = glob.getProperty().get("xpath", (String)null);

      if (oidString == null && xpathString == null) {
         usage();
         log.error(ME, "Specify -oid <message-oid> or -xpath <query>");
         System.exit(1);
      }

      String xmlKey;
      String queryType;
      if (oidString != null) {
         xmlKey = oidString;
         queryType = "EXACT";
      }
      else {
         xmlKey = xpathString;
         queryType = "XPATH";
      }

      try {
         xmlBlasterConnection = new XmlBlasterConnection(glob);
         ConnectQos qos = new ConnectQos(glob);
         xmlBlasterConnection.connect(qos, null); // Login to xmlBlaster
      }
      catch (Exception e) {
         log.error(ME, e.toString());
         e.printStackTrace();
      }

      GetKey xmlKeyWr = new GetKey(glob, xmlKey, queryType);
      GetQos xmlQos = new GetQos(glob);
      MsgUnit[] msgs = xmlBlasterConnection.get(xmlKeyWr.toXml(), xmlQos.toXml());
      log.info(ME, "Got " + msgs.length + " messages for '" + xmlKey + "'");
      for (int ii=0; ii<msgs.length; ii++) {
         System.out.println("\n" + msgs[ii].toXml());
      }

      xmlBlasterConnection.disconnect(null);
   }

   /**
    * Command line usage.
    */
   private void usage() {
      log.plain(ME, "----------------------------------------------------------");
      log.plain(ME, "java org.xmlBlaster.client.reader.GetMessage <options>");
      log.plain(ME, "----------------------------------------------------------");
      log.plain(ME, "Options:");
      log.plain(ME, "   -?                  Print this message.");
      log.plain(ME, "");
      log.plain(ME, "   -oid <XmlKeyOid>    The unique oid of the message");
      log.plain(ME, "   -xpath <XPATH>      The XPATH query");
      //XmlBlasterConnection.usage();
      //log.usage();
      log.plain(ME, "----------------------------------------------------------");
      log.plain(ME, "Example:");
      log.plain(ME, "java org.xmlBlaster.client.reader.GetMessage -oid mySpecialMessage");
      log.plain(ME, "");
      log.plain(ME, "java org.xmlBlaster.client.reader.GetMessage -oid __cmd:?freeMem");
      log.plain(ME, "");
      log.plain(ME, "java org.xmlBlaster.client.reader.GetMessage -xpath //key/CAR");
      log.plain(ME, "----------------------------------------------------------");
      log.plain(ME, "");
   }


   /**
    * Invoke:  java org.xmlBlaster.client.reader.GetMessage  -loginName Tim  -passwd secret  -oid __cmd:?totalMem
    */
   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         GetMessage getter = new GetMessage(glob);
         getter.usage();
         System.exit(0);
      }
      try {
         GetMessage getter = new GetMessage(glob);
         getter.get();
      } 
      catch (XmlBlasterException e) {
         System.out.println("ERROR: " + e.getMessage());
      }
      catch (Throwable e) {
         e.printStackTrace();
         System.out.println("ERROR: " + e.toString());
      }
   }
}

