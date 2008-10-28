#define PthreadDraftVersion 10

//
// Include implementation-specific src file.
//

#if defined(__arm__) && defined(__atmos__)
#include "posix.cc"

#elif defined(__osf1__)
#include "posix.cc"

#elif defined(__aix__)
#include "posix.cc"

#elif defined(__MacOSX__) || defined(__IPhoneOS__) || defined(__APPLE__)
#include "posix.cc"

#elif defined(__hpux__)
#include "posix.cc"

#elif defined(__WIN32__)

#if defined(__POSIX_NT__)
#include "posix.cc"
#else
#include "nt.cc"
#endif

#elif defined(WINCE)

// marcelruff.info: To use posix http://sources.redhat.com/pthreads-win32 for Windows CE
#if defined(__POSIX_NT__)
#include "posix.cc"
#else
#include "nt.cc"
#endif
 
#elif defined(__sunos__)
#if __OSVERSION__ != 5
// XXX Workaround for SUN C++ compiler (seen on 4.2) Template.DB code
//     regeneration bug. See omniORB2/CORBA_sysdep.h for details.
#if !defined(__SUNPRO_CC) || __OSVERSION__ != '5'
#error "Only SunOS 5.x or later is supported."
#endif
#endif
#ifdef UseSolarisThreads
#include "solaris.cc"
#else
#include "posix.cc"
#endif

#elif defined(__linux__)
#include "posix.cc"

#elif defined(__nextstep__)
#include "mach.cc"

#elif defined(__VMS)
#include "posix.cc"

#elif defined(__SINIX__)
#include "posix.cc"

#elif defined(__osr5__)
#include "posix.cc"

#elif defined(__uw7__)
#include "posix.cc"

#elif defined(__irix__)
#include "posix.cc"

#elif defined(__freebsd__)
#include "posix.cc"

#elif defined(__rtems__)
#include "posix.cc"

#elif defined(__darwin__)
#include "posix.cc"

#elif defined(__macos__)
#include <sys/time.h>
#define __COMPILE_POSIX_CC__
#include "posix.cc"

#else
#error "No implementation header file"
#endif
