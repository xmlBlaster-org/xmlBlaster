/*------------------------------------------------------------------------------
Name:      xmlBlaster.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Contains general definitions commonly used in the project
Version:   $Id: xmlBlasterDef.h,v 1.4 2002/11/27 10:53:40 laghi Exp $
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
	typedef long long int Timestamp;
}}}

#endif
