/*------------------------------------------------------------------------------
Name:      ProtocolManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   ProtocolManager which loads protocol plugins
Version:   $Id: ProtocolManager.java,v 1.13 2003/03/22 12:28:02 laghi Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.jutils.log.LogChannel;
import org.jutils.JUtilsException;

import org.xmlBlaster.engine.*;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.engine.runlevel.I_RunlevelListener;
import org.xmlBlaster.engine.runlevel.RunlevelManager;

import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;

/**
 * ProtocolManager loads the protocol plugins like CORBA/RMI/XmlRpc. 
 * <p />
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.html" target="others">protocol</a>
 */
public class ProtocolManager extends PluginManagerBase implements I_RunlevelListener
{
   private final String ME;
   private final Global glob;
   private final LogChannel log;
   private final CbProtocolManager cbProtocolManager;
   private static final String defaultPluginName = "org.xmlBlaster.protocol.corba.CorbaDriver";
   public static final String pluginPropertyName = "ProtocolPlugin";

   /** Vector holding all protocol I_Driver.java implementations, e.g. CorbaDriver */
   private Vector protocols = new Vector();

   /** Vector holding all callback protocol I_CallbackDriver.java implementations, e.g. CallbackCorbaDriver */
   private Hashtable cbProtocolClasses = new Hashtable();

   public ProtocolManager(Global glob) {
      super(glob);
      this.glob = glob;
      this.log = glob.getLog("protocol");
      this.ME = "ProtocolManager" + this.glob.getLogPrefixDashed();
      this.cbProtocolManager = new CbProtocolManager(glob);
      if (log.CALL) log.call(ME, "Constructor ProtocolManager");
      glob.getRunlevelManager().addRunlevelListener(this);
   }

   public final CbProtocolManager getCbProtocolManager() {
      return cbProtocolManager;
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
    * Load the drivers from xmlBlaster.properties.
    * <p />
    * <pre>
    *  ProtocolPlugin[IOR][1.0]=org.xmlBlaster.protocol.corba.CorbaDriver
    *  ProtocolPlugin[SOCKET][1.0]=org.xmlBlaster.protocol.socket.SocketDriver
    *  ProtocolPlugin[RMI][1.0]=org.xmlBlaster.protocol.rmi.RmiDriver
    *  ProtocolPlugin[XML-RPC][1.0]=org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver
    *  ProtocolPlugin[JDBC][1.0]=org.xmlBlaster.protocol.jdbc.JdbcDriver
    * </pre>
    */
   private void initDrivers() {
      // ProtocolPlugin[RMI][1.0]=org.xmlBlaster.protocol.rmi.RmiDriver
      Map map = glob.getProperty().get(pluginPropertyName, (Map)null);
      if (map == null) {
         log.error(ME, "No protocol driver configuration like 'ProtocolPlugin[RMI][1.0]=org.xmlBlaster.protocol.rmi.RmiDriver' found");
         map = new TreeMap();
         map.put("IOR:1.0", "org.xmlBlaster.protocol.corba.CorbaDriver");
      }

      Iterator it = map.keySet().iterator();
      while (it.hasNext()) {
         String key = (String)it.next();
         String clazz = (String)map.get(key);        // "org.xmlBlaster.protocol.rmi.RmiDriver"
         if (clazz == null || clazz.length() < 1) {
            log.info(ME, "Ignoring empty protocol driver " + key);
            continue;
         }
         log.trace(ME, "Loading protocol driver " + key + "=" + clazz);
         int colon = key.indexOf(":");
         if (colon < 0) {
            log.error(ME, "Ignoring protocol driver " + key + "=" + clazz + ", wrong format");
            continue;
         }
         String type = key.substring(0, colon);      // "RMI"
         String version = key.substring(colon+1);    // "1.0"
         try {
            I_Driver driver = getPlugin(type, version);
            protocols.addElement(driver);
            driver.init(glob, glob.getAuthenticate(), glob.getAuthenticate().getXmlBlaster());
         }
         catch (XmlBlasterException e) {
            log.error(ME, e.toString());
         }
         catch (Throwable e) {
            log.error(ME, "Problems loading protocol driver type=" + type + " version=" + version + " class=" + clazz + ": " + e.toString());
            e.printStackTrace();
         }
      }

      findUniqueNodeIdName();
   }

   /**
    * Try to extract a unique name for this xmlBlaster server instance,
    * if running in a cluster
    */
   private void findUniqueNodeIdName() {
      String uniqueNodeIdName = null;
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         if (driver.getRawAddress() != null) {
            // choose the shortest (human readable) unique name for this cluster node (xmlBlaster instance)
            if (uniqueNodeIdName == null)
               uniqueNodeIdName = driver.getRawAddress();
            else if (uniqueNodeIdName.length() > driver.getRawAddress().length())
               uniqueNodeIdName = driver.getRawAddress();
         }
      }
      if (glob.getNodeId() == null) {
         if (uniqueNodeIdName != null)
            glob.setUniqueNodeIdName(uniqueNodeIdName);
      }
   }

