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
#include <util/thread/ThreadImpl.h>
#include <util/I_Log.h>
#include <util/queue/QueueInterface.h> // The C implementation interface

namespace org { namespace xmlBlaster { namespace util { namespace queue {

/**
 * Implements a persistent queue using SQLite as a base. 
 *
 * This class wraps the ANSI C based persistent queue implementation 
 * <code>xmlBlaster/src/c/util/queue/SQLiteQueue.c</code>.
 *
 * @see <a href="http://www.sqlite.org">The embedded SQLite SQL database</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.c.queue.html">The client.c.queue requirement</a>
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
   org::xmlBlaster::util::qos::storage::ClientQueueProperty property_;
   ::I_Queue *queueP_;
   org::xmlBlaster::util::thread::Mutex accessMutex_;

public:
   SQLiteQueuePlugin(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::qos::storage::ClientQueueProperty& property);
   
   virtual ~SQLiteQueuePlugin();
    
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
    * Clears (removes all entries) this queue
    */
    void clear();

    /**
     * returns true if the queue is empty, false otherwise
     */                                  
     bool empty() const;
};

}}}} // namespace

#endif

