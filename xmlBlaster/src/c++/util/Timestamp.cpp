/*------------------------------------------------------------------------------
Name:      Timestamp.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Create unique timestamp
Version:   $Id: Timestamp.cpp,v 1.4 2002/11/27 10:53:40 laghi Exp $
------------------------------------------------------------------------------*/

#include <util/Timestamp.h>
#include <boost/thread/thread.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/thread/xtime.hpp>

// #include <stddef.h>
// #include <iostream>

namespace org { namespace xmlBlaster { namespace util {

   TimestampFactory::TimestampFactory(): TOUSAND(1000), MILLION(1000000),
      BILLION(1000000000) {
      lastTimestamp_  = 0;
      getterMutex_ = new boost::mutex();
   }

   TimestampFactory::TimestampFactory(const TimestampFactory &factory) :
      TOUSAND(1000), MILLION(1000000), BILLION(1000000000) {
      lastTimestamp_  = factory.lastTimestamp_;
      getterMutex_ = new boost::mutex();
   }
   
   TimestampFactory& TimestampFactory::operator =(const TimestampFactory &factory) {
      lastTimestamp_  = factory.lastTimestamp_;
      delete getterMutex_;
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


   Timestamp TimestampFactory::getTimestamp() {
      boost::mutex::scoped_lock lock(*getterMutex_);
      boost::xtime xt;
      boost::xtime_get(&xt, boost::TIME_UTC);
      Timestamp timestamp = xt.sec * BILLION + xt.nsec;
      if (timestamp == lastTimestamp_) timestamp++;
/*
      while (timestamp == lastTimestamp_) {
         boost::xtime_get(&xt, boost::TIME_UTC);
         timestamp = xt.sec * BILLION + xt.nsec;
      }
*/
      lastTimestamp_ = timestamp;
      return timestamp;
   }


   void TimestampFactory::sleep(Timestamp nanoSecondDelay) {
      boost::xtime xt;
      boost::xtime_get(&xt, boost::TIME_UTC);
      xt.sec += nanoSecondDelay / BILLION;
      xt.nsec += nanoSecondDelay % BILLION;
      boost::thread::sleep(xt);
   }

}}}; // namespace

