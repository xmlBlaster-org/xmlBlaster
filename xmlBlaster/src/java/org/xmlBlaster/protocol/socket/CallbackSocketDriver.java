/*------------------------------------------------------------------------------
Name:      CallbackSocketDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.qos.address.CallbackAddress;


/**
 * One instance of this for each client to send him callback. 
 * <p />
 * This is sort of a dummy needed by the plugin framework which
 * assumed for CORBA/RMI/XMLRPC a separate callback connection
 * @author xmlBlaster@marcelruff.info
 */
public class CallbackSocketDriver implements I_CallbackDriver
{
   private String ME = "CallbackSocketDriver";
   private Global glob = null;
   private LogChannel log;
   private String loginName;
   private HandleClient handler;
   private CallbackAddress callbackAddress;
   private boolean isFirstPing_hack = true;

   /**
    * Should not be instantiated by plugin loader.
    */
   public CallbackSocketDriver() {
      System.err.println(ME + ": Empty Constructor!");
      (new Exception("")).printStackTrace();
   }

   /**
    * This constructor is called when the callback shall be tunneled through by
    * the SAME SOCKET connection which the client already has established. 
    */
   public CallbackSocketDriver(String loginName, HandleClient handler) {
      this.loginName = loginName;
      this.ME += "-" + this.loginName;
      this.handler = handler;
   }

   /**
    * This constructor is called when the callback shall be delivered by
    * a separate SOCKET connection which we open here (in initialize())
    */
   public CallbackSocketDriver(String loginName /*, CallbackAddress callbackAddress*/) {
      this.loginName = loginName;
      this.ME += "-" + this.loginName;
      //this.callbackAddress = callbackAddress;
   }

   public String getName() {
      return this.loginName;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "SOCKET"
    */
   public String getProtocolId() {
      return "SOCKET";
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
    * @return "server.mars.univers:6701"
    */
   public String getRawAddress() {
      return this.callbackAddress.getRawAddress();
   }

   public void init(Global glob, CallbackAddress callbackAddress) {
      this.glob = glob;
      this.log = glob.getLog("socket");
      this.ME = "CallbackSocketDriver" + this.glob.getLogPrefixDashed();
      if (log.CALL) log.call(ME, "init()");
      this.callbackAddress = callbackAddress;
   }

   /**
    * This sends the update to the client.
    * @exception e.id="CallbackFailed", should be caught and handled appropriate
    */
   public final String[] sendUpdate(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      return this.handler.sendUpdate(callbackAddress.getSecretSessionId(), msgArr, ExecutorBase.WAIT_ON_RESPONSE);
   }

   /**
    * The oneway variant, without return value. 
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      this.handler.sendUpdate(callbackAddress.getSecretSessionId(), msgArr, ExecutorBase.ONEWAY);
   }

   /**
    * Ping to check if callback server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    * @exception XmlBlasterException If client not reachable
    */
   public final String ping(String qos) throws XmlBlasterException {
      if (this.isFirstPing_hack) {
         // Ingore first ping (which is triggered by dispatch framework after plugin creation
         // It leads to a deadlock since we are working on a connec() and should first return the ConnectReturnQos
         // See CbDispatchConnection.java this.cbDriver.ping("");
         this.isFirstPing_hack = false;
         return "";
      }
      try {
         return this.handler.ping(qos);
      } catch (Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                     "SOCKET callback ping failed", e);
      }
   }

   public void shutdown() {
      if (log.CALL) log.call(ME, "shutdown()");
      if (this.handler != null) {
         this.handler.shutdown();
      }
   }
}

