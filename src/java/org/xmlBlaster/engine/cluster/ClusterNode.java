/*------------------------------------------------------------------------------
Name:      ClusterNode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding all information about the current node.
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.engine.ServerScope;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;

import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Iterator;

/**
 * This class holds the informations about an xmlBlaster server instance (=cluster node).
 * <p />
 * It collects the node informations from NodeInfo.java, NodeDomainInfo.java and NodeStateInfo.java
 */
public final class ClusterNode implements java.lang.Comparable, I_Callback, I_ConnectionStateListener
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
   private NodeInfo nodeInfo;

   /** Holds performance informations for load balancing */
   private NodeStateInfo state;

   /**
    * Hold mapping informations to map a message to a master node.
    * The key is the query string -> to avoid duplicate identical queries
    * The value is an instance of NodeDomainInfo
    */
   private Map domainInfoMap = new TreeMap();

   /** Currently always true, needs to be configurable !!! TODO */
   private boolean isAllowed = true;

   /**
    * Create an object holding all informations about a node
    */
   public ClusterNode(ServerScope glob, NodeId nodeId, SessionInfo sessionInfo) throws XmlBlasterException {
      this.fatherGlob = glob;
      this.sessionInfo = sessionInfo;

      this.remoteGlob = this.fatherGlob.getClone(new String[0]);
      this.remoteGlob.addObjectEntry(Constants.OBJECT_ENTRY_ServerScope, this.fatherGlob.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope)); // Used e.g. by Pop3Driver
      this.nodeInfo = new NodeInfo(this.remoteGlob, nodeId);
      this.state = new NodeStateInfo(this.remoteGlob);
      this.ME = "ClusterNode" + this.remoteGlob.getLogPrefixDashed() + "-" + "/node/" + getId() + "/";
//!!!      addDomainInfo(new NodeDomainInfo());
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

         this.xmlBlasterConnection = this.remoteGlob.getXmlBlasterAccess();
         this.xmlBlasterConnection.setServerNodeId(getId());
         this.xmlBlasterConnection.registerConnectionListener(this);

         ConnectQosData qos = getNodeInfo().getConnectQosData();

         try {
            Address addr = qos.getAddress();
            log.info("Trying to connect to node '" + getId() + "' on address '" + addr.getRawAddress() + "' using protocol=" + addr.getType());

            if (this.fatherGlob.getClusterManager().isLocalAddress(addr)) {
               log.severe("We want to connect to ourself, route to node'" + getId() + "' ignored: ConnectQos=" + qos.toXml());
               return null;
            }
            if (log.isLoggable(Level.FINEST)) log.finest("Connecting to other cluster node, ConnectQos=" + qos.toXml());

            /*ConnectReturnQos retQos = */this.xmlBlasterConnection.connect(new ConnectQos(this.remoteGlob, qos), this);
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
         this.xmlBlasterConnection = null;
      }
   }

   /**
    * Access the current nodeInfo of the node.
    */
   public NodeInfo getNodeInfo() {
      return nodeInfo;
   }

   /**
    * Overwrite the current nodeInfo of the node.
    */
   public void setNodeInfo(NodeInfo nodeInfo) {
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
    * @return The map contains NodeDomainInfo objects, it is never null, please treat as read only.
    */
   public Map getDomainInfoMap() {
      return domainInfoMap;
   }

   /**
    * Set the filter rules to determine the master of a message.
    */
   public void addDomainInfo(NodeDomainInfo domainInfo) {
      // How to avoid duplicates? key = domainInfo.getQuery() does not help because of subtags
      this.domainInfoMap.put(""+domainInfo.getCount(), domainInfo);
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
   public boolean isAllowed() throws XmlBlasterException {
      if (isLocalNode())
         return true;
      return isAllowed;
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
    * Set the connection status
    */
   public void setAvailable(boolean available) {
      this.available = available;
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

   public void shutdown() {
      resetXmlBlasterAccess(false);
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
      StringBuffer sb = new StringBuffer(512);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<clusternode id='").append(getId()).append("'>");

      sb.append(getNodeInfo().toXml(extraOffset + Constants.INDENT, props));

      if (getDomainInfoMap() != null) {
         Iterator it = getDomainInfoMap().values().iterator();
         while (it.hasNext()) {
            NodeDomainInfo info = (NodeDomainInfo)it.next();
            sb.append(info.toXml(extraOffset + Constants.INDENT));
         }
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
}
