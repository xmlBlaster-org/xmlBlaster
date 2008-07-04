/*------------------------------------------------------------------------------
Name:      ClusterManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main manager class for clustering
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.qos.EraseQosServer;
import org.xmlBlaster.engine.qos.GetQosServer;
import org.xmlBlaster.engine.qos.SubscribeQosServer;
import org.xmlBlaster.engine.qos.UnSubscribeQosServer;
import org.xmlBlaster.engine.runlevel.I_RunlevelListener;
import org.xmlBlaster.engine.runlevel.RunlevelManager;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.cluster.RouteInfo;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.QosData;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.Destination;

/**
 * The manager instance for a cluster node. 
 * <p />
 * Each xmlBlaster server instance has one instance
 * of this class to manage its behavior in the cluster. 
 * <p />
 * Note: Our own node id is available via glob.getNodeId()
 * <p />
 * See the <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/cluster.html">cluster requirement</a>
 * for a detailed description.
 * @author xmlBlaster@marcelruff.info
 * @since 0.79e
 */
public final class ClusterManager implements I_RunlevelListener, I_Plugin, ClusterManagerMBean
{
   private String ME;

   // The following 3 declarations are 'final' but the SUN JDK 1.3.1 does not like it
   private ServerScope glob;
   private static Logger log = Logger.getLogger(ClusterManager.class.getName());
   private SessionInfo sessionInfo;

   private MapMsgToMasterPluginManager mapMsgToMasterPluginManager;
   private LoadBalancerPluginManager loadBalancerPluginManager;
   private I_LoadBalancer loadBalancer;

   public String pluginLoadBalancerType;
   public String pluginLoadBalancerVersion;

   private PluginInfo pluginInfo;
   private ContextNode contextNode;
   /** My JMX registration */
   private Object mbeanHandle;

   /**
    * Map containing ClusterNode objects, the key is a 'node Id'
    * The entries are sorted to contain the local node as first entry.
    */
   private Map clusterNodeMap = new TreeMap(new NodeComparator());
   private ClusterNode[] clusterNodesCache;

   /** Info about myself */
   private ClusterNode myClusterNode;

   private boolean postInitialized = false;

   /**
    * Usually connecting on demand is enough (e.g. connecting when a message needs to be delivered). 
    * <p />
    * If you want to immediately resend tail back messages on server startup we can
    * force to establish the connections to all nodes immediately.<br />
    * The I_XmlBlasterAccess checks then for tailed back messages which where not yet delivered
    * and sends them.
    */
   private boolean lazyConnect = false;

   /**
    * If loaded by RunlevelManager. 
    */
   public ClusterManager() {
   }

   /**
    * You need to call postInit() after all drivers are loaded. 
    * Loaded by RequestBroker.java (hard coded)
    *
    * @param sessionInfo Internal handle to be used directly with RequestBroker
    *                    NOTE: We (the cluster code) are responsible for security checks
    *                    as we directly write into RequestBroker.
    */
   public ClusterManager(ServerScope glob, SessionInfo sessionInfo) {
      this.glob = glob;
      this.sessionInfo = sessionInfo;

      this.ME = "ClusterManager" + this.glob.getLogPrefixDashed();
      this.glob.getRunlevelManager().addRunlevelListener(this);
      this.glob.setUseCluster(true);
   }

   /**
    * Enforced by I_Plugin
    * @return The configured type in xmlBlaster.properties, defaults to "SOCKET"
    */
   public String getType() {
      return (this.pluginInfo == null) ? "cluster" : this.pluginInfo.getType();
   }

   /*
    * The command line key prefix
    * @return The configured type in xmlBlasterPlugins.xml, defaults to "plugin/cluster"
   public String getEnvPrefix() {
      return (addressServer != null) ? addressServer.getEnvPrefix() : "plugin/"+getType().toLowerCase();
   }
    */

   /** Enforced by I_Plugin */
   public String getVersion() {
      return (this.pluginInfo == null) ? "1.0" : this.pluginInfo.getVersion();
   }
 
   /**
    * This method is called by the PluginManager (enforced by I_Plugin).
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global globUtil, PluginInfo pluginInfo)
      throws XmlBlasterException {
      this.pluginInfo = pluginInfo;
      this.ME = "ClusterManager";
      this.glob = (org.xmlBlaster.engine.ServerScope)globUtil.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope);
      if (this.glob == null)
         throw new XmlBlasterException(globUtil, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "could not retreive the ServerNodeScope. Am I really on the server side ?");

      
      if (!this.glob.useCluster()) {
         log.info("Activating cluster is switched off with '-cluster false'");
         return;
      }
      this.sessionInfo = this.glob.getInternalSessionInfo();
      this.glob.getRunlevelManager().addRunlevelListener(this);
      this.glob.setClusterManager(this);
      
      // For JMX instanceName may not contain ","
      String vers = ("1.0".equals(getVersion())) ? "" : getVersion();
      this.contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG,
            "ClusterManager[" + getType() + vers + "]", this.glob.getContextNode());
      this.ME = this.contextNode.getRelativeName();
      this.mbeanHandle = this.glob.registerMBean(this.contextNode, this);
      
      try {
         postInit();
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "init. Could'nt initialize ClusterManager.", ex);
      }
   }
   
   /**
    * To initialize ClusterNode we need the addresses from the protocol drivers.
    */
   public void postInit() throws XmlBlasterException {
      this.pluginLoadBalancerType = this.glob.getProperty().get("cluster.loadBalancer.type", "RoundRobin");
      this.pluginLoadBalancerVersion = this.glob.getProperty().get("cluster.loadBalancer.version", "1.0");
      this.loadBalancerPluginManager = new LoadBalancerPluginManager(this.glob, this);
      loadBalancer = loadBalancerPluginManager.getPlugin(
                this.pluginLoadBalancerType, this.pluginLoadBalancerVersion); // "RoundRobin", "1.0"
      if (loadBalancer == null) {
         String tmp = "No load balancer plugin type='" + this.pluginLoadBalancerType + "' version='" + this.pluginLoadBalancerVersion + "' found, clustering switched off";
         log.severe(tmp);
         //Thread.currentThread().dumpStack();
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED,
            ME, tmp); // is caught in RequestBroker.java
      }

