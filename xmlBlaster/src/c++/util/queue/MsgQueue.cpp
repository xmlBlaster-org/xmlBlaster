/*------------------------------------------------------------------------------
Name:      MsgQueue.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/MsgQueue.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos::storage;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

MsgQueue::MsgQueue(Global& global, const ClientQueueProperty& property)
   : Queue(global, property)
{
    ME = "MsgQueue";
}


MsgQueue::MsgQueue(const MsgQueue& queue)
   : Queue(queue)
{
   ME = "MsgQueue";
}

MsgQueue& MsgQueue::operator =(const MsgQueue& queue)
{
   Queue::operator =(queue);
   return *this;
}

void MsgQueue::put(const PublishQueueEntry& entry)
{
   PublishQueueEntry *ptr = new PublishQueueEntry(entry);
   try {
      Queue::put(ptr);
   }
   catch (const XmlBlasterException &ex) {
      delete ptr;
      throw ex;
   }
}

void MsgQueue::put(const ConnectQueueEntry& entry)
{
   ConnectQueueEntry *ptr = new ConnectQueueEntry(entry);
   try {
      Queue::put(ptr);
   }
   catch (const XmlBlasterException &ex) {
      delete ptr;
      throw ex;
   }
}

void MsgQueue::put(const SubscribeQueueEntry& entry)
{
   SubscribeQueueEntry *ptr = new SubscribeQueueEntry(entry);
   try {
      Queue::put(ptr);
   }
   catch (const XmlBlasterException &ex) {
      delete ptr;
      throw ex;
   }
}

void MsgQueue::put(const UnSubscribeQueueEntry& entry)
{
   UnSubscribeQueueEntry *ptr = new UnSubscribeQueueEntry(entry);
   try {
      Queue::put(ptr);
   }
   catch (const XmlBlasterException &ex) {
      delete ptr;
      throw ex;
   }
}


}}}} // namespace

