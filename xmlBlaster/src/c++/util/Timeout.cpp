/*-----------------------------------------------------------------------------
Name:      Timeout.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Allows you be called back after a given delay.
-----------------------------------------------------------------------------*/
#include <algorithm>
#include <string>

#include <util/Timeout.h>
#include <boost/thread/thread.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/thread/condition.hpp>
#include <boost/thread/xtime.hpp>
#include <boost/lexical_cast.hpp>
#include <util/Constants.h>
#include <util/Global.h>

using boost::lexical_cast;

namespace org { namespace xmlBlaster { namespace util {

   Timeout::Timeout(Global& global)
      : ME("Timeout"), threadName_("Timeout-Thread"),
        timeoutMap_(), isRunning_(false), isReady_(false), isActive_(true),
        isDebug_(false), timestampFactory_(TimestampFactory::getInstance()),
        global_(global), log_(global.getLog("util"))
   {
      // the thread will only be instantiated when starting
      log_.call(ME, " default constructor");
      runningThread_ = NULL;

      invocationMutex_ = new boost::mutex();
      waitForTimeoutMutex_ = new boost::mutex();
      waitForTimeoutCondition_ = new boost::condition();
      log_.trace(ME, " default constructor: after creating timeout condition");
   }

   Timeout::Timeout(Global& global, const string &name)
      : ME("Timeout"), threadName_("Timeout-Thread"),
        timeoutMap_(), isRunning_(false), isReady_(false), isActive_(true),
        isDebug_(false), timestampFactory_(TimestampFactory::getInstance()),
        global_(global), log_(global.getLog("util"))
   {
      // the thread remains uninitialized ...
      log_.call(ME, " alternative constructor");
      runningThread_ = NULL;
      invocationMutex_ = new boost::mutex();
      waitForTimeoutMutex_ = new boost::mutex();
      waitForTimeoutCondition_ = new boost::condition();
   }

   Timeout::~Timeout() {
      log_.call(ME, " destructor");
      shutdown();
      while (isActive_) { } // wait for the thread to finish
      delete invocationMutex_;
      delete waitForTimeoutMutex_;
      delete waitForTimeoutCondition_;
      delete runningThread_;
   }


   void Timeout::start() {
      log_.call(ME, " start");
      isRunning_ = true;
      log_.trace(ME, " before creating the running thread");
      runningThread_ = new boost::thread(TimeoutRunner(*this));
      log_.trace(ME, " start: running thread created");
   }

   void Timeout::join() {
      runningThread_->join();
      log_.trace(ME, " start: running thread joined (i.e. thread started)");
   }

   Timestamp Timeout::addTimeoutListener(I_Timeout *listener, long delay, void *userData) {
      log_.call(ME, " addTimeoutListener");
      std::cout << ME << " addTimeoutListener" << std::endl;
      Timestamp key = 0;
      if (delay < 1) std::cerr << ME <<": addTimeoutListener with delay = " << delay << std::endl;
      int nanoCounter = 0;
      
      boost::mutex::scoped_lock lock(*invocationMutex_);
      while (true) {
         key = timestampFactory_.getTimestamp() + Constants::MILLION * delay;
         TimeoutMap::iterator iter = timeoutMap_.find(key);
         if (iter == timeoutMap_.end()) {
            log_.trace(ME, "addTimeoutListener, adding key: " + lexical_cast<string>(key));
            Container cont(listener, userData);
            TimeoutMap::value_type el(key, cont);
            timeoutMap_.insert(el);
            break;
         }
      }
      waitForTimeoutCondition_->notify_one();
      return key;
   }

   Timestamp Timeout::refreshTimeoutListener(Timestamp key, long delay) {
      log_.call(ME, " refreshTimeoutListener");
      std::cout << ME << " refreshTimeoutListener" << std::endl;
      if (key < 0)
         // throw an exception here ...
      Timestamp newKey = 0;
      boost::mutex::scoped_lock lock(*invocationMutex_);
      TimeoutMap::iterator iter = timeoutMap_.find(key);
      if (iter == timeoutMap_.end()) {
          // throw the exception here ...
         // throw new XmlBlasterException(ME, "The timeout handle '" + key + "' is unknown, no timeout refresh done");
         std::cerr << ME << "The timeout handle '" << key <<"' is unknown, no timeout refresh done" << std::endl;
         return -1; // temporarly. Change this once exception is thrown
      }
      timeoutMap_.erase(key);
      I_Timeout *callback = (*iter).second.first;
      void *userData = (*iter).second.second;
      return addTimeoutListener(callback, delay, userData);
   }

   Timestamp Timeout::addOrRefreshTimeoutListener(I_Timeout *listener, long delay, void *userData, Timestamp key) {
      std::cout << ME << " addOrRefreshTimeoutListener" << std::endl;
      log_.call(ME, " addOrRefreshTimeoutListener");
      boost::mutex::scoped_lock lock(*invocationMutex_);
      if (key < 0) return addTimeoutListener(listener, delay, NULL);
      return refreshTimeoutListener(key, delay);
   }

