/*------------------------------------------------------------------------------
Name:      NativeDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   NativeDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: NativeDriver.java,v 1.10 2002/04/08 17:09:27 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.nativ;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.util.DisconnectQos;



/**
 * Native driver class to invoke the xmlBlaster server in the same JVM (not remote).
 * <p />
 * This is a demo fragment only. You can take a copy and
 * code your own native access.
 * <p />
 * The native driver needs to be registered in xmlBlaster.properties
 * and will be started on xmlBlaster startup, for example:
 * <pre>
 *   Protocol.Drivers=IOR:org.xmlBlaster.protocol.corba.CorbaDriver,\
 *                    RMI:org.xmlBlaster.protocol.rmi.RmiDriver,\
 *                    NativeDemo:org.xmlBlaster.protocol.nativ.NativeDriver
 *
 *   Protocol.CallbackDrivers=IOR:org.xmlBlaster.protocol.corba.CallbackCorbaDriver,\
 *                            RMI:org.xmlBlaster.protocol.rmi.CallbackRmiDriver,\
 *                            NativeDemo:org.xmlBlaster.protocol.nativ.CallbackNativeDriver
 * </pre>
 * The interface I_Driver is needed by xmlBlaster to instantiate and shutdown
 * this driver implementation.
 * @author ruff@swand.lake.de
 */
public class NativeDriver implements I_Driver
{
   private static final String ME = "NativeDriver";
   /** The singleton handle for this xmlBlaster server */
   private I_Authenticate authenticate = null;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl = null;
   /** The authentication session identifier */
   private String sessionId = null;


   /** Get a human readable name of this driver.
    * <p />
    * Enforced by interface I_Driver.
    */
   public String getName()
   {
      return ME;
   }


   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "NativeDemo"
    */
   public String getProtocolId()
   {
      return "NativeDemo";
   }

   /**
    * Get the address how to access this driver. 
    * @return null
    */
   public String getRawAddress()
   {
      Log.error(ME+".getRawAddress()", "No external access address available");
      return null;
   }

   /**
    * Start xmlBlaster native access.
    * <p />
    * Enforced by interface I_Driver.
    * @param glob Global handle to access logging, property and commandline args
    * @param authenticate Handle to access authentication server
    * @param xmlBlasterImpl Handle to access xmlBlaster core
    */
   public void init(final Global glob, I_Authenticate authenticate, I_XmlBlaster xmlBlasterImpl) throws XmlBlasterException
   {
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;
      Log.info(ME, "Started successfully native driver.");

      // ------------------------------
      // login and get a session id ...
      String loginName = XmlBlasterProperty.get("NativeDemo.loginName", "NativeDemo");
      String passwd = XmlBlasterProperty.get("NativeDemo.password", "secret");
      // "NativeDemo" below is the 'callback protocol type', which results in instantiation of given the class:
      CallbackAddress callback = new CallbackAddress("NativeDemo", "org.xmlBlaster.protocol.nativ.CallbackNativeDriver");
      ConnectQos connectQos = new ConnectQos(null,null,loginName,passwd);
      connectQos.addCallbackAddress(callback);
      ConnectReturnQos returnQos = authenticate.connect(connectQos);
      sessionId = returnQos.getSessionId();

      // ----------------------------------------------------
      // Sending demo message to our CallbackNativeDriver ...
      String receiverName = loginName;
      String xmlKey = "<key oid='' contentMime='text/plain'>\n" +
                      "</key>";
      String qos = "<qos>" +
                   "   <destination queryType='EXACT'>" +
                           receiverName +
                   "   </destination>" +
                   "</qos>";
      String content = "Hi " + receiverName + "!";
      MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), qos);
      try {
         String publishOid = xmlBlasterImpl.publish(sessionId, msgUnit);
         Log.info(ME, "Sending done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         Log.error(ME, "publish() XmlBlasterException: " + e.reason);
      }
   }


   /**
    * Instructs native driver to shut down.
    * <p />
    * Enforced by interface I_Driver.
    */
   public void shutdown()
   {
      Log.info(ME, "Shutting down native driver ...");
      try { authenticate.disconnect(sessionId, (new DisconnectQos()).toXml()); } catch(XmlBlasterException e) { }
   }


   /**
    * Command line usage.
    * <p />
    * Enforced by interface I_Driver.
    */
   public String usage()
   {
      String text = "\n";
      text += "NativeDriver options:\n";
      //text += "   -native.name        Specify a logging name.\n";
      text += "\n";
      return text;
   }
}
