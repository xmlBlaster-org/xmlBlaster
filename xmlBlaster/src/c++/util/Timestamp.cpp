/*------------------------------------------------------------------------------
Name:      Timestamp.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Create unique timestamp
Version:   $Id: Timestamp.cpp,v 1.8 2002/12/31 11:29:02 laghi Exp $
------------------------------------------------------------------------------*/

#include <util/Timestamp.h>
#include <util/Constants.h>
// #include <boost/thread/thread.hpp>
// #include <boost/thread/mutex.hpp>
// #include <boost/thread/xtime.hpp>
#include <boost/lexical_cast.hpp>
#include <time.h>

using boost::lexical_cast;

namespace org { namespace xmlBlaster { namespace util {

   TimestampFactory::TimestampFactory() : getterMutex_()
   {
      lastTimestamp_  = 0;
//      getterMutex_ = new boost::mutex();
   }

   TimestampFactory::TimestampFactory(const TimestampFactory &factory)
      : getterMutex_()
   {
      lastTimestamp_  = factory.lastTimestamp_;
//      getterMutex_ = new boost::mutex();
   }
   
   TimestampFactory& TimestampFactory::operator =(const TimestampFactory &factory)
   {
      lastTimestamp_  = factory.lastTimestamp_;
//      delete getterMutex_;
//      getterMutex_ = new boost::mutex();
      return *this;
   }


   TimestampFactory::~TimestampFactory()
   {
//      delete getterMutex_;
   }

   TimestampFactory& TimestampFactory::getInstance()
   {
      static TimestampFactory timestamp;
      return timestamp;
   }


   Timestamp TimestampFactory::getTimestamp()
   {
//      boost::mutex::scoped_lock lock(*getterMutex_);
      Lock lock(getterMutex_);
//      boost::xtime xt;
//      boost::xtime_get(&xt, boost::TIME_UTC);
//      Timestamp timestamp = Constants::BILLION * xt.sec + xt.nsec;
      Timestamp timestamp = Thread::getCurrentTimestamp();
      if (timestamp <= lastTimestamp_) timestamp++;
/*
      while (timestamp == lastTimestamp_) {
         boost::xtime_get(&xt, boost::TIME_UTC);
         timestamp = xt.sec * BILLION + xt.nsec;
      }
*/
      lastTimestamp_ = timestamp;
      return timestamp;
   }

/*
   void TimestampFactory::sleep(Timestamp nanoSecondDelay)
   {
      boost::xtime xt;
      boost::xtime_get(&xt, boost::TIME_UTC);

      long secDelay  = nanoSecondDelay / Constants::BILLION;
      long nanoDelay = nanoSecondDelay % Constants::BILLION;
      xt.sec        += secDelay;
      xt.nsec       += nanoDelay;
      boost::thread::sleep(xt);
   }

   void TimestampFactory::sleepMillis(long millis)
   {
      Timestamp nanos = Constants::MILLION * millis;
      TimestampFactory::sleep(nanos);
   }

   void TimestampFactory::sleepSecs(long secs)
   {
      TimestampFactory::sleepMillis(secs * 1000l);
   }
*/

   string TimestampFactory::toXml(Timestamp timestamp, const string& extraOffset, bool literal)
   {
      string ret;
      string offset = "\n ";
      offset += extraOffset;
      if (literal) {
         // implement it here ....
         ret += offset + "<timestamp nanos='" + lexical_cast<string>(timestamp) + "'>";
         ret += offset + " " + getTimeAsString(timestamp);
         ret += offset + "</timestamp>";
      }
      else {
         ret += offset + "<timestamp nanos='" + lexical_cast<string>(timestamp) + "'/>";
      }
      return ret;
   }

   string TimestampFactory::getTimeAsString(Timestamp timestamp)
   {
       time_t secs = (time_t)(timestamp / Constants::BILLION);
       Timestamp nanos = timestamp % Constants::BILLION;

       struct tm* help = gmtime(&secs);

       char *ptr = new char[300];
       /* size_t nmax = */ strftime(ptr, 300, "%Y-%m-%d %H:%M:%S", help);

       string ret = string(ptr) + "." + lexical_cast<string>(nanos);
       delete ptr;
       return ret;
   }

}}}; // namespace


#ifdef _XMLBLASTER_CLASSTEST

using namespace std;
using namespace org::xmlBlaster::util;

int main(int args, char* argv[])
{
   TimestampFactory& factory = TimestampFactory::getInstance();

   Timestamp timestamp = factory.getTimestamp();
   cout << "raw timestamp: " << timestamp << endl;
   string ret = factory.toXml(timestamp, "  ", true);
   cout << "as xml: " << endl << ret << endl;

   Thread::sleepSecs(3);

    Timestamp t2 = factory.getTimestamp();
    cout << factory.toXml(t2, "   ", true) << endl;

    return 0;
}

#endif
