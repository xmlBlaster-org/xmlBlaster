/*------------------------------------------------------------------------------
Name:      DispatchManager.cpp
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

#include <util/dispatch/DispatchManager.h>
//  #include <util/dispatch/ConnectionsHandler.h>
#ifdef COMPILE_CORBA_PLUGIN
#  include <client/protocol/corba/CorbaDriverFactory.h>
#endif
#ifdef COMPILE_SOCKET_PLUGIN
#  include <client/protocol/socket/SocketDriverFactory.h>
#endif
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util { namespace dispatch {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::client::protocol;

DispatchManager::DispatchManager(Global& global)
   : ME("DispatchManager"),
     global_(global),
     log_(global.getLog("org.xmlBlaster.util.dispatch"))
{
   if (log_.call()) log_.call(ME, "::constructor");
}


DispatchManager::~DispatchManager()
{
   if (log_.call()) log_.call(ME, "::destructor");
}

void DispatchManager::releasePlugin(const string& instanceName, const string& type, const string& version)
{
   if (log_.call()) log_.call(ME, "::releasePlugin");
   if (log_.trace())
      log_.trace(ME, string("releasePlugin: type: '") + type + string("', version: '") + version + "' for instance '" + instanceName + "'");
   if (type == Constants::IOR) {
#     ifdef COMPILE_CORBA_PLUGIN
      org::xmlBlaster::client::protocol::corba::CorbaDriverFactory::getFactory(global_).killDriverInstance(&global_);
#     endif
      return;
   }
   else if (type == Constants::SOCKET) {
#     ifdef COMPILE_SOCKET_PLUGIN
      org::xmlBlaster::client::protocol::socket::SocketDriverFactory::getFactory(global_).killDriverInstance(&global_);
      return;
#     endif
   }
   string embeddedMsg = string("plugin: '") + type +
                        string("' and version: '") + version +
                        string("' not supported");
   throw XmlBlasterException(RESOURCE_CONFIGURATION_PLUGINFAILED,
                    "client-c++",
                    ME + string("::releasePlugin"),
                    "en",
                    global_.getVersion() + " " + global_.getBuildTimestamp(),
                    "",
                    "",
                    embeddedMsg);
}

I_XmlBlasterConnection& DispatchManager::getPlugin(const string& instanceName, const string& type, const string& version)
{
   if (log_.call()) log_.call(ME, "::getPlugin");
   if (log_.trace())
      log_.trace(ME, string("getPlugin: type: '") + type + string("', version: '") + version + "' for instance '" + instanceName + "'");
   
   if (type == Constants::IOR) {
#     ifdef COMPILE_CORBA_PLUGIN
      return org::xmlBlaster::client::protocol::corba::CorbaDriverFactory::getFactory(global_).getDriverInstance(&global_);
#     endif
   }
   else if (type == Constants::SOCKET) {
#     ifdef COMPILE_SOCKET_PLUGIN
      return org::xmlBlaster::client::protocol::socket::SocketDriverFactory::getFactory(global_).getDriverInstance(&global_);
#     endif
   }

   // add here other protocols ....

   string embeddedMsg = string("plugin: '") + type +
                        string("' and version: '") + version +
                        string("' not supported");
   throw XmlBlasterException(RESOURCE_CONFIGURATION_PLUGINFAILED,
                    "client-c++",
                    ME + string("::getPlugin"),
                    "en",
                    global_.getVersion() + " " + global_.getBuildTimestamp(),
                    "",
                    "",
                    embeddedMsg);
}


ConnectionsHandler* DispatchManager::getConnectionsHandler(const string& instanceName)
{
   // it makes sense to have one per XmlBlasterAccess (must be destructed by the invoker of this method !!!)
   return new ConnectionsHandler(global_, instanceName);
}


}}}} // namespaces

