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
    * Constructor. You can provide a name different from 'publish'.
    * Normally the entry has the priority specified in the PublishQos of the message unit. However, if you
    * pass a non-negative priority, it will be taken as the priority of this entry, in other words, the
    * priority of the message unit will be ignored.
    */
   PublishQueueEntry(Global& global, const MessageUnit& msgUnit, const string& type="publish", int priority=-1, bool persistent=false);

   /**
    * gets the content of this queue entry (the embedded object). In
    * persistent queues this is the data which is stored as a blob.
    */
   void* getEmbeddedObject();

   // this should actually be in another interface but since it is an only method we put it here.
   MsgQueueEntry& send(I_ConnectionsHandler& connectionsHandler);

   MessageUnit& getMsgUnit() const;

   PublishReturnQos getPublishReturnQos() const;

   string toXml(const string& indent="") const;

};

}}}} // namespace

#endif

