/*------------------------------------------------------------------------------
Name:      SubscribeMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code to subscribe from command line for a message
Version:   $Id: SubscribeMessage.java,v 1.18 2002/09/13 23:17:58 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.reader;

import org.jutils.log.LogChannel;
import org.jutils.init.Args;
import org.jutils.JUtilsException;

import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.SubscribeKeyWrapper;
import org.xmlBlaster.client.SubscribeQosWrapper;
import org.xmlBlaster.client.SubscribeRetQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Constants;


/**
 * Subscribe from command line for a message.
 * <br />
 * Use this as a command line tool to subscribe for messages from xmlBlaster,
 * for example for debugging reasons.
 * Invoke examples:<br />
 * <pre>
 *    java org.xmlBlaster.client.reader.SubscribeMessage  -loginName  Tim  -passwd  secret  -oid  __cmd:?totalMem
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
   private final LogChannel log;
   private XmlBlasterConnection xmlBlasterConnection;
   private String loginName;
   private String passwd;
   private String subscriptionHandle;

   /**
    * Constructs the SubscribeMessage object.
    * <p />
    * Start with parameter -? to get a usage description.<br />
    * These command line parameters are not merged with xmlBlaster.property properties.
    * @param args      Command line arguments
    */
   public SubscribeMessage(Global glob) throws JUtilsException
   {
      this.glob = glob;
      this.log = glob.getLog("client");
      loginName = glob.getProperty().get("loginName", ME);
      passwd = glob.getProperty().get("passwd", "secret");

      String oidString = glob.getProperty().get("oid", (String)null);
      String xpathString = glob.getProperty().get("xpath", (String)null);

      if (oidString == null && xpathString == null) {
         usage();
         log.error(ME, "Specify the message oid or a xpath query");
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

      try { Thread.currentThread().sleep(10000000L); } catch (Exception e) { }
      log.warn(ME, "Bye, time is over.");
   }


   /**
    * Open the connection, and subscribe to the message
    */
   public SubscribeMessage(Global glob, String loginName, String passwd, String xmlKey, String queryType)
   {
      this.glob = glob;
      this.log = glob.getLog("client");
      this.loginName = loginName;
      this.passwd = passwd;
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
         xmlBlasterConnection = new XmlBlasterConnection(glob);
         ConnectQos qos = new ConnectQos(glob, loginName, passwd);
         xmlBlasterConnection.connect(qos, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          log.error(ME, e.toString());
          e.printStackTrace();
      }
   }


   /**
    * Logout from xmlBlaster
    */
   private void tearDown()
   {
      unSubscribe(subscriptionHandle);
      xmlBlasterConnection.logout();
   }


   private String subscribe(String xmlKey, String queryType)
   {
      try {
         SubscribeKeyWrapper xmlKeyWr = new SubscribeKeyWrapper(xmlKey, queryType);
         SubscribeQosWrapper xmlQos = new SubscribeQosWrapper();
         SubscribeRetQos ret = xmlBlasterConnection.subscribe(xmlKeyWr.toXml(), xmlQos.toXml());
         String subscriptionId = ret.getSubscriptionId();
         log.info(ME, "Subscribed to [" + xmlKey + "], subscriptionId=" + subscriptionId);
         return subscriptionId;
      } catch(XmlBlasterException e) {
         log.error(ME, "XmlBlasterException:\n" + e.reason);
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
         SubscribeKeyWrapper xmlKey = new SubscribeKeyWrapper(subscriptionId);
         SubscribeQosWrapper xmlQos = new SubscribeQosWrapper();
         xmlBlasterConnection.unSubscribe(xmlKey.toXml(), xmlQos.toXml());
         if (log.TRACE) log.trace(ME, "Unsubscribed from " + subscriptionId + " (GML and XML Packages)");
      } catch(XmlBlasterException e) {
         log.warn(ME, "unSubscribe(" + subscriptionId + ") failed: XmlBlasterException: " + e.reason);
      }
   }


   public String update(String loginName, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      System.out.println("");
      System.out.println("============= START " + updateKey.getUniqueKey() + " =======================");
      log.info(ME, "Receiving update of a message ...");
      System.out.println("<xmlBlaster>");
      System.out.println(updateKey.toString());
      System.out.println("");
      System.out.println("<content>");
      System.out.println(new String(content));
      System.out.println("</content>");
      System.out.println(updateQos.toString());
      System.out.println("</xmlBlaster>");
      System.out.println("============= END " + updateKey.getUniqueKey() + " =========================");
      System.out.println("");
      return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
   }


   /**
    * Command line usage.
    */
   private static void usage()
   {
      System.out.println("----------------------------------------------------------");
      System.out.println("java org.xmlBlaster.client.reader.SubscribeMessage <options>");
      System.out.println("----------------------------------------------------------");
      System.out.println("Options:");
      System.out.println("   -?                  Print this message.");
      System.out.println("");
      System.out.println("   -oid <XmlKeyOid>    The unique oid of the message");
      System.out.println("   -xpath <XPATH>      The XPATH query");
      //XmlBlasterConnection.usage();
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

