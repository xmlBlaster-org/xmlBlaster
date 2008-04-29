/*------------------------------------------------------------------------------
Name:      ClusterNode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding all information about the current node.
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.protocol.socket.CallbackSocketDriver;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBuffer;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.dispatch.I_ConnectionStatusListener;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.address.Address;

/**
 * This class holds the informations about an xmlBlaster server instance (=cluster node).
 * <p />
 * It collects the node informations from NodeInfo.java, NodeMasterInfo.java and NodeStateInfo.java
 */
public final class ClusterNode implements java.lang.Comparable, I_Callback, I_ConnectionStateListener, ClusterNodeMBean
{
   private final String ME;
   private final ServerScope fatherGlob;
   /**
    * This util global instance is used for I_XmlBlasterAccess, it
    * uses the specific settings from NodeInfo to connect to the remote node
    */
   private final org.xmlBlaster.util.Global remoteGlob;
   private static Logger log = Logger.getLogger(ClusterNode.class.getName());
   private final SessionInfo sessionInfo;

   private I_XmlBlasterAccess xmlBlasterConnection = null;
   private boolean available;

   /** Holds address and backup informations */
   private NodeConnectQos nodeInfo;

   /** Holds performance informations for load balancing */
   private NodeStateInfo state;

   /**
    * Hold mapping informations to map a message to a master node.
    * The key is the query string -> to avoid duplicate identical queries
    * The value is an instance of NodeMasterInfo
    */
   private Map/*<String, NodeMasterInfo>*/ masterInfoMap = new TreeMap();

   /** Currently always true, needs to be configurable !!! TODO */
   private boolean isAllowed = true;
   
   private ContextNode contextNode;
   /** My JMX registration */
   private Object mbeanHandle;

   /**
    * Create an object holding all informations about a node
    */
   public ClusterNode(ServerScope glob, NodeId nodeId, SessionInfo sessionInfo) throws XmlBlasterException {
      this.fatherGlob = glob;
      this.sessionInfo = sessionInfo;

      this.remoteGlob = this.fatherGlob.getClone(new String[0]);
      this.remoteGlob.setCheckpointPlugin(this.fatherGlob.getCheckpointPlugin());
      this.remoteGlob.addObjectEntry(Constants.OBJECT_ENTRY_ServerScope, this.fatherGlob.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope)); // Used e.g. by Pop3Driver
      
      this.nodeInfo = new NodeConnectQos(this.remoteGlob, nodeId);
      this.state = new NodeStateInfo(this.remoteGlob);
      this.ME = "ClusterNode" + this.remoteGlob.getLogPrefixDashed() + "-" + "/node/" + getId() + "/";

      this.contextNode = new ContextNode(ContextNode.CLUSTERCONF_MARKER_TAG,
            getId(), this.fatherGlob.getClusterManager().getContextNode());
      this.mbeanHandle = this.fatherGlob.registerMBean(this.contextNode, this);

