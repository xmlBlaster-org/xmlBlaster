/*------------------------------------------------------------------------------
Name:      Queue.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/Queue.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos::storage;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

Queue::Queue(Global& global, const QueueProperty& property)
   : ME("Queue"), 
     global_(global), 
     log_(global.getLog("queue")), 
     property_(property), 
     storage_(), 
     accessMutex_()
{
   numOfBytes_ = 0;
}

Queue::Queue(const Queue& queue)
   : ME("Queue"), 
     global_(queue.global_), 
     log_(queue.log_), 
     property_(queue.property_), 
     storage_(queue.storage_), 
     accessMutex_()
{
   numOfBytes_ = queue.numOfBytes_;
}

Queue& Queue::operator =(const Queue& queue)
{
   Lock lock(queue.accessMutex_);
   property_   = queue.property_;
   storage_    = queue.storage_;
   numOfBytes_ = queue.numOfBytes_;
   return *this;

}

Queue::~Queue()
{
   if (log_.CALL) log_.call(ME, "destructor");
   if (!storage_.empty()) {
      Lock lock(accessMutex_);
      storage_.erase(storage_.begin(), storage_.end());
   }
} 

void Queue::put(MsgQueueEntry *entry)
{
   if (log_.CALL) log_.call(ME, "::put");
   if (log_.DUMP) log_.dump(ME, string("::put, the entry is: ")  + entry->toXml());

   Lock lock(accessMutex_);
   if (!entry) throw XmlBlasterException(INTERNAL_NULLPOINTER, ME + "::put", "the entry is NULL");
   if (numOfBytes_+entry->getSizeInBytes() > ((size_t)property_.getMaxBytes()) ) {
      throw XmlBlasterException(RESOURCE_OVERFLOW_QUEUE_BYTES, ME + "::put", "client queue");
   }

   if (storage_.size() >= (size_t)property_.getMaxMsg() ) {
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

vector<EntryType> Queue::peekWithSamePriority(long maxNumOfEntries, long maxNumOfBytes) const
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


long Queue::randomRemove(vector<EntryType>::const_iterator start, vector<EntryType>::const_iterator end) 
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

void Queue::clear()
{
   Lock lock(accessMutex_);
   storage_.erase(storage_.begin(), storage_.end());
   numOfBytes_ = 0;
}


bool Queue::empty() const
{
   return storage_.empty();
}


}}}} // namespace



