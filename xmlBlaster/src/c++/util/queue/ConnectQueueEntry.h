/*------------------------------------------------------------------------------
Name:      ConnectQueueEntry.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#ifndef _UTIL_QUEUE_CONNECTQUEUEENRY_H
#define _UTIL_QUEUE_CONNECTQUEUEENRY_H

#include <util/queue/MsgQueueEntry.h>

/**
 * Class embedding messages or information to be stored on the client queues
 * Note that all content is copied when passed to the constructors.
 * This way this queue entry is the owner of the content (and therefore will
 * delete it when its destructor is called).
 *
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 */
namespace org { namespace xmlBlaster { namespace util { namespace queue {

class Dll_Export ConnectQueueEntry : public org::xmlBlaster::util::queue::MsgQueueEntry
{
public:

   /**
    * Constructor suited for operations like publishes
    */
   ConnectQueueEntry(org::xmlBlaster::util::Global& global,
                     const org::xmlBlaster::util::qos::ConnectQos& connectQos,
                     const std::string& type=org::xmlBlaster::util::MethodName::CONNECT,
                     int priority=MAX_PRIORITY,
                     bool persistent=false);

   MsgQueueEntry *getClone() const;

   /**
    * gets the content of this queue entry (the embedded object). In
    * persistent queues this is the data which is stored as a blob.
    */
   const void* getEmbeddedObject() const;

   // this should actually be in another interface but since it is an only method we put it here.
   const org::xmlBlaster::util::queue::MsgQueueEntry& send(org::xmlBlaster::util::dispatch::I_ConnectionsHandler& connectionsHandler) const;

   org::xmlBlaster::util::qos::ConnectQos getConnectQos() const;

   org::xmlBlaster::util::qos::ConnectReturnQos getConnectReturnQos() const;

   std::string toXml(const std::string& indent="") const;

};

}}}} // namespace

#endif

