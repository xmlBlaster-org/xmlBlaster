//  (C) Copyright Boost.org 2001. Permission to copy, use, modify, sell and
//  distribute this software is granted provided this copyright notice appears
//  in all copies. This software is provided "as is" without express or implied
//  warranty, and with no claim as to its suitability for any purpose.

//  See http://www.boost.org for most recent version.

//  Borland C++ compiler setup:

#   if __BORLANDC__ <= 0x0550
// Borland C++ Builder 4 and 5:
#     define BOOST_NO_MEMBER_TEMPLATE_FRIENDS
#     if __BORLANDC__ == 0x0550
// Borland C++ Builder 5, command-line compiler 5.5:
#       define BOOST_NO_OPERATORS_IN_NAMESPACE
#     endif
#   endif

#if (__BORLANDC__ >= 0x550)
// <climits> is partly broken, some macos define symbols that are really in
// namespace std, so you end up having to use illegal constructs like
// std::DBL_MAX, as a fix we'll just include float.h and have done with:
#include <float.h>
#endif

// Version 5.51:
#if (__BORLANDC__ <= 0x551) || !defined(BOOST_STRICT_CONFIG)
#  define BOOST_NO_CV_SPECIALIZATIONS
#  define BOOST_NO_CV_VOID_SPECIALIZATIONS
#  define BOOST_NO_INTEGRAL_INT64_T
#  define BOOST_NO_PRIVATE_IN_AGGREGATE
#  define BOOST_NO_DEPENDENT_NESTED_DERIVATIONS
#  define BOOST_NO_SWPRINTF
#  define BOOST_NO_USING_TEMPLATE
#endif

#define BOOST_COMPILER "Borland C++ version " BOOST_STRINGIZE(__BORLANDC__)

//
// versions check:
// we don't support Borland prior to version 5.4:
#if __BORLANDC__ < 0x540
#  error "Compiler not supported or configured - please reconfigure"
#endif
//
// last known and checked version is 5.51:
#if (__BORLANDC__ > 0x551)
#  if defined(BOOST_ASSERT_CONFIG)
#     error "Unknown compiler version - please run the configure tests and report the results"
#  else
#     pragma message( "Unknown compiler version - please run the configure tests and report the results")
#  endif
#endif


