/*------------------------------------------------------------------------------
Name:      DeliveryManager.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Manager to retrieve the correct delivery protocol
------------------------------------------------------------------------------*/

/**
 * It returns the appropriate implementation of the I_XmlBlasterConnection
 * interface (note that this is a class in c++) for the given protocol.
 * with your own lowlevel SOCKET or CORBA coding as well.
 *
 * @see org.xmlBlaster.client.protocol.I_XmlBlasterConnection
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>.
 */

#ifndef _UTIL_DISPATCH_DELIVERYMANAGER_H
#define _UTIL_DISPATCH_DELIVERYMANAGER_H

#include <util/xmlBlasterDef.h>
#include <util/Log.h>
#include <util/XmlBlasterException.h>
#include <client/protocol/I_XmlBlasterConnection.h>
#include <util/dispatch/ConnectionsHandler.h>
#include <string>
// #include <map>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::client::protocol;

namespace org { namespace xmlBlaster { namespace util { namespace dispatch {

class Dll_Export DeliveryManager
{

private:
   const string        ME;
   Global&             global_;
   Log&                log_;

public:
   DeliveryManager(Global& global);

   ~DeliveryManager();

   I_XmlBlasterConnection& getPlugin(const string& instanceName, const string& type, const string& version);

   ConnectionsHandler* getConnectionsHandler(const string& instanceName);
   
   void releasePlugin(const string& instanceName, const string& type, const string& version);

};

}}}} // namespaces

#endif
