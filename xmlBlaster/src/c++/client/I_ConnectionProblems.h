/*------------------------------------------------------------------------------
Name:      I_ConnectionProblems.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages
Version:   $Id: I_ConnectionProblems.h,v 1.5 2003/01/17 18:38:54 laghi Exp $
------------------------------------------------------------------------------*/

/**
 * Callback the client from XmlBlasterConnection if the connection to xmlBlaster is lost
 * or was reestablished (fail save mode).
 * <p>
 * @version $Revision: 1.5 $
Author:    xmlBlaster@marcelruff.info
 * @author <a href='xmlBlaster@marcelruff.info'>Marcel Ruff</a>
 * @author <a href='laghi@swissinfo.org'>Michele Laghi</a>
 */

#ifndef _CLIENT_ICONNECTIONPROBLEMS_H
#define _CLIENT_ICONNECTIONPROBLEMS_H

#include <util/dispatch/I_ConnectionsHandler.h>

using namespace org::xmlBlaster::util::dispatch;

namespace org { namespace xmlBlaster { namespace client {

typedef enum States StatesEnum;

class I_ConnectionProblems
{
public:

   /**
    * This is the callback method invoked from ConnectionsHandler
    * notifying the client that a connection has been established and that its status is now ALIVE.
    * It has a bool return which informs the ConnectionsHandler what to do with the entries in the queue. 
    * If you return 'true', then the queue is flushed (i.e. the contents of the queue are sent to 
    * xmlBlaster). If you return 'false', then the contents of the queue are left untouched. You can then 
    * erase all entries manually. Note that this method is invoked also when the connection has been 
    * established the first time.
    */
   virtual bool reachedAlive(StatesEnum oldState, I_ConnectionsHandler* connectionsHandler) = 0;

   /**
    * This is the callback method invoked from ConnectionsHandler
    * informing the client that the connection was lost (i.e. when the state of the
    * connectionsHandler has gone to DEAD).
    */
   virtual void reachedDead(StatesEnum oldState, I_ConnectionsHandler* connectionsHandler) = 0;

   /**
    * This is the callback method invoked from ConnectionsHandler
    * informing the client that the connection state has changed to POLLING.
    */
   virtual void reachedPolling(StatesEnum oldState, I_ConnectionsHandler* connectionsHandler) = 0;

};

}}} // namespace 

#endif



