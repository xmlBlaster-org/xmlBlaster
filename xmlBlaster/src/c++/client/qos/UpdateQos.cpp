/*------------------------------------------------------------------------------
Name:      UpdateQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

# include <client/qos/UpdateQos.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::cluster;

using namespace std;

namespace org { namespace xmlBlaster { namespace client { namespace qos {

UpdateQos::UpdateQos(Global& global, const MsgQosData data)
   : GetReturnQos(global, data)
{
   ME = "UpdateQos";
}

UpdateQos::UpdateQos(const UpdateQos& data)
   : GetReturnQos(data)
{
   ME = data.ME;
}

UpdateQos& UpdateQos::operator=(const UpdateQos& data)
{
   GetReturnQos::operator=(static_cast<GetReturnQos>(data));
   ME = data.ME;
   return *this;
}

/**
* Test if Publish/Subscribe style is used.
*
* @return true if Publish/Subscribe style is used
*         false if addressing of the destination is used
*/
bool UpdateQos::isSubscribable() const
{
   return data_.isSubscribable();
}

/**
* Test if Point to Point addressing style is used.
*
* @return true if addressing of the destination is used
*         false if Publish/Subscribe style is used
*/
bool UpdateQos::isPtp()
{
   return data_.isPtp();
}

/**
* If Pub/Sub style update: contains the subscribe ID which caused this update
* @return subscribeId or null if PtP message
*/
string UpdateQos::getSubscriptionId() const
{
   return data_.getSubscriptionId();
}

/**
* Returns > 0 if the message probably is redelivered. 
* @return == 0 The message is guaranteed to be delivered only once.
*/
int UpdateQos::getRedeliver() const
{
   return data_.getRedeliver();
}

/**
* @return The number of queued messages
*/
long UpdateQos::getQueueSize() const
{
   return data_.getQueueSize();
}

/**
* @return The index of the message in the queue
*/
long UpdateQos::getQueueIndex() const
{
   return data_.getQueueIndex();
}

bool UpdateQos::isOk() const
{
   return data_.isOk();
}

bool UpdateQos::isErased() const
{
   return data_.isErased();
}

bool UpdateQos::isTimeout() const
{
   return data_.isTimeout();
}

bool UpdateQos::isForwardError() const
{
   return data_.isForwardError();
}

}}}}


