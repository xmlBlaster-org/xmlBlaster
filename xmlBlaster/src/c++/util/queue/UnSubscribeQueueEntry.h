/*------------------------------------------------------------------------------
Name:      UnSubscribeQueueEntry.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#ifndef _UTIL_QUEUE_UNSUBSCRIBEQUEUEENRY_H
#define _UTIL_QUEUE_UNSUBSCRIBEQUEUEENRY_H

#include <util/MethodName.h>
#include <util/queue/MsgQueueEntry.h>
#include <client/qos/UnSubscribeQos.h>
#include <client/qos/UnSubscribeReturnQos.h>
#include <client/key/UnSubscribeKey.h>

/**
 * Class embedding messages or information to be stored on the client queues
 * Note that all content is copied when passed to the constructors.
 * This way this queue entry is the owner of the content (and therefore will
 * delete it when its destructor is called).
 *
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 */
namespace org { namespace xmlBlaster { namespace util { namespace queue {

class Dll_Export UnSubscribeQueueEntry : public org::xmlBlaster::util::queue::MsgQueueEntry
{
public:

   /**
    * Constructor suited for operations like subscribe and unSubscribe
    */
   UnSubscribeQueueEntry(org::xmlBlaster::util::Global& global,
                         const org::xmlBlaster::client::key::UnSubscribeKey& unSubscribeKey,
                         const org::xmlBlaster::client::qos::UnSubscribeQos& unSubscribeQos,
                         int priority=NORM_PRIORITY,
                         org::xmlBlaster::util::Timestamp uniqueId = TimestampFactory::getInstance().getTimestamp());

   MsgQueueEntry *getClone() const;
   
   // this should actually be in another interface but since it is an only method we put it here.
   const org::xmlBlaster::util::queue::MsgQueueEntry& send(org::xmlBlaster::util::dispatch::I_ConnectionsHandler& connectionsHandler) const;

   org::xmlBlaster::client::qos::UnSubscribeQos getUnSubscribeQos() const;

   org::xmlBlaster::client::key::UnSubscribeKey getUnSubscribeKey() const;
 
   org::xmlBlaster::client::qos::UnSubscribeReturnQos getUnSubscribeReturnQos() const;

   virtual std::string toXml(const std::string& indent="") const;

};

}}}} // namespace

#endif

