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
#include <util/queue/I_Queue.h>
#include <util/XmlBlasterException.h>
#include <vector>

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
    * Called after a messages is send asynchronously from connection queue.
    * Is triggered for oneway messages as well (PUBLISH_ONEWAY, UPDATE_ONEWAY) 
    * @param entries, the MsgQueueEntry instances includes the returned QoS
    * contains org::xmlBlaster::util::queue::MsgQueueEntry &msgQueueEntry
    * @see typedef ReferenceHolder<MsgQueueEntry> EntryType
    * @example
    * <pre>
    * vector<EntryType>::const_iterator iter = entries.begin();
    * while (iter != entries.end()) {
    *   const MsgQueueEntry &entry = *(*iter);
    *   ...
    * }
    * </pre>
    */
   virtual void postSend(const std::vector<org::xmlBlaster::util::queue::EntryType> &entries) = 0;
   
   /**
    * Called if an asynchronous message is rejected by the server. 
    * <p>
    * If the server e.g. throws an IllegalArgument back to the client
    * the message will most probably never succeed and retrying to send
    * the message makes no sense. You can intercept this case
    * here and eliminate the message.
    * <p>
    * ErrorCodes of type "communication.*" are not reported here
    * as the dispatcher framework automatically handles reconnect and retry.
    * <p>
    * NOTE:
    * For ErrorCodes of type "authentication.*" the connection will
    * go to DEAD and the connection queue entries remain for pubSessionId>0.
    * 
    * For pubSessionId<0 (none fail safe) the queue entries are removed (to be implemented TODO).  
    *  
    * @param entries Each MsgQueueEntry includes the returned QoS
    * @param exception The cause
    * 
    * @return 
    * false: We have not handled this case and the dispatcher framework
    * does its default handling (which is currently hard coded and the connection goes to dead).
    * For the client it is just a notification.
    * 
    * true: We have processed some error handling and the dispatch framework
    * will remove the message from the queue and continue with sending the next message.
    * This is for example done internally by cluster client plugins inside cluster nodes
    * which will propagate the message to the error handler which emits a dead message.
    *
    * @see typedef ReferenceHolder<MsgQueueEntry> EntryType
    */
   virtual bool sendingFailed(const std::vector<org::xmlBlaster::util::queue::EntryType> &entries, const org::xmlBlaster::util::XmlBlasterException &exception) = 0;
};


}}}} // namespaces

#endif /*I_POSTSENDLISTENER_*/
