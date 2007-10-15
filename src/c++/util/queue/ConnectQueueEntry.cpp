/*------------------------------------------------------------------------------
Name:      ConnectQueueEntry.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#include <util/queue/ConnectQueueEntry.h>
#include <util/dispatch/I_ConnectionsHandler.h>
#include <util/msgUtil.h> // from xmlBlaster C library
#include <socket/xmlBlasterSocket.h> // from xmlBlaster C library ::encodeMsgUnit(&msgUnit, debug);

namespace org { namespace xmlBlaster { namespace util { namespace queue {

using namespace std;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::dispatch;

ConnectQueueEntry::ConnectQueueEntry(Global& global, const ConnectQosRef& connectQos, int priority, Timestamp uniqueId)
   : MsgQueueEntry(global, connectQos,
                   org::xmlBlaster::util::Constants::ENTRY_TYPE_MSG_RAW + "|" + org::xmlBlaster::util::MethodName::CONNECT,
                   priority,
                   (connectQos.isNull() ? false : connectQos->isPersistent()),
                   uniqueId)
{
   ME = "ConnectQueueEntry";
   if (log_.call()) log_.call(ME, "ctor ...");
   memset(&blobHolder_, 0, sizeof(BlobHolder));
}

/** copy constructor */
ConnectQueueEntry::ConnectQueueEntry(const ConnectQueueEntry& rhs)
    : MsgQueueEntry(rhs.getGlobal(), rhs.getConnectQos(), rhs.getEmbeddedType(), rhs.getPriority(), rhs.isPersistent(), rhs.getUniqueId())
{
   memset(&blobHolder_, 0, sizeof(BlobHolder)); // reset cache
}

/** assignment constructor */
ConnectQueueEntry& ConnectQueueEntry::operator=(const ConnectQueueEntry& rhs)
{
   if (this == &rhs)
      return *this;
   memset(&blobHolder_, 0, sizeof(BlobHolder)); // reset cache
   return *this;
}

MsgQueueEntry *ConnectQueueEntry::getClone() const
{
   return new ConnectQueueEntry(*this);
}

ConnectQueueEntry::~ConnectQueueEntry() {
   ::BlobHolder blob;
   blob.data    = blobHolder_.data;
   blob.dataLen = blobHolder_.dataLen;
   ::freeBlobHolderContent(&blob);
   memset(&blobHolder_, 0, sizeof(BlobHolder));
}

bool ConnectQueueEntry::isConnect() const {
	return true;
}

const void* ConnectQueueEntry::getEmbeddedObject() const
{
   if (log_.call()) log_.call(ME, "getEmbeddedObject() ...");
   if (connectQos_.isNull()) {
      return 0;
   }
   if (embeddedType_ != (org::xmlBlaster::util::Constants::ENTRY_TYPE_MSG_RAW + "|" + org::xmlBlaster::util::MethodName::CONNECT)) // "MSG_RAW|connect"
      throw XmlBlasterException(INTERNAL_ILLEGALARGUMENT, ME + ".getEmbeddedObject()", string("We only support embeddedType '") + org::xmlBlaster::util::Constants::ENTRY_TYPE_MSG_RAW + "|" + org::xmlBlaster::util::MethodName::CONNECT + "', '" + embeddedType_ + "' is not known");

   if (blobHolder_.data != 0) // Cached
      return &blobHolder_;

   //log_.info(ME+".getEmbeddedObject()", string("C++ connectQos=")+connectQos_->toXml());

   // dump MsgQueueEntry->connectQos_ with SOCKET protocol into C ::MsgUnit
   ::MsgUnit mu;
   memset(&mu, 0, sizeof(::MsgUnit));
   mu.key = "";
   string qosXml = connectQos_->toXml();
   mu.qos = qosXml.c_str();

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
      log_.dump(ME+".getEmbeddedObject()", string("Putting connect entry into queue:") + p);
      ::freeBlobDump(p);
   }

   return &blobHolder_;
}

const MsgQueueEntry& ConnectQueueEntry::send(I_ConnectionsHandler& connectionsHandler) const
{
   if (log_.call()) log_.call(ME, "send");
   if (log_.dump()) log_.dump(ME, string("send: ") + toXml());
   connectReturnQos_ = connectionsHandler.connectRaw(connectQos_);
//   connectionsHandler.setConnectReturnQos(*connectReturnQos_);
   return *this;
}

size_t ConnectQueueEntry::getSizeInBytes() const
{
   if (!connectQos_.isNull()) {
      return sizeof(*connectQos_); // TODO: use toXml().size() ?
   }
   return 1024;
}

ConnectQosRef ConnectQueueEntry::getConnectQos() const
{
   return connectQos_;
}

ConnectReturnQosRef ConnectQueueEntry::getConnectReturnQos() const
{
   return connectReturnQos_;
}


string ConnectQueueEntry::toXml(const string& indent) const
{
   string extraOffset = "   " + indent;
   string ret = indent + "<connectQueueEntry>\n";
   if (!connectQos_.isNull()) {
      ret += extraOffset + connectQos_->toXml(indent);
   }
   ret += indent + "</connectQueueEntry>\n";
   return ret;
}

}}}} // namespace


