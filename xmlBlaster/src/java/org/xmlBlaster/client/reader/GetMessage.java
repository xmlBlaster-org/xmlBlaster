/*------------------------------------------------------------------------------
Name:      GetMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code to get from command line a message
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.reader;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.jutils.init.Args;
import org.jutils.JUtilsException;

import org.xmlBlaster.client.I_XmlBlasterAccess;
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
   private static Logger log = Logger.getLogger(GetMessage.class.getName());
   private I_XmlBlasterAccess xmlBlasterConnection;

   /**
    * Constructs the GetMessage object.
    * <p />
    * Start with parameter -? to get a usage description.<br />
    * These command line parameters are not merged with xmlBlaster.property properties.
    * @param args      Command line arguments
    */
   public GetMessage(Global glob) {
      this.glob = glob;

   }

   /**
    * Get the message from xmlBlaster. 
    */
   public void get() throws Exception {
      String oidString = glob.getProperty().get("oid", (String)null);
      String xpathString = glob.getProperty().get("xpath", (String)null);

      if (oidString == null && xpathString == null) {
         usage();
         log.severe("Specify -oid <message-oid> or -xpath <query>");
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
         xmlBlasterConnection = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob);
         xmlBlasterConnection.connect(qos, null); // Login to xmlBlaster
      }
      catch (Exception e) {
         log.severe(e.toString());
         e.printStackTrace();
      }

      GetKey xmlKeyWr = new GetKey(glob, xmlKey, queryType);
      GetQos xmlQos = new GetQos(glob);
      MsgUnit[] msgs = xmlBlasterConnection.get(xmlKeyWr.toXml(), xmlQos.toXml());
      log.info("Got " + msgs.length + " messages for '" + xmlKey + "'");
      for (int ii=0; ii<msgs.length; ii++) {
         System.out.println("\n" + msgs[ii].toXml());
      }

      xmlBlasterConnection.disconnect(null);
   }

   /**
    * Command line usage.
    */
   private void usage() {
      System.out.println("----------------------------------------------------------");
      System.out.println("java org.xmlBlaster.client.reader.GetMessage <options>");
      System.out.println("----------------------------------------------------------");
      System.out.println("Options:");
      System.out.println("   -?                  Print this message.");
      System.out.println("");
      System.out.println("   -oid <XmlKeyOid>    The unique oid of the message");
      System.out.println("   -xpath <XPATH>      The XPATH query");
      //I_XmlBlasterAccess.usage();
      //log.usage();
      System.out.println("----------------------------------------------------------");
      System.out.println("Example:");
      System.out.println("java org.xmlBlaster.client.reader.GetMessage -oid mySpecialMessage");
      System.out.println("");
      System.out.println("java org.xmlBlaster.client.reader.GetMessage -oid __cmd:?freeMem");
      System.out.println("");
      System.out.println("java org.xmlBlaster.client.reader.GetMessage -xpath //key/CAR");
      System.out.println("----------------------------------------------------------");
      System.out.println("");
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

