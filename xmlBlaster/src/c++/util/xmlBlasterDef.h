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

// definition for the timestamps (see xmlBlaster/src/c/util/basicDefs.h)
namespace org { namespace xmlBlaster { namespace util {
//  replace by basicDefs.h:
#if defined(_WINDOWS)
  typedef __int64 int64_t;
  typedef __int32 int32_t;
  //typedef __uint32 uint32_t; -> __uint32 is not correct, what is the correct type?
#else
  typedef long long int int64_t;        // C99 standard: #include<stdint.h> has uint64_t etc.
  typedef int int32_t;
  //typedef unsigned int uint32_t;
#endif
typedef int64_t Timestamp;

// change this if it does not compile correctly
// #define STRING_TO_TIMESTAMP(x) atoll(x)
// #define      STRING_TO_TIMESTAMP(x) lexical_cast<org::xmlBlaster::util::Timestamp>(x)

#ifndef _UTIL_GLOBAL_H
   class Global; // forward declaration for (chicken-egg)
#endif


}}}

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
