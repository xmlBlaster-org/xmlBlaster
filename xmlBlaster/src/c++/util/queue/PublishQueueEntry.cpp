/*------------------------------------------------------------------------------
Name:      PublishQueueEntry.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/PublishQueueEntry.h>

namespace org { namespace xmlBlaster { namespace util { namespace queue {

PublishQueueEntry::PublishQueueEntry(Global& global, const MessageUnit& msgUnit, const string& type, int priority, bool durable)
   : MsgQueueEntry(global, msgUnit, type, priority, durable)
{
   ME = "PublishQueueEntry";
   if (priority < 0) priority_ = msgUnit.getQos().getPriority();
}

void* PublishQueueEntry::getEmbeddedObject()
{
   return msgUnit_;
}

// this should actually be in another interface but since it is an only method we put it here.
MsgQueueEntry& PublishQueueEntry::send(I_XmlBlasterConnection& connection)
{
   if (log_.CALL) log_.call(ME, "send");
   if (publishReturnQos_) {
      delete publishReturnQos_;
      publishReturnQos_ = NULL;
   }
   if (log_.DUMP) log_.dump(ME, string("send: ") + PublishQueueEntry::toXml());
   publishReturnQos_ = new PublishReturnQos(connection.publish(*msgUnit_));
   return *this;
}

MessageUnit PublishQueueEntry::getMsgUnit() const 
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


