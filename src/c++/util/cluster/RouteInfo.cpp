/*------------------------------------------------------------------------------
Name:      RouteInfo.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/cluster/RouteInfo.h>
#include <util/lexical_cast.h>
#include <util/Global.h>



using namespace org::xmlBlaster::util;
using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace cluster {

RouteInfo::RouteInfo(Global& global) : nodeId_(global, "")
{
   stratum_   = 0;
   timestamp_ = 0;
   dirtyRead_ = false;
}


RouteInfo::RouteInfo(const NodeId& nodeId, int stratum, Timestamp timestamp)
: nodeId_(nodeId)
{
  stratum_   = stratum;
  timestamp_ = timestamp;
  dirtyRead_  = false; //  = NodeDomainInfo.DEFAULT_dirtyRead
}

void RouteInfo::setNodeId(const NodeId& nodeId)
{
   nodeId_ = nodeId;
}

NodeId RouteInfo::getNodeId() const
{
   return nodeId_;
}

string RouteInfo::getId() const
{
   return nodeId_.getId();
}

void RouteInfo::setStratum(int stratum)
{
   stratum_ = stratum;
}

int RouteInfo::getStratum() const
{
   return stratum_;
}

void RouteInfo::setTimestamp(Timestamp timestamp)
{
   timestamp_ = timestamp;
}

Timestamp RouteInfo::getTimestamp() const
{
   return timestamp_;
}

void RouteInfo::setDirtyRead(bool dirtyRead)
{
   dirtyRead_ = dirtyRead;
}

bool RouteInfo::getDirtyRead() const
{
   return dirtyRead_;
}

string RouteInfo::toXml(const string& extraOffset) const
{
   string ret;
   string offset = "\n ";
   offset += extraOffset;

   ret += offset + " <node id='" + getNodeId().toString();
   ret += "' stratum='" + lexical_cast<std::string>(getStratum());
   ret += "' timestamp='"+ lexical_cast<std::string>(getTimestamp()) + "'";
      //if (dirtyRead != NodeDomainInfo.DEFAULT_dirtyRead)
   ret += " dirtyRead='" + lexical_cast<std::string>(dirtyRead_) + "'";
   ret += "/>";
   return ret;
}

}}}}

