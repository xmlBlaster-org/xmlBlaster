/*----------------------------------------------------------------------------
Name:      basicDefs.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/
#ifndef XMLBLASTER_basicDefs_H
#define XMLBLASTER_basicDefs_H

#ifndef __cplusplus
#  if !defined(__sun) && !defined(_WINDOWS)
#    include <stdbool.h>
#  endif
#  ifndef __bool_true_false_are_defined
#    define bool int
#    define true 1
#    define false 0
#  endif
#endif

#if defined(_WINDOWS)
#  define SNPRINTF _snprintf
#  define VSNPRINTF _vsnprintf
#else
#  define SNPRINTF snprintf
#  define VSNPRINTF vsnprintf
#endif

#ifdef GCC_ANSI  /* Set -DGCC_ANSI on command line if you use the 'gcc --ansi' flag */
#ifndef __USE_BSD /* gcc -ansi on Linux: */
   typedef unsigned short u_short;
#endif
#endif

#ifdef _WINDOWS
#  define _INLINE_FUNC        /* C99 allows to declare functions as 'inline', not supported on WIN cl */
#else
#  ifdef __cplusplus
#    define _INLINE_FUNC      /* 'inline' does not compile with g++ */
#  else
#    define _INLINE_FUNC inline /* C99 allows to declare functions as 'inline' */
#  endif
#endif


#endif /* XMLBLASTER_basicDefs_H */

