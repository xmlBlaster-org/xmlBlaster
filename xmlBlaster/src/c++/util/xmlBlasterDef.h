/*------------------------------------------------------------------------------
Name:      xmlBlaster.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Contains general definitions commonly used in the project
Version:   $Id: xmlBlasterDef.h,v 1.14 2003/01/16 21:36:25 laghi Exp $
------------------------------------------------------------------------------*/

#ifndef _UTIL_XMLBLASTERDEF_H
#define _UTIL_XMLBLASTERDEF_H

#ifdef _WINDOWS
#pragma warning(disable:4786)
#pragma warning(disable:4251)
#endif

#include <util/XmlBCfg.h>

// definition for the timestamps
namespace org { namespace xmlBlaster { namespace util {
#if defined(_WINDOWS)
  typedef __int64 Timestamp;
#else
  typedef long long int Timestamp;
#endif

// change this if it does not compile correctly
// #define STRING_TO_TIMESTAMP(x) atoll(x)
// #define      STRING_TO_TIMESTAMP(x) boost::lexical_cast<Timestamp>(x)

#ifndef _UTIL_GLOBAL_H
   class Global; // forward declaration for (chicken-egg)
#endif


}}}

// This is useful to retrieve stack traces in exceptions. If your system
// contains execinfo.h then you can add the ifdef of that system here.
#ifdef __GNUC__
#define _ENABLE_STACK_TRACE_
#include <execinfo.h>
#endif

#endif
