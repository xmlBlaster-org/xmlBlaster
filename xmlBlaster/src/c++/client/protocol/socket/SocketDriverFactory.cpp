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

SocketDriverFactory::SocketDriverFactory(Global& global)
   : ME("SocketDriverFactory"), 
     drivers_(),
     getterMutex_()
{
   I_Log& log = global.getLog("org.xmlBlaster.client.protocol.socket");
   if (log.call()) log.call(ME, string("Constructor"));

   //int args                 = global_.getArgs();
   //const char * const* argc = global_.getArgc();
}

SocketDriverFactory::SocketDriverFactory(const SocketDriverFactory& factory)
: ME(factory.ME), 
  drivers_(),
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
   thread::Lock lock(getterMutex_);
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

   if (log.call()) log.call(ME, string("getInstance for ") + instanceName);
   SocketDriver*  driver = NULL;
   int count = 1;
   {
      thread::Lock lock(getterMutex_);
      DriversMap::iterator iter = drivers_.find(global);
      if (iter == drivers_.end()) {
         if (log.trace()) log.trace(ME, string("created a new instance for ") + instanceName);
         driver = new SocketDriver(*global, instanceName);
         // initially the counter is set to 1
         drivers_.insert(DriversMap::value_type(global, pair<SocketDriver*, int>(driver, 1)));
      }
      else {
         driver = ((*iter).second).first;
         count = ((*iter).second).second++; // this is the counter ...
      }
   }
   if (log.trace()) 
      log.trace(ME, string("number of instances for '") + instanceName + "' are " + lexical_cast<std::string>(count));
   return *driver;
}


int SocketDriverFactory::killDriverInstance(Global* global)
{
   string instanceName = lexical_cast<string>(global);
   I_Log& log = global->getLog("org.xmlBlaster.client.protocol.socket");

   log.call(ME, string("killDriverInstance with a total of ") + lexical_cast<string>(drivers_.size()) + " instances, looking for global " + global->getId() + " instanceName=" + instanceName);
   thread::Lock lock(getterMutex_);
   DriversMap::iterator iter = drivers_.find(global);
   if (iter == drivers_.end()) return -1;
   int ret = --(*iter).second.second;
   if (log.trace()) log.trace(ME, string("instances before deleting ") + lexical_cast<std::string>(ret+1));
   if (ret <= 0) {
      if (log.trace()) log.trace(ME, string("kill instance '") + instanceName + "' will be deleted now");
      // do remove it since the counter is zero
      SocketDriver* driver = (*iter).second.first;
      drivers_.erase(iter);
      delete driver;
   }
   if (log.trace()) 
      log.trace(ME, string("kill instance '") + instanceName + "' the number of references is " + lexical_cast<std::string>(ret));
   return ret;
}

void SocketDriverFactory::run()
{
   // if (log_.trace()) log_.trace(ME, "the socket loop starts now");
}


}}}}} // namespaces

