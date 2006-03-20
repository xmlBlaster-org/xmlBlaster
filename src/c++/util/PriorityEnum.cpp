/*-----------------------------------------------------------------------------
Name:      PriorityEnum.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Allows you be called back after a given delay.
-----------------------------------------------------------------------------*/

#include <util/PriorityEnum.h>
#include <util/lexical_cast.h>
#include <iostream>

namespace org { namespace xmlBlaster { namespace util {

PriorityEnum int2Priority(int val)
{
   if (val <= MIN_PRIORITY) return MIN_PRIORITY;
   if (val >= MAX_PRIORITY) return MAX_PRIORITY;
   switch(val) {
      case MIN1_PRIORITY  : return MIN1_PRIORITY;
      case MIN2_PRIORITY  : return MIN2_PRIORITY;
      case LOW_PRIORITY   : return LOW_PRIORITY;
      case LOW4_PRIORITY  : return LOW4_PRIORITY;
      case NORM_PRIORITY  : return NORM_PRIORITY;
      case NORM6_PRIORITY : return NORM6_PRIORITY;
      case HIGH_PRIORITY  : return HIGH_PRIORITY;
   }
   return HIGH8_PRIORITY;
}

PriorityEnum str2Priority(const std::string& val)
{
   if (val == "MIN") return MIN_PRIORITY;
   if (val == "LOW") return LOW_PRIORITY;
   if (val == "NORM") return NORM_PRIORITY;
   if (val == "HIGH") return HIGH_PRIORITY;
   if (val == "MAX") return MAX_PRIORITY;
   try {
      int prio = lexical_cast<int>(val);
      return int2Priority(prio);
   }
   catch (...) {   //bad_lexical_cast
      std::cerr << "Don't know what to do with priority '" << val << "', returning NORM priority" << std::endl;
      return NORM_PRIORITY;
   }
   /*
   int prio;
   int numConverted = sscanf(val.c_str(), "%d", &prio);
   if (numConverted != 1) {
      std::cerr << "Don't know what to to with priority '" << val << "', returning NORM priority" << std::endl;
      return NORM_PRIORITY;
   }
   return int2Priority(prio);
   */
}


}}} // namespaces



