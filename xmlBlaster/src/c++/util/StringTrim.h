/*-----------------------------------------------------------------------------
Name:      StringTrim.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to emulate the java String.trim() method
Author:    <Michele Laghi> laghi@swissinfo.org
-----------------------------------------------------------------------------*/

#ifndef _UTIL_STRINGTRIM_H
#define _UTIL_STRINGTRIM_H

#include <util/XmlBCfg.h>
#include <ctype.h>  // ::tolower
#include <string>

#define  EMPTY_STRING std::string("")



namespace org { namespace xmlBlaster {
namespace util {
   
   /**
    * This template class is used to trim a std::string the same way the String.trim
    * java method does. It can be instantiated with which character type you
    * want and will therefore work even with xerces XMLCh* std::strings. </p>
    * This class is part of the package because the XMLString::trim(...) 
    * method in the xerces package does not fullfill our requirements.
    */
   class Dll_Export StringTrim
   {
   public:
      
      /** 
       * Default constructor. Does nothing
       */
      StringTrim()
      {
      }

      /**
       * This method converts a string to lowercase. Note that the input string is
       * modified and a reference to it is returned.
       */
      static std::string& toLowerCase(std::string& ref)
      {
         std::string::iterator iter = ref.begin();
         while (iter != ref.end()) {
            *iter = ::tolower(*iter);
            iter++;
         }
         return ref;
      }

      /**
       * Replace all occurrences of "from" with to "to".
       */
      static std::string replaceAll(const std::string &str, const std::string &from, const std::string &to) {
         if (str.empty() || from.empty() || to.empty())
            return str;
         if (str.find(from) == std::string::npos)
            return str;

         std::string buf;
         std::string tail = str;
         while (true) {
            std::string::size_type index = tail.find(from);
            if (index != std::string::npos) {
               if (index > 0)
                  buf += tail.substr(0, index);
               buf += to;
               tail = tail.substr(index + from.size());
            }
            else
               break;
         }
         buf += tail;
         return buf;
      }

      /**
       * @param str The checked std::string (is not modified)
       * @return true if trimmed std::string is "TRUE" or "true" or "1" else false
       */
      static bool isTrue(const std::string& str) {
         std::string tmp = trim(str.c_str());
         return std::string("true")==tmp || std::string("1")==tmp || std::string("TRUE")==tmp;
      }

      /**
       * Use this method instead of isTrue(std::string&) if your input std::string may
       * be manipulated (it performs a bit better then the other method). 
       * @param str The checked std::string (is trimmed!!)
       * @return true if trimmed std::string is "TRUE" or "true" or "1" else false
       */
      static bool isTrueTrim(std::string& str) {
         trim(str);
         return std::string("true")==str || std::string("1")==str || std::string("TRUE")==str;
      }

      /**
       * returns the length of the character std::string str. (by detecting the 
       * first occurence of zero.
       */
       /*
      static int std::stringLength(const char *str)
      {
         int count = 0;
         while (str[count] != (char)0) count++;
         return count;
      }
        */

      /**
       * Trims the start of the std::string (whitespaces, newlines etc.). 
       */
      static std::string trimStart(const char *str)
      {
         if (str == static_cast<const char *>(0)) return EMPTY_STRING;

         int start = 0;
         while (str[start] != 0)
            if (isspace(str[start++]))
               continue;
            else
               return std::string(str + (start-1));

         return EMPTY_STRING;
      }
      

      /**
       * @param str The given std::string will be trimmed (in - out parameter)
       * @param Returns the trimmed given parameter (the str instance)
       * @see #trimStart(const char *)
       */
      static std::string& trimStart(std::string &str)
      {
         if (str.capacity() < 1) return str;

         if (str.length() < 1)
            return str;
         if (!isspace(str[0]))
            return str;

         for (std::string::size_type ii=1; ii<str.length(); ii++) {
            if (!isspace(str[ii])) {
               str = str.substr(ii);
               return str;
            }
         }

         return str;
      }

      /**
       * Trims all spaces from the end of the std::string (whitespaces, newlines etc). 
       */
      static std::string trimEnd(const char *str)
      {
         if (str == static_cast<const char *>(0) || *str == 0) return EMPTY_STRING;
         std::string strip(str);
         trimEnd(strip);
         return strip;
      }      

      /**
       * @param str The given std::string will be trimmed (in - out parameter)
       * @param Returns the trimmed given parameter (the str instance)
       * @see #trimEnd(const char *)
       */
      static std::string& trimEnd(std::string &str)
      {
         if (str.capacity() < 1) return str;

         int i;
         for (i=str.length()-1; i >= 0; i--) {
             if (!isspace(str[i])) {
                 str = str.substr(0, i+1);
                 return str;
             }
         }
         if (i<0) str = EMPTY_STRING;
         return str;
      }
      

      /**
       * Trims all spaces (like blanks and newlines) at the start and the end of the std::string. 
       * <pre>
       * "  \t Hello \t World \n" -> "Hello \t World"
       * </pre>
       * @param str The given std::string
       * @return The trimmed std::string
       */
      static std::string trim(const char *str)
      {
         std::string buffer = trimStart(str);
         if (buffer.empty()) return EMPTY_STRING;
         return trimEnd(buffer.c_str());
      }

      /**
       * @param str The given std::string will be trimmed (in - out parameter)
       * @param Returns the trimmed given parameter (the str instance)
       * @see #trim(const char *)
       */
      static void trim(std::string &str)
      {
         if (str.capacity() < 1 || str.size() < 1) return;

         int jj=0;
         if (isspace(str[str.size()-1])) {
            for (jj=str.size()-2; jj >= 0; jj--) {
                if (!isspace(str[jj])) {
                   str.resize(jj+1);
                   break;
                }
            }
         }
         if (jj<0) {
            str = EMPTY_STRING;
            return;
         }

         if (!isspace(str[0]))
            return;
         for (std::string::size_type ii=1; ii<str.size(); ii++) {
            if (!isspace(str[ii])) {
               str = str.substr(ii);
               return;
            }
         }
      }

      static std::string trim(const std::string &str) {
         std::string tmp = str;
         trim(tmp);
         return tmp;
      }

   };

}}} // namespace

#endif


