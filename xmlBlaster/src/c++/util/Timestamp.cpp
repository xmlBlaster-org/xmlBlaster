/*------------------------------------------------------------------------------
Name:      Timestamp.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Create unique timestamp
Version:   $Id: Timestamp.cpp,v 1.2 2002/11/26 12:37:37 ruff Exp $
------------------------------------------------------------------------------*/

#include <util/Timestamp.h>
#include <boost/thread/thread.hpp>
#include <boost/thread/mutex.hpp>

#include <stddef.h> // only for testing
#include <iostream>

namespace org { namespace xmlBlaster { namespace util {

   
   Timestamp::Timestamp(): TOUSAND(1000), MILLION(1000000), BILLION(1000000000) {
      lastSeconds_  = 0;
      milliCounter_ = 0;
      nanoCounter_  = 0;
      getterMutex_ = new boost::mutex();
   }

   Timestamp::~Timestamp() {
      delete getterMutex_;
   }

   Timestamp& Timestamp::getInstance() {
      static Timestamp timestamp;
      return timestamp;
   }
    
   long long Timestamp::getTimestamp() {
      time_t currentTime;
      time(&currentTime);
      long long newSeconds = (long long)difftime(currentTime, (time_t)0);
      long long timestamp = 0;
      
      // synchronize from here ...
      {
         boost::mutex::scoped_lock lock(*getterMutex_);
         if (newSeconds != lastSeconds_) { // reset counters
            nanoCounter_ = 0;
            milliCounter_ = 0;
         }
         else {
            nanoCounter_++;
            if (nanoCounter_ >= MILLION) {
               nanoCounter_ = 0;
               milliCounter_++;
               if (milliCounter_ >= TOUSAND) {
                  // throw an exception 
               }
            }
            timestamp = (newSeconds * MILLION) + (milliCounter_*TOUSAND) + nanoCounter_;
         } 
         lastSeconds_ = newSeconds;
      }

      return timestamp;
   }
   
}}}; // namespace

// using org::xmlBlaster::util::Timestamp;

int main() {
   org::xmlBlaster::util::Timestamp &p = org::xmlBlaster::util::Timestamp::getInstance();
   for (int i=0; i < 100; i++) {
      long long time = p.getTimestamp();
      std::cout << time << " size of object: " << sizeof(time) << std::endl;
   }
   return 0;
}


