/*------------------------------------------------------------------------------
Name:      Global.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling global data on server side
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.cluster.ClusterManager;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.extern.MomClientGateway;
import org.xmlBlaster.protocol.CbProtocolManager;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.dispatch.DispatchConnectionsHandler;
import org.xmlBlaster.engine.dispatch.CbDispatchConnectionsHandler;
import org.xmlBlaster.engine.distributor.plugins.MsgDistributorPluginManager;
import org.xmlBlaster.util.queue.I_EntryFactory;
import org.xmlBlaster.engine.queuemsg.ServerEntryFactory;
import org.xmlBlaster.engine.msgstore.StoragePluginManager;
import org.xmlBlaster.engine.persistence.MsgFileDumper;
import org.xmlBlaster.engine.runlevel.I_RunlevelListener;
import org.xmlBlaster.engine.runlevel.RunlevelManager;
import org.xmlBlaster.engine.runlevel.PluginHolderSaxFactory;
import org.xmlBlaster.engine.runlevel.PluginHolder;

import java.util.*;
import org.jutils.init.Property;

/**
 * This holds global needed data of one xmlBlaster instance.
 * <p>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public final class Global extends org.xmlBlaster.util.Global implements I_RunlevelListener
{
   private RunlevelManager runlevelManager;

   /** the authentication service (a layer around it for security reasons) */
   private I_Authenticate authenticate;
   /** the xmlBlaster core class */
   private RequestBroker requestBroker;
   private NodeId nodeId;
   private ClusterManager clusterManager;
   private Timeout sessionTimer;
   private Timeout topicTimer;
   private Timeout telnetSessionTimer;

   private boolean useCluster = true; // default
   private boolean firstUseCluster = true; // to allow caching

   private CbProtocolManager cbProtocolManager;

   private StoragePluginManager topicStorePluginManager;

   private CommandManager commandManager;
   private boolean useAdminManager = true;
   private boolean firstUseAdminManager = true; // to allow caching
   private MomClientGateway momClientGateway = null;

   private MsgFileDumper msgFileDumper;

   private PluginHolder pluginHolder;

   private MsgDistributorPluginManager msgDistributorPluginManager;

   private SubjectEntryShuffler subjectEntryShuffler;

   public void finalize() {
      if (log.TRACE) log.trace(ME, "Entering finalize");
      shutdown();
   }

   public void shutdown() {
      super.shutdown();
      if (log.TRACE) log.trace(ME, "Destroying engine.Global handle");

      if (sessionTimer != null) {
         sessionTimer.shutdown();
         sessionTimer = null;
      }
      if (topicTimer != null) {
         topicTimer.shutdown();
         topicTimer = null;
      }
      removeTelnetSessionTimer();
   }

    public Global() {
       this(null, true);
    }

   /**
    * One instance of this represents one xmlBlaster server.
    * @param args Environment arguments (key/value pairs)
    */
   public Global(String[] args) {
      init(args);
      addObjectEntry("ServerNodeScope", this); // registers itself in util.Global
   }

   public Global(Properties p, boolean loadPropFile) {
      super(Property.propsToArgs(p), loadPropFile, false);
      initThis();
      addObjectEntry("ServerNodeScope", this); // registers itself in util.Global
      // The util.Global base class can't initiliaze it, as this class is initialized later and overwrites with null
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
      if (myId == null && getBootstrapAddress().getRawAddress() != null && getBootstrapAddress().getRawAddress().length() > 0) {
         myId = getStrippedString(getBootstrapAddress().getRawAddress());
      }
      if (myId == null && getBootstrapAddress().getBootstrapPort() > 0) {
         myId = getStrippedString(getBootstrapAddress().getBootstrapHostname() + ":" + getBootstrapAddress().getBootstrapPort());
      }
      if (myId == null) {
         if (useCluster()) {
            log.error(ME, "Can't determine server instance name, try to add '-cluster.node.id <aUniqueId>' on startup.");
            System.exit(1);
         }
         else {
            myId = "xmlBlaster";  // fallback
         }
      }
      if (myId != null) {
         setId(myId);
      }
      getRunlevelManager().addRunlevelListener(this);
      log.info(ME, "Setting xmlBlaster instance name (-cluster.node.id) to '" + getId() + "'");
   }

   /**
    * engin.Global returns true, util.Global returns false
    * @return true
    */
   public boolean isServerSide() {
      return true;
   }

   /**
    * The unique name of this xmlBlaster server instance.
    * @return Can be null during startup
    */
   public final NodeId getNodeId() {
      return this.nodeId;
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
         Thread.dumpStack();
         throw new XmlBlasterException(this, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Internal problem: please intialize ClusterManager first - Please ask on mailing list for support");
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
    * Initialize cb protocol manager. 
    * <p>To administer CORBA/RMI etc. plugins</p>
    */
   public final CbProtocolManager getCbProtocolManager() throws XmlBlasterException {
      if (this.cbProtocolManager == null) {
         synchronized(this) {
            if (this.cbProtocolManager == null)
               this.cbProtocolManager = new CbProtocolManager(this);
         }
      }
      return this.cbProtocolManager;
   }

   public final StoragePluginManager getStoragePluginManager() {
      if (topicStorePluginManager == null) {
         synchronized (StoragePluginManager.class) {
            if (topicStorePluginManager == null)
               topicStorePluginManager = new StoragePluginManager(this);
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

   /*
    * Sets the unique node id of this xmlBlaster server instance (needed for clustering).
    * <p />
    * The new node ID is only set if my current instance is null!
    * <p />
    * See private org.xmlBlaster.Main#createNodeId()
    */
/*
   public void setUniqueNodeIdName(String uniqueNodeIdName) {
      if (this.nodeId == null && uniqueNodeIdName != null) {
         this.nodeId = new NodeId(uniqueNodeIdName);
         super.id = this.nodeId.getId();
         this.contextNode = new ContextNode(this, ContextNode.CLUSTER_MARKER_TAG, getStrippedId(), (ContextNode)null);
         log.info(ME, "Setting xmlBlaster instance name to '" + this.nodeId.toString() + "'");
         getRunlevelManager().setId(this.nodeId.getId());
         Thread.currentThread().dumpStack();
      }
   }
*/
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
    * Access the handle of the TopicHandler timer thread.
    * @return The Timeout instance
    */
   public final Timeout getTelnetSessionTimer() {
      if (this.telnetSessionTimer == null) {
         synchronized(this) {
            if (this.telnetSessionTimer == null)
               this.telnetSessionTimer = new Timeout("XmlBlaster.TelnetSessionTimer");
         }
      }
      return this.telnetSessionTimer;
   }

   public final boolean hasTelnetSessionTimer() {
      return this.telnetSessionTimer != null;
   }

   public final void removeTelnetSessionTimer() {
      if (this.telnetSessionTimer != null) {
         synchronized(this) {
            if (this.telnetSessionTimer != null) {
              this.telnetSessionTimer.shutdown();
              this.telnetSessionTimer = null;
            }
         }
      }
   }

   /**
    * The factory creating queue or msgUnitStore entries from persistent store.
    * Is derived from util.Global
    */
   public I_EntryFactory getEntryFactory() {
      if (this.entryFactory != null) return this.entryFactory;
      synchronized(this) {
         this.entryFactory = new ServerEntryFactory();
         this.entryFactory.initialize(this);
         return this.entryFactory;
      }
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
         Thread.dumpStack();
         throw new XmlBlasterException(this, ErrorCode.INTERNAL_UNKNOWN, ME, "please intialize CommandManager first - Please ask on mailing list for support");
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
         Thread.dumpStack();
         return false;
      }
      return xmlKey.isAdministrative();
   }

   /**
    * Returns the callback layer implementation 'CbDispatchConnectionsHandler' on server side.
    * In util.Global we return the client side implementation 'ClientDispatchConnectionsHandler'
    * @return A new instance of CbDispatchConnectionsHandler
    */
   public DispatchConnectionsHandler createDispatchConnectionsHandler(DispatchManager dispatchManager) throws XmlBlasterException {
      return new CbDispatchConnectionsHandler(this, dispatchManager);
   }

   /**
    * Sets the authentication in the engine.Global scope.
    * <p>
    * Additionally the I_Authentication is registered in the <i>util.Global.addObjectEntry</i>
    * under the name <i>"/xmlBlaster/I_Authenticate"</i> (see Constants.I_AUTHENTICATE_PROPERTY_KEY).<br />
    * This allows lookup similar to a naming service if we are in the same JVM.
    */
   public void setAuthenticate(I_Authenticate auth) {
      this.authenticate = auth;
      addObjectEntry(Constants.I_AUTHENTICATE_PROPERTY_KEY, this.authenticate);
   }

   public I_Authenticate getAuthenticate() {
      return this.authenticate;
   }

   public final MsgDistributorPluginManager getMsgDistributorPluginManager() {
      if (this.msgDistributorPluginManager == null) {
         synchronized (MsgDistributorPluginManager.class) {
            if (this.msgDistributorPluginManager == null)
               this.msgDistributorPluginManager = new MsgDistributorPluginManager(this);
         }
      }
      return this.msgDistributorPluginManager;
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
            getHttpServer(); // incarnate allow http based access (is currently only used by CORBA)
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
         if (to <= RunlevelManager.RUNLEVEL_HALTED) {
            shutdownHttpServer(); // should be an own Plugin ?
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
      sb.append(" java.vendor='").append(System.getProperty("java.vendor")).append("'");
      sb.append(" java.version='").append(System.getProperty("java.version")).append("'");
      sb.append("\n   ");
      sb.append(" os.name='").append(System.getProperty("os.name")).append("'");
      sb.append(" os.version='").append(System.getProperty("os.version")).append("'");
      sb.append("\n   ");
      sb.append(" freeMemory='").append(Runtime.getRuntime().freeMemory()).append("'");
      sb.append(" totalMemory='").append(Runtime.getRuntime().totalMemory()).append("'");
      sb.append("\n   ");
      sb.append(" dumpTimestamp='").append(org.jutils.time.TimeHelper.getDateTimeDump(0)).append("'");
      //sb.append(" ='").append(get()).append("'");
      sb.append(">");
      sb.append(getProperty().toXml());
      sb.append(offset).append(" <ThreadDump><![CDATA[");
      sb.append(org.jutils.runtime.ThreadLister.listAllThreads());
      sb.append(offset).append(" ]]></ThreadDump>");
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
    * Check where we are, on client or on server side?
    * util.Global returns false
    * @return true As we are engine.Global and running server side
    */
   public boolean isServer() {
      return true;
   }

   /**
    * gets the object holding all configuration information for the plugins (both for
    * statically loaded plugins (by the run level manager) and dynamically loaded
    * plugins (such plugins loaded on client request).
    */
   public PluginHolder getPluginHolder() throws XmlBlasterException {
      if (this.pluginHolder != null) return this.pluginHolder;
      synchronized(this) {
        if (this.pluginHolder == null) {
           PluginHolderSaxFactory factory = new PluginHolderSaxFactory(this);
           this.pluginHolder = factory.readConfigFile();
         }
         return this.pluginHolder;
      }
   }

   public SubjectEntryShuffler getSubjectInfoShuffler() {
      if (this.subjectEntryShuffler != null) return this.subjectEntryShuffler;
      synchronized(SubjectEntryShuffler.class) {
         if (this.subjectEntryShuffler == null) {
            this.subjectEntryShuffler = new SubjectEntryShuffler(this);
         }
         return this.subjectEntryShuffler;           
      }
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
      sb.append(logUsage());
      sb.append("\n");
      sb.append("  There are fine grained logging possibilities like:\n");
      sb.append("   -trace[corba]       Switch on trace mode only for IOR driver.\n");
      sb.append("   -call[cluster]      Show method calls in the cluster module.\n");
      sb.append("   -trace[mime]        Trace code in mime based filter plugins.\n");
      sb.append("    Supported is [core], [auth], [dispatch], [mime], [corba], [xmlrpc] [admin]\n");
      return sb.toString();
   }
}

