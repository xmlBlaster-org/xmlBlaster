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
     log_(global.getLog("org.xmlBlaster.util.queue"))
{
   connectQos_       = NULL;
   connectReturnQos_ = NULL;
   publishReturnQos_ = NULL;
   msgUnit_          = new MessageUnit(msgUnit);
   queryQosData_     = NULL;
   queryKeyData_     = NULL;
   statusQosData_    = NULL;
   uniqueId_         = uniqueId; //TimestampFactory::getInstance().getTimestamp();
   embeddedType_     = embeddedType;
   priority_         = priority; // should be normal priority
   persistent_       = persistent; // currently no persistents supported
   logId_            = embeddedType_ + string(":") + lexical_cast<std::string>(uniqueId_);
}

MsgQueueEntry::MsgQueueEntry(Global& global, const ConnectQos& connectQos, const string& embeddedType, int priority, bool persistent, Timestamp uniqueId)
   : ReferenceCounterBase(), ME("MsgQueueEntry"), global_(global), log_(global.getLog("org.xmlBlaster.util.queue"))
{
   connectQos_       = new ConnectQos(connectQos);
   msgUnit_          = NULL;
   connectReturnQos_ = NULL;
   publishReturnQos_ = NULL;
   queryQosData_     = NULL;
   queryKeyData_     = NULL;
   statusQosData_    = NULL;
   uniqueId_         = uniqueId;
   embeddedType_     = embeddedType;
   priority_         = priority; // should be maximum priority
   persistent_       = persistent; // currently no persistents supported
   logId_            = embeddedType_ + string(":") + lexical_cast<std::string>(uniqueId_);
}


MsgQueueEntry::MsgQueueEntry(Global& global, const QueryKeyData& queryKeyData, const QueryQosData& queryQosData, const string& embeddedType, int priority, bool persistent, Timestamp uniqueId)
   : ReferenceCounterBase(), ME("MsgQueueEntry"), global_(global), log_(global.getLog("org.xmlBlaster.util.queue"))
{
   connectQos_       = NULL;
   msgUnit_          = NULL;
   connectReturnQos_ = NULL;
   publishReturnQos_ = NULL;
   queryQosData_     = new QueryQosData(queryQosData);
   queryKeyData_     = new QueryKeyData(queryKeyData);
   statusQosData_    = NULL;
   uniqueId_         = uniqueId;
   embeddedType_     = embeddedType;
   priority_         = priority; // should be maximum priority
   persistent_          = persistent; // currently no persistents supported
   logId_            = embeddedType_ + string(":") + lexical_cast<std::string>(uniqueId_);
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

void MsgQueueEntry::setSender(org::xmlBlaster::util::SessionNameRef sender)
{
   if (msgUnit_) {
      msgUnit_->getQos().setSender(sender);
   }
   //connectQos_
   //queryQosData_
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

