/*------------------------------------------------------------------------------
Name:      LocalCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.local;


import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.client.I_CallbackRaw;
import org.xmlBlaster.protocol.local.I_LocalCallback;
import java.io.IOException;


/**
 * A client callback driver for in jvm calls.
 * <p>This does not work if not the serverside local driver is loaded to: {@link org.xmlBlaster.protocol.local.CallbackLocalDriver}</p>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @author <a href="mailto:pra@tim.se">Peter Antman</a>.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.local.html">The protocol.local requirement</a>
 */
public class LocalCallbackImpl implements I_CallbackServer, I_LocalCallback
{
   private String ME = "LocalCallbackImpl";
   private Global glob;
   private LogChannel log;
   /** The access callback */
   private I_CallbackExtended cbClient;
   /** The id (raw address of this object */
   private String callbackId;
   /** The serverside engine global, used as a naming store */
   private org.xmlBlaster.engine.Global engineGlob;

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
      this.log = this.glob.getLog("local");
      this.ME = "LocalCallbackImpl-" + loginName;
      this.callbackId = "LOCAL:"+this.hashCode();
      this.cbClient = cbClient;
      
      // Set this object an the engine.Global so that the server cb handler
      // can find it.
      engineGlob = (org.xmlBlaster.engine.Global)glob.getObjectEntry("ServerNodeScope");
      if (engineGlob == null)
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "could not retreive the ServerNodeScope. Am I really on the server side ?");
      
      // Ad the driver to the "naming" store.
      engineGlob.addObjectEntry(callbackId,this);

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
      return callbackId;
   }

   /**
    * Shutdown callback, called by LocalConnection on problems
    * @return true everything is OK, false if probably messages are lost on shutdown
    */
   public synchronized void shutdown() {
      engineGlob.removeObjectEntry(callbackId);
   }
   
   public String[] update(String cbSessionId, MsgUnitRaw[] msgUnitArr) throws XmlBlasterException
   {
      if (msgUnitArr == null) throw new XmlBlasterException(ME, "Received update of null message");
      if (log.CALL) log.call(ME, "Entering update(" + cbSessionId + ") of " + msgUnitArr.length + " messages");
      
      return cbClient.update(cbSessionId, msgUnitArr);
   }
   
   /**
    * The oneway variant for better performance. 
    */
   public void updateOneway(String cbSessionId, org.xmlBlaster.util.MsgUnitRaw[] msgUnitArr) throws XmlBlasterException
   {
      if (msgUnitArr == null) return;
      if (log.CALL) log.call(ME, "Entering updateOneway(" + cbSessionId + ") of " + msgUnitArr.length + " messages");
      try {
         cbClient.updateOneway(cbSessionId, msgUnitArr);
      }
      // FIXME retrow???
      catch (Throwable e) {
         log.error(ME, "Caught exception which can't be delivered to xmlBlaster because of 'oneway' mode: " + e.toString());
      }
   }
   
   /**
    * Ping to check if the xmlBlaster server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    */
   public String ping(String str)
   {
      return "";
   }
} // class LocalCallbackImpl