      //!!!      addDomainInfo(new NodeMasterInfo());
   }

   /**
    * Convenience, delegates call to NodeInfo.
    * @return The unique name of the managed xmlBlaster instance e.g. "bilbo.mycompany.com"
    */
   public String getId(){
     return nodeInfo.getId();
   }

   /**
    * Convenience, delegates call to NodeInfo.
    * @return The unique name of the managed xmlBlaster instance
    */
   public NodeId getNodeId() {
     return nodeInfo.getNodeId();
   }

   /**
    * Convenience, delegates call to NodeInfo.
    * @param The unique name of the managed xmlBlaster instance
    */
   public void setNodeId(NodeId nodeId) {
      nodeInfo.setNodeId(nodeId);
   }

   /**
    * On first invocation we connect to the other xmlBlaster cluster node.
    * <p />
    * The failsafe mode is switched on, you can configure it:
    * <ul>
    *   <li>delay[heron] defaults to 4000L</li>
    *   <li>pingInterval[heron] defaults to 10 * 1000L</li>
    *   <li>retries[heron] defaults to -1 == forever</li>
    *   <li>queue/CACHE/maxEntries[heron] defaults to 100000</li>
    *   <li>Security.Client.DefaultPlugin defaults to "htpasswd,1.0"
    *   <li>name[heron] the login name defaults to our local node id</li>
    *   <li>passwd[heron] defaults to secret</li>
    * </ul>
    * @see org.xmlBlaster.client.I_XmlBlasterAccess
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#registerConnectionListener
    */
   public I_XmlBlasterAccess getXmlBlasterAccess() throws XmlBlasterException {
      if (isLocalNode())
         return null;

      if (!isAllowed())
         return null;

      if (this.xmlBlasterConnection == null) { // Login to other cluster node ...
         
         ConnectQosData qos = getNodeInfo().getConnectQosData();
         
         ////// BjoernCluster: Reuse in a gateway the remote cluster node login's socket for our connection
         // TODO: Only for protocol of types "socket*"
         //!!!!!!!!! NOT POSSIBLE, WE ALLWAYS NEED AN INSTANCE FOR CLIENT SIDE QUEUEING
         
         // ConnectQosSaxFactory->ClientProperty.ATTRIBUTE_TAG
         //<address type='SOCKET' sessionId='4e56890ghdFzj0' pingInterval='10000' retries='-1' delay='10000'>
         //  <burstMode collectTime='400' maxEntries='20' maxBytes='-1' />
         //  <ptp>true</ptp>
         //  <attribute name='useRemoteLoginAsTunnel' type='boolean'>true</attribute>
         //</address>
         boolean useRemoteLoginAsTunnel = qos.getAddress().getEnv("useRemoteLoginAsTunnel", false).getValue(); //"heron".equals(qos.getSessionName().getLoginName());
         if (useRemoteLoginAsTunnel) {
            final String secretSessionId = null;
            final SessionName sessionName = new SessionName(this.remoteGlob, "client/avalon/session/1");
            SessionInfo myRemotePartnerLogin = this.fatherGlob.getRequestBroker().getAuthenticate(secretSessionId).getSessionInfo(sessionName);
            this.remoteGlob.addObjectEntry("ClusterManager[cluster]/HandleClient", "dummyPlaceHolder");
            
            DispatchManager mgr = myRemotePartnerLogin.getDispatchManager();
            if (mgr != null) {
               boolean fireInitial = true;
                mgr.addConnectionStatusListener(new I_ConnectionStatusListener() {
                   // The !remote! node has logged in (not our client connection)
                   public void toAlive(DispatchManager dispatchManager, ConnectionStateEnum oldState) {
                      SessionInfo myRemotePartnerLogin = fatherGlob.getRequestBroker().getAuthenticate(secretSessionId).getSessionInfo(sessionName);
                      if (myRemotePartnerLogin != null && myRemotePartnerLogin.getAddressServer() != null) {
                         Object obj = myRemotePartnerLogin.getAddressServer().getCallbackDriver();
                         if (obj != null && obj instanceof CallbackSocketDriver) {
                            CallbackSocketDriver cbDriver = (CallbackSocketDriver)myRemotePartnerLogin.getAddressServer().getCallbackDriver();
                            remoteGlob.addObjectEntry("ClusterManager[cluster]/HandleClient", cbDriver.getHandler());
                         }
                         else {
                            remoteGlob.addObjectEntry("ClusterManager[cluster]/HandleClient", "dummyPlaceHolder");
                         }
                      }
                      else {
                         remoteGlob.addObjectEntry("ClusterManager[cluster]/HandleClient", "dummyPlaceHolder");
                      }
                   }
                   public void toPolling(DispatchManager dispatchManager, ConnectionStateEnum oldState) {
                      remoteGlob.addObjectEntry("ClusterManager[cluster]/HandleClient", "dummyPlaceHolder");
                   }
                   public void toDead(DispatchManager dispatchManager, ConnectionStateEnum oldState, String errorText) {
                      remoteGlob.addObjectEntry("ClusterManager[cluster]/HandleClient", "dummyPlaceHolder");
                   }
                }, fireInitial);
             }
            /* done by fireInitial
            if (myRemotePartnerLogin != null && myRemotePartnerLogin.getAddressServer() != null) {
               Object obj = myRemotePartnerLogin.getAddressServer().getCallbackDriver();
               if (obj != null && obj instanceof CallbackSocketDriver) {
                  CallbackSocketDriver cbDriver = (CallbackSocketDriver)myRemotePartnerLogin.getAddressServer().getCallbackDriver();
                  this.remoteGlob.addObjectEntry("ClusterManager[cluster]/HandleClient", cbDriver.getHandler());
               }
            }
            */
         }

         this.xmlBlasterConnection = this.remoteGlob.getXmlBlasterAccess();
         this.xmlBlasterConnection.setServerNodeId(getId());
         this.xmlBlasterConnection.registerConnectionListener(this);

         // fixed to be unique since 1.5.2
         boolean oldQueueNameBehavior = this.remoteGlob.getProperty().get("xmlBlaster/cluster/useLegacyClientQueueName", false);
         if (!oldQueueNameBehavior)
            this.xmlBlasterConnection.setStorageIdStr(getId()+qos.getSessionName().getRelativeName());

         try {
            Address addr = qos.getAddress();
            log.info("Trying to connect to node '" + getId() + "' on address '" + addr.getRawAddress() + "' using protocol=" + addr.getType());

            if (this.fatherGlob.getClusterManager().isLocalAddress(addr)) {
               log.severe("We want to connect to ourself, route to node'" + getId() + "' ignored: ConnectQos=" + qos.toXml());
               return null;
            }
            if (log.isLoggable(Level.FINEST)) log.finest("Connecting to other cluster node, ConnectQos=" + qos.toXml());

            ConnectQos connectQos = new ConnectQos(this.remoteGlob, qos);

            /*ConnectReturnQos retQos = */this.xmlBlasterConnection.connect(connectQos, this);
         }
         catch(XmlBlasterException e) {
            if (e.isInternal()) {
               log.severe("Connecting to " + getId() + " is not possible: " + e.getMessage());
            }
            else {
               log.warning("Connecting to " + getId() + " is currently not possible: " + e.toString());
            }
            log.info("The connection is in failsafe mode and will queue messages until " + getId() + " is available");
         }
      }
      return xmlBlasterConnection;
   }

   /*
   public void setI_XmlBlasterAccess(I_XmlBlasterAccess xmlBlasterConnection) {
      this.xmlBlasterConnection = xmlBlasterConnection;
   }
   */

   /**
    * @param force shutdown even if no <disconnect/> was configured
    */
   public void resetXmlBlasterAccess(boolean force) {
      if (this.xmlBlasterConnection != null) {
         if (this.xmlBlasterConnection.isConnected())
            if (force)
               this.xmlBlasterConnection.disconnect(this.getNodeInfo().getDisconnectQos());
            else if (this.getNodeInfo().getDisconnectQos() != null)
               this.xmlBlasterConnection.disconnect(this.getNodeInfo().getDisconnectQos());
            else
               this.xmlBlasterConnection.leaveServer(null);
         this.xmlBlasterConnection = null;
      }
   }

   /**
    * Access the current nodeInfo of the node.
    * This configures the connection string. 
    */
   public NodeConnectQos getNodeInfo() {
      return nodeInfo;
   }

   /**
    * Overwrite the current nodeInfo of the node.
    */
   public void setNodeInfo(NodeConnectQos nodeInfo) {
      this.nodeInfo = nodeInfo;
   }

   /**
    * Access the current state of the node, like current CPU and memory informations.
    */
   public NodeStateInfo getNodeStateInfo() {
      return state;
   }

   /**
    * Access the current state of the node, like current CPU and memory informations.
    */
   public void setNodeStateInfo(NodeStateInfo state) {
      this.state = state;
   }

   /**
    * Access the filter rules to determine the master of a message.
    * @return The map contains NodeMasterInfo objects, it is never null
    * Please treat as read only or synchronize over the map
    */
   public NodeMasterInfo[] getNodeMasterInfos() {
      synchronized (this.masterInfoMap) {
         return (NodeMasterInfo[])this.masterInfoMap.values().toArray(new NodeMasterInfo[this.masterInfoMap.size()]);
      }
   }

   public String replace(NodeMasterInfo old, String xmlNew) {
      try {
         if (old == null || xmlNew == null) return "IllegalArgument";
         
         XmlBuffer sb = new XmlBuffer(xmlNew.length() + 128);
         sb.append("<clusternode id='").appendAttributeEscaped(getId()).append("'>");
         sb.append(xmlNew);
         sb.append("</clusternode>");
         
         ClusterNode clusterNodeDummy = new ClusterNode(this.fatherGlob, getNodeId(), this.sessionInfo);
         new NodeParser(this.fatherGlob, clusterNodeDummy, sb.toString());
         NodeMasterInfo newOne = clusterNodeDummy.getNodeMasterInfos()[0];
         
         synchronized (this.masterInfoMap) {
            old.shutdown(); // removes it from domainInfoMap
            addNodeMasterInfo(newOne);
         }
   
         return "Reconfigured to " + xmlNew + "\nPlease also change your configuration file to survive xmlBlaster restart";
      }
      catch (XmlBlasterException e) {
         log.warning(e.getMessage());
         return e.getMessage();
      }
   }
   
   //public void addNodeMasterInfo(String xml) {
   //}

   /**
    * Set the filter rules to determine the master of a message.
    */
   public void addNodeMasterInfo(NodeMasterInfo domainInfo) {
      // How to avoid duplicates? key = domainInfo.getQuery() does not help because of subtags
      synchronized (this.masterInfoMap) {
         this.masterInfoMap.put(""+domainInfo.getCount(), domainInfo);
      }
   }

   public void removeNodeMasterInfo(NodeMasterInfo domainInfo) {
      synchronized (this.masterInfoMap) {
         this.masterInfoMap.remove(""+domainInfo.getCount());
      }
   }

   /**
    * Check if we have currently a functional connection to this node.
    * <p />
    * Note: A call to this check does try to login if the connection
    *       was not initialized before. This is sometimes an unwanted behavior.
    *       On the other hand, without trying to login it is difficult to
    *       determine the connection state.
    */
   public boolean isConnected() throws XmlBlasterException {
      if (isLocalNode())
         return true;
      I_XmlBlasterAccess con = getXmlBlasterAccess();
      if (con != null)
         return con.isConnected();
      return false;
   }

   /**
    * Check if we are currently polling for a connection to this node.
    * <p />
    * Note: A call to this check does try to login if the connection
    *       was not initialized before. This is sometimes an unwanted behavior.
    *       On the other hand, without trying to login it is difficult to
    *       determine the connection state.
    */
   public boolean isPolling() throws XmlBlasterException {
      if (isLocalNode())
         return false;
      I_XmlBlasterAccess con = getXmlBlasterAccess();
      if (con != null)
         return con.isPolling();
      return false;
   }

   /**
    * Check if we currently have an open connection to this node.
    * @return Always true for the local node
    */
   public boolean isAlive() throws XmlBlasterException {
      if (isLocalNode())
         return true;
      I_XmlBlasterAccess con = getXmlBlasterAccess();
      if (con != null)
         return con.isAlive();
      return false;
   }

   /**
    * Is this node usable.
    * @return true if we are logged in or are polling for the node<br />
    *         false if the node should not be used
    */
   public boolean isAllowed() {
      if (isLocalNode())
         return true;
      return isAllowed;
   }
   
   public void setAllowed(boolean allowed) {
      this.isAllowed = allowed;
   }
   
   /**
    * For JMX access. 
    * @return
    */
   public String getConnectionStateStr() {
      try {
         int state = getConnectionState();
         switch (state) {
            case 0: return "ALIVE";
            case 1: return "POLLING";
            case 2: return "DEAD";
            default: return "UNKNOWN";
         }
      }
      catch (XmlBlasterException e) {
         e.printStackTrace();
         return "UNKNOWN";
      }
   }

   /**
    * Returns the current connection state to the node.
    * @return 0 -> We are logged in<br />
    *         1 -> We are polling for this node<br />
    *         2 -> The node is not allowed to use<br />
    */
   public int getConnectionState() throws XmlBlasterException {
      if (!isConnected()) // connect() was not yet called
         return 2;

      if (isAlive())
         return 0;
      else if (isPolling())
         return 1;
      else
         return 2;
   }

   /**
    * Check if we have currently a functional connection to this node.
    */
   public boolean isLocalNode() {
      return getId().equals(this.fatherGlob.getId());
   }

   /**
    * Needed for TreeSet and MapSet, implements Comparable.
    */
   public int compareTo(Object obj)  {
      ClusterNode n = (ClusterNode)obj;
      return getId().compareTo(n.getId());
   }

   /**
    * This is the callback method invoked from I_XmlBlasterAccess
    * informing the client in an asynchronous mode if the connection was established.
    * <p />
    * This method is enforced through interface I_ConnectionStateListener
    */
   public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      this.available = true;
      if (connection.getQueue().getNumOfEntries() > 0) {
         log.info("Connected to xmlBlaster node '" + getId() + "', sending " + connection.getQueue().getNumOfEntries() + " tailback messages ...");
      }
      else {
         log.info("Connected to " + getId() + ", no backup messages to flush");
      }
   }

   /**
    * This is the callback method invoked from I_XmlBlasterAccess
    * informing the client in an asynchronous mode if the connection was lost.
    * <p />
    * This method is enforced through interface I_ConnectionStateListener
    */
   public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      this.available = false;
      log.warning("I_ConnectionStateListener: No connection to xmlBlaster node '" + getId() + "', we are polling ...");
   }

   /**
    * This is the callback method invoked from I_XmlBlasterAccess
    * informing the client in an asynchronous mode if the connection was lost.
    * <p />
    * This method is enforced through interface I_ConnectionStateListener
    */
   public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      this.available = false;
      log.severe("I_ConnectionStateListener: No connection to xmlBlaster node '" + getId() + "', state=DEAD, giving up.");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message.
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      if (isLocalNode()) {
         log.severe("Receiving unexpected update of message oid=" + updateKey.getOid() + " from xmlBlaster node '" + getId() + "' sessionId=" + cbSessionId);
         Thread.dumpStack();
      }
      else {
         if (log.isLoggable(Level.FINER)) log.finer("Receiving update of message oid=" + updateKey.getOid() + " from xmlBlaster node '" + getId() + "' sessionId=" + cbSessionId);
      }

      // Important: Do authentication of sender:
      if (!getNodeInfo().getCbSessionId().equals(cbSessionId)) {
         log.warning("The callback sessionId '" + cbSessionId + "' is invalid, no access to " + this.remoteGlob.getId());
         throw new XmlBlasterException(updateKey.getGlobal(), ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, ME,
                     "Your callback sessionId is invalid, no access to " + this.remoteGlob.getId());
      }

      // Publish messages to our RequestBroker WITHOUT ANY FURTHER SECURITY CHECKS:

      String ret = this.fatherGlob.getRequestBroker().update(sessionInfo, updateKey, content, updateQos.getData());
      if (ret == null || ret.length() < 1)
         return Constants.RET_FORWARD_ERROR;   // OK like this?
      return Constants.RET_OK;
   }
   
   /**
    * For JMX only. 
    * @return
    */
   public String destroy() {
      this.shutdown();
      log.warning("Configuration of '" + getId() + "' is destroyed. Please also change your configuration file to survive xmlBlaster restart");
      return "Configuration of '" + getId() + "' is destroyed.\nPlease also change your configuration file to survive xmlBlaster restart";
   }
   
   public void shutdown() {
      Object mbean = this.mbeanHandle;
      ServerScope serverScope = this.fatherGlob;
      if (serverScope != null && mbean != null) {
         this.mbeanHandle = null;
         serverScope.unregisterMBean(mbean);
      }
      
      NodeMasterInfo[] nodeMasterInfos = getNodeMasterInfos();
      for (int i=0; i<nodeMasterInfos.length; i++) {
         nodeMasterInfos[i].shutdown();
      }

      resetXmlBlasterAccess(false);
      
      try {
         this.fatherGlob.getClusterManager().removeClusterNode(this);
      } catch (XmlBlasterException e) {
         e.printStackTrace();
      }
   }

   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml() {
      return toXml((String)null, (Properties)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * @param extraOffset indenting of tags for nice output
    */
   public final String toXml(String extraOffset, Properties props) {
      XmlBuffer sb = new XmlBuffer(512);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<clusternode id='").appendAttributeEscaped(getId()).append("'>");

      sb.append(getNodeInfo().toXml(extraOffset + Constants.INDENT, props));

      NodeMasterInfo[] infos = getNodeMasterInfos();
      for (int i=0; i<infos.length; i++) {
         NodeMasterInfo info = infos[i];
         sb.append(info.toXml(extraOffset + Constants.INDENT, false));
      }

      sb.append(getNodeStateInfo().toXml(extraOffset + Constants.INDENT));

      sb.append(offset).append("</clusternode>");

      return sb.toString();
   }

   public boolean isAvailable() {
      return available;
   }

   /**
    * @return Returns the remoteGlob.
    */
   public org.xmlBlaster.util.Global getRemoteGlob() {
      return this.remoteGlob;
   }

   public ContextNode getContextNode() {
      return contextNode;
   }
}
