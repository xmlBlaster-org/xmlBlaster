/*------------------------------------------------------------------------------
Name:      DeliveryManager.cpp
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

#include <util/dispatch/DeliveryManager.h>
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

DeliveryManager::DeliveryManager(Global& global)
   : ME("DeliveryManager"),
     global_(global),
     log_(global.getLog("dispatch"))
{
   if (log_.call()) log_.call(ME, "::constructor");
}


DeliveryManager::~DeliveryManager()
{
   if (log_.call()) log_.call(ME, "::destructor");
}

void DeliveryManager::releasePlugin(const string& instanceName, const string& type, const string& version)
{
   if (log_.call()) log_.call(ME, "::releasePlugin");
   if (log_.trace())
      log_.trace(ME, string("releasePlugin: type: '") + type + string("', version: '") + version + "' for instance '" + instanceName + "'");
   if (type == "IOR") {
#     ifdef COMPILE_CORBA_PLUGIN
      org::xmlBlaster::client::protocol::corba::CorbaDriverFactory::getFactory(global_).killDriverInstance(instanceName);
#     endif
      return;
   }
   else if (type == "SOCKET") {
#     ifdef COMPILE_SOCKET_PLUGIN
      org::xmlBlaster::client::protocol::socket::SocketDriverFactory::getFactory(global_).killDriverInstance(instanceName);
      return;
#     endif
   }
   string embeddedMsg = string("plugin: '") + type +
                        string("' and version: '") + version +
                        string("' not supported");
   throw new XmlBlasterException(RESOURCE_CONFIGURATION_PLUGINFAILED,
                    "client-c++",
                    ME + string("::releasePlugin"),
                    "en",
                    "client-c++",
                    "",
                    "",
                    embeddedMsg);
}

I_XmlBlasterConnection& DeliveryManager::getPlugin(const string& instanceName, const string& type, const string& version)
{
   if (log_.call()) log_.call(ME, "::getPlugin");
   if (log_.trace())
      log_.trace(ME, string("getPlugin: type: '") + type + string("', version: '") + version + "' for instance '" + instanceName + "'");
   
   if (type == "IOR") {
#     ifdef COMPILE_CORBA_PLUGIN
      return org::xmlBlaster::client::protocol::corba::CorbaDriverFactory::getFactory(global_).getDriverInstance(instanceName);
#     endif
   }
   else if (type == "SOCKET") {
#     ifdef COMPILE_SOCKET_PLUGIN
      return org::xmlBlaster::client::protocol::socket::SocketDriverFactory::getFactory(global_).getDriverInstance(instanceName);
#     endif
   }

   // add here other protocols ....

   string embeddedMsg = string("plugin: '") + type +
                        string("' and version: '") + version +
                        string("' not supported");
   throw new XmlBlasterException(RESOURCE_CONFIGURATION_PLUGINFAILED,
                    "client-c++",
                    ME + string("::getPlugin"),
                    "en",
                    "client-c++",
                    "",
                    "",
                    embeddedMsg);
}


ConnectionsHandler* DeliveryManager::getConnectionsHandler(const string& instanceName)
{
   // it makes sense to have one per XmlBlasterAccess (must be destructed by the invoker of this method !!!)
   return new ConnectionsHandler(global_, instanceName);
}


}}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST

#include <assert.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::client::protocol;

/** For testing:  */
int main(int args, char* argv[])
{
    Global& glob = Global::getInstance();
    glob.initialize(args, argv);
    DeliveryManager manager = glob.getDeliveryManager();
    try {
       I_XmlBlasterConnection& conn = manager.getPlugin("IOR", "1.0");
    }
    catch (XmlBlasterException &ex) {
       cout << ex.toXml() << endl;
       cout << "exception occured when retrieving a Corba callback server" << endl;
       assert(0);
    }
    try {
       I_XmlBlasterConnection& conn = manager.getPlugin("SOCKET", "1.0");
    }
    catch (XmlBlasterException &ex) {
       cout << ex.toXml() << endl;
       cout << "exception occured when retrieving a SOCKET callback server" << endl;
    }

   return 0;
}

#endif


