/*------------------------------------------------------------------------------
Name:      SQLiteQueuePlugin.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
#include <util/queue/SQLiteQueuePlugin.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <stdarg.h>           // va_start for logging
#include <stdio.h>            // vsnprintf for g++ 2.9x only
#include <string.h>           // memset
#include <util/lexical_cast.h>
#include <util/MessageUnit.h>
#include <util/queue/ConnectQueueEntry.h>
#include <util/queue/SubscribeQueueEntry.h>
#include <util/queue/UnSubscribeQueueEntry.h>
#include <util/queue/PublishQueueEntry.h>
#include <socket/xmlBlasterSocket.h> // C xmlBlaster client library: for msgUnit serialize
#include <util/queue/QueueInterface.h> // The C implementation interface

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::util::qos::storage;
using namespace org::xmlBlaster::util::key;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

//static ::XmlBlasterLogging loggingFp = ::xmlBlasterDefaultLogging;
static void myLogger(void *logUserP, 
                     XMLBLASTER_LOG_LEVEL currLevel,
                     XMLBLASTER_LOG_LEVEL level,
                     const char *location, const char *fmt, ...);

namespace org { namespace xmlBlaster { namespace util { namespace queue {

SQLiteQueuePlugin::SQLiteQueuePlugin(Global& global, const ClientQueueProperty& property)
   : ME("SQLiteQueuePlugin"), 
     global_(global), 
     log_(global.getLog("org.xmlBlaster.util.queue")), 
     property_(property), 
     queueP_(0), 
     connectQosFactory_(global_),
     statusQosFactory_(global_),
     msgKeyFactory_(global_),
     msgQosFactory_(global_),
     accessMutex_()
{
   if (log_.call()) log_.call(ME, "Constructor queue [" + getType() + "][" + getVersion() + "] ...");
   /*
    TODO: Pass basic configuration from plugin key/values similar to (see xmlBlaster.properties)
     QueuePlugin[SQLite][1.0]=SQLiteQueuePlugin,
         url=/${user.home}${file.separator}tmp${file.separator}$_{xmlBlaster_uniqueId}.db,\
         user=sqlite,\
         password=,\
         connectionPoolSize=1,\
         connectionBusyTimeout=90000,\
         maxWaitingThreads=300,\
         tableNamePrefix=XB_,\
         entriesTableName=ENTRIES,\
         dbAdmin=true
   */
   const std::string classRelating = "QueuePlugin["+getType()+"]["+getVersion()+"]"; // "QueuePlugin[SQLite][1.0]"
   const std::string instanceRelating = property.getPropertyPrefix();                // == "connection"

   // Should it be "queue/connection/tableNamePrefix" or "queue/QueuePlugin[SQLite][1.0]/tableNamePrefix"
   // The first allows different instances with changing "connection" to e.g. "tailback" etc.
   if (global_.getProperty().propertyExists(classRelating, true)) {
      log_.warn(ME, "Your setting of property '" + classRelating + "' is not supported");
   }

   std::string defaultPath = ""; // for example: "/home/joe/tmp/" or "C:\Documents and Settings\marcel\tmp"
   if (global_.getProperty().get("user.home", "") != "")
      defaultPath = global_.getProperty().get("user.home", "") +
                    global_.getProperty().get("file.separator", "");
                    //+ "tmp" +                                     // We currently can't create missing directories, TODO!!!
                    //global_.getProperty().get("file.separator", "");

   const std::string url = global_.getProperty().get("queue/"+instanceRelating+"/url", defaultPath+"xmlBlasterClientCpp.db");  // "queue/connection/url"
   const std::string queueName = global_.getProperty().get("queue/"+instanceRelating+"/queueName", instanceRelating + "_" + global_.getStrippedImmutableId()); // "connection_clientJoe2"
   const std::string tableNamePrefix = global_.getProperty().get("queue/"+instanceRelating+"/tableNamePrefix", "XB_");// "queue/connection/tableNamePrefix"

   ::ExceptionStruct exception;
   ::QueueProperties queueProperties;
   memset(&queueProperties, 0, sizeof(QueueProperties));

   strncpy0(queueProperties.dbName, url.c_str(), QUEUE_DBNAME_MAX);
   strncpy0(queueProperties.queueName, queueName.c_str(), QUEUE_ID_MAX);
   strncpy0(queueProperties.tablePrefix, tableNamePrefix.c_str(), QUEUE_PREFIX_MAX);
   queueProperties.maxNumOfEntries = (int32_t)property.getMaxEntries();
   queueProperties.maxNumOfBytes = property.getMaxBytes();
   queueProperties.logFp = myLogger;
   queueProperties.logLevel = (log_.call() || log_.trace()) ? XMLBLASTER_LOG_TRACE : XMLBLASTER_LOG_INFO;
   queueProperties.userObject = &log_;

   queueP_ = createQueue(&queueProperties, &exception); // &log_ Used in myLogger(), see above
   if (*exception.errorCode != 0) throw convertFromQueueException(&exception);

   log_.info(ME, "Created queue [" + getType() + "][" + getVersion() + "], queue/"+instanceRelating+"/url='" +
                 queueProperties.dbName + "', queue/"+instanceRelating+"/queueName='" + queueProperties.queueName +
                 "', queue/"+instanceRelating+"/maxEntries=" + lexical_cast<string>(queueProperties.maxNumOfEntries));
}

