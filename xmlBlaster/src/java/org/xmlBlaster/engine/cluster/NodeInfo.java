/*------------------------------------------------------------------------------
Name:      NodeInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding information about the current node.
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.engine.helper.Address;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;

import java.util.Map;

/**
 * This class holds the informations about an xmlBlaster server instance (=cluster node). 
 */
public final class NodeInfo
{
   private NodeId id;
   private Address address = new Address();
   private Address[] addresses = new Address[0];
   private Address[] cbAddresses = new Address[0];
   private boolean nameService = false;
   private NodeId[] backupIds = new NodeId[0];
   private NodeStateInfo state = new NodeStateInfo();
   private XmlBlasterConnection xmlBlasterConnection = null;

   /**
    * @link aggregation
    * @associates <{%Dst%}>
    */
   private Map masterMap;
   private boolean available;

   public NodeInfo(NodeId nodeId) {
      setNodeId(nodeId);
   }

   /**
    *
    * @return The unique name of this xmlBlaster instance
    */
   public String getId(){
     return id.getId();
   }

   public NodeId getNodeId(){
     return id;
   }

   public void setNodeId(NodeId id){
      this.id = id;
   }

   public XmlBlasterConnection getXmlBlasterConnection() {
      return xmlBlasterConnection;
   }

   public void setXmlBlasterConnection(XmlBlasterConnection xmlBlasterConnection) {
      this.xmlBlasterConnection = xmlBlasterConnection;
   }

   public void resetXmlBlasterConnection() {
      if (this.xmlBlasterConnection != null) {
         this.xmlBlasterConnection.disconnect(null);
         this.xmlBlasterConnection = null;
      }
   }

   public Address getAddress(){
      return address;
   }

   public void setAddress(Address address){
      this.address = address;
   }

   public Address[] getAddresses(){
      return addresses;
   }

   public void setAddresses(Address[] addresses){
      this.addresses = addresses;
   }

   public Address[] getCbAddresses(){
      return cbAddresses;
   }

   public void setCbAddresses(Address[] cbAddresses){
      this.cbAddresses = cbAddresses;
   }

   public boolean isNameService(){
      return nameService;
   }

   public void setNameService(boolean nameService){
      this.nameService = nameService;
   }

   public NodeId[] getBackupIds(){
      return backupIds;
   }

   public void setBackupIds(NodeId[] backupIds){
      this.backupIds = backupIds;
   }

   public NodeStateInfo getState(){
      return state;
   }

   public void setState(NodeStateInfo state){
      this.state = state;
   }

   public Map getMasterMap(){
      return masterMap;
   }

   public void setMasterMap(Map masterMap){
      this.masterMap = masterMap;
   }

   public boolean isAvailable(){
      return available;
   }

   public void setAvailable(boolean available){
      this.available = available;
   }

}
