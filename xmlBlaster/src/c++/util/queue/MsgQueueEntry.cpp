/*------------------------------------------------------------------------------
Name:      MsgQueueEntry.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/MsgQueueEntry.h>
#include <util/dispatch/I_ConnectionsHandler.h>
#include <util/Global.h>
#include <util/lexical_cast.h>
#include <cstddef> //<stddef.h>
#include <util/msgUtil.h> // from xmlBlaster C library
#include <socket/xmlBlasterSocket.h> // from xmlBlaster C library ::encodeMsgUnit(&msgUnit, debug);

/**
 * Class embedding messages or information to be stored on the client queues
 * Note that all content is copied when passed to the constructors.
 * This way this queue entry is the owner of the content (and therefore will
 * delete it when its destructor is called).
 *
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 */
namespace org { namespace xmlBlaster { namespace util { namespace queue {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::key;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::dispatch;

MsgQueueEntry::MsgQueueEntry(Global& global, const MessageUnit& msgUnit, const string& embeddedType, int priority, bool persistent, Timestamp uniqueId)
   : ReferenceCounterBase(), 
     ME("MsgQueueEntry"), 
     global_(global), 
     log_(global.getLog("org.xmlBlaster.util.queue")),
     connectQos_((ConnectQos*)0),
     connectReturnQos_((ConnectReturnQos*)0)
{
   publishReturnQos_ = NULL;
   msgUnit_          = new MessageUnit(msgUnit);
   statusQosData_    = NULL;
   uniqueId_         = uniqueId; //TimestampFactory::getInstance().getTimestamp();
   embeddedType_     = embeddedType;
   priority_         = priority; // should be normal priority
   persistent_       = persistent; // currently no persistents supported
   logId_            = embeddedType_ + string(":") + lexical_cast<std::string>(uniqueId_);
   memset(&blobHolder_, 0, sizeof(BlobHolder));
}

MsgQueueEntry::MsgQueueEntry(Global& global, const ConnectQosRef& connectQos, const string& embeddedType, int priority, bool persistent, Timestamp uniqueId)
   : ReferenceCounterBase(), ME("MsgQueueEntry"), global_(global), log_(global.getLog("org.xmlBlaster.util.queue")),
     connectQos_(*connectQos),  // OK to take a reference only???!!! Should we clone it so that RAM queue behaves same as persistent queue?
     connectReturnQos_((ConnectReturnQos*)0)
{
   msgUnit_          = NULL;
   publishReturnQos_ = NULL;
   statusQosData_    = NULL;
   uniqueId_         = uniqueId;
   embeddedType_     = embeddedType;
   priority_         = priority; // should be maximum priority
   persistent_       = persistent; // currently no persistents supported
   logId_            = embeddedType_ + string(":") + lexical_cast<std::string>(uniqueId_);
   memset(&blobHolder_, 0, sizeof(BlobHolder));
}


MsgQueueEntry::MsgQueueEntry(Global& global, const QueryKeyData& queryKeyData, const QueryQosData& queryQosData, const string& embeddedType, int priority, bool persistent, Timestamp uniqueId)
   : ReferenceCounterBase(), ME("MsgQueueEntry"), global_(global), log_(global.getLog("org.xmlBlaster.util.queue")),
     connectQos_((ConnectQos*)0),
     connectReturnQos_((ConnectReturnQos*)0)
{
   // The MessageUnit takes a copy of the passed queryKeyData and queryQosData:
   msgUnit_          = new MessageUnit(queryKeyData, string(""), queryQosData);
   publishReturnQos_ = NULL;
   statusQosData_    = NULL;
   uniqueId_         = uniqueId;
   embeddedType_     = embeddedType;
   priority_         = priority; // should be maximum priority
   persistent_          = persistent; // currently no persistents supported
   logId_            = embeddedType_ + string(":") + lexical_cast<std::string>(uniqueId_);
   memset(&blobHolder_, 0, sizeof(BlobHolder));
}

void MsgQueueEntry::copy(const MsgQueueEntry& entry)
{
   connectQos_ = new ConnectQos(*entry.connectQos_);

   if (msgUnit_ != NULL) {
      delete msgUnit_;
      msgUnit_ = NULL;
   }
   if (entry.msgUnit_ != NULL) msgUnit_ = new org::xmlBlaster::util::MessageUnit(*entry.msgUnit_);

   connectReturnQos_ = new ConnectReturnQos(*entry.connectReturnQos_);

   if (publishReturnQos_ != NULL) {
      delete publishReturnQos_;
      publishReturnQos_ = NULL; 
   }
   if (entry.publishReturnQos_ != NULL) 
      publishReturnQos_ = new org::xmlBlaster::client::qos::PublishReturnQos(*entry.publishReturnQos_);

   if (statusQosData_ != NULL) {
      delete statusQosData_;
      statusQosData_ = NULL; 
   }
   if (entry.statusQosData_ != NULL) 
      statusQosData_ = new org::xmlBlaster::util::qos::StatusQosData(*entry.statusQosData_);

   uniqueId_     = entry.uniqueId_;
   embeddedType_ = entry.embeddedType_;
   priority_     = entry.priority_;
   persistent_      = entry.persistent_;
   logId_        = logId_;
}


MsgQueueEntry::~MsgQueueEntry()
{
   delete msgUnit_;
   delete publishReturnQos_;
   delete statusQosData_;

   ::BlobHolder blob;
   blob.data    = blobHolder_.data;
   blob.dataLen = blobHolder_.dataLen;
   ::freeBlobHolderContent(&blob);
   memset(&blobHolder_, 0, sizeof(BlobHolder));
}

MsgQueueEntry::MsgQueueEntry(const MsgQueueEntry& entry)
   : ReferenceCounterBase(entry), ME(entry.ME), global_(entry.global_), log_(entry.log_),
     connectQos_((ConnectQos*)0),
     connectReturnQos_((ConnectReturnQos*)0)
{
   memset(&blobHolder_, 0, sizeof(BlobHolder)); // reset cache
   msgUnit_          = NULL;
   publishReturnQos_ = NULL;
   statusQosData_    = NULL;
   copy(entry);
}


MsgQueueEntry& MsgQueueEntry::operator =(const MsgQueueEntry& entry)
{
   ReferenceCounterBase::operator =(entry);
   memset(&blobHolder_, 0, sizeof(BlobHolder)); // reset cache
   copy(entry);
   return *this;
}

size_t MsgQueueEntry::getSizeInBytes() const
{
   if (msgUnit_) return msgUnit_->getSizeInBytes();
   return 0;
}

int MsgQueueEntry::getPriority() const
{
   return priority_;
}

bool MsgQueueEntry::isPersistent() const
{
   return persistent_;
}

void MsgQueueEntry::setSender(org::xmlBlaster::util::SessionNameRef sender)
{
   if (msgUnit_) {
      msgUnit_->getQos().setSender(sender);
   }
   //connectQos_
   //statusQosData_
}

Timestamp MsgQueueEntry::getUniqueId() const
{
   return uniqueId_;
}

string MsgQueueEntry::getLogId() const
{
   return logId_;
}

string MsgQueueEntry::getEmbeddedType() const
{
   return embeddedType_;
}

const void* MsgQueueEntry::getEmbeddedObject() const
{
   if (log_.call()) log_.call(ME, string("getEmbeddedObject(") + embeddedType_ + ") ...");
   if (msgUnit_ == 0) {
      log_.error(ME, "getEmbeddedObject() with msgUnit == NULL");
      return 0;
   }
   //if (embeddedType_ != (org::xmlBlaster::util::Constants::ENTRY_TYPE_MSG_RAW + "|" + org::xmlBlaster::util::MethodName::SUBSCRIBE)) // "MSG_RAW|subscribe"
   //   throw XmlBlasterException(INTERNAL_ILLEGALARGUMENT, ME + "getEmbeddedObject()", string("We only support embeddedType '") + org::xmlBlaster::util::Constants::ENTRY_TYPE_MSG_RAW + "|" + org::xmlBlaster::util::MethodName::SUBSCRIBE + "'");

   if (blobHolder_.data != 0) // Cached
      return &blobHolder_;

   log_.info(ME+".getEmbeddedObject("+ embeddedType_ +")", string("C++ msgUnit=")+msgUnit_->toXml());

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

string MsgQueueEntry::toXml(const string& /*indent*/) const
{
   return "<notImplemented/>\n";
}

const MsgQueueEntry& MsgQueueEntry::send(I_ConnectionsHandler&) const
{
   log_.error(ME, "send not implemented");
   return *this;
}

MessageUnit& MsgQueueEntry::getMsgUnit() const 
{
   return *msgUnit_;
}

}}}} // namespace