/*
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
*/

SQLiteQueuePlugin::~SQLiteQueuePlugin()
{
   if (log_.call()) log_.call(ME, "destructor");
   if (queueP_) {
      Lock lock(accessMutex_);
      ::ExceptionStruct exception;
      queueP_->shutdown(&queueP_, &exception); // NULLs the queueP_
      if (*exception.errorCode != 0) {
         const int ERRORSTR_LEN = 1024;
         char errorString[ERRORSTR_LEN];
         log_.warn(ME, string("Ignoring problem during shutdown: ") + getExceptionStr(errorString, ERRORSTR_LEN, &exception));
      }
   }
} 

void SQLiteQueuePlugin::put(const MsgQueueEntry &entry)
{
   if (log_.call()) log_.call(ME, "::put");
   if (log_.dump()) log_.dump(ME+".put()", string("The msg entry is: ")  + entry.toXml());

   Lock lock(accessMutex_);
   if (queueP_ == 0) throw XmlBlasterException(RESOURCE_DB_UNAVAILABLE, ME, "Sorry, no persistent queue is available, put() failed");

   ::ExceptionStruct exception;
   ::QueueEntry queueEntry;

   // Copy C++ to C struct ...

   queueEntry.priority = entry.getPriority();
   queueEntry.isPersistent = entry.isPersistent();
   queueEntry.uniqueId = entry.getUniqueId();
   queueEntry.sizeInBytes = entry.getSizeInBytes();
   strncpy0(queueEntry.embeddedType, entry.getEmbeddedType().c_str(), QUEUE_ENTRY_EMBEDDEDTYPE_LEN);  // "MSG_RAW|publish"
   queueEntry.embeddedType[QUEUE_ENTRY_EMBEDDEDTYPE_LEN-1] = 0;

   // dump MsgQueueEntry with SOCKET protocol into C ::MsgUnit ...
   
   const BlobHolder *blob = (const BlobHolder *)entry.getEmbeddedObject();
   if (blob == 0) throw XmlBlasterException(INTERNAL_ILLEGALARGUMENT, ME, "put() failed, the entry " + entry.getLogId() + " returned NULL for embeddedObject");
   queueEntry.embeddedBlob.data = blob->data;
   queueEntry.embeddedBlob.dataLen = blob->dataLen;

   if (log_.dump()) {
      char *dumpP = blobDump(&queueEntry.embeddedBlob);
      log_.dump(ME+".put()", string("Put blob to queue:") + dumpP);
      ::xmlBlasterFree(dumpP);
   }

   // Push into C persistent queue ...

   queueP_->put(queueP_, &queueEntry, &exception);

   if (*exception.errorCode != 0) throw convertFromQueueException(&exception);
}

