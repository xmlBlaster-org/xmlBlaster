/*------------------------------------------------------------------------------
Name:      xmlBlaster.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Contains general definitions commonly used in the project
Version:   $Id: xmlBlasterDef.h,v 1.10 2002/12/13 12:23:56 laghi Exp $
------------------------------------------------------------------------------*/

#ifndef _UTIL_XMLBLASTERDEF_H
#define _UTIL_XMLBLASTERDEF_H

#ifdef _WINDOWS
#pragma warning(disable:4786)
#pragma warning(disable:4251)
#endif

#include <util/XmlBCfg.h>

// forward declaration to avoid include boost header files 
// note that this number might change
#ifndef BOOST_MUTEX_WEK070601_HPP 
namespace boost { 
   class thread;    
   class mutex;
//   class timed_mutex;
   class condition;
}
#endif

// definition for the timestamps
namespace org { namespace xmlBlaster { namespace util {
#if defined(_WINDOWS)
  typedef __int64 Timestamp;
#else
	typedef long long int Timestamp;
#endif

// change this if it does not compile correctly
#define STRING_TO_TIMESTAMP(x) atoll(x)

#ifndef _UTIL_GLOBAL_H
   class Global;
#endif


}}}

// This is useful to retrieve stack traces in exceptions. If your system
// contains execinfo.h then you can add the ifdef of that system here.
#ifdef __GNUC__
#define _ENABLE_STACK_TRACE_
#include <execinfo.h>
#endif

#endif
