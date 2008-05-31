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
import org.xmlBlaster.authentication.plugins.htpasswd.SecurityQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.protocol.socket.CallbackSocketDriver;
import org.xmlBlaster.protocol.socket.SocketDriver;
import org.xmlBlaster.util.Global;
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
import org.xmlBlaster.util.protocol.socket.SocketExecutor;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.qos.storage.ClientQueueProperty;

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
    * The failsafe mode is switched on, you can configure it with a connect qos markup.
    * @see org.xmlBlaster.client.I_XmlBlasterAccess
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#registerConnectionListener
    */
   public synchronized I_XmlBlasterAccess getXmlBlasterAccess() throws XmlBlasterException {
      if (isLocalNode())
         return null;

      if (!isAllowed())
         return null;

      if (this.xmlBlasterConnection == null) { // Login to other cluster node ...
         
         ConnectQosData connectQosData = getNodeInfo().getConnectQosData();
         
         // Reuse in a gateway the remote cluster node login's socket for our connection
         // Only for protocol of types "socket*"
         // ConnectQosSaxFactory->ClientProperty.ATTRIBUTE_TAG
         //<address type='SOCKET' sessionId='4e56890ghdFzj0' pingInterval='10000' retries='-1' delay='10000'>
         //  <burstMode collectTime='400' maxEntries='20' maxBytes='-1' />
         //  <ptp>true</ptp>
         //  <attribute name='useRemoteLoginAsTunnel' type='boolean'>true</attribute>
         //</address>
         boolean useRemoteLoginAsTunnel = connectQosData.getAddress().getEnv("useRemoteLoginAsTunnel", false).getValue(); //"heron".equals(qos.getSessionName().getLoginName());
         if (useRemoteLoginAsTunnel) { // The cluster master tries to tunnel using the slaves connection
            final String globalKey = SocketExecutor.getGlobalKey(connectQosData.getSessionName());
            final String secretSessionId = null;
            final int pubSessionId = 1;
            // "client/avalon/session/1" (we are heron and want to re-use avalons connection)
            // Dangerous Precond: The remote cluster logs in with subjectId==his-nodeId and pubSessionId=1 
            final SessionName sessionName = new SessionName(this.remoteGlob, null, nodeInfo.getId(), pubSessionId);
            SessionInfo myRemotePartnerLogin = this.fatherGlob.getRequestBroker().getAuthenticate(secretSessionId).getSessionInfo(sessionName);
            this.remoteGlob.addObjectEntry(globalKey, "dummyPlaceHolder");
            
            if (myRemotePartnerLogin == null) {
               // Create the temporary SessionInfo until the real client arrives
               String[] args = new String[0]; //{ "-queue/connection/defaultPlugin", "RAM,1.0" };
               Global glob = this.remoteGlob.getClone(args);
               String type = "SOCKET";
               String version = "1.0";
               String rawAddress = "socket://:7607";
               boolean found = false;
               I_Driver[] drivers = this.fatherGlob.getPluginRegistry().getPluginsOfInterfaceI_Driver();//register(pluginInfo.getId(), plugin);//getProtocolPluginManager().getPlugin(type, version)
               for (int i=0; i<drivers.length; i++) {
                  if (drivers[i] instanceof SocketDriver) {
                     SocketDriver sd = (SocketDriver)drivers[i];
                     rawAddress = sd.getRawAddress();
                     type = sd.getType();
                     version = sd.getVersion();
                     found = true;
                  }
               }
               if (!found)
                  log.severe("No socket protocol driver found");
               // TODO: How to avoid configuring the password (pass a flag to Authenticate?)
               // TODO: Currently we can only configure loginName/password based credentials
               // cluster/securityService/avalon=<securityService type='htpasswd' version='1.0'><user>avalon</user><passwd>secret</passwd></securityService>
               String xml = this.fatherGlob.get("cluster/securityService/"+sessionName.getLoginName(), "", null, null);
               if ("".equals(xml)) {
                  log.severe("To bootstrap an initial session of " + sessionName.getLoginName() + " cluster slave you need to give his password like this (adjust the password and the type if necessary): " +
                        "cluster/securityService/" + sessionName.getLoginName() + "=<securityService type='htpasswd' version='1.0'><user>" + sessionName.getLoginName() + "</user><passwd>secret</passwd></securityService>");
                  return null;
               }
               SecurityQos securityQos = new SecurityQos(glob, xml);
               ConnectQos tmpQos = new ConnectQos(glob, sessionName.getRelativeName(), "");
               tmpQos.getData().setSecurityQos(securityQos);
               tmpQos.setSessionName(sessionName);
               ClientQueueProperty prop = new ClientQueueProperty(glob, null);
               prop.setType("RAM");
               Address address = new Address(glob);
               address.setDelay(40000L);
               address.setRetries(-1);
               address.setPingInterval(20000L);
               address.setType(type);
               address.setVersion(version);
               //address.addClientProperty(new ClientProperty("useRemoteLoginAsTunnel", true));
               address.addClientProperty(new ClientProperty("acceptRemoteLoginAsTunnel", true));
               address.setRawAddress(rawAddress); // Address to find ourself
               //address.addClientProperty(new ClientProperty("acceptRemoteLoginAsTunnel", "", "", ""+true));
               prop.setAddress(address);
               tmpQos.addClientQueueProperty(prop);
               CallbackAddress cbAddress = new CallbackAddress(glob);
               cbAddress.setDelay(40000L);
               cbAddress.setRetries(-1);
               cbAddress.setPingInterval(20000L);
               cbAddress.setDispatcherActive(false);
               cbAddress.setType(type);
               cbAddress.setVersion(version);
               //cbAddress.addClientProperty(new ClientProperty("useRemoteLoginAsTunnel", true));
               cbAddress.addClientProperty(new ClientProperty("acceptRemoteLoginAsTunnel", true));
               tmpQos.addCallbackAddress(cbAddress);
               tmpQos.setPersistent(true);
               log.info("Creating temporary session " + sessionName.getRelativeName() + " until real cluster node arrives");
               glob.getXmlBlasterAccess().connect(tmpQos, new I_Callback() {
                  public String update(String cbSessionId, UpdateKey updateKey,
                        byte[] content, UpdateQos updateQos)
                        throws XmlBlasterException {
                     return null;
                  }
               });
               glob.getXmlBlasterAccess().leaveServer(null);
               myRemotePartnerLogin = this.fatherGlob.getRequestBroker().getAuthenticate(secretSessionId).getSessionInfo(sessionName);
               
               if (myRemotePartnerLogin == null) {
                  log.severe("Can't create session " + sessionName.getAbsoluteName());
                  return null;
               }
            }
            
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
                            // cbDriver.callbackAddress: socket://192.168.1.20:8920
                            CallbackSocketDriver cbDriver = (CallbackSocketDriver)myRemotePartnerLogin.getAddressServer().getCallbackDriver();
                            log.info("toAlive(" + sessionName.getAbsoluteName() + ")... found existing session to back-tunnel '" + getId() + "' on address '" + myRemotePartnerLogin.getAddressServer().getRawAddress() + "' protocol=" + myRemotePartnerLogin.getAddressServer().getType() + " cbDriver-Handler " + ((cbDriver.getHandler()==null)?"null":cbDriver.getHandler().getAddressServer().getRawAddress()));
                            //log.severe("Register toAlive: CallbackSocketDriver.handler=" + cbDriver.getHandler());
                            remoteGlob.addObjectEntry(globalKey, cbDriver.getHandler());
                         }
                         else {
                            log.info("toAlive(" + sessionName.getAbsoluteName() + ")... no  CallbackSocketDriver to back-tunnel '" + getId() + "' found");
                            remoteGlob.addObjectEntry(globalKey, "dummyPlaceHolder");
                         }
                      }
                      else {
                         log.info("toAlive(" + sessionName.getAbsoluteName() + ")... no  session to back-tunnel '" + getId() + "' found");
                         remoteGlob.addObjectEntry(globalKey, "dummyPlaceHolder");
                      }
                   }
                   public void toPolling(DispatchManager dispatchManager, ConnectionStateEnum oldState) {
                      log.warning("toPolling(" + sessionName.getAbsoluteName() + ") for cluster back-tunnel ...");
                      remoteGlob.addObjectEntry(globalKey, "dummyPlaceHolder");
                   }
                   public void toDead(DispatchManager dispatchManager, ConnectionStateEnum oldState, String errorText) {
                      log.severe("toDead(" + sessionName.getAbsoluteName() + ") for cluster back-tunnel ...");
                      remoteGlob.addObjectEntry(globalKey, "dummyPlaceHolder");
                   }
                }, fireInitial);
             }
            /* done by fireInitial
            if (myRemotePartnerLogin != null && myRemotePartnerLogin.getAddressServer() != null) {
               Object obj = myRemotePartnerLogin.getAddressServer().getCallbackDriver();
               if (obj != null && obj instanceof CallbackSocketDriver) {
                  CallbackSocketDriver cbDriver = (CallbackSocketDriver)myRemotePartnerLogin.getAddressServer().getCallbackDriver();
                  this.remoteGlob.addObjectEntry(globalKey, cbDriver.getHandler());
               }
            }
            */
         }
         
         boolean acceptRemoteLoginAsTunnel = connectQosData.getAddress().getEnv("acceptRemoteLoginAsTunnel", false).getValue(); //"heron".equals(qos.getSessionName().getLoginName());
         if (acceptRemoteLoginAsTunnel) { // The cluster slave accepts publish(), subscribe() etc callbacks
            this.remoteGlob.addObjectEntry("ClusterManager[cluster]/I_Authenticate", this.fatherGlob.getAuthenticate());
            this.remoteGlob.addObjectEntry("ClusterManager[cluster]/I_XmlBlaster", this.fatherGlob.getAuthenticate().getXmlBlaster());
         }

         this.xmlBlasterConnection = this.remoteGlob.getXmlBlasterAccess();
         this.xmlBlasterConnection.setServerNodeId(getId());
         this.xmlBlasterConnection.registerConnectionListener(this);

         // fixed to be unique since 1.5.2
         boolean oldQueueNameBehavior = this.remoteGlob.getProperty().get("xmlBlaster/cluster/useLegacyClientQueueName", false);
         if (!oldQueueNameBehavior)
            this.xmlBlasterConnection.setStorageIdStr(getId()+connectQosData.getSessionName().getRelativeName());

         try {
            Address addr = connectQosData.getAddress();
            log.info("Trying to connect to node '" + getId() + "' on address '" + addr.getRawAddress() + "' using protocol=" + addr.getType());

            // TODO: Check if physical IP:PORT is identical
            if (this.fatherGlob.getClusterManager().getMyClusterNode().getId().equals(getId())) {
               log.severe("We want to connect to ourself, route to node'" + getId() + "' ignored: ConnectQos=" + connectQosData.toXml());
               return null;
            }
            if (log.isLoggable(Level.FINEST)) log.finest("Connecting to other cluster node, ConnectQos=" + connectQosData.toXml());

            ConnectQos connectQos = new ConnectQos(this.remoteGlob, connectQosData);
            if (useRemoteLoginAsTunnel) {
               // We switch off callback ping, it is not yet implemented to handle pings from remote
               // We don't need those pings as the other side is responsible to take care on the socket connection
               connectQos.getSessionCbQueueProperty().getCurrentCallbackAddress().setPingInterval(0L);
               connectQos.getAddress().setPingInterval(0L);
            }

            ConnectReturnQos retQos = this.xmlBlasterConnection.connect(connectQos, this);
            
            if (log.isLoggable(Level.FINE)) log.fine("Connected to server " + retQos.getServerInstanceId());
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