      this.mapMsgToMasterPluginManager = new MapMsgToMasterPluginManager(this.glob, this);

      if (this.glob.getNodeId() == null)
         log.severe("Node ID is still unknown, please set '-cluster.node.id' to a unique name.");
      else
         initMyselfClusterNode();

      // Look for environment settings to configure startup clustering
      String names = "";
      String[] env = { "cluster.node", "cluster.node.info", "cluster.node.master" };
      for (int ii=0; ii<env.length; ii++) {
         Map nodeMap = this.glob.getProperty().get(env[ii], (Map)null);
         if (nodeMap != null) {
            Iterator iter = nodeMap.keySet().iterator();
            if (log.isLoggable(Level.FINE)) log.fine("Found -" + env[ii] + " with " + nodeMap.size() + " array size, ii=" + ii);
            while (iter.hasNext()) {
               String nodeIdName = (String)iter.next();       // e.g. "heron" from "cluster.node.master[heron]=..."
               String xml = (String)nodeMap.get(nodeIdName);  // The "<clusternode>..." xml ASCII string for heron
               if (xml == null || xml.length() < 1) {
                  log.info("Ignoring environment setting -" + env[ii]);
                  continue;
               }
               if (log.isLoggable(Level.FINE)) log.fine("Parsing environment -" + env[ii] + " for node '" + nodeIdName + "' ...");
               /*NodeParser nodeParser =*/ new NodeParser(this.glob, this, xml, sessionInfo); // fills the info to ClusterManager
               log.info("Environment for node '" + nodeIdName + "' parsed.");
            }
         }
      }

      publish();

      subscribe();

