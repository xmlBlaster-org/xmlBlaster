/*------------------------------------------------------------------------------
Name:      ConnectQueueEntry.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/ConnectQueueEntry.h>
#include <util/dispatch/I_ConnectionsHandler.h>

namespace org { namespace xmlBlaster { namespace util { namespace queue {

using namespace std;
using namespace org::xmlBlaster::util::dispatch;

ConnectQueueEntry::ConnectQueueEntry(Global& global, const ConnectQos& connectQos, const string& type, int priority, bool persistent)
   : MsgQueueEntry(global, connectQos, type, priority, persistent)
{
   ME = "ConnectQueueEntry";
}

MsgQueueEntry *ConnectQueueEntry::getClone() const
{
   return new ConnectQueueEntry(*this);
}

const void* ConnectQueueEntry::getEmbeddedObject() const
{
   return msgUnit_;
}

const MsgQueueEntry& ConnectQueueEntry::send(I_ConnectionsHandler& connectionsHandler) const
{
   if (log_.call()) log_.call(ME, "send");
   if (connectReturnQos_) {
      delete connectReturnQos_;
      connectReturnQos_ = NULL;
   }
   if (log_.dump()) log_.dump(ME, string("send: ") + toXml());
   connectReturnQos_ = new ConnectReturnQos(connectionsHandler.connectRaw(*connectQos_));
//   connectionsHandler.setConnectReturnQos(*connectReturnQos_);
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


