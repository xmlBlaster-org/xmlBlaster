/*------------------------------------------------------------------------------
Name:      SubscribeMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code to subscribe from command line for a message
Version:   $Id: SubscribeMessage.java,v 1.12 2002/03/18 00:29:30 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.reader;

import org.xmlBlaster.util.Log;
import org.jutils.init.Args;
import org.jutils.JUtilsException;

import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.*;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.protocol.corba.clientIdl.*;


/**
 * Subscribe from command line for a message.
 * <br />
 * Use this as a command line tool to subscribe for messages from xmlBlaster,
 * for example for debugging reasons.
 * Invoke examples:<br />
 * <pre>
 *    jaco org.xmlBlaster.client.reader.SubscribeMessage  -name  Tim  -passwd  secret  -oid  __sys__TotalMem
 * </pre>
 * For other supported options type
 * <pre>
 *    java org.xmlBlaster.client.reader.SubscribeMessage -?
 * </pre>
 */
public class SubscribeMessage implements I_Callback
{
   private static final String ME = "SubscribeMessage";
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
   public SubscribeMessage(String[] args) throws JUtilsException
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      if (Args.getArg(args, "-?") == true || Args.getArg(args, "-h") == true) {
         usage();
         return;
      }

      loginName = Args.getArg(args, "-name", ME);
      passwd = Args.getArg(args, "-passwd", "secret");

      String oidString = Args.getArg(args, "-oid", (String)null);
      String xpathString = Args.getArg(args, "-xpath", (String)null);

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
   public SubscribeMessage(String loginName, String passwd, String xmlKey, String queryType)
   {
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
         xmlBlasterConnection = new XmlBlasterConnection(); // Find orb
         xmlBlasterConnection.login(loginName, passwd, null, this); // Login to xmlBlaster
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
         String subscriptionId = xmlBlasterConnection.subscribe(xmlKeyWr.toXml(), xmlQos.toXml());
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


   public String update(String loginName, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS)
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
      Log.plain(ME, updateQoS.toString());
      Log.plain(ME, "</xmlBlaster>");
      Log.plain(ME, "============= END " + updateKey.getUniqueKey() + " =========================");
      Log.plain(ME, "");
      return "<qos><state>OK</state></qos>";
   }


   /**
    * Command line usage.
    */
   private void usage()
   {
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "jaco org.xmlBlaster.client.reader.SubscribeMessage <options>");
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "Options:");
      Log.plain(ME, "   -?                  Print this message.");
      Log.plain(ME, "");
      Log.plain(ME, "   -name <LoginName>   Your xmlBlaster login name.");
      Log.plain(ME, "   -passwd <Password>  Your xmlBlaster password.");
      Log.plain(ME, "");
      Log.plain(ME, "   -oid <XmlKeyOid>    The unique oid of the message");
      Log.plain(ME, "   -xpath <XPATH>      The XPATH query");
      //XmlBlasterConnection.usage();
      //Log.usage();
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "Example:");
      Log.plain(ME, "jaco org.xmlBlaster.client.reader.SubscribeMessage -oid mySpecialMessage");
      Log.plain(ME, "");
      Log.plain(ME, "jaco org.xmlBlaster.client.reader.SubscribeMessage -xpath //key/CAR");
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "");
   }


   /**
    * Invoke:  jaco org.xmlBlaster.client.reader.SubscribeMessage  -name Tim  -passwd secret  -oid __sys__TotalMem
    */
   public static void main(String args[])
   {
      try {
         SubscribeMessage publishFile = new SubscribeMessage(args);
      } catch (Throwable e) {
         e.printStackTrace();
         Log.panic(SubscribeMessage.ME, e.toString());
      }
      Log.exit(SubscribeMessage.ME, "Good bye");
   }
}

