/*------------------------------------------------------------------------------
Name:      SQLiteQueuePlugin.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#ifndef _UTIL_QUEUE_SQLITEQUEUE_H
#define _UTIL_QUEUE_SQLITEQUEUE_H

#include <util/xmlBlasterDef.h>
#include <util/ReferenceHolder.h>
#include <util/queue/I_Queue.h>
#include <util/queue/MsgQueueEntry.h>
#include <util/qos/ConnectQosFactory.h>
#include <util/qos/StatusQosFactory.h>
#include <util/qos/MsgQosFactory.h>
#include <util/key/MsgKeyFactory.h>
#include <util/thread/ThreadImpl.h>
#include <util/I_Log.h>

struct I_QueueStruct;
struct ExceptionStruct;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

/**
 * Implements a persistent queue using SQLite as a base. 
 *
 * This class wraps the ANSI C based persistent queue implementation 
 * <code>xmlBlaster/src/c/util/queue/SQLiteQueue.c</code>.
 *
 * @see <a href="http://www.sqlite.org">The embedded SQLite SQL database</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.c.queue.html">The client.c.queue requirement</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.cpp.queue.html">The client.cpp.queue requirement</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
class Dll_Export SQLiteQueuePlugin : public I_Queue
{
private:
   SQLiteQueuePlugin(const SQLiteQueuePlugin& queue);
   SQLiteQueuePlugin& operator =(const SQLiteQueuePlugin& queue);

protected:
   std::string ME;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log& log_;
   org::xmlBlaster::util::qos::storage::QueuePropertyBase property_;
   struct ::I_QueueStruct *queueP_; // The C based xmlBlaster SQLite queue implementation
   mutable org::xmlBlaster::util::qos::ConnectQosFactory connectQosFactory_;
   mutable org::xmlBlaster::util::qos::StatusQosFactory statusQosFactory_;
   mutable org::xmlBlaster::util::key::MsgKeyFactory msgKeyFactory_;
   mutable org::xmlBlaster::util::qos::MsgQosFactory msgQosFactory_;
   org::xmlBlaster::util::thread::Mutex accessMutex_;

public:
   SQLiteQueuePlugin(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::qos::storage::ClientQueueProperty& property);
   
   /**
    * Shutdown the queue, keep existing entries. 
    */
   virtual ~SQLiteQueuePlugin();
    
   /**
    * Access logging framework. 
    */
   org::xmlBlaster::util::I_Log& getLog() const { return log_; }

   /**
    * puts a new entry into the queue. 
    * Note that this method takes the entry pointed to by the argument 
    * and puts a reference to it into the queue. This means that you can not destroy the entry before the
    * reference to it has been removed from the queue (which normally happens on a remove or when destroying
    * the queue.
    */
   void put(const MsgQueueEntry &entry);

   /**
    * Returns the entries with the highest priority in the queue. If 'maxNumOfEntries' is positive,
    * this is the maximum number of entries to return. If maxNumOfBytes is positive, only the entries
    * which fit into the range specified are returned. If there are no such entries, an empty std::vector is
    * returned.
    */
   const std::vector<EntryType> peekWithSamePriority(long maxNumOfEntries=-1, long maxNumOfBytes=-1) const;

   /**
    * Deletes the entries specified in the std::vector in the argument list. If this std::vector is empty or if
    * the queue is empty, zero (0) is returned, otherwise it returns the number of entries really deleted.
    */
   long randomRemove(const std::vector<EntryType>::const_iterator &start, const std::vector<EntryType>::const_iterator &end);

   /**
    * Access the current number of entries. 
    * @return The number of entries in the queue
    */                                  
   long getNumOfEntries() const;

   /**
    * Access the configured maximum number of elements for this queue. 
    * @return The maximum number of elements in the queue
    */
   long getMaxNumOfEntries() const;

   /**
    * Returns the amount of bytes currently in the queue. 
    * If the implementation of this interface is not able to return the correct
    * number of entries (for example if the implementation must make a remote
    * call to a DB which is temporarly not available) it will return -1.
    * @return The amount of bytes currently in the queue, returns -1 on error
    */
   int64_t getNumOfBytes() const;

   /**
    * Access the configured capacity (maximum bytes) for this queue. 
    * @return The maximum capacity for the queue in bytes
    */
   int64_t getMaxNumOfBytes() const;

   /**
    * Clears (removes all entries) this queue
    */
   void clear();

   /**
    * returns true if the queue is empty, false otherwise
    */                                  
   bool empty() const;

   /**
    * Converts the C ExceptionStruct into our XmlBlasterException class. 
    */
   org::xmlBlaster::util::XmlBlasterException convertFromQueueException(const ::ExceptionStruct *ex) const;

   /**
    * Parse the embedded type information. 
    * @param embeddedType The input, for example "MSG_RAW|publish"
    * @param type Output: "MSG_RAW" (the SOCKET serialization format)
    * @param methodName Output: "publish" (see MethodName.cpp)
    */
   static void parseEmbeddedType(const std::string& embeddedType, std::string &type, std::string &methodName);

   static std::string usage();

   /**
    * Get the name of the plugin. 
    * @return "SQLite"
    * @enforcedBy I_Plugin
    */
   std::string getType() { static std::string type = "SQLite"; return type; }

   /**
    * Get the version of the plugin. 
    * @return "1.0"
    * @enforcedBy I_Plugin
    */
   std::string getVersion() { static std::string version = "1.0"; return version; }

   void destroy();
};

}}}} // namespace

#endif

