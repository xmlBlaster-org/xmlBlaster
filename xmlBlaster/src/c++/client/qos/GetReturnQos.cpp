/*------------------------------------------------------------------------------
Name:      GetReturnQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

# include <client/qos/GetReturnQos.h>
# include <util/Timestamp.h>
# include <util/Global.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cluster;

using namespace std;

namespace org { namespace xmlBlaster { namespace client { namespace qos {

GetReturnQos::GetReturnQos(Global& global, const MsgQosData data)
   : ME("GetReturnQos"), global_(global), log_(global.getLog("core")), data_(data)
{
}

GetReturnQos::GetReturnQos(const GetReturnQos& data)
   : ME(data.ME), global_(data.global_), log_(data.log_), data_(data.data_)
{
}

GetReturnQos& GetReturnQos::operator=(const GetReturnQos& data)
{
   return *this;
}

/**
* @return true/false
*/
bool GetReturnQos::isVolatile() const
{
   return data_.isVolatile();
}

/**
* @return true/false
*/
bool GetReturnQos::isDurable() const
{
   return data_.isDurable();
}

/**
* @return true/false
*/
bool GetReturnQos::isReadonly() const
{
   return data_.isReadonly();
}

/**
* Access sender unified naming object.
* @return sessionName of sender or null if not known
*/
SessionQos GetReturnQos::getSender()
{
   return data_.getSender();
}

/**
* Message priority.
* @return priority 0-9
* @see org.xmlBlaster.engine.helper.Constants
*/
PriorityEnum GetReturnQos::getPriority() const
{
   return data_.getPriority();
}

/**
* This is the value delivered in the QoS (as it was calculated by the server on sending)
* and is NOT dynamically recalculated.
* So trust this value only if your client clock is out of date (or not trusted) and
* if you know the message sending latency is not too big.
* @return Milliseconds until message expiration (from now) or -1L if forever
*         if 0L the message is expired
*/
long GetReturnQos::getRemainingLifeStatic() const
{
   return data_.getRemainingLifeStatic();
}

/**
* Dump state of this object into a XML ASCII string.
* <br>
* @param extraOffset indenting of tags for nice output
* @return internal state of the message QoS as a XML ASCII string
*/
string GetReturnQos::toXml(const string& extraOffset)
{
   return data_.toXml(extraOffset);
}


/**
* Access state of message on update().
* @return OK (Other values are not yet supported)
*/
string GetReturnQos::getState() const
{
   return data_.getState();
}

/**
* True if the message is OK on update(). 
*/
bool GetReturnQos::isOk() const
{
   return data_.isOk();
}

/**
* True if the message was erased by timer or by a
* client invoking erase(). 
*/
bool GetReturnQos::isErased() const
{
   return data_.isErased();
}

/**
* True if a timeout on this message occurred. 
* <p />
* Timeouts are spanned by the publisher and thrown by xmlBlaster
* on timeout to indicate for example
* STALE messages or any other user problem domain specific event.
*/
bool GetReturnQos::isTimeout() const
{
   return data_.isTimeout();
}

/** 
* The approximate receive timestamp (UTC time),
* when message arrived in requestBroker.publish() method.<br />
* In milliseconds elapsed since midnight, January 1, 1970 UTC
*/
Timestamp GetReturnQos::getRcvTimestamp() const
{
   return data_.getRcvTimestamp();
}

// the following where not present before ...
RouteVector GetReturnQos::getRouteNodes()
{
   return data_.getRouteNodes();
}

string GetReturnQos::getRcvTime() const
{
   return TimestampFactory::toXml(data_.getRcvTimestamp());
}


}}}}


