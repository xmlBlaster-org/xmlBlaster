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
#include <util/Log.h>

using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace util { namespace cluster {

class Dll_Export NodeId
{
private:
   const string ME;
   string       id_;
   Global&      global_;
   Log&         log_;

public:
   NodeId(Global& global, const string& id);

   NodeId(const NodeId& nodeId);

   NodeId& operator =(const NodeId& nodeId);

   string getId() const;

   /**
    * @param id The cluster node id, e.g. "heron".<br />
    * If you pass "/node/heron/client/joe" everything ins stripped to get "heron"
    */
   void setId(const string& id);

   string toString() const;

   /**
    * Needed for use in TreeSet and TreeMap, enforced by java.lang.Comparable
    */
   bool operator <(const NodeId& nodeId) const;

   bool operator ==(const NodeId& nodeId) const;

};

}}}} // namespace

#endif
