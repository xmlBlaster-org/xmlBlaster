/*------------------------------------------------------------------------------
Name:      UnSubscribeQueueEntry.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/UnSubscribeQueueEntry.h>

namespace org { namespace xmlBlaster { namespace util { namespace queue {

UnSubscribeQueueEntry::UnSubscribeQueueEntry(Global& global, const UnSubscribeKey& unSubscribeKey, const UnSubscribeQos& unSubscribeQos, const string& type, int priority, bool durable)
   : MsgQueueEntry(global, unSubscribeKey.getData(), unSubscribeQos.getData(), type, priority, durable)
{
   ME = "UnSubscribeQueueEntry";
}

void* UnSubscribeQueueEntry::getEmbeddedObject()
{
   return queryKeyData_; // actually not used now otherwise we would need to return also the qos
}

// this should actually be in another interface but since it is an only method we put it here.
MsgQueueEntry& UnSubscribeQueueEntry::send(I_XmlBlasterConnection& connection)
{
   if (statusQosData_) {
      delete statusQosData_;
      statusQosData_ = NULL;
   }
   // the return value is not stored ...
   connection.unSubscribe(UnSubscribeKey(global_, *queryKeyData_), UnSubscribeQos(global_, *queryQosData_));

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


}}}} // namespace


