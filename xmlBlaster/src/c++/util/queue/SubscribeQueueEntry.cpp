/*------------------------------------------------------------------------------
Name:      SubscribeQueueEntry.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/SubscribeQueueEntry.h>
#include <util/dispatch/I_ConnectionsHandler.h>

namespace org { namespace xmlBlaster { namespace util { namespace queue {

using namespace std;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

SubscribeQueueEntry::SubscribeQueueEntry(Global& global, const SubscribeKey& subscribeKey, const SubscribeQos& subscribeQos, const string& type, int priority, bool persistent, Timestamp uniqueId)
   : MsgQueueEntry(global, subscribeKey.getData(), subscribeQos.getData(), type, priority, persistent, uniqueId)
{
   ME = "SubscribeQueueEntry";
}

MsgQueueEntry *SubscribeQueueEntry::getClone() const
{
   return new SubscribeQueueEntry(*this);
}

const void* SubscribeQueueEntry::getEmbeddedObject() const
{
   return queryKeyData_; // actually not used now otherwise we would need to return also the qos
}

// this should actually be in another interface but since it is an only method we put it here.
const MsgQueueEntry& SubscribeQueueEntry::send(I_ConnectionsHandler& connectionsHandler) const
{
   if (log_.call()) log_.call(ME, "send");
   if (statusQosData_) {
      delete statusQosData_;
      statusQosData_ = NULL;
   }
   if (log_.dump()) log_.dump(ME, string("send: ") + SubscribeQueueEntry::toXml());
   statusQosData_ = new StatusQosData(connectionsHandler.getConnection().subscribe(SubscribeKey(global_, *queryKeyData_), SubscribeQos(global_, *queryQosData_)).getData());
   return *this;
}

size_t SubscribeQueueEntry::getSizeInBytes() const
{
   size_t sum = 0;
   if (queryQosData_     != NULL) sum += sizeof(*queryQosData_);
   if (queryKeyData_     != NULL) sum += sizeof(*queryKeyData_);
   return sum;
}

SubscribeQos SubscribeQueueEntry::getSubscribeQos() const
{
   return SubscribeQos(global_, *queryQosData_);
}

SubscribeKey SubscribeQueueEntry::getSubscribeKey() const
{
   return SubscribeKey(global_, *queryKeyData_);
}

SubscribeReturnQos SubscribeQueueEntry::getSubscribeReturnQos() const
{
   return SubscribeReturnQos(global_, *statusQosData_);
}

string SubscribeQueueEntry::toXml(const string& indent) const
{
   string extraOffset = "   " + indent;
   string ret = indent + "<subscribeQueueEntry>\n" + 
                extraOffset + queryKeyData_->toXml("  ") +
                extraOffset + queryQosData_->toXml("  ") +
                indent + "</subscribeQueueEntry>\n";
   return ret;
}


}}}} // namespace


