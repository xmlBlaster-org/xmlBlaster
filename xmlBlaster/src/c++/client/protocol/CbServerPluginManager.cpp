/*------------------------------------------------------------------------------
Name:      CbServerPluginManager.cpp
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

#include <client/protocol/CbServerPluginManager.h>
#ifdef COMPILE_CORBA_PLUGIN
#  include <client/protocol/corba/CorbaDriverFactory.h>
#endif
#ifdef COMPILE_SOCKET_PLUGIN
#   include <client/protocol/socket/SocketDriverFactory.h>
#endif
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace client { namespace protocol {

using namespace std;
using namespace org::xmlBlaster::util;

CbServerPluginManager::CbServerPluginManager(Global& global)
   : ME("CbServerPluginManager"),
     global_(global),
     log_(global.getLog("org.xmlBlaster.client"))
//     serverMap_()
{
   if (log_.call()) log_.call(ME, "::constructor");
}


CbServerPluginManager::~CbServerPluginManager()
{
   // should be synchronized ...
/*
   ServerMap::iterator iter = serverMap_.begin();
   while (iter != serverMap_.end()) {
      I_CallbackServer* el = (*iter).second;
      serverMap_.erase(iter);
      delete el;
      iter = serverMap_.begin();
   }
*/
}

I_CallbackServer& CbServerPluginManager::getPlugin(const string& instanceName, const string& type, const string& version)
{
   if (log_.call()) log_.call(ME, "::getPlugin");
   if (log_.trace())
      log_.trace(ME, string("getPlugin: type: '") + type + string("', version: '") + version + "' for instance '" + instanceName + "'");
//   string completeName = /*string(instanceName) + "/" + */ type + "/" + version;
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



void CbServerPluginManager::releasePlugin(const string& instanceName, const string& type, const string& version)
{
   if (log_.call()) log_.call(ME, "::releasePlugin");
   if (log_.trace())
      log_.trace(ME, string("releasePlugin: type: '") + type + string("', version: '") + version + "' for instance '" + instanceName + "'");
   if (type == Constants::IOR) {
#     ifdef COMPILE_CORBA_PLUGIN
      org::xmlBlaster::client::protocol::corba::CorbaDriverFactory::getFactory(global_).killDriverInstance(&global_);
      return;
#     endif
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
                    ME + string("::getPlugin"),
                    "en",
                    global_.getVersion() + " " + global_.getBuildTimestamp(),
                    "",
                    "",
                    embeddedMsg);
}


}}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST

#include <assert.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::client::protocol;

/** For testing: java org.xmlBlaster.authentication.plugins.simple.SecurityQos */
int main(int args, char* argv[])
{
    Global& glob = Global::getInstance();
    glob.initialize(args, argv);
    CbServerPluginManager manager = glob.getCbServerPluginManager();
    try {
       I_CallbackServer& cbServer = manager.getPlugin(Constants::IOR, "1.0");
    }
    catch (XmlBlasterException &ex) {
       cout << ex.toXml() << endl;
       cout << "exception occured when retrieving a correct callback server" << endl;
       assert(0);
    }
    try {
       I_CallbackServer& cbServer = manager.getPlugin(Constants::SOCKET, "1.0");
       cout << "The socket protocol is not implemented yet" << endl;
       assert(0);
    }
    catch (XmlBlasterException &ex) {
       cout << ex.toXml() << endl;
       cout << "The socket protocol is not implemented yet, so the exception was normal" << endl;
    }

   return 0;
}

#endif


