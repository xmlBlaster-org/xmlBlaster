/*------------------------------------------------------------------------------
Name:      MsgQueueEntry.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/MsgQueueEntry.h>
#include <boost/lexical_cast.hpp>
using boost::lexical_cast;

using org::xmlBlaster::util::qos::ConnectQos;
using org::xmlBlaster::util::MessageUnit;

/**
 * Class embedding messages or information to be stored on the client queues
 * Note that all content is copied when passed to the constructors.
 * This way this queue entry is the owner of the content (and therefore will
 * delete it when its destructor is called).
 *
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 */
namespace org { namespace xmlBlaster { namespace util { namespace queue {

MsgQueueEntry::MsgQueueEntry(const MessageUnit& msgUnit, const string& type, int priority, bool durable)
{
   connectQos_     = NULL;
   msgUnit_        = new MessageUnit(msgUnit);
   embeddedObject_ = msgUnit_;

   uniqueId_       = TimestampFactory::getInstance().getTimestamp();
   embeddedType_   = type;
   priority_       = priority; // should be normal priority
   durable_        = durable; // currently no durables supported
   logId_          = embeddedType_ + string(":") + lexical_cast<string>(uniqueId_);
}

MsgQueueEntry::MsgQueueEntry(const ConnectQos& connectQos, const string& type, int priority, bool durable)
{
   connectQos_     = new ConnectQos(connectQos);
   msgUnit_        = NULL;
   embeddedObject_ = connectQos_;

   uniqueId_       = TimestampFactory::getInstance().getTimestamp();
   embeddedType_   = type;
   priority_       = priority; // should be maximum priority
   durable_        = durable; // currently no durables supported
   logId_          = embeddedType_ + string(":") + lexical_cast<string>(uniqueId_);
}

MsgQueueEntry::~MsgQueueEntry()
{
   delete connectQos_;
   delete msgUnit_;
}

MsgQueueEntry::MsgQueueEntry(const MsgQueueEntry& entry)
{
   connectQos_ = NULL;
   msgUnit_    = NULL;
   copy(entry);
}

MsgQueueEntry& MsgQueueEntry::operator =(const MsgQueueEntry& entry)
{
   copy(entry);
   return *this;
}

int MsgQueueEntry::getPriority() const
{
   return priority_;
}

bool MsgQueueEntry::isDurable() const
{
   return durable_;
}

long MsgQueueEntry::getUniqueId() const
{
   return uniqueId_;
}

void* MsgQueueEntry::getEmbeddedObject()
{
   return embeddedObject_;
}

string MsgQueueEntry::getEmbeddedType() const
{
   return embeddedType_;
}

string MsgQueueEntry::getLogId()
{
   return logId_;
}

size_t MsgQueueEntry::getSizeInBytes() const
{
   if (msgUnit_ != NULL) return sizeof(*msgUnit_);
   if (connectQos_ != NULL) return sizeof(*connectQos_);
   // otherwise throw an exception here ...
   return 0;
   //      return sizeof(*embeddedObject_);
}

}}}} // namespace
