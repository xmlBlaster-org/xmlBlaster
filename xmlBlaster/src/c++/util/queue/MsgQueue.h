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

#include <util/queue/Queue.h>
#include <util/queue/PublishQueueEntry.h>
#include <util/queue/ConnectQueueEntry.h>
#include <util/queue/SubscribeQueueEntry.h>
#include <util/queue/UnSubscribeQueueEntry.h>
#include <util/dispatch/I_ConnectionsHandler.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::util::qos::storage;
using namespace org::xmlBlaster::util::dispatch;


namespace org { namespace xmlBlaster { namespace util { namespace queue {

class Dll_Export MsgQueue : public Queue
{

public:
   MsgQueue(Global& global, const ClientQueueProperty& property);

   MsgQueue(const MsgQueue& queue);

   MsgQueue& operator =(const MsgQueue& queue);
   
   void put(const PublishQueueEntry& entry);

   void put(const ConnectQueueEntry& entry);

   void put(const SubscribeQueueEntry& entry);

   void put(const UnSubscribeQueueEntry& entry);

};

}}}} // namespace

#endif