const vector<EntryType> SQLiteQueuePlugin::peekWithSamePriority(long maxNumOfEntries, long maxNumOfBytes) const
{
   Lock lock(accessMutex_);
   if (queueP_ == 0) throw XmlBlasterException(RESOURCE_DB_UNAVAILABLE, ME, "Sorry, no persistent queue is available, peekWithSamePriority() failed");
   vector<EntryType> ret;
   if (queueP_->empty(queueP_)) return ret;
   if (log_.call()) log_.call(ME, "peekWithSamePriority maxNumOfEntries=" + lexical_cast<std::string>(maxNumOfEntries) + " maxNumOfBytes=" + lexical_cast<std::string>(maxNumOfBytes));

   ::ExceptionStruct exception;
   ::QueueEntryArr *entriesC = queueP_->peekWithSamePriority(queueP_, (int32_t)maxNumOfEntries, maxNumOfBytes, &exception);
   if (*exception.errorCode != 0) throw convertFromQueueException(&exception);

   // Now we need to copy the C results into C++ classes ...

   for (size_t ii=0; ii<entriesC->len; ii++) {
      ::QueueEntry &queueEntryC = entriesC->queueEntryArr[ii];
      string type, methodName;
      parseEmbeddedType(queueEntryC.embeddedType, type, methodName);

      if (type != Constants::ENTRY_TYPE_MSG_RAW) {
         string embedded = queueEntryC.embeddedType;
         freeQueueEntryArr(entriesC);
         throw XmlBlasterException(INTERNAL_UNKNOWN, ME + "::peekWithSamePriority", string("The queue entry embeddedType '") + embedded + "' type='" + type + "' is not supported");
      }

      if (log_.dump()) {
         char *dumpP = blobDump(&queueEntryC.embeddedBlob);
         log_.dump(ME+".peekWithSamePriority()", string("Retrieved blob from queue:") + dumpP);
         ::xmlBlasterFree(dumpP);
      }

      ::MsgUnitArr *msgUnitArrC = ::parseMsgUnitArr(queueEntryC.embeddedBlob.dataLen, queueEntryC.embeddedBlob.data);

      for (size_t j=0; msgUnitArrC!=0 && j<msgUnitArrC->len; j++) { // TODO: Collect a PUBLISH_ARR !!! (currently we transform it to single publish()es)
         ::MsgUnit &msgUnit = msgUnitArrC->msgUnitArr[j];
         if (log_.dump()) {
            char *dumpP = ::messageUnitToXmlLimited(&msgUnit, 128);
            log_.dump(ME+".peekWithSamePriority()", string("Retrieved and parsed C message from queue:") + dumpP);
            ::xmlBlasterFree(dumpP);
         }
         if (methodName == MethodName::PUBLISH) {
            MsgKeyData msgKeyData = msgKeyFactory_.readObject(string(msgUnit.key));
            MsgQosData msgQosData = msgQosFactory_.readObject(string(msgUnit.qos));
            MessageUnit messageUnit(msgKeyData, msgUnit.contentLen, (const unsigned char*)msgUnit.content, msgQosData);
            PublishQueueEntry *pq = new PublishQueueEntry(global_, messageUnit,
                                           queueEntryC.priority, queueEntryC.uniqueId);
            if (log_.trace()) log_.trace(ME, "Got PublishQueueEntry from queue");
            ret.insert(ret.end(), EntryType(*pq));
            if (log_.trace()) log_.trace(ME, "PublishQueueEntry is reference countet");
         }
         else if (methodName == MethodName::CONNECT) {
            ConnectQosRef connectQos = connectQosFactory_.readObject(string(msgUnit.qos));
            ConnectQueueEntry *pq = new ConnectQueueEntry(global_, connectQos,
                                           queueEntryC.priority, queueEntryC.uniqueId);
            if (log_.trace()) log_.trace(ME, "Got ConnectQueueEntry from queue");
            ret.insert(ret.end(), EntryType(*pq));
            if (log_.trace()) log_.trace(ME, "ConnectQueueEntry is reference countet");
         }
         /* TODO: queryKeyFactory and queryQosFactory!
         else if (methodName == MethodName::SUBSCRIBE) {
            QueryKeyData queryKeyData = queryKeyFactory_.readObject(string(msgUnit.key));
            SubscribeKey subscribeKey(global_, queryKeyData);
            QueryQosData queryQosData = queryQosFactory_.readObject(string(msgUnit.qos));
            SubscribeQos subscribeQos(global_, queryQosData);
            SubscribeQueueEntry *pq = new SubscribeQueueEntry(global_, subscribeKey, subscribeQos,
                                           queueEntryC.priority, queueEntryC.uniqueId);
            if (log_.trace()) log_.trace(ME, "Got SubscribeQueueEntry from queue");
            ret.insert(ret.end(), EntryType(*pq));
            if (log_.trace()) log_.trace(ME, "SubscribeQueueEntry is reference countet");
         }
         */
         else {  // TODO: How to handle: throw exception or remove the buggy entry?
            log_.error(ME + "::peekWithSamePriority", string("The queue entry embeddedType '") + queueEntryC.embeddedType + "' methodName='" + methodName + "' is not supported, we ignore it.");
         }
      }

      freeMsgUnitArr(msgUnitArrC);
   }

   freeQueueEntryArr(entriesC);
   return ret;
}

void SQLiteQueuePlugin::parseEmbeddedType(const string& embeddedType, string &type, string &methodName)
{
   string::size_type pos = embeddedType.find("|");
   if (pos == string::npos) {
      type = embeddedType;
      methodName = "";
      return;
   }
   type = embeddedType.substr(0, pos);
   if (pos < embeddedType.size())
      methodName = embeddedType.substr(pos+1);
   // No trim(): we assume no white spaces
}

