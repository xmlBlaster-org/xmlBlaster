/*------------------------------------------------------------------------------
Name:      CbServerPluginManager.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Manager to retrieve the correct callback protocol implementation
------------------------------------------------------------------------------*/

/**
 * It returns the appropriate implementation of the I_CallbackServer
 * interface (note that this is a class in c++) for the given protocol.
 * with your own lowlevel SOCKET or CORBA coding as well.
 *
 * @see org.xmlBlaster.client.protocol.I_CallbackServer
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>.
 */

#ifndef _CLIENT_PROTOCOL_CBSERVERPLUGINMANAGER_H
#define _CLIENT_PROTOCOL_CBSERVERPLUGINMANAGER_H

#include <util/xmlBlasterDef.h>
#include <util/Log.h>
#include <util/XmlBlasterException.h>
#include <client/protocol/I_CallbackServer.h>
#include <string>
#include <map>

using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace client { namespace protocol {

typedef map<string, I_CallbackServer*> ServerMap;

class Dll_Export CbServerPluginManager
{

private:
   const string ME;
   Global&      global_;
   Log&         log_;
   ServerMap    serverMap_;

public:
   CbServerPluginManager(Global& global);

   ~CbServerPluginManager();

   I_CallbackServer& getPlugin(const string& instanceName, const string& type, const string& version);
};

#define _CBSERVERPLUGINMANAGER_CLASS

}}}} // namespaces

#endif
