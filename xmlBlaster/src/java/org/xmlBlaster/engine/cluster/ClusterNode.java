/*------------------------------------------------------------------------------
Name:      ClusterNode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding all information about the current node.
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.engine.Global;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionProblems;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.Address;
import org.xmlBlaster.engine.helper.CallbackAddress;

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
   private final Log log;
   
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

   /**
    * Create an object holding all informations about a node
    */
   public ClusterNode(Global glob, NodeId nodeId) {
      this.glob = glob;
      this.log = glob.getLog();
      this.nodeInfo = new NodeInfo(glob, nodeId);
      this.state = new NodeStateInfo(glob);
      this.ME = "ClusterNode-" + getId();
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
    *   <li>client.failSave.retryInterval[heron] defaults to 2000L</li>
    *   <li>client.failSave.pingInterval[heron] defaults to 10 * 1000L</li>
    *   <li>client.failSave.retries[heron] defaults to -1 == forever</li>
    *   <li>client.failSave.maxInvocations[heron] defaults to 100000</li>
    *   <li>security.plugin.type[heron] defaults to "simple"</li>
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
         Log.info(ME, "Trying to connect to node '" + getId() + "' on address '" +
             addr.getAddress() + "' using protocol=" + addr.getType());

         Vector vec = new Vector();
         vec.addElement("-client.protocol");
         vec.addElement(addr.getType());
         vec.addElement("-port");           // For all protocol we may use set an alternate server port
         vec.addElement(""+addr.getPort()); // glob.getProperty().get("port["+getId()+"]", "3412");
         vec.addElement("-socket.port");
         vec.addElement(""+addr.getPort()); // glob.getProperty().get("socket.port["+getId()+"]", "7607");
         vec.addElement("-rmi.registryPort");
         vec.addElement(""+addr.getPort()); // glob.getProperty().get("rmi.registryPort["+getId()+"]", "1099");
         vec.addElement("-xmlrpc.port");
         vec.addElement(""+addr.getPort()); // glob.getProperty().get("xmlrpc.port["+getId()+"]", "8080");
         vec.addElement("-server.node.id"); // Set a nice name to which node we want to connect
         vec.addElement(getId());
         String[] args = (String[])vec.toArray(new String[0]);

         this.xmlBlasterConnection = new XmlBlasterConnection(args);
         
         // Setup fail save handling ...
         long retryInterval = glob.getProperty().get("client.failSave.retryInterval["+getId()+"]", 2000L);
         long pingInterval = glob.getProperty().get("client.failSave.pingInterval["+getId()+"]", 10 * 1000L);
         int retries = glob.getProperty().get("client.failSave.retries["+getId()+"]", -1); // -1 == forever
         int maxMessages = glob.getProperty().get("client.failSave.maxInvocations["+getId()+"]", 100000);
         log.warn(ME, "Configuration possibility for cluster connections is not coded yet cool enough.");

         this.xmlBlasterConnection.initFailSave(this, retryInterval, retries, maxMessages, pingInterval);

         String type = glob.getProperty().get("security.plugin.type["+getId()+"]", "simple");
         String version = glob.getProperty().get("security.plugin.version["+getId()+"]", "1.0");
         String name = glob.getProperty().get("name["+getId()+"]", glob.getId());
         String passwd = glob.getProperty().get("passwd["+getId()+"]", "secret");

         CallbackAddress cbProps = new CallbackAddress();
         String cbSessionId = glob.getProperty().get("security.cbSessionId["+getId()+"]", glob.getId());
         cbProps.setSessionId(cbSessionId);

         // new Global(args) !!!!!!!
         ConnectQos qos = new ConnectQos(glob, type, version, name, passwd);
         qos.setSessionTimeout(0L); // session lasts forever
         qos.clearSessions(true);   // We only login once, kill other (older) sessions of myself!

         // Login to other xmlBlaster cluster node, register for updates
         try {
            ConnectReturnQos retQos = this.xmlBlasterConnection.connect(qos, this, cbProps);
         }
         catch(XmlBlasterException e) {
            Log.warn(ME, "Connecting to " + getId() + " is currently not possible: " + e.toString());
            Log.info(ME, "The connection is in fail save mode and will queue messages until " + getId() + " is available");
         }
      }
      return xmlBlasterConnection;
   }

   /*
   public void setXmlBlasterConnection(XmlBlasterConnection xmlBlasterConnection) {
      this.xmlBlasterConnection = xmlBlasterConnection;
   }
   */

   public void resetXmlBlasterConnection() {
      if (this.xmlBlasterConnection != null) {
         this.xmlBlasterConnection.disconnect(null);
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
      this.domainInfoMap.put(domainInfo.getQuery(), domainInfo);
   }

   /**
    * Check if we have currently a functional connection to this node. 
    */
   public boolean isLoggedIn() throws XmlBlasterException {
      if (isLocalNode())
         return true;
      XmlBlasterConnection con = getXmlBlasterConnection();
      if (con != null)
         return con.isLoggedIn();
      return false;
   }

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
            Log.info(ME, "Reconnected to xmlBlaster node '" + getId() + "', sending " + xmlBlasterConnection.queueSize() + " tailback messages ...");
            xmlBlasterConnection.flushQueue();
         }
         else
            Log.info(ME, "Reconnected to " + getId() + ", no backup messages to flush");
      }
      catch (XmlBlasterException e) {
         // !!!! TODO: producing dead letters
         Log.error(ME, "Sorry, flushing of tailback messages failed, they are lost: " + e.toString());
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
      log.warn(ME, "I_ConnectionProblems: Lost connection to xmlBlaster node '" + getId() + "'");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      log.warn(ME, "Receiving unexpected update of message oid=" + updateKey.getUniqueKey() + " from xmlBlaster node '" + getId() + "'");
      return "";
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
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<clusternode id='").append(getId()).append("'>");

      sb.append(getNodeInfo().toXml(extraOffset + "   "));

      if (getDomainInfoMap() != null) {
         Iterator it = getDomainInfoMap().values().iterator();
         while (it.hasNext()) {
            NodeDomainInfo info = (NodeDomainInfo)it.next();
            sb.append(info.toXml(extraOffset + "   "));
         }
      }
      
      sb.append(getNodeStateInfo().toXml(extraOffset + "   "));
      
      sb.append(offset).append("</clusternode>");

      return sb.toString();
   }
}
