/*------------------------------------------------------------------------------
Name:      CbServerPluginManager.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Manager to retrieve the correct callback protocol implementation
------------------------------------------------------------------------------*/

/**
 * It returns the appropriate implementation of the org::xmlBlaster::client::protocol::I_CallbackServer
 * interface (note that this is a class in c++) for the given protocol.
 * with your own lowlevel SOCKET or CORBA coding as well.
 *
 * @see org.xmlBlaster.client.protocol.I_CallbackServer
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>.
 */

#ifndef _CLIENT_PROTOCOL_CBSERVERPLUGINMANAGER_H
#define _CLIENT_PROTOCOL_CBSERVERPLUGINMANAGER_H

#include <util/xmlBlasterDef.h>
#include <util/I_Log.h>
#include <util/XmlBlasterException.h>
#include <client/protocol/I_CallbackServer.h>
#include <string>

namespace org { namespace xmlBlaster { namespace client { namespace protocol {

class Dll_Export CbServerPluginManager
{

private:
   const std::string ME;
   org::xmlBlaster::util::Global&      global_;
   org::xmlBlaster::util::I_Log&         log_;

public:
   CbServerPluginManager(org::xmlBlaster::util::Global& global);

   ~CbServerPluginManager();

   org::xmlBlaster::client::protocol::I_CallbackServer& getPlugin(const std::string& instanceName, const std::string& type, const std::string& version);

   void releasePlugin(const std::string& instanceName, const std::string& type, const std::string& version);

};

}}}} // namespaces

#endif
