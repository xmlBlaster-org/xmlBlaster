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

PublishQueueEntry::PublishQueueEntry(Global& global, const MessageUnit& msgUnit, const string& type, int priority, bool persistent)
   : MsgQueueEntry(global, msgUnit, type, priority, persistent)
{
   ME = "PublishQueueEntry";
   memset(&blobHolder_, 0, sizeof(BlobHolder));
   if (priority < 0) priority_ = msgUnit.getQos().getPriority();
}

PublishQueueEntry::~PublishQueueEntry() {
   delete blobHolder_.data;
}

MsgQueueEntry *PublishQueueEntry::getClone() const
{
   return new PublishQueueEntry(*this);
}

/**
 * @return Returns a BlobHolder instance containing the serialized message (identical serialization as in SOCKET protocol)
 *         May return 0.
 */
const void* PublishQueueEntry::getEmbeddedObject() const
{
   if (msgUnit_ == 0) {
      return 0;
   }
   // dump MsgQueueEntry->msgUnit_ with SOCKET protocol into C ::MsgUnit
   ::MsgUnit mu;
   mu.key = msgUnit_->getKey().toXml().c_str();
   mu.contentLen = msgUnit_->getContentLen();
   mu.content = (char *)msgUnit_->getContent();
   mu.qos = msgUnit_->getQos().toXml().c_str();
   mu.responseQos = (char*)0;

   // Serialize the message identical to the SOCKET protocol serialization
   // We use the functionality from our xmlBlaster C library
   ::BlobHolder blob = ::encodeMsgUnit(&mu, 0);
   memset(&blob, 0, sizeof(::BlobHolder));

   log_.error(ME, "TODO: Mismatch of malloc() in C and later free with delete in C++!!!");
   blobHolder_.data = blob.data;
   blobHolder_.dataLen = blob.dataLen;
   return &blobHolder_;
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


