/*------------------------------------------------------------------------------
Name:      SubscribeQueueEntry.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/SubscribeQueueEntry.h>
#include <util/dispatch/I_ConnectionsHandler.h>
#include <util/msgUtil.h> // from xmlBlaster C library
#include <socket/xmlBlasterSocket.h> // from xmlBlaster C library ::encodeMsgUnit(&msgUnit, debug);

namespace org { namespace xmlBlaster { namespace util { namespace queue {

using namespace std;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

SubscribeQueueEntry::SubscribeQueueEntry(Global& global, const SubscribeKey& subscribeKey,
     const SubscribeQos& subscribeQos, int priority, Timestamp uniqueId)
   : MsgQueueEntry(global, subscribeKey.getData(), subscribeQos.getData(), 
     org::xmlBlaster::util::Constants::ENTRY_TYPE_MSG_RAW + "|" + org::xmlBlaster::util::MethodName::SUBSCRIBE,
     priority,
     subscribeQos.getData().isPersistent(),
     uniqueId)
{
   ME = "SubscribeQueueEntry";
   memset(&blobHolder_, 0, sizeof(BlobHolder));
}

SubscribeQueueEntry::~SubscribeQueueEntry() {
   ::BlobHolder blob;
   blob.data    = blobHolder_.data;
   blob.dataLen = blobHolder_.dataLen;
   ::freeBlobHolderContent(&blob);
   memset(&blobHolder_, 0, sizeof(BlobHolder));
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

const void* SubscribeQueueEntry::getEmbeddedObject() const
{
   if (log_.call()) log_.call(ME, "getEmbeddedObject() ...");
   if (msgUnit_ == 0) {
      return 0;
   }
   if (embeddedType_ != (org::xmlBlaster::util::Constants::ENTRY_TYPE_MSG_RAW + "|" + org::xmlBlaster::util::MethodName::SUBSCRIBE)) // "MSG_RAW|subscribe"
      throw XmlBlasterException(INTERNAL_ILLEGALARGUMENT, ME + "getEmbeddedObject()", string("We only support embeddedType '") + org::xmlBlaster::util::Constants::ENTRY_TYPE_MSG_RAW + "|" + org::xmlBlaster::util::MethodName::SUBSCRIBE + "'");

   if (blobHolder_.data != 0) // Cached
      return &blobHolder_;

   log_.info(ME+".getEmbeddedObject()", string("C++ msgUnit=")+msgUnit_->toXml());

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

   if (log_.dump()) {
      char *p = ::messageUnitToXmlLimited(&mu, 100);
      log_.dump(ME+".getEmbeddedObject()", string("C msgUnit:") + p);
      ::xmlBlasterFree(p);
   }

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
   //return queryKeyData_; // actually not used now otherwise we would need to return also the qos
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
   statusQosData_ = new StatusQosData(connectionsHandler.getConnection().subscribe(SubscribeKey(global_, *queryKeyData_), SubscribeQos(global_, *queryQosData_)).getData());
   return *this;
}

size_t SubscribeQueueEntry::getSizeInBytes() const
{
   size_t sum = 0;
   if (queryQosData_     != NULL) sum += sizeof(*queryQosData_);
   if (queryKeyData_     != NULL) sum += sizeof(*queryKeyData_);
   return sum;
}

SubscribeQos SubscribeQueueEntry::getSubscribeQos() const
{
   return SubscribeQos(global_, *queryQosData_);
}

SubscribeKey SubscribeQueueEntry::getSubscribeKey() const
{
   return SubscribeKey(global_, *queryKeyData_);
}

SubscribeReturnQos SubscribeQueueEntry::getSubscribeReturnQos() const
{
   return SubscribeReturnQos(global_, *statusQosData_);
}

string SubscribeQueueEntry::toXml(const string& indent) const
{
   string extraOffset = "   " + indent;
   string ret = indent + "<subscribeQueueEntry>\n" + 
                extraOffset + queryKeyData_->toXml("  ") +
                extraOffset + queryQosData_->toXml("  ") +
                indent + "</subscribeQueueEntry>\n";
   return ret;
}


}}}} // namespace


