/*------------------------------------------------------------------------------
Name:      GetReturnQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

# include <client/qos/GetReturnQos.h>
# include <util/Timestamp.h>
# include <util/Global.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::cluster;

using namespace std;
namespace org { namespace xmlBlaster { namespace client { namespace qos {

GetReturnQos::GetReturnQos(Global& global, const MsgQosData& data)
   : ME("GetReturnQos"), global_(global), log_(global.getLog("org.xmlBlaster.client")), data_(data)
{
}

GetReturnQos::GetReturnQos(const GetReturnQos& data)
   : ME(data.ME), global_(data.global_), log_(data.log_), data_(data.data_)
{
}

GetReturnQos& GetReturnQos::operator=(const GetReturnQos&)
{
   return *this;
}

bool GetReturnQos::isVolatile() const
{
   return data_.isVolatile();
}

bool GetReturnQos::isPersistent() const
{
   return data_.isPersistent();
}

bool GetReturnQos::isReadonly() const
{
   return data_.isReadonly();
}

SessionNameRef GetReturnQos::getSender()
{
   return data_.getSender();
}

PriorityEnum GetReturnQos::getPriority() const
{
   return data_.getPriority();
}

long GetReturnQos::getRemainingLifeStatic() const
{
   return data_.getRemainingLifeStatic();
}

string GetReturnQos::toXml(const string& extraOffset, bool clearText)
{
   return data_.toXml(clearText, extraOffset);
}

string GetReturnQos::getState() const
{
   return data_.getState();
}

bool GetReturnQos::isOk() const
{
   return data_.isOk();
}

bool GetReturnQos::isErased() const
{
   return data_.isErased();
}

bool GetReturnQos::isTimeout() const
{
   return data_.isTimeout();
}

Timestamp GetReturnQos::getRcvTimestamp() const
{
   return data_.getRcvTimestamp();
}

RouteVector GetReturnQos::getRouteNodes()
{
   return data_.getRouteNodes();
}

string GetReturnQos::getRcvTime() const
{
   return TimestampFactory::toXml(data_.getRcvTimestamp());
}

const QosData::ClientPropertyMap& GetReturnQos::getClientProperties() const
{
   return data_.getClientProperties();
}

}}}}
