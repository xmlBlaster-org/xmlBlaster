/*------------------------------------------------------------------------------
Name:      Global.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling global data
Version:   $Id: Global.java,v 1.24 2002/09/19 20:56:43 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.callback.CbWorkerPool;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.engine.cluster.NodeId;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.cluster.ClusterManager;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.extern.MomClientGateway;
import org.xmlBlaster.protocol.ProtocolManager;
import org.xmlBlaster.authentication.Authenticate;

import java.util.*;
import java.io.IOException;


/**
 * This holds global needed data of one xmlBlaster instance. 
 * <p>
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>
 */
public final class Global extends org.xmlBlaster.util.Global implements I_RunlevelListener
{
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
   private MomClientGateway momClientGateway = null;


   public void shutdown() { 
      log.info(ME, "Destroying global handle");
      if (cbWorkerPool != null) {
         // registered itsell to Runlevel changes cbWorkerPool.shutdown();
         cbWorkerPool = null;
      }
      if (burstModeTimer != null) {
         burstModeTimer.shutdown();
         burstModeTimer = null;
      }
      if (cbPingTimer != null) {
         cbPingTimer.shutdown();
         cbPingTimer = null;
      }
      if (sessionTimer != null) {
         sessionTimer.shutdown();
         sessionTimer = null;
      }
      if (messageTimer != null) {
         messageTimer.shutdown();
         messageTimer = null;
      }
   }

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

   public Global(Properties p) {
      super(p);
      initThis();
      // The util.Global base class can't initiliaze it, as this class is initialized later and overwrites with null
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
      String myId = getProperty().get("cluster.node.id", (String)null);
      if (myId == null && getBootstrapAddress().getPort() > 0) {
         myId = getBootstrapAddress().getAddress();
      }
      if (myId != null) {
         setId(myId);
         log.info(ME, "Setting xmlBlaster instance name (-cluster.node.id) to '" + getId() + "'");
      }
      getRunlevelManager().addRunlevelListener(this);
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
    * Returns for no cluster an empty string, in
    * cluster environment if more than one node is known
    * it returns the node id like "/node/heron". 
    * <p />
    * Used for logging
    */
   public final String getLogPraefix() {
      if (useCluster()) {
         /*
          Switch of too much logging if no other cluster is around does
         if (this.clusterManager == null)
            return "";
         if (this.clusterManager.getNumNodes() == 1)
            return "";
         */
         return "/node/" + getAdminId();
      }
      else
         return "";
   }

   /**
    * Returns for no cluster the given post string.
    * In cluster environment if more than one node is known
    * it returns the node id like "/node/heron/client" if post="client". 
    * <p />
    * Used for logging
    * @param post the postfix string like "client"
    */
   public final String getLogPraefixDashed(String post) {
      String prae = getLogPraefix();
      return (prae.length() < 1) ? ("-" + post) : ("-/node/" + getAdminId() + "/" + post); // relativ or absolute addressed
   }

   /**
    * Same as getLogPraefix() but if in cluster environment a "-" is praefixed
    * like "-/node/heron/". 
    * <p />
    * Useful for logging information of classes like Authenticate.java
    */
   public final String getLogPraefixDashed() {
      String prae = getLogPraefix();
      if (prae.length() > 0)
         return "-" + prae;
      return "";
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
            if (this.clusterManager == null) {
               this.clusterManager = new ClusterManager(this, sessionInfo);
               this.ME = "Global" + getLogPraefixDashed();
            }
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

   /**
    * Access handler to forward messages beginning with "__cmd:" (administrative messages)
    * @return null if no available. Is guaranteed to be not null if isAdministrationCommand(XmlKey)
    *         returned true.
    */
   public final MomClientGateway getMomClientGateway() {
      return this.momClientGateway;
   }

   /**
    * Invoked by CommandManager to register message command handler
    */
   public final void registerMomClientGateway(MomClientGateway momClientGateway) {
      this.momClientGateway = momClientGateway;
   }

   /**
    * @return true if MomClientGateway is registered and key oid starts with "__cmd:" (case sensitiv)
    */
   public final boolean isAdministrationCommand(XmlKey xmlKey) throws XmlBlasterException {
      if (this.momClientGateway == null) return false;
      if (xmlKey == null) {
         log.error(ME, "Illegal null argument in isAdministrationCommand()");
         Thread.currentThread().dumpStack();
         return false;
      }
      return xmlKey.getUniqueKey().startsWith("__cmd:");
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
         }
         if (to == RunlevelManager.RUNLEVEL_CLEANUP) {
         }
         if (to == RunlevelManager.RUNLEVEL_RUNNING) {
         }
      }
      else if (to < from) { // shutdown
         if (to == RunlevelManager.RUNLEVEL_CLEANUP) {
         }
         if (to == RunlevelManager.RUNLEVEL_STANDBY) {
         }
         if (to == RunlevelManager.RUNLEVEL_HALTED) {
            shutdown();
         }
      }
   }

   /**
    * If property -xmlBlaster.isEmbedded true is set we return true here
    * <p />
    * An embedded server should not do any exit()
    */
   public boolean isEmbedded() {
      return getProperty().get("xmlBlaster.isEmbedded", false);
   }

   public String getDump() throws XmlBlasterException {
      StringBuffer sb = new StringBuffer(10000);
      sb.append("<xmlBlaster id='").append(getId()).append("'");
      sb.append(" version='").append(getVersion()).append("' counter='").append(counter).append("'");
      sb.append("\n   ");
      sb.append(" buildTimestamp='").append(getBuildTimestamp()).append("'");
      sb.append(" buildJavaVendor='").append(getBuildJavaVendor()).append("'");
      sb.append(" buildJavaVersion='").append(getBuildJavaVersion()).append("'");
      sb.append("\n   ");
      sb.append(" dumpTimestamp='").append(org.jutils.time.TimeHelper.getDateTimeDump(0)).append("'");
      //sb.append(" ='").append(get()).append("'");
      sb.append(">");
      if (getAuthenticate() != null) {
         sb.append(getAuthenticate().toXml());
         if (getAuthenticate().getXmlBlaster() != null) {
            sb.append(getAuthenticate().getXmlBlaster().toXml());
         }
      }
      sb.append("</xmlBlaster>");
      return sb.toString();
   }

   /**
    * Command line usage.
    * <p />
    * These variables may be set in your property file as well.
    * Don't use the "-" prefix there.
    * <p />
    * Set the verbosity when loading properties (outputs with System.out).
    * <p />
    * 0=nothing, 1=info, 2=trace, configure with
    * <pre>
    * java -Dproperty.verbose 2 ...
    *
    * java org.xmlBlaster.Main -property.verbose 2
    * </pre>
    */
   public String usage()
   {
      StringBuffer sb = new StringBuffer(512);
      sb.append(super.usage());
      sb.append("\n");
      sb.append("  There are fine grained logging possibilities like:\n");
      sb.append("   -trace[corba]       Switch on trace mode only for IOR driver.\n");
      sb.append("   -call[cluster]      Show method calls in the cluster module.\n");
      sb.append("   -trace[mime]        Trace code in mime based filter plugins.\n");
      sb.append("    Supported is [core], [auth], [cb], [mime], [corba], [xmlrpc] [admin]\n");
      return sb.toString();
   }
}
