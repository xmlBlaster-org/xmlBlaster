/*------------------------------------------------------------------------------
Name:      SocketDriverFactory.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The client driver for the socket protocol
------------------------------------------------------------------------------*/
#include <util/ErrorCode.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <util/lexical_cast.h>
#include <client/protocol/socket/SocketDriverFactory.h>
#include <client/protocol/socket/SocketDriver.h>

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace socket {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;

SocketDriverFactory::SocketDriverFactory(Global& global)
   : Thread(), 
     ME("SocketDriverFactory"), 
     drivers_(),
     mutex_(),
     getterMutex_()
{
   I_Log& log = global.getLog("org.xmlBlaster.client.protocol.socket");
   if (log.call()) log.call("SocketDriver", string("Constructor"));
   doRun_     = true;
   isRunning_ = false;

   //int args                 = global_.getArgs();
   //const char * const* argc = global_.getArgc();
}

SocketDriverFactory::SocketDriverFactory(const SocketDriverFactory& factory)
: Thread(), 
  ME(factory.ME), 
  drivers_(),
  doRun_(true),
  isRunning_(false),
  mutex_(),
  getterMutex_()
{
   throw util::XmlBlasterException(INTERNAL_NOTIMPLEMENTED, ME, "private copy constructor");
}

SocketDriverFactory& SocketDriverFactory::operator =(const SocketDriverFactory&)
{
   throw util::XmlBlasterException(INTERNAL_NOTIMPLEMENTED, ME, "private assignement operator");
}

SocketDriverFactory::~SocketDriverFactory()
{
   //if (log_.call()) log_.call(ME, "Destructor start");
   Lock lock(getterMutex_);
   DriversMap::iterator iter = drivers_.begin();
   while (iter != drivers_.end()) {
      delete ((*iter).second).first;
      iter++;
   }
   drivers_.erase(drivers_.begin(), drivers_.end());
   //if (log_.trace()) log_.trace(ME, "erased all drivers");
}

SocketDriverFactory* SocketDriverFactory::factory_ = NULL;

SocketDriverFactory& SocketDriverFactory::getFactory(Global& global)
{
   if(factory_ == NULL)
   {
     factory_ = new SocketDriverFactory(global);
     org::xmlBlaster::util::Object_Lifetime_Manager::instance()->manage_object("XB_SocketDriverFactory", factory_);  // if not pre-allocated.
   }
   return *factory_;
}

SocketDriver& SocketDriverFactory::getDriverInstance(Global* global)
{
   string instanceName = lexical_cast<string>(global);
   I_Log& log = global->getLog("org.xmlBlaster.client.protocol.socket");

   if (log.call()) log.call("SocketDriver", string("getInstance for ") + instanceName);
   SocketDriver*  driver = NULL;
   int count = 1;
   {
      Lock lock(getterMutex_);
      DriversMap::iterator iter = drivers_.find(global);
      if (iter == drivers_.end()) {
         if (log.trace()) log.trace("SocketDriver", string("created a new instance for ") + instanceName);
         driver = new SocketDriver(*global, mutex_, instanceName);
         // initially the counter is set to 1
         drivers_.insert(DriversMap::value_type(global, pair<SocketDriver*, int>(driver, 1)));
         if (!isRunning_) start(); // if threadSafe isRunning_ will never be set to true
      }
      else {
         driver = ((*iter).second).first;
         count = ((*iter).second).second++; // this is the counter ...
      }
   }
   if (log.trace()) 
      log.trace("SocketDriver", string("number of instances for '") + instanceName + "' are " + lexical_cast<std::string>(count));
   return *driver;
}


int SocketDriverFactory::killDriverInstance(Global* global)
{
   string instanceName = lexical_cast<string>(global);
   I_Log& log = global->getLog("org.xmlBlaster.client.protocol.socket");

   log.call(ME, "killDriverInstance");
   Lock lock(getterMutex_);
   DriversMap::iterator iter = drivers_.find(global);
   if (iter == drivers_.end()) return -1;
   int ret = --(*iter).second.second;
   if (log.trace()) log.trace(ME, string("instances before deleting ") + lexical_cast<std::string>(ret));
   if (ret <= 0) {
      if (log.trace()) log.trace(ME, string("kill instance '") + instanceName + "' will be deleted now");
      // do remove it since the counter is zero
      SocketDriver* driver = (*iter).second.first;
      drivers_.erase(iter);
      delete driver;
   }
   if (log.trace()) 
      log.trace("SocketDriver", string("kill instance '") + instanceName + "' the number of references is " + lexical_cast<std::string>(ret));
   return ret;
}

void SocketDriverFactory::run()
{
   // if (log_.trace()) log_.trace(ME, "the socket loop starts now");
}


}}}}} // namespaces

