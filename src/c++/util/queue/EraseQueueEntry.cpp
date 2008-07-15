/*------------------------------------------------------------------------------
Name:      EraseQueueEntry.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/EraseQueueEntry.h>
#include <util/dispatch/I_ConnectionsHandler.h>
#include <cstring> // memset()

namespace org { namespace xmlBlaster { namespace util { namespace queue {

using namespace std;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::key;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

EraseQueueEntry::EraseQueueEntry(Global& global, const EraseKey& eraseKey,
     const EraseQos& eraseQos, int priority, Timestamp uniqueId)
   : MsgQueueEntry(global, eraseKey.getData(), eraseQos.getData(), 
     org::xmlBlaster::util::Constants::ENTRY_TYPE_MSG_RAW + "|" + org::xmlBlaster::util::MethodName::ERASE,
     priority,
     eraseQos.getData().isPersistent(),
     uniqueId)
{
   ME = "EraseQueueEntry";
}

EraseQueueEntry::~EraseQueueEntry() {
}

/** copy constructor */
EraseQueueEntry::EraseQueueEntry(const EraseQueueEntry& rhs)
    //: MsgQueueEntry((MsgQueueEntry)rhs)
    : MsgQueueEntry(rhs.getGlobal(), rhs.getMsgUnit(), rhs.getEmbeddedType(), rhs.getPriority(), rhs.isPersistent(), rhs.getUniqueId())
{
   memset(&blobHolder_, 0, sizeof(BlobHolder)); // reset cache
}

/** assignment constructor */
EraseQueueEntry& EraseQueueEntry::operator=(const EraseQueueEntry& rhs)
{
   if (this == &rhs)
      return *this;
   memset(&blobHolder_, 0, sizeof(BlobHolder)); // reset cache
   return *this;
}

MsgQueueEntry *EraseQueueEntry::getClone() const
{
   return new EraseQueueEntry(*this);
}

bool EraseQueueEntry::isErase() const {
        return true;
}

// this should actually be in another interface but since it is an only method we put it here.
const MsgQueueEntry& EraseQueueEntry::send(I_ConnectionsHandler& connectionsHandler) const
{
   if (log_.call()) log_.call(ME, "send");
   if (statusQosData_) {
      delete statusQosData_;
      statusQosData_ = NULL;
   }

   log_.error(ME, string("send() return is not implemented: ") + EraseQueueEntry::toXml());
   /*
   // ??? TODO:
   vector<EraseReturnQos> 
   */
  //statusQosData_ = new StatusQosData(
        connectionsHandler.getConnection().erase(getEraseKey(), getEraseQos());
    //.getData());
   return *this;
}

EraseQos EraseQueueEntry::getEraseQos() const
{
   const QueryQosData *qos = dynamic_cast<const QueryQosData *>(&msgUnit_->getQos());
   return EraseQos(global_, *qos);
}

EraseKey EraseQueueEntry::getEraseKey() const
{
   const QueryKeyData *key = dynamic_cast<const QueryKeyData *>(&msgUnit_->getKey());
   return EraseKey(global_, *key);
}

EraseReturnQos EraseQueueEntry::getEraseReturnQos() const
{
   return EraseReturnQos(global_, *statusQosData_);
}

string EraseQueueEntry::toXml(const string& indent) const
{
   string extraOffset = "   " + indent;
   string ret = indent + "<eraseQueueEntry>\n";
   if (msgUnit_) {
      ret += extraOffset + msgUnit_->toXml(indent);
   }
   ret += indent + "</eraseQueueEntry>\n";
   return ret;
}


}}}} // namespace


