/*------------------------------------------------------------------------------
Name:      ProtocolManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   ProtocolManager which loads protocol plugins
Version:   $Id: ProtocolManager.java,v 1.6 2002/08/23 21:24:55 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.jutils.log.LogChannel;
import org.jutils.JUtilsException;

import org.xmlBlaster.engine.*;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.util.PluginManagerBase;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.authentication.Authenticate;

import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;

/**
 * ProtocolManager loads the protocol plugins like CORBA/RMI/XmlRpc. 
 * <p />
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
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
      this.ME = "ProtocolManager" + this.glob.getLogPraefixDashed();
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
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return The load balancer for this type and version or null if none is specified
    */
   public I_Driver getPlugin(String type, String version) throws XmlBlasterException {
      if (log.CALL) log.call(ME+".getPlugin()", "Loading " + getPluginPropertyName(type, version));
      I_Driver driver = null;
      String[] pluginNameAndParam = null;

      pluginNameAndParam = choosePlugin(type, version);

      if(pluginNameAndParam!=null && pluginNameAndParam[0]!=null && pluginNameAndParam[0].length()>1) {
         driver = (I_Driver)managers.get(pluginNameAndParam[0]);
         if (driver!=null) return driver;
         driver = loadPlugin(pluginNameAndParam);
      }
      else {
         //throw new XmlBlasterException(ME+".notSupported","The requested security manager isn't supported!");
      }

      return driver;
   }

   /**
    * Loads the plugin. 
    * <p/>
    * @param String[] The first element of this array contains the class name
    *                 e.g. org.xmlBlaster.engine.cluster.simpledomain.RoundRobin<br />
    *                 Following elements are arguments for the plugin. (Like in c/c++ the command-line arguments.)
    * @return I_LoadBalancer
    * @exception XmlBlasterException Thrown if loading or initializing failed.
    */
   protected I_Driver loadPlugin(String[] pluginNameAndParam) throws XmlBlasterException {
      I_Driver i = (I_Driver)super.instantiatePlugin(pluginNameAndParam);
      i.init(glob, glob.getAuthenticate(), glob.getAuthenticate().getXmlBlaster());
      return i;
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
            log.error(ME, "Initializing of driver " + driver.getName() + " failed:" + e.reason);
            //throw new XmlBlasterException("Driver.NoInit", "Initializing of driver " + driver.getName() + " failed:" + e.reason);
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
         driver.shutdown(force);
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
            cbProtocolManager.initCbDrivers();
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
