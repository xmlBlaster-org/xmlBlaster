/*------------------------------------------------------------------------------
Name:      SubscribeQueueEntry.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#ifndef _UTIL_QUEUE_SUBSCRIBEQUEUEENRY_H
#define _UTIL_QUEUE_SUBSCRIBEQUEUEENRY_H

#include <util/queue/MsgQueueEntry.h>
#include <client/qos/SubscribeQos.h>
#include <client/qos/SubscribeReturnQos.h>
#include <client/key/SubscribeKey.h>

using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

/**
 * Class embedding messages or information to be stored on the client queues
 * Note that all content is copied when passed to the constructors.
 * This way this queue entry is the owner of the content (and therefore will
 * delete it when its destructor is called).
 *
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 */
namespace org { namespace xmlBlaster { namespace util { namespace queue {

class Dll_Export SubscribeQueueEntry : public MsgQueueEntry
{
public:

   /**
    * Constructor suited for operations like subscribe and unSubscribe
    */
   SubscribeQueueEntry(Global& global, const SubscribeKey& subscribeKey, const SubscribeQos& subscribeQos, const string& type="subscribe", int priority=9, bool durable=false);

   /**
    * gets the content of this queue entry (the embedded object). In
    * persistent queues this is the data which is stored as a blob.
    */
   void* getEmbeddedObject();

   // this should actually be in another interface but since it is an only method we put it here.
   MsgQueueEntry& send(I_XmlBlasterConnection& connection);

   SubscribeQos getSubscribeQos() const;

   SubscribeKey getSubscribeKey() const;
 
   SubscribeReturnQos getSubscribeReturnQos() const;

   string toXml(const string& indent="") const;

};

}}}} // namespace

#endif

