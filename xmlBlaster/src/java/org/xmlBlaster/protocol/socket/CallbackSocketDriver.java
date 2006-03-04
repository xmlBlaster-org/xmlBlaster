/*------------------------------------------------------------------------------
Name:      CallbackSocketDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.protocol.socket.SocketExecutor;


/**
 * One instance of this for each client to send him callback. 
 * <p />
 * This is sort of a dummy needed by the plugin framework which
 * assumed for CORBA/RMI/XMLRPC a separate callback connection
 * @author xmlBlaster@marcelruff.info
 */
public class CallbackSocketDriver implements I_CallbackDriver /* which extends I_Plugin */
{
   private String ME = "CallbackSocketDriver";
   private Global glob = null;
   private static Logger log = Logger.getLogger(CallbackSocketDriver.class.getName());
   private String loginName;
   private HandleClient handler;
   private CallbackAddress callbackAddress;
   //private boolean isFirstPing_hack = true;
   private PluginInfo pluginInfo;

   /**
    * Should not be instantiated by plugin loader.
    */
   public CallbackSocketDriver() {
      // System.err.println(ME + ": Empty Constructor!");
      // (new Exception("")).printStackTrace();
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
      return (this.pluginInfo == null) ? "SOCKET" : this.pluginInfo.getType();
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getProtocolId();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return (this.pluginInfo == null) ? "1.0" : this.pluginInfo.getVersion();
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) {
      if (log == null) {
         this.glob = glob;

      }
      // This can happen when on xmlBlaster restart a session is recovered from persistency
      // and the client has not yet connected (so we can't reuse its socket for callbacks).
      // In such a case this callback driver is loaded as a dummy.
      log.fine("init(PluginInfo) call not is expected, we are loaded dynamically if configured by ConnectQos");
      this.pluginInfo = pluginInfo;
   }

   /**
    * Get the address how to access this driver. 
    * @return "server.mars.univers:6701"
    */
   public String getRawAddress() {
      return this.callbackAddress.getRawAddress();
   }

   public void init(Global glob, CallbackAddress callbackAddress) 
      throws XmlBlasterException {
      this.glob = glob;

      this.ME = "CallbackSocketDriver" + this.glob.getLogPrefixDashed();
      if (log.isLoggable(Level.FINER)) log.finer("init()");
      this.callbackAddress = callbackAddress;
   }

   /**
    * This sends the update to the client.
    * @exception e.id="CallbackFailed", should be caught and handled appropriate
    */
   public final String[] sendUpdate(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      if (this.handler == null)
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                  "SOCKET sendUpdate failed");
      return this.handler.sendUpdate(callbackAddress.getSecretSessionId(), msgArr, SocketExecutor.WAIT_ON_RESPONSE);
   }

   /**
    * The oneway variant, without return value. 
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      if (this.handler == null)
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                  "SOCKET sendUpdateOneway failed");
      this.handler.sendUpdate(callbackAddress.getSecretSessionId(), msgArr, SocketExecutor.ONEWAY);
   }

   /**
    * Ping to check if callback server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    * @exception XmlBlasterException If client not reachable
    */
   public final String ping(String qos) throws XmlBlasterException {
      // "<qos><state info='INITIAL'/></qos>"
      // Send from CbDispatchConnection.java on connect 
      if (qos != null && qos.indexOf(Constants.INFO_INITIAL) != -1) {
         if (log.isLoggable(Level.FINE)) log.fine("Socket callback ping is suppressed as doing it before connect() may" +
         " block the clients connect() if the callback is not functional");
         return Constants.RET_OK;
      }
      /*
      if (this.isFirstPing_hack) {
         // Ingore first ping (which is triggered by dispatch framework after plugin creation
         // It leads to a deadlock since we are working on a connec() and should first return the ConnectReturnQos
         // See CbDispatchConnection.java this.cbDriver.ping("");
         this.isFirstPing_hack = false;
         return "";
      }
      */
      
      if (this.handler == null)
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                  "SOCKET callback ping failed");
      try {
         return this.handler.ping(qos);
      } catch (Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                     "SOCKET callback ping failed", e);
      }
   }

   public I_ProgressListener registerProgressListener(I_ProgressListener listener) {
      if (this.handler == null) return null;
      return this.handler.registerProgressListener(listener);
   }

   final I_ProgressListener getProgressListener() {
      if (this.handler == null) return null;
      return this.handler.getProgressListener();
   }

   public void shutdown() {
      if (this.log != null) {
         if (log.isLoggable(Level.FINER)) log.finer("shutdown()");
      } 
      if (this.handler != null) {
         this.handler.shutdown();
      }
   }
   
   /**
    * @return true if the plugin is still alive, false otherwise
    */
   public boolean isAlive() {
      return this.handler != null && !this.handler.isShutdown();
   }


}

