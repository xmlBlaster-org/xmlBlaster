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
#include <ctype.h>
#include <string>

#define  EMPTY_STRING string("")

using namespace std;

namespace org { namespace xmlBlaster {
namespace util {
   
   /**
    * This template class is used to trim a string the same way the String.trim
    * java method does. It can be instantiated with which character type you
    * want and will therefore work even with xerces XMLCh* strings. </p>
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
       * @param str The checked string (is not modified)
       * @return true if trimmed string is "TRUE" or "true" else false
       */
      static bool isTrue(const string& str) {
         string tmp = trim(str.c_str());
         return string("true")==tmp || string("TRUE")==tmp;
      }

      /**
       * Use this method instead of isTrue(string&) if your input string may
       * be manipulated (it performs a bit better then the other method). 
       * @param str The checked string (is trimmed!!)
       * @return true if trimmed string is "TRUE" or "true" else false
       */
      static bool isTrueTrim(string& str) {
         trim(str);
         return string("true")==str || string("TRUE")==str;
      }

      /**
       * returns the length of the character string str. (by detecting the 
       * first occurence of zero.
       */
       /*
      static int stringLength(const char *str)
      {
         int count = 0;
         while (str[count] != (char)0) count++;
         return count;
      }
        */

      /**
       * Trims the start of the string (whitespaces, newlines etc.). 
       */
      static string trimStart(const char *str)
      {
         if (str == static_cast<const char *>(0)) return EMPTY_STRING;

         int start = 0;
         while (str[start] != 0)
            if (isspace(str[start++]))
               continue;
            else
               return string(str + (start-1));

         return EMPTY_STRING;
      }
      

      /**
       * @param str The given string will be trimmed (in - out parameter)
       * @param Returns the trimmed given parameter (the str instance)
       * @see #trimStart(const char *)
       */
      static string& trimStart(string &str)
      {
         if (str.capacity() < 1) return str;

         if (str.length() < 1)
            return str;
         if (!isspace(str[0]))
            return str;

         for (string::size_type ii=1; ii<str.length(); ii++) {
            if (!isspace(str[ii])) {
               str = str.substr(ii);
               return str;
            }
         }

         return str;
      }

      /**
       * Trims all spaces from the end of the string (whitespaces, newlines etc). 
       */
      static string trimEnd(const char *str)
      {
         if (str == static_cast<const char *>(0) || *str == 0) return EMPTY_STRING;
         string strip(str);
         trimEnd(strip);
         return strip;
      }      

      /**
       * @param str The given string will be trimmed (in - out parameter)
       * @param Returns the trimmed given parameter (the str instance)
       * @see #trimEnd(const char *)
       */
      static string& trimEnd(string &str)
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
       * Trims all spaces (like blanks and newlines) at the start and the end of the string. 
       * <pre>
       * "  \t Hello \t World \n" -> "Hello \t World"
       * </pre>
       * @param str The given string
       * @return The trimmed string
       */
      static string trim(const char *str)
      {
         string buffer = trimStart(str);
         if (buffer.empty()) return EMPTY_STRING;
         return trimEnd(buffer.c_str());
      }

      /**
       * @param str The given string will be trimmed (in - out parameter)
       * @param Returns the trimmed given parameter (the str instance)
       * @see #trim(const char *)
       */
      static string& trim(string &str)
      {
         if (str.capacity() < 1 || str.size() < 1) return str;

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
            return str;
         }

         if (!isspace(str[0]))
            return str;
         for (string::size_type ii=1; ii<str.size(); ii++) {
            if (!isspace(str[ii])) {
               str = str.substr(ii);
               return str;
            }
         }
         return str;
      }
   };
}}} // namespace

#endif


