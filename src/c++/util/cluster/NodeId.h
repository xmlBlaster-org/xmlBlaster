/*------------------------------------------------------------------------------
Name:      NodeId.h
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

#ifndef _UTIL_CLUSTER_NODEID_H
#define _UTIL_CLUSTER_NODEID_H

#include <util/xmlBlasterDef.h>
#include <string>
#include <util/I_Log.h>

namespace org { namespace xmlBlaster { namespace util { namespace cluster {

class Dll_Export NodeId
{
private:
   const std::string ME;
   std::string       id_;
   org::xmlBlaster::util::Global&      global_;
   org::xmlBlaster::util::I_Log&       log_;

public:
   NodeId(org::xmlBlaster::util::Global& global, const std::string& id);

   NodeId(const NodeId& nodeId);

   NodeId& operator =(const NodeId& nodeId);

   std::string getId() const;

   /**
    * @param id The cluster node id, e.g. "heron".<br />
    * If you pass "/node/heron/client/joe" everything ins stripped to get "heron"
    */
   void setId(const std::string& id);

   std::string toString() const;

   /**
    * Needed for use in TreeSet and TreeMap, enforced by java.lang.Comparable
    */
   bool operator <(const NodeId& nodeId) const;

   bool operator ==(const NodeId& nodeId) const;

};

}}}} // namespace

#endif
