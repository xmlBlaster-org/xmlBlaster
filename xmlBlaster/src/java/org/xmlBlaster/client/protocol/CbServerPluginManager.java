/*------------------------------------------------------------------------------
Name:      CbServerPluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.protocol.I_CallbackServer;

/**
 * CbServerPluginManager loads the callback protocol plugins like CORBA/RMI/XmlRpc on client side
 * to be able to receive callbacks from xmlBlaster. 
 * <p />
 * <pre>
 * A typical xmlBlaster.properties entry:
 *
 * ClientCbServerProtocolPlugin[IOR][1.0]=org.xmlBlaster.client.protocol.corba.CorbaCallbackServer
 * </pre>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.html" target="others">protocol</a>
 */
public class CbServerPluginManager extends PluginManagerBase
{
   private final String ME;
   private final Global glob;
   private static Logger log = Logger.getLogger(CbServerPluginManager.class.getName());
   private static final String defaultPluginName = "org.xmlBlaster.client.protocol.corba.CorbaCallbackServer";
   public static final String pluginPropertyName = "ClientCbServerProtocolPlugin";

   public CbServerPluginManager(Global glob) {
      super(glob);
      this.glob = glob;

      this.ME = "CbServerPluginManager" + this.glob.getLogPrefixDashed();
      if (log.isLoggable(Level.FINER)) log.finer("Constructor CbServerPluginManager");
   }

   /**
    * Enforced by PluginManagerBase. 
    * @return The name of the property in xmlBlaster.property "LoadBalancerPlugin"
    * for "LoadBalancerPlugin[RoundRobin][1.0]"
    */
   protected String getPluginPropertyName() {
      return pluginPropertyName;
   }

   /**
    * @return please return your default plugin classname or null if not specified
    */
   public String getDefaultPluginName(String type, String version) {
      return defaultPluginName;
   }

   /**
    * Creates a new instance of the given protocol driver type. 
    * <p />
    * You need to call clientDriver.init(glob, address) on it.
    * @param driverType e.g. "RMI"
    * @return The uninitialized driver, never null
    * @exception XmlBlasterException on problems
    */
   public final I_CallbackServer getNewProtocolDriverInstance(String driverType) throws XmlBlasterException {
      return getPlugin(driverType, "1.0");
   }

   /**
    * Return a specific plugin, every call will create a new plugin instance. 
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return The plugin for this type and version or null if none is specified
    */
   public I_CallbackServer getPlugin(String type, String version) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Creating instance of " + createPluginPropertyKey(type, version));

      // We need a new instance every time! (no caching in base class)
      PluginInfo pluginInfo = new PluginInfo(glob, this, type, version);
      I_CallbackServer driver = (I_CallbackServer)super.instantiatePlugin(pluginInfo, false);
      if (driver == null) log.warning("Creating instance of " + createPluginPropertyKey(type, version) + " failed, no such plugin found.");
      return driver;
   }

   public void postInstantiate(I_Plugin plugin, PluginInfo pluginInfo) {}

   public void activateDrivers() throws XmlBlasterException {
      if (log.isLoggable(Level.FINE)) log.fine("Don't know how to activate the calback server protocol drivers, they are created for each client and session separately");
   }

   public final void deactivateDrivers(boolean force) {
      if (log.isLoggable(Level.FINE)) log.fine("Don't know how to deactivate the calback server protocol drivers, they are created for each client and session separately");
   }

   public void shutdownDrivers(boolean force) throws XmlBlasterException {
      if (log.isLoggable(Level.FINE)) log.fine("Don't know how to shutdown the calback server protocol drivers, they are created for each client and session separately");
   }
}
