//  boost lexical_cast.hpp header  -------------------------------------------//

//  See http://www.boost.org for most recent version including documentation.

#ifndef BOOST_LEXICAL_CAST_INCLUDED
#define BOOST_LEXICAL_CAST_INCLUDED

// what:  lexical_cast custom keyword cast
// who:   contributed by Kevlin Henney, with alternative naming, behaviors
//        and fixes contributed by Dave Abrahams, Daryle Walker and other
//        Boosters on the list
// when:  November 2000
// where: tested with MSVC 6.0, BCC 5.5, and g++ 2.91

#include <sstream>
#include <typeinfo>
#include <util/XmlBCfg.h>

namespace org { namespace xmlBlaster { namespace util {

    class Dll_Export bad_lexical_cast : public std::bad_cast
    {
    public:
        // constructors, destructors, and assignment operator defaulted

        // function inlined for brevity and consistency with rest of library
        virtual const char * what() const throw()
        {
            return "bad lexical cast: "
                   "source type value could not be interpreted as target";
        }
    };

    /**
     * Note: The default double precision is 6 digits and will be rounded
     * You can use <code>interpreter.precision(15);</code> to increase the precision
     * A "double" has ANSI-C minium of 10 digits, on a PC Linux typically 15
     * A "long double" has ANSI-C minium of 10 digits, on a PC Linux typically 18
     * Marcel Ruff
     */
    template<typename Target, typename Source>
    Target lexical_cast(Source arg)
    {
// # ifdef BOOST_LEXICAL_CAST_USE_STRSTREAM
//        std::strstream interpreter; // for out-of-the-box g++ 2.95.2
// # else
        std::stringstream interpreter;
// # endif
        Target result;

        // Precision fix for "double" and "long double"
        // Marcel Ruff 2004-01-17
        //int origPrecision = interpreter.precision(); // 6 digits
        if (typeid(arg) == typeid(double(1.7L)))
            interpreter.precision(15);
        if (typeid(arg) == typeid((long double)(1.7L)))
            interpreter.precision(18);

        if(!(interpreter << arg) || !(interpreter >> result) ||
           !(interpreter >> std::ws).eof())
            throw bad_lexical_cast();

        //interpreter.precision(origPrecision);

        return result;
    }
    Dll_Export template<> std::string lexical_cast(bool arg); // See Global.cpp
    Dll_Export template<> const char * lexical_cast(bool arg);
}}}


// Copyright Kevlin Henney, 2000, 2001, 2002. All rights reserved.
//
// Permission to use, copy, modify, and distribute this software for any
// purpose is hereby granted without fee, provided that this copyright and
// permissions notice appear in all copies and derivatives.
//
// This software is provided "as is" without express or implied warranty.

#endif
