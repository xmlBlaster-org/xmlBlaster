// Boost.Function library
//
// Copyright (C) 2001 Doug Gregor (gregod@cs.rpi.edu)
//
// Permission to copy, use, sell and distribute this software is granted
// provided this copyright notice appears in all copies.
// Permission to modify the code and to distribute modified code is granted
// provided this copyright notice appears in all copies, and a notice
// that the code was modified is included with the copyright notice.
//
// This software is provided "as is" without express or implied warranty,
// and with no claim as to its suitability for any purpose.
 
// For more information, see http://www.boost.org

#ifndef BOOST_FUNCTION_FUNCTION7_HEADER
#define BOOST_FUNCTION_FUNCTION7_HEADER

#define BOOST_FUNCTION_NUM_ARGS 7
#define BOOST_FUNCTION_TEMPLATE_PARMS typename T0, typename T1, typename T2, typename T3, typename T4, typename T5, typename T6
#define BOOST_FUNCTION_TEMPLATE_ARGS T0, T1, T2, T3, T4, T5, T6
#define BOOST_FUNCTION_PARMS T0 a0, T1 a1, T2 a2, T3 a3, T4 a4, T5 a5, T6 a6
#define BOOST_FUNCTION_ARGS a0, a1, a2, a3, a4, a5, a6
#define BOOST_FUNCTION_NOT_0_PARMS T1 a1, T2 a2, T3 a3, T4 a4, T5 a5, T6 a6
#define BOOST_FUNCTION_NOT_0_ARGS a1, a2, a3, a4, a5, a6
#define BOOST_FUNCTION_ARG_TYPES typedef T0 arg1_type; typedef T1 arg2_type; typedef T2 arg3_type; typedef T3 arg4_type; typedef T4 arg5_type; typedef T5 arg6_type; typedef T6 arg7_type;

#include <boost/function/function_template.hpp>

#undef BOOST_FUNCTION_ARG_TYPES
#undef BOOST_FUNCTION_NOT_0_ARGS
#undef BOOST_FUNCTION_NOT_0_PARMS
#undef BOOST_FUNCTION_ARGS
#undef BOOST_FUNCTION_PARMS
#undef BOOST_FUNCTION_TEMPLATE_ARGS
#undef BOOST_FUNCTION_TEMPLATE_PARMS
#undef BOOST_FUNCTION_NUM_ARGS

#endif // BOOST_FUNCTION_FUNCTION7_HEADER
