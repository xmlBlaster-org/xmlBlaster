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
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionProblems;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.Address;
import org.xmlBlaster.engine.helper.CallbackAddress;

import java.util.Map;
import java.util.TreeMap;
import java.util.Iterator;

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
   
   /** Hold mapping informations to map a message to a master node */
   private Map domainInfoMap = new TreeMap();

   /**
    * Create an object holding all informations about a node
    */
   public ClusterNode(Global glob, NodeId nodeId) {
      this.glob = glob;
      this.log = glob.getLog();
      this.nodeInfo = new NodeInfo(glob, nodeId);
      this.state = new NodeStateInfo(glob);
      this.ME = "ClusterNode-" + getId();
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

      if (this.xmlBlasterConnection == null) { // Login to other cluster node ...
         // TODO: get the protocol, login properties, callback properties etc. from the __sys__ messages as well:

         Address addr = getNodeInfo().getAddress();
         Log.info(ME, "Trying to connect to node '" + getId() + "' on address '" +
             addr.getAddress() + "' using protocol=" + addr.getType());

         String[] args = new String[10];
         // TODO: This needs to be generic
         args[0] = "-client.protocol";
         args[1] = addr.getType();
         args[2] = "-iorPort";     // For all protocol we may use set an alternate server port
         args[3] = ""+addr.getPort(); // glob.getProperty().get("iorPort["+getId()+"]", "7609");
         args[4] = "-socket.port";
         args[5] = ""+addr.getPort(); // glob.getProperty().get("socket.port["+getId()+"]", "7607");
         args[6] = "-rmi.registryPort";
         args[7] = ""+addr.getPort(); // glob.getProperty().get("rmi.registryPort["+getId()+"]", "1099");
         args[8] = "-xmlrpc.port";
         args[9] = ""+addr.getPort(); // glob.getProperty().get("xmlrpc.port["+getId()+"]", "8080");

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

         ConnectQos qos = new ConnectQos(type, version, name, passwd);
         qos.setSessionTimeout(0L); // session lasts forever

         // Login to other xmlBlaster cluster node, register for updates
         ConnectReturnQos retQos = this.xmlBlasterConnection.connect(qos, this, cbProps);
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
      this.domainInfoMap.put(""+domainInfo.getCount(), domainInfo);
   }

   /**
    * Check if we have currently a functional connection to this node. 
    */
   public boolean isAvailable() {
      return available;
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
      log.info(ME, "I_ConnectionProblems: Reconnected to xmlBlaster node '" + getId() + "'");
      log.warn(ME, "Customized reconnect handling code is missing, but all tailed back messages will be flushed");
      // corbaConnection.resetQueue(); // discard messages (dummy)
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
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQoS)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS) {
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
