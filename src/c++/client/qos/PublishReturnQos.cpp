/*------------------------------------------------------------------------------
Name:      PublishReturnQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <client/qos/PublishReturnQos.h>
#include <util/Global.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace std;

namespace org { namespace xmlBlaster { namespace client { namespace qos {

PublishReturnQos::PublishReturnQos(Global& global, const StatusQosData& data)
   : ME("PublishReturnQos"), global_(global), data_(data)
{
}

PublishReturnQos::PublishReturnQos(Global& global)
   : ME("PublishReturnQos"), global_(global), data_(global)
{
}

PublishReturnQos::PublishReturnQos(const PublishReturnQos& data)
  : ME(data.ME), global_(data.global_), data_(data.data_)
{
}

PublishReturnQos PublishReturnQos::operator =(const PublishReturnQos& /*data*/)
{
   return *this;
}

string PublishReturnQos::getState() const
{
   return data_.getState();
}

void PublishReturnQos::setState(const string& state)
{
   data_.setState(state);
}

string PublishReturnQos::getStateInfo() const
{
   return data_.getStateInfo();
}

string PublishReturnQos::getKeyOid() const
{
   return data_.getKeyOid();
}

void PublishReturnQos::setKeyOid(const string& oid)
{
   data_.setKeyOid(oid);
}

StatusQosData& PublishReturnQos::getData()
{
   return data_;
}

Timestamp PublishReturnQos::getRcvTimestamp() const
{
   return data_.getRcvTimestamp();
}

string PublishReturnQos::toXml(const string& extraOffset) const
{
   return data_.toXml(extraOffset);
}

}}}} // namespace
