/*------------------------------------------------------------------------------
Name:      CacheQueuePlugin.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#ifndef _UTIL_QUEUE_CACHEQUEUE_H
#define _UTIL_QUEUE_CACHEQUEUE_H

#include <util/xmlBlasterDef.h>
#include <util/ReferenceHolder.h>
#include <util/queue/I_Queue.h>
#include <util/queue/MsgQueueEntry.h>
#include <util/thread/ThreadImpl.h>
#include <util/I_Log.h>
#include <set>

namespace org { namespace xmlBlaster { namespace util { namespace queue {

typedef std::set<EntryType, std::greater<EntryType> > StorageType;

/**
 * This class implements a very simple cache around the RAM and SQLite queue. 
 * Note there is no swapping support for transient or persistent messages
 * all transient messages are hold in RAM, and all persistent messages are
 * duplicated to harddisk.
 * When time permits we will add swapping support similar to the Java CACHE
 * implementation. 
 * <br />
 * If you have mainly persistent messages and need to take care on
 * your RAM consumption with many messages in queue consider to use
 * the "SQLite" persistent queue directly (without any RAM or CACHE)
 * with the option <code>-connection/queue/type SQLite</code> instead of the default
 * <code>-connection/queue/type CACHE</code>.
 * <br />
 * On the other hand if you use only transient message consider using the RAM queue directly
 * with the option <code>-connection/queue/type RAM</code> instead of the default
 * <code>-connection/queue/type CACHE</code>.
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.cpp.queue.html">The client.cpp.queue requirement</a>
 */
class Dll_Export CacheQueuePlugin : public I_Queue
{
private:
   CacheQueuePlugin(const CacheQueuePlugin& queue);
   CacheQueuePlugin& operator =(const CacheQueuePlugin& queue);

   /**
    * Shuffle messages from persistent store to transient store. 
    * On startup we can only load the highest priority in a bulk
    * NOTE: This must be called from inside a synchronization lock.
    * @return Number of entries loaded
    */
   long reloadFromPersistentStore() const;

protected:
   std::string ME;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log& log_;
   org::xmlBlaster::util::qos::storage::ClientQueueProperty property_;
   mutable I_Queue *transientQueueP_;
   I_Queue *persistentQueueP_;
   org::xmlBlaster::util::thread::Mutex accessMutex_;

public:
   CacheQueuePlugin(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::qos::storage::ClientQueueProperty& property);

   virtual ~CacheQueuePlugin();
    
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

    static std::string usage();

   /**
    * Get the name of the plugin. 
    * @return "CACHE"
    * @enforcedBy I_Plugin
    */
   std::string getType() { static std::string type = "CACHE"; return type; }

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

