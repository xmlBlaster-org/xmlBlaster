/*------------------------------------------------------------------------------
Name:      Queue.h
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
 */

#ifndef _UTIL_QUEUE_QUEUE_H
#define _UTIL_QUEUE_QUEUE_H

#include <util/xmlBlasterDef.h>
#include <util/ReferenceHolder.h>
#include <util/queue/MsgQueueEntry.h>
#include <util/thread/ThreadImpl.h>
#include <util/I_Log.h>
#include <set>

namespace org { namespace xmlBlaster { namespace util { namespace queue {

typedef ReferenceHolder<MsgQueueEntry>      EntryType;
typedef std::set<EntryType, std::greater<EntryType> > StorageType;

class Dll_Export Queue
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
   Queue(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::qos::storage::ClientQueueProperty& property);

   Queue(const Queue& queue);

   Queue& operator =(const Queue& queue);
   
   virtual ~Queue();
    
   /**
    * puts a new entry into the queue. 
    * Note that this method takes the entry pointed to by the argument 
    * and puts a reference to it into the queue. This means that you can not destroy the entry before the
    * reference to it has been removed from the queue (which normally happens on a remove or when destroying
    * the queue.
    */
   void put(MsgQueueEntry *entry);

   /**
    * Returns the entries with the highest priority in the queue. If 'maxNumOfEntries' is positive,
    * this is the maximum number of entries to return. If maxNumOfBytes is positive, only the entries
    * which fit into the range specified are returned. If there are no such entries, an empty std::vector is
    * returned.
    */
   std::vector<EntryType> peekWithSamePriority(long maxNumOfEntries=-1, long maxNumOfBytes=-1) const;

   /**
    * Deletes the entries specified in the std::vector in the argument list. If this std::vector is empty or if
    * the queue is empty, zero (0) is returned, otherwise it returns the number of entries really deleted.
    */
   long randomRemove(std::vector<EntryType>::const_iterator start, std::vector<EntryType>::const_iterator end);

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

