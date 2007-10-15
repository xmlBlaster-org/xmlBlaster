/*------------------------------------------------------------------------------
Name:      I_PostSendListener.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Is called when asynchronously messages are send from the client side queue
------------------------------------------------------------------------------*/
#ifndef I_POSTSENDLISTENER_
#define I_POSTSENDLISTENER_

#include <util/xmlBlasterDef.h>
#include <util/queue/MsgQueueEntry.h>

namespace org { namespace xmlBlaster { namespace util { namespace dispatch {

/**
 * Notify when a message is successfully send asynchronously. 
 * Does not notify for oneway messages (PUBLISH_ONEWAY, UPDATE_ONEWAY) 
 * Is called when asynchronously messages are send from the client side queue
 * This is a pure virtual class.
 * <p />
 * @author Marcel Ruff
 */
class Dll_Export I_PostSendListener
{
public:
   virtual ~I_PostSendListener() {}

   /**
    * Called after a messages is send, but not for oneway messages. 
    * @param msgQueueEntry, includes the returned QoS
    */
   virtual void postSend(const org::xmlBlaster::util::queue::MsgQueueEntry &msgQueueEntry) = 0;
};


}}}} // namespaces

#endif /*I_POSTSENDLISTENER_*/
