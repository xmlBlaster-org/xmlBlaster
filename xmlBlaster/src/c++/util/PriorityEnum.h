/*-----------------------------------------------------------------------------
Name:      PriorityEnum.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Allows you be called back after a given delay.
-----------------------------------------------------------------------------*/

#ifndef _UTIL_PRIORITYENUM_H
#define _UTIL_PRIORITYENUM_H

#include <string>
#include <util/xmlBlasterDef.h>

namespace org { namespace xmlBlaster { namespace util {

enum Priority {

   /**
    * The minimum priority of a message (0).
    */
   MIN_PRIORITY,

   /**
    * The minimum priority of a message (1).
    */
   MIN1_PRIORITY,

   /**
    * The minimum priority of a message (2).
    */
   MIN2_PRIORITY,

   /**
    * The lower priority of a message (3).
    */
   LOW_PRIORITY,

   /**
    * The lower priority of a message (4).
    */
   LOW4_PRIORITY,

    /**
    * The default priority of a message (5).
    */
   NORM_PRIORITY,

   /**
    * The default priority of a message (6).
    */
   NORM6_PRIORITY,

   /**
    * The higher priority of a message (7).
    */
   HIGH_PRIORITY,

   /**
    * The higher priority of a message (8).
    */
   HIGH8_PRIORITY,

   /**
    * The maximum priority of a message (9).
    */
   MAX_PRIORITY
};

typedef enum Priority PriorityEnum;

Dll_Export PriorityEnum int2Priority(int val);

Dll_Export PriorityEnum str2Priority(const std::string& val);

}}} // namespaces

#endif


