/*------------------------------------------------------------------------------
Name:      CallbackSocketDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.protocol.socket.SocketExecutor;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.util.xbformat.XbfParser;


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
   private SocketExecutor handler;
   private CallbackAddress callbackAddress;
   //private boolean isFirstPing_hack = true;
   private PluginInfo pluginInfo; // remains null as we are loaded dynamically
   private String msgInfoParserClassName;
   private boolean useUdpForOneway;

   /**
    * Should not be instantiated by plugin loader.
    */
   public CallbackSocketDriver() {
      //log.severe("Empty Ctor not expected");
      //(new Exception("")).printStackTrace();
   }

   /**
    * This constructor is called when the callback shall be tunneled through by
    * the SAME SOCKET connection which the client already has established. 
    */
   public CallbackSocketDriver(String loginName, SocketExecutor handler) {
      this.loginName = loginName;
      this.ME += "-" + this.loginName;
      this.handler = handler;
      this.useUdpForOneway = handler.useUdpForOneway();
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
      return Global.getStrippedString(this.ME);
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
      
      //if (this.pluginInfo == null) {
         // Use configuration parameters from xmlBlaster.properties (to be able to change parserClass)
         // even that we are instantiated directly (socket callback tunnel) and not by plugin manager
         //CbProtocolPlugin[socket_script][1.0]=org.xmlBlaster.protocol.socket.CallbackSocketDriver,\
         //compress/type=,\
         //isNullTerminated=true,\
         //parserClass=org.xmlBlaster.util.xbformat.XmlScriptParser
         try {
            this.msgInfoParserClassName = null; // reset
            this.pluginInfo = ((org.xmlBlaster.engine.ServerScope)glob).getCbProtocolManager().getPluginInfo(this.callbackAddress.getType(), this.callbackAddress.getVersion());
            if (this.pluginInfo != null) {
               this.callbackAddress.setPluginInfoParameters(this.pluginInfo.getParameters());
            }
         }
         catch (XmlBlasterException e) {
            // Don't log if no configuration is found
            if (e.isErrorCode(ErrorCode.RESOURCE_CONFIGURATION))
               log.fine("No socket protocol type '" + this.callbackAddress.getType() + "' configuration loaded: " + e.toString());
            else
               log.warning("No socket protocol type '" + this.callbackAddress.getType() + "' configuration loaded: " + e.toString());
         }
         catch (Throwable e) {
            e.printStackTrace();
            log.warning("No socket protocol type '" + this.callbackAddress.getType() + "' configuration loaded: " + e.toString());
         }
      //}
         
      Object obj = this.callbackAddress.getCallbackDriver();
      if (obj != null && obj instanceof SocketExecutor) { //SocketCallbackImpl
         this.handler = (SocketExecutor)obj;
      }
      //Object obj = glob.getObjectEntry(SocketExecutor.getGlobalKey(this.callbackAddress.getSessionName()));
      //if (obj != null) {
      //   if (obj instanceof org.xmlBlaster.util.protocol.socket.SocketExecutor) {
      //      this.handler = (org.xmlBlaster.util.protocol.socket.SocketExecutor)obj;
      //   }
      //}
   }

   /**
    * This sends the update to the client.
    * @exception e.id="CallbackFailed", should be caught and handled appropriate
    */
   public final String[] sendUpdate(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      if (this.handler == null)
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                  "SOCKET sendUpdate failed, the handle is null");
      return this.handler.sendUpdate(callbackAddress.getSecretSessionId(), msgArr,
             SocketExecutor.WAIT_ON_RESPONSE, this.useUdpForOneway, getPluginInfo());
   }

   /**
    * The oneway variant, without return value. 
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      if (this.handler == null)
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                  "SOCKET sendUpdateOneway failed");
      this.handler.sendUpdate(callbackAddress.getSecretSessionId(), msgArr,
            SocketExecutor.ONEWAY, this.useUdpForOneway, getPluginInfo());
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
      
      SocketExecutor se = this.handler;
      if (se == null)
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                  "SOCKET callback ping failed, handler is null");
      try {
         return se.ping(qos);
      } catch (Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                     "CallbackSocketDriver " + getType() + " callback ping failed", e);
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
      if (log != null) {
         if (log.isLoggable(Level.FINER)) log.finer("shutdown()");
      } 
      final SocketExecutor se = this.handler;
      if (se != null) {
         // The core can not do it, it does not know the HandleClient instance
         // it would be possible to pass the HandleClient with AddressServer to
         // the core but this needs to be discussed
         //this.handler.shutdown();
         
         // Give a Authenticate.connect exception to be delivered to the client
         // or the client some chance to close the socket itself after disconnect
         long delay = 5000; // 5 sec
         new Timeout("CallbackSocketShutdownTimer", true).addTimeoutListener(new I_Timeout() {
               public void timeout(Object userData) {
                  se.shutdown();
                  //handler = null;
               }
            }, delay, null);
      }
   }
   
   /**
    * @return true if the plugin is still alive, false otherwise
    */
   public boolean isAlive() {
      return this.handler != null && !this.handler.isShutdown();
   }

   /**
    * @return the pluginInfo: 
    * Always null as we are loaded dynamically!
    */
   public PluginInfo getPluginInfo() {
      return this.pluginInfo;
   }

   /**
    * @return the callbackAddress
    */
   public CallbackAddress getCallbackAddress() {
      return this.callbackAddress;
   }
   
   /**
    * Which parser to use.
    * The SOCKET protocol uses as a default setting the XbfParser
    * @return The class name of the parser, "org.xmlBlaster.util.xbformat.XbfParser"
    */
   public String getMsgInfoParserClassName() {
      if (this.msgInfoParserClassName == null) {
         synchronized (this) {
            if (this.msgInfoParserClassName == null && this.callbackAddress != null) {
               this.msgInfoParserClassName = this.callbackAddress.getEnv("parserClass", XbfParser.class.getName()).getValue();
            }
         }
      }
      return this.msgInfoParserClassName; // "org.xmlBlaster.util.xbformat.XbfParser"
   }
   
   public SocketExecutor getHandler() {
      return handler;
   }
}

