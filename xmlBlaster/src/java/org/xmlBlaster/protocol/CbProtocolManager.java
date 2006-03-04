/*------------------------------------------------------------------------------
Name:      CbProtocolManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.jutils.JUtilsException;

import org.xmlBlaster.engine.*;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.authentication.Authenticate;

import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;

/**
 * CbProtocolManager loads the callback protocol plugins like CORBA/RMI/XmlRpc. 
 * <p />
 * <pre>
 * A typical xmlBlaster.properties entry:
 *
 * CbProtocolPlugin[IOR][1.0]=org.xmlBlaster.protocol.corba.CallbackCorbaDriver
 * </pre>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.html" target="others">protocol</a>
 */
public class CbProtocolManager extends PluginManagerBase
{
   private final String ME;
   private final Global glob;
   private static Logger log = Logger.getLogger(CbProtocolManager.class.getName());
   private static final String defaultPluginName = "org.xmlBlaster.protocol.corba.CallbackCorbaDriver";
   public static final String pluginPropertyName = "CbProtocolPlugin";

   public CbProtocolManager(Global glob) {
      super(glob);
      this.glob = glob;

      this.ME = "CbProtocolManager" + this.glob.getLogPrefixDashed();
      if (log.isLoggable(Level.FINER)) log.finer("Constructor CbProtocolManager");
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
    * You need to call cbDriver.init(glob, cbAddress) on it.
    * @param driverType e.g. "RMI"
    * @return The uninitialized driver, never null
    * @exception XmlBlasterException on problems
    */
   public final I_CallbackDriver getNewCbProtocolDriverInstance(String driverType) throws XmlBlasterException {
      return getPlugin(driverType, "1.0");
   }

   /**
    * Return a specific plugin, every call will create a new plugin instance. 
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return The plugin for this type and version or null if none is specified
    */
   public I_CallbackDriver getPlugin(String type, String version) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Creating instance of " + createPluginPropertyKey(type, version));

      // We need a new instance every time! (no caching in base class)
      PluginInfo pluginInfo = new PluginInfo(glob, this, type, version);
      I_CallbackDriver driver = (I_CallbackDriver)super.instantiatePlugin(pluginInfo, false);
      return driver;
   }

   public void postInstantiate(I_Plugin plugin, PluginInfo pluginInfo) {}

   public void activateCbDrivers() throws XmlBlasterException {
      if (log.isLoggable(Level.FINE)) log.fine("Don't know how to activate the callback drivers, they are created for each client and session separately");
   }

   public final void deactivateCbDrivers(boolean force) {
      if (log.isLoggable(Level.FINE)) log.fine("Don't know how to deactivate the callback drivers, they are created for each client and session separately");
   }

   public void shutdownCbDrivers(boolean force) throws XmlBlasterException {
      if (log.isLoggable(Level.FINE)) log.fine("Don't know how to shutdown the callback drivers, they are created for each client and session separately");
   }
}
