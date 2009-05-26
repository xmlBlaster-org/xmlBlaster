/*------------------------------------------------------------------------------
Name:      CacheQueuePlugin.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#include <util/queue/CacheQueuePlugin.h>
#include <util/queue/QueueFactory.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#if defined (XMLBLASTER_PERSISTENT_QUEUE) || defined (XMLBLASTER_PERSISTENT_QUEUE_SQLITE3) // to compile on Windows
#  include <util/queue/SQLiteQueuePlugin.h> // temporary for usage -> remove again
#endif

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::util::qos::storage;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

CacheQueuePlugin::CacheQueuePlugin(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::qos::storage::ClientQueueProperty& property)
   : ME("CacheQueuePlugin"), 
     global_(global), 
     log_(global.getLog("org.xmlBlaster.util.queue")), 
     property_(property), 
     transientQueueP_(0), 
     persistentQueueP_(0), 
     accessMutex_()
{
   // TODO: type/version should be set from outside!!!

   transientQueueP_ = &QueueFactory::getFactory().getPlugin(global_, property, "RAM", "1.0");

   try {
      persistentQueueP_ = &QueueFactory::getFactory().getPlugin(global_, property, "SQLite", "1.0");

      // Note: On startup we can only load the highest priority in a bulk, peekWithSamePriority() does not support to get all!
      reloadFromPersistentStore();
   }
   catch (const XmlBlasterException &e) {
      log_.warn(ME, "No persistent queue is available, we continue RAM based. Reason: " + e.getMessage());
   }
   log_.info(ME, "Created queue [" + getType() + "][" + getVersion() + "]");
}

/*
CacheQueuePlugin::CacheQueuePlugin(const CacheQueuePlugin& queue)
   : ME("CacheQueuePlugin"), 
     global_(queue.global_), 
     log_(queue.log_), 
     property_(queue.property_), 
     storage_(queue.storage_), 
     accessMutex_()
{
   numOfBytes_ = queue.numOfBytes_;
}

CacheQueuePlugin& CacheQueuePlugin::operator =(const CacheQueuePlugin& queue)
{
   Lock lock(queue.accessMutex_);
   property_   = queue.property_;
   storage_    = queue.storage_;
   numOfBytes_ = queue.numOfBytes_;
   return *this;

}
*/

CacheQueuePlugin::~CacheQueuePlugin()
{
   if (log_.call()) log_.call(ME, "destructor");
   QueueFactory::getFactory().releasePlugin(transientQueueP_);
   if (persistentQueueP_) QueueFactory::getFactory().releasePlugin(persistentQueueP_);
} 

void CacheQueuePlugin::put(const MsgQueueEntry &entry)
{
   if (log_.call()) log_.call(ME, "::put");

   Lock lock(accessMutex_);
   transientQueueP_->put(entry);
   if (persistentQueueP_) {
      if (entry.isPersistent()) {
         try {
           persistentQueueP_->put(entry);
         }
         catch (const XmlBlasterException &e) {
            log_.warn(ME, "Ignoring problem to put entry into persistent queue, we are handling it transient: " + e.getMessage());
         }
      }
   }
}

long CacheQueuePlugin::reloadFromPersistentStore() const
{
   if (persistentQueueP_ && transientQueueP_->getNumOfEntries() == 0 && persistentQueueP_->getNumOfEntries() > 0) {
      // On startup shuffle them to the transient queue (only the highest priority is accessible with our I_Queue API)
      const vector<EntryType> vec = persistentQueueP_->peekWithSamePriority(-1, -1);
      long count = 0;
      vector<EntryType>::const_iterator iter = vec.begin();
      for (; iter != vec.end(); ++iter) {
         const EntryType &entryType = (*iter);
         transientQueueP_->put(*entryType);
         count++;
      }
      return count;
   }
   return 0;
}

const vector<EntryType> CacheQueuePlugin::peekWithSamePriority(long maxNumOfEntries, long maxNumOfBytes) const
{
   Lock lock(accessMutex_);
   vector<EntryType> vec = transientQueueP_->peekWithSamePriority(maxNumOfEntries, maxNumOfBytes);

   if (vec.size() == 0) {
      long count = reloadFromPersistentStore();
      if (count > 0) {
         return transientQueueP_->peekWithSamePriority(maxNumOfEntries, maxNumOfBytes);
      }
   }

   return vec;
}


long CacheQueuePlugin::randomRemove(const vector<EntryType>::const_iterator &start, const vector<EntryType>::const_iterator &end) 
{
   Lock lock(accessMutex_);
   long count = transientQueueP_->randomRemove(start, end);

   if (persistentQueueP_) {
      vector<EntryType> persistents;
      vector<EntryType>::const_iterator iter = start;
      while (iter != end) {
         const EntryType &entryType = (*iter);
         if (entryType->isPersistent()) {
            persistents.push_back(entryType);
         }
         iter++;
      }
      try {
         persistentQueueP_->randomRemove(persistents.begin(), persistents.end());
      }
      catch (const XmlBlasterException &e) {
         log_.warn(ME, "Ignoring problem to remove entry from persistent queue, we remove it from the transient queue only: " + e.getMessage());
      }
   }
   return count;
}

long CacheQueuePlugin::getNumOfEntries() const
{
   return transientQueueP_->getNumOfEntries();
}

long CacheQueuePlugin::getMaxNumOfEntries() const
{
   return transientQueueP_->getMaxNumOfEntries();
}

int64_t CacheQueuePlugin::getNumOfBytes() const
{
   return transientQueueP_->getNumOfBytes();
}

int64_t CacheQueuePlugin::getMaxNumOfBytes() const
{
   return transientQueueP_->getMaxNumOfBytes();
}

void CacheQueuePlugin::clear()
{
   Lock lock(accessMutex_);
   transientQueueP_->clear();
   if (persistentQueueP_) {
      try {
         persistentQueueP_->clear();
      }
      catch (const XmlBlasterException &e) {
         log_.warn(ME, "Ignoring problem to put entry into persistent queue, we are handling it transient: " + e.getMessage());
      }
   }
}

bool CacheQueuePlugin::empty() const
{
   return transientQueueP_->empty();
}

void CacheQueuePlugin::destroy()
{
   transientQueueP_->destroy();
   if (persistentQueueP_) {
      try {
         persistentQueueP_->destroy();
      }
      catch (const XmlBlasterException &e) {
         log_.warn(ME, "Ignoring problem to destroy the persistent queue: " + e.getMessage());
      }
   }
}

string CacheQueuePlugin::usage()
{
   std::string text = string("");
   text += string("\nThe CACHE queue plugin configuration:");
#if defined (XMLBLASTER_PERSISTENT_QUEUE) || defined (XMLBLASTER_PERSISTENT_QUEUE_SQLITE3) // to compile on Windows
   text += SQLiteQueuePlugin::usage();   // TODO: depending on persistency
#else
   text += ClientQueueProperty::usage();
#endif
   return text;
}
}}}} // namespace



