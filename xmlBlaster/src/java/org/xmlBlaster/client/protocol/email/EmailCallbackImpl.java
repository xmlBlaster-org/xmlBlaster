/*------------------------------------------------------------------------------
 Name:      EmailCallbackImpl.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.email;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.protocol.email.EmailExecutor;

/**
 * Used for client to receive xmlBlaster callbacks over emails.
 * <p />
 * 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see <a
 *      href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.email.html">The
 *      protocol.email requirement</a>
 */
public class EmailCallbackImpl extends EmailExecutor implements
      I_CallbackServer {
   private String ME = "EmailCallbackImpl";

   private Global glob;

   private LogChannel log;

   private CallbackAddress callbackAddress;

   private PluginInfo pluginInfo;

   /** Stop the thread */
   boolean running = false;

   /**
    * Called by plugin loader which calls init(Global, PluginInfo) thereafter. A
    * thread receiving all messages from xmlBlaster, and delivering them back to
    * the client code.
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
    * 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) {
      this.pluginInfo = pluginInfo;
   }

   /**
    * Initialize and start the callback server A thread receiving all messages
    * from xmlBlaster, and delivering them back to the client code.
    */
   public synchronized final void initialize(Global glob, String loginName,
         CallbackAddress callbackAddress, I_CallbackExtended cbClient)
         throws XmlBlasterException {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.log = this.glob.getLog("email");
      this.ME = "EmailCallbackImpl-" + loginName;
      this.callbackAddress = callbackAddress;
      setLoginName(loginName);
      setCbClient(cbClient); // access callback client in super class
                              // SocketExecutor:callback

      // Now we can do super.init() for smtpClient and pop3Driver setup
      super.init(glob, callbackAddress, this.pluginInfo);
      
      // This is a contract with server side CallbackEmailDriver.java:
      // as we use it to register at Pop3Poller to listen for incoming updates
      super.setEmailSessionId(callbackAddress.getSecretSessionId());
      
      // Who are we?
      // We need to correct the mail addresses from EmailExecutor
      // as it assumes server side operation
      if (super.fromAddress != null
            && this.callbackAddress.getRawAddress().length() == 0)
         this.callbackAddress.setRawAddress(super.fromAddress.toString());
      else
         super.setFrom(this.callbackAddress.getRawAddress());

      // Guess the email address to reach the xmlBlaster server
      // TODO: Extract the address dynamically from the received UPDATE message
      super.setTo(this.glob.get("mail.smtp.to", "xmlBlaster@localhost", null,
            this.pluginInfo));

      getPop3Driver().registerForEmail(super.getEmailSessionId(),
            "", this);

      log.info(ME, "Initialized email callback, from '"
            + super.fromAddress.toString() + "' to="
            + super.toAddress.toString() + "'");
   }

   /**
    * Returns the protocol type.
    * 
    * @return The configured [type] in xmlBlaster.properties, defaults to
    *         "email"
    */
   public final String getCbProtocol() {
      return (this.pluginInfo == null) ? "email" : this.pluginInfo.getType();
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
      if (super.pop3Driver != null && super.getEmailSessionId().length() > 0)
         super.pop3Driver.deregisterForEmail(super.getEmailSessionId(),"");
      setCbClient(null); // reset callback client in super class
                           // SocketExecutor:callback
   }

} // class EmailCallbackImpl

