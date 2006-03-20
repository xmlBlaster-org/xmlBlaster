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
     isDebug_(false), detached_(false), timestampFactory_(TimestampFactory::getInstance()),
     global_(global), log_(global.getLog("org.xmlBlaster.util")),
     invocationMutex_(), waitForTimeoutMutex_(), waitForTimeoutCondition_()
{
   ME += "-Timeout-Thread-" + lexical_cast<std::string>(this);
   // the thread will only be instantiated when starting
   if (log_.call()) log_.call(ME, " default constructor");
   if (log_.trace()) log_.trace(ME, " default constructor: after creating timeout condition");
   start(detached_);
   if (log_.trace()) log_.trace(ME, " default constructor: after starting the thread");
}

Timeout::Timeout(Global& global, const string &name)
   : Thread(), ME("Timeout"), threadName_(name),
     timeoutMap_(), isRunning_(false), isReady_(false),
     mapHasNewEntry_(false), isActive_(true),
     isDebug_(false), detached_(false), timestampFactory_(TimestampFactory::getInstance()),
     global_(global), log_(global.getLog("org.xmlBlaster.util")),
     invocationMutex_(), waitForTimeoutMutex_(), waitForTimeoutCondition_()
{
   // the thread remains uninitialized ...
   ME += "-" + name + "-" + lexical_cast<std::string>(this);
   if (log_.call()) log_.call(ME, " alternative constructor");
   start(detached_);
   if (log_.trace()) log_.trace(ME, " default constructor: after starting the thread");
}

Timeout::~Timeout() 
{
   if (log_.call()) log_.call(ME, " destructor");

   shutdown();

   if (!detached_)
      join();

   if (isActive_) { /* Should never happen */
      for (int i=0; i<200; i++) {
         if (!isActive_) break;
         log_.warn(ME, "Waiting for timer thread to finish");
         //Thread::yield();
         Thread::sleep(10);
      }
   }
}


bool Timeout::start(bool detached) 
{
   if (log_.call()) log_.call(ME, " start" + lexical_cast<string>(detached));
   isRunning_ = true;
   if (log_.trace()) log_.trace(ME, " before creating the running thread");
   Thread::start(detached);

   if (log_.trace()) log_.trace(ME, " start: waiting for the thread to be ready (waiting for the first timeout addition)");
   while (!isReady_) {
      Thread::sleep(5);
   }
   if (log_.trace()) log_.trace(ME, " start: running thread created and ready");
   return true;
}

void Timeout::join() 
{
   Thread::join();
   if (log_.trace()) log_.trace(ME, " start: running thread joined (i.e. thread started)");
}

Timestamp Timeout::addTimeoutListener(I_Timeout *listener, long delay, void *userData) 
{
   if (!isRunning_) 
      throw org::xmlBlaster::util::XmlBlasterException(USER_WRONG_API_USAGE, "", ME + ".addTimeoutListener", "en", "The timer is not running");

   //if (log_.call()) log_.call(ME, " addTimeoutListener");
   Timestamp key = 0;
   if (delay < 1) log_.error(ME, ": addTimeoutListener with delay = " + lexical_cast<std::string>(delay));

   {
      Lock lock(invocationMutex_);
      while (true) {
         key = timestampFactory_.getTimestamp() + Constants::MILLION * delay;
         TimeoutMap::iterator iter = timeoutMap_.find(key);
         if (iter == timeoutMap_.end()) {
            if (log_.trace()) log_.trace(ME, "addTimeoutListener, adding key: " + lexical_cast<std::string>(key));
            Container cont(listener, userData);
            TimeoutMap::value_type el(key, cont);
            timeoutMap_.insert(el);
            mapHasNewEntry_ = true;
            break;
         }
      }
   }

   if (log_.trace()) log_.trace(ME, "addTimeoutListener, going to notify");
   Lock waitForTimeoutLock(waitForTimeoutMutex_);
   waitForTimeoutCondition_.notify();
   //if (log_.trace()) log_.trace(ME, "addTimeoutListener, successfully notified");
   return key;
}

Timestamp Timeout::refreshTimeoutListener(Timestamp key, long delay) 
{
   if (log_.call()) log_.call(ME, " refreshTimeoutListener");

   if (!isRunning_) 
      throw org::xmlBlaster::util::XmlBlasterException(USER_WRONG_API_USAGE, "", ME + ".refreshTimeoutListener", "en", "The timer is not running");

   if (key < 0)
      throw org::xmlBlaster::util::XmlBlasterException(INTERNAL_ILLEGALARGUMENT, "", ME + ".refreshTimeoutListener", "en", "In Timeout.cpp refreshTimeoutListener() is key < 0");

   I_Timeout *callback = 0;
   void *userData = 0;
   {
      Lock lock(invocationMutex_);
      TimeoutMap::iterator iter = timeoutMap_.find(key);
      if (iter == timeoutMap_.end()) {
         if (log_.trace()) log_.trace(ME, "The timeout handle '" + lexical_cast<std::string>(key) + "' is unknown, no timeout refresh done");
         return -1;
      }
      callback = (*iter).second.first;
      userData = (*iter).second.second;
      timeoutMap_.erase(key);
   }
   return addTimeoutListener(callback, delay, userData);
}

