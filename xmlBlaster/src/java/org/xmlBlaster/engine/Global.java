/*------------------------------------------------------------------------------
Name:      Global.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling global data
Version:   $Id: Global.java,v 1.17 2002/06/17 15:16:43 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.callback.CbWorkerPool;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.engine.cluster.NodeId;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.cluster.ClusterManager;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.protocol.ProtocolManager;
import org.xmlBlaster.authentication.Authenticate;

import java.util.*;
import java.io.IOException;


/**
 * This holds global needed data of one xmlBlaster instance. 
 * <p>
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>
 */
public final class Global extends org.xmlBlaster.util.Global
{
   private static final String ME = "Global";

   private RunlevelManager runlevelManager;

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

   private CbWorkerPool cbWorkerPool;

   private boolean useCluster = true; // default
   private boolean firstUseCluster = true; // to allow caching

   private ProtocolManager protocolManager;

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
         log.info(ME, "Setting xmlBlaster instance name (-cluster.node.id) to '" + nodeId.toString() + "'");
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

   /**
    * Initialize runlevel manager used to start/stop xmlBlaster with different run levels. 
    */
   public final RunlevelManager getRunlevelManager() {
      if (this.runlevelManager == null) {
         synchronized(this) {
            if (this.runlevelManager == null)
               this.runlevelManager = new RunlevelManager(this);
         }
      }
      return this.runlevelManager;
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
    * Initialize protocol manager (to administer CORBA/RMI etc. plugins). 
    */
   public final ProtocolManager getProtocolManager() throws XmlBlasterException {
      if (this.protocolManager == null) {
         synchronized(this) {
            if (this.protocolManager == null)
               this.protocolManager = new ProtocolManager(this);
         }
      }
      return this.protocolManager;
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
         getRunlevelManager().setId(nodeId.getId());
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

   public String getDump() throws XmlBlasterException {
      StringBuffer sb = new StringBuffer(10000);
      sb.append("<xmlBlaster id='").append(getId()).append("'");
      sb.append(" version='").append(getVersion()).append("'");
      sb.append("\n   ");
      sb.append(" buildTimestamp='").append(getBuildTimestamp()).append("'");
      sb.append(" buildJavaVendor='").append(getBuildJavaVendor()).append("'");
      sb.append(" buildJavaVersion='").append(getBuildJavaVersion()).append("'");
      sb.append("\n   ");
      sb.append(" dumpTimestamp='").append(org.jutils.time.TimeHelper.getDateTimeDump(0)).append("'");
      //sb.append(" ='").append(get()).append("'");
      sb.append(">");
      sb.append(getAuthenticate().toXml());
      sb.append(getAuthenticate().getXmlBlaster().toXml());
      sb.append("</xmlBlaster>");
      return sb.toString();
   }

}