   /**
    * Return a specific plugin. 
    * <p>
    * The protocol driver is only instantiated once 
    * </p>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return The plugin for this type and version or null if none is specified
    */
   public I_Driver getPlugin(String type, String version) throws XmlBlasterException {
      return (I_Driver)getPluginObject(type, version);
   }

   public void postInstantiate(I_Plugin plugin, PluginInfo pluginInfo) throws XmlBlasterException {
      //((I_Driver)plugin).init(glob, glob.getAuthenticate(), glob.getAuthenticate().getXmlBlaster());
   }

   private void activateDrivers() throws XmlBlasterException {
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         activateDriver(driver);
      }
   }

   private void activateDriver(I_Driver driver) throws XmlBlasterException {
      if (driver != null) {
         try {
            driver.activate();
         } catch (XmlBlasterException e) {
            log.error(ME, "Initializing of driver " + driver.getName() + " failed:" + e.getMessage());
            //throw new XmlBlasterException("Driver.NoInit", "Initializing of driver " + driver.getName() + " failed:" + e.getMessage());
         }
         catch(Throwable e) {
            log.error(ME, "Ignoring problems on loading protocol driver: " + e.toString());
            e.printStackTrace();
         }
      }
   }

   private void deactivateDrivers(boolean force) {
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         try {
            driver.deActivate();
         }
         catch (Throwable e) {
            log.error(ME, "Shutdown of driver " + driver.getName() + " failed: " + e.toString());
         }
      }
   }

   private void shutdownDrivers(boolean force) throws XmlBlasterException {
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         driver.shutdown();
      }
   }

   /**
    * Access all known I_Driver instances. 
    * NOTE: Please don't manipulate the returned Vector
    * @return The vector with protocol drivers, to be handled as immutable objects.
    */
   public Vector getProtocolDrivers() {
      return protocols;
   }

   /**
    * Access all I_Driver instances which have a public available address. 
    * NOTE: Please don't manipulate the returned drivers
    * @return Protocol drivers, to be handled as immutable objects.
    */
   public I_Driver[] getPublicProtocolDrivers() {
      int num = 0;
      if (log.TRACE) log.trace(ME, "Checking " + protocols.size() + " drivers for raw address");
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         if (driver.getRawAddress() != null)
            num++;
      }
      I_Driver[] drivers = new I_Driver[num];
      int count = 0;
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         if (driver.getRawAddress() != null)
            drivers[count++] = driver;
      }
      return drivers;
   }

   /**
    * Protocol driver usage. 
    */
   public String usage() {
      StringBuffer sb = new StringBuffer(2048);
      try {
         Vector protocols = getProtocolDrivers();
         for (int ii=0; ii<protocols.size(); ii++) {
            I_Driver driver = (I_Driver)protocols.elementAt(ii);
            sb.append(driver.usage());
         }
      }
      catch(Exception e) {
         sb.append("Sorry - no help: " + e.toString());
      }
      return sb.toString();
   }

   /**
    * A human readable name of the listener for logging. 
    * <p />
    * Enforced by I_RunlevelListener
    */
   public String getName() {
      return ME;
   }

   /**
    * Invoked on run level change, see RunlevelManager.RUNLEVEL_HALTED and RunlevelManager.RUNLEVEL_RUNNING
    * <p />
    * Enforced by I_RunlevelListener
    */
   public void runlevelChange(int from, int to, boolean force) throws org.xmlBlaster.util.XmlBlasterException {
      //if (log.CALL) log.call(ME, "Changing from run level=" + from + " to level=" + to + " with force=" + force);
      if (to == from)
         return;

      if (to > from) { // startup
         if (to == RunlevelManager.RUNLEVEL_STANDBY) {
            initDrivers();
            glob.getHttpServer(); // incarnate allow http based access (is currently only used by CORBA)
         }
         if (to == RunlevelManager.RUNLEVEL_CLEANUP) {
            cbProtocolManager.activateCbDrivers(); // not implemented: is done for each client on callback
         }
         if (to == RunlevelManager.RUNLEVEL_RUNNING) {
            activateDrivers();
         }
      }
      else if (to < from) { // shutdown
         if (to == RunlevelManager.RUNLEVEL_CLEANUP) {
            deactivateDrivers(force);
         }
         if (to == RunlevelManager.RUNLEVEL_STANDBY) {
            cbProtocolManager.deactivateCbDrivers(force); // not implemented: is done for each client on callback
         }
         if (to == RunlevelManager.RUNLEVEL_HALTED) {
            shutdownDrivers(force);
            cbProtocolManager.shutdownCbDrivers(force);  // not implemented: is done for each client on callback
            protocols.clear();
            cbProtocolClasses.clear();
            glob.shutdownHttpServer();
         }
      }
   }
}
