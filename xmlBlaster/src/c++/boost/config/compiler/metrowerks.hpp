//  (C) Copyright Boost.org 2001. Permission to copy, use, modify, sell and
//  distribute this software is granted provided this copyright notice appears
//  in all copies. This software is provided "as is" without express or implied
//  warranty, and with no claim as to its suitability for any purpose.

//  See http://www.boost.org for most recent version.

//  Metrowerks C++ compiler setup:

// locale support is disabled when linking with the dynamic runtime
#   ifdef _MSL_NO_LOCALE
#     define BOOST_NO_STD_LOCALE
#   endif 

#   if __MWERKS__ <= 0x2301  // 5.3
#     define BOOST_NO_FUNCTION_TEMPLATE_ORDERING
#     define BOOST_NO_POINTER_TO_MEMBER_CONST
#     define BOOST_NO_DEPENDENT_TYPES_IN_TEMPLATE_VALUE_PARAMETERS
#     define BOOST_NO_MEMBER_TEMPLATE_KEYWORD
#   endif

#   if __MWERKS__ <= 0x2401  // 6.2
//#     define BOOST_NO_FUNCTION_TEMPLATE_ORDERING
#   endif

#   if(__MWERKS__ <= 0x2407)  // 7.x
#     define BOOST_NO_MEMBER_TEMPLATE_FRIENDS
#     define BOOST_NO_MEMBER_FUNCTION_SPECIALIZATIONS
#   endif

#if !__option(wchar_type)
#   define BOOST_NO_INTRINSIC_WCHAR_T
#endif

#   if __MWERKS__ == 0x3000
#     define BOOST_COMPILER_VERSION 8.0
#   elif __MWERKS__ == 0x3001
#     define BOOST_COMPILER_VERSION 8.1
#   else
#     define BOOST_COMPILER_VERSION __MWERKS__
#   endif 

#define BOOST_COMPILER "Metrowerks CodeWarrior C++ version " BOOST_STRINGIZE(BOOST_COMPILER_VERSION)

//
// versions check:
// we don't support Metrowerks prior to version 5.3:
#if __MWERKS__ < 0x2301
#  error "Compiler not supported or configured - please reconfigure"
#endif
//
// last known and checked version is 0x3001:
#if (__MWERKS__ > 0x3001)
#  if defined(BOOST_ASSERT_CONFIG)
#     error "Unknown compiler version - please run the configure tests and report the results"
#  endif
#endif






