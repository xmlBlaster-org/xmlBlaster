/*------------------------------------------------------------------------------
Name:      I_ConnectionsHandler.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Extended Interface to I_XmlBlasterConnections for ConnectionHandler
------------------------------------------------------------------------------*/

/**
 * Interface for XmlBlaster, the supported methods on c++ client side. This is
 * a pure virtual class.
 * <p />
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 */


#ifndef _UTIL_DISPATCH_ICONNECTIONSHANDLER_H
#define _UTIL_DISPATCH_ICONNECTIONSHANDLER_H

#include <util/xmlBlasterDef.h>
#include <client/protocol/I_XmlBlasterConnection.h>
#include <util/queue/MsgQueue.h>

using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::protocol;
using namespace org::xmlBlaster::util::queue;

namespace org { namespace xmlBlaster { namespace util { namespace dispatch {

enum States {START, CONNECTED, POLLING, DEAD, END};

class Dll_Export I_ConnectionsHandler : public I_XmlBlasterConnection
{
public:

   /**
    * Flushes all entries in the queue, i.e. the entries of the queue are sent to xmlBlaster.
    * If the queue is empty or NULL, then 0 is returned. If the state is in POLLING or DEAD, then -1 is
    * returned.. This method blocks until all entries in the queue have been sent.
    */
   virtual long flushQueue() = 0;

   /**
    * gets a pointer to the queue used.
    */
   virtual MsgQueue* getQueue() = 0;

   virtual bool isFailsafe() const = 0;

   virtual bool isConnected() const = 0;

};


}}}} // namespaces

#endif
