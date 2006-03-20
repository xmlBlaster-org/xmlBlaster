/*------------------------------------------------------------------------------
Name:      I_ConnectionsHandler.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Extended Interface to org::xmlBlaster::client::protocol::I_XmlBlasterConnections for ConnectionHandler
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

// circular dependency I_ConnectionsHandler -> org::xmlBlaster::util::queue::I_Queue -> org::xmlBlaster::util::queue::MsgQueueEntry
#ifndef _UTIL_QUEUE_I_QUEUE_H
namespace org { namespace xmlBlaster { namespace util { namespace queue {
class I_Queue;
}}}}
#endif

namespace org { namespace xmlBlaster { namespace util { namespace dispatch {

enum States {START, ALIVE, POLLING, DEAD, END};

class Dll_Export I_ConnectionsHandler : public org::xmlBlaster::client::protocol::I_XmlBlasterConnection
{
public:
   virtual ~I_ConnectionsHandler() {}

   /**
    * Flushes all entries in the queue, i.e. the entries of the queue are sent to xmlBlaster.
    * If the queue is empty or NULL, then 0 is returned. If the state is in POLLING or DEAD, then -1 is
    * returned.. This method blocks until all entries in the queue have been sent.
    */
   virtual long flushQueue() = 0;

   /**
    * gets a pointer to the queue used.
    */
   virtual org::xmlBlaster::util::queue::I_Queue* getQueue() = 0;

   /**
    * Returns true if the connection is in failsafe mode. You can activate this mode by invoking initFailsafe
    * in org::xmlBlaster::client::XmlBlasterAccess.
    */
   virtual bool isFailsafe() const = 0;

   virtual bool isConnected() const = 0;

   virtual org::xmlBlaster::util::qos::ConnectReturnQosRef connectRaw(const org::xmlBlaster::util::qos::ConnectQosRef& connectQos) = 0;

   virtual org::xmlBlaster::client::protocol::I_XmlBlasterConnection& getConnection() const = 0;

   virtual org::xmlBlaster::util::qos::ConnectReturnQosRef getConnectReturnQos() = 0;

   virtual org::xmlBlaster::util::qos::ConnectQosRef getConnectQos() = 0;

};


}}}} // namespaces

#endif
