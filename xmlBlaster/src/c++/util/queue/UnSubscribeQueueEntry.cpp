/*------------------------------------------------------------------------------
Name:      UnSubscribeQueueEntry.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/UnSubscribeQueueEntry.h>
#include <util/dispatch/I_ConnectionsHandler.h>

namespace org { namespace xmlBlaster { namespace util { namespace queue {

using namespace std;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

UnSubscribeQueueEntry::UnSubscribeQueueEntry(Global& global, const UnSubscribeKey& unSubscribeKey, const UnSubscribeQos& unSubscribeQos, const string& type, int priority, bool persistent)
   : MsgQueueEntry(global, unSubscribeKey.getData(), unSubscribeQos.getData(), type, priority, persistent)
{
   ME = "UnSubscribeQueueEntry";
}

MsgQueueEntry *UnSubscribeQueueEntry::getClone() const
{
   return new UnSubscribeQueueEntry(*this);
}

void* UnSubscribeQueueEntry::getEmbeddedObject()
{
   return queryKeyData_; // actually not used now otherwise we would need to return also the qos
}

// this should actually be in another interface but since it is an only method we put it here.
const MsgQueueEntry& UnSubscribeQueueEntry::send(I_ConnectionsHandler& connectionsHandler) const
{
   if (log_.call()) log_.call(ME, "send");
   if (statusQosData_) {
      delete statusQosData_;
      statusQosData_ = NULL;
   }
   if (log_.dump()) log_.dump(ME, string("send: ") + toXml());
   // the return value is not stored ...
   connectionsHandler.getConnection().unSubscribe(UnSubscribeKey(global_, *queryKeyData_), UnSubscribeQos(global_, *queryQosData_));

   return *this;
}

UnSubscribeQos UnSubscribeQueueEntry::getUnSubscribeQos() const
{
   return UnSubscribeQos(global_, *queryQosData_);
}

UnSubscribeKey UnSubscribeQueueEntry::getUnSubscribeKey() const
{
   return UnSubscribeKey(global_, *queryKeyData_);
}

UnSubscribeReturnQos UnSubscribeQueueEntry::getUnSubscribeReturnQos() const
{
   return UnSubscribeReturnQos(global_, *statusQosData_);
}


string UnSubscribeQueueEntry::toXml(const string& indent) const
{
   string extraOffset = "   " + indent;
   string ret = indent + "<unSubscribeQueueEntry>\n" + 
                extraOffset + queryKeyData_->toXml("  ") +
                extraOffset + queryQosData_->toXml("  ") +
                indent + "</unSubscribeQueueEntry>\n";
   return ret;
}

}}}} // namespace


