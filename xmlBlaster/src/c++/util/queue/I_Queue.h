/*------------------------------------------------------------------------------
Name:      I_Queue.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Interface for queue implementations (RAM, JDBC or CACHE). 
 *
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 * @author <a href='mailto:xmlblast@marcelruff.info'>Marcel Ruff</a>
 */
#ifndef _UTIL_QUEUE_I_QUEUE_H
#define _UTIL_QUEUE_I_QUEUE_H

#include <util/xmlBlasterDef.h>
#include <util/ReferenceHolder.h>
#include <util/queue/MsgQueueEntry.h>
#include <util/thread/ThreadImpl.h>
#include <util/I_Log.h>
#include <set>

namespace org { namespace xmlBlaster { namespace util { namespace queue {

typedef ReferenceHolder<MsgQueueEntry>      EntryType;

class Dll_Export I_Queue
{
public:
   virtual ~I_Queue() {};
    
   /**
    * puts a new entry into the queue. 
    * Note that this method takes the entry pointed to by the argument 
    * and puts a reference to it into the queue. This means that you can not destroy the entry before the
    * reference to it has been removed from the queue (which normally happens on a remove or when destroying
    * the queue.
    */
   virtual void put(const MsgQueueEntry &entry) = 0;

   /**
    * Returns the entries with the highest priority in the queue. If 'maxNumOfEntries' is positive,
    * this is the maximum number of entries to return. If maxNumOfBytes is positive, only the entries
    * which fit into the range specified are returned. If there are no such entries, an empty std::vector is
    * returned.
    */
   virtual const std::vector<EntryType> peekWithSamePriority(long maxNumOfEntries=-1, long maxNumOfBytes=-1) const = 0;

   /**
    * Deletes the entries specified in the std::vector in the argument list. If this std::vector is empty or if
    * the queue is empty, zero (0) is returned, otherwise it returns the number of entries really deleted.
    */
   virtual long randomRemove(const std::vector<EntryType>::const_iterator &start, const std::vector<EntryType>::const_iterator &end) = 0;

   /**
    * Clears (removes all entries) this queue
    */
   virtual void clear() = 0;

    /**
     * returns true if the queue is empty, false otherwise
     */                                  
   virtual bool empty() const = 0;
};

}}}} // namespace

#endif