long SQLiteQueuePlugin::randomRemove(const vector<EntryType>::const_iterator &start, const vector<EntryType>::const_iterator &end) 
{
   Lock lock(accessMutex_);
   if (queueP_ == 0) throw XmlBlasterException(RESOURCE_DB_UNAVAILABLE, ME, "Sorry, no persistent queue is available, randomRemove() failed");
   if (start == end || queueP_->empty(queueP_)) return 0;

   ::QueueEntryArr queueEntryArr;
   memset(&queueEntryArr, 0, sizeof(QueueEntryArr));
   {
      vector<EntryType>::const_iterator iter = start;
      while (iter != end) {
         iter++;
         queueEntryArr.len++;
      }
   }
   if (queueEntryArr.len < 1) return 0;

   queueEntryArr.queueEntryArr = (QueueEntry *)calloc(queueEntryArr.len, sizeof(QueueEntry));

   vector<EntryType>::const_iterator iter = start;
   for (int currIndex=0; iter != end; ++iter, currIndex++) {
      const EntryType &entryType = (*iter);
      const MsgQueueEntry &entry = *entryType;
      ::QueueEntry &queueEntry = queueEntryArr.queueEntryArr[currIndex];

      // Copy C++ to C struct ...

      queueEntry.priority = entry.getPriority();
      queueEntry.isPersistent = entry.isPersistent();
      queueEntry.uniqueId = entry.getUniqueId();
      queueEntry.sizeInBytes = entry.getSizeInBytes();
      strncpy0(queueEntry.embeddedType, entry.getEmbeddedType().c_str(), QUEUE_ENTRY_EMBEDDEDTYPE_LEN);  // "MSG_RAW|publish" "MSG_RAW|connect"
      queueEntry.embeddedType[QUEUE_ENTRY_EMBEDDEDTYPE_LEN-1] = 0;
      /*
      const BlobHolder *blob = (const BlobHolder *)entry.getEmbeddedObject();
      if (blob == 0) throw XmlBlasterException(INTERNAL_ILLEGALARGUMENT, ME, "put() failed, the entry " + entry.getLogId() + " returned NULL for embeddedObject");
      queueEntry.embeddedBlob.data = blob->data;
      queueEntry.embeddedBlob.dataLen = blob->dataLen;
      */
      if (log_.dump()) {
         char *dumpP = ::queueEntryToXml(&queueEntry, 200);
         log_.dump(ME+".put()", string("Put blob to queue:") + dumpP);
         xmlBlasterFree(dumpP);
      }
   }

   ::ExceptionStruct exception;

   int32_t numRemoved = queueP_->randomRemove(queueP_, &queueEntryArr, &exception);

   freeQueueEntryArrInternal(&queueEntryArr);

   if (*exception.errorCode != 0) throw convertFromQueueException(&exception);
   return (long)numRemoved;
}

long SQLiteQueuePlugin::getNumOfEntries() const
{
   if (queueP_ == 0) throw XmlBlasterException(RESOURCE_DB_UNAVAILABLE, ME, "Sorry, no persistent queue is available, getNumOfEntries() failed");
   return queueP_->getNumOfEntries(queueP_);
}

long SQLiteQueuePlugin::getMaxNumOfEntries() const
{
   if (queueP_ == 0) return property_.getMaxEntries(); // throw XmlBlasterException(RESOURCE_DB_UNAVAILABLE, ME, "Sorry, no persistent queue is available, getNumOfEntries() failed");
   return queueP_->getMaxNumOfEntries(queueP_);
}

int64_t SQLiteQueuePlugin::getNumOfBytes() const
{
   if (queueP_ == 0) throw XmlBlasterException(RESOURCE_DB_UNAVAILABLE, ME, "Sorry, no persistent queue is available, getNumOfBytes() failed");
   return queueP_->getNumOfBytes(queueP_);
}

int64_t SQLiteQueuePlugin::getMaxNumOfBytes() const
{  
   if (queueP_ == 0) return property_.getMaxBytes(); // throw XmlBlasterException(RESOURCE_DB_UNAVAILABLE, ME, "Sorry, no persistent queue is available, getMaxNumOfBytes() failed");
   return queueP_->getMaxNumOfBytes(queueP_);
}

void SQLiteQueuePlugin::clear()
{
   Lock lock(accessMutex_);
   if (queueP_ == 0) throw XmlBlasterException(RESOURCE_DB_UNAVAILABLE, ME, "Sorry, no persistent queue is available, clear() failed");
   ::ExceptionStruct exception;
   queueP_->clear(queueP_, &exception);
}


