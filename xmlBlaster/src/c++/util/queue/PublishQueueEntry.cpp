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

PublishQueueEntry::PublishQueueEntry(Global& global, const MessageUnit& msgUnit, const string& type, int priority, bool persistent, Timestamp uniqueId)
   : MsgQueueEntry(global, msgUnit, type, priority, persistent, uniqueId)
{
   ME = "PublishQueueEntry";
   if (log_.call()) log_.call(ME, "ctor ...");
   memset(&blobHolder_, 0, sizeof(BlobHolder));
   if (priority < 0) priority_ = msgUnit.getQos().getPriority();
}

PublishQueueEntry::~PublishQueueEntry() {
   ::BlobHolder blob;
   blob.data    = blobHolder_.data;
   blob.dataLen = blobHolder_.dataLen;
   ::freeBlobHolderContent(&blob);
   memset(&blobHolder_, 0, sizeof(BlobHolder));
}

/** copy constructor */
PublishQueueEntry::PublishQueueEntry(const PublishQueueEntry& rhs)
    //: MsgQueueEntry((MsgQueueEntry)rhs)
    : MsgQueueEntry(rhs.getGlobal(), rhs.getMsgUnit(), rhs.getEmbeddedType(), rhs.getPriority(), rhs.isPersistent(), rhs.getUniqueId())
{
   memset(&blobHolder_, 0, sizeof(BlobHolder)); // reset cache
}

/** assignment constructor */
PublishQueueEntry& PublishQueueEntry::operator=(const PublishQueueEntry& rhs)
{
   if (this == &rhs)
      return *this;
   memset(&blobHolder_, 0, sizeof(BlobHolder)); // reset cache
   return *this;
}

MsgQueueEntry *PublishQueueEntry::getClone() const
{
   return new PublishQueueEntry(*this);
}

/**
 * The serialized data with MSG_RAW (identical to the SOCKET protocol serialization). 
 * @return Returns a BlobHolder instance containing the serialized message (identical serialization as in SOCKET protocol)
 *         May return 0.
 */
const void* PublishQueueEntry::getEmbeddedObject() const
{
   if (log_.call()) log_.call(ME, "getEmbeddedObject() ...");
   if (msgUnit_ == 0) {
      return 0;
   }
   if (embeddedType_ != (org::xmlBlaster::util::Constants::ENTRY_TYPE_MSG_RAW + "|" + org::xmlBlaster::util::MethodName::PUBLISH)) // "MSG_RAW|publish"
      throw XmlBlasterException(INTERNAL_ILLEGALARGUMENT, ME + "getEmbeddedObject()", string("We only support embeddedType '") + org::xmlBlaster::util::Constants::ENTRY_TYPE_MSG_RAW + "|" + org::xmlBlaster::util::MethodName::PUBLISH + "'");

   if (blobHolder_.data != 0) // Cached
      return &blobHolder_;

   //log_.info(ME+".getEmbeddedObject()", string("C++ msgUnit=")+msgUnit_->toXml());

   // dump MsgQueueEntry->msgUnit_ with SOCKET protocol into C ::MsgUnit
   ::MsgUnit mu;
   memset(&mu, 0, sizeof(::MsgUnit));
   string keyXml = msgUnit_->getKey().toXml(); // We need the temporary string, as using .c_str() directly would lead to released memory of temporary string
   mu.key = keyXml.c_str();
   mu.contentLen = msgUnit_->getContentLen();
   mu.content = (char *)msgUnit_->getContent();
   string qosXml = msgUnit_->getQos().toXml();
   mu.qos = qosXml.c_str();
   mu.responseQos = (char*)0;

   /*
   if (log_.dump()) {
      char *p = ::messageUnitToXmlLimited(&mu, 100);
      log_.dump(ME+".getEmbeddedObject()", string("C msgUnit:") + p);
      ::xmlBlasterFree(p);
   }
   */

   // Serialize the message identical to the SOCKET protocol serialization
   // We use the functionality from our xmlBlaster C library
   ::BlobHolder blob = ::encodeMsgUnit(&mu, 0);

   blobHolder_.data = blob.data;
   blobHolder_.dataLen = blob.dataLen;

   if (log_.dump()) {
      char *p = ::blobDump(&blob);
      log_.dump(ME+".getEmbeddedObject()", string("Putting entry into queue:") + p);
      ::freeBlobDump(p);
   }

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

PublishReturnQos &PublishQueueEntry::getPublishReturnQos() const
{
   return *publishReturnQos_;
}

size_t PublishQueueEntry::getSizeInBytes() const
{
   if (msgUnit_) return msgUnit_->getSizeInBytes();
   return 0;
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


