
// (C) Copyright Steve Cleary, Beman Dawes, Howard Hinnant & John Maddock 2000.
// Permission to copy, use, modify, sell and distribute this software is 
// granted provided this copyright notice appears in all copies. This software 
// is provided "as is" without express or implied warranty, and with no claim 
// as to its suitability for any purpose.
//
// See http://www.boost.org for most recent version including documentation.

#ifndef BOOST_TT_IS_COMPOUND_HPP_INCLUDED
#define BOOST_TT_IS_COMPOUND_HPP_INCLUDED

#include "boost/type_traits/is_array.hpp"
#include "boost/type_traits/is_pointer.hpp"
#include "boost/type_traits/is_reference.hpp"
#include "boost/type_traits/is_class.hpp"
#include "boost/type_traits/is_union.hpp"
#include "boost/type_traits/is_enum.hpp"
#include "boost/type_traits/is_member_pointer.hpp"
#include "boost/type_traits/detail/ice_or.hpp"
#include "boost/config.hpp"

// should be the last #include
#include "boost/type_traits/detail/bool_trait_def.hpp"

namespace boost {

namespace detail {

template <typename T>
struct is_compound_impl
{
   BOOST_STATIC_CONSTANT(bool, value =
      (::boost::type_traits::ice_or<
         ::boost::is_array<T>::value,
         ::boost::is_pointer<T>::value,
         ::boost::is_reference<T>::value,
         ::boost::is_class<T>::value,
         ::boost::is_union<T>::value,
         ::boost::is_enum<T>::value,
         ::boost::is_member_pointer<T>::value
      >::value));
};

} // namespace detail

BOOST_TT_AUX_BOOL_TRAIT_DEF1(is_compound,T,::boost::detail::is_compound_impl<T>::value)

} // namespace boost

#include "boost/type_traits/detail/bool_trait_undef.hpp"

#endif // BOOST_TT_IS_COMPOUND_HPP_INCLUDED