bool SQLiteQueuePlugin::empty() const
{
   if (queueP_ == 0) throw XmlBlasterException(RESOURCE_DB_UNAVAILABLE, ME, "Sorry, no persistent queue is available, empty() failed");
   return queueP_->empty(queueP_);
}

void SQLiteQueuePlugin::destroy()
{
   if (queueP_ == 0) throw XmlBlasterException(RESOURCE_DB_UNAVAILABLE, ME, "Sorry, no persistent queue is available, destroy() failed");
   ::ExceptionStruct exception;
   queueP_->destroy(&queueP_, &exception);
   if (*exception.errorCode != 0) throw convertFromQueueException(&exception);
}

// Exception conversion ....
org::xmlBlaster::util::XmlBlasterException SQLiteQueuePlugin::convertFromQueueException(const ::ExceptionStruct *ex) const
{
   return org::xmlBlaster::util::XmlBlasterException(
            (*ex->errorCode=='\0')?string("internal.unknown"):string(ex->errorCode),
            string(""),
            ME,
            "en",
            string(ex->message),
            global_.getVersion() + " " + global_.getBuildTimestamp());
}

string SQLiteQueuePlugin::usage()
{
   std::string text = string("");
   text += string("\nThe SQLite persistent queue plugin configuration:");
   text += string("\n   -queue/connection/url [xmlBlasterClientCpp.db]");
   text += string("\n                       The database file name (incl. path), defaults to the current directory.");
   text += string("\n   -queue/connection/tableNamePrefix [XB_]");
   text += string("\n                       The prefix for all tables in the database.");
   text += ClientQueueProperty::usage();
   return text;
}

}}}} // namespace


/**
 * Customized logging output is handled by this method. 
 * We redirect logging output from the C implementation to our C++ logging plugin.
 * <p>
 * Please compile with <code>XMLBLASTER_PERSISTENT_QUEUE</code> defined.
 * </p>
 * @param queueP
 * @param currLevel The actual log level of the client
 * @param level The level of this log entry
 * @param location A string describing the code place
 * @param fmt The formatting string
 * @param ... Other variables to log, corresponds to 'fmt'
 * @see xmlBlaster/src/c/msgUtil.c: xmlBlasterDefaultLogging() is the default
 *      implementation
 */
static void myLogger(void *logUserP, 
                     XMLBLASTER_LOG_LEVEL currLevel,
                     XMLBLASTER_LOG_LEVEL level,
                     const char *location, const char *fmt, ...)
{
   /* Guess we need no more than 200 bytes. */
   int n, size = 200;
   char *p = 0;
   va_list ap;
   ::I_Queue *queueP = (::I_Queue *)logUserP;

   //org::xmlBlaster::util::queue::SQLiteQueuePlugin *pluginP =
   //      (org::xmlBlaster::util::queue::SQLiteQueuePlugin *)queueP->userObject;
   //org::xmlBlaster::util::I_Log& log = pluginP->getLog();

   if (queueP->userObject == 0) {
      std::cout << "myLogger not initialized" << std::endl;
      return;
   }
   org::xmlBlaster::util::I_Log& log = *((org::xmlBlaster::util::I_Log*)queueP->userObject);

   if (level > currLevel) { /* XMLBLASTER_LOG_ERROR, XMLBLASTER_LOG_WARN, XMLBLASTER_LOG_INFO, XMLBLASTER_LOG_TRACE */
      return;
   }
   if ((p = (char *)malloc (size)) == NULL)
      return;

   for (;;) {
      /* Try to print in the allocated space. */
      va_start(ap, fmt);
      n = VSNPRINTF(p, size, fmt, ap); /* UNIX: vsnprintf(), WINDOWS: _vsnprintf() */
      va_end(ap);
      /* If that worked, print the string to console. */
      if (n > -1 && n < size) {
         if (level == XMLBLASTER_LOG_INFO)
            log.info(location, p);
         else if (level == XMLBLASTER_LOG_WARN)
            log.warn(location, p);
         else if (level == XMLBLASTER_LOG_ERROR)
            log.error(location, p);
         else
            log.trace(location, p);
         free(p);
         return;
      }
      /* Else try again with more space. */
      if (n > -1)    /* glibc 2.1 */
         size = n+1; /* precisely what is needed */
      else           /* glibc 2.0 */
         size *= 2;  /* twice the old size */
      if ((p = (char *)realloc (p, size)) == NULL) {
         return;
      }
   }
}

