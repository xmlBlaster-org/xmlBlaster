/*------------------------------------------------------------------------------
Name:      ConnectQueueEntry.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/ConnectQueueEntry.h>

namespace org { namespace xmlBlaster { namespace util { namespace queue {

ConnectQueueEntry::ConnectQueueEntry(Global& global, const ConnectQos& connectQos, const string& type, int priority, bool persistent)
   : MsgQueueEntry(global, connectQos, type, priority, persistent)
{
   ME = "ConnectQueueEntry";
}

void* ConnectQueueEntry::getEmbeddedObject()
{
   return msgUnit_;
}

// this should actually be in another interface but since it is an only method we put it here.
MsgQueueEntry& ConnectQueueEntry::send(I_XmlBlasterConnection& connection)
{
   if (log_.CALL) log_.call(ME, "send");
   if (connectReturnQos_) {
      delete connectReturnQos_;
      connectReturnQos_ = NULL;
   }
   if (log_.DUMP) log_.dump(ME, string("send: ") + toXml());
   connectReturnQos_ = new ConnectReturnQos(connection.connect(*connectQos_));
   return *this;
}

ConnectQos ConnectQueueEntry::getConnectQos() const
{
   return *connectQos_;
}

ConnectReturnQos ConnectQueueEntry::getConnectReturnQos() const
{
   return *connectReturnQos_;
}


string ConnectQueueEntry::toXml(const string& indent) const
{
   string extraOffset = "   " + indent;
   string ret = indent + "<connectQueueEntry>\n" +
          extraOffset + connectQos_->toXml(indent) +
          indent + "</connectQueueEntry>\n";
   return ret;
}


}}}} // namespace


