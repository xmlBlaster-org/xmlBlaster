/*------------------------------------------------------------------------------
Name:      SubscribeQueueEntry.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#ifndef _UTIL_QUEUE_SUBSCRIBEQUEUEENRY_H
#define _UTIL_QUEUE_SUBSCRIBEQUEUEENRY_H

#include <util/MethodName.h>
#include <util/queue/MsgQueueEntry.h>
#include <client/qos/SubscribeQos.h>
#include <client/qos/SubscribeReturnQos.h>
#include <client/key/SubscribeKey.h>



/**
 * Class embedding messages or information to be stored on the client queues
 * Note that all content is copied when passed to the constructors.
 * This way this queue entry is the owner of the content (and therefore will
 * delete it when its destructor is called).
 *
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 */
namespace org { namespace xmlBlaster { namespace util { namespace queue {

class Dll_Export SubscribeQueueEntry : public org::xmlBlaster::util::queue::MsgQueueEntry
{
public:

   /**
    * Constructor suited for operations like subscribe and unSubscribe
    */
   SubscribeQueueEntry(org::xmlBlaster::util::Global& global,
                       const org::xmlBlaster::client::key::SubscribeKey& subscribeKey,
                       const org::xmlBlaster::client::qos::SubscribeQos& subscribeQos,
                       const std::string& methodName=org::xmlBlaster::util::Constants::ENTRY_TYPE_MSG_RAW + "|" + org::xmlBlaster::util::MethodName::SUBSCRIBE,
                       int priority=MAX_PRIORITY, // 9
                       bool persistent=false,
                       org::xmlBlaster::util::Timestamp uniqueId = TimestampFactory::getInstance().getTimestamp());

   MsgQueueEntry *getClone() const;

   /**
    * gets the content of this queue entry (the embedded object). In
    * persistent queues this is the data which is stored as a blob.
    */
   const void* getEmbeddedObject() const;

   // this should actually be in another interface but since it is an only method we put it here.
   const org::xmlBlaster::util::queue::MsgQueueEntry& send(org::xmlBlaster::util::dispatch::I_ConnectionsHandler& connectionsHandler) const;

   size_t getSizeInBytes() const;

   org::xmlBlaster::client::qos::SubscribeQos getSubscribeQos() const;

   org::xmlBlaster::client::key::SubscribeKey getSubscribeKey() const;
 
   org::xmlBlaster::client::qos::SubscribeReturnQos getSubscribeReturnQos() const;

   std::string toXml(const std::string& indent="") const;

};

}}}} // namespace

#endif

