/*------------------------------------------------------------------------------
Name:      Global.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling global data
Version:   $Id: Global.java,v 1.14 2002/06/12 18:43:38 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.callback.CbWorkerPool;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.engine.cluster.NodeId;
import org.xmlBlaster.engine.cluster.ClusterManager;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.authentication.Authenticate;

import java.util.Vector;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Collection;
import java.io.IOException;


/**
 * This holds global needed data of one xmlBlaster instance. 
 * <p>
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>
 */
public final class Global extends org.xmlBlaster.util.Global
{
   private static final String ME = "Global";

   /** the authentication service */
   private Authenticate authenticate = null;
   /** the xmlBlaster core class */
   private RequestBroker requestBroker = null;
   private NodeId nodeId = null;
   private ClusterManager clusterManager;
   private Timeout burstModeTimer;
   private Timeout cbPingTimer;
   private Timeout sessionTimer;
   private Timeout messageTimer;

   /** Vector holding all protocol I_Driver.java implementations, e.g. CorbaDriver */
   private Vector protocols = new Vector();

   /** Vector holding all callback protocol I_CallbackDriver.java implementations, e.g. CallbackCorbaDriver */
   private Hashtable cbProtocols = new Hashtable();

   private CbWorkerPool cbWorkerPool;

   private boolean useCluster = true; // default
   private boolean firstUseCluster = true; // to allow caching

   private CommandManager commandManager;
   private boolean useAdminManager = true;
   private boolean firstUseAdminManager = true; // to allow caching

   /**
    * One instance of this represents one xmlBlaster server.
    */
   public Global() {
      super();
      initThis();
      //Thread.currentThread().dumpStack();
   }

   /**
    * One instance of this represents one xmlBlaster server.
    * @param args Environment arguments (key/value pairs)
    */
   public Global(String[] args) {
      init(args);
      //Thread.currentThread().dumpStack();
   }

   /**
    * If you have a util.Global and need a engine.Global. 
    * <p />
    * Note: The cluster node id of utilGlob is overwritten
    */
   public Global(org.xmlBlaster.util.Global utilGlob) {
      super(utilGlob);
      initThis();
      utilGlob.setId(getId()); // Inherit backwards the cluster node id
      //Thread.currentThread().dumpStack();
   }

   /**
    * Calls super.init and checks the environment for "cluster.node.id"
    * <p />
    * See private org.xmlBlaster.Main#createNodeId()
    * @return 1 Show usage, 0 OK, -1 error
    */
   public int init(String[] args) {
      int ret = super.init(args);
      initThis();
      return ret;
   }

   private void initThis() {
      String id = getProperty().get("cluster.node.id", (String)null);
      if (id == null && getBootstrapAddress().getPort() > 0) {
         id = getBootstrapAddress().getAddress();
      }
      if (id != null) {
         nodeId = new NodeId(id);
         super.setId(nodeId.toString());
         log.info(ME, "Setting xmlBlaster instance name (cluster node id) to '" + nodeId.toString() + "'");
      }
   }

   /**
    * The unique name of this xmlBlaster server instance. 
    * @return Can be null during startup
    */
   public final NodeId getNodeId() {
      return nodeId;
   }

   /**
    * Access the unique cluster node id (as a String). 
    * @return The name of this xmlBlaster instance, e.g. "heron.mycompany.com"
    *         Can be null during startup
    */
   public final String getId() {
      if (getNodeId() == null) return null;
      return getNodeId().getId();
   }

   public final void setId(String id) {
      super.setId(id);
      nodeId = new NodeId(id);
   }

   public void addProtocolDriver(I_Driver driver) {
      protocols.addElement(driver);
   }

