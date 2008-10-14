/*------------------------------------------------------------------------------
Name:      Global.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling global data on server side
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.extern.MomClientGateway;
import org.xmlBlaster.engine.cluster.ClusterManager;
import org.xmlBlaster.engine.dispatch.CbDispatchConnectionsHandler;
import org.xmlBlaster.engine.distributor.plugins.MsgDistributorPluginManager;
import org.xmlBlaster.engine.msgstore.StoragePluginManager;
import org.xmlBlaster.engine.persistence.MsgFileDumper;
import org.xmlBlaster.engine.queuemsg.ReferenceEntry;
import org.xmlBlaster.engine.queuemsg.ServerEntryFactory;
import org.xmlBlaster.engine.runlevel.I_RunlevelListener;
import org.xmlBlaster.engine.runlevel.PluginHolder;
import org.xmlBlaster.engine.runlevel.PluginHolderSaxFactory;
import org.xmlBlaster.engine.runlevel.RunlevelManager;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.protocol.CbProtocolManager;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.util.IsoDateParser;
import org.xmlBlaster.util.ThreadLister;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.dispatch.DispatchConnectionsHandler;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.property.Property;
import org.xmlBlaster.util.queue.I_EntryFactory;
import org.xmlBlaster.util.queue.I_Queue;


/**
 * This holds global needed data of one xmlBlaster instance.
 * <p>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public final class ServerScope extends org.xmlBlaster.util.Global implements I_RunlevelListener
{
   private static Logger log = Logger.getLogger(ServerScope.class.getName());

   private volatile RunlevelManager runlevelManager;
   private int currRunlevel = 0;

   /** the authentication service (a layer around it for security reasons) */
   private I_Authenticate authenticate;
   /** the xmlBlaster core class */
   private RequestBroker requestBroker;
   private NodeId nodeId;
   /** Unique id, even for each restart of a node */
   private volatile String instanceId;
   private volatile ClusterManager clusterManager;
   private volatile Timeout sessionTimer;
   private volatile Timeout topicTimer;
   private volatile Timeout telnetSessionTimer;

   private boolean useCluster;
   private boolean firstUseCluster = true; // to allow caching

   private volatile CbProtocolManager cbProtocolManager;

   private volatile StoragePluginManager topicStorePluginManager;

   private volatile CommandManager commandManager;
   private boolean useAdminManager = true;
   private boolean firstUseAdminManager = true; // to allow caching
   private MomClientGateway momClientGateway = null;

   private volatile MsgFileDumper msgFileDumper;

   private PluginHolder pluginHolder;

   private volatile MsgDistributorPluginManager msgDistributorPluginManager;

   private SubjectEntryShuffler subjectEntryShuffler;

   private SessionInfo internalSessionInfo;

   private TopicAccessor topicAccessor;

   public void finalize() {
      if (log.isLoggable(Level.FINE)) log.fine("Entering finalize");
      shutdown();
      //super.finalize(); as our shutdown calls super.shutdown
   }

   public void shutdown() {
      super.shutdown();
      if (log.isLoggable(Level.FINE)) log.fine("Destroying engine.Global handle");

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

    public ServerScope() {
       this(null, true);
    }

   /**
    * One instance of this represents one xmlBlaster server.
    * @param args Environment arguments (key/value pairs)
    */
   public ServerScope(String[] args) {
      super(args);
      init(args);
      addObjectEntry(Constants.OBJECT_ENTRY_ServerScope, this); // registers itself in util.Global "ServerNodeScope"
   }

   public ServerScope(Properties p, boolean loadPropFile) {
      super(Property.propsToArgs(p), loadPropFile, false);
      initThis();
      addObjectEntry(Constants.OBJECT_ENTRY_ServerScope, this); // registers itself in util.Global
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
            log.severe("Can't determine server instance name, try to add '-cluster.node.id <aUniqueId>' on startup.");
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
      log.info("Setting xmlBlaster instance name (-cluster.node.id) to '" + getId() + "'");
   }

   /**
    * Check where we are, on client or on server side?
    * util.Global returns false
    * @return true As we are engine.Global and running server side
    */
   public boolean isServerSide() {
      return true;
   }

   /**
    * The unique name of this xmlBlaster server instance.
    * @return Can be null during startup
    */
   public final ContextNode getContextNode() {
      return super.contextNode;
   }

   /**
    * Unique id of the xmlBlaster server, changes on each restart.
    * If 'node/heron' is restarted, the instanceId changes.
    * @return nodeId + timestamp, '/node/heron/instanceId/33470080380'
    */
   public String getInstanceId() {
      if (this.instanceId == null) {
         synchronized(this) {
            if (this.instanceId == null) {
               // TODO: Two mirrored /node/heron: add IP:port to instanceId?
               ContextNode node = new ContextNode("instanceId", ""+System.currentTimeMillis(),
                                      getContextNode());
               this.instanceId = node.getAbsoluteName();
               //this.instanceId = getLogPrefix() + "/instanceId/" + System.currentTimeMillis();
            }
         }
      }
      return this.instanceId;
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
      if (id == null) return;
      this.nodeId = new NodeId(id); // ContextNode should replace NodeId one day
      this.contextNode = new ContextNode(ContextNode.CLUSTER_MARKER_TAG, getStrippedId(), (ContextNode)null);
   }

   /**
    * Initialize runlevel manager used to start/stop xmlBlaster with different run levels.
    */
   public final RunlevelManager getRunlevelManager() {
      if (this.runlevelManager == null) {
         boolean initJmx = false;
         synchronized(this) {
            if (this.runlevelManager == null) {
               this.runlevelManager = new RunlevelManager(this);
               initJmx = true;
            }
         }
         if (initJmx) {
            /* To avoid dead lock do outside of sync:
             * "RMI TCP Connection(1)-127.0.0.2" daemon prio=1 tid=0x08602b10 nid=0x1ae1 waiting for monitor entry [0xa73b8000..0xa73b9040]
        at org.xmlBlaster.util.Global.initLog(Global.java:519)
        - waiting to lock <0xa8f06f40> (a org.xmlBlaster.engine.Global)
        at org.xmlBlaster.util.Global.addLogger(Global.java:624)
        at org.xmlBlaster.util.Global.getLog(Global.java:646)
        at org.xmlBlaster.util.log.XmlBlasterJdk14LoggingHandler.publish(XmlBlasterJdk14LoggingHandler.java:86)
        at java.util.logging.Logger.log(Logger.java:428)
        at java.util.logging.Logger.doLog(Logger.java:450)
        at java.util.logging.Logger.logp(Logger.java:566)
        at sun.rmi.runtime.Log$LoggerLog.log(Log.java:212)
        at sun.rmi.server.UnicastServerRef.logCall(UnicastServerRef.java:424)
        at sun.rmi.server.UnicastServerRef.oldDispatch(UnicastServerRef.java:372)
        at sun.rmi.server.UnicastServerRef.dispatch(UnicastServerRef.java:240)
        at sun.rmi.transport.Transport$1.run(Transport.java:153)
        at java.security.AccessController.doPrivileged(Native Method)
        at sun.rmi.transport.Transport.serviceCall(Transport.java:149)
        at sun.rmi.transport.tcp.TCPTransport.handleMessages(TCPTransport.java:460)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run(TCPTransport.java:701)
        at java.lang.Thread.run(Thread.java:595)

"main" prio=1 tid=0x0805f588 nid=0x1ad2 runnable [0xbfe20000..0xbfe20998]
        at java.net.SocketInputStream.socketRead0(Native Method)
        at java.net.SocketInputStream.read(SocketInputStream.java:129)
        at java.io.BufferedInputStream.fill(BufferedInputStream.java:218)
        at java.io.BufferedInputStream.read(BufferedInputStream.java:235)
        - locked <0xa8a5f1e8> (a java.io.BufferedInputStream)
        at java.io.DataInputStream.readByte(DataInputStream.java:241)
        at sun.rmi.transport.StreamRemoteCall.executeCall(StreamRemoteCall.java:189)
        at sun.rmi.server.UnicastRef.invoke(UnicastRef.java:343)
        at sun.rmi.registry.RegistryImpl_Stub.unbind(Unknown Source)
        at java.rmi.Naming.unbind(Naming.java:135)
        at org.xmlBlaster.util.admin.extern.JmxWrapper.init(JmxWrapper.java:325)
        - locked <0xa8f40a18> (a org.xmlBlaster.util.admin.extern.JmxWrapper)
        at org.xmlBlaster.util.admin.extern.JmxWrapper.<init>(JmxWrapper.java:133)
        at org.xmlBlaster.util.admin.extern.JmxWrapper.getInstance(JmxWrapper.java:117)
        - locked <0xacc982b8> (a java.lang.Class)
        at org.xmlBlaster.util.Global.getJmxWrapper(Global.java:327)
        at org.xmlBlaster.util.Global.registerMBean(Global.java:375)
        at org.xmlBlaster.engine.runlevel.RunlevelManager.<init>(RunlevelManager.java:81)
        at org.xmlBlaster.engine.Global.getRunlevelManager(Global.java:230)
        - locked <0xa8f06f40> (a org.xmlBlaster.engine.Global)
        at org.xmlBlaster.engine.Global.initThis(Global.java:156)
        at org.xmlBlaster.engine.Global.init(Global.java:132)
        at org.xmlBlaster.engine.Global.<init>(Global.java:113)
        at org.xmlBlaster.Main.<init>(Main.java:108)
        at org.xmlBlaster.Main.main(Main.java:524)

             */
            this.runlevelManager.initJmx();
         }
      }
      return this.runlevelManager;
   }

   public int getRunlevel() {
      return this.currRunlevel;
   }

   public void setUseCluster(boolean useCluster) {
      this.useCluster = useCluster;
   }

   /**
    * Implicitely sets useCluster to true
    * @param clusterManager
    */
   public void setClusterManager(ClusterManager clusterManager) {
      this.clusterManager = clusterManager;
      this.useCluster = (this.clusterManager != null);
   }

   /**
    * Since v1.1 the clusterManager is loaded via xmlBlasterPlugins.xml
    * See useCluster() to check if clustering is switched on
    * First checks if ClusterManager is loaded already and if its state is ready.
    * @return
    */
   public final boolean isClusterManagerReady() {
      ClusterManager cm = this.clusterManager;
      return (cm != null && cm.isReady());
   }

   /**
    * Access instance which manages myself in a cluster environment.
    * @return null if cluster support is switched off
    */
   public final ClusterManager getClusterManager() throws XmlBlasterException {
      if (this.clusterManager == null) {
         if (!useCluster())
            return null;
         log.severe("Internal problem: please intialize ClusterManager first");
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
         log.severe("Internal problem: please intialize CommandManager first");
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
         log.severe("Illegal null argument in isAdministrationCommand()");
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
      //if (log.isLoggable(Level.FINER)) log.call(ME, "Changing from run level=" + from + " to level=" + to + " with force=" + force);
      if (to == from)
         return;

      try {
         if (to > from) { // startup
            if (to == RunlevelManager.RUNLEVEL_STANDBY) {
               getHttpServer(); // incarnate allow http based access (is for example used for CORBA-IOR download)
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
      finally {
         this.currRunlevel = to;
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
      ByteArrayOutputStream out = new ByteArrayOutputStream(100000);
      getDump(out);
      try {
         return out.toString("UTF-8");
      } catch (UnsupportedEncodingException e) {
         return out.toString();
      }
   }

   public void getDump(OutputStream out) throws XmlBlasterException {
      try {
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
         sb.append(" dumpTimestamp='").append(IsoDateParser.getCurrentUTCTimestamp()).append("'");
         // sb.append(" ='").append(get()).append("'");
         sb.append(">");
         out.write(sb.toString().getBytes("UTF-8"));

         out.write(getProperty().toXml().getBytes("UTF-8"));
         out.write((offset + " <ThreadDump><![CDATA[").getBytes("UTF-8"));
         out.write(ThreadLister.listAllThreads().getBytes("UTF-8"));
         out.write((offset + " ]]></ThreadDump>").getBytes("UTF-8"));
         if (getAuthenticate() != null) {
            out.write(getAuthenticate().toXml().getBytes("UTF-8"));
            if (getAuthenticate().getXmlBlaster() != null) {
               out.write(getAuthenticate().getXmlBlaster().toXml().getBytes("UTF-8"));
            }
         }
         out.write((offset + "</xmlBlaster>").getBytes("UTF-8"));
      } catch (Exception e) {
         e.printStackTrace();
      }
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

   public String[] peekMessages(I_Queue queue, int numOfEntries, String label) throws XmlBlasterException {
      if (numOfEntries == 0)
         return new String[] { "Please pass number of messages to peak" };
      if (queue == null)
         return new String[] { "There is no " + label + " queue available" };
      if (queue.getNumOfEntries() < 1)
         return new String[] { "The " + label + " queue is empty" };

      java.util.ArrayList list = queue.peek(numOfEntries, -1);

      if (list.size() == 0)
         return new String[] { "Peeking messages from " + label + " queue failed, the reason is not known" };

      int maxLength = getProperty().get("xmlBlaster/peekMessages/maxLength", 5000);

      ArrayList tmpList = new ArrayList();
      for (int i=0; i<list.size(); i++) {
         ReferenceEntry entry = (ReferenceEntry)list.get(i);
         MsgUnitWrapper wrapper = entry.getMsgUnitWrapper();
         tmpList.add("<MsgUnit index='"+i+"'>");
         if (wrapper == null) {
            tmpList.add("  NOT REFERENCED");
         }
         else {
            tmpList.add("  "+wrapper.getMsgKeyData().toXml());
            String content = wrapper.getMsgUnit().getContentStr();
            if (content.length() > (maxLength+5) ) {
               content = content.substring(0, maxLength) + " ...";
            }
            tmpList.add("  <content size='"+wrapper.getMsgUnit().getContent().length+"'>"+content+"</content>");
            tmpList.add("  "+wrapper.getMsgQosData().toXml());
         }
         tmpList.add("</MsgUnit>");
      }

      return (String[])tmpList.toArray(new String[tmpList.size()]);
   }

   /**
    * Dumps given amount of messages from queue to file.
    * @param queue The queue to observe
    * @param numOfEntries Maximum number of messages to dump
    * @param path The path to dump the messages to, it is automatically created if missing.
    * @param label A nice queue name for logging/exceptions
    * @return The file names dumped, including the path
    */
   public String[] peekQueueMessagesToFile(I_Queue queue, int numOfEntries, String path, String label) throws XmlBlasterException {
      if (numOfEntries == 0)
         return new String[] { "Please pass number of messages to peak" };
      if (queue == null)
         return new String[] { "There is no " + label + " queue available" };
      if (queue.getNumOfEntries() < 1)
         return new String[] { "The " + label + " queue is empty" };

      ArrayList list = queue.peek(numOfEntries, -1);

      if (list.size() == 0)
         return new String[] { "Peeking messages from " + label + " queue failed, the reason is not known" };

      MsgFileDumper dumper = new MsgFileDumper();
      if (path == null || path.equalsIgnoreCase("String"))
         path = "";
      dumper.init(this, path);

      ArrayList tmpList = new ArrayList();
      for (int i=0; i<list.size(); i++) {
         ReferenceEntry entry = (ReferenceEntry)list.get(i);
         MsgUnitWrapper wrapper = entry.getMsgUnitWrapper();
         if (wrapper == null) {
            tmpList.add("NOT REFERENCED #" + i);
         }
         else {
            String fileName = dumper.store(wrapper);
            tmpList.add(fileName);
         }
      }

      return (String[])tmpList.toArray(new String[tmpList.size()]);
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
      //sb.append("    Bla bla\n");
      return sb.toString();
   }

   public SessionInfo getInternalSessionInfo() {
      return internalSessionInfo;
   }

   /**
    * Filled by RequestBroker.java
    * @param internalSessionInfo
    */
   public void setInternalSessionInfo(SessionInfo internalSessionInfo) {
      this.internalSessionInfo = internalSessionInfo;
   }

   /**
    * The singleton to handle all topics and its access.
    * @return never null (is filled by RequestBroker after construction)
    */
   public TopicAccessor getTopicAccessor() {
      return topicAccessor;
   }

   public void setTopicAccessor(TopicAccessor topicAccessor) {
      this.topicAccessor = topicAccessor;
   }

}

