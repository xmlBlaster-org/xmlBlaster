/*------------------------------------------------------------------------------
Name:      ClusterNode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding all information about the current node.
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.engine.Global;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionProblems;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.authentication.SessionInfo;

import java.util.Map;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.Vector;

/**
 * This class holds the informations about an xmlBlaster server instance (=cluster node). 
 * <p />
 * It collects the node informations from NodeInfo.java, NodeDomainInfo.java and NodeStateInfo.java
 */
public final class ClusterNode implements java.lang.Comparable, I_Callback, I_ConnectionProblems
{
   private final String ME;
   private final Global glob;
   /** 
    * This util global instance is used for XmlBlasterConnection, it
    * uses the specific settings from NodeInfo to connect to the remote node
    */
   private final org.xmlBlaster.util.Global connectGlob;
   private final LogChannel log;
   private final SessionInfo sessionInfo;
   
   private XmlBlasterConnection xmlBlasterConnection = null;
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

   /** A unique created session id delivered on callback in update() method */
   private String cbSessionId = null;

   private long counter = 0L;

   /**
    * Create an object holding all informations about a node
    */
   public ClusterNode(Global glob, NodeId nodeId, SessionInfo sessionInfo) {
      this.glob = glob;
      this.sessionInfo = sessionInfo;
      this.log = this.glob.getLog("cluster");
      this.nodeInfo = new NodeInfo(glob, nodeId);
      this.state = new NodeStateInfo(glob);
      this.ME = "ClusterNode" + glob.getLogPrefixDashed() + "-" + "/node/" + getId() + "/";
      this.connectGlob = glob.getClone(new String[0]);
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
    * The fail save mode is switched on, you can configure it:
    * <ul>
    *   <li>delay[heron] defaults to 4000L</li>
    *   <li>pingInterval[heron] defaults to 10 * 1000L</li>
    *   <li>retries[heron] defaults to -1 == forever</li>
    *   <li>queue.maxMsg[heron] defaults to 100000</li>
    *   <li>security.plugin.type[heron] defaults to "htpasswd"</li>
    *   <li>security.plugin.version[heron] defaults to "1.0"</li>
    *   <li>name[heron] the login name defaults to our local node id</li>
    *   <li>passwd[heron] defaults to secret</li>
    * </ul>
    * @see org.xmlBlaster.client.protocol.XmlBlasterConnection
    * @see org.xmlBlaster.client.protocol.XmlBlasterConnection#initFailSave
    */
   public XmlBlasterConnection getXmlBlasterConnection() throws XmlBlasterException {
      if (isLocalNode())
         return null;

      if (!isAllowed())
         return null;

      if (this.xmlBlasterConnection == null) { // Login to other cluster node ...
 
         // TODO: get the protocol, login properties, callback properties etc. from the __sys__ messages as well:

         Address addr = getNodeInfo().getAddress();
         if (addr == null) {
            log.error(ME, "Can't connect to node '" + getId() + "', address is null");
            throw new XmlBlasterException(ME, "Can't connect to node '" + getId() + "', address is null");
         }
         connectGlob.setBootstrapAddress(addr);

         this.xmlBlasterConnection = new XmlBlasterConnection(connectGlob);
         this.xmlBlasterConnection.setServerNodeId(getId());
         this.xmlBlasterConnection.initFailSave(this);

         CallbackAddress callback = nodeInfo.getCbAddress();
         if (callback.getSessionId().equals(AddressBase.DEFAULT_sessionId))
            callback.setSessionId(createCbSessionId());
         this.cbSessionId = callback.getSessionId();

         ConnectQosData qos = new ConnectQosData(connectGlob, glob.getNodeId());

         qos.setIsClusterNode(true);

         // As we forward many subscribes probably accessing the
         // same message but only want one update.
         // We cache this update and distribute to all our clients:
         qos.setDuplicateUpdates(false);

         qos.setUserId(connectGlob.getId()); // the login name
         // The password is from the environment -passwd or more specific -passwd[heron]

         qos.setAddress(addr);      // use the configured access properties
         qos.addCallbackAddress(callback); // we want to receive update()
         qos.getSessionQos().setSessionTimeout(0L); // session lasts forever
         qos.getSessionQos().clearSessions(true);   // We only login once, kill other (older) sessions of myself!

         try {
            log.info(ME, "Trying to connect to node '" + getId() + "' on address '" + addr.getAddress() + "' using protocol=" + addr.getType());

            if (glob.getClusterManager().isLocalAddress(addr)) {
               log.error(ME, "We want to connect to ourself, route to node'" + getId() + "' ignored: ConnectQos=" + qos.toXml());
               return null;
            }
            if (log.DUMP) log.dump(ME, "Connecting to other cluster node, ConnectQos=" + qos.toXml());

            ConnectReturnQos retQos = this.xmlBlasterConnection.connect(new ConnectQos(glob, qos), this);
         }
         catch(XmlBlasterException e) {
            log.warn(ME, "Connecting to " + getId() + " is currently not possible: " + e.toString());
            log.info(ME, "The connection is in fail save mode and will queue messages until " + getId() + " is available");
         }
      }
      return xmlBlasterConnection;
   }

   /*
   public void setXmlBlasterConnection(XmlBlasterConnection xmlBlasterConnection) {
      this.xmlBlasterConnection = xmlBlasterConnection;
   }
   */

   /**
    * @param force shutdown if if messages are pending
    */
   public void resetXmlBlasterConnection(boolean force) {
      if (this.xmlBlasterConnection != null) {
         if (this.xmlBlasterConnection.isLoggedIn())
            this.xmlBlasterConnection.disconnect(null);
         this.xmlBlasterConnection.shutdown(force);
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
   public boolean isLoggedIn() throws XmlBlasterException {
      if (isLocalNode())
         return true;
      XmlBlasterConnection con = getXmlBlasterConnection();
      if (con != null)
         return con.isLoggedIn();
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
      XmlBlasterConnection con = getXmlBlasterConnection();
      if (con != null)
         return con.isPolling();
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
      if (isLoggedIn())
         return 0;
      if (isPolling())
         return 1;
      return 2;
   }

   /**
    * Check if we have currently a functional connection to this node. 
    */
   public boolean isLocalNode() {
      return getId().equals(glob.getId());
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
    * This is the callback method invoked from XmlBlasterConnection
    * informing the client in an asynchronous mode if the connection was established.
    * <p />
    * This method is enforced through interface I_ConnectionProblems
    */
   public void reConnected() {
      available = true;
      try {
         if (xmlBlasterConnection.queueSize() > 0) {
            log.info(ME, "Connected to xmlBlaster node '" + getId() + "', sending " + xmlBlasterConnection.queueSize() + " tailback messages ...");
            xmlBlasterConnection.flushQueue();
         }
         else
            log.info(ME, "Connected to " + getId() + ", no backup messages to flush");
      }
      catch (XmlBlasterException e) {
         // !!!! TODO: producing dead letters
         log.error(ME, "Sorry, flushing of tailback messages failed, they are lost: " + e.toString());
      }
   }

   /**
    * This is the callback method invoked from XmlBlasterConnection
    * informing the client in an asynchronous mode if the connection was lost.
    * <p />
    * This method is enforced through interface I_ConnectionProblems
    */
   public void lostConnection() {
      available = false;
      log.warn(ME, "I_ConnectionProblems: No connection to xmlBlaster node '" + getId() + "'");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      if (isLocalNode()) {
         log.error(ME, "Receiving unexpected update of message oid=" + updateKey.getOid() + " from xmlBlaster node '" + getId() + "' sessionId=" + cbSessionId);
         Thread.currentThread().dumpStack();
      }
      else {
         if (log.CALL) log.call(ME, "Receiving update of message oid=" + updateKey.getOid() + " from xmlBlaster node '" + getId() + "' sessionId=" + cbSessionId);
      }

      // Important: Do authentication of sender:
      if (!this.cbSessionId.equals(cbSessionId)) {
         log.warn(ME+".AccessDenied", "The callback sessionId '" + cbSessionId + "' is invalid, no access to " + glob.getId());
         throw new XmlBlasterException("AccessDenied", "Your callback sessionId is invalid, no access to " + glob.getId());
      }

      // Publish messages to our RequestBroker WITHOUT ANY FURTHER SECURITY CHECKS:

      String ret = glob.getRequestBroker().update(sessionInfo, updateKey, content, updateQos.getData());
      if (ret == null || ret.length() < 1)
         return Constants.RET_FORWARD_ERROR;   // OK like this?
      return Constants.RET_OK;
   }

   /**
    * Create a more or less unique sessionId. 
    * <p />
    * see Authenticate.java createSessionId() for a discussion
    */
   private String createCbSessionId() throws XmlBlasterException {
      try {
         String ip = glob.getLocalIP();
         java.util.Random ran = new java.util.Random();
         StringBuffer buf = new StringBuffer(512);
         buf.append(Constants.SESSIONID_PREFIX).append(ip).append("-").append(glob.getId()).append("-");
         buf.append(System.currentTimeMillis()).append("-").append(ran.nextInt()).append("-").append((counter++));
         String sessionId = buf.toString();
         if (log.TRACE) log.trace(ME, "Created sessionId='" + sessionId + "'");
         return sessionId;
      }
      catch (Exception e) {
         String text = "Can't generate a unique sessionId: " + e.toString();
         log.error(ME, text);
         throw new XmlBlasterException("NoSessionId", text);
      }
   }

   public void shutdown(boolean force) {
      resetXmlBlasterConnection(force);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml()
   {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * @param extraOffset indenting of tags for nice output
    */
   public final String toXml(String extraOffset)
   {
      StringBuffer sb = new StringBuffer(512);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<clusternode id='").append(getId()).append("'>");

      sb.append(getNodeInfo().toXml(extraOffset + Constants.INDENT));

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
}
