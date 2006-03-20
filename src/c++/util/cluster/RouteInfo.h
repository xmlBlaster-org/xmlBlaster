/*------------------------------------------------------------------------------
Name:      RouteInfo.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#ifndef _UTIL_CLUSTER_ROUTEINFO_H
#define _UTIL_CLUSTER_ROUTEINFO_H

#include <util/xmlBlasterDef.h>
#include <util/cluster/NodeId.h>
#include <string>

namespace org { namespace xmlBlaster { namespace util { namespace cluster {

/**
 * This class holds the information about a route node which the message passed. 
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */
class Dll_Export RouteInfo
{
private:
   org::xmlBlaster::util::cluster::NodeId    nodeId_;
   int       stratum_;
   Timestamp timestamp_;
   bool      dirtyRead_;

public:

   RouteInfo(org::xmlBlaster::util::Global& global);

   /**
    * @param nodeId The unique name of the xmlBlaster instance
    * @param stratum The distance from the node to the master node, if you don't know
    *                it set it to 0.
    * @param timestamp The receive timestamp of the message (nano seconds)
    */
   RouteInfo(const org::xmlBlaster::util::cluster::NodeId& nodeId, int stratum, Timestamp timestamp);

   /**
    * The unique node name of the xmlBlaster instance. 
    */
   void setNodeId(const org::xmlBlaster::util::cluster::NodeId& nodeId);

   /**
    * The unique node name of the xmlBlaster instance. 
    */
   org::xmlBlaster::util::cluster::NodeId getNodeId() const;

   /**
    * The unique node name of the xmlBlaster instance. 
    * @param The std::string representation of my name
    */
   std::string getId() const;

   /**
    * The distance from the current xmlBlaster node from the
    * master node (for this message). 
    */
   void setStratum(int stratum);

   /**
    * The distance from the current xmlBlaster node from the
    * master node (for this message). 
    */
   int getStratum() const;

   /**
    * Message receive timestamp in nano seconds
    */
   void setTimestamp(Timestamp timestamp);

   /**
    * Message receive timestamp in nano seconds
    */
   Timestamp getTimestamp() const;

   /**
    * @param dirtyRead true if cluster slaves cache forwarded publish messages
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/cluster.dirtyRead.html">The cluster.dirtyRead requirement</a>
    */
   void setDirtyRead(bool dirtyRead);

   bool getDirtyRead() const;

   /**
    * Dump state of this object into a XML ASCII std::string. 
    * @param extraOffset indenting of tags for nice output
    */
   std::string toXml(const std::string& extraOffset="") const;
};

}}}}

#endif
