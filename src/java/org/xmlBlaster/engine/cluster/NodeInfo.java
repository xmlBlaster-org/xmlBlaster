/*------------------------------------------------------------------------------
Name:      NodeInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding information about the current node.
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.ConnectQosSaxFactory;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.cluster.NodeId;


import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * This class holds the address informations about an
 * xmlBlaster server instance (=cluster node). 
 * It is created by the NodeParser from xml markup of by
 * the ClusterManager (via ClusterNode) for our local node.
 * <pre>
 * &lt;!-- Messages of type "__sys__cluster.node.master[heron]": -->
 * &lt;connect>
 *   &lt;qos>
 *   ...
 *   &lt;/qos>
 * &lt;/connect>
 */
public final class NodeInfo
{
   private final String ME;
   /** 
    * This util global instance is used for I_XmlBlasterAccess, it
    * uses the specific settings from NodeInfo to connect to the remote node
    */
   private final org.xmlBlaster.util.Global remoteGlob;
   private static Logger log = Logger.getLogger(NodeInfo.class.getName());

   private NodeId nodeId;
   private long counter = 0L;

   private ConnectQosSaxFactory connectQosSaxFactory;
   private ConnectQosData connectQosData;

   private DisconnectQos disconnectQos;

   /** @deprecated We now use ConnectQos */
   private Address tmpAddress = null; // Helper for SAX parsing
   /** @deprecated We now use ConnectQos */
   private CallbackAddress tmpCbAddress = null; // Helper for SAX parsing

   private boolean nameService = false;

   private boolean inConnectQos = false;  // parsing inside <connect><qos> ?
   private boolean inInfo = false;
   private boolean inAddress = false; // parsing inside <address> ?
   private boolean inCallback = false; // parsing inside <callback> ?

   /** A unique created session id delivered on callback in update() method */
   private String cbSessionId = null;

   /**
    * Holds the ConnectQos of a node. 
    * @param remoteGlob The global specific to this node instance. 
    */
   public NodeInfo(Global remoteGlob, NodeId nodeId) throws XmlBlasterException {
      this.remoteGlob = remoteGlob;

      this.setNodeId(nodeId);
      this.ME = "NodeInfo." + getId();
      this.connectQosData = new ConnectQosData(this.remoteGlob, this.nodeId);
   }

   /**
    * @return The unique name of the managed xmlBlaster instance e.g. "bilbo.mycompany.com"
    */
   public String getId(){
     return nodeId.getId();
   }

   /**
    * @return The unique name of the managed xmlBlaster instance.
    */
   public NodeId getNodeId() {
     return nodeId;
   }

   /**
    * @return The connection configuration for the remote cluster node, never null
    */   
   public ConnectQosData getConnectQosData() {
      /*
      if (this.connectQosData == null) {
         synchronized (this) {
            if (this.connectQosData == null) {
               this.connectQosData = new ConnectQosData(this.remoteGlob, this.nodeId);
            }
         }
      }
      */
      return this.connectQosData;
   }

   /**
    * TODO: !!!! is this needed?
    * @param The unique name of the managed xmlBlaster instance
    */
   public void setNodeId(NodeId nodeId) {
      if (nodeId == null) throw new IllegalArgumentException("NodeInfo.setNodeId(): NodeId argument is null");
      this.nodeId = nodeId;
   }

   /**
    * The expected callback sessionId which used to authenticate the update() call
    */
   String getCbSessionId() {
      return (this.cbSessionId == null) ? "" : this.cbSessionId;
   }

   /**
    * Access the currently used address to access the node
    * @return null if not specified  !!!!!! TODO: Changed to throws IllegalArgumentException
    */
   private Address getAddress() {
      return getConnectQosData().getAddress();
   }

   /**
    * Add another address for this cluster node. 
    * <p />
    * The map is sorted with the same sequence as the given XML sequence
    */
   public void addAddress(Address address){
      // All local plugin configurations are added here if this is the local node
      getConnectQosData().addAddress(address);
   }

   /**
    * Does the given address belong to this node?
    */
   public boolean contains(Address other) {
      return getConnectQosData().contains(other);
   }

   /**
    * Add another callback address for this cluster node. 
    */
   public void addCbAddress(CallbackAddress cbAddress) {
      getConnectQosData().addCallbackAddress(cbAddress);
   }

   /**
    * Is the node acting as a preferred cluster naming service. 
    * <p />
    * NOTE: This mode is currently not supported
    */
   public boolean isNameService() {
      return nameService;
   }

   /**
    * Tag this node as a cluster naming service. 
    * <p />
    * NOTE: This mode is currently not supported
    */
   public void setNameService(boolean nameService) {
      this.nameService = nameService;
   }

