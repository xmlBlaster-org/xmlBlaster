/*------------------------------------------------------------------------------
Name:      QosData.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Data container handling of publish() and update() quality of services. 
 * <p />
 * QoS Informations sent from the client to the server via the publish() method and back via the update() method<br />
 * They are needed to control xmlBlaster and inform the client.
 * <p />
 * <p>
 * This data holder is accessible through 4 decorators, each of them allowing a specialized view on the data:
 * </p>
 * <ul>
 * <li>PublishQosServer Server side access</i>
 * <li>PublishQos Client side access</i>
 * <li>UpdateQosServer Server side access facade</i>
 * <li>UpdateQos Client side access facade</i>
 * </ul>
 * <p>
 * For the xml representation see MsgQosSaxFactory.
 * </p>
 * @see org.xmlBlaster.util.qos.MsgQosSaxFactory
 * @see org.xmlBlaster.test.classtest.qos.MsgQosFactoryTest
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */

#include <util/qos/QosData.h>
#include <util/Constants.h>
#include <util/lexical_cast.h>
#include <util/Global.h>



using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cluster;

using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos {
                                          
const bool DEFAULT_isSubscribable = true;
const bool DEFAULT_isVolatile   = false;
const bool DEFAULT_persistent   = false;
const bool DEFAULT_forceUpdate  = true;
const bool DEFAULT_forceDestroy = false;

void QosData::init()
{
   state_ = Constants::STATE_OK;
   stateInfo_ = "";
   rcvTimestamp_ = 0;
   rcvTimestampFound_ = false;
   serialData_ = "";
   persistent_ = DEFAULT_persistent;
}

void QosData::copy(const QosData& data)
{
   clientProperties_ = data.clientProperties_;
   state_ = data.state_;
   stateInfo_ = data.stateInfo_;
   rcvTimestamp_ = data.rcvTimestamp_;
   rcvTimestampFound_ = data.rcvTimestampFound_;
   serialData_ = data.serialData_;
   persistent_ = data.persistent_;
}


QosData::QosData(Global& global, const string& serialData)
   : ME("QosData"),
     global_(global),
     log_(global.getLog("org.xmlBlaster.util.qos")),
     routeNodeList_(),
     clientProperties_()     
{
   init();
   serialData_ = serialData;

}


QosData::QosData(const QosData& data)
   : ME(data.ME),
     global_(data.global_),
     log_(data.log_),
     routeNodeList_(data.routeNodeList_),
     clientProperties_()
{
   copy(data);
}

QosData& QosData::operator=(const QosData& data)
{
   copy(data);
   return *this;
}


QosData::~QosData()
{
}

void QosData::setState(const string& state)
{
   state_ = state;
}

string QosData::getState() const
{
   return state_;
}

void QosData::setStateInfo(const string& stateInfo)
{
   stateInfo_ = stateInfo;
}

string QosData::getStateInfo() const
{
   return stateInfo_;
}

bool QosData::isOk() const
{
   return Constants::STATE_OK == state_;
}

bool QosData::isErased() const
{
   return Constants::STATE_ERASED == state_;
}

bool QosData::isTimeout() const
{
   return Constants::STATE_TIMEOUT == state_;
}

bool QosData::isForwardError() const
{
   return Constants::STATE_FORWARD_ERROR == state_;
}

void QosData::addRouteInfo(const RouteInfo& routeInfo)
{
   routeNodeList_.insert(routeNodeList_.end(), routeInfo);

   // Set stratum to new values
   int offset = routeInfo.getStratum();
   if (offset < 0) offset = 0;

   vector<RouteInfo>::reverse_iterator iter = routeNodeList_.rbegin();
   while (iter != routeNodeList_.rend()) {
      (*iter).setStratum(offset++);
      iter++;
   }
}

int QosData::count(const NodeId& nodeId) const
{
   int cnt = 0;
   if (routeNodeList_.empty()) return cnt;
   vector<RouteInfo>::const_iterator iter = routeNodeList_.begin();
   while (iter != routeNodeList_.end()) {
      if ((*iter).getNodeId() == nodeId) cnt++;
      iter++;
   }
   return cnt;
}

bool QosData::dirtyRead(NodeId nodeId) const
{
   if (routeNodeList_.empty()) return false;
   vector<RouteInfo>::const_iterator iter = routeNodeList_.begin();
   while (iter != routeNodeList_.end()) {
      if ((*iter).getNodeId() == nodeId) return (*iter).getDirtyRead();
   }
   return false;
}

void QosData::setRcvTimestamp(Timestamp rcvTimestamp)
{
   rcvTimestamp_ = rcvTimestamp;
}

Timestamp QosData::getRcvTimestamp() const
{
   return rcvTimestamp_;
}

void QosData::touchRcvTimestamp()
{
   rcvTimestamp_ = TimestampFactory::getInstance().getTimestamp();
}

void QosData::addClientProperty(const ClientProperty& clientProperty)
{
   clientProperties_.insert(ClientPropertyMap::value_type(clientProperty.getName(), clientProperty));   
}

bool QosData::hasClientProperty(const string& name) const
{
   return clientProperties_.count(name) > 0;
}

const QosData::ClientPropertyMap& QosData::getClientProperties() const
{
   return clientProperties_;
}

void QosData::setClientProperties(const QosData::ClientPropertyMap& cm)
{
   clientProperties_ = cm;
}

RouteVector QosData::getRouteNodes() const
{
   return routeNodeList_;
}

void QosData::clearRoutes()
{
   routeNodeList_.erase(routeNodeList_.begin(), routeNodeList_.end());
}

int QosData::size() const
{
   return toXml().size();
}

/**
 * @param persistent mark a message as persistent
 */
void QosData::setPersistent(bool persistent)
{
   persistent_ = persistent;
}

/**
 * @return true/false
 */
bool QosData::isPersistent() const
{
   return persistent_;
}

string QosData::dumpClientProperties(const string& extraOffset, bool clearText) const
{
   string ret = "";
   QosData::ClientPropertyMap::const_iterator iter = clientProperties_.begin();
   while (iter != clientProperties_.end()) {
      const ClientProperty& cp = (*iter).second;
      ret += cp.toXml(extraOffset, clearText);
      iter++;
   }
   return ret;
}


}}}}

