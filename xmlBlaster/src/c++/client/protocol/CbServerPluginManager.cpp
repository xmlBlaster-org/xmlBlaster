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
#include <client/protocol/corba/CorbaDriver.h>

using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace client { namespace protocol {

CbServerPluginManager::CbServerPluginManager(Global& global)
   : ME("CbServerPluginManager"),
     global_(global),
     log_(global.getLog("client")),
     serverMap_()
{
   if (log_.CALL) log_.call(ME, "::constructor");
}


CbServerPluginManager::~CbServerPluginManager()
{
   if (log_.CALL) log_.call(ME, "::destructor");
   // should be synchronized ...
   ServerMap::iterator iter = serverMap_.begin();
   while (iter != serverMap_.end()) {
      if (log_.TRACE)
         log_.trace(ME, string("destructor: deleting type: '") + (*iter).first + string("'"));
      I_CallbackServer* el = (*iter).second;
      serverMap_.erase(iter);
      delete el;
      iter = serverMap_.begin();
   }
}

I_CallbackServer& CbServerPluginManager::getPlugin(const string& type, const string& version)
{
   if (log_.CALL) log_.call(ME, "::getPlugin");
   if (log_.TRACE)
      log_.trace(ME, string("getPlugin: type: '") + type + string("', version: '") + version + string("'"));
    if (type == "IOR") {
      ServerMap::iterator iter = serverMap_.find(type);
      if (iter == serverMap_.end()) {
         corba::CorbaDriver* driver = new corba::CorbaDriver(global_);
         // probably notify the dispatcher framework here since they
         // share the same object.
         ServerMap::value_type el(type, driver);
         serverMap_.insert(el);
         iter = serverMap_.find(type);
      }
      return *((*iter).second);
   }
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
       I_CallbackServer& cbServer = manager.getPlugin("IOR", "1.0");
    }
    catch (XmlBlasterException &ex) {
       cout << ex.toXml() << endl;
       cout << "exception occured when retrieving a correct callback server" << endl;
       assert(0);
    }
    try {
       I_CallbackServer& cbServer = manager.getPlugin("SOCKET", "1.0");
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


