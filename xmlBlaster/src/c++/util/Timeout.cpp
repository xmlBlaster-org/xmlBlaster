/*-----------------------------------------------------------------------------
Name:      Timeout.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Allows you be called back after a given delay.
-----------------------------------------------------------------------------*/
#include <util/Timeout.h>
#include <boost/thread/thread.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/thread/condition.hpp>
#include <boost/thread/xtime.hpp>

#include <algorithm>

namespace org { namespace xmlBlaster { namespace util {


   Timeout::Timeout(int args, const char * const argc[]) : ME("Timeout"), threadName_("Timeout-Thread"), 
      isRunning_(false), isReady_(false), isDebug_(false), timeoutMap_(),
      timestampFactory_(TimestampFactory::getInstance()), log_(args, argc) {
      // the thread will only be instantiated when starting 
      log_.initialize();
      log_.call(ME, " default constructor");
      runningThread_ = NULL;

      invocationMutex_ = new boost::mutex();
      waitForTimeoutMutex_ = new boost::mutex();
      waitForTimeoutCondition_ = new boost::condition();
      log_.trace(ME, " default constructor: after creating timeout condition");
   }

   Timeout::Timeout(const string &name, int args, const char * const argc[]) : ME("Timeout"), threadName_(name), 
        isRunning_(false), isReady_(false), isDebug_(false), timeoutMap_(),
        timestampFactory_(TimestampFactory::getInstance()), log_(args, argc) {
      // the thread remains uninitialized ...
      log_.initialize();
      log_.call(ME, " alternative constructor");
      runningThread_ = NULL;
      invocationMutex_ = new boost::mutex();
      waitForTimeoutMutex_ = new boost::mutex();
      waitForTimeoutCondition_ = new boost::condition();
   }

   Timeout::~Timeout() {
      log_.call(ME, " destructor");
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



   TimeoutRunner::TimeoutRunner(Timeout &ref) : reference_(ref), ME("TimeoutRunner") {
   }

   void TimeoutRunner::operator()() {
       
      reference_.log_.call(ME, " run: operator ()");

      Container *container = NULL;
      Container tmpContainer;
      
      while (reference_.isRunning_) {
         reference_.log_.trace(ME, " operator (): is running");
         long delay = 100000; // sleep veeery long
         {
            boost::mutex::scoped_lock lock(*reference_.invocationMutex_);
            TimeoutMap::iterator iter = reference_.timeoutMap_.begin();
            if (iter == reference_.timeoutMap_.end()) {
               reference_.log_.warn(ME, " The timeout is empty");
            }
            else {
               reference_.log_.trace(ME, " The timeout is not empty");
               Timestamp nextWakeup = (*iter).first;
               long next = (long)(nextWakeup / reference_.timestampFactory_.MILLION);
               long current = (long)(reference_.timestampFactory_.getTimestamp() / reference_.timestampFactory_.MILLION);
               delay = next - current;
               if (delay <= 0) {
                  tmpContainer = (*iter).second;
                  reference_.timeoutMap_.erase((*iter).first);
                  container = &tmpContainer;
                  if (reference_.isDebug_)
                    std::cout << reference_.ME << " Timeout occurred, calling listener with real time error of " << delay << " millis" << std::endl;
               }
            }
            if (container != NULL) (container->first)->timeout(container->second);
            continue;
         }
         {
            boost::xtime timeToWait;
            boost::xtime_get(&timeToWait, boost::TIME_UTC);
            timeToWait.sec  +=  delay / reference_.timestampFactory_.MILLION;
            timeToWait.nsec += delay % reference_.timestampFactory_.MILLION;
            boost::mutex::scoped_lock waitForTimeoutLock(*reference_.waitForTimeoutMutex_);
            reference_.isReady_ = true;
            reference_.waitForTimeoutCondition_->timed_wait(waitForTimeoutLock, timeToWait);
         }
      }
   }



   Timestamp Timeout::addTimeoutListener(I_Timeout *listener, long delay, void *userData) {
      log_.call(ME, " addTimeoutListener");
      std::cout << ME << " addTimeoutListener" << std::endl;
      Timestamp key = 0;
      if (delay < 1) std::cerr << ME <<": addTimeoutListener with delay = " << delay << std::endl;
      int nanoCounter = 0;
      
      boost::mutex::scoped_lock lock(*invocationMutex_);
      while (true) {
         key = timestampFactory_.getTimestamp(delay);
         TimeoutMap::iterator iter = timeoutMap_.find(key);
         if (iter == timeoutMap_.end()) {
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
      
      return getTimeout(key) - (long)(currentTimestamp / timestampFactory_.MILLION);
   }

   long Timeout::getTimeout(Timestamp key) {
      log_.call(ME, " getTimeout");
      std::cout << ME << " getTimeout" << std::endl;
      if (key < 0) return -1;
      return key / timestampFactory_.MILLION;
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
      removeAll();
      isRunning_ = false;
//      boost::mutex::scoped_lock lock(*invocationMutex_);
      waitForTimeoutCondition_->notify_one();
   }

}}}; // namespaces