   public void shutdownProtocolDrivers() {
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         try {
            driver.shutdown();
         }
         catch (Throwable e) {
            log.error(ME, "Shutdown of driver " + driver.getName() + " failed: " + e.toString());
         }
      }
      protocols.clear();
   }

   /**
    * Stops the protocol specific driver (e.g. XML-RPC) and
    * removes the handle from the list
    */
   public void shutdownProtocolDriver(I_Driver driver) {
      try {
         driver.shutdown();
      }
      catch (Throwable e) {
         log.error(ME, "Shutdown of driver " + driver.getName() + " failed: " + e.toString());
      }
      protocols.removeElement(driver);
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
    * Access all I_CallbackDriver instances which have a public available address. 
    * NOTE: Please don't manipulate the returned drivers
    * @return CbProtocol drivers, to be handled as immutable objects.
    */
    /*
   public final I_CallbackDriver[] getCbProtocolDrivers() {
      return (I_CallbackDriver[])cbProtocols.values().toArray(new I_CallbackDriver[cbProtocols.size()]);
   }  */

   /**
    * @param driverType e.g. "RMI" or "IOR"
    */
   public final void addCbProtocolDriverClass(String driverType, Class driver) {
      cbProtocols.put(driverType, driver);
   }

   public final Class getCbProtocolDriverClass(String driverType) {
      return (Class)cbProtocols.get(driverType);
   }

   /**
    * Creates a new instance of the given protocol driver type. 
    * <p />
    * You need to call cbDriver.init(glob, cbAddress) on it.
    * @return The uninitialized driver, never null
    * @exception XmlBlasterException on problems
    */
   public final I_CallbackDriver getCbProtocolDriver(String driverType) throws XmlBlasterException {
      Class cl = getCbProtocolDriverClass(driverType);
      String err = null;
      try {
         I_CallbackDriver cbDriver = (I_CallbackDriver)cl.newInstance();
         if (log.TRACE) log.trace(ME, "Created callback driver for protocol '" + driverType + "'");
         return cbDriver;
      }
      catch (IllegalAccessException e) {
         err = "The protocol driver class '" + driverType + "' is not accessible\n -> check the driver name and/or the CLASSPATH to the driver";
      }
      catch (SecurityException e) {
         err = "No right to access the protocol driver class or initializer '" + driverType + "'";
      }
      catch (Throwable e) {
         err = "The protocol driver class or initializer '" + driverType + "' is invalid\n -> check the driver name and/or the CLASSPATH to the driver file: " + e.toString();
      }
      log.error(ME, err);
      throw new XmlBlasterException(ME, err);
   }

   public final void shutdownCbProtocolDrivers() {
      Iterator iterator = cbProtocols.values().iterator();
      while (iterator.hasNext()) {
         I_CallbackDriver driver = (I_CallbackDriver)iterator.next();
         try {
            driver.shutdown();
         }
         catch (Throwable e) {
            log.error(ME, "Shutdown of driver " + driver.getName() + " failed: " + e.toString());
         }
      }
      cbProtocols.clear();
   }

   /**
    * Access instance which manages myself in a cluster environment. 
    * @return null if cluster support is switched off
    */
   public final ClusterManager getClusterManager() throws XmlBlasterException {
      if (this.clusterManager == null) {
         if (!useCluster())
            return null;
         log.error(ME, "Internal problem: please intialize ClusterManager first");
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Internal problem: please intialize ClusterManager first - Please ask on mailing list for support");
      }
      return this.clusterManager;
   }

   /**
    * Is cluster support switched on?
    */
   public final boolean useCluster() {
      if (firstUseCluster) {
         useCluster = getProperty().get("cluster", useCluster);
         firstUseCluster = false;
      }
      return useCluster;
   }

   /**
    * Initialize the instance which manages myself in a cluster environment. 
    * Only the first call will set the sessionInfo
    * @param An internal sessionInfo instance, see RequestBroker.
    * @return null if cluster support is switched off
    */
   public final ClusterManager getClusterManager(org.xmlBlaster.authentication.SessionInfo sessionInfo) throws XmlBlasterException {
      if (this.clusterManager == null) {
         if (!useCluster())
            return null;
         synchronized(this) {
            if (this.clusterManager == null)
               this.clusterManager = new ClusterManager(this, sessionInfo);
         }
      }
      return this.clusterManager;
   }

   /**
    * Sets the unique node id of this xmlBlaster server instance (needed for clustering). 
    * <p />
    * The new node ID is only set if my current instance is null!
    * <p />
    * See private org.xmlBlaster.Main#createNodeId()
    */
   public void setUniqueNodeIdName(String uniqueNodeIdName) {
      if (nodeId == null && uniqueNodeIdName != null) {
         nodeId = new NodeId(uniqueNodeIdName);
         log.info(ME, "Setting xmlBlaster instance name to '" + nodeId.toString() + "'");
      }
   }

   /**
    * Access the handle of the burst mode timer thread. 
    * @return The Timeout instance
    */
   public final Timeout getBurstModeTimer() {
      if (this.burstModeTimer == null) {
         synchronized(this) {
            if (this.burstModeTimer == null)
               this.burstModeTimer = new Timeout("BurstmodeTimer");
         }
      }
      return this.burstModeTimer;
   }

   /**
    * Access the handle of the callback ping timer thread. 
    * @return The Timeout instance
    */
   public final Timeout getCbPingTimer() {
      if (this.cbPingTimer == null) {
         synchronized(this) {
            if (this.cbPingTimer == null)
               this.cbPingTimer = new Timeout("CbPingTimer");
         }
      }
      return this.cbPingTimer;
   }

   /**
    * Access the handle of the user session timer thread. 
    * @return The Timeout instance
    */
   public final Timeout getSessionTimer() {
      if (this.sessionTimer == null) {
         synchronized(this) {
            if (this.sessionTimer == null)
               this.sessionTimer = new Timeout("SessionTimer");
         }
      }
      return this.sessionTimer;
   }

   /**
    * Access the handle of the message expiry timer thread. 
    * @return The Timeout instance
    */
   public final Timeout getMessageTimer() {
      if (this.messageTimer == null) {
         synchronized(this) {
            if (this.messageTimer == null)
               this.messageTimer = new Timeout("MessageTimer");
         }
      }
      return this.messageTimer;
   }

   /**
    * Access the handle of the callback thread pool. 
    * @return The CbWorkerPool instance
    */
   public final CbWorkerPool getCbWorkerPool() {
      if (this.cbWorkerPool == null) {
         synchronized(this) {
            if (this.cbWorkerPool == null)
               this.cbWorkerPool = new CbWorkerPool(this);
         }
      }
      return this.cbWorkerPool;
   }

   /**
    * Access instance of remote command administration manager. 
    * @return null if command administration support is switched off
    */
   public final CommandManager getCommandManager() throws XmlBlasterException {
      if (this.commandManager == null) {
         if (!useAdminManager())
            return null;
         log.error(ME, "Internal problem: please intialize CommandManager first");
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Internal problem: please intialize CommandManager first - Please ask on mailing list for support");
      }
      return this.commandManager;
   }

   /**
    * @return Is command administration support switched on?
    */
   public final boolean useAdminManager() {
      if (firstUseAdminManager) {
         useAdminManager = getProperty().get("admin", useAdminManager);
         firstUseAdminManager = false;
      }
      return useAdminManager;
   }

   /**
    * Initialize instance of remote command administration manager. 
    * Only the first call will set the sessionInfo
    * @param An internal sessionInfo instance, see RequestBroker.
    * @return null if command support is switched off
    */
   public final CommandManager getCommandManager(org.xmlBlaster.authentication.SessionInfo sessionInfo) throws XmlBlasterException {
      if (this.commandManager == null) {
         if (!useAdminManager())
            return null;
         synchronized(this) {
            if (this.commandManager == null)
               this.commandManager = new CommandManager(this, sessionInfo);
         }
      }
      return this.commandManager;
   }

   public void setAuthenticate(Authenticate auth) {
      this.authenticate = auth;
   }

   public Authenticate getAuthenticate() {
      return this.authenticate;
   }

   public void setRequestBroker(RequestBroker requestBroker) {
      this.requestBroker = requestBroker;
   }

   public RequestBroker getRequestBroker() {
      return this.requestBroker;
   }
}
