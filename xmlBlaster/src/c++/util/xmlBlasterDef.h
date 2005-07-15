/*------------------------------------------------------------------------------
Name:      xmlBlasterDef.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Contains general definitions commonly used in the project
Version:   $Id$
------------------------------------------------------------------------------*/

#ifndef _UTIL_XMLBLASTERDEF_H
#define _UTIL_XMLBLASTERDEF_H

#ifdef _WINDOWS
#pragma warning(disable:4786)
#pragma warning(disable:4251)
#endif

#include <util/XmlBCfg.h>
//NEW: #include <util/basicDefs.h> // definition for Dll_Export, int64_t, the timestamps ... (see C-client library xmlBlaster/src/c/util/basicDefs.h)

// copied from xmlBlaster/src/c/util/basicDefs.h:
#if defined(_WINDOWS)
  typedef __int64 int64_t;
# define PRINTF_PREFIX_INT64_T "%I64d"
  /*typedef long long int64_t;*/
  typedef __int32 int32_t;
  typedef __int16 int16_t;
#else

/* FreeBSD uses inttypes.h, not stdint.h.  Boost's lib suggests
   this should read  defined(__FreeBSD__) || defined(__IBMCPP__)
*/
# if defined(__FreeBSD__) 
#   include <inttypes.h>
# elif defined(__sun)
    /*#   include <int_types.h>*/ /* /usr/include/sys/int_types.h */
# elif defined(__hpux)
  /*typedef long long int64_t;*/
  /*#   include <int_types.h>*/ /* /usr/include/sys/int_types.h */
# else
#   include <stdint.h>  /*-> C99:  uint64_t etc. */
# endif
#endif

#include <string> // need for XMLBLASTER_TRUE/XMLBLASTER_FALSE, shall we replace it by defines and throw out this include?

// definition for the timestamps (see xmlBlaster/src/c/util/basicDefs.h)
namespace org { namespace xmlBlaster { namespace util {

static const std::string XMLBLASTER_TRUE("true");
static const std::string XMLBLASTER_FALSE("false");

typedef int64_t Timestamp;

// change this if it does not compile correctly
// #define STRING_TO_TIMESTAMP(x) atoll(x)
// #define      STRING_TO_TIMESTAMP(x) lexical_cast<org::xmlBlaster::util::Timestamp>(x)

#ifndef _UTIL_GLOBAL_H
   class Global; // forward declaration for (chicken-egg)
#endif

}}} // org::xmlBlaster::util

// This is useful to retrieve stack traces in exceptions. If your system
// contains execinfo.h then you can add the ifdef of that system here.
#ifdef __GNUC__ 
#ifndef __sun__
#ifdef _ENABLE_STACK_TRACE_  // is set in build.xml
#include <execinfo.h>
#endif
#endif
#endif

#endif
