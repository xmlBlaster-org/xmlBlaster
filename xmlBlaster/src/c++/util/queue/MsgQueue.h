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
#include <util/queue/MsgQueueEntry.h>
#include <util/ReferenceHolder.h>
#include <util/qos/storage/QueueProperty.h>
#include <util/Log.h>
#include <set>

using namespace std;
using namespace org::xmlBlaster::util;
using org::xmlBlaster::util::qos::storage::QueueProperty;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

typedef ReferenceHolder<MsgQueueEntry> EntryType;
typedef set<EntryType>                 StorageType;

class Dll_Export MsgQueue
{
private:
   const string  ME;
   Global&       global_;
   Log&          log_;
   QueueProperty property_;
   StorageType   storage_;
   long          numOfBytes_;

public:
   MsgQueue(Global& global, const QueueProperty& property);
   
   ~MsgQueue();
    
   /**
    * puts a new entry into the queue. Note that this method takes the entry pointed to by the argument 
    * and puts a reference to it into the queue. This means that you can not destroy the entry before the
    * reference to it has been removed from the queue (which normally happens on a remove or when destroying
    * the queue.
    */
   void put(MsgQueueEntry *entry);

   /**
    * Returns the entries with the highest priority in the queue. If 'maxNumOfEntries' is positive,
    * this is the maximum number of entries to return. If maxNumOfBytes is positive, only the entries
    * which fit into the range specified are returned. If there are no such entries, an empty vector is
    * returned.
    */
   vector<MsgQueueEntry*> peekWithSamePriority(long maxNumOfEntries=-1, long maxNumOfBytes=-1) const;

   /**
    * Deletes the entries specified in the vector in the argument list. If this vector is empty or if
    * the queue is empty, zero (0) is returned, otherwise it returns the number of entries really deleted.
    */
   long randomRemove(const vector<MsgQueueEntry*>& entries);

};

}}}} // namespace

#endif

