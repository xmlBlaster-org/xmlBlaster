/*------------------------------------------------------------------------------
Name:      NodeInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding information about the current node.
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.cluster.NodeId;

import java.util.Map;
import java.util.TreeMap;
import java.util.Iterator;

import org.xml.sax.Attributes;

/**
 * This class holds the address informations about an
 * xmlBlaster server instance (=cluster node). 
 */
public final class NodeInfo
{
   private final String ME;
   private final Global glob;

   private NodeId nodeId;

   private Address tmpAddress = null; // Helper for SAX parsing
   private Map addressMap = null;

   private CallbackAddress tmpCbAddress = null; // Helper for SAX parsing
   private Map cbAddressMap = null;

   private Map backupnodeMap = null;

   private boolean nameService = false;

   private boolean inAddress = false; // parsing inside <address> ?
   private boolean inCallback = false; // parsing inside <callback> ?
   private boolean inBackupnode = false; // parsing inside <backupnode> ?

   /**
    * Holds the addresses of a node. 
    * @param glob The global specific to this node instance. 
    */
   public NodeInfo(Global glob, NodeId nodeId) {
      this.glob = glob;
      this.setNodeId(nodeId);
      this.ME = "NodeInfo." + getId();
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
    * @param The unique name of the managed xmlBlaster instance
    */
   public void setNodeId(NodeId nodeId) {
      if (nodeId == null) throw new IllegalArgumentException("NodeInfo.setNodeId(): NodeId argument is null");
      this.nodeId = nodeId;
   }

   /**
    * Access the currently used address to access the node
    * @return null if not specified
    */
   public Address getAddress() {
      if (addressMap == null) return null;
      return (Address)addressMap.values().iterator().next();
   }

   /**
    * Add another address for this cluster node. 
    * <p />
    * The map is sorted with the same sequence as the given XML sequence
    */
   public void addAddress(Address address){
      if (addressMap == null) addressMap = new TreeMap();
      this.addressMap.put(""+addressMap.size(), address);
   }

   /**
    * Access all addresses of a node, please handle as readonly. 
    */
   public final Map getAddressMap() {
      return addressMap;
   }

   /**
    * Does the given address belong to this node?
    */
   public boolean contains(Address other) {
      if (addressMap == null || addressMap.size() == 0)
         return false;
      Iterator it = addressMap.values().iterator();
      while (it.hasNext()) {
         Address aa = (Address)it.next();
         if (aa.isSameAddress(other))
            return true;
      }
      return false;
   }

   /**
    * Access the currently used callback address for this node
    * @return Never null, returns a default if none specified
    */
   public CallbackAddress getCbAddress() {
      if (cbAddressMap == null) {
         addCbAddress(new CallbackAddress(glob));
      }
      return (CallbackAddress)cbAddressMap.values().iterator().next();
   }

   /**
    * Currently not used. 
    */
   public Map getCbAddressMap() {
      return cbAddressMap;
   }

   /**
    * Add another callback address for this cluster node. 
    */
   public void addCbAddress(CallbackAddress cbAddress) {
      if (cbAddressMap == null) cbAddressMap = new TreeMap();
      this.cbAddressMap.put(cbAddress.getRawAddress(), cbAddress);
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
    * If this node is not accessible, we can use its backup nodes. 
    * @return a Map containing NodeId objects
    */
   public Map getBackupnodeMap() {
      return backupnodeMap;
   }

   /**
    * Set backup nodes. 
    */
   public void addBackupnode(NodeId backupId) {
      if (backupnodeMap == null) backupnodeMap = new TreeMap();
      backupnodeMap.put(backupId.getId(), backupId);
   }

   /**
    * Called for SAX master start tag
    * @return true if ok, false on error
    */
   public final boolean startElement(String uri, String localName, String name, StringBuffer character, Attributes attrs) {
      // glob.getLog("cluster").info(ME, "startElement: name=" + name + " character='" + character.toString() + "'");

      if (name.equalsIgnoreCase("info")) {
         return true;
      }

      if (inAddress) { // delegate internal tags
         if (tmpAddress == null) return false;
         tmpAddress.startElement(uri, localName, name, character, attrs);
         return true;
      }
      if (name.equalsIgnoreCase("address")) {
         inAddress = true;
         tmpAddress = new Address(glob, "", getId());
         tmpAddress.startElement(uri, localName, name, character, attrs);
         return true;
      }

      if (name.equalsIgnoreCase("callback")) {
         inCallback = true;
         tmpCbAddress = new CallbackAddress(glob);
         tmpCbAddress.startElement(uri, localName, name, character, attrs);
         return true;
      }

      if (name.equalsIgnoreCase("backupnode")) {
         inBackupnode = true;
         return true;
      }
      if (inBackupnode && name.equalsIgnoreCase("clusternode")) {
         if (attrs != null) {
            String tmp = attrs.getValue("id");
            if (tmp == null) {
               glob.getLog("cluster").error(ME, "<backupnode><clusternode> attribute 'id' is missing, ignoring message");
               throw new RuntimeException("NodeParser: <backupnode><clusternode> attribute 'id' is missing, ignoring message");
            }
            addBackupnode(new NodeId(tmp.trim()));
         }
         return true;
      }

      return false;
   }

   /**
    * Handle SAX parsed end element
    */
   public final void endElement(String uri, String localName, String name, StringBuffer character) {
      if (inAddress) { // delegate address internal tags
         tmpAddress.endElement(uri, localName, name, character);
         if (name.equalsIgnoreCase("address")) {
            inAddress = false;
            addAddress(tmpAddress);
         }
         return;
      }

      if (inCallback) { // delegate address internal tags
         tmpCbAddress.endElement(uri, localName, name, character);
         if (name.equalsIgnoreCase("callback")) {
            inCallback = false;
            addCbAddress(tmpCbAddress);
         }
         return;
      }

      if (name.equalsIgnoreCase("backupnode")) {
         inBackupnode = false;
         character.setLength(0);
         return;
      }

      character.setLength(0);
      return;
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
      StringBuffer sb = new StringBuffer(512);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<info>");
      if (getAddressMap() != null && getAddressMap().size() > 0) {
         Iterator it = getAddressMap().values().iterator();
         while (it.hasNext()) {
            Address info = (Address)it.next();
            sb.append(info.toXml(extraOffset + Constants.INDENT));
         }
      }
 
      if (getCbAddressMap() != null && getCbAddressMap().size() > 0) {
         Iterator it = getCbAddressMap().values().iterator();
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

      return sb.toString();
   }
}
