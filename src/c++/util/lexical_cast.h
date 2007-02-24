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
#include <stdio.h> 
#if __GNUC__ == 2
  // g++ 2.95.3 does only know limits.h
#else
#  include <limits>
#endif

#if defined(_WINDOWS)
#  if _MSC_VER >= 1400  /* _WINDOWS: 1200->VC++6.0, 1310->VC++7.1 (2003), 1400->VC++8.0 (2005) */
//#    define XMLBLASTER_CPP_SNPRINTF snprintf0
#    define XMLBLASTER_CPP_SNPRINTF sprintf_s
#  else
#    define XMLBLASTER_CPP_SNPRINTF _snprintf
#  endif
#else
#  define XMLBLASTER_CPP_SNPRINTF snprintf
#endif

namespace org { namespace xmlBlaster { namespace util {

#if __sun__
# define DISABLE_WIDE_CHAR_SUPPORT
#endif

#if __GNUC__ == 2
   // Marcel 2004-04-01:
   // Is buggy for lexical_cast<string>(string("")): empty strings throw a bad_lexical_cast
   // Newest version from boost handles it but did not compile on g++ 2.9x
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
#else
   // Marcel 2004-04-01:
   // Copy from current boost.org cvs, slightly modified
   // to be not depending on other boost headers,
   // and added more expressive exception.what() text

    // exception used to indicate runtime lexical_cast failure
    class bad_lexical_cast : public std::bad_cast
    {
    public:
        bad_lexical_cast() :
        source(&typeid(void)), target(&typeid(void))
        {
        }
        bad_lexical_cast(
            const std::type_info &s,
            const std::type_info &t) :
            source(&s), target(&t)
        {
        }
        const std::type_info &source_type() const
        {
            return *source;
        }
        const std::type_info &target_type() const
        {
            return *target;
        }
        virtual const char *what() const throw()
        {
            XMLBLASTER_CPP_SNPRINTF((char*)str_, 255, "bad lexical cast: source type value '%.50s' could not be interpreted as target '%.50s'", 
                     source->name(), target->name());
            return str_;
        }
        virtual ~bad_lexical_cast() throw()
        {
        }
    private:
        const std::type_info *source;
        const std::type_info *target;
        char str_[256];
    };

    namespace detail // selectors for choosing stream character type
    {
        template<typename Type>
        struct stream_char
        {
            typedef char type;
        };

        #ifndef DISABLE_WIDE_CHAR_SUPPORT
        template<>
        struct stream_char<wchar_t>
        {
            typedef wchar_t type;
        };

        template<>
        struct stream_char<wchar_t *>
        {
            typedef wchar_t type;
        };

        template<>
        struct stream_char<const wchar_t *>
        {
            typedef wchar_t type;
        };

        template<>
        struct stream_char<std::wstring>
        {
            typedef wchar_t type;
        };
        #endif

        template<typename TargetChar, typename SourceChar>
        struct widest_char
        {
            typedef TargetChar type;
        };

        template<>
        struct widest_char<char, wchar_t>
        {
            typedef wchar_t type;
        };
    }
    
    namespace detail // stream wrapper for handling lexical conversions
    {
        template<typename Target, typename Source>
        class lexical_stream
        {
        public:
            lexical_stream()
            {
                stream.unsetf(std::ios::skipws);

                if(std::numeric_limits<Target>::is_specialized)
                    stream.precision(std::numeric_limits<Target>::digits10 + 1);
                else if(std::numeric_limits<Source>::is_specialized)
                    stream.precision(std::numeric_limits<Source>::digits10 + 1);
            }
            ~lexical_stream()
            {
                #if defined(BOOST_NO_STRINGSTREAM)
                stream.freeze(false);
                #endif
            }
            bool operator<<(const Source &input)
            {
                return !(stream << input).fail();
            }
            template<typename InputStreamable>
            bool operator>>(InputStreamable &output)
            {
                return /*!is_pointer<InputStreamable>::value &&*/
                       stream >> output &&
                       (stream >> std::ws).eof();
            }
            bool operator>>(std::string &output)
            {
                #if defined(BOOST_NO_STRINGSTREAM)
                stream << '\0';
                #endif
                output = stream.str();
                return true;
            }
            #ifndef DISABLE_WIDE_CHAR_SUPPORT
            bool operator>>(std::wstring &output)
            {
                output = stream.str();
                return true;
            }
            #endif
        private:
            typedef typename widest_char<
                typename stream_char<Target>::type,
                typename stream_char<Source>::type>::type char_type;

            #if defined(BOOST_NO_STRINGSTREAM)
            std::strstream stream;
            #elif defined(BOOST_NO_STD_LOCALE)
            std::stringstream stream;
            #else
            std::basic_stringstream<char_type> stream;
            #endif
        };
    }

    template<typename Target, typename Source>
    Target lexical_cast(Source arg)
    {
        detail::lexical_stream<Target, Source> interpreter;
        Target result;

        if(!(interpreter << arg && interpreter >> result))
            throw bad_lexical_cast(typeid(Target), typeid(Source));
        return result;
    }
#endif

//#if __GNUC__ == 2 || defined(__sun)
#if __GNUC__ == 2 || defined(__SUNPRO_CC)
//#if __GNUC__ == 2
  // Problems with g++ 2.95.3 and template<>
#else
   /**
    * Specialization which returns "true" instead of "1". 
    * replaces Global::getBoolAsString(bool)
    */
   template<> Dll_Export std::string lexical_cast(bool arg); // See Global.cpp

   /**
    * Specialization which returns "true" instead of "1". 
    */
   template<> Dll_Export const char * lexical_cast(bool arg);

   /**
    * Laghi 2004-07-04
    * Have a separate string -> string mapper because '<< string' does strip everything
    * after white spaces in the string and on SUN it results in an exception. 
    * Don't try 'const string &' as it is not invoked!
    */
   template<> Dll_Export std::string lexical_cast(std::string arg); // See Global.cpp

   /**
    * Laghi 2004-10-28
    * transforming 'true' or 'false' to bool fails
    */
   template<> Dll_Export bool lexical_cast(std::string arg); // See Global.cpp
   template<> Dll_Export bool lexical_cast(const char* arg); // See Global.cpp

#endif
}}}


// Copyright Kevlin Henney, 2000, 2001, 2002. All rights reserved.
//
// Permission to use, copy, modify, and distribute this software for any
// purpose is hereby granted without fee, provided that this copyright and
// permissions notice appear in all copies and derivatives.
//
// This software is provided "as is" without express or implied warranty.

#endif
