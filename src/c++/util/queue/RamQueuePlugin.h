/*------------------------------------------------------------------------------
Name:      RamQueuePlugin.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Class embedding messages or information to be stored on the client queues
 * Note that all content is copied when passed to the constructors.
 * This way this queue entry is the owner of the content (and therefore will
 * delete it when its destructor is called).
 *
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.cpp.queue.html">The client.cpp.queue requirement</a>
 */

#ifndef _UTIL_QUEUE_RAMQUEUE_H
#define _UTIL_QUEUE_RAMQUEUE_H

#include <util/xmlBlasterDef.h>
#include <util/ReferenceHolder.h>
#include <util/queue/I_Queue.h>
#include <util/queue/MsgQueueEntry.h>
#include <util/thread/ThreadImpl.h>
#include <util/I_Log.h>
#include <set>

namespace org { namespace xmlBlaster { namespace util { namespace queue {

typedef std::set<EntryType, std::greater<EntryType> > StorageType;

class Dll_Export RamQueuePlugin : public I_Queue
{
protected:
   std::string        ME;
   org::xmlBlaster::util::Global&       global_;
   org::xmlBlaster::util::I_Log&          log_;
   org::xmlBlaster::util::qos::storage::ClientQueueProperty property_;
   StorageType   storage_;
   long          numOfBytes_;
   org::xmlBlaster::util::thread::Mutex accessMutex_;

public:
   RamQueuePlugin(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::qos::storage::ClientQueueProperty& property);

   RamQueuePlugin(const RamQueuePlugin& queue);

   RamQueuePlugin& operator =(const RamQueuePlugin& queue);
   
   virtual ~RamQueuePlugin();
    
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
    * Get the name of the plugin. 
    * @return "RAM"
    * @enforcedBy I_Plugin
    */
   std::string getType() { static std::string type = "RAM"; return type; }

   /**
    * Get the version of the plugin. 
    * @return "1.0"
    * @enforcedBy I_Plugin
    */
   std::string getVersion() { static std::string version = "1.0"; return version; }

   void destroy() { return; }
};

}}}} // namespace

#endif

