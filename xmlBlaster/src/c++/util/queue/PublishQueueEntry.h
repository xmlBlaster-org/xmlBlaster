/*------------------------------------------------------------------------------
Name:      PublishQueueEntry.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#ifndef _UTIL_QUEUE_PUBLISHQUEUEENRY_H
#define _UTIL_QUEUE_PUBLISHQUEUEENRY_H

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

class Dll_Export PublishQueueEntry : public MsgQueueEntry
{
public:

   /**
    * Constructor suited for operations like publishes
    */
   PublishQueueEntry(const MessageUnit& msgUnit);

   /**
    * gets the content of this queue entry (the embedded object). In
    * persistent queues this is the data which is stored as a blob.
    */
   void* getEmbeddedObject();

   // this should actually be in another interface but since it is an only method we put it here.
   MsgQueueEntry& send(I_XmlBlasterConnection& connection);

   MessageUnit getMsgUnit() const;

   PublishReturnQos getPublishReturnQos() const;

};

}}}} // namespace

#endif

