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
#include <util/Log.h>

#include <util/qos/address/CallbackAddress.h>
#include <util/qos/address/Address.h>
#include <util/cluster/NodeId.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos::address;
using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace cluster {

/**
 * This class holds the address informations about an
 * xmlBlaster server instance (=cluster node). 
 */

typedef map<string, CallbackAddress> CbAddressMap;
typedef map<string, Address>         AddressMap;
typedef map<string, NodeId>          NodeMap;

class Dll_Export NodeInfo
{
private:
   string          ME;
   Global&         global_;
   NodeId          nodeId_;
   Address         tmpAddress_; // = null; // Helper for SAX parsing
   CallbackAddress tmpCbAddress_; // = null; // Helper for SAX parsing
   CbAddressMap    cbAddressMap_; // = null;
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
   NodeInfo(Global& global, NodeId nodeId);

   NodeInfo(const NodeInfo& info);

   NodeInfo& operator=(const NodeInfo& info);

   /**
    * @return The unique name of the managed xmlBlaster instance e.g. "bilbo.mycompany.com"
    */
   string getId() const;

   /**
    * @return The unique name of the managed xmlBlaster instance.
    */
   NodeId getNodeId() const;

   /**
    * @param The unique name of the managed xmlBlaster instance
    */
   void setNodeId(NodeId nodeId);

   /**
    * Access the currently used address to access the node
    * @return null if not specified
    */
   Address getAddress() const;

   /**
    * Add another address for this cluster node. 
    * <p />
    * The map is sorted with the same sequence as the given XML sequence
    */
   void addAddress(Address& address);

   /**
    * Access all addresses of a node, please handle as readonly. 
    */
   AddressMap getAddressMap() const;

   /**
    * Does the given address belong to this node?
    */
   bool contains(Address& other);

   /**
    * Access the currently used callback address for this node
    * @return Never null, returns a default if none specified
    */
   CallbackAddress getCbAddress();

   /**
    * Currently not used. 
    */
   CbAddressMap getCbAddressMap() const;

   /**
    * Add another callback address for this cluster node. 
    */
   void addCbAddress(CallbackAddress& cbAddress);

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
    * @return a Map containing NodeId objects
    */
   NodeMap getBackupnodeMap() const;

   /**
    * Set backup nodes. 
    */
   void addBackupnode(const NodeId& backupId);

   /**
    * Dump state of this object into a XML ASCII string.
    * @param extraOffset indenting of tags for nice output
    */
   string toXml(const string& extraOffset="");
};

}}}}

#endif
