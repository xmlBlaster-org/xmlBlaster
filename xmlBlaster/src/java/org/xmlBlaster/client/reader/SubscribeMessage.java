/*------------------------------------------------------------------------------
Name:      SubscribeMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code to subscribe from command line for a message
Version:   $Id: SubscribeMessage.java,v 1.17 2002/06/18 13:51:53 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.reader;

import org.xmlBlaster.util.Log;
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
      loginName = glob.getProperty().get("loginName", ME);
      passwd = glob.getProperty().get("passwd", "secret");

      String oidString = glob.getProperty().get("oid", (String)null);
      String xpathString = glob.getProperty().get("xpath", (String)null);

      if (oidString == null && xpathString == null) {
         usage();
         Log.panic(ME, "Specify the message oid or a xpath query");
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
      Log.exit(ME, "Bye, time is over.");
   }


   /**
    * Open the connection, and subscribe to the message
    */
   public SubscribeMessage(Global glob, String loginName, String passwd, String xmlKey, String queryType)
   {
      this.glob = glob;
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
          Log.error(ME, e.toString());
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
         Log.info(ME, "Subscribed to [" + xmlKey + "], subscriptionId=" + subscriptionId);
         return subscriptionId;
      } catch(XmlBlasterException e) {
         Log.panic(ME, "XmlBlasterException:\n" + e.reason);
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
         if (Log.TRACE) Log.trace(ME, "Unsubscribed from " + subscriptionId + " (GML and XML Packages)");
      } catch(XmlBlasterException e) {
         Log.warn(ME, "unSubscribe(" + subscriptionId + ") failed: XmlBlasterException: " + e.reason);
      }
   }


   public String update(String loginName, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      Log.plain(ME, "");
      Log.plain(ME, "============= START " + updateKey.getUniqueKey() + " =======================");
      Log.info(ME, "Receiving update of a message ...");
      Log.plain(ME, "<xmlBlaster>");
      Log.plain(ME, updateKey.toString());
      Log.plain(ME, "");
      Log.plain(ME, "<content>");
      Log.plain(ME, new String(content));
      Log.plain(ME, "</content>");
      Log.plain(ME, updateQos.toString());
      Log.plain(ME, "</xmlBlaster>");
      Log.plain(ME, "============= END " + updateKey.getUniqueKey() + " =========================");
      Log.plain(ME, "");
      return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
   }


   /**
    * Command line usage.
    */
   private static void usage()
   {
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "java org.xmlBlaster.client.reader.SubscribeMessage <options>");
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "Options:");
      Log.plain(ME, "   -?                  Print this message.");
      Log.plain(ME, "");
      Log.plain(ME, "   -oid <XmlKeyOid>    The unique oid of the message");
      Log.plain(ME, "   -xpath <XPATH>      The XPATH query");
      //XmlBlasterConnection.usage();
      //Log.usage();
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "Example:");
      Log.plain(ME, "java org.xmlBlaster.client.reader.SubscribeMessage -oid mySpecialMessage");
      Log.plain(ME, "");
      Log.plain(ME, "java org.xmlBlaster.client.reader.SubscribeMessage -xpath //key/CAR");
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "");
   }


   /**
    * Invoke:  java org.xmlBlaster.client.reader.SubscribeMessage  -loginName Tim  -passwd secret  -oid __cmd:?totalMem
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         usage();
         Log.exit("","Bye");
      }
      try {
         SubscribeMessage publishFile = new SubscribeMessage(glob);
      } catch (Throwable e) {
         e.printStackTrace();
         Log.panic(SubscribeMessage.ME, e.toString());
      }
      Log.exit(SubscribeMessage.ME, "Good bye");
   }
}

