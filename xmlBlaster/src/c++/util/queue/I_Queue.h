/*------------------------------------------------------------------------------
Name:      I_Queue.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#ifndef _UTIL_QUEUE_I_QUEUE_H
#define _UTIL_QUEUE_I_QUEUE_H

#include <util/xmlBlasterDef.h>
#include <util/ReferenceHolder.h>
#include <util/queue/MsgQueueEntry.h>
#include <util/thread/ThreadImpl.h>
#include <util/I_Log.h>
#include <set>

namespace org { namespace xmlBlaster { namespace util { namespace queue {

/**
 * Smart pointer support. 
 */
typedef ReferenceHolder<MsgQueueEntry> EntryType;

/**
 * Interface for queue implementations (RAM, JDBC or CACHE). 
 *
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 * @author <a href='mailto:xmlblast@marcelruff.info'>Marcel Ruff</a>
 */
class Dll_Export I_Queue
{
public:
   virtual ~I_Queue() {};
    
   /**
    * Puts a new entry into the queue. 
    * The put() method takes a clone of the passed entry, you can safely destroy
    * your passed entry after this invocation.
    * @param entry A message which is stored in the queue
    * @throws XmlBlasterException on problems
    */
   virtual void put(const MsgQueueEntry &entry) = 0;

   /**
    * Returns the entries with the highest priority in the queue. If 'maxNumOfEntries' is positive,
    * this is the maximum number of entries to return. If maxNumOfBytes is positive, only the entries
    * which fit into the range specified are returned. If there are no such entries, an empty std::vector is
    * returned.
    * @return A vector with EntryType, this smart pointer wraps the real message, you don't need to take
    *         care on freeing memory.
    * @throws XmlBlasterException on problems
    */
   virtual const std::vector<EntryType> peekWithSamePriority(long maxNumOfEntries=-1, long maxNumOfBytes=-1) const = 0;

   /**
    * Deletes the entries specified in the std::vector in the argument list. If this std::vector is empty or if
    * the queue is empty, zero (0) is returned, otherwise it returns the number of entries really deleted.
    * @param start The inclusive beginning of the messages to remove
    * @param end The exclusive ending
    * @return Number of entries removed
    * @throws XmlBlasterException on problems
    */
   virtual long randomRemove(const std::vector<EntryType>::const_iterator &start, const std::vector<EntryType>::const_iterator &end) = 0;

   /**
    * Clears (removes all entries) this queue
    * @throws XmlBlasterException on problems
    */
   virtual void clear() = 0;

    /**
     * @return true if the queue is empty, false otherwise
     * @throws XmlBlasterException on problems
     */                                  
   virtual bool empty() const = 0;
};

}}}} // namespace

#endif

