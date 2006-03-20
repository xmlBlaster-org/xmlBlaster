/*------------------------------------------------------------------------------
Name:      StatusQosData.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Data container handling of status returned by subscribe(), unSubscribe(), erase() and ping(). 
 * <p>
 * This data holder is accessible through decorators, each of them allowing a specialized view on the data:
 * </p>
 * <ul>
 * <li>SubscribeReturnQos Returned QoS of a subscribe() invocation (Client side)</i>
 * <li>UnSubscribeReturnQos Returned QoS of a unSubscribe() invocation (Client side)</i>
 * <li>EraseReturnQos Returned QoS of an erase() invocation (Client side)</i>
 * </ul>
 * <p>
 * For the xml representation see StatusQosSaxFactory.
 * </p>
 * @see org.xmlBlaster.util.qos.StatusQosSaxFactory
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */

#include <util/qos/StatusQosData.h>
#include <util/Global.h>
// #include <lexical_cast.hpp>
// 

using namespace org::xmlBlaster::util;
using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

void StatusQosData::copy(const StatusQosData& data)
{
   state_          = data.state_;
   stateInfo_      = data.stateInfo_;
   subscriptionId_ = data.subscriptionId_;
   keyOid_         = data.keyOid_;
   rcvTimestamp_   = data.rcvTimestamp_;
   persistent_     = data.persistent_;
}

StatusQosData::StatusQosData(Global& global)
   : ME("StatusQosData"), global_(global)
{
   state_ = Constants::STATE_OK;
   rcvTimestamp_ = 0;
   persistent_ = false;
}

StatusQosData::StatusQosData(const StatusQosData& data)
  : ME(data.ME), global_(data.global_)
{
   copy(data);
}

StatusQosData StatusQosData::operator =(const StatusQosData& data)
{
   copy(data);
   return *this;
}

void StatusQosData::setState(const string& state)
{
   state_ = state;
}

string StatusQosData::getState() const
{
   return state_;
}

bool StatusQosData::isOk() const
{
   return Constants::STATE_OK == state_;
}

bool StatusQosData::isErased() const
{
   return Constants::STATE_ERASED == state_;
}

bool StatusQosData::isTimeout() const
{
   return Constants::STATE_TIMEOUT == state_;
}

bool StatusQosData::isForwardError() const
{
   return Constants::STATE_FORWARD_ERROR == state_;
}

void StatusQosData::setStateInfo(const string& stateInfo)
{
   stateInfo_ = stateInfo;
}

string StatusQosData::getStateInfo() const
{
   return stateInfo_;
}

void StatusQosData::setSubscriptionId(const string& subscriptionId)
{
   subscriptionId_ = subscriptionId;
}

string StatusQosData::getSubscriptionId() const
{
   return subscriptionId_;
}

string StatusQosData::getKeyOid() const
{
   return keyOid_;
}

void StatusQosData::setKeyOid(const string& oid)
{
   keyOid_ = oid;
}

int StatusQosData::size() const
{
   return (int)toXml().length();
}

string StatusQosData::toXml(const string& extraOffset) const
{
   string ret;
   string offset = Constants::OFFSET + extraOffset;

   ret += offset + "<qos>"; // <!-- SubscribeRetQos -->");
   if (!isOk()) {
      ret += offset + " <state id='" + getState();
      if (!getStateInfo().empty())
         ret += "' info='" + getStateInfo();
      ret += "'/>";
   }
   if (!getSubscriptionId().empty())
      ret += offset + " <subscribe id='" + getSubscriptionId() + "'/>";
   if (!getKeyOid().empty())
      ret += offset + " <key oid='" + getKeyOid() + "'/>";
   ret += offset + "</qos>";
   if (isPersistent())
      ret += offset + " <persistent/>";

   if (ret.length() < 16)
      return "<qos/>";  // minimal footprint

   return ret;
}

void StatusQosData::setRcvTimestamp(Timestamp rcvTimestamp)
{
   rcvTimestamp_ = rcvTimestamp;
}

Timestamp StatusQosData::getRcvTimestamp() const
{
   return rcvTimestamp_;
}

void StatusQosData::touchRcvTimestamp()
{
   rcvTimestamp_ = TimestampFactory::getInstance().getTimestamp();
}

/**
 * @param persistent mark a message as persistent
 */
void StatusQosData::setPersistent(bool persistent)
{
   persistent_ = persistent;
}

/**
 * @return true/false
 */
bool StatusQosData::isPersistent() const
{
   return persistent_;
}

}}}} // namespace
