/*------------------------------------------------------------------------------
Name:      NodeInfo.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding information about the current node.
------------------------------------------------------------------------------*/

#include <util/cluster/NodeInfo.h>
#include <util/Global.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cfg;
using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace cluster {

/**
 * This class holds the address informations about an
 * xmlBlaster server instance (=cluster node). 
 */

typedef map<string, CallbackAddress> CbAddressMap;
typedef map<string, Address>         AddressMap;
typedef map<string, NodeId>          NodeMap;

NodeInfo::NodeInfo(Global& global, NodeId nodeId)
   : ME(string("NodeInfo.") + nodeId.toString()), global_(global), nodeId_(nodeId),
     tmpAddress_(global), tmpCbAddress_(global), cbAddressMap_(), addressMap_(),
     backupNodeMap_()
{
   init();
}

NodeInfo::NodeInfo(const NodeInfo& info)
   : ME(info.ME), global_(info.global_), nodeId_(info.nodeId_),
     tmpAddress_(info.tmpAddress_), tmpCbAddress_(info.tmpCbAddress_),
     cbAddressMap_(info.cbAddressMap_), addressMap_(info.addressMap_),
     backupNodeMap_(info.backupNodeMap_)
{
   copy(info);
}

NodeInfo& NodeInfo::operator=(const NodeInfo& info)
{
   copy(info);
   return *this;
}

/**
 * @return The unique name of the managed xmlBlaster instance e.g. "bilbo.mycompany.com"
 */
string NodeInfo::getId() const
{
  return nodeId_.getId();
}

   /**
    * @return The unique name of the managed xmlBlaster instance.
    */
   NodeId NodeInfo::getNodeId() const
   {
     return nodeId_;
   }

   /**
    * @param The unique name of the managed xmlBlaster instance
    */
   void NodeInfo::setNodeId(NodeId nodeId)
   {
      nodeId_ = nodeId;
   }

   /**
    * Access the currently used address to access the node
    * @return null if not specified
    */
   Address NodeInfo::getAddress() const
   {
      if (addressMap_.empty()) return Address(global_);
      return (*(addressMap_.begin())).second;
   }

   /**
    * Add another address for this cluster node. 
    * <p />
    * The map is sorted with the same sequence as the given XML sequence
    */
   void NodeInfo::addAddress(Address& address)
   {
      addressMap_.insert(AddressMap::value_type(address.getAddress(), address));
   }

   /**
    * Access all addresses of a node, please handle as readonly. 
    */
   AddressMap NodeInfo::getAddressMap() const
   {
      return addressMap_;
   }

   /**
    * Does the given address belong to this node?
    */
   bool NodeInfo::contains(Address& other)
   {
      if (addressMap_.empty()) return false;
      return (addressMap_.find(other.getAddress()) != addressMap_.end());
   }

   /**
    * Access the currently used callback address for this node
    * @return Never null, returns a default if none specified
    */
   CallbackAddress NodeInfo::getCbAddress()
   {
      if (cbAddressMap_.empty()) {
         CallbackAddress cbAddr = CallbackAddress(global_);
         addCbAddress(cbAddr);
      }
      return (*(cbAddressMap_.begin())).second;
   }

   /**
    * Currently not used. 
    */
   CbAddressMap NodeInfo::getCbAddressMap() const
   {
      return cbAddressMap_;
   }

   /**
    * Add another callback address for this cluster node. 
    */
   void NodeInfo::addCbAddress(CallbackAddress& cbAddress)
   {
      cbAddressMap_.insert(CbAddressMap::value_type(cbAddress.getAddress(),cbAddress));
   }

   /**
    * Is the node acting as a preferred cluster naming service. 
    * <p />
    * NOTE: This mode is currently not supported
    */
   bool NodeInfo::isNameService() const
   {
      return nameService_;
   }

   /**
    * Tag this node as a cluster naming service. 
    * <p />
    * NOTE: This mode is currently not supported
    */
   void NodeInfo::setNameService(bool nameService)
   {
      nameService_ = nameService;
   }

   /**
    * If this node is not accessible, we can use its backup nodes. 
    * @return a Map containing NodeId objects
    */
   NodeMap NodeInfo::getBackupnodeMap() const
   {
      return backupNodeMap_;
   }

   /**
    * Set backup nodes. 
    */
   void NodeInfo::addBackupnode(const NodeId& backupId)
   {
      backupNodeMap_.insert(NodeMap::value_type(backupId.getId(), backupId));
   }

   /**
    * Called for SAX master start tag
    * @return true if ok, false on error
    */
/*
   bool startElement(String uri, String localName, String name, StringBuffer character, Attributes attrs) {
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
*/

   /**
    * Handle SAX parsed end element
    */
/*
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
*/

   /**
    * Dump state of this object into a XML ASCII string.
    * @param extraOffset indenting of tags for nice output
    */
   string NodeInfo::toXml(const string& extraOffset="")
   {
      string ret;
      string offset = "\n   ";
      offset += extraOffset;

      ret += offset + "<info>";
      if (!addressMap_.empty()) {
         AddressMap::iterator iter = addressMap_.begin();
         while (iter != addressMap_.end()) {
            Address info = (*iter).second;
            ret += info.toXml(extraOffset + "   ");
            iter++;
         }
      }
 
      if (!cbAddressMap_.empty()) {
         CbAddressMap::iterator iter = cbAddressMap_.begin();
         while (iter != cbAddressMap_.end()) {
            CallbackAddress info = (*iter).second;
            ret +=  info.toXml(extraOffset + "   ");
            iter++;
         }
      }

      if (!backupNodeMap_.empty()) {
         NodeMap::iterator iter = backupNodeMap_.begin();
         ret += offset + "   <backupnode>";
         while (iter != backupNodeMap_.end()) {
            NodeId info = (*iter).second;
            ret += offset + "      <clusternode id='" + info.getId() + "'/>";
         }
         ret += offset + "   </backupnode>";
      }
      ret += offset + "</info>";
      return ret;
   }

}}}}

