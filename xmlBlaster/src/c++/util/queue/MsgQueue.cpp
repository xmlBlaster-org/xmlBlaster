/*------------------------------------------------------------------------------
Name:      MsgQueue.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/MsgQueue.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using org::xmlBlaster::util::qos::storage::QueueProperty;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

MsgQueue::MsgQueue(Global& global, const QueueProperty& property)
   : ME("MsgQueue"), global_(global), log_(global.getLog("queue")), property_(property), storage_()
{
   numOfBytes_ = 0;
}

MsgQueue::~MsgQueue()
{
   if (!storage_.empty()) {
      storage_.erase(storage_.begin(), storage_.end());
   }
} 

 
/**
 * puts a new entry into the queue. Note that this method takes the entry pointed to by the argument 
 * and puts a reference to it into the queue. This means that you can not destroy the entry before the
 * reference to it has been removed from the queue (which normally happens on a remove or when destroying
 * the queue.
 */
void MsgQueue::put(MsgQueueEntry *entry)
{
   if (!entry) {
      // throw an exception here
   }
   if (storage_.size() > ((size_t)property_.getMaxMsg()) ) {
     // throw an exception
   }
   if (numOfBytes_ > property_.getMaxBytes() ) {
   }
   try {
      storage_.insert(EntryType(*entry));
      numOfBytes_ += entry->getSizeInBytes();
      // add the sizeInBytes_ here ...
   }
   catch (...) {
      // throw an exception here
   }	  
}

/**
 * Returns the entries with the highest priority in the queue. If 'maxNumOfEntries' is positive,
 * this is the maximum number of entries to return. If maxNumOfBytes is positive, only the entries
 * which fit into the range specified are returned. If there are no such entries, an empty vector is
 * returned.
 */
vector<MsgQueueEntry*> MsgQueue::peekWithSamePriority(long maxNumOfEntries, long maxNumOfBytes) const
{
   vector<MsgQueueEntry*> ret;
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
      MsgQueueEntry* entry = &(**iter);
      ret.insert(ret.end(), entry); 
      iter++;
   }
   return ret;
}


/**
 * Deletes the entries specified in the vector in the argument list. If this vector is empty or if
 * the queue is empty, zero (0) is returned, otherwise it returns the number of entries really deleted.
 */
long MsgQueue::randomRemove(const vector<MsgQueueEntry*>& entries) 
{
   if (entries.empty() || storage_.empty()) return 0;
   vector<MsgQueueEntry*>::const_iterator iter = entries.begin();
   long count = 0;
   while (iter != entries.end()) {
      long entrySize = (*iter)->getSizeInBytes();
      if (storage_.empty()) return 0;
      EntryType el(**iter);
      int help = storage_.erase(el);
      if (help > 0) {
         count -= help;
         numOfBytes_ -= help * entrySize;
      }
      iter++;
   }
   return count;
}

}}}} // namespace


