/*------------------------------------------------------------------------------
Name:      XmlRpcProxy.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code to subscribe from command line for a message
Version:   $Id: XmlRpcProxy.java,v 1.6 2000/06/26 15:22:50 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import org.jutils.log.Log;
import org.jutils.init.Args;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.client.*;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;

import java.io.IOException;
import helma.xmlrpc.*;

/**
 * Routes XML-RPC through to xmlBlaster.
 * <br />
 * Invoke examples:<br />
 * <pre>
 *    java -Dsax.driver=xp org.xmlBlaster.protocol.xmlrpc.XmlRpcProxy -xmlPort 8080
 * </pre>
 */
public class XmlRpcProxy implements I_Callback
{
   private static final String ME = "XmlRpcProxy";
   private CorbaConnection corbaConnection;
   private String loginName;
   private String passwd;
   private String subscriptionHandle;
   private WebServer webserver;
   private int xmlPort = 8080;

   /**
    * Constructs the XmlRpcProxy object.
    * <p />
    * Start with parameter -? to get a usage description.<br />
    * These command line parameters are not merged with xmlBlaster.property properties.
    * @param args      Command line arguments
    */
   public XmlRpcProxy(String[] args)
   {
      try {
         XmlBlasterProperty.init(args);
         if (Args.getArg(args, "-?") == true || Args.getArg(args, "-h") == true) {
            usage();
            return;
         }
         Log.setLogLevel(XmlBlasterProperty.getProperty()); // initialize log level

         loginName = Args.getArg(args, "-name", ME);
         passwd = Args.getArg(args, "-passwd", "secret");
         xmlPort = Args.getArg(args, "-xmlPort", 8080);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }

      setUp();  // login
      // String subscriptionHandle = subscribe(xmlKey, queryType);
      corbaConnection.getOrb().run();
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
         LoginQosWrapper loginQos = new LoginQosWrapper(); // "<qos></qos>";
         corbaConnection.login(loginName, passwd, loginQos, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          Log.error(ME, e.toString());
          e.printStackTrace();
      }

      try {
         webserver = new WebServer(xmlPort);
         webserver.addHandler ("math", Math.class);
         webserver.addHandler("proxy", corbaConnection);
      } catch (IOException e) {
          Log.error(ME, "Error creating webserver: " + e.toString());
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

/*
   public String subscribe(String xmlKey, String queryType)
   {
      Log.info(ME, "Entering subscribe to [" + xmlKey + "] [" + queryType + "]");
      try {
         SubscribeKeyWrapper xmlKeyWr = new SubscribeKeyWrapper(xmlKey);
         SubscribeQosWrapper xmlQos = new SubscribeQosWrapper();
         String subscriptionId = corbaConnection.subscribe(xmlKeyWr.toXml(), xmlQos.toXml());
         Log.info(ME, "Subscribed to [" + xmlKey + "], subscriptionId=" + subscriptionId);
         return subscriptionId;
      } catch(XmlBlasterException e) {
         Log.panic(ME, "XmlBlasterException:\n" + e.reason);
      }
      return null;
   }
*/

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
      Log.plain(ME, "jaco org.xmlBlaster.protocol.xmlrpc.XmlRpcProxy <options>");
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "Options:");
      Log.plain(ME, "   -?                  Print this message.");
      Log.plain(ME, "");
      Log.plain(ME, "   -name <LoginName>   Your xmlBlaster login name.");
      Log.plain(ME, "   -passwd <Password>  Your xmlBlaster password.");
      Log.plain(ME, "");
      Log.plain(ME, "   -xmlPort 8080       The port of the XML-RPC server");
      //CorbaConnection.usage();
      //Log.usage();
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "Example:");
      Log.plain(ME, "jaco org.xmlBlaster.protocol.xmlrpc.XmlRpcProxy -oid mySpecialMessage");
      Log.plain(ME, "");
      Log.plain(ME, "jaco org.xmlBlaster.protocol.xmlrpc.XmlRpcProxy -xpath //key/CAR");
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "");
   }


   /**
    * Invoke:  jaco org.xmlBlaster.protocol.xmlrpc.XmlRpcProxy -c <content-file> -k <key-file> -q <qos-file> -m <mime-type>
    */
   public static void main(String args[])
   {
      XmlRpcProxy publishFile = new XmlRpcProxy(args);
      Log.exit(XmlRpcProxy.ME, "Good bye");
   }
}

