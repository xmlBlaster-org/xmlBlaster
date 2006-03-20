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
#include <util/lexical_cast.h>

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace corba {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;

CorbaDriverFactory::CorbaDriverFactory(Global& global, CORBA::ORB_ptr orb)
   : Thread(), 
     ME("CorbaDriverFactory"), 
     drivers_(),
     mutex_(),
     getterMutex_(),
     orbIsThreadSafe_(ORB_IS_THREAD_SAFE)
{
   org::xmlBlaster::util::I_Log& log = global.getLog("org.xmlBlaster.client.protocol.corba");
   if (log.call()) 
      log.call("CorbaDriver", string("Constructor orbIsThreadSafe_=") + lexical_cast<std::string>(orbIsThreadSafe_));
   doRun_     = true;
   isRunning_ = false;

   if (orb) {
      orb_ = orb;
      isOwnOrb_ = false;
   }
   else {
      int args                 = global.getArgs();
      const char * const* argc = global.getArgc();
      orb_ = CORBA::ORB_init(args, const_cast<char **>(argc));
      isOwnOrb_ = true;
   }
}

CorbaDriverFactory::CorbaDriverFactory(const CorbaDriverFactory& factory)
: Thread(), 
  ME(factory.ME), 
  drivers_(),
  doRun_(true),
  isRunning_(false),
  mutex_(),
  getterMutex_(),
  orbIsThreadSafe_(factory.orbIsThreadSafe_),
  orb_(factory.orb_),
  isOwnOrb_(factory.isOwnOrb_)
{
   throw XmlBlasterException(INTERNAL_NOTIMPLEMENTED, ME, "private copy constructor");
}

CorbaDriverFactory& CorbaDriverFactory::operator =(const CorbaDriverFactory&)
{
   throw XmlBlasterException(INTERNAL_NOTIMPLEMENTED, ME, "private assignement operator");
}

CorbaDriverFactory::~CorbaDriverFactory()
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
   if (!orbIsThreadSafe_) { // stop the running thread
      if (isRunning_) {
        //if (log_.trace()) log_.trace(ME, "stopping the thread which performs orb work");
        doRun_ = false;
        join();
      }
   }
   if (isOwnOrb_) {
      if (!CORBA::is_nil(orb_)) {
         //if (log_.trace()) log_.trace(ME, "shutting down the orb");
         orb_->shutdown(true);
#if      !(defined(_WINDOWS) && defined(XMLBLASTER_TAO))
         //if (log_.trace()) log_.trace(ME, "destroying the orb");
         orb_->destroy();         // blocks forever on Windows XP VC7 with TAO 1.3
#endif
         //if (log_.trace()) log_.trace(ME, "releasing the orb");
         CORBA::release(orb_);
      }                                 
   }
   //if (log_.trace()) log_.trace(ME, "Destructor end");
}

CorbaDriverFactory* CorbaDriverFactory::factory_ = NULL;

CorbaDriverFactory& CorbaDriverFactory::getFactory(Global& global, CORBA::ORB_ptr orb)
{
   //static CorbaDriverFactory factory(global, orb);
   //return factory;
   if(factory_ == NULL)
   {
     factory_ = new CorbaDriverFactory(global, orb);
     org::xmlBlaster::util::Object_Lifetime_Manager::instance()->manage_object("XB_CorbaDriverFactory", factory_);  // if not pre-allocated.
   }
   return *factory_;
}

