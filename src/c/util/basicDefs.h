/*----------------------------------------------------------------------------
Name:      basicDefs.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Note:      The gcc and icc (>=8) both define __GNUC__
Note:      cl.exe (Windows) always defines _WIN32
           __cplusplus    Defined for C++ programs only.
           _CHAR_UNSIGNED Defined when /J is specified.
           _CPPRTTI  Defined for code compiled with the /GR (Enable Run-Time Type Information) option.
           _CPPUNWIND  Defined for code compiled with the /GX (Enable Exception Handling) option.
                          _DLL      Defined when /MD or /MDd - Multithread DLL is specified.
           _M_IX86   (x86 specific) Defined as 500 for Blend (/GB), 300 for 80386 (/G3), 400 for 80486 (/G4), 500 for Pentium (/G5), and 600 for Pentium Pro (/G6).
           _MSC_VER  Defines the compiler version. Always defined.
                     1200: VC++ 6.0 Microsoft Visual C++ version 6.0 or later
                                                        1310: VC++ 7.1 2003 Microsoft (R) 32-bit C/C++ 13.10.3077 for 80x86 1984-2002
                     1400: VC++ 8.0 2005 14.00.50727.42
           _WIN32    (x86 specific) Defined for applications for WIN32. Always defined.
           _MT       Defined when /MD or /MT is specified.
           http://msdn.microsoft.com/library/default.asp?url=/library/en-us/vccore98/HTML/_core_.2f.u.2c_2f.u.asp
           http://msdn.microsoft.com/en-us/library/b0084kay(VS.80).aspx
-----------------------------------------------------------------------------*/
#ifndef XMLBLASTER_basicDefs_H
#define XMLBLASTER_basicDefs_H

/*
  __IPhoneOS__ is used for Apple iPhone
  __MacOSX__   (__OBJC__ __APPLE__ are predefined)
  __FreeBSD__
  __hpux__
  __linux__
  _WINDOWS
  WINCE
 e.g. #if defined(_WINDOWS) || defined(WINCE)
 */

#if defined(_WIN32)
#  if !defined(_WINDOWS)
#     define _WINDOWS /* We work with _WINDOWS and _MSC_VER in our code */
#  endif
#else  /* UNIX */
/*#  define _MSC_VER 0 not possible as #ifdefs will return true!*/ /* Dummy for UNIX to avoid compiler warnings at: #if _MSC_VER >= 1400 */
#endif

#if defined(_WINDOWS)
# define Blaster_Export_Flag __declspec (dllexport)
# define Blaster_Import_Flag __declspec (dllimport)
# if defined(_WINDLL)
#   define Dll_Export Blaster_Export_Flag
# elif defined(DLL_IGNORE)
#   define Dll_Export
# else
#   define Dll_Export Blaster_Import_Flag
#endif
#else
# define Dll_Export
#endif

/* Note: cast int to bool like this: i!=0 which is true or false depending on i */
/* Windows Note for bool:
The bool type participates in integral promotions. An r-value of type bool can be converted to an r-value of type int, with false becoming zero and true becoming one.
Microsoft Specific:
In Visual C++4.2, the Standard C++ header files contained a typedef that equated bool with int.
In Visual C++ 5.0 and later, bool is implemented as a built-in type with a size of 1 byte.
That means that for Visual C++ 4.2, a call of sizeof(bool) yields 4, while in Visual C++ 5.0 and later, the same call yields 1.
This can cause memory corruption problems if you have defined structure members of type bool in Visual C++ 4.2 and are mixing object files (OBJ) and/or
DLLs built with the 4.2 and 5.0 or later compilers. 
*/
#ifndef __cplusplus
#  if !defined(__sun) && !defined(_WINDOWS)  /* __GNUC_MINOR__=85 : gcc 2.85 does not like it either: how to define? */
#    include <stdbool.h>
#  endif
#  ifndef __bool_true_false_are_defined
#    define bool char   /* Can Windows CE P/Invoke handle this? */
#    define true (char)1
#    define false (char)0
/*#    define bool int   Not possible if used with C++ as a bool is 1byte and struct alignement broken, savest ist probably: build -DXMLBLASTER_C_COMPILE_AS_CPP=1 c cpp
#    define true 1
#    define false 0*/
#  endif
#  define XMLBLASTER_C_bool bool
#else  /* __cplusplus */
#  define XMLBLASTER_C_bool bool
#endif

#if defined(_WINDOWS)
#  if _MSC_VER >= 1400  /* _WINDOWS: 1200->VC++6.0, 1310->VC++7.1 (2003), 1400->VC++8.0 (2005) */
#    define SNPRINTF snprintf0
     /* sscanf_s( tokenstring, "%s", s, sizeof(s) );   !! for %s: two args, the second with size !! */
#    define VSNPRINTF _vsnprintf
#  else
#    define SNPRINTF _snprintf
#    define VSNPRINTF _vsnprintf
#  endif
#else
#  define SNPRINTF snprintf
#  define VSNPRINTF vsnprintf    /* stdarg.h */
#endif

#if defined(_WINDOWS)
  typedef unsigned __int64 uint64_t;
  typedef __int64 int64_t;
# define PRINTF_PREFIX_INT64_T "%I64d"
# define PRINTF_PREFIX_UINT64_T "%I64u"
  /*typedef long long int64_t;*/
  typedef unsigned __int32 uint32_t;
  typedef __int32 int32_t;
  typedef unsigned __int16 uint16_t;
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

  /* TODO: Change to inttypes.h: PRId64 (ld or lld) instead of PRINTF_PREFIX_INT64_T */
  /* unfortunately PRId64 is missing on Windows and if we do C++ instead of C compile */
  /* PRId64 as 32bit system need "%lld" and 64 bit systems need "%ld" */

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

