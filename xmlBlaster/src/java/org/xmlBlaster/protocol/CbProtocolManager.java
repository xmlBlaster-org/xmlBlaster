/*------------------------------------------------------------------------------
Name:      CbProtocolManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.jutils.log.LogChannel;
import org.jutils.JUtilsException;

import org.xmlBlaster.engine.*;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.util.PluginManagerBase;
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
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.html" target="others">protocol</a>
 */
public class CbProtocolManager extends PluginManagerBase
{
   private final String ME;
   private final Global glob;
   private final LogChannel log;
   private static final String defaultPluginName = "org.xmlBlaster.protocol.corba.CallbackCorbaDriver";
   public static final String pluginPropertyName = "CbProtocolPlugin";

   public CbProtocolManager(Global glob) {
      super(glob);
      this.glob = glob;
      this.log = glob.getLog("protocol");
      this.ME = "CbProtocolManager" + this.glob.getLogPraefixDashed();
      if (log.CALL) log.call(ME, "Constructor CbProtocolManager");
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
    * Load the callback drivers from xmlBlaster.properties,
    * here we only store the class objects into a hashtable.
    * <p />
    * Accessing the CallbackDriver for this client, supporting the
    * desired protocol (CORBA, EMAIL, HTTP, RMI).
    * <p />
    * Default is support for IOR, XML-RPC, RMI and the JDBC service (ODBC bridge)
    * <p />
    * This is done once and than cached in the static protocols Hashtable.
    */
   public final void initCbDrivers() {
      //if (glob.getProperty().get("CbProtocolPlugin[RMI][1.0]", (String)null) == null)
      //   glob.getProperty().set("CbProtocolPlugin[RMI][1.0]", "org.xmlBlaster.protocol.rmi.CallbackRmiDriver");
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
      if (log.CALL) log.call(ME+".getPlugin()", "Creating instance of " + getPluginPropertyName(type, version));

      String[] pluginNameAndParam = choosePlugin(type, version);

      if(pluginNameAndParam!=null && pluginNameAndParam[0]!=null && pluginNameAndParam[0].length()>1)
         return (I_CallbackDriver)super.instantiatePlugin(pluginNameAndParam);

      return null;
   }

   public void activateCbDrivers() throws XmlBlasterException {
      if (log.TRACE) log.trace(ME, "Don't know how to activate the callback drivers, they are created for each client and session separately");
   }

   public final void deactivateCbDrivers(boolean force) {
      if (log.TRACE) log.trace(ME, "Don't know how to deactivate the callback drivers, they are created for each client and session separately");
   }

   public void shutdownCbDrivers(boolean force) throws XmlBlasterException {
      if (log.TRACE) log.trace(ME, "Don't know how to shutdown the callback drivers, they are created for each client and session separately");
   }
}
