/*------------------------------------------------------------------------------
Name:      PublishQueueEntry.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/PublishQueueEntry.h>
#include <util/dispatch/I_ConnectionsHandler.h>

namespace org { namespace xmlBlaster { namespace util { namespace queue {

using namespace std;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

PublishQueueEntry::PublishQueueEntry(Global& global, const MessageUnit& msgUnit, const string& type, int priority, bool persistent)
   : MsgQueueEntry(global, msgUnit, type, priority, persistent)
{
   ME = "PublishQueueEntry";
   if (priority < 0) priority_ = msgUnit.getQos().getPriority();
}

MsgQueueEntry *PublishQueueEntry::getClone() const
{
   return new PublishQueueEntry(*this);
}

void* PublishQueueEntry::getEmbeddedObject()
{
   return msgUnit_;
}

const MsgQueueEntry& PublishQueueEntry::send(I_ConnectionsHandler& connectionsHandler) const
{
   if (log_.call()) log_.call(ME, "send");
   if (publishReturnQos_) {
      delete publishReturnQos_;
      publishReturnQos_ = NULL;
   }
   if (log_.dump()) log_.dump(ME, string("send: ") + PublishQueueEntry::toXml());
   publishReturnQos_ = new PublishReturnQos(connectionsHandler.getConnection().publish(*msgUnit_));
   return *this;
}

MessageUnit& PublishQueueEntry::getMsgUnit() const 
{
   return *msgUnit_;
}

PublishReturnQos PublishQueueEntry::getPublishReturnQos() const
{
   return *publishReturnQos_;
}

string PublishQueueEntry::toXml(const string& indent) const
{
   string extraOffset = "   " + indent;
   string ret = indent + "<publishQueueEntry>\n" + 
                extraOffset + msgUnit_->toXml(indent) +
                indent + "</publishQueueEntry>\n";
   return ret;
}

}}}} // namespace


