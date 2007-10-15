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
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::key;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

UnSubscribeQueueEntry::UnSubscribeQueueEntry(Global& global, const UnSubscribeKey& unSubscribeKey, const UnSubscribeQos& unSubscribeQos, int priority, Timestamp uniqueId)
   : MsgQueueEntry(global, unSubscribeKey.getData(), unSubscribeQos.getData(),
                   org::xmlBlaster::util::Constants::ENTRY_TYPE_MSG_RAW + "|" + org::xmlBlaster::util::MethodName::UNSUBSCRIBE,
                   priority,
                   unSubscribeQos.getData().isPersistent(),
                   uniqueId)
{
   ME = "UnSubscribeQueueEntry";
}

MsgQueueEntry *UnSubscribeQueueEntry::getClone() const
{
   return new UnSubscribeQueueEntry(*this);
}

bool UnSubscribeQueueEntry::isUnSubscribe() const {
	return true;
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
   connectionsHandler.getConnection().unSubscribe(getUnSubscribeKey(), getUnSubscribeQos());

   return *this;
}

UnSubscribeQos UnSubscribeQueueEntry::getUnSubscribeQos() const
{
   const QueryQosData *qos = dynamic_cast<const QueryQosData *>(&msgUnit_->getQos());
   return UnSubscribeQos(global_, *qos);
}

UnSubscribeKey UnSubscribeQueueEntry::getUnSubscribeKey() const
{
   const QueryKeyData *key = dynamic_cast<const QueryKeyData *>(&msgUnit_->getKey());
   return UnSubscribeKey(global_, *key);
}

UnSubscribeReturnQos UnSubscribeQueueEntry::getUnSubscribeReturnQos() const
{
   return UnSubscribeReturnQos(global_, *statusQosData_);
}


string UnSubscribeQueueEntry::toXml(const string& indent) const
{
   string extraOffset = "   " + indent;
   string ret = indent + "<unSubscribeQueueEntry>\n";
   if (msgUnit_) {
      ret += extraOffset + msgUnit_->toXml(indent);
   }
   ret += indent + "</unSubscribeQueueEntry>\n";
   return ret;
}

}}}} // namespace


