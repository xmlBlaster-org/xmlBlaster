/*------------------------------------------------------------------------------
Name:      UnSubscribeQueueEntry.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#ifndef _UTIL_QUEUE_UNSUBSCRIBEQUEUEENRY_H
#define _UTIL_QUEUE_UNSUBSCRIBEQUEUEENRY_H

#include <util/queue/MsgQueueEntry.h>
#include <client/qos/UnSubscribeQos.h>
#include <client/qos/UnSubscribeReturnQos.h>
#include <client/key/UnSubscribeKey.h>

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

class Dll_Export UnSubscribeQueueEntry : public MsgQueueEntry
{
public:

   /**
    * Constructor suited for operations like subscribe and unSubscribe
    */
   UnSubscribeQueueEntry(Global& global, const UnSubscribeKey& unSubscribeKey, const UnSubscribeQos& unSubscribeQos, const string& type="unSubscribe", int priority=9, bool persistent=false);

   /**
    * gets the content of this queue entry (the embedded object). In
    * persistent queues this is the data which is stored as a blob.
    */
   void* getEmbeddedObject();

   // this should actually be in another interface but since it is an only method we put it here.
   MsgQueueEntry& send(I_ConnectionsHandler& connectionsHandler);

   UnSubscribeQos getUnSubscribeQos() const;

   UnSubscribeKey getUnSubscribeKey() const;
 
   UnSubscribeReturnQos getUnSubscribeReturnQos() const;

   virtual string toXml(const string& indent="") const;

};

}}}} // namespace

#endif

