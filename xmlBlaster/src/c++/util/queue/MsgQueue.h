/*------------------------------------------------------------------------------
Name:      MsgQueue.h
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


#ifndef _UTIL_QUEUE_MSGQUEUE_H
#define _UTIL_QUEUE_MSGQUEUE_H

#include <util/xmlBlasterDef.h>
#include <util/ReferenceHolder.h>
#include <util/queue/MsgQueueEntry.h>
#include <util/queue/PublishQueueEntry.h>
#include <util/queue/ConnectQueueEntry.h>
#include <util/queue/SubscribeQueueEntry.h>
#include <util/queue/UnSubscribeQueueEntry.h>
#include <util/qos/storage/QueueProperty.h>
#include <util/thread/Thread.h>
#include <util/Log.h>
#include <set>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;
using org::xmlBlaster::util::qos::storage::QueueProperty;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

typedef ReferenceHolder<MsgQueueEntry>      EntryType;
typedef set<EntryType, greater<EntryType> > StorageType;

class Dll_Export MsgQueue
{
private:
   const string  ME;
   Global&       global_;
   Log&          log_;
   QueueProperty property_;
   StorageType   storage_;
   long          numOfBytes_;
   Mutex         accessMutex_;

public:
   MsgQueue(Global& global, const QueueProperty& property);

   MsgQueue(const MsgQueue& queue);

   MsgQueue& operator =(const MsgQueue& queue);
   
   ~MsgQueue();
    
   /**
    * puts a new entry into the queue. 
    * Note that this method takes the entry pointed to by the argument 
    * and puts a reference to it into the queue. This means that you can not destroy the entry before the
    * reference to it has been removed from the queue (which normally happens on a remove or when destroying
    * the queue.
    */
   void put(MsgQueueEntry *entry);

   void put(const PublishQueueEntry& entry);

   void put(const ConnectQueueEntry& entry);

   void put(const SubscribeQueueEntry& entry);

   void put(const UnSubscribeQueueEntry& entry);


   /**
    * Returns the entries with the highest priority in the queue. If 'maxNumOfEntries' is positive,
    * this is the maximum number of entries to return. If maxNumOfBytes is positive, only the entries
    * which fit into the range specified are returned. If there are no such entries, an empty vector is
    * returned.
    */
   vector<EntryType> peekWithSamePriority(long maxNumOfEntries=-1, long maxNumOfBytes=-1) const;

   /**
    * Deletes the entries specified in the vector in the argument list. If this vector is empty or if
    * the queue is empty, zero (0) is returned, otherwise it returns the number of entries really deleted.
    */
   long randomRemove(vector<EntryType>::const_iterator start, vector<EntryType>::const_iterator end);

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

