/*------------------------------------------------------------------------------
Name:      Global.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling global data on server side
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.cluster.ClusterManager;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.extern.MomClientGateway;
import org.xmlBlaster.protocol.ProtocolManager;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.dispatch.DeliveryManager;
import org.xmlBlaster.util.dispatch.DeliveryConnectionsHandler;
import org.xmlBlaster.engine.dispatch.CbDeliveryConnectionsHandler;
import org.xmlBlaster.util.queue.I_EntryFactory;
import org.xmlBlaster.engine.queuemsg.ServerEntryFactory;
import org.xmlBlaster.engine.msgstore.MsgUnitStorePluginManager;
import org.xmlBlaster.engine.msgstore.TopicStorePluginManager;
import org.xmlBlaster.engine.persistence.MsgFileDumper;


import java.util.*;
import java.io.IOException;


/**
 * This holds global needed data of one xmlBlaster instance. 
 * <p>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public final class Global extends org.xmlBlaster.util.Global implements I_RunlevelListener
{
   private RunlevelManager runlevelManager;

   private ContextNode contextNode;

   /** the authentication service */
   private Authenticate authenticate;
   /** the xmlBlaster core class */
   private RequestBroker requestBroker;
   private NodeId nodeId;
   private ClusterManager clusterManager;
   private Timeout sessionTimer;
   private Timeout topicTimer;

   private boolean useCluster = true; // default
   private boolean firstUseCluster = true; // to allow caching

   private ProtocolManager protocolManager;

   private MsgUnitStorePluginManager msgStorePluginManager;
   private TopicStorePluginManager topicStorePluginManager;

   private CommandManager commandManager;
   private boolean useAdminManager = true;
   private boolean firstUseAdminManager = true; // to allow caching
   private MomClientGateway momClientGateway = null;

   private MsgFileDumper msgFileDumper;


   public void shutdown() { 
      super.shutdown();
      log.info(ME, "Destroying global handle");
      if (sessionTimer != null) {
         sessionTimer.shutdown();
         sessionTimer = null;
      }
      if (topicTimer != null) {
         topicTimer.shutdown();
         topicTimer = null;
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
      if (myId == null && !useCluster())
         myId = "xmlBlaster";  // fallback
      if (myId == null && getBootstrapAddress().getPort() > 0) {
         myId = getStrippedString(getBootstrapAddress().getAddress());
      }
      if (myId != null) {
         setId(myId);
      }
      getRunlevelManager().addRunlevelListener(this);
      log.info(ME, "Setting xmlBlaster instance name (-cluster.node.id) to '" + getId() + "'");
   }

   /**
    * The unique name of this xmlBlaster server instance. 
    * @return Can be null during startup
    */
   public final NodeId getNodeId() {
      return this.nodeId;
   }

   /**
    * The unique name of this xmlBlaster server instance. 
    * @return Can be null during startup
    */
   public final ContextNode getContextNode() {
      return contextNode;
   }

   /**
    * Access the unique cluster node id (as a String). 
    * @return The name of this xmlBlaster instance, e.g. "heron.mycompany.com"
    *         or "http://mycomp:3412"
    *         Can be null during startup
    */
   public final String getId() {
      if (getNodeId() == null) return null;
      return getNodeId().getId();
   }

   public final void setId(String id) {
      super.setId(id);
      this.nodeId = new NodeId(id); // ContextNode should replace NodeId one day
      this.contextNode = new ContextNode(this, ContextNode.CLUSTER_MARKER_TAG, getStrippedId(), (ContextNode)null);
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
   public final String getLogPrefix() {
      if (useCluster()) {
         /*
          Switch of too much logging if no other cluster is around does
         if (this.clusterManager == null)
            return "";
         if (this.clusterManager.getNumNodes() == 1)
            return "";
         */
         return "/node/" + getId(); //getStrippedId();
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
   public final String getLogPrefixDashed(String post) {
      String prae = getLogPrefix();                         // getStrippedId
      return (prae.length() < 1) ? ("-" + post) : ("-/node/" + getId() + "/" + post); // relativ or absolute addressed
   }

   /**
    * Same as getLogPrefix() but if in cluster environment a "-" is prefixed
    * like "-/node/heron/". 
    * <p />
    * Useful for logging information of classes like Authenticate.java
    */
   public final String getLogPrefixDashed() {
      String prae = getLogPrefix();
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
               this.ME = "Global" + getLogPrefixDashed();
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

   public final MsgUnitStorePluginManager getMsgUnitStorePluginManager() {
      if (msgStorePluginManager == null) {
         synchronized (MsgUnitStorePluginManager.class) {
            if (msgStorePluginManager == null)
               msgStorePluginManager = new MsgUnitStorePluginManager(this);
         }
      }
      return msgStorePluginManager;
   }

   public final TopicStorePluginManager getTopicStorePluginManager() {
      if (topicStorePluginManager == null) {
         synchronized (TopicStorePluginManager.class) {
            if (topicStorePluginManager == null)
               topicStorePluginManager = new TopicStorePluginManager(this);
         }
      }
      return topicStorePluginManager;
   }

   /**
    * A helper to dump a message to a file. 
    */
   public final MsgFileDumper getMsgFileDumper() throws XmlBlasterException {
      if (this.msgFileDumper == null) {
         synchronized(this) {
            if (this.msgFileDumper == null) {
               this.msgFileDumper = new MsgFileDumper();
               this.msgFileDumper.init(this);
            }
         }
      }
      return this.msgFileDumper;
   }

   /**
    * Sets the unique node id of this xmlBlaster server instance (needed for clustering). 
    * <p />
    * The new node ID is only set if my current instance is null!
    * <p />
    * See private org.xmlBlaster.Main#createNodeId()
    */
   public void setUniqueNodeIdName(String uniqueNodeIdName) {
      if (this.nodeId == null && uniqueNodeIdName != null) {
         this.nodeId = new NodeId(uniqueNodeIdName);
         super.id = this.nodeId.getId();
         this.contextNode = new ContextNode(this, ContextNode.CLUSTER_MARKER_TAG, getStrippedId(), (ContextNode)null);
         log.info(ME, "Setting xmlBlaster instance name to '" + this.nodeId.toString() + "'");
         getRunlevelManager().setId(this.nodeId.getId());
      }
   }

   /**
    * Access the handle of the user session timer thread. 
    * @return The Timeout instance
    */
   public final Timeout getSessionTimer() {
      if (this.sessionTimer == null) {
         synchronized(this) {
            if (this.sessionTimer == null)
               this.sessionTimer = new Timeout("XmlBlaster.SessionTimer");
         }
      }
      return this.sessionTimer;
   }

   /**
    * Access the handle of the TopicHandler timer thread. 
    * @return The Timeout instance
    */
   public final Timeout getTopicTimer() {
      if (this.topicTimer == null) {
         synchronized(this) {
            if (this.topicTimer == null)
               this.topicTimer = new Timeout("XmlBlaster.TopicTimer");
         }
      }
      return this.topicTimer;
   }

   /**
    * The factory creating queue or msgUnitStore entries from persistent store. 
    * Is derived from util.Global
    * @param name A name identifying this plugin.
    */
   public I_EntryFactory getEntryFactory(String name) {
      ServerEntryFactory factory = new ServerEntryFactory();
      factory.initialize(this, name);
      return factory;
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

   public final boolean supportAdministrative() {
      return this.momClientGateway != null;
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
      return xmlKey.isAdministrative();
   }

   /**
    * Returns the callback layer implementation 'CbDeliveryConnectionsHandler'. 
    * In util.Global we return the client side implementation 'ClientDeliveryConnectionsHandler'
    * @return A new instance of CbDeliveryConnectionsHandler
    */
   public DeliveryConnectionsHandler createDeliveryConnectionsHandler(DeliveryManager deliveryManager, AddressBase[] addrArr) throws XmlBlasterException {
      return new CbDeliveryConnectionsHandler(this, deliveryManager, addrArr);
   }

   /**
    * Sets the authentication in the engine.Global scope. 
    * <p>
    * Additionally the I_Authentication is registered in the <i>util.Global.addObjectEntry</i>
    * under the name <i>"/xmlBlaster/I_Authenticate"</i> (see Constants.I_AUTHENTICATE_PROPERTY_KEY).<br />
    * This allows lookup similar to a naming service if we are in the same JVM.
    */
   public void setAuthenticate(Authenticate auth) {
      this.authenticate = auth;
      addObjectEntry(Constants.I_AUTHENTICATE_PROPERTY_KEY, this.authenticate);
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
      String offset = "\n";

      sb.append(offset).append("<xmlBlaster id='").append(getId()).append("'");
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
      sb.append(offset).append("</xmlBlaster>");
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
      sb.append("    Supported is [core], [auth], [dispatch], [mime], [corba], [xmlrpc] [admin]\n");
      return sb.toString();
   }
}

