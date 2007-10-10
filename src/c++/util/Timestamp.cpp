/*------------------------------------------------------------------------------
Name:      Timestamp.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Create unique timestamp
Version:   $Id$
------------------------------------------------------------------------------*/

#include <util/Timestamp.h>
#include <util/Constants.h>
#include <util/lexical_cast.h>
#include <time.h>

namespace org { namespace xmlBlaster { namespace util {

using namespace std;
using namespace org::xmlBlaster::util::thread;

TimestampFactory::TimestampFactory() : getterMutex_()
{
   lastTimestamp_  = 0;
}

TimestampFactory::TimestampFactory(const TimestampFactory &factory)
   : getterMutex_()
{
   lastTimestamp_  = factory.lastTimestamp_;
}

TimestampFactory& TimestampFactory::operator =(const TimestampFactory &factory)
{
   lastTimestamp_  = factory.lastTimestamp_;
   return *this;
}


TimestampFactory::~TimestampFactory()
{
}

TimestampFactory& TimestampFactory::getInstance()
{
   static TimestampFactory timestamp;
   return timestamp;
}


Timestamp TimestampFactory::getTimestamp()
{
   Lock lock(getterMutex_);
   Timestamp timestamp = Thread::getCurrentTimestamp();
   if (timestamp <= lastTimestamp_) {
      timestamp = lastTimestamp_;
      timestamp++;
   }

   lastTimestamp_ = timestamp;
   return timestamp;
}

string TimestampFactory::toXml(Timestamp timestamp, const string& extraOffset, bool literal)
{
   string ret;
   string offset = "\n ";
   offset += extraOffset;
   if (literal) {
      // implement it here ....
      ret += offset + "<timestamp nanos='" + lexical_cast<std::string>(timestamp) + "'>";
      ret += offset + " " + getTimeAsString(timestamp);
      ret += offset + "</timestamp>";
   }
   else {
      ret += offset + "<timestamp nanos='" + lexical_cast<std::string>(timestamp) + "'/>";
   }
   return ret;
}

string TimestampFactory::getTimeAsString(Timestamp timestamp)
{
    time_t secs = (time_t)(timestamp / Constants::BILLION);
    Timestamp nanos = timestamp % Constants::BILLION;

    struct tm* help = gmtime(&secs);

    string ret;
    char *ptr = new char[300];

    try {
       /* size_t nmax = */ strftime(ptr, 300, "%Y-%m-%d %H:%M:%S", help);
       ret = string(ptr) + "." + lexical_cast<std::string>(nanos);
       delete[] ptr;
    }
    catch(...) {
       delete[] ptr;
    }
    return ret;
}

}}} // namespace


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
