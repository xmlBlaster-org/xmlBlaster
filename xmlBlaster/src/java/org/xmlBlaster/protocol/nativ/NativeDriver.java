/*------------------------------------------------------------------------------
Name:      NativeDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   NativeDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: NativeDriver.java,v 1.22 2002/12/20 16:32:39 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.nativ;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.qos.address.CallbackAddress;
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
 *ProtocolPlugin[NativeDemo][NATIVE]=org.xmlBlaster.protocol.nativ.NativeDriver
 *
 *CbProtocolPlugin[NativeDemo][NATIVE]=org.xmlBlaster.protocol.nativ.CallbackNativeDriver
 * </pre>
 * The interface I_Driver is needed by xmlBlaster to instantiate and shutdown
 * this driver implementation.
 * @author xmlBlaster@marcelruff.info
 */
public class NativeDriver implements I_Driver
{
   private String ME = "NativeDriver";
   private Global glob = null;
   private LogChannel log = null;
   /** The singleton handle for this xmlBlaster server */
   private I_Authenticate authenticate = null;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl = null;
   /** The authentication session identifier */
   private String sessionId = null;

   private String loginName = null;
   private String passwd = null;

   /** Get a human readable name of this driver.
    * <p />
    * Enforced by interface I_Driver.
    */
   public String getName() {
      return ME;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "NativeDemo"
    */
   public String getProtocolId() {
      return "NativeDemo";
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getProtocolId();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return "1.0";
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
   }

   /**
    * Get the address how to access this driver. 
    * @return null
    */
   public String getRawAddress() {
      log.error(ME+".getRawAddress()", "No external access address available");
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
      this.glob = glob;
      this.ME = "NativeDriver" + this.glob.getLogPrefixDashed();
      this.log = glob.getLog("native");
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;
      log.info(ME, "Started successfully native driver.");

      // login and get a session id ...
      loginName = glob.getProperty().get("NativeDemo.loginName", "NativeDemo");
      passwd = glob.getProperty().get("NativeDemo.password", "secret");
      // "NativeDemo" below is the 'callback protocol type', which results in instantiation of given the class:
      CallbackAddress callback = new CallbackAddress(glob, "NativeDemo");
      callback.setAddress("org.xmlBlaster.protocol.nativ.CallbackNativeDriver");
      ConnectQos connectQos = new ConnectQos(glob, null,null,loginName,passwd);
      connectQos.addCallbackAddress(callback);
      connectQos.setSessionTimeout(0L);
      ConnectReturnQos returnQos = authenticate.connect(connectQos);
      sessionId = returnQos.getSessionId();
   }

   /**
    * Activate xmlBlaster access through this protocol.
    */
   public synchronized void activate() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering activate");

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
      MsgUnitRaw msgUnit = new MsgUnitRaw(xmlKey, content.getBytes(), qos);
      try {
         String publishOid = xmlBlasterImpl.publish(sessionId, msgUnit);
         log.info(ME, "Sending done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.error(ME, "publish() XmlBlasterException: " + e.getMessage());
      }
   }

   /**
    * Deactivate xmlBlaster access (standby), no clients can connect. 
    */
   public synchronized void deActivate() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering deActivate");
   }

   /**
    * Instructs native driver to shut down.
    * <p />
    * Enforced by interface I_Driver.
    */
   public void shutdown(boolean force)
   {
      log.info(ME, "Shutting down native driver ...");
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
