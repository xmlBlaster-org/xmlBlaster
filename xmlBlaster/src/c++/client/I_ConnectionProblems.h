/*------------------------------------------------------------------------------
Name:      I_ConnectionProblems.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages
------------------------------------------------------------------------------*/

/**
 * Callback the client from XmlBlasterConnection if the connection to xmlBlaster is lost
 * or was reestablished (failsafe mode).
 * <p>
 * @version $Revision: 1.10 $
 * @author <a href='xmlBlaster@marcelruff.info'>Marcel Ruff</a>
 * @author <a href='laghi@swissinfo.org'>Michele Laghi</a>
 */

#ifndef _CLIENT_ICONNECTIONPROBLEMS_H
#define _CLIENT_ICONNECTIONPROBLEMS_H

#include <util/dispatch/I_ConnectionsHandler.h>
//namespace org { namespace xmlBlaster { namespace util { namespace dispatch {
//   class I_ConnectionsHandler;
//}}}}

namespace org { namespace xmlBlaster { namespace client {

typedef enum org::xmlBlaster::util::dispatch::States StatesEnum;

class Dll_Export I_ConnectionProblems
{
public:
   virtual ~I_ConnectionProblems() {}

   /**
    * This is the callback method invoked from org::xmlBlaster::util::dispatch::ConnectionsHandler
    * notifying the client that a connection has been established and that its status is now ALIVE.
    * It has a bool return which informs the org::xmlBlaster::util::dispatch::ConnectionsHandler what to do with the entries in the queue. 
    * If you return 'true', then the queue is flushed (i.e. the contents of the queue are sent to 
    * xmlBlaster). If you return 'false', then the contents of the queue are left untouched. You can then 
    * erase all entries manually. Note that this method is invoked also when the connection has been 
    * established the first time.
    */
   virtual bool reachedAlive(StatesEnum oldState,
                  org::xmlBlaster::util::dispatch::I_ConnectionsHandler* connectionsHandler) = 0;

   /**
    * This is the callback method invoked from org::xmlBlaster::util::dispatch::ConnectionsHandler
    * informing the client that the connection was lost (i.e. when the state of the
    * connectionsHandler has gone to DEAD).
    */
   virtual void reachedDead(StatesEnum oldState,
                  org::xmlBlaster::util::dispatch::I_ConnectionsHandler* connectionsHandler) = 0;

   /**
    * This is the callback method invoked from org::xmlBlaster::util::dispatch::ConnectionsHandler
    * informing the client that the connection state has changed to POLLING.
    */
   virtual void reachedPolling(StatesEnum oldState,
                  org::xmlBlaster::util::dispatch::I_ConnectionsHandler* connectionsHandler) = 0;

};

}}} // namespace 

#endif



