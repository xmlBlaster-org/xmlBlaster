/*-----------------------------------------------------------------------------
Name:      Timeout.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Allows you be called back after a given delay.
-----------------------------------------------------------------------------*/
#include <algorithm>
#include <string>

#include <util/Timeout.h>

// #include <boost/thread/thread.hpp>
// #include <boost/thread/mutex.hpp>
// #include <boost/thread/condition.hpp>
// #include <boost/thread/xtime.hpp>

#include <boost/lexical_cast.hpp>
#include <util/Constants.h>
#include <util/Global.h>

using namespace std;
using namespace boost;

/*
#if defined(_WINDOWS)   
   ostream& operator <<(ostream& target, const __int64& x)
   {
     
     target << x;
     return target;

   }
#endif
*/
namespace org { namespace xmlBlaster { namespace util {



Timeout::Timeout(Global& global)
   : Thread(), ME("Timeout"), threadName_("Timeout-Thread"),
     timeoutMap_(), isRunning_(false), isReady_(false), isActive_(true),
     isDebug_(false), timestampFactory_(TimestampFactory::getInstance()),
     global_(global), log_(global.getLog("util")),
     invocationMutex_(), waitForTimeoutMutex_(), waitForTimeoutCondition_()
{
   // the thread will only be instantiated when starting
   log_.call(ME, " default constructor");
   log_.trace(ME, " default constructor: after creating timeout condition");
}

Timeout::Timeout(Global& global, const string &name)
   : Thread(), ME("Timeout"), threadName_("Timeout-Thread"),
     timeoutMap_(), isRunning_(false), isReady_(false), isActive_(true),
     isDebug_(false), timestampFactory_(TimestampFactory::getInstance()),
     global_(global), log_(global.getLog("util")),
     invocationMutex_(), waitForTimeoutMutex_(), waitForTimeoutCondition_()
{
   // the thread remains uninitialized ...
   log_.call(ME, " alternative constructor");
}

Timeout::~Timeout() {
   log_.call(ME, " destructor");
   shutdown();
   while (isActive_) { } // wait for the thread to finish
}


void Timeout::start() {
   log_.call(ME, " start");
   isRunning_ = true;
   log_.trace(ME, " before creating the running thread");
   Thread::start();
   log_.trace(ME, " start: running thread created");
}

void Timeout::join() {
   Thread::join();
   log_.trace(ME, " start: running thread joined (i.e. thread started)");
}

Timestamp Timeout::addTimeoutListener(I_Timeout *listener, long delay, void *userData) {
   log_.call(ME, " addTimeoutListener");
   Timestamp key = 0;
   if (delay < 1) std::cerr << ME <<": addTimeoutListener with delay = " << delay << std::endl;

   Lock lock(invocationMutex_);
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
   waitForTimeoutCondition_.notify();
   return key;
}

Timestamp Timeout::refreshTimeoutListener(Timestamp key, long delay) {
   log_.call(ME, " refreshTimeoutListener");
   if (key < 0) {
      // throw an exception here ...
   }
   Lock lock(invocationMutex_);
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
   log_.call(ME, " addOrRefreshTimeoutListener");
   Lock lock(invocationMutex_);
   if (key < 0) return addTimeoutListener(listener, delay, NULL);
   return refreshTimeoutListener(key, delay);
}

void Timeout::removeTimeoutListener(Timestamp key) {
   log_.call(ME, " removeTimeoutListener");
   Lock lock(invocationMutex_);
   timeoutMap_.erase(key);
}

bool Timeout::isExpired(Timestamp key) {
   log_.call(ME, " isExpired");
   Lock lock(invocationMutex_);
   return (timeoutMap_.find(key) == timeoutMap_.end());
}

long Timeout::spanToTimeout(Timestamp key) {
   log_.call(ME, " spanToTimeout");
   Lock lock(invocationMutex_);
   TimeoutMap::iterator iter = timeoutMap_.find(key);
   if (iter == timeoutMap_.end()) return -1;
   Timestamp currentTimestamp = timestampFactory_.getTimestamp();
   return getTimeout(key) - (long)(currentTimestamp / Constants::MILLION);
}

long Timeout::getTimeout(Timestamp key) {
   log_.call(ME, " getTimeout");
   if (key < 0) return -1;
   return (long)(key / Constants::MILLION);
}

void Timeout::removeAll() {
   log_.call(ME, " removeAll");
   Lock lock(invocationMutex_);
   timeoutMap_.clear();
}

void Timeout::shutdown() {
   log_.call(ME, " shutdown");
   isRunning_ = false;
   removeAll();
   waitForTimeoutCondition_.notify();
}



void Timeout::run()
{
   log_.call(ME, " run: operator ()");
   isActive_ = true;

   Container *container = NULL;
   Container tmpContainer;
   
   while (isRunning_) {
      Lock waitForTimeoutLock(waitForTimeoutMutex_);

      log_.trace(ME, " operator (): is running");
      Timestamp delay = 100000 * Constants::MILLION; // sleep veeery long


      {
         Lock lock(invocationMutex_);
         TimeoutMap::iterator iter = timeoutMap_.begin();
         if (iter == timeoutMap_.end()) {
            log_.warn(ME, " The timeout is empty");
         }
         else {
            log_.trace(ME, " The timeout is not empty");
            Timestamp nextWakeup = (*iter).first;
            log_.trace(ME, "run, next event (Timestamp): " + lexical_cast<string>(nextWakeup) + " ns");
            delay = nextWakeup - timestampFactory_.getTimestamp();

            log_.trace(ME, "run, delay       : " + lexical_cast<string>(nextWakeup) + " ns");
				if ( delay < 0 ) {
					delay = 0;
				}

            if (delay <= 0) {
               tmpContainer = (*iter).second;
               timeoutMap_.erase((*iter).first);
               container = &tmpContainer;
               if (isDebug_)
                  std::cout << ME << " Timeout occurred, calling listener with real time error of " << delay << " nanos" << std::endl;
            }
         }
      }
      // must be outside the sync
      if (container != NULL) {
          (container->first)->timeout(container->second);
          container = NULL;
      }
		Timestamp milliDelay = delay / Constants::MILLION;
      if (milliDelay > 0) {
         log_.trace(ME, "sleeping ... " + lexical_cast<string>(milliDelay) + " milliseconds");
         isReady_ = true;
         waitForTimeoutCondition_.wait(waitForTimeoutLock, (long)milliDelay);
         log_.trace(ME, "waking up .. ");
      }
   }
   log_.trace(ME, "the running thread is exiting");
   isActive_ = false;
}

}}}; // namespaces

