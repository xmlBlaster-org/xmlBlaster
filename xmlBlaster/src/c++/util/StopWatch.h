/*-----------------------------------------------------------------------------
Name:      StopWatch.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
-----------------------------------------------------------------------------*/

#ifndef _UTIL_STOPWATCH_H
#define _UTIL_STOPWATCH_H

#include <util/XmlBCfg.h>
#include <sys/timeb.h>
#include <string>
#include <boost/lexical_cast.hpp>

using namespace std;
using boost::lexical_cast;

namespace org { namespace xmlBlaster {
namespace util {

/**
 * Measure the elapsed time. <p />
 * Use this helper class if you want to measure elapsed time in some code 
 * fragment. If you specify (a positive) time in the argument list, it is the
 * time in milliseconds which the stop watch should wait.
 */
   class Dll_Export StopWatch {

   private:
      struct timeb *startTime_, *currentTime_;
      long   stopTime_;

   public:
      StopWatch(long stopTime=-1) {
         startTime_   = new timeb();
         currentTime_ = new timeb();
         restart(stopTime);
      }
      

      ~StopWatch() {
         delete startTime_;
         delete currentTime_;
      }


      /**
       * Return the elapsed milliseconds since creation or since the last 
       * restart().<p />
       * @return elapsed Milliseconds since creation or restart()
       */
      long elapsed() const {
         ftime(currentTime_);
         double seconds  = difftime(currentTime_->time, startTime_->time);
         double milliSec = 
            (double)currentTime_->millitm - (double)startTime_->millitm;
         return (long)(1000.0 * seconds + milliSec);
      }

      
      
      /**
       * Returns a nice string with elapsed time.
       * Resets the stop watch.
       * @return The elapsed time in a nice formatted string
       */
      string nice() {
         string ret = toString();
         restart();
         return ret;
      }


      /**
       * Nicely formatted output containing elapsed time since
       * Construction or since last restart().
       * <p />
       * @return The elapsed time in a nice formatted string
       */
      string toString() const {
         return string("elapsed time: ") + lexical_cast<string>(elapsed()) + " milliseconds";
      }


      /**
       * Reset and start the stop watch for a new measurement cycle.
       * <p />
       */
      void restart(long stopTime=-1) {
         stopTime_ = stopTime;
         ftime(startTime_);
      }

      /**
       * returns true if the stopwatch is still running, false if it has
       * stopped (i.e. when the time specified in the constructor of in the
       * restart method has elapsed). If no stop time was given (nothing was
       * passed to the constructor or restart), then it always returns true.
       */
      bool isRunning() const {
         if (stopTime_ < 0) return true; // always running
         return (stopTime_ > elapsed());
      }


      /**
       * Waits for the amount of milliseconds specified in millisec and then
       * returns.
       */
      void wait(long millisec) {
         restart(millisec);
         while (isRunning()) {
         }
      }
   };
}}} // namespace

#endif

