/*------------------------------------------------------------------------------
Name:      SubscribeQueueEntry.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/SubscribeQueueEntry.h>

namespace org { namespace xmlBlaster { namespace util { namespace queue {

SubscribeQueueEntry::SubscribeQueueEntry(Global& global, const SubscribeKey& subscribeKey, const SubscribeQos& subscribeQos, const string& type, int priority, bool durable)
   : MsgQueueEntry(global, subscribeKey.getData(), subscribeQos.getData(), type, priority, durable)
{
   ME = "SubscribeQueueEntry";
}

void* SubscribeQueueEntry::getEmbeddedObject()
{
   return queryKeyData_; // actually not used now otherwise we would need to return also the qos
}

// this should actually be in another interface but since it is an only method we put it here.
MsgQueueEntry& SubscribeQueueEntry::send(I_XmlBlasterConnection& connection)
{
   if (log_.CALL) log_.call(ME, "send");
   if (statusQosData_) {
      delete statusQosData_;
      statusQosData_ = NULL;
   }
   if (log_.DUMP) log_.dump(ME, string("send: ") + SubscribeQueueEntry::toXml());
   statusQosData_ = new StatusQosData(connection.subscribe(SubscribeKey(global_, *queryKeyData_), SubscribeQos(global_, *queryQosData_)).getData());
   return *this;
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


