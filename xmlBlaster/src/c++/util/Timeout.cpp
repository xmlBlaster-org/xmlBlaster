/*-----------------------------------------------------------------------------
Name:      Timeout.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Allows you be called back after a given delay.
-----------------------------------------------------------------------------*/
#include <algorithm>
#include <string>

#include <util/Timeout.h>
#include <util/lexical_cast.h>
#include <util/Constants.h>
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util {

using namespace std;
using namespace org::xmlBlaster::util::thread;

Timeout::Timeout(Global& global)
   : Thread(), ME("Timeout"), threadName_("Timeout-Thread"),
     timeoutMap_(), isRunning_(false), isReady_(false),
     mapHasNewEntry_(false), isActive_(true),
     isDebug_(false), timestampFactory_(TimestampFactory::getInstance()),
     global_(global), log_(global.getLog("util")),
     invocationMutex_(), waitForTimeoutMutex_(), waitForTimeoutCondition_()
{
   ME += "-Timeout-Thread-" + lexical_cast<std::string>(this);
   // the thread will only be instantiated when starting
   log_.call(ME, " default constructor");
   log_.trace(ME, " default constructor: after creating timeout condition");
   start();
   log_.trace(ME, " default constructor: after starting the thread");
}

Timeout::Timeout(Global& global, const string &name)
   : Thread(), ME("Timeout"), threadName_(name),
     timeoutMap_(), isRunning_(false), isReady_(false),
     mapHasNewEntry_(false), isActive_(true),
     isDebug_(false), timestampFactory_(TimestampFactory::getInstance()),
     global_(global), log_(global.getLog("util")),
     invocationMutex_(), waitForTimeoutMutex_(), waitForTimeoutCondition_()
{
   // the thread remains uninitialized ...
   ME += "-" + name + "-" + lexical_cast<std::string>(this);
   log_.call(ME, " alternative constructor");
   start();
   log_.trace(ME, " default constructor: after starting the thread");
}

Timeout::~Timeout() 
{
   log_.call(ME, " destructor");
   shutdown();
   while (isActive_) { } // wait for the thread to finish
}


void Timeout::start() 
{
   log_.call(ME, " start");
   isRunning_ = true;
   log_.trace(ME, " before creating the running thread");
   Thread::start();

   log_.trace(ME, " start: waiting for the thread to be ready (waiting for the first timeout addition)");
   while (!isReady_) {
      Thread::sleep(5);
   }
   log_.trace(ME, " start: running thread created and ready");
}

void Timeout::join() 
{
   Thread::join();
   log_.trace(ME, " start: running thread joined (i.e. thread started)");
}

Timestamp Timeout::addTimeoutListener(I_Timeout *listener, long delay, void *userData) 
{
   log_.call(ME, " addTimeoutListener");
   Timestamp key = 0;
   if (delay < 1) std::cerr << ME <<": addTimeoutListener with delay = " << delay << std::endl;

   {
      Lock lock(invocationMutex_);
      while (true) {
         key = timestampFactory_.getTimestamp() + Constants::MILLION * delay;
         TimeoutMap::iterator iter = timeoutMap_.find(key);
         if (iter == timeoutMap_.end()) {
            log_.trace(ME, "addTimeoutListener, adding key: " + lexical_cast<std::string>(key));
            Container cont(listener, userData);
            TimeoutMap::value_type el(key, cont);
            timeoutMap_.insert(el);
            mapHasNewEntry_ = true;
            break;
         }
      }
   }

   log_.trace(ME, "addTimeoutListener, going to notify");
   Lock waitForTimeoutLock(waitForTimeoutMutex_);
   waitForTimeoutCondition_.notify();
   log_.trace(ME, "addTimeoutListener, successfully notified");
   return key;
}

Timestamp Timeout::refreshTimeoutListener(Timestamp key, long delay) 
{
   log_.call(ME, " refreshTimeoutListener");
   if (key < 0) {
      cerr << "In Timeout.cpp refreshTimeoutListener() is key < 0" << endl;
      // throw an exception here ...
   }
   I_Timeout *callback = 0;
   void *userData = 0;
   {
      Lock lock(invocationMutex_);
      TimeoutMap::iterator iter = timeoutMap_.find(key);
      if (iter == timeoutMap_.end()) {
          // throw the exception here ...
         // throw new XmlBlasterException(ME, "The timeout handle '" + key + "' is unknown, no timeout refresh done");
         std::cerr << ME << "The timeout handle '" << key <<"' is unknown, no timeout refresh done" << std::endl;
         return -1; // temporarly. Change this once exception is thrown
      }
      timeoutMap_.erase(key);
      callback = (*iter).second.first;
      userData = (*iter).second.second;
   }
   return addTimeoutListener(callback, delay, userData);
}

Timestamp Timeout::addOrRefreshTimeoutListener(I_Timeout *listener, long delay, void*, Timestamp key) 
{
   log_.call(ME, " addOrRefreshTimeoutListener");
   if (key < 0) return addTimeoutListener(listener, delay, NULL);
   return refreshTimeoutListener(key, delay);
}

void Timeout::removeTimeoutListener(Timestamp key) 
{
   log_.call(ME, " removeTimeoutListener");
   Lock lock(invocationMutex_);
   timeoutMap_.erase(key);
}

bool Timeout::isExpired(Timestamp key) 
{
   log_.call(ME, " isExpired");
   Lock lock(invocationMutex_);
   return (timeoutMap_.find(key) == timeoutMap_.end());
}

long Timeout::spanToTimeout(Timestamp key) 
{
   log_.call(ME, " spanToTimeout");
   Lock lock(invocationMutex_);
   TimeoutMap::iterator iter = timeoutMap_.find(key);
   if (iter == timeoutMap_.end()) return -1;
   Timestamp currentTimestamp = timestampFactory_.getTimestamp();
   return getTimeout(key) - (long)(currentTimestamp / Constants::MILLION);
}

long Timeout::getTimeout(Timestamp key) 
{
   log_.call(ME, " getTimeout");
   if (key < 0) return -1;
   return (long)(key / Constants::MILLION);
}

void Timeout::removeAll() 
{
   log_.call(ME, " removeAll");
   Lock lock(invocationMutex_);
   timeoutMap_.clear();
}

void Timeout::shutdown() 
{
   log_.call(ME, " shutdown");
   isRunning_ = false;
   removeAll();
   Lock waitForTimeoutLock(waitForTimeoutMutex_);
   waitForTimeoutCondition_.notify();
}


size_t Timeout::getTimeoutMapSize()
{
   Lock lock(invocationMutex_);
   return timeoutMap_.size();
}


void Timeout::run()
{
   log_.call(ME, " run()");
   isActive_ = true;

   Container *container = NULL;
   Container tmpContainer;

   while (isRunning_) {

      log_.trace(ME, " run(): is running");
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
            log_.trace(ME, "run, next event (Timestamp): " + lexical_cast<std::string>(nextWakeup) + " ns");
            delay = nextWakeup - timestampFactory_.getTimestamp();

            log_.trace(ME, "run, delay       : " + lexical_cast<std::string>(nextWakeup) + " ns");
            if ( delay < 0 ) delay = 0;

            if (delay <= 0) {
               tmpContainer = (*iter).second;
               timeoutMap_.erase((*iter).first);
               container = &tmpContainer;
               if (isDebug_)
                  std::cout << ME << " Timeout occurred, calling listener with real time error of " << delay << " nanos" << std::endl;
            }
         }
         mapHasNewEntry_ = false;
      }
      // must be outside the sync
      if (container != NULL) {
          (container->first)->timeout(container->second);
          container = NULL;
      }
      Timestamp milliDelay = delay / Constants::MILLION;
      if (milliDelay > 0) {
         log_.trace(ME, "sleeping ... " + lexical_cast<std::string>(milliDelay) + " milliseconds");

         Lock waitForTimeoutLock(waitForTimeoutMutex_);
         if (!mapHasNewEntry_) {
            isReady_ = true;
            waitForTimeoutCondition_.wait(waitForTimeoutLock, (long)milliDelay);
            log_.trace(ME, "waking up ... ");
         }
      }
   }
   log_.trace(ME, "the running thread is exiting");
   isActive_ = false;
}

}}}; // namespaces

