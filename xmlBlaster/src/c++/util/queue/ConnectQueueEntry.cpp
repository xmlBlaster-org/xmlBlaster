/*------------------------------------------------------------------------------
Name:      ConnectQueueEntry.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/ConnectQueueEntry.h>

namespace org { namespace xmlBlaster { namespace util { namespace queue {

ConnectQueueEntry::ConnectQueueEntry(const ConnectQos& connectQos, const string& type, int priority, bool durable)
   : MsgQueueEntry(connectQos, type, priority, durable)
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
   if (connectReturnQos_) {
      delete connectReturnQos_;
      connectReturnQos_ = NULL;
   }
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

}}}} // namespace


