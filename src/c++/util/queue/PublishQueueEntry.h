/*------------------------------------------------------------------------------
Name:      PublishQueueEntry.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#ifndef _UTIL_QUEUE_PUBLISHQUEUEENRY_H
#define _UTIL_QUEUE_PUBLISHQUEUEENRY_H

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

class Dll_Export PublishQueueEntry : public org::xmlBlaster::util::queue::MsgQueueEntry
{
public:

   /**
    * Constructor. You can provide a name different from 'publish'.
    * Normally the entry has the priority specified in the org::xmlBlaster::client::qos::PublishQos of the message unit. However, if you
    * pass a non-negative priority, it will be taken as the priority of this entry, in other words, the
    * priority of the message unit will be ignored.
    * @param type Only "MSG_RAW|publish" is supported
    */
   PublishQueueEntry(org::xmlBlaster::util::Global& global,
                     const org::xmlBlaster::util::MessageUnit& msgUnit,
                     int priority=NORM_PRIORITY,
                     org::xmlBlaster::util::Timestamp uniqueId = TimestampFactory::getInstance().getTimestamp());

   ~PublishQueueEntry();

   /**
    * copy constructor
    */
   PublishQueueEntry(const PublishQueueEntry& entry);

   /**
    * assignment constructor
    */
   PublishQueueEntry& operator =(const PublishQueueEntry& entry);

   MsgQueueEntry *getClone() const;

   /**
    * @param type "MSG_RAW|publish"
    std::string getEmbeddedType() const { return org::xmlBlaster::util::Constants::ENTRY_TYPE_MSG_RAW + "|" + org::xmlBlaster::util::MethodName::PUBLISH }
    */

   // this should actually be in another interface but since it is an only method we put it here.
   const org::xmlBlaster::util::queue::MsgQueueEntry& send(org::xmlBlaster::util::dispatch::I_ConnectionsHandler& connectionsHandler) const;

   size_t getSizeInBytes() const;

   org::xmlBlaster::client::qos::PublishReturnQos &getPublishReturnQos() const;

   std::string toXml(const std::string& indent="") const;

};

}}}} // namespace

#endif

