/*------------------------------------------------------------------------------
Name:      CorbaDriver.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The client driver for the corba protocol
------------------------------------------------------------------------------*/

#include <client/protocol/corba/CorbaDriverFactory.h>
#include <util/ErrorCode.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <boost/lexical_cast.hpp>

using namespace org::xmlBlaster::util;
using namespace boost;

using namespace std;

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace corba {

CorbaDriverFactory::CorbaDriverFactory(Global& global, CORBA::ORB_ptr orb)
   : Thread(), 
     ME("CorbaDriverFactory"), 
     global_(global), 
     log_(global_.getLog("corba")),
     drivers_(),
     mutex_(),
     getterMutex_(),
#    if MICO
     orbIsThreadSafe_(false)
#    elif TAO
     orbIsThreadSafe_(true)
#    else
     orbIsThreadSafe_(true)
#    endif
{
   if (log_.call()) 
      log_.call("CorbaDriver", string("Constructor orbIsThreadSafe_=") + lexical_cast<string>(orbIsThreadSafe_));
   doRun_     = true;
   isRunning_ = false;

   if (orb) {
      orb_ = orb;
      isOwnOrb_ = false;
   }
   else {
      int args                 = global_.getArgs();
      const char * const* argc = global_.getArgc();
      orb_ = CORBA::ORB_init(args, const_cast<char **>(argc));
      isOwnOrb_ = true;
   }
}

CorbaDriverFactory::CorbaDriverFactory(const CorbaDriverFactory& factory)
: Thread(), 
  ME(factory.ME), 
  global_(factory.global_),
  log_(factory.log_),
  drivers_(),
  doRun_(true),
  isRunning_(false),
  mutex_(),
  getterMutex_(),
  orbIsThreadSafe_(factory.orbIsThreadSafe_),
  orb_(factory.orb_),
  isOwnOrb_(factory.isOwnOrb_)
{
   throw new XmlBlasterException(INTERNAL_NOTIMPLEMENTED, ME, "private copy constructor");
}

CorbaDriverFactory& CorbaDriverFactory::operator =(const CorbaDriverFactory&)
{
   throw new XmlBlasterException(INTERNAL_NOTIMPLEMENTED, ME, "private assignement operator");
}

CorbaDriverFactory::~CorbaDriverFactory()
{
   Lock lock(getterMutex_);
   DriversMap::iterator iter = drivers_.begin();
   while (iter != drivers_.end()) {
      delete ((*iter).second).first;
      iter++;
   }
   drivers_.erase(drivers_.begin(), drivers_.end());
   if (!orbIsThreadSafe_) { // stop the running thread
      if (isRunning_) {
        doRun_ = false;
        join();
      }
   }
   if (isOwnOrb_) {
      if (CORBA::is_nil(orb_)) {
         orb_->destroy();
         CORBA::release(orb_);
      }                                 
   }
}

CorbaDriverFactory& CorbaDriverFactory::getFactory(Global& global, CORBA::ORB_ptr orb)
{
   static CorbaDriverFactory factory(global, orb);
   return factory;
}

CorbaDriver& CorbaDriverFactory::getDriverInstance(const string& instanceName)
{
   if (log_.call()) log_.call("CorbaDriver", string("getInstance for ") + instanceName);
   CorbaDriver*  driver = NULL;
   int count = 1;
   {
      Lock lock(getterMutex_);
      DriversMap::iterator iter = drivers_.find(instanceName);
      if (iter == drivers_.end()) {
         if (log_.trace()) log_.trace("CorbaDriver", string("created a new instance for ") + instanceName);
         driver = new CorbaDriver(global_, mutex_, doRun_, isRunning_, instanceName, orb_);
         // initially the counter is set to 1
         drivers_.insert(DriversMap::value_type(instanceName, pair<CorbaDriver*, int>(driver, 1)));
         if (!isRunning_) start(); // if threadSafe isRunning_ will never be set to true
      }
      else {
         driver = ((*iter).second).first;
         count = ((*iter).second).second++; // this is the counter ...
      }
   }
   if (log_.trace()) 
      log_.trace("CorbaDriver", string("number of instances for '") + instanceName + "' are " + lexical_cast<string>(count));
   return *driver;
}


int CorbaDriverFactory::killDriverInstance(const string& instanceName)
{
   Lock lock(getterMutex_);
   DriversMap::iterator iter = drivers_.find(instanceName);
   if (iter == drivers_.end()) return -1;
   int ret = --(*iter).second.second;
   log_.trace(ME, string("instances before deleting ") + lexical_cast<string>(ret));
   if (ret <= 0) {
      log_.trace(ME, string("kill instance '") + instanceName + "' will be deleted now");
      // do remove it since the counter is zero
      CorbaDriver* driver = (*iter).second.first;
      drivers_.erase(iter);
      delete driver;
      if (drivers_.empty()) {
         if (!orbIsThreadSafe_) {
            if (isRunning_) {
               doRun_ = false;
               join(); // wait until the run thread has returned ...
            }
         }
         return 0;
      }
   }
   log_.trace("CorbaDriver", string("kill instance '") + instanceName + "' the number of references is " + lexical_cast<string>(ret));
   return ret;
}

bool CorbaDriverFactory::orbRun()
{
   if (orb_ == NULL) return false;
   orb_->run();
   return true;
}

void CorbaDriverFactory::run()
{
   if (log_.trace()) log_.trace(ME, "the corba loop starts now");
   if (orbIsThreadSafe_) {
      orbRun(); // e.g. TAO
   }
   else {
      doRun_ = true;    // e.g. MICO
      if (isRunning_) return;
      log_.info(ME, "the corba loop starts now");
      isRunning_ = true;
      while (doRun_) {
         { // this is for the scope of the lock ...
            Lock lock(mutex_, orbIsThreadSafe_);
            if (log_.trace()) log_.trace(ME, "sweep in running thread");
            while (orb_->work_pending()) orb_->perform_work();
         }
         if (log_.trace()) log_.trace(ME, "sleeping for 20 millis");
         sleep(20); // sleep 20 milliseconds
         if (log_.trace()) log_.trace(ME, "waiking up");
      }
      log_.info(ME, "the corba loop has ended now");
      isRunning_ = false;
   }
}


}}}}} // namespaces

