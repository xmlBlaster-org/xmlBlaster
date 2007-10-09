/*------------------------------------------------------------------------------
Name:      RamQueuePlugin.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/queue/RamQueuePlugin.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::util::qos::storage;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

RamQueuePlugin::RamQueuePlugin(Global& global, const ClientQueueProperty& property)
   : ME("RamQueuePlugin"), 
     global_(global), 
     log_(global.getLog("org.xmlBlaster.util.queue")), 
     property_(property), 
     storage_(), 
     accessMutex_()
{
   numOfBytes_ = 0;
   log_.info(ME, "Created queue [" + getType() + "][" + getVersion() + "]");
}

RamQueuePlugin::RamQueuePlugin(const RamQueuePlugin& queue)
   : ME("RamQueuePlugin"), 
     global_(queue.global_), 
     log_(queue.log_), 
     property_(queue.property_), 
     storage_(queue.storage_), 
     accessMutex_()
{
   numOfBytes_ = queue.numOfBytes_;
}

RamQueuePlugin& RamQueuePlugin::operator =(const RamQueuePlugin& queue)
{
   Lock lock(queue.accessMutex_);
   property_   = queue.property_;
   storage_    = queue.storage_;
   numOfBytes_ = queue.numOfBytes_;
   return *this;

}

RamQueuePlugin::~RamQueuePlugin()
{
   if (log_.call()) log_.call(ME, "destructor");
   if (!storage_.empty()) {
      Lock lock(accessMutex_);
      storage_.erase(storage_.begin(), storage_.end());
   }
} 

void RamQueuePlugin::put(const MsgQueueEntry &entry)
{
   if (log_.call()) log_.call(ME, "::put");
   if (log_.dump()) log_.dump(ME, string("::put, the entry is: ")  + entry.toXml());

   Lock lock(accessMutex_);
   if (numOfBytes_+entry.getSizeInBytes() > ((size_t)property_.getMaxBytes()) ) {
      throw XmlBlasterException(RESOURCE_OVERFLOW_QUEUE_BYTES, ME + "::put", "client queue");
   }

   if (storage_.size() >= (size_t)property_.getMaxEntries() ) {
      throw XmlBlasterException(RESOURCE_OVERFLOW_QUEUE_ENTRIES, ME + "::put", "client queue");
   }
   try {
      const EntryType help(*entry.getClone());
      storage_.insert(help);
      numOfBytes_ += entry.getSizeInBytes();
      // add the sizeInBytes_ here ...
   }
   catch (exception& ex) {
      throw XmlBlasterException(INTERNAL_UNKNOWN, ME + "::put", ex.what());
   }      
   catch (...) {
      throw XmlBlasterException(INTERNAL_UNKNOWN, ME + "::put", "the original type of this exception is unknown");
   }
}

const vector<EntryType> RamQueuePlugin::peekWithSamePriority(long maxNumOfEntries, long maxNumOfBytes) const
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
      if (numOfEntries > maxNumOfEntries && maxNumOfEntries > -1) break;
      if ((**iter).getPriority() != referencePriority ) break;
      EntryType entry = (*iter);
      ret.insert(ret.end(), entry); 
      iter++;
   }
   return ret;
}


long RamQueuePlugin::randomRemove(const vector<EntryType>::const_iterator &start, const vector<EntryType>::const_iterator &end) 
{
   Lock lock(accessMutex_);
   if (start == end || storage_.empty()) return 0;
   vector<EntryType>::const_iterator iter = start;
   long count = 0;
   while (iter != end) {
      long entrySize = (*iter)->getSizeInBytes();
      if (storage_.empty()) return 0;
      string::size_type help = storage_.erase(*iter);
      if (help > 0) {
         count += help;
         numOfBytes_ -= help * entrySize;
      }
      iter++;
   }
   return count;
}

long RamQueuePlugin::getNumOfEntries() const
{
   return storage_.size();
}

long RamQueuePlugin::getMaxNumOfEntries() const
{
   return property_.getMaxEntries();
}

int64_t RamQueuePlugin::getNumOfBytes() const
{
   return numOfBytes_;
}

int64_t RamQueuePlugin::getMaxNumOfBytes() const
{
   return property_.getMaxBytes();
}

void RamQueuePlugin::clear()
{
   Lock lock(accessMutex_);
   storage_.erase(storage_.begin(), storage_.end());
   numOfBytes_ = 0;
}


bool RamQueuePlugin::empty() const
{
   return storage_.empty();
}


}}}} // namespace



