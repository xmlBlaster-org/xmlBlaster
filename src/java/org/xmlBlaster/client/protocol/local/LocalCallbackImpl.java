/*------------------------------------------------------------------------------
Name:      LocalCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.local;


import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;


/**
 * A client callback driver for in jvm calls.
 * the start/invocation sequence for the local protocol is now decoupled. This means that the
 * client can come before the server and the server can come before the client (as would
 * normally be the case when recovering from persistence).
 * This means that even when choosing the local protocol there could be the situation where
 * the client is not (yet/anymore) available, particularly on run level changes.
 *  
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>.
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @author <a href="mailto:pra@tim.se">Peter Antman</a>.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.local.html">The protocol.local requirement</a>
 */
public class LocalCallbackImpl implements I_CallbackServer
{
   private String ME = "LocalCallbackImpl";
   private Global glob;
   /** The access callback */
   private I_CallbackExtended cbClient;
   /** The id (raw address of this object */
   private String callbackId;
   /** The serverside engine global, used as a naming store */
   private org.xmlBlaster.engine.ServerScope engineGlob;

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
    * This method is not used, since callbacks plugins are actually factories.
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
   }

   /**
    * Initialize the callback server.
    * <p>The server will register itself in the serverside engine global as
    its raw Address. Any serverside callback driver must use the address to
    * look the object up and invoke it directly.</p>
    * <p>The given global must contain the serverside org.xmlBlaster.engine.Global in its ObjectEntry "ServerNodeScope"</p>
    */
   public synchronized final void initialize(Global glob, String loginName,
                            CallbackAddress callbackAddress, I_CallbackExtended cbClient) throws XmlBlasterException {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.ME = "LocalCallbackImpl-" + loginName;

      this.callbackId = "LOCAL:"+this.hashCode();
      
      // Set this object an the engine.Global so that the server cb handler
      // can find it.
      engineGlob = (org.xmlBlaster.engine.ServerScope)glob.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope);
      if (engineGlob == null)
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "could not retreive the ServerNodeScope. Am I really on the server side ?");
      
      // Ad the driver to the "naming" store.
      if (cbClient != null) {
         this.cbClient = cbClient;
         engineGlob.addObjectEntry(this.callbackId, this.cbClient);
      }
   }

   /**
    * Returns the protocol type. 
    * @return "LOCAL"
    */
   public final String getCbProtocol() {
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
      return callbackId;
   }

   /**
    * Shutdown callback, called by LocalConnection on problems
    * @return true everything is OK, false if probably messages are lost on shutdown
    */
   public synchronized void shutdown() {
      engineGlob.removeObjectEntry(this.callbackId);
   }
   
} // class LocalCallbackImpl

