/*------------------------------------------------------------------------------
Name:      xmlBlaster.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Contains general definitions commonly used in the project
Version:   $Id: xmlBlasterDef.h,v 1.5 2002/11/29 03:38:45 johnson Exp $
------------------------------------------------------------------------------*/

#ifndef _UTIL_XMLBLASTERDEF_H
#define _UTIL_XMLBLASTERDEF_H

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
}}}

#endif
