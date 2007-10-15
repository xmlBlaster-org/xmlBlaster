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
using namespace org::xmlBlaster::util::key;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

SubscribeQueueEntry::SubscribeQueueEntry(Global& global, const SubscribeKey& subscribeKey,
     const SubscribeQos& subscribeQos, int priority, Timestamp uniqueId)
   : MsgQueueEntry(global, subscribeKey.getData(), subscribeQos.getData(), 
     org::xmlBlaster::util::Constants::ENTRY_TYPE_MSG_RAW + "|" + org::xmlBlaster::util::MethodName::SUBSCRIBE,
     priority,
     false, // subscribeQos.getData().isPersistent(), TODO: first implement retrieval before we can put it in !!!
     uniqueId)
{
   ME = "SubscribeQueueEntry";
}

SubscribeQueueEntry::~SubscribeQueueEntry() {
}

/** copy constructor */
SubscribeQueueEntry::SubscribeQueueEntry(const SubscribeQueueEntry& rhs)
    //: MsgQueueEntry((MsgQueueEntry)rhs)
    : MsgQueueEntry(rhs.getGlobal(), rhs.getMsgUnit(), rhs.getEmbeddedType(), rhs.getPriority(), rhs.isPersistent(), rhs.getUniqueId())
{
   memset(&blobHolder_, 0, sizeof(BlobHolder)); // reset cache
}

/** assignment constructor */
SubscribeQueueEntry& SubscribeQueueEntry::operator=(const SubscribeQueueEntry& rhs)
{
   if (this == &rhs)
      return *this;
   memset(&blobHolder_, 0, sizeof(BlobHolder)); // reset cache
   return *this;
}

MsgQueueEntry *SubscribeQueueEntry::getClone() const
{
   return new SubscribeQueueEntry(*this);
}

bool SubscribeQueueEntry::isSubscribe() const {
	return true;
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
   statusQosData_ = new StatusQosData(
        connectionsHandler.getConnection().subscribe(
            getSubscribeKey(), getSubscribeQos()).getData());
   return *this;
}

SubscribeQos SubscribeQueueEntry::getSubscribeQos() const
{
   const QueryQosData *qos = dynamic_cast<const QueryQosData *>(&msgUnit_->getQos());
   return SubscribeQos(global_, *qos);
}

SubscribeKey SubscribeQueueEntry::getSubscribeKey() const
{
   const QueryKeyData *key = dynamic_cast<const QueryKeyData *>(&msgUnit_->getKey());
   return SubscribeKey(global_, *key);
}

SubscribeReturnQos SubscribeQueueEntry::getSubscribeReturnQos() const
{
   return SubscribeReturnQos(global_, *statusQosData_);
}

string SubscribeQueueEntry::toXml(const string& indent) const
{
   string extraOffset = "   " + indent;
   string ret = indent + "<subscribeQueueEntry>\n";
   if (msgUnit_) {
      ret += extraOffset + msgUnit_->toXml(indent);
   }
   ret += indent + "</subscribeQueueEntry>\n";
   return ret;
}


}}}} // namespace


