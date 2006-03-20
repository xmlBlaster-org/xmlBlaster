/*------------------------------------------------------------------------------
Name:      EraseQueueEntry.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#ifndef _UTIL_QUEUE_ERASEQUEUEENRY_H
#define _UTIL_QUEUE_ERASEQUEUEENRY_H

#include <util/MethodName.h>
#include <util/queue/MsgQueueEntry.h>
#include <client/qos/EraseQos.h>
#include <client/qos/EraseReturnQos.h>
#include <client/key/EraseKey.h>


/**
 * Class embedding messages or information to be stored on the client queues
 * Note that all content is copied when passed to the constructors.
 * This way this queue entry is the owner of the content (and therefore will
 * delete it when its destructor is called).
 *
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 */
namespace org { namespace xmlBlaster { namespace util { namespace queue {

class Dll_Export EraseQueueEntry : public org::xmlBlaster::util::queue::MsgQueueEntry
{
public:

   /**
    * Constructor for erase operations. 
    */
   EraseQueueEntry(org::xmlBlaster::util::Global& global,
                       const org::xmlBlaster::client::key::EraseKey& eraseKey,
                       const org::xmlBlaster::client::qos::EraseQos& eraseQos,
                       int priority=NORM_PRIORITY,
                       org::xmlBlaster::util::Timestamp uniqueId = TimestampFactory::getInstance().getTimestamp());

   ~EraseQueueEntry();

   /**
    * copy constructor
    */
   EraseQueueEntry(const EraseQueueEntry& entry);

   /**
    * assignment constructor
    */
   EraseQueueEntry& operator =(const EraseQueueEntry& entry);

   MsgQueueEntry *getClone() const;

   // this should actually be in another interface but since it is an only method we put it here.
   const org::xmlBlaster::util::queue::MsgQueueEntry& send(org::xmlBlaster::util::dispatch::I_ConnectionsHandler& connectionsHandler) const;

   /**
    * @return A copy of the erase QoS
    */
   org::xmlBlaster::client::qos::EraseQos getEraseQos() const;

   /**
    * @return A copy of the erase Key
    */
   org::xmlBlaster::client::key::EraseKey getEraseKey() const;
 
   org::xmlBlaster::client::qos::EraseReturnQos getEraseReturnQos() const;

   std::string toXml(const std::string& indent="") const;

};

}}}} // namespace

#endif