   /**
    * Force some cluster specific connection settings. 
    */
   private void postInitialize() throws XmlBlasterException
   {
      ConnectQosData data = getConnectQosData();
      
      data.setClusterNode(true);
      this.remoteGlob.setBootstrapAddress(getAddress());

      // Shall we allow a configurable user name for cluster slave logins?
      // Required: To use the cluster.node.id as login name
      //           so other cluster nodes accept our subscriptionId, e.g. "__subId:heron-3456646466"
      //SessionName sessionName = new SessionName(this.getRemoteGlob(), this.remoteGlob.getId() + "/1"); // is done in setUserId already
      //data.getSessionQos().setSessionName(sessionName);
      data.setUserId(this.remoteGlob.getId() + "/1"); // the login name, e.g. "heron/1"
      // The password is from the environment -passwd or more specific -passwd[heron]
      // Or from the XML securityQos

      // Create a secret callback session id to be able to authenticate update() calls
      CallbackAddress callback = data.getCurrentCallbackAddress();
      if (callback != null) {
         if (callback.getSecretSessionId().equals(AddressBase.DEFAULT_sessionId))
            callback.setSecretSessionId(createCbSessionId());
         this.cbSessionId = callback.getSecretSessionId();
         callback.setRetries(-1);
      }
      else {
         log.severe("Internal problem: Expected a callback address setup but none was delivered");
      }

      // As we forward many subscribes probably accessing the
      // same message but only want one update.
      // We cache this update and distribute to all our clients:
      // TODO: Please change to use multiSubscribe=false from SubscribeQos
      //              as an unSubscribe() deletes all subscribes() at once
      //              we have not yet implemented the new desired use of multiSubscribe
      data.setDuplicateUpdates(false);

      data.getSessionQos().setSessionTimeout(0L); // session lasts forever
      data.getSessionQos().clearSessions(true);   // We only login once, kill other (older) sessions of myself!
   }

   /**
    * Called for SAX master start tag
    * @return true if ok, false on error
    */
   public final boolean startElement(String uri, String localName, String name, StringBuffer character, Attributes attrs) 
   {
      // log.info(ME, "startElement: name=" + name + " character='" + character.toString() + "'");

      if (name.equalsIgnoreCase(MethodName.CONNECT.getMethodName())) { // "connect"
         inConnectQos = true;
         this.connectQosSaxFactory = new ConnectQosSaxFactory(this.remoteGlob);
         this.connectQosSaxFactory.setConnectQosData(getConnectQosData());
         return true;
      }

      if (inConnectQos) {
         this.connectQosSaxFactory.startElement(uri, localName, name, character, attrs);
         return true;
      }

      //========= The rest is deprecated:

      /*
         "info" is deprecated as it only contains address specific informations
         New is "connect" -> now we can specify all connection details
      */
      if (!inConnectQos && name.equalsIgnoreCase("info")) {
         inInfo = true;
         //this.connectQosData = new ConnectQosData(this.remoteGlob, this.nodeId);
         return true;
      }

      if (inAddress) { // delegate internal tags
         if (tmpAddress == null) return false;
         tmpAddress.startElement(uri, localName, name, character, attrs);
         return true;
      }
      if (inInfo && name.equalsIgnoreCase("address")) {
         inAddress = true;
         String type = (attrs != null) ? attrs.getValue("type") : null;
         tmpAddress = new Address(this.remoteGlob, type, getId());
         tmpAddress.startElement(uri, localName, name, character, attrs);
         log.warning("Using <address> markup is deprecated, please use connectQos markup");
         return true;
      }

      if (inInfo && name.equalsIgnoreCase("callback")) {
         inCallback = true;
         tmpCbAddress = new CallbackAddress(this.remoteGlob);
         tmpCbAddress.startElement(uri, localName, name, character, attrs);
         log.warning("Using <callback> markup is deprecated, please use connectQos markup");
         return true;
      }

      return false;
   }

