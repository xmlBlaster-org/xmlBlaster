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
public class NodeInfo {
   public NodeId getId(){
     return id;
   }

   public void setId(NodeId id){
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

   private NodeId id;
   private Address address;
   private Address[] addresses;
   private Address[] cbAddresses;
   private boolean nameService;
   private NodeId[] backupIds;
   private NodeStateInfo state;
   private XmlBlasterConnection xmlBlasterConnection;

   /**
    *@link aggregation
    *      @associates <{%Dst%}>
    */
   private Map masterMap;
   private boolean available;
}
