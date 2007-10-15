/*------------------------------------------------------------------------------
Name:      PublishQueueEntry.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/PublishQueueEntry.h>
#include <util/dispatch/I_ConnectionsHandler.h>
#include <util/msgUtil.h> // from xmlBlaster C library
#include <socket/xmlBlasterSocket.h> // from xmlBlaster C library ::encodeMsgUnit(&msgUnit, debug);

namespace org { namespace xmlBlaster { namespace util { namespace queue {

using namespace std;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

PublishQueueEntry::PublishQueueEntry(Global& global, const MessageUnit& msgUnit,
                                     int priority, Timestamp uniqueId)
   : MsgQueueEntry(global, msgUnit, 
                   org::xmlBlaster::util::Constants::ENTRY_TYPE_MSG_RAW + "|" + org::xmlBlaster::util::MethodName::PUBLISH,
                   priority, msgUnit.getQos().isPersistent(), uniqueId)
{
   ME = "PublishQueueEntry";
   if (log_.call()) log_.call(ME, "ctor ...");
   if (priority < 0) priority_ = msgUnit.getQos().getPriority();
}

PublishQueueEntry::~PublishQueueEntry() {
}

/** copy constructor */
PublishQueueEntry::PublishQueueEntry(const PublishQueueEntry& rhs)
    //: MsgQueueEntry((MsgQueueEntry)rhs)
    : MsgQueueEntry(rhs.getGlobal(), rhs.getMsgUnit(), rhs.getEmbeddedType(), rhs.getPriority(), rhs.isPersistent(), rhs.getUniqueId())
{
}

/** assignment constructor */
PublishQueueEntry& PublishQueueEntry::operator=(const PublishQueueEntry& rhs)
{
   if (this == &rhs)
      return *this;
   return *this;
}

MsgQueueEntry *PublishQueueEntry::getClone() const
{
   return new PublishQueueEntry(*this);
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

const PublishReturnQos* PublishQueueEntry::getPublishReturnQos() const
{
   return publishReturnQos_;
}

bool PublishQueueEntry::isPublish() const {
	return true;
}

size_t PublishQueueEntry::getSizeInBytes() const
{
   if (msgUnit_) return msgUnit_->getSizeInBytes();
   return 0;
}

string PublishQueueEntry::toXml(const string& indent) const
{
   string extraOffset = "   " + indent;
   string ret = indent + "<publishQueueEntry>\n";
   if (msgUnit_) {
      ret += extraOffset + msgUnit_->toXml(indent);
   }
   ret += indent + "</publishQueueEntry>\n";
   return ret;
}

}}}} // namespace