   /**
    * Handle SAX parsed end element
    */
   public final void endElement(String uri, String localName, String name, StringBuffer character)
      throws SAXException {

      try {
         if (inConnectQos) { // delegate to connectQosSaxFactory ...
            if (name.equalsIgnoreCase(MethodName.CONNECT.getMethodName())) { // "connect"
               inConnectQos = false;
               character.setLength(0);
               this.connectQosData = this.connectQosSaxFactory.getConnectQosData();
               this.connectQosSaxFactory = null;
               postInitialize();
            }
            else {
               this.connectQosSaxFactory.endElement(uri, localName, name, character);
            }
            return;
         }

         //======== all the rest is deprecated:

         if (inInfo && inAddress) { // delegate address internal tags
            tmpAddress.endElement(uri, localName, name, character);
            if (name.equalsIgnoreCase("address")) {
               inAddress = false;
               addAddress(tmpAddress);
            }
            return;
         }

         if (inInfo && inCallback) { // delegate address internal tags
            tmpCbAddress.endElement(uri, localName, name, character);
            if (name.equalsIgnoreCase("callback")) {
               inCallback = false;
               addCbAddress(tmpCbAddress);
            }
            return;
         }

         if (inInfo && name.equalsIgnoreCase("info")) {
            ConnectQosData data = getConnectQosData();
            if (!data.hasAddress()) {
               log.severe("Can't connect to node '" + getId() + "', address is null");
               throw new XmlBlasterException(this.remoteGlob, ErrorCode.USER_CONFIGURATION, ME,
                         "Can't connect to node '" + getId() + "', address is null");
            }
            // TODO: Change this "/1" to use a SessionName instance:
            data.setUserId(this.remoteGlob.getId() + "/1"); // the login name, e.g. "heron/1"
            // The password is from the environment -passwd or more specific -passwd[heron]
            postInitialize();
         }
      }
      catch(XmlBlasterException e) {
         throw new SAXException("Cluster node configuration parse error", e);
      }
      finally {
         character.setLength(0);
      }
      return;
   }

   /**
    * Create a more or less unique sessionId. 
    * <p />
    * see Authenticate.java createSessionId() for a discussion
    */
   private String createCbSessionId() throws XmlBlasterException {
      try {
         String ip = this.remoteGlob.getLocalIP();
         java.util.Random ran = new java.util.Random();
         StringBuffer buf = new StringBuffer(512);
         buf.append(Constants.SESSIONID_PREFIX).append(ip).append("-").append(this.remoteGlob.getId()).append("-");
         buf.append(System.currentTimeMillis()).append("-").append(ran.nextInt()).append("-").append((counter++));
         String sessionId = buf.toString();
         if (log.isLoggable(Level.FINE)) log.fine("Created callback sessionId='" + sessionId + "'");
         return sessionId;
      }
      catch (Exception e) {
         String text = "Can't generate a unique callback sessionId: " + e.toString();
         log.severe(text);
         throw new XmlBlasterException(this.remoteGlob, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, ME, text);
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
      StringBuffer sb = new StringBuffer(512);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<").append(MethodName.CONNECT.getMethodName()).append(">");
      sb.append(getConnectQosData().toXml(extraOffset + Constants.INDENT));
      sb.append(offset).append("</").append(MethodName.CONNECT.getMethodName()).append(">");
      
      DisconnectQos dis = getDisconnectQos();
      if (dis != null) {
         sb.append(offset).append("<").append(MethodName.DISCONNECT.getMethodName()).append(">");
         sb.append(dis.toXml(extraOffset + Constants.INDENT, props));
         sb.append(offset).append("</").append(MethodName.DISCONNECT.getMethodName()).append(">");
      }

      /*
      sb.append(offset).append("<info>");
      if (this.connectQosData != null) {
         AddressBase[] arr = this.connectQosData.getAddresses();
         for (int i=0; i<arr.length; i++) {
            sb.append(arr[i].toXml(extraOffset + Constants.INDENT));
         }
      }
 
      if (cbAddressMap != null && cbAddressMap.size() > 0) {
         Iterator it = cbAddressMap.values().iterator();
         while (it.hasNext()) {
            CallbackAddress info = (CallbackAddress)it.next();
            sb.append(info.toXml(extraOffset + Constants.INDENT));
         }
      }

      if (getBackupnodeMap() != null && getBackupnodeMap().size() > 0) {
         Iterator it = getBackupnodeMap().values().iterator();
         sb.append(offset).append("   <backupnode>");
         while (it.hasNext()) {
            NodeId info = (NodeId)it.next();
            sb.append(offset).append("      <clusternode id='").append(info.getId()).append("'/>");
         }
         sb.append(offset).append("   </backupnode>");
      }
      sb.append(offset).append("</info>");
      */

      return sb.toString();
   }

   /**
    * @return Returns the disconnectQos.
    */
   public DisconnectQos getDisconnectQos() {
      return this.disconnectQos;
   }

   /**
    * @param disconnectQos The disconnectQos to set.
    */
   public void setDisconnectQos(DisconnectQos disconnectQos) {
      this.disconnectQos = disconnectQos;
   }

   /**
    * @return Returns the remoteGlob.
    */
   public org.xmlBlaster.util.Global getRemoteGlob() {
      return this.remoteGlob;
   }
}
