/*-----------------------------------------------------------------------------
Name:      StopWatch.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
-----------------------------------------------------------------------------*/

#ifndef _UTIL_STOPWATCH_H
#define _UTIL_STOPWATCH_H

#include <util/XmlBCfg.h>
#if !defined(WINCE)
#include <sys/timeb.h>
#endif
#include <string>
#include <util/lexical_cast.h>


namespace org { namespace xmlBlaster {
namespace util {

/**
 * Measure the elapsed time. <p />
 * Use this helper class if you want to measure elapsed time in some code 
 * fragment. If you specify (a positive) time in the argument list, it is the
 * time in milliseconds which the stop watch should wait.
 * <p>Is not implemented for Windows CE</p>
 */
   class Dll_Export StopWatch {

   private:
#if defined(WINCE)
#else
      struct timeb *startTime_, *currentTime_;
#endif
      long   stopTime_;

   public:
      StopWatch(long stopTime=-1) {
#if defined(WINCE)
#else
         startTime_   = new timeb();
         currentTime_ = new timeb();
         restart(stopTime);
#endif
      }
      

      ~StopWatch() {
#if defined(WINCE)
#else
         delete startTime_;
         delete currentTime_;
#endif
          }


      /**
       * Return the elapsed milliseconds since creation or since the last 
       * restart().<p />
       * @return elapsed Milliseconds since creation or restart()
       */
      long elapsed() const {
#if defined(WINCE)
                return 0;
#else
         ftime(currentTime_);
         double seconds  = difftime(currentTime_->time, startTime_->time);
         double milliSec = 
            (double)currentTime_->millitm - (double)startTime_->millitm;
         return (long)(1000.0 * seconds + milliSec);
#endif //(WINCE)
      }

      
      
      /**
       * Returns a nice std::string with elapsed time.
       * Resets the stop watch.
       * @return The elapsed time in a nice formatted std::string
       */
      std::string nice() {
         std::string ret = toString();
         restart();
         return ret;
      }


      /**
       * Nicely formatted output containing elapsed time since
       * Construction or since last restart().
       * <p />
       * @return The elapsed time in a nice formatted std::string
       */
      std::string toString() const {
         return std::string("elapsed time: ") + lexical_cast<std::string>(elapsed()) + " milliseconds";
      }


      /**
       * Reset and start the stop watch for a new measurement cycle.
       * <p />
       */
      void restart(long stopTime=-1) {
         stopTime_ = stopTime;
#if defined(WINCE)
#else
         ftime(startTime_);
#endif
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