Timestamp Timeout::addOrRefreshTimeoutListener(I_Timeout *listener, long delay, void *userData, Timestamp key) 
{
   if (log_.call()) log_.call(ME, " addOrRefreshTimeoutListener");
   if (key <= 0) return addTimeoutListener(listener, delay, userData);
   key = refreshTimeoutListener(key, delay);
   if (key <= 0) return addTimeoutListener(listener, delay, userData);
   return key;
}

void Timeout::removeTimeoutListener(Timestamp key) 
{
   if (log_.call()) log_.call(ME, " removeTimeoutListener");
   Lock lock(invocationMutex_);
   timeoutMap_.erase(key);
}

bool Timeout::isExpired(Timestamp key) 
{
   if (log_.call()) log_.call(ME, " isExpired");
   Lock lock(invocationMutex_);
   return (timeoutMap_.find(key) == timeoutMap_.end());
}

long Timeout::spanToTimeout(Timestamp key) 
{
   if (log_.call()) log_.call(ME, " spanToTimeout");
   Lock lock(invocationMutex_);
   TimeoutMap::iterator iter = timeoutMap_.find(key);
   if (iter == timeoutMap_.end()) return -1;
   Timestamp currentTimestamp = timestampFactory_.getTimestamp();
   return getTimeout(key) - (long)(currentTimestamp / Constants::MILLION);
}

long Timeout::getTimeout(Timestamp key) 
{
   if (log_.call()) log_.call(ME, " getTimeout");
   if (key < 0) return -1;
   return (long)(key / Constants::MILLION);
}

void Timeout::removeAll() 
{
   if (log_.call()) log_.call(ME, " removeAll");
   Lock lock(invocationMutex_);
   timeoutMap_.clear();
}

void Timeout::shutdown() 
{
   if (log_.call()) log_.call(ME, " shutdown");
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
   if (log_.call()) log_.call(ME, " run()");
   isActive_ = true;

   Container *container = NULL;
   Container tmpContainer;

   try {
      while (isRunning_) {

         if (log_.trace()) log_.trace(ME, " run(): is running");
         Timestamp delay = 100000 * Constants::MILLION; // sleep veeery long

         {
            Lock lock(invocationMutex_);

            TimeoutMap::iterator iter = timeoutMap_.begin();
            if (iter == timeoutMap_.end()) {
               if (log_.trace()) log_.trace(ME, "No timer is registered, nothing to do");
            }
            else {
               if (log_.trace()) log_.trace(ME, " The timeout is not empty");
               Timestamp nextWakeup = (*iter).first;
               if (log_.trace()) log_.trace(ME, "run, next event (Timestamp): " + lexical_cast<std::string>(nextWakeup) + " ns");
               delay = nextWakeup - timestampFactory_.getTimestamp();

               if (log_.trace()) log_.trace(ME, "run, delay       : " + lexical_cast<std::string>(delay) + " ns");
               if ( delay < 0 ) delay = 0;

               if (delay <= 0) {
                  tmpContainer = (*iter).second;
                  timeoutMap_.erase((*iter).first);
                  container = &tmpContainer;
                  if (log_.trace()) log_.trace(ME, "Timeout occurred, calling listener with real time error of " + lexical_cast<std::string>(delay) + " nanos");
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
            if (log_.trace()) log_.trace(ME, "sleeping ... " + lexical_cast<std::string>(milliDelay) + " milliseconds");
            Lock waitForTimeoutLock(waitForTimeoutMutex_);
            if (!mapHasNewEntry_) {
               isReady_ = true;
               if (!isRunning_) break;
               waitForTimeoutCondition_.wait(waitForTimeoutLock, (long)milliDelay);
               //if (log_.trace()) log_.trace(ME, "waking up ... ");
            }
         }
      }
      if (log_.trace()) log_.trace(ME, "The running thread is exiting");
   }
   catch (const std::exception &e) {
      log_.error(ME, string("The running thread is exiting: ") + e.what());
   }
   catch (...) {
      log_.error(ME, "The running thread is exiting with an unknown exception");
   }
   isActive_ = false;
}

}}} // namespaces

