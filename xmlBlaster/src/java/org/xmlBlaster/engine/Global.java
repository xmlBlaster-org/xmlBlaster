/*------------------------------------------------------------------------------
Name:      Global.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling global data
Version:   $Id: Global.java,v 1.7 2002/05/11 08:08:44 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.callback.CbWorkerPool;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.engine.cluster.NodeId;
import org.xmlBlaster.engine.cluster.ClusterManager;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.authentication.Authenticate;

import java.util.Vector;


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
   private Timeout sessionTimer;
   private Timeout messageTimer;

   /** Vector holding all protocol I_Driver.java implementations, e.g. CorbaDriver */
   private Vector protocols = new Vector();

   private CbWorkerPool cbWorkerPool;

   /**
    * One instance of this represents one xmlBlaster server.
    */
   public Global()
   {
      super();
   }

   /**
    * One instance of this represents one xmlBlaster server.
    * @param args Environment arguments (key/value pairs)
    */
   public Global(String[] args)
   {
      init(args);
   }

   /**
    * Calls super.init and checks the environment for "cluster.node.id"
    * @return 1 Show usage, 0 OK, -1 error
    * @see org.xmlBlaster.Main#createNodeId()
    */
   public int init(String[] args)
   {
      int ret = super.init(args);
      String id = getProperty().get("cluster.node.id", (String)null);
      if (id == null && getBootstrapAddress().getPort() > 0) {
         id = getBootstrapAddress().getAddress();
      }
      if (id != null) {
         nodeId = new NodeId(id);
         super.setId(nodeId.toString());
         log.info(ME, "Setting xmlBlaster instance name (cluster node id) to '" + nodeId.toString() + "'");
      }
      return ret;
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
    * The instance which manages myself in a cluster environment. 
    */
   public final ClusterManager getClusterManager() throws XmlBlasterException {
      if (this.clusterManager == null) {
         synchronized(this) {
            if (this.clusterManager == null)
               this.clusterManager = new ClusterManager(this);
         }
      }
      return this.clusterManager;
   }

   /**
    * Sets the unique node id of this xmlBlaster server instance (needed for clustering). 
    * <p />
    * The new node ID is only set if my current instance is null!
    * @see org.xmlBlaster.Main#createNodeId()
    */
   public void setUniqueNodeIdName(String uniqueNodeIdName)
   {
      if (nodeId == null && uniqueNodeIdName != null) {
         nodeId = new NodeId(uniqueNodeIdName);
         log.info(ME, "Setting xmlBlaster instance name to '" + nodeId.toString() + "'");
      }
   }

   /**
    * Access the handle of the burst mode timer thread. 
    * @return The Timeout instance
    */
   public final Timeout getBurstModeTimer()
   {
      if (this.burstModeTimer == null) {
         synchronized(this) {
            if (this.burstModeTimer == null)
               this.burstModeTimer = new Timeout("BurstmodeTimer");
         }
      }
      return this.burstModeTimer;
   }

   /**
    * Access the handle of the user session timer thread. 
    * @return The Timeout instance
    */
   public final Timeout getSessionTimer()
   {
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
   public final Timeout getMessageTimer()
   {
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
   public final CbWorkerPool getCbWorkerPool()
   {
      if (this.cbWorkerPool == null) {
         synchronized(this) {
            if (this.cbWorkerPool == null)
               this.cbWorkerPool = new CbWorkerPool(this);
         }
      }
      return this.cbWorkerPool;
   }

   public void setAuthenticate(Authenticate auth)
   {
      this.authenticate = auth;
   }

   public Authenticate getAuthenticate()
   {
      return this.authenticate;
   }

   public void setRequestBroker(RequestBroker requestBroker)
   {
      this.requestBroker = requestBroker;
   }

   public RequestBroker getRequestBroker()
   {
      return this.requestBroker;
   }
}
