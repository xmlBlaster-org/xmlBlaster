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
#include <util/dispatch/ConnectionsHandler.h>
#include <client/protocol/corba/CorbaDriver.h>
#include <util/Global.h>

using namespace org::xmlBlaster::util;
using org::xmlBlaster::client::protocol::corba::CorbaDriver;

namespace org { namespace xmlBlaster { namespace util { namespace dispatch {

DeliveryManager::DeliveryManager(Global& global)
   : ME("DeliveryManager"),
     global_(global),
     log_(global.getLog("dispatch"))
//     serverMap_()
{
   if (log_.CALL) log_.call(ME, "::constructor");
//   connectionsHandler_ = NULL;
}


DeliveryManager::~DeliveryManager()
{
   if (log_.CALL) log_.call(ME, "::destructor");

   ServerMap::iterator iter = serverMap_.begin();
   while (iter != serverMap_.end()) {
      if (log_.TRACE)
         log_.trace(ME, string("destructor: deleting type: '") + (*iter).first + string("'"));
      I_XmlBlasterConnection* el = (*iter).second;
      serverMap_.erase(iter);
//      delete el; ---> not owned by this container !!!
      iter = serverMap_.begin();
   }
}

I_XmlBlasterConnection& DeliveryManager::getPlugin(const string& instanceName, const string& type, const string& version)
{
   if (log_.CALL) log_.call(ME, "::getPlugin");
   if (log_.TRACE)
      log_.trace(ME, string("getPlugin: type: '") + type + string("', version: '") + version + string("'") /* + " for instance '" + instanceName + "'"*/);
   
   string completeName = /*string(instanceName) + "/" + */ type + "/" + version; 
   if (type == "IOR") {
      ServerMap::iterator iter = serverMap_.find(completeName);
      if (iter == serverMap_.end()) {
         corba::CorbaDriver* driver =   &corba::CorbaDriver::getInstance(global_, instanceName);
         // probably notify the dispatcher framework here since they
         // share the same object.
         ServerMap::value_type el(completeName, driver);
         serverMap_.insert(el);
         iter = serverMap_.find(completeName);
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


ConnectionsHandler* DeliveryManager::getConnectionsHandler(const string& instanceName)
{
/*
   if (connectionsHandler_ == NULL) {
      connectionsHandler_ = new ConnectionsHandler(global_, *this);
   }
   return *connectionsHandler_;
*/  
   // it makes sense to have one per XmlBlasterAccess (must be destructed by the invoker of this method !!!)
   return new ConnectionsHandler(global_, *this, instanceName);
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
    DeliveryManager manager = glob.getDeliveryManager();
    try {
       I_XmlBlasterConnection& conn = manager.getPlugin("IOR", "1.0");
    }
    catch (XmlBlasterException &ex) {
       cout << ex.toXml() << endl;
       cout << "exception occured when retrieving a correct callback server" << endl;
       assert(0);
    }
    try {
       I_XmlBlasterConnection& conn = manager.getPlugin("SOCKET", "1.0");
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


