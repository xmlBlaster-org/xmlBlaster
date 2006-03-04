/*------------------------------------------------------------------------------
 Name:      CallbackEmailDriver.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.email;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.protocol.email.EmailExecutor;
import org.xmlBlaster.util.protocol.email.Pop3Driver;
import org.xmlBlaster.util.protocol.socket.SocketExecutor;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.MsgUnitRaw;

/**
 * Sends a MsgUnitRaw back to a client using Email.
 * <p>
 * Activate the email callback driver in xmlBlaster.properies first, for
 * example:
 * 
 * <pre>
CbProtocolPlugin[email][1.0]=\
   org.xmlBlaster.protocol.email.CallbackEmailDriver,\
   mail.smtp.bcc=sniffer@localhost,\
   inlineExtension=*,\
   compress/minSize=200,\
   compress/type=zlib,\
   mail.subject=XmlBlaster generated mail,\
   parserClass=org.xmlBlaster.util.xbformat.XmlScriptParser
 * </pre>
 * The parserClass is optional, the default message serialization
 * is XbfParser.
 * 
 * @author xmlBlaster@marcelruff.info
 */
public class CallbackEmailDriver extends EmailExecutor implements
      I_CallbackDriver {
   private String ME = "CallbackEmailDriver";

   private static Logger log = Logger.getLogger(CallbackEmailDriver.class.getName());

   private CallbackAddress callbackAddress;

   private PluginInfo pluginInfo;

   /** Get a human readable name of this driver */
   public String getName() {
      return ME;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver.
    * 
    * @return "email"
    */
   public String getProtocolId() {
      return (this.pluginInfo == null) ? "email" : this.pluginInfo.getType();
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
    * The command line key prefix
    * 
    * @return The configured type in xmlBlasterPlugins.xml, defaults to
    *         "plugin/email"
    */
   public String getEnvPrefix() {
      return (this.callbackAddress != null) ? this.callbackAddress
            .getEnvPrefix() : "plugin/" + getType().toLowerCase();
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin).
    * 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo)
         throws XmlBlasterException {
      this.pluginInfo = pluginInfo;
      this.glob = glob;
   }

   /**
    * Get the address how to access this driver.
    * 
    * @return null
    */
   public String getRawAddress() {
      return this.callbackAddress.getRawAddress();
   }

   /**
    * This init() is called after the init(Global, PluginInfo)
    * 
    * @param callbackAddress
    *           Contains the email TO: address
    */
   public void init(Global glob, CallbackAddress callbackAddress)
         throws XmlBlasterException {
      super.init(glob, callbackAddress, this.pluginInfo);

      this.callbackAddress = callbackAddress;
      super.setSecretSessionId(callbackAddress.getSecretSessionId());
      // This is a contract with client side EmailCallbackImpl.java:
      super.setEmailSessionId(callbackAddress.getSecretSessionId());
      
      if (super.mbeanHandle == null) {
         String tmp = callbackAddress.getEnv("__ContextNode", (String)null).getValueString();
         if (tmp != null) {
            ContextNode parent = ContextNode.valueOf(tmp);
            // For JMX instanceName may not contain ","
            super.contextNode = new ContextNode(ContextNode.PROTOCOL_MARKER_TAG,
                  "CallbackEmailDriver", parent);
            super.mbeanHandle = this.glob.registerMBean(super.contextNode, this);
         }
      }
   }

   /**
    * This sends the update to the client.
    */
   public String[] sendUpdate(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      try {
         return (String[]) super.sendEmail(msgArr, MethodName.UPDATE,
            SocketExecutor.WAIT_ON_RESPONSE);
      }
      catch (XmlBlasterException e) {
         throw XmlBlasterException.tranformCallbackException(e);
      }
   }

   /**
    * The oneway variant, without return value.
    * 
    * @exception XmlBlasterException
    *               Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      try {
         super.sendEmail(msgArr, MethodName.UPDATE_ONEWAY, SocketExecutor.ONEWAY);
      }
      catch (XmlBlasterException e) {
         throw XmlBlasterException.tranformCallbackException(e);
      }
      catch (Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
               "Email oneway callback of message to client [" + callbackAddress.getSecretSessionId() + "] failed", e);
      }
   }

   /**
    * Ping to check if callback server is alive. This ping checks the
    * availability on the application level.
    * 
    * @param qos
    * @return Constants.RET_OK or the remote ping return
    * @exception XmlBlasterException
    *               If client not reachable
    */
   public String ping(String qos) throws XmlBlasterException {
      
      if (Thread.currentThread().getName().equals(
            Pop3Driver.threadName)) {
         if (log.isLoggable(Level.FINE)) log.fine("Email ping is suppressed as doing this from thread '"
         + Pop3Driver.threadName
         + "' would deadlock");
         return Constants.RET_OK;
      }
      
      // "<qos><state info='INITIAL'/></qos>"
      // Send from CbDispatchConnection.java on connect 
      if (qos != null && qos.indexOf(Constants.INFO_INITIAL) != -1) {
         if (log.isLoggable(Level.FINE)) log.fine("Email callback ping is suppressed as doing it before connect() may" +
         " block the clients connect() if the callback is not functional");
         return Constants.RET_OK;
      }
      
      return (String) super.sendEmail(qos, MethodName.PING,
            SocketExecutor.WAIT_ON_RESPONSE);
   }

   /**
    * This method shuts down the driver.
    * <p />
    */
   public void shutdown() {
      super.shutdown();
      this.glob.unregisterMBean(this.mbeanHandle);
      this.mbeanHandle = null; 
      if (log != null) log.fine("shutdown() does currently nothing");
   }

   /**
    * @return true if the plugin is still alive, false otherwise
    */
   public boolean isAlive() {
      return true;
   }
}
