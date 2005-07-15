/*------------------------------------------------------------------------------
Name:      NodeInfo.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding information about the current node.
------------------------------------------------------------------------------*/
#ifndef _UTIL_CLUSTER_NODEINFO_H
#define _UTIL_CLUSTER_NODEINFO_H

#include <util/xmlBlasterDef.h>
#include <string>
#include <map>

#include <util/qos/address/CallbackAddress.h>
#include <util/qos/address/Address.h>
#include <util/cluster/NodeId.h>

namespace org { namespace xmlBlaster { namespace util { namespace cluster {

/**
 * This class holds the address informations about an
 * xmlBlaster server instance (=cluster node). 
 */
typedef std::map<std::string, org::xmlBlaster::util::qos::address::AddressBaseRef> AddressMap;
typedef std::map<std::string, org::xmlBlaster::util::cluster::NodeId>          NodeMap;

class Dll_Export NodeInfo
{
private:
   std::string          ME;
   org::xmlBlaster::util::Global&         global_;
   org::xmlBlaster::util::cluster::NodeId          nodeId_;
   org::xmlBlaster::util::qos::address::Address         tmpAddress_; // = null; // Helper for SAX parsing
   org::xmlBlaster::util::qos::address::CallbackAddress tmpCbAddress_; // = null; // Helper for SAX parsing
   AddressMap      cbAddressMap_; // = null;
   AddressMap      addressMap_; // = null;
   NodeMap         backupNodeMap_; // = null;
   bool            nameService_; //  = false;
   bool            inAddress_; // = false; // parsing inside <address> ?
   bool            inCallback_; //  = false; // parsing inside <callback> ?
   bool            inBackupnode_; // = false; // parsing inside <backupnode> ?

   void init()
   {
      nameService_  = false;
      inAddress_    = false;
      inCallback_   = false;
      inBackupnode_ = false;
   }

   void copy(const NodeInfo& info)
   {
   tmpAddress_   = info.tmpAddress_;
   tmpCbAddress_ = info.tmpCbAddress_;
   nameService_  = info.nameService_;
   inAddress_    = info.inAddress_;
   inCallback_   = info.inCallback_;
   inBackupnode_ = info.inBackupnode_;
   }


   /**
    * Holds the addresses of a node. 
    */
public:
   NodeInfo(org::xmlBlaster::util::Global& global, org::xmlBlaster::util::cluster::NodeId nodeId);

   NodeInfo(const NodeInfo& info);

   NodeInfo& operator=(const NodeInfo& info);

   /**
    * @return The unique name of the managed xmlBlaster instance e.g. "bilbo.mycompany.com"
    */
   std::string getId() const;

   /**
    * @return The unique name of the managed xmlBlaster instance.
    */
   org::xmlBlaster::util::cluster::NodeId getNodeId() const;

   /**
    * @param The unique name of the managed xmlBlaster instance
    */
   void setNodeId(org::xmlBlaster::util::cluster::NodeId nodeId);

   /**
    * Access the currently used address to access the node
    * @return null if not specified
    */
   org::xmlBlaster::util::qos::address::AddressBaseRef getAddress() const;

   /**
    * Add another address for this cluster node. 
    * <p />
    * The std::map is sorted with the same sequence as the given XML sequence
    */
   void addAddress(const org::xmlBlaster::util::qos::address::AddressBaseRef& address);

   /**
    * Access all addresses of a node, please handle as readonly. 
    */
   AddressMap getAddressMap() const;

   /**
    * Does the given address belong to this node?
    */
   bool contains(const org::xmlBlaster::util::qos::address::AddressBaseRef& other);

   /**
    * Access the currently used callback address for this node
    * @return Never null, returns a default if none specified
    */
   org::xmlBlaster::util::qos::address::AddressBaseRef getCbAddress();

   /**
    * Currently not used. 
    */
   AddressMap getCbAddressMap() const;

   /**
    * Add another callback address for this cluster node. 
    */
   void addCbAddress(const org::xmlBlaster::util::qos::address::AddressBaseRef& cbAddress);

   /**
    * Is the node acting as a preferred cluster naming service. 
    * <p />
    * NOTE: This mode is currently not supported
    */
   bool isNameService() const;

   /**
    * Tag this node as a cluster naming service. 
    * <p />
    * NOTE: This mode is currently not supported
    */
   void setNameService(bool nameService);

   /**
    * If this node is not accessible, we can use its backup nodes. 
    * @return a Map containing org::xmlBlaster::util::cluster::NodeId objects
    */
   NodeMap getBackupnodeMap() const;

   /**
    * Set backup nodes. 
    */
   void addBackupnode(const org::xmlBlaster::util::cluster::NodeId& backupId);

   /**
    * Dump state of this object into a XML ASCII std::string.
    * @param extraOffset indenting of tags for nice output
    */
   std::string toXml(const std::string& extraOffset="");
};

}}}}

#endif
