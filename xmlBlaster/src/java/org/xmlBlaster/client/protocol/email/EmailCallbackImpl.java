/*------------------------------------------------------------------------------
Name:      EmailCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.email;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.protocol.email.EmailExecutor;
import org.xmlBlaster.util.protocol.email.Pop3Driver;

/**
 * Used for client to receive xmlBlaster callbacks over emails.  
 * <p />
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see org.xmlBlaster.util.xbformat.Parser
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">The protocol.socket requirement</a>
 */
public class EmailCallbackImpl extends EmailExecutor implements I_CallbackServer
{
   private String ME = "EmailCallbackImpl";
   private Global glob;
   private LogChannel log;
   private CallbackAddress callbackAddress;
   private PluginInfo pluginInfo;
   private Pop3Driver pop3Driver;

   /** Stop the thread */
   boolean running = false;

   /**
    * Called by plugin loader which calls init(Global, PluginInfo) thereafter. 
    * A thread receiving all messages from xmlBlaster, and delivering them back to the client code.
    */
   public EmailCallbackImpl() {
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getCbProtocol();
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
      this.pluginInfo = pluginInfo;
   }

   /**
    * Initialize and start the callback server
    * A thread receiving all messages from xmlBlaster, and delivering them back to the client code.
    */
   public synchronized final void initialize(Global glob, String loginName,
                            CallbackAddress callbackAddress, I_CallbackExtended cbClient) throws XmlBlasterException {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.log = this.glob.getLog("email");
      this.ME = "EmailCallbackImpl-" + loginName;
      this.callbackAddress = callbackAddress;
      if (this.pluginInfo != null)
         this.callbackAddress.setPluginInfoParameters(this.pluginInfo.getParameters());
      setLoginName(loginName);
      setCbClient(cbClient); // access callback client in super class Executor:callback
      
      this.pop3Driver = (Pop3Driver)glob.getObjectEntry(Pop3Driver.class.getName());
      if (this.pop3Driver == null) {
         this.pop3Driver = new Pop3Driver();
         this.pop3Driver.init(glob, this.pluginInfo);
      }
      
      // Now we can do super.init() as it needs the pop3Driver in global
      super.init(glob, callbackAddress);
      
      // Who are we?
      // We need to correct the mail addresses from EmailExecutor
      // as it assumes server side operation
      try {
         //super.fromAddress = new InternetAddress(from);
         super.fromAddress = new InternetAddress(this.callbackAddress.getRawAddress());
      } catch (AddressException e) {
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT,
               ME, "Illegal 'from' address '" + this.callbackAddress.getRawAddress() + "'");
      }
      
      // Guess the email address to reach the xmlBlaster server
      // TODO: Extract the address dynamically from the received UPDATE message 
      String to = this.glob.get("mail.smtp.to", "xmlBlaster@localhost", null,
            this.pluginInfo);
      try {
         super.toAddress = new InternetAddress(to);
      } catch (AddressException e) {
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT,
               ME, "Illegal 'from' address '" + to + "'");
      }
      
      this.pop3Driver.registerForEmail(callbackAddress.getSecretSessionId(), null, this);
      
      log.info(ME, "Initialized email callback with from '" + super.fromAddress.toString() + "'");
   }

   /**
    * Returns the protocol type. 
    * @return The configured [type] in xmlBlaster.properties, defaults to "EMAIL"
    */
   public final String getCbProtocol()
   {
      return (this.pluginInfo == null) ? "EMAIL" : this.pluginInfo.getType();
   }

   /**
    * @return e.g. "email:et@mars.universe"
    */
   public String getCbAddress() throws XmlBlasterException {
      return this.callbackAddress.getRawAddress();
   }

   /**
    * Shutdown callback only.
    */
   public synchronized void shutdown() {
      setCbClient(null); // reset callback client in super class Executor:callback
   }

} // class EmailCallbackImpl

