/*------------------------------------------------------------------------------
Name:      DispatchManager.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Manager to retrieve the correct delivery protocol
------------------------------------------------------------------------------*/

/**
 * It returns the appropriate implementation of the org::xmlBlaster::client::protocol::I_XmlBlasterConnection
 * interface (note that this is a class in c++) for the given protocol.
 * with your own lowlevel SOCKET or CORBA coding as well.
 *
 * @see org.xmlBlaster.client.protocol.I_XmlBlasterConnection
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>.
 */

#ifndef _UTIL_DISPATCH_DELIVERYMANAGER_H
#define _UTIL_DISPATCH_DELIVERYMANAGER_H

#include <util/xmlBlasterDef.h>
#include <util/XmlBlasterException.h>
#include <client/protocol/I_XmlBlasterConnection.h>
#include <util/dispatch/ConnectionsHandler.h>
#include <string>
// #include <map>



namespace org { namespace xmlBlaster { namespace util { namespace dispatch {

class Dll_Export DispatchManager
{

private:
   const std::string        ME;
   org::xmlBlaster::util::Global&             global_;
   org::xmlBlaster::util::I_Log&                log_;

public:
   DispatchManager(org::xmlBlaster::util::Global& global);

   ~DispatchManager();

   org::xmlBlaster::client::protocol::I_XmlBlasterConnection& getPlugin(const std::string& instanceName, const std::string& type, const std::string& version);

   org::xmlBlaster::util::dispatch::ConnectionsHandler* getConnectionsHandler(const std::string& instanceName);
   
   void releasePlugin(const std::string& instanceName, const std::string& type, const std::string& version);

};

}}}} // namespaces

#endif
