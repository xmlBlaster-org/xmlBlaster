/*-----------------------------------------------------------------------------
Name:      PriorityEnum.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Allows you be called back after a given delay.
-----------------------------------------------------------------------------*/

#include <util/PriorityEnum.h>

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


}}} // namespaces



