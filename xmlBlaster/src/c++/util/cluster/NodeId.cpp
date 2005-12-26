/*------------------------------------------------------------------------------
Name:      NodeId.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holds the unique name of a cluster node
------------------------------------------------------------------------------*/


/**
 * Holds the unique name of an xmlBlaster server instance (= cluster node)
 * @author xmlBlaster@marcelruff.info 
 * @author laghi@swissinfo.org
 * @since 0.79e
 * @url http://www.xmlBlaster.org/xmlBlaster/doc/requirements/cluster.html
 */

#include <util/cluster/NodeId.h>
#include <util/Global.h>
#include <util/lexical_cast.h>

namespace org { namespace xmlBlaster { namespace util { namespace cluster {

using namespace std;
using namespace org::xmlBlaster::util;

NodeId::NodeId(Global& global, const string& id)
: ME("NodeId"), global_(global), log_(global.getLog("org.xmlBlaster.cluster"))
{
   setId(id);
}

NodeId::NodeId(const NodeId& nodeId) : ME("NodeId"), global_(nodeId.global_), log_(nodeId.log_)
{
   setId(nodeId.id_);
}

NodeId& NodeId::operator =(const NodeId& nodeId)
{
   setId(nodeId.id_);
   return *this;
}

string NodeId::getId() const
{
   return id_;
}

/**
 * @param id The cluster node id, e.g. "heron".<br />
 * If you pass "/node/heron/client/joe" everything ins stripped to get "heron"
 */
void NodeId::setId(const string& id)
{
   if (id.empty()) {
//      log_.error(ME, "Cluster node has no name");
      id_ = "NoNameNode";
   }
   id_ = id;

   if (id_.find_first_of("/node/") == 0)
      id_ = id_.substr(string("/node/").length()); // strip leading "/node/"
   string::size_type index = id_.find_first_of("/");   // strip tailing tokens, e.g. from "heron/client/joe" make a "heron"
   if (index == 0) {
      throw XmlBlasterException(INTERNAL_ILLEGALARGUMENT, ME, "setId: The given cluster node ID '" + lexical_cast<std::string>(id_) + "' may not start with a '/'");
   }
   if (index > 0) {
      id_ = id_.substr(0, index);
   }
}

string NodeId::toString() const
{
   return getId();
}
 
/**
 * Needed for use in TreeSet and TreeMap, enforced by java.lang.Comparable
 */
bool NodeId::operator <(const NodeId& nodeId) const
{
   return toString() < nodeId.toString();
}

bool NodeId::operator ==(const NodeId& nodeId) const
{
   return toString() == nodeId.toString();
}

}}}} // namespace
