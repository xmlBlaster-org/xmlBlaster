/*------------------------------------------------------------------------------
Name:      ConnectQueueEntry.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#ifndef _UTIL_QUEUE_CONNECTQUEUEENRY_H
#define _UTIL_QUEUE_CONNECTQUEUEENRY_H

#include <util/MethodName.h>
#include <util/queue/MsgQueueEntry.h>

/**
 * Class embedding messages or information to be stored on the client queues
 * Note that all content is copied when passed to the constructors.
 * This way this queue entry is the owner of the content (and therefore will
 * delete it when its destructor is called).
 *
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 * @author <a href='mailto:xmlblast@marcelruff.info'>Marcel Ruff</a>
 */
namespace org { namespace xmlBlaster { namespace util { namespace queue {

class Dll_Export ConnectQueueEntry : public org::xmlBlaster::util::queue::MsgQueueEntry
{
   /**
    * Holds the serialized information which is returned by getEmbeddedObject(),
    * encoded according to embeddedType
    */
   mutable BlobHolder blobHolder_;

   /**
    * assignment constructor
    */
   ConnectQueueEntry& operator =(const ConnectQueueEntry& entry);

public:

   /**
    * Constructor suited for operations like publishes
    * @param type Only "MSG_RAW|connect" is supported
    */
   ConnectQueueEntry(org::xmlBlaster::util::Global& global,
                     const org::xmlBlaster::util::qos::ConnectQos& connectQos,
                     const std::string& type=org::xmlBlaster::util::Constants::ENTRY_TYPE_MSG_RAW + "|" + org::xmlBlaster::util::MethodName::CONNECT,
                     int priority=MAX_PRIORITY,
                     bool persistent=false,
                     org::xmlBlaster::util::Timestamp uniqueId = TimestampFactory::getInstance().getTimestamp());

   /**
    * copy constructor
    */
   ConnectQueueEntry(const ConnectQueueEntry& entry);

   MsgQueueEntry *getClone() const;

   ~ConnectQueueEntry();

   /**
    * gets the content of this queue entry (the embedded object). In
    * persistent queues this is the data which is stored as a blob.
    */
   const void* getEmbeddedObject() const;

   // this should actually be in another interface but since it is an only method we put it here.
   const org::xmlBlaster::util::queue::MsgQueueEntry& send(org::xmlBlaster::util::dispatch::I_ConnectionsHandler& connectionsHandler) const;

   size_t getSizeInBytes() const;

   org::xmlBlaster::util::qos::ConnectQos &getConnectQos() const;

   org::xmlBlaster::util::qos::ConnectReturnQos &getConnectReturnQos() const;

   std::string toXml(const std::string& indent="") const;

};

}}}} // namespace

#endif

