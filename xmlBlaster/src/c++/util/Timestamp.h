// MAKE THIS A HEADER FILE


/*------------------------------------------------------------------------------
Name:      Timestamp.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Create unique timestamp
Version:   $Id: Timestamp.h,v 1.3 2002/11/26 18:02:03 laghi Exp $
------------------------------------------------------------------------------*/

#ifndef _UTIL_TIMESTAMP_H
#define _UTIL_TIMESTAMP_H

#include <string>
#include <time.h>
#include <util/xmlBlasterDef.h>

/*
#ifndef BOOST_MUTEX_WEK070601_HPP 
namespace boost { 
class mutex;
}
#endif
*/
using namespace std;

namespace org { namespace xmlBlaster { namespace util {

/**
 * High performing timestamp class, time elapsed since 1970, the nanos are simulated
 * as a unique counter. 
 * <br />
 * The counter is rewound on any second step (this is different from its omonimous java
 * class since there is no portable way in c++ to get the system time with a millisecond
 * precision yet. Besides this difference, the c++ timestamp is a singleton working as a
 * factory of timestamps (which are of the type long long).
 * <br />
 * Guarantees that any created Timestamp instance is unique in the current process.
 * <br /><br />
 * Fails only if
 * <ul>
 *   <li>a CPU can create more than 999999999 Timestamp instances per second</li>
 *   <li>In ~ 288 years when Long.MAX_VALUE = 9223372036854775807 overflows (current value is 1013338358124000008)</li>
 * </ul>
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */

   class TimestampFactory {
      
   private:
      Timestamp nanoCounter_;
      Timestamp milliCounter_;
      Timestamp lastSeconds_;
      boost::mutex *getterMutex_;

      /** Cache for string representation */
      string strFormat_; //  = null;

      /**
       * The default constructor is made private to implement the singleton
       * pattern.
       */
      TimestampFactory();
      TimestampFactory(const TimestampFactory &factory);
      TimestampFactory& operator =(const TimestampFactory &factory);
      ~TimestampFactory();

   public:
      const Timestamp TOUSAND;
      const Timestamp MILLION;
      const Timestamp BILLION;


      /**
       * The method to call to get the singleton Timestamp object.
       */
      static TimestampFactory& getInstance();
    
      /**
       * Constructs a current timestamp which is guaranteed to be unique in time for this JVM
       * @param delay the time in milliseconds from now the return value has to point to.
       * @exception RuntimeException on overflow (never happens :-=)
       */
      Timestamp getTimestamp(long delay=0);
   };

}}}; // namespace

#endif
