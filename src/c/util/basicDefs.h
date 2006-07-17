/*----------------------------------------------------------------------------
Name:      basicDefs.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Note:      The gcc and icc (>=8) both define __GNUC__
-----------------------------------------------------------------------------*/
#ifndef XMLBLASTER_basicDefs_H
#define XMLBLASTER_basicDefs_H

#if defined(_WINDOWS)
# define Blaster_Export_Flag __declspec (dllexport)
# define Blaster_Import_Flag __declspec (dllimport)
# if defined(DLL_BUILD)
#   define Dll_Export Blaster_Export_Flag
# elif defined(DLL_IGNORE)
#   define Dll_Export
# else
#   define Dll_Export Blaster_Import_Flag
#endif
#else
# define Dll_Export
#endif

#ifndef __cplusplus
#  if !defined(__sun) && !defined(_WINDOWS)  /* __GNUC_MINOR__=85 : gcc 2.85 does not like it either: how to define? */
#    include <stdbool.h>
#  endif
#  ifndef __bool_true_false_are_defined
#    define bool int
#    define true 1
#    define false 0
#  endif
#       define XMLBLASTER_C_bool bool
#else
#if defined(_WINDOWS)
#define XMLBLASTER_C_bool int
#else
#define XMLBLASTER_C_bool bool
#endif
#endif

#if defined(_WINDOWS)
#  define SNPRINTF _snprintf
#  define VSNPRINTF _vsnprintf
#else
#  define SNPRINTF snprintf
#  define VSNPRINTF vsnprintf    /* stdarg.h */
#endif

#if defined(_WINDOWS)
  typedef __int64 int64_t;
# define PRINTF_PREFIX_INT64_T "%I64d"
# define PRINTF_PREFIX_UINT64_T "%I64u"
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

  /* __unix __WIN64 __WIN32 */
# if __LP64__
   /* For example a Xeon processor with UNIX */
#  define PRINTF_PREFIX_INT64_T "%ld"
#  define PRINTF_PREFIX_UINT64_T "%lu"
   /*#elif __ILP64__ __LLP64__ */
# else
#  define PRINTF_PREFIX_INT64_T "%lld"
#  define PRINTF_PREFIX_UINT64_T "%llu"
# endif

#endif
/*#define INT64_DIGITLEN_MAX 19  Size of a max int64_t dumped to a string: LLONG_MAX from limits.h 9223372036854775807 */
#define INT64_STRLEN_MAX 22 /**< Size of a max int64_t dumped to a string including an optional LL and termination '\0': LLONG_MAX from limits.h 9223372036854775807LL */


#ifdef GCC_ANSI  /**< Set -DGCC_ANSI on command line if you use the 'gcc --ansi' flag */
#ifndef __USE_BSD /**< gcc -ansi on Linux: */
   typedef unsigned short u_short;
#endif
#endif

#ifdef _WINDOWS
#  define _INLINE_FUNC        /* C99 allows to declare functions as 'inline', not supported on WIN cl */
#else
#  ifdef __cplusplus
#    define _INLINE_FUNC      /* 'inline' does not compile with g++ */
#  elif __GNUC__
#    define _INLINE_FUNC __inline__ /* http://gcc.gnu.org/onlinedocs/gcc/Alternate-Keywords.html */
#  else
#    define _INLINE_FUNC inline /* C99 allows to declare functions as 'inline', it has internal linkage -> code to be in same file as call */
#  endif
#endif

#if defined(_WINDOWS)
#  define socklen_t int
#  define ssize_t signed int
#elif defined(__alpha)
#  define socklen_t int
#else
#  include <unistd.h>
#endif

#ifndef XB_NO_PTHREADS
#  define XB_USE_PTHREADS 1 /**< Used to dump thread ID in default logging output, undef it if you run single threaded */
#endif

/**
 * Declare function callback pointer about how many bytes are read from socket. 
 * @param userP A user pointer
 * @param currBytesRead The currently number of bytes read
 * @param nbytes Totally expected bytes
 */
typedef void ( * XmlBlasterNumReadFunc)(void *xb, const size_t currBytesRead, const size_t nbytes);

#endif /* XMLBLASTER_basicDefs_H */

