/*------------------------------------------------------------------------------
Name:      LocalCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.local;


import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;

import java.io.IOException;


/**
 * Used for client to receive xmlBlaster callbacks over plain locals. 
 * <p />
 * One instance of this for each client, as a separate thread blocking
 * on the local input stream waiting for messages from xmlBlaster. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see org.xmlBlaster.protocol.local.Parser
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.local.html">The protocol.local requirement</a>
 */
public class LocalCallbackImpl implements I_CallbackServer
{
   private String ME = "LocalCallbackImpl";
   private Global glob;
   private LogChannel log;
   /** The connection manager 'singleton' */
   private LocalConnection localConnection;

   /** Stop the thread */
   boolean running = false;

   /**
    * Called by plugin loader which calls init(Global, PluginInfo) thereafter. 
    * A thread receiving all messages from xmlBlaster, and delivering them back to the client code.
    */
   public LocalCallbackImpl() {
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getCbProtocol();
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
    * Initialize and start the callback server
    * A thread receiving all messages from xmlBlaster, and delivering them back to the client code.
    */
   public synchronized final void initialize(Global glob, String loginName,
                            CallbackAddress callbackAddress, I_CallbackExtended cbClient) throws XmlBlasterException {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.log = this.glob.getLog("local");
      this.ME = "LocalCallbackImpl-" + loginName;

      if (this.running == false) {
         // Lookup LocalConnection instance in the NameService
         this.localConnection = (LocalConnection)glob.getObjectEntry("org.xmlBlaster.client.protocol.local.LocalConnection");

         if (this.localConnection == null) {
            // LocalConnection.java must be instantiated first and registered to reuse the local for callbacks
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME,
                  "Sorry, creation of LOCAL callback handler is not possible if client connection is not of type 'LOCAL'");
         }
         
         //this.localConnection.registerCb(this);

         this.running = true;
      }
   }

   /**
    * Returns the protocol type. 
    * @return "LOCAL"
    */
   public final String getCbProtocol()
   {
      return "LOCAL";
   }

   /**
    * Returns the callback address. 
    * <p />
    * This is no listen local, as we need no callback server.
    * It is just the client side local data of the established connection to xmlBlaster.
    * @return "local"
    */
   public String getCbAddress() throws XmlBlasterException {
      return "local";
   }
   
   final LocalConnection getLocalConnection() {
      return localConnection;
   }

   /**
    * Shutdown callback, called by LocalConnection on problems
    * @return true everything is OK, false if probably messages are lost on shutdown
    */
   public synchronized void shutdown() {
      this.running = false;
   }
} // class LocalCallbackImpl

