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
   if (statusQosData_) {
      delete statusQosData_;
      statusQosData_ = NULL;
   }
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


}}}} // namespace


