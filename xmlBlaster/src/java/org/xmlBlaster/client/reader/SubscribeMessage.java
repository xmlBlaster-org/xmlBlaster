/*------------------------------------------------------------------------------
Name:      SubscribeMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code to subscribe from command line for a message
Version:   $Id: SubscribeMessage.java,v 1.5 2000/06/18 15:21:59 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.reader;

import org.jutils.log.Log;
import org.jutils.init.Args;
import org.jutils.JUtilsException;

import org.xmlBlaster.client.*;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.corba.clientIdl.*;


/**
 * Subscribe from command line for a message.
 * <br />
 * Use this as a command line tool to subscribe for messages from xmlBlaster,
 * for example for debugging reasons.
 * Invoke examples:<br />
 * <pre>
 *    jaco org.xmlBlaster.client.reader.SubscribeMessage -c &lt;content-file> -k &lt;key-file> -q &lt;qos-file> -m &lt;mime-type>
 * </pre>
 */
public class SubscribeMessage implements I_Callback
{
   private static final String ME = "SubscribeMessage";
   private CorbaConnection corbaConnection;
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
      if (Args.getArg(args, "-?") == true || Args.getArg(args, "-h") == true) {
         usage();
         return;
      }
      Log.setLogLevel(args); // initialize log level and xmlBlaster.property file

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
      corbaConnection.getOrb().run();
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
         corbaConnection = new CorbaConnection(); // Find orb
         corbaConnection.login(loginName, passwd, null, this); // Login to xmlBlaster
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
      corbaConnection.logout();
   }


   private String subscribe(String xmlKey, String queryType)
   {
      try {
         SubscribeKeyWrapper xmlKeyWr = new SubscribeKeyWrapper(xmlKey, queryType);
         SubscribeQosWrapper xmlQos = new SubscribeQosWrapper();
         String subscriptionId = corbaConnection.subscribe(xmlKeyWr.toXml(), xmlQos.toXml());
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
         corbaConnection.unSubscribe(xmlKey.toXml(), xmlQos.toXml());
         if (Log.TRACE) Log.trace(ME, "Unsubscribed from " + subscriptionId + " (GML and XML Packages)");
      } catch(XmlBlasterException e) {
         Log.warning(ME, "unSubscribe(" + subscriptionId + ") failed: XmlBlasterException: " + e.reason);
      }
   }


   public void update(String loginName, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS)
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
      //CorbaConnection.usage();
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
    * Invoke:  jaco org.xmlBlaster.client.reader.SubscribeMessage -c <content-file> -k <key-file> -q <qos-file> -m <mime-type>
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

