/*------------------------------------------------------------------------------
Name:      SQLiteQueuePlugin.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/

#include <util/queue/SQLiteQueuePlugin.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::util::qos::storage;

static ::XmlBlasterLogging loggingFp = ::xmlBlasterDefaultLogging;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

SQLiteQueuePlugin::SQLiteQueuePlugin(Global& global, const ClientQueueProperty& property)
   : ME("SQLiteQueuePlugin"), 
     global_(global), 
     log_(global.getLog("org.xmlBlaster.util.queue")), 
     property_(property), 
     queueP_(0), 
     accessMutex_()
{
   const std::string dbName = "xmlBlasterClientCpp.db";
   ::ExceptionStruct exception;
   ::QueueProperties queueProperties;

   strncpy0(queueProperties.dbName, dbName.c_str(), QUEUE_DBNAME_MAX);
   strncpy0(queueProperties.nodeId, "clientJoe1081594557415", QUEUE_ID_MAX);
   strncpy0(queueProperties.queueName, "connection_clientJoe", QUEUE_ID_MAX);
   strncpy0(queueProperties.tablePrefix, "XB_", QUEUE_PREFIX_MAX);
   queueProperties.maxNumOfEntries = 4L;
   queueProperties.maxNumOfBytes = 25LL;

   queueP_ = createQueue(&queueProperties, loggingFp, LOG_TRACE, &exception);
}

SQLiteQueuePlugin::SQLiteQueuePlugin(const SQLiteQueuePlugin& queue)
   : ME("SQLiteQueuePlugin"), 
     global_(queue.global_), 
     log_(queue.log_), 
     property_(queue.property_), 
     queueP_(queue.queueP_), 
     accessMutex_()
{
}

SQLiteQueuePlugin& SQLiteQueuePlugin::operator =(const SQLiteQueuePlugin& queue)
{
   Lock lock(queue.accessMutex_);
   property_   = queue.property_;
   queueP_    = queue.queueP_;
   return *this;

}

SQLiteQueuePlugin::~SQLiteQueuePlugin()
{
   if (log_.call()) log_.call(ME, "destructor");
   if (queueP_) {
      Lock lock(accessMutex_);
      ::ExceptionStruct exception;
      queueP_->shutdown(&queueP_, &exception); // NULLs the queueP_
   }
} 

void SQLiteQueuePlugin::put(const MsgQueueEntry &entry)
{
   if (log_.call()) log_.call(ME, "::put");
   if (log_.dump()) log_.dump(ME, string("::put, the entry is: ")  + entry.toXml());

   Lock lock(accessMutex_);
   /*
   ::ExceptionStruct exception;
   ::QueueEntry queueEntry;

   queueEntry.priority = entry.getPriority();
   queueEntry.isPersistent = entry.isPersistent();
   queueEntry.uniqueId = entry.getUniqueId();
   strncpy0(queueEntry.embeddedType, "MSG_RAW|publish", QUEUE_ENTRY_EMBEDDEDTYPE_LEN);
   queueEntry.embeddedType[QUEUE_ENTRY_EMBEDDEDTYPE_LEN-1] = 0;

   --> TODO: dump MsgUnit with SOCKET protocol int data

   queueEntry.embeddedBlob.data = (char *)data;
   queueEntry.embeddedBlob.dataLen = strlen(queueEntry.embeddedBlob.data);

   queueP_->put(queueP_, &queueEntry, &exception);


   try {
      const EntryType help(*entry.getClone());
      queueP_->insert(help);
      numOfBytes_ += entry.getSizeInBytes();
      // add the sizeInBytes_ here ...
   }
   catch (exception& ex) {
      throw XmlBlasterException(INTERNAL_UNKNOWN, ME + "::put", ex.what());
   }      
   catch (...) {
      throw XmlBlasterException(INTERNAL_UNKNOWN, ME + "::put", "the original type of this exception is unknown");
   }
   */
}

const vector<EntryType> SQLiteQueuePlugin::peekWithSamePriority(long maxNumOfEntries, long maxNumOfBytes) const
{
   Lock lock(accessMutex_);
   vector<EntryType> ret;
   if (queueP_->empty(queueP_)) return ret;
   /*
   StorageType::const_iterator iter = queueP_->begin();
   long numOfEntries = 0;
   long numOfBytes = 0;
   int referencePriority = (**iter).getPriority();
   while (iter != queueP_->end()) {
      numOfBytes += (**iter).getSizeInBytes();
      numOfEntries++;
      if (numOfBytes > maxNumOfBytes && maxNumOfBytes > -1) break;
      if (numOfEntries > maxNumOfEntries && maxNumOfBytes > -1) break;
      if ((**iter).getPriority() != referencePriority ) break;
      EntryType entry = (*iter);
      ret.insert(ret.end(), entry); 
      iter++;
   }
   */
   return ret;
}


long SQLiteQueuePlugin::randomRemove(const vector<EntryType>::const_iterator &start, const vector<EntryType>::const_iterator &end) 
{
   Lock lock(accessMutex_);
   if (start == end || queueP_->empty(queueP_)) return 0;
   vector<EntryType>::const_iterator iter = start;
   long count = 0;
   /*
   while (iter != end) {
      long entrySize = (*iter)->getSizeInBytes();
      if (queueP_->empty()) return 0;
      int help = queueP_->erase(*iter);
      if (help > 0) {
         count += help;
         numOfBytes_ -= help * entrySize;
      }
      iter++;
   }
   */
   return count;
}

void SQLiteQueuePlugin::clear()
{
   Lock lock(accessMutex_);
   ::ExceptionStruct exception;
   queueP_->clear(queueP_, &exception);
}


bool SQLiteQueuePlugin::empty() const
{
   return queueP_->empty(queueP_);
}


}}}} // namespace



