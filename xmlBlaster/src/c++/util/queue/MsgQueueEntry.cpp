/*------------------------------------------------------------------------------
Name:      MsgQueueEntry.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/MsgQueueEntry.h>
#include <util/dispatch/I_ConnectionsHandler.h>
#include <util/Global.h>
#include <util/lexical_cast.h>


using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;

/**
 * Class embedding messages or information to be stored on the client queues
 * Note that all content is copied when passed to the constructors.
 * This way this queue entry is the owner of the content (and therefore will
 * delete it when its destructor is called).
 *
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 */
namespace org { namespace xmlBlaster { namespace util { namespace queue {

MsgQueueEntry::MsgQueueEntry(Global& global, const MessageUnit& msgUnit, const string& type, int priority, bool persistent)
   : ReferenceCounterBase(), 
     ME("MsgQueueEntry"), 
     global_(global), 
     log_(global.getLog("queue"))
{
   connectQos_       = NULL;
   connectReturnQos_ = NULL;
   publishReturnQos_ = NULL;
   msgUnit_          = new MessageUnit(msgUnit);
   queryQosData_     = NULL;
   queryKeyData_     = NULL;
   statusQosData_    = NULL;
   uniqueId_         = TimestampFactory::getInstance().getTimestamp();
   embeddedType_     = type;
   priority_         = priority; // should be normal priority
   persistent_       = persistent; // currently no persistents supported
   logId_            = embeddedType_ + string(":") + lexical_cast<string>(uniqueId_);
}

MsgQueueEntry::MsgQueueEntry(Global& global, const ConnectQos& connectQos, const string& type, int priority, bool persistent)
   : ReferenceCounterBase(), ME("MsgQueueEntry"), global_(global), log_(global.getLog("queue"))
{
   connectQos_       = new ConnectQos(connectQos);
   msgUnit_          = NULL;
   connectReturnQos_ = NULL;
   publishReturnQos_ = NULL;
   queryQosData_     = NULL;
   queryKeyData_     = NULL;
   statusQosData_    = NULL;
   uniqueId_         = TimestampFactory::getInstance().getTimestamp();
   embeddedType_     = type;
   priority_         = priority; // should be maximum priority
   persistent_       = persistent; // currently no persistents supported
   logId_            = embeddedType_ + string(":") + lexical_cast<string>(uniqueId_);
}


MsgQueueEntry::MsgQueueEntry(Global& global, const QueryKeyData& queryKeyData, const QueryQosData& queryQosData, const string& type, int priority, bool persistent)
   : ReferenceCounterBase(), ME("MsgQueueEntry"), global_(global), log_(global.getLog("queue"))
{
   connectQos_       = NULL;
   msgUnit_          = NULL;
   connectReturnQos_ = NULL;
   publishReturnQos_ = NULL;
   queryQosData_     = new QueryQosData(queryQosData);
   queryKeyData_     = new QueryKeyData(queryKeyData);
   statusQosData_    = NULL;
   uniqueId_         = TimestampFactory::getInstance().getTimestamp();
   embeddedType_     = type;
   priority_         = priority; // should be maximum priority
   persistent_          = persistent; // currently no persistents supported
   logId_            = embeddedType_ + string(":") + lexical_cast<string>(uniqueId_);
}



MsgQueueEntry::~MsgQueueEntry()
{
   delete connectQos_;
   delete msgUnit_;
   delete publishReturnQos_;
   delete connectReturnQos_;
   delete queryQosData_;
   delete queryKeyData_;
   delete statusQosData_;
}

MsgQueueEntry::MsgQueueEntry(const MsgQueueEntry& entry)
   : ReferenceCounterBase(entry), ME(entry.ME), global_(entry.global_), log_(entry.log_)
{
   connectQos_       = NULL;
   msgUnit_          = NULL;
   connectReturnQos_ = NULL;
   publishReturnQos_ = NULL;
   queryQosData_     = NULL;
   queryKeyData_     = NULL;
   statusQosData_    = NULL;
   copy(entry);
}


MsgQueueEntry& MsgQueueEntry::operator =(const MsgQueueEntry& entry)
{
   ReferenceCounterBase::operator =(entry);
   copy(entry);
   return *this;
}
int MsgQueueEntry::getPriority() const
{
   return priority_;
}

bool MsgQueueEntry::isPersistent() const
{
   return persistent_;
}

Timestamp MsgQueueEntry::getUniqueId() const
{
   return uniqueId_;
}

string MsgQueueEntry::getLogId()
{
   return logId_;
}

size_t MsgQueueEntry::getSizeInBytes() const
{
   size_t sum = 0;
   if (msgUnit_          != NULL) sum += sizeof(*msgUnit_);
   if (connectQos_       != NULL) sum += sizeof(*connectQos_);
   if (queryQosData_     != NULL) sum += sizeof(*queryQosData_);
   if (queryKeyData_     != NULL) sum += sizeof(*queryKeyData_);
   return sum;
}

string MsgQueueEntry::getEmbeddedType() const
{
   return embeddedType_;
}

string MsgQueueEntry::toXml(const string& /*indent*/) const
{
   return "<notImplemented/>\n";
}

MsgQueueEntry& MsgQueueEntry::send(I_ConnectionsHandler&)
{
   log_.error(ME, "send not implemented");
   return *this;
}


}}}} // namespace