      if (log.isLoggable(Level.FINEST)) log.finest(toXml());
      log.info("Initialized and ready for " + getClusterNodes().length + " cluster nodes");
      postInitialized = true;
   }

   /*
    * On xmlBlaster startup we need to wait for incoming messages until clusterManager is ready. 
    * NOTE: This should be resolved in future by the runlevel manager
    * @return false on timeout (manager was never ready)
   public boolean blockUntilReady() {
      if (this.postInitialized)
         return true;
      for (int i=0; i<2000; i++) {
         try { Thread.sleep(10L); } catch( InterruptedException ie) {}
         if (this.postInitialized)
            return true;
      }
      log.severe("Waited for " + (2000*10L) + " millis for cluster manager to be ready, giving up");
      return false;
   }
    */

   public boolean isReady() {
      return this.postInitialized;
   }

   /**
    * TODO: not implemented yet
    * You can't currently configure the cluster setup with messages, only statically
    * on startup
    */
   private void publish() {
      if (log.isLoggable(Level.FINE)) log.fine("publish() of cluster internal messages is missing");
   /*
      StringBuffer keyBuf = new StringBuffer(256);
      keyBuf.append("<key oid='").append(Constants.OID_CLUSTER_INFO).append("[").append(getId()).append("]").append("'><").append(Constants.OID_CLUSTER_INFO)("/></key>");
      String qos = pubQos.toXml());
      XmlKey xmlKey = new XmlKey(msgUnit.getXmlKey(), true);
      clone msgUnit
      retArr[ii] = publish(unsecureSessionInfo, xmlKey, msgUnit, new PublishQosServer(this.glob, msgUnit.getQos()));
   */
   }

   /**
    * TODO: not implemented yet
    * You can't currently configure the cluster setup with messages, only statically
    * on startup
    */
   private void subscribe() {
      if (log.isLoggable(Level.FINE)) log.fine("subscribe() of cluster internal messages is missing");
   }

   /**
    * Initialize ClusterNode object, containing all informations about myself. 
    */
   private void initMyselfClusterNode() throws XmlBlasterException {
      this.myClusterNode = new ClusterNode(this.glob, this.glob.getNodeId(), this.sessionInfo);
      this.addClusterNode(this.myClusterNode);
/*
      I_Driver[] drivers = glob.getProtocolManager().getPublicProtocolDrivers();
      for (int ii=0; ii<drivers.length; ii++) {
         I_Driver driver = drivers[ii];
         Address addr = new Address(glob, driver.getProtocolId(), glob.getId());
         addr.setRawAddress(driver.getRawAddress());
         this.myClusterNode.getNodeInfo().addAddress(addr);
      }
      if (drivers.length > 0) {
         if (log.isLoggable(Level.FINE)) log.trace(ME, "Setting " + drivers.length + " addresses for cluster node '" + getId() + "'");
      }
*/
      //java.util.Vector drivers = glob.getPluginRegistry().getPluginsOfGroup("protocol");
      I_Driver[] drivers = this.glob.getPluginRegistry().getPluginsOfInterfaceI_Driver();
      for (int i=0; i < drivers.length; i++) {
         I_Driver driver = drivers[i];
         String rawAddr = driver.getRawAddress();
         if (rawAddr != null) {
            Address addr = new Address(this.glob, driver.getProtocolId(), this.glob.getId());
            addr.setRawAddress(rawAddr);
            this.myClusterNode.getNodeInfo().addAddress(addr);
         }
      }
      if (drivers.length > 0) {
         if (log.isLoggable(Level.FINE)) log.fine("Setting " + drivers.length + " addresses for cluster node '" + getId() + "'");
      }
      else {
         log.severe("ClusterNode is not properly initialized, no protocol pluging - no local xmlBlaster (node=" + getId() + ") address available");
         Thread.dumpStack();
      }
   }

   /**
    * Check if supplied address would connect to our own node. 
    */
   public final boolean isLocalAddress(Address other) {
      return getMyClusterNode().getNodeInfo().contains(other);
   }

   /**
    * Return myself
    */
   public ClusterNode getMyClusterNode() {
      return this.myClusterNode;
   }

   /**
    * Access the unique cluster node id (as NodeId object). 
    */
   public final NodeId getNodeId() {
      return this.glob.getNodeId();
   }

   /**
    * Access the unique cluster node id (as a String). 
    * @return The name of this xmlBlaster instance, e.g. "heron.mycompany.com"
    */
   public final String getId() {
      return this.glob.getId();
   }

   /**
    * The plugin loader instance to map messages to their master node. 
    */
   public MapMsgToMasterPluginManager getMapMsgToMasterPluginManager() {
      return this.mapMsgToMasterPluginManager;
   }

   /**
    * @return null if no forwarding is done and we are the master of this message ourself<br />
    *         <pre>&lt;qos>&lt;state id='OK' info='QUEUED[bilbo]'/>&lt;/qos></pre> if message is
    *         tailed back because cluster node is temporary not available. The message will
    *         be flushed on reconnect.<br />
    *         Otherwise the normal publish return value of the remote cluster node
    * @exception XmlBlasterException and RuntimeExceptions are just forwarded to the caller<br />
    *         ErrorCode.USER_PTP_UNKNOWNDESTINATION if destination cluster node is not found<br />
    *         ErrorCode.RESOURCE_CLUSTER_NOTAVAILABLE is destination cluster node is known but down   
    */
   public PublishReturnQos forwardPtpPublish(SessionInfo publisherSession, MsgUnit msgUnit, Destination destination) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering forwardPtpPublish(" + msgUnit.getLogId() + ", " + destination.getDestination() + ")");

      if (destination.getDestination().getNodeId() == null)
         return null;

      // First check if a specific not local nodeId is given
      ClusterNode clusterNode = getClusterNode(destination.getDestination().getNodeId());
      if (log.isLoggable(Level.FINE)) log.fine("PtP message '" + msgUnit.getLogId() + "' destination " + destination.getDestination() +
                   " trying node " + ((clusterNode==null)?"null":clusterNode.getId()) +
                   " isNodeIdExplicitlyGiven=" + destination.getDestination().isNodeIdExplicitlyGiven());

      if (clusterNode != null && clusterNode.isLocalNode()) {
         if (destination.getDestination().isNodeIdExplicitlyGiven()) {
            if (log.isLoggable(Level.FINE)) log.fine("PtP message '" + msgUnit.getLogId() +
                         "' destination " + destination.getDestination() + " destination cluster node reached");
            return null; // handle locally
         }
      }

      if (clusterNode == null && destination.getDestination().isNodeIdExplicitlyGiven() &&
            !glob.getId().equals(destination.getDestination().getNodeIdStr())) {
         log.warning("PtP message '" + msgUnit.getLogId() +
                        "' for destination " + destination.getDestination() +
                        ": Explicitely given remote destination cluster node '"+destination.getDestination().getNodeIdStr()+"' not found");
      }
      
      if (clusterNode != null && destination.getDestination().isNodeIdExplicitlyGiven()) {
         if (log.isLoggable(Level.FINE)) log.fine("PtP message '" + msgUnit.getLogId() +
                        "' destination " + destination.getDestination() + " remote destination cluster node found");
      }
      else {
         // Ask the plugin
         NodeMasterInfo nodeMasterInfo = getConnection(publisherSession, msgUnit, destination);
         if (nodeMasterInfo == null)
            return null;
         clusterNode =  nodeMasterInfo.getClusterNode();
      }

      if (clusterNode.isLocalNode())
         return null;

      I_XmlBlasterAccess con = clusterNode.getXmlBlasterAccess();
      if (con == null) {
         String text = "Cluster node '" + destination.getDestination() + "' is known but not reachable, message '" + msgUnit.getLogId() + "' is lost";
         log.warning(text);
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CLUSTER_NOTAVAILABLE, ME, text);
      }

      if (log.isLoggable(Level.FINE)) log.fine("PtP message '" + msgUnit.getLogId() + "' destination " + destination.getDestination() +
                   " is now forwarded to node " + clusterNode.getId());

      // To be on the save side we clone the message
      return con.publish(msgUnit.getClone());
   }

   /**
    * @return null if no forwarding is done, if we are the master of this message ourself<br />
    *         <pre>&lt;qos>&lt;state id='OK' info='QUEUED[bilbo]'/>&lt;/qos></pre> if message is
    *         tailed back because cluster node is temporary not available. The message will
    *         be flushed on reconnect.<br />
    *         Otherwise the normal publish return value of the remote cluster node and the responsible
    *         NodeMasterInfo instance.  
    * @exception XmlBlasterException and RuntimeExceptions are just forwarded to the caller
    */
   public PublishRetQosWrapper forwardPublish(SessionInfo publisherSession, MsgUnit msgUnit) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering forwardPublish(" + msgUnit.getLogId() + ")");
      NodeMasterInfo nodeMasterInfo = getConnection(publisherSession, msgUnit);
      if (nodeMasterInfo == null)
         return null;
      I_XmlBlasterAccess con =  nodeMasterInfo.getClusterNode().getXmlBlasterAccess();
      if (con == null)
         return null;

      QosData publishQos = msgUnit.getQosData();
      if (nodeMasterInfo.isDirtyRead() == true) {
         // mark QoS of published message that we dirty read the message:
         RouteInfo[] ris = publishQos.getRouteNodes();
         if (ris == null || ris.length < 1) {
            log.severe("The route info for '" + msgUnit.getLogId() + "' is missing");
            Thread.dumpStack();
         }
         else {
            ris[ris.length-1].setDirtyRead(true);
         }
      }
      // Set the new qos ...
      MsgUnit msgUnitShallowClone = new MsgUnit(msgUnit, null, null, publishQos);

      return new PublishRetQosWrapper(nodeMasterInfo, con.publish(msgUnitShallowClone));
   }

   /**
    * @return null if no forwarding is done, if we are the master of this message ourself<br />
    *         <pre>&lt;qos>&lt;state id='OK' info='QUEUED[bilbo]'/>&lt;/qos></pre> if message is
    *         tailed back because cluster node is temporary not available. The message will
    *         be flushed on reconnect.<br />
    *         Otherwise the normal subscribe return value of the remote cluster node.  
    * @exception XmlBlasterException and RuntimeExceptions are just forwarded to the caller
    */
   public SubscribeReturnQos forwardSubscribe(SessionInfo publisherSession, QueryKeyData xmlKey, SubscribeQosServer subscribeQos) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering forwardSubscribe(" + xmlKey.getOid() + ")");

      MsgUnit msgUnit = new MsgUnit(xmlKey, (byte[])null, subscribeQos.getData());
      NodeMasterInfo nodeMasterInfo = getConnection(publisherSession, msgUnit);
      if (nodeMasterInfo == null)
         return null;
      I_XmlBlasterAccess con =  nodeMasterInfo.getClusterNode().getXmlBlasterAccess();
      if (con == null) {
         if (log.isLoggable(Level.FINE)) log.fine("forwardSubscribe - Nothing to forward");
         return null;
      }

      SubscribeQos subscribeQos2 = new SubscribeQos(this.glob, subscribeQos.getData());
      // The cluster master needs to accept our "__subId:heron-3456646466"
      
      ClientProperty clientProperty = subscribeQos2.getClientProperty(Constants.PERSISTENCE_ID);
      if (clientProperty != null) {
         // remove marker that this is from persistent store, the other node would react wrong
         subscribeQos2 = new SubscribeQos(this.glob, (QueryQosData)subscribeQos.getData().clone());
         subscribeQos2.getData().getClientProperties().remove(Constants.PERSISTENCE_ID);
      }
      
      // As we forward many subscribes probably accessing the
      // same message but only want one update.
      // We cache this update and distribute to all our clients
      // TODO: As an unSubscribe() deletes all subscribes() at once
      //       we have not yet activated the new desired use of multiSubscribe
      //       We need to add some sort of subscribe reference counting
      //       preferably in the server implementation (see RequestBroker.java)
      // TODO: As soon we have implemented it here we need to remove 
      //       data.setDuplicateUpdates(false); in NodeInfo.java

      //subscribeQos2.setMultiSubscribe(false);

      return con.subscribe(new SubscribeKey(this.glob, xmlKey), subscribeQos2);
   }

   /**
    * @return null if no forwarding is done, if we are the master of this message ourself<br />
    *         <pre>&lt;qos>&lt;state id='OK' info='QUEUED[bilbo]'/>&lt;/qos></pre> if message is
    *         tailed back because cluster node is temporary not available. The message will
    *         be flushed on reconnect.<br />
    *         Otherwise the normal unSubscribe return value of the remote cluster node.  
    * @exception XmlBlasterException and RuntimeExceptions are just forwarded to the caller
    */
   public UnSubscribeReturnQos[] forwardUnSubscribe(SessionInfo publisherSession, QueryKeyData xmlKey, UnSubscribeQosServer unSubscribeQos) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering forwardUnSubscribe(" + xmlKey.getOid() + ")");

      MsgUnit msgUnit = new MsgUnit(xmlKey, (byte[])null, unSubscribeQos.getData());
      NodeMasterInfo nodeMasterInfo = getConnection(publisherSession, msgUnit);
      if (nodeMasterInfo == null)
         return null;
      I_XmlBlasterAccess con =  nodeMasterInfo.getClusterNode().getXmlBlasterAccess();
      if (con == null) {
         if (log.isLoggable(Level.FINE)) log.fine("forwardUnSubscribe - Nothing to forward");
         return null;
      }

      return con.unSubscribe(new UnSubscribeKey(this.glob, xmlKey), new UnSubscribeQos(this.glob, unSubscribeQos.getData()));
   }

   /**
    * @return null if no forwarding is done, if we are the master of this message ourself<br />
    *         msgUnit.length==0 if message is
    *         tailed back because cluster node is temporary not available. The command will
    *         be flushed on reconnect.<br />
    *         Otherwise the normal get return value of the remote cluster node.  
    * @exception XmlBlasterException and RuntimeExceptions are just forwarded to the caller
    */
   public MsgUnit[] forwardGet(SessionInfo publisherSession, QueryKeyData xmlKey, GetQosServer getQos) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering forwardGet(" + xmlKey.getOid() + ")");

      MsgUnit msgUnit = new MsgUnit(xmlKey, new byte[0], getQos.getData());
      NodeMasterInfo nodeMasterInfo = getConnection(publisherSession, msgUnit);
      if (nodeMasterInfo == null)
         return null;
      I_XmlBlasterAccess con =  nodeMasterInfo.getClusterNode().getXmlBlasterAccess();
      if (con == null) {
         if (log.isLoggable(Level.FINE)) log.fine("forwardGet - Nothing to forward");
         return null;
      }

      return con.get(new GetKey(glob, xmlKey), new GetQos(glob, getQos.getData()));
   }

   /**
    * @return null if no forwarding is done, if we are the master of this message ourself<br />
    *         <pre>&lt;qos>&lt;state id='OK' info='QUEUED[bilbo]/>&lt;/qos></pre> if message is
    *         tailed back because cluster node is temporary not available. The command will
    *         be flushed on reconnect.<br />
    *         Otherwise the normal erase return value of the remote cluster node.  
    * @exception XmlBlasterException and RuntimeExceptions are just forwarded to the caller
    */
   public EraseReturnQos[] forwardErase(SessionInfo publisherSession, QueryKeyData xmlKey, EraseQosServer eraseQos) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering forwardErase(" + xmlKey.getOid() + ")");

      MsgUnit msgUnit = new MsgUnit(xmlKey, new byte[0], eraseQos.getData());
      NodeMasterInfo nodeMasterInfo = getConnection(publisherSession, msgUnit);
      if (nodeMasterInfo == null)
         return null;
      I_XmlBlasterAccess con =  nodeMasterInfo.getClusterNode().getXmlBlasterAccess();
      if (con == null) {
         if (log.isLoggable(Level.FINE)) log.fine("forwardErase - Nothing to forward");
         return null;
      }

      return con.erase(new EraseKey(glob, xmlKey), new EraseQos(glob, eraseQos.getData()));
   }

   /**
    * Add a new node info object or overwrite an existing one. 
    * @param The ClusterNode instance
    * @exception  IllegalArgumentException
    */
   public final void addClusterNode(ClusterNode clusterNode) {
      if (clusterNode == null || clusterNode.getNodeId() == null) {
         Thread.dumpStack();
         log.severe("Illegal argument in addClusterNode()");
         throw new IllegalArgumentException("Illegal argument in addClusterNode()");
      }
      synchronized (this.clusterNodeMap) {
         this.clusterNodesCache = null; // reset cache
         this.clusterNodeMap.put(clusterNode.getId(), clusterNode);
      }
   }

   public final void removeClusterNode(ClusterNode clusterNode) {
      if (clusterNode == null || clusterNode.getNodeId() == null) {
         Thread.dumpStack();
         log.severe("Illegal argument in addClusterNode()");
         throw new IllegalArgumentException("Illegal argument in removeClusterNode()");
      }
      synchronized (this.clusterNodeMap) {
         this.clusterNodesCache = null; // reset cache
         this.clusterNodeMap.remove(clusterNode.getId());
      }
   }

   /**
    * Return array containing all known cluster nodes. 
    * @return ClusterNode[] which is a snapshot copy of our map, is never null
    */
   public ClusterNode[] getClusterNodes() {
      if (this.clusterNodesCache == null) {
         synchronized (this.clusterNodeMap) {
            if (this.clusterNodeMap == null) {
               this.clusterNodesCache = new ClusterNode[0];
            }
            else {
               this.clusterNodesCache = (ClusterNode[])this.clusterNodeMap.values().toArray(new ClusterNode[this.clusterNodeMap.size()]);
            }
         }
      }
      return this.clusterNodesCache;
   }

   public int getNumNodes() {
      synchronized (this.clusterNodeMap) {
         if (this.clusterNodeMap == null) return 1; // The caller is a single node
         return this.clusterNodeMap.size();
      }
   }

   /**
    * Access a list of known cluster nodes e.g. "heron,avalon,bilbo,frodo"
    * @return If cluster is switched off just our node
    */
   public final String getNodeList() {
      int numNodes = getNumNodes();
      if (numNodes <= 1)
         return glob.getId();
      StringBuffer sb = new StringBuffer(numNodes * 30);
      ClusterNode[] clusterNodes = getClusterNodes();
      for(int i=0; i<clusterNodes.length; i++) {
         if (sb.length() > 0)
            sb.append(",");
         sb.append(clusterNodes[i].getId());
      }
      return sb.toString();
   }

   /**
    * Access a list of known cluster nodes e.g. "heron","avalon","bilbo","frodo"
    * @return If cluster is switched off just our node
    */
   public final String[] getNodes() {
      ClusterNode[] clusterNodes = getClusterNodes();
      if (clusterNodes == null || clusterNodes.length == 0) {
         return new String[0];
      }
      String[] nodes = new String[clusterNodes.length];
      for(int i=0; i<clusterNodes.length; i++) {
         nodes[i] = clusterNodes[i].getId();
      }
      return nodes;
   }
   
   public String addClusterNode(String xml) {
      try {
         // fills the info to ClusterManager
         new NodeParser(this.glob, this, xml, this.sessionInfo);
         String msg = "New cluster node configuration parsed." +
                      "\nPlease also change your configuration file to have same change on xmlBlaster restart\n"  +
                      xml;
         log.info(msg);
         return msg;
      } catch (XmlBlasterException e) {
         String msg = "Parsing cluster node configuration failed\n" + e.getMessage() + xml;
         log.warning(msg);
         return msg;
      }
   }

   /**
    * Access the informations belonging to a node id
    * @return The ClusterNode instance or null if unknown
    */
   public final ClusterNode getClusterNode(NodeId nodeId) {
      return getClusterNode(nodeId.getId());
   }

   /**
    * Access the informations belonging to a node id
    * @param The cluster node id as a string
    * @return The ClusterNode instance or null if unknown
    */
   public final ClusterNode getClusterNode(String id) {
      synchronized (this.clusterNodeMap) {
         if (this.clusterNodeMap == null) return null;
         return (ClusterNode)this.clusterNodeMap.get(id);
      }
   }

   /*
   public final void addConnection(NodeId nodeId, I_XmlBlasterAccess connection) throws XmlBlasterException {
      ClusterNode info = getClusterNode(nodeId);
      if (info == null)
         throw new XmlBlasterException(ME, "Unknown node id = " + nodeId.toString() + ", can't add xmlBlasterConnection");
      info.setI_XmlBlasterAccess(connection);
   }

   public final void removeConnection(NodeId nodeId) {
      ClusterNode info = getClusterNode(nodeId);
      if (info == null) {
         log.error(ME, "Unknown node id = " + nodeId.toString() + ", can't remove xmlBlasterConnection");
         return;
      }
      info.resetI_XmlBlasterAccess();
   }
   */

   /**
    * Usually the connection is established on demand (a message wants to travel to a node). 
    * <p />
    * Here you can force to establish connections to all known cluster nodes.
    */
   private void initConnections() throws XmlBlasterException {
      ClusterNode[] clusterNodes = getClusterNodes();
      for (int i=0; i<clusterNodes.length; i++) {
         // force a connect (not allowed and local node are checked to do nothing) ...
         clusterNodes[i].getXmlBlasterAccess();    // should we check for Exception and proceed with other nodes ?
      }
   }

   public final NodeMasterInfo getConnection(SessionInfo publisherSession, MsgUnit msgUnit) throws XmlBlasterException {
      return getConnection(publisherSession, msgUnit, null);
   }

   /**
    * Get connection to the master node (or a node at a closer stratum to the master). 
    * @param publisherSession can be null
    * @param destination For PtP, else null
    * @return null if local node, otherwise access other node with <code>nodeMasterInfo.getClusterNode().getI_XmlBlasterAccess()</code>
    */
   public final NodeMasterInfo getConnection(SessionInfo publisherSession, MsgUnit msgUnit, Destination destination) throws XmlBlasterException {
      if (!postInitialized) {
         // !!! we need proper run level initialization
         if (log.isLoggable(Level.FINE)) log.fine("Entering getConnection(" + msgUnit.getLogId() + "), but clustering is not ready, handling in local node");
         return null;
      }

      if (log.isLoggable(Level.FINER)) log.finer("Entering getConnection(" + msgUnit.getLogId() + "), testing " + getClusterNodes().length + " known cluster nodes ...");

      // e.g. unSubscribe(__subId:heron-55) shall be forwarded
      if (msgUnit.getQosData().isPublish() && msgUnit.getKeyData().isInternal()) {
         // key oid can be null for XPath subscription
         // internal system messages are handled locally
         String keyOid = msgUnit.getKeyOid();
         if (keyOid.startsWith(Constants.INTERNAL_OID_CLUSTER_PREFIX))
            log.severe("Forwarding of '" + msgUnit.getLogId() + "' implementation is missing");
            // !!! TODO: forward system messages with cluster info of foreign nodes!
         return null;
      }

      // Search all other cluster nodes to find the masters of this message ...
      // NOTE: If no filters are used, the masterSet=f(msgUnit) could be cached for performance gain
      //       Cache implementation is currently missing

      Set masterSet = new TreeSet(); // Contains the NodeMasterInfo objects which match this message
                                     // Sorted by stratum (0 is the first entry) -> see NodeMasterInfo.compareTo
      int numRulesFound = 0;                             // For nicer logging of warnings

      QosData publishQos = msgUnit.getQosData();
      if (publishQos.count(glob.getNodeId()) > 1) { // Checked in RequestBroker as well with warning
         log.warning("Warning, message '" + msgUnit.getLogId() +
            "' passed my node id='" + glob.getId() + "' before, we have a circular routing problem, keeping message locally");
         return null;
      }

      ClusterNode[] clusterNodes = getClusterNodes();
      for (int ic=0; ic<clusterNodes.length; ic++) {
         ClusterNode clusterNode = clusterNodes[ic];
         NodeMasterInfo[] nodeMasterInfos = clusterNode.getNodeMasterInfos();

         if (nodeMasterInfos.length < 1)
            continue;
         if (clusterNode.isAllowed() == false) {
            if (log.isLoggable(Level.FINE)) log.fine("Ignoring master node id='" + clusterNode.getId() + "' because it is not available");
            continue;
         }
         if (!clusterNode.isLocalNode() && publishQos.count(clusterNode.getNodeId()) > 0) {
            if (log.isLoggable(Level.FINE)) log.fine("Ignoring node id='" + clusterNode.getId() + "' for routing, message '" +
                            msgUnit.getLogId() + "' has been there already");
            continue;
         }
         if (log.isLoggable(Level.FINE)) log.fine("Testing " + nodeMasterInfos.length + " domains rules of node " +
                                  clusterNode.getId() + " for " + msgUnit.getLogId());
         numRulesFound += clusterNode.getNodeMasterInfos().length;
         // for each domain mapping rule ...
         for (int i=0; i<nodeMasterInfos.length; i++) {
            NodeMasterInfo nodeMasterInfo = (NodeMasterInfo)nodeMasterInfos[i];
            I_MapMsgToMasterId domainMapper = this.mapMsgToMasterPluginManager.getMapMsgToMasterId(
                                 nodeMasterInfo.getType(), nodeMasterInfo.getVersion(), // "DomainToMaster", "1.0"
                                 msgUnit.getContentMime(), msgUnit.getContentMimeExtended());
            if (domainMapper == null) {
               log.warning("No domain mapping plugin type='" + nodeMasterInfo.getType() + "' version='" + nodeMasterInfo.getVersion() +
                              "' found for message mime='" + msgUnit.getContentMime() + "' and '" + msgUnit.getContentMimeExtended() +
                              "' ignoring rules " + nodeMasterInfo.toXml());
               continue;
            }

            // Now invoke the plugin to find out who is the master ...
            nodeMasterInfo = domainMapper.getMasterId(nodeMasterInfo, msgUnit);
            if (nodeMasterInfo != null) {
               masterSet.add(nodeMasterInfo);
               break; // found one
            }
         }
      }

      if (masterSet.size() < 1) {
         if (numRulesFound == 0) {
            if (log.isLoggable(Level.FINE)) log.fine("Using local node for message, no master mapping rules are known.");
         }
         else {
            if (destination == null) {
               log.info("No master found for " + msgUnit.getMethodName() + " message '" + msgUnit.getLogId() + "' mime='" +
                         msgUnit.getContentMime() + "' domain='" + msgUnit.getDomain() + "', using local node.");
            }
            else {
               if (log.isLoggable(Level.FINE)) log.fine("No master found for PtP message '" + msgUnit.getLogId() + "' mime='" +
                         msgUnit.getContentMime() + "' domain='" + msgUnit.getDomain() + "', using local node.");
            
            }
         }
         return null;
      }
      if (masterSet.size() > 1) {
         if (log.isLoggable(Level.FINE)) log.fine(masterSet.size() + " masters found for message '" + msgUnit.getLogId() +
                                      "' domain='" + msgUnit.getDomain() + "'");
      }

      NodeMasterInfo nodeMasterInfo = loadBalancer.getClusterNode(masterSet); // Invoke for masterSet.size()==1 as well, the balancer may choose to ignore it

      /*
      if (nodeMasterInfo == null) {
         log.error(ME, "Message '" + msgUnit.getLogId() + "' domain='" + msgUnit.getDomain() + "'" +
                   "has no master, message is lost (implementation to handle this case is missing)!");
         return null;
      }
      */
      if (nodeMasterInfo == null || nodeMasterInfo.getClusterNode().isLocalNode()) {
         if (log.isLoggable(Level.FINE)) log.fine("Using local node '" + getMyClusterNode().getId() + "' as master for message '"
               + msgUnit.getLogId() + "' domain='" + msgUnit.getDomain() + "'");
         if (log.isLoggable(Level.FINEST)) log.finest("Received message at master node: " + msgUnit.toXml());
         return null;
      }
      else {
         if (log.isLoggable(Level.FINE)) log.fine("Using master node '" + nodeMasterInfo.getClusterNode().getId() + "' for message '"
               + msgUnit.getLogId() + "' domain='" + msgUnit.getDomain() + "'");
      }

      return nodeMasterInfo;
   }

   public final I_XmlBlasterAccess getConnection(NodeId nodeId) {
      log.severe("getConnection() is not implemented");
      return null;
      /*
      ClusterNode clusterNode = getClusterNode(nodeId);
      return (I_XmlBlasterAccess)connectionMap.get(nodeId.getId());
      */
   }

   public void shutdown() {
      synchronized (this.clusterNodeMap) {
         ClusterNode[] clusterNodes = getClusterNodes();
         for(int i=0; i<clusterNodes.length; i++) {
            clusterNodes[i].shutdown();
         }
         this.clusterNodesCache = null;
         this.clusterNodeMap.clear();  
      }
      if (this.glob != null)
         this.glob.unregisterMBean(this.mbeanHandle);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * @param extraOffset indenting of tags for nice output
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(1024);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<clusterManager>");
      ClusterNode[] clusterNodes = getClusterNodes();
      for(int i=0; i<clusterNodes.length; i++) {
         sb.append(clusterNodes[i].toXml(extraOffset + Constants.INDENT, (Properties)null));
      }
      sb.append(offset).append("</clusterManager>");

      return sb.toString();
   }

   /**
    * Sorts the cluster nodes for the clusterNodeMap
    * <ol>
    *   <li>First is the local node</li>
    *   <li>Others by node id</li>
    * </ol>
    */
   class NodeComparator implements Comparator
   {
      /**
       * We compare the cluster node id string. 
       */
      public final int compare(Object o1, Object o2) {
         String id1 = (String)o1;
         String id2 = (String)o2;
         //log.info("NodeComparator", "Compare " + id1 + " to " + id2);
         if (id1.equals(id2))
            return 0;
         if (id1.equals(glob.getId())) // id1 is local node
            return -1;
         if (id2.equals(glob.getId())) // id2 is local node
            return 1;
         return id1.compareTo(id2);
      }
   }

   /**
    * Sorts the cluster nodes for the masterSet
    * <ol>
    *   <li>First is the local node</li>
    *   <li>Others by node id</li>
    * </ol>
    */
    /*
   class MasterNodeComparator implements Comparator
   {

      public final int compare(Object o1, Object o2) {
         NodeMasterInfo id1 = (NodeMasterInfo)o1;
         NodeMasterInfo id2 = (NodeMasterInfo)o2;
         //log.info("MasterNodeComparator", "Compare " + id1 + " to " + id2);

         if (id1.equals(id2))
            return 0;
         if (id1.equals(glob.getId())) // id1 is local node
            return -1;
         if (id2.equals(glob.getId())) // id2 is local node
            return 1;
         return id1.compareTo(id2);
      }
   }  */

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

      if (this.glob.useCluster() == false)
         return;

      if (to > from) { // startup
         if (to == RunlevelManager.RUNLEVEL_STANDBY_POST) { // 4
            //if (this.pluginInfo == null) { // Old style: Instantiate hard coded by RequestBroker.java
            //   postInit(); // Assuming the protocol drivers are initialized to deliver their addresses, currently they are started at run level 3
            //}
         }
         else if (to == RunlevelManager.RUNLEVEL_RUNNING_PRE) { // 8
            // Assuming we can do a fake login (for missing cluster nodes)
            try {
               if (!lazyConnect)
                  initConnections();
            }
            catch (XmlBlasterException ex) {
               throw ex;
            }
            catch (Throwable ex) {
               throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "init. Could'nt initialize ClusterManager.", ex);
            }
         }
      }
      if (to < from) { // shutdown
         if (to == RunlevelManager.RUNLEVEL_STANDBY) {
            if (this.pluginInfo == null) { // Old style: Instantiate hard coded by RequestBroker.java
               shutdown();
            }
         }
      }
   }

   /**
    * @return A link for JMX usage
    */
   public java.lang.String getUsageUrl() {
      return ServerScope.getJavadocUrl(this.getClass().getName(), null);
   }

   /* dummy to have a copy/paste functionality in jconsole */
   public void setUsageUrl(java.lang.String url) {
   }

   /**
    * @return For JMX usage
    */
   public String usage() {
      return staticUsage();
   }

   public boolean isShutdown() {
      synchronized (this.clusterNodeMap) {
         return this.clusterNodeMap.size() == 0;
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
   public static String staticUsage()
   {
      StringBuffer sb = new StringBuffer(512);
      sb.append("Cluster support (activated in xmlBlasterPlugins.xml):\n");
      sb.append("   -cluster.node.id    A unique name for this xmlBlaster instance, e.g. 'com.myCompany.myHost'.\n");
      sb.append("                       If not specified a unique name is chosen and displayed on command line.\n");
      sb.append("   ...                 See http://www.xmlBlaster.org/xmlBlaster/doc/requirements/cluster.html\n");
      return sb.toString();
   }

   public ContextNode getContextNode() {
      return contextNode;
   }
} // class ClusterManager

