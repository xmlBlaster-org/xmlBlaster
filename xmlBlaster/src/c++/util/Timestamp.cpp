/*------------------------------------------------------------------------------
Name:      Timestamp.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Create unique timestamp
Version:   $Id: Timestamp.cpp,v 1.3 2002/11/26 18:02:03 laghi Exp $
------------------------------------------------------------------------------*/

#include <util/Timestamp.h>
#include <boost/thread/thread.hpp>
#include <boost/thread/mutex.hpp>

#include <stddef.h> // only for testing
#include <iostream>

namespace org { namespace xmlBlaster { namespace util {

   
   TimestampFactory::TimestampFactory(): TOUSAND(1000), MILLION(1000000), 
      BILLION(1000000000) {
      lastSeconds_  = 0;
      milliCounter_ = 0;
      nanoCounter_  = 0;
      getterMutex_ = new boost::mutex();
   }

   TimestampFactory::TimestampFactory(const TimestampFactory &factory) {
      lastSeconds_  = factory.lastSeconds_;
      milliCounter_ = factory.milliCounter_;
      nanoCounter_  = factory.nanoCounter_;
      getterMutex_ = new boost::mutex();
   }
   
   TimestampFactory& TimestampFactory::operator =(const TimestampFactory &factory) {
      lastSeconds_  = factory.lastSeconds_;
      milliCounter_ = factory.milliCounter_;
      nanoCounter_  = factory.nanoCounter_;
      getterMutex_ = new boost::mutex();
      return *this;
   }


   TimestampFactory::~TimestampFactory() {
      delete getterMutex_;
   }

   TimestampFactory& TimestampFactory::getInstance() {
      static TimestampFactory timestamp;
      return timestamp;
   }
    
   Timestamp TimestampFactory::getTimestamp(long delay) {
      time_t currentTime;
      time(&currentTime);
      Timestamp newSeconds = (Timestamp)difftime(currentTime, (time_t)0);
      Timestamp timestamp = 0;
      
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
            timestamp = (newSeconds * MILLION) + ((milliCounter_+delay)*TOUSAND) + nanoCounter_;
         }
         lastSeconds_ = newSeconds;
      }

      return timestamp;
   }
   
}}}; // namespace

