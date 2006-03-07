/*------------------------------------------------------------------------------
Name:      SubscribeMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code to subscribe from command line for a message
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.reader;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;


/**
 * Subscribe from command line for a message.
 * <br />
 * Use this as a command line tool to subscribe for messages from xmlBlaster,
 * for example for debugging reasons.
 * Invoke examples:<br />
 * <pre>
 *    java org.xmlBlaster.client.reader.SubscribeMessage  -session.name Tim  -passwd  secret  -oid  __cmd:?totalMem
 * </pre>
 * For other supported options type
 * <pre>
 *    java org.xmlBlaster.client.reader.SubscribeMessage -?
 * </pre>
 */
public class SubscribeMessage implements I_Callback
{
   private static final String ME = "SubscribeMessage";
   private final Global glob;
   private static Logger log = Logger.getLogger(SubscribeMessage.class.getName());
   private I_XmlBlasterAccess xmlBlasterConnection;
   private String subscriptionHandle;

   /**
    * Constructs the SubscribeMessage object.
    * <p />
    * Start with parameter -? to get a usage description.<br />
    * These command line parameters are not merged with xmlBlaster.property properties.
    * @param args      Command line arguments
    */
   public SubscribeMessage(Global glob) throws XmlBlasterException
   {
      this.glob = glob;


      String oidString = glob.getProperty().get("oid", (String)null);
      String xpathString = glob.getProperty().get("xpath", (String)null);

      if (oidString == null && xpathString == null) {
         usage();
         log.severe("Specify the message oid or a xpath query");
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

      setUp();  // login
      subscriptionHandle = subscribe(xmlKey, queryType);

      try { Thread.sleep(10000000L); } catch (Exception e) { }
      log.warning("Bye, time is over.");
   }


   /**
    * Open the connection, and subscribe to the message
    */
   public SubscribeMessage(Global glob, String xmlKey, String queryType)
   {
      this.glob = glob;

      setUp();  // login
      subscriptionHandle = subscribe(xmlKey, queryType);
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   private void setUp()
   {
      try {
         xmlBlasterConnection = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob);
         xmlBlasterConnection.connect(qos, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          log.severe(e.toString());
          e.printStackTrace();
      }
   }


   /**
    * Logout from xmlBlaster
    */
   private void tearDown()
   {
      unSubscribe(subscriptionHandle);
      xmlBlasterConnection.disconnect(null);
   }


   private String subscribe(String xmlKey, String queryType)
   {
      try {
         SubscribeKey xmlKeyWr = new SubscribeKey(glob, xmlKey, queryType);
         SubscribeQos xmlQos = new SubscribeQos(glob);
         SubscribeReturnQos ret = xmlBlasterConnection.subscribe(xmlKeyWr.toXml(), xmlQos.toXml());
         String subscriptionId = ret.getSubscriptionId();
         log.info("Subscribed to [" + xmlKey + "] " + queryType + ", subscriptionId=" + subscriptionId);
         return subscriptionId;
      } catch(XmlBlasterException e) {
         log.severe("XmlBlasterException:\n" + e.getMessage());
         System.exit(1);
      }
      return null;
   }


   /**
    * Unsubscribe from given subscription
    * @param subscriptionId The id you got from your subscription
    */
   private void unSubscribe(String subscriptionId)
   {
      if (subscriptionId == null || subscriptionId.length() < 1) return;
      try {
         SubscribeKey xmlKey = new SubscribeKey(glob, subscriptionId);
         SubscribeQos xmlQos = new SubscribeQos(glob);
         xmlBlasterConnection.unSubscribe(xmlKey.toXml(), xmlQos.toXml());
         if (log.isLoggable(Level.FINE)) log.fine("Unsubscribed from " + subscriptionId + " (GML and XML Packages)");
      } catch(XmlBlasterException e) {
         log.warning("unSubscribe(" + subscriptionId + ") failed: XmlBlasterException: " + e.getMessage());
      }
   }


   public String update(String loginName, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      System.out.println("");
      System.out.println("============= START " + updateKey.getOid() + " =======================");
      log.info("Receiving update of a message ...");
      System.out.println("<xmlBlaster>");
      System.out.println(updateKey.toXml());
      System.out.println("");
      System.out.println("<content>");
      System.out.println(new String(content));
      System.out.println("</content>");
      System.out.println(updateQos.toXml());
      System.out.println("</xmlBlaster>");
      System.out.println("============= END " + updateKey.getOid() + " =========================");
      System.out.println("");
      return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
   }


   /**
    * Command line usage.
    */
   private static void usage()
   {
      System.out.println(Global.instance().usage());
      System.out.println("----------------------------------------------------------");
      System.out.println("java org.xmlBlaster.client.reader.SubscribeMessage <options>");
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
      System.out.println("java org.xmlBlaster.client.reader.SubscribeMessage -oid mySpecialMessage");
      System.out.println("");
      System.out.println("java org.xmlBlaster.client.reader.SubscribeMessage -xpath //key/CAR");
      System.out.println("----------------------------------------------------------");
      System.out.println("");
   }


   /**
    * Invoke:  java org.xmlBlaster.client.reader.SubscribeMessage  -loginName Tim  -passwd secret  -oid __cmd:?totalMem
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         usage();
         System.exit(1);
      }
      try {
         SubscribeMessage publishFile = new SubscribeMessage(glob);
      } catch (Throwable e) {
         e.printStackTrace();
         System.err.println(SubscribeMessage.ME + ": " + e.toString());
      }
   }
}