   void Timeout::removeTimeoutListener(Timestamp key) {
      log_.call(ME, " removeTimeoutListener");
      boost::mutex::scoped_lock lock(*invocationMutex_);
      timeoutMap_.erase(key);
   }

   bool Timeout::isExpired(Timestamp key) {
      log_.call(ME, " isExpired");
      std::cout << ME << "isExpired" << std::endl;
      boost::mutex::scoped_lock lock(*invocationMutex_);
      return (timeoutMap_.find(key) == timeoutMap_.end());
   }

   long Timeout::spanToTimeout(Timestamp key) {
      log_.call(ME, " spanToTimeout");
      std::cout << ME << " spanToTimeout" << std::endl;
      boost::mutex::scoped_lock lock(*invocationMutex_);
      TimeoutMap::iterator iter = timeoutMap_.find(key);
      if (iter == timeoutMap_.end()) return -1;
      Timestamp currentTimestamp = timestampFactory_.getTimestamp();
      return getTimeout(key) - (long)(currentTimestamp / Constants::MILLION);
   }

   long Timeout::getTimeout(Timestamp key) {
      log_.call(ME, " getTimeout");
      std::cout << ME << " getTimeout" << std::endl;
      if (key < 0) return -1;
      return (long)(key / Constants::MILLION);
   }

   void Timeout::removeAll() {
      log_.call(ME, " removeAll");
      std::cout << ME << " removeAll" << std::endl;
      boost::mutex::scoped_lock lock(*invocationMutex_);
      timeoutMap_.clear();
   }

   void Timeout::shutdown() {
      log_.call(ME, " shutdown");
      std::cout << ME << " shutdown" << std::endl;
      isRunning_ = false;
      removeAll();
      waitForTimeoutCondition_->notify_one();
   }


//-------------------- and the timeout runner .... ----------------------------


   TimeoutRunner::TimeoutRunner(Timeout &ref) : reference_(ref), ME("TimeoutRunner") {
   }

   void TimeoutRunner::operator()() {
       
      reference_.log_.call(ME, " run: operator ()");
      reference_.isActive_ = true;

      Container *container = NULL;
      Container tmpContainer;
      
      while (reference_.isRunning_) {
         boost::mutex::scoped_lock waitForTimeoutLock(*reference_.waitForTimeoutMutex_);

         reference_.log_.trace(ME, " operator (): is running");
         double delay = 100000.0; // sleep veeery long

         boost::xtime timeToWait;
         boost::xtime_get(&timeToWait, boost::TIME_UTC);

         {
            boost::mutex::scoped_lock lock(*reference_.invocationMutex_);
            TimeoutMap::iterator iter = reference_.timeoutMap_.begin();
            if (iter == reference_.timeoutMap_.end()) {
               reference_.log_.warn(ME, " The timeout is empty");
            }
            else {
               reference_.log_.trace(ME, " The timeout is not empty");
               Timestamp nextWakeup = (*iter).first;
               reference_.log_.trace(ME, "run, next event (Timestamp): " + lexical_cast<string>(nextWakeup) + " ms");
               double next = nextWakeup / Constants::MILLION;
               double current = reference_.timestampFactory_.getTimestamp() / Constants::MILLION;
               delay = next - current;

               reference_.log_.trace(ME, "run, next event  : " + lexical_cast<string>(next) + " ms");
               reference_.log_.trace(ME, "run, current time: " + lexical_cast<string>(current) + " ms");
               reference_.log_.trace(ME, "run, delay       : " + lexical_cast<string>(delay) + " ms");
               if (delay <= 1.0e-9) {
                  tmpContainer = (*iter).second;
                  reference_.timeoutMap_.erase((*iter).first);
                  container = &tmpContainer;
                  if (reference_.isDebug_)
                     std::cout << reference_.ME << " Timeout occurred, calling listener with real time error of " << delay << " millis" << std::endl;
               }
            }
         }
         // must be outside the sync
         if (container != NULL) {
             (container->first)->timeout(container->second);
             container = NULL;
         }
         {
//            boost::mutex::scoped_lock waitForTimeoutLock(*reference_.waitForTimeoutMutex_);
            long int sec = (long int)(delay / Constants::THOUSAND);
            long int nano = (long int)((delay - sec*Constants::THOUSAND)*Constants::MILLION);
            timeToWait.sec  +=  sec;
            timeToWait.nsec += nano;
            reference_.log_.trace(ME, "sleeping ... " + lexical_cast<string>(sec) + " seconds and " + lexical_cast<string>(nano));
            reference_.isReady_ = true;
            reference_.waitForTimeoutCondition_->timed_wait(waitForTimeoutLock, timeToWait);
            reference_.log_.trace(ME, "waking up .. ");
         }
      }
      reference_.log_.trace(ME, "the running thread is exiting");
      reference_.isActive_ = false;
   }


}}}; // namespaces

