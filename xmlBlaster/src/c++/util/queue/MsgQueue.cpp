/*------------------------------------------------------------------------------
Name:      MsgQueue.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/MsgQueue.h>
#include <util/Global.h>
#include <util/XmlBlasterException.h>

using namespace std;
using namespace org::xmlBlaster::util;
using org::xmlBlaster::util::qos::storage::QueueProperty;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

MsgQueue::MsgQueue(Global& global, const QueueProperty& property)
   : ME("MsgQueue"), global_(global), log_(global.getLog("queue")), property_(property), storage_(), accessMutex_()
{
   numOfBytes_ = 0;
}


MsgQueue::MsgQueue(const MsgQueue& queue)
   : ME("MsgQueue"), 
     global_(queue.global_), 
     log_(queue.log_), 
     property_(queue.property_), 
     storage_(queue.storage_), 
     accessMutex_()
{
   numOfBytes_ = queue.numOfBytes_;
}

MsgQueue& MsgQueue::operator =(const MsgQueue& queue)
{
   Lock lock(queue.accessMutex_);
   property_   = queue.property_;
   storage_    = queue.storage_;
   numOfBytes_ = queue.numOfBytes_;
   return *this;

}

MsgQueue::~MsgQueue()
{
   if (!storage_.empty()) {
      Lock lock(accessMutex_);
      storage_.erase(storage_.begin(), storage_.end());
   }
} 


void MsgQueue::put(MsgQueueEntry *entry)
{
   Lock lock(accessMutex_);
   if (!entry) throw XmlBlasterException(INTERNAL_NULLPOINTER, ME + "::put", "the entry is NULL");
   if (numOfBytes_+entry->getSizeInBytes() > ((size_t)property_.getMaxBytes()) ) {
      throw XmlBlasterException(RESOURCE_OVERFLOW_QUEUE_BYTES, ME + "::put", "client queue");
   }

   if (storage_.size() >= property_.getMaxMsg() ) {
      throw XmlBlasterException(RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME + "::put", "client queue");
   }
   try {
      EntryType help(*entry);
      storage_.insert(help);
      numOfBytes_ += entry->getSizeInBytes();
      // add the sizeInBytes_ here ...
   }
   catch (exception& ex) {
      throw XmlBlasterException(INTERNAL_UNKNOWN, ME + "::put", ex.what());
   }	  
   catch (...) {
      throw XmlBlasterException(INTERNAL_UNKNOWN, ME + "::put", "the original type of this exception is unknown");
   }
}


void MsgQueue::put(const PublishQueueEntry& entry)
{
   PublishQueueEntry *ptr = new PublishQueueEntry(entry);
   put(ptr);
}

void MsgQueue::put(const ConnectQueueEntry& entry)
{
   ConnectQueueEntry *ptr = new ConnectQueueEntry(entry);
   put(ptr);
}

void MsgQueue::put(const SubscribeQueueEntry& entry)
{
   SubscribeQueueEntry *ptr = new SubscribeQueueEntry(entry);
   put(ptr);
}

void MsgQueue::put(const UnSubscribeQueueEntry& entry)
{
   UnSubscribeQueueEntry *ptr = new UnSubscribeQueueEntry(entry);
   put(ptr);
}


vector<EntryType> MsgQueue::peekWithSamePriority(long maxNumOfEntries, long maxNumOfBytes) const
{
   Lock lock(accessMutex_);
   vector<EntryType> ret;
   if (storage_.empty()) return ret;
   StorageType::const_iterator iter = storage_.begin();
   long numOfEntries = 0;
   long numOfBytes = 0;
   int referencePriority = (**iter).getPriority();
   while (iter != storage_.end()) {
      numOfBytes += (**iter).getSizeInBytes();
      numOfEntries++;
      if (numOfBytes > maxNumOfBytes && maxNumOfBytes > -1) break;
      if (numOfEntries > maxNumOfEntries && maxNumOfBytes > -1) break;
      if ((**iter).getPriority() != referencePriority ) break;
      EntryType entry = (*iter);
      ret.insert(ret.end(), entry); 
      iter++;
   }
   return ret;
}


long MsgQueue::randomRemove(vector<EntryType>::const_iterator start, vector<EntryType>::const_iterator end) 
{
   Lock lock(accessMutex_);
   if (start == end || storage_.empty()) return 0;
   vector<EntryType>::const_iterator iter = start;
   long count = 0;
   while (iter != end) {
      long entrySize = (*iter)->getSizeInBytes();
      if (storage_.empty()) return 0;
      int help = storage_.erase(*iter);
      if (help > 0) {
         count += help;
         numOfBytes_ -= help * entrySize;
      }
      iter++;
   }
   return count;
}

void MsgQueue::clear()
{
   Lock lock(accessMutex_);
   storage_.erase(storage_.begin(), storage_.end());
   numOfBytes_ = 0;
}


bool MsgQueue::empty() const
{
   return storage_.empty();
}


}}}} // namespace



#ifdef _XMLBLASTER_CLASSTEST

#include <util/qos/ConnectQos.h>
#include <util/queue/ConnectQueueEntry.h>

using org::xmlBlaster::util::qos::ConnectQos;
using org::xmlBlaster::util::queue::ConnectQueueEntry;

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::queue;

int main(int args, char* argv[])
{

   Global& global = Global::getInstance();
   global.initialize(args, argv);
   Log& log = global.getLog("main");
   
   QueueProperty property(global, "");
   log.info("main", string("queue property: ") + property.toXml());

   ConnectQos qos(global);
   ConnectQueueEntry* entry = new ConnectQueueEntry(qos);

   {
      MsgQueue queue(global, property);
      queue.put(entry);
      vector<EntryType> vec = queue.peekWithSamePriority();
//      ConnectQueueEntry pub(*vec[0]);
//      cout << pub.getConnectQos().toXml() << endl;
      cout << (vec[0])->onlyForTesting() << endl;

   }

   return 0;
}

#endif