CorbaDriver& CorbaDriverFactory::getDriverInstance(Global* global)
{
   std::string instanceName = lexical_cast<std::string>(global);
   I_Log& log = global->getLog("org.xmlBlaster.client.protocol.corba");
   if (log.call()) log.call("CorbaDriver", string("getInstance for ") + instanceName);
   CorbaDriver*  driver = NULL;
   int count = 1;
   {
      Lock lock(getterMutex_);
      DriversMap::iterator iter = drivers_.find(global);
      if (iter == drivers_.end()) {
         if (log.trace()) log.trace("CorbaDriver", string("created a new instance for ") + instanceName);

         CORBA::ORB_ptr orb = CORBA::ORB::_duplicate(orb_);
         driver = new CorbaDriver(*global, mutex_, instanceName, orb);
         // initially the counter is set to 1
         drivers_.insert(DriversMap::value_type(global, pair<CorbaDriver*, int>(driver, 1)));

			// In a thread to support single thread orb->performwork to dispatch corba main loop
			const bool detached = false;
         if (!isRunning_) start(detached); // if threadSafe isRunning_ will never be set to true
      }
      else {
         driver = ((*iter).second).first;
         count = ((*iter).second).second++; // this is the counter ...
      }
   }
   if (log.trace()) 
      log.trace("CorbaDriver", string("number of instances for '") + instanceName + "' are " + lexical_cast<std::string>(count));
   return *driver;
}


int CorbaDriverFactory::killDriverInstance(Global* global)
{
   std::string instanceName = lexical_cast<std::string>(global);
   I_Log& log = global->getLog("org.xmlBlaster.client.protocol.corba");
   log.call(ME, "killDriverInstance");
   Lock lock(getterMutex_);
   DriversMap::iterator iter = drivers_.find(global);
   if (iter == drivers_.end()) return -1;
   int ret = --(*iter).second.second;
   if (log.trace()) log.trace(ME, string("instances before deleting ") + lexical_cast<std::string>(ret));
   if (ret <= 0) {
      if (log.trace()) log.trace(ME, string("kill instance '") + instanceName + "' will be deleted now");
      // do remove it since the counter is zero
      CorbaDriver* driver = (*iter).second.first;
      drivers_.erase(iter);
      delete driver;
      if (drivers_.empty() && isOwnOrb_) {
         if (!orbIsThreadSafe_) {
            if (isRunning_) {
               doRun_ = false;
               join(); // wait until the run thread has returned ...
            }
         }
//         orb_->shutdown(true);
//         orb_->destroy();
         return 0;
      }
   }
   if (log.trace()) 
      log.trace("CorbaDriver", string("kill instance '") + instanceName + "' the number of references is " + lexical_cast<std::string>(ret));
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
   //if (log_.trace()) log_.trace(ME, "the corba loop starts now");
   if (!isOwnOrb_) return;

   if (orbIsThreadSafe_) {
      orbRun(); // e.g. TAO
   }
   else {
      doRun_ = true;    // e.g. MICO
      if (isRunning_) return;
      //log_.info(ME, "the corba loop starts now");
      isRunning_ = true;

      try {
         while (doRun_) {
            {  // this is for the scope of the lock ...
               Lock lock(mutex_, orbIsThreadSafe_);
               //if (log_.trace()) log_.trace(ME, "sweep in running thread");
               while (orb_->work_pending()) orb_->perform_work();
            }
            //if (log_.trace()) log_.trace(ME, "sleeping for 20 millis");
            sleep(20); // sleep 20 milliseconds
            //if (log_.trace()) log_.trace(ME, string("awakening, doRun is: ") + lexical_cast<std::string>(doRun_));
         }
      }
      catch(CORBA::Exception &ex) {
         //log_.warn(ME, string("a corba exception occured in the running thread. It has now been stopped: ") + to_string(ex));
         std::cerr << ME << " " << string("a corba exception occured in the running thread. It has now been stopped: ") << to_string(ex) << std::endl;
      }
      catch (exception &ex) {
         //log_.warn(ME, string("an exception occured in the running thread. It has now been stopped: ") + ex.what());
         std::cerr << ME << string("an exception occured in the running thread. It has now been stopped: ") << ex.what() << std::endl;
      }

      catch (...) {
         //log_.warn(ME, "an unknown exception occured in the running thread. It has now been stopped");
         std::cerr << ME << "an unknown exception occured in the running thread. It has now been stopped" << std::endl;
      }

      //log_.info(ME, "the corba loop has ended now");
      isRunning_ = false;
   }
}


}}}}} // namespaces

