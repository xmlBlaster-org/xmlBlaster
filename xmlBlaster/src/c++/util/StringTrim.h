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
   template <class CharT> class Dll_Export StringTrim
   {
      
   public:
      
      /** 
       * Default constructor. Does nothing
       */
      StringTrim()
      {
      }
      

      /**
       * returns the length of the character string str. (by detecting the 
       * first occurence of zero.
       */
      int stringLength(const CharT *str) const
      {
         int count = 0;
         while (str[count] != (CharT)0) count++;
         return count;
      }
      

      /**
       * trims the start of the string (whitespaces, newlines and tabs are
       * the trimmed characters). It creates a new string, so the caller is
       * responsible for deleting the return string which has been allocated by
       * this method.
       */
      CharT* trimStart(const CharT *str) const
      {
         int length = stringLength(str), start = 0;
         while ( (start      != length     ) && 
                 ( (str[start] == (CharT)' ' ) ||
                   (str[start] == (CharT)'\t') ||
                   (str[start] == (CharT)'\n') ) ) start++;
         if (start == length) return 0;
         int sizeOfNewString = length - start + 1; // zero terminated string !!
         CharT *ret = new CharT[sizeOfNewString];
         for (int i=0; i < sizeOfNewString; i++) ret[i] = str[start+i];
         return ret;
      }
      

      /**
       * trims the end of the string (whitespaces, newlines and tabs are
       * the trimmed characters). It creates a new string, so the caller is
       * responsible for deleting the return string which has been allocated by
       * this method.
       */
      CharT* trimEnd(const CharT *str) const
      {
         int length = stringLength(str), end = length-1;
         while ( (end        != -1     ) && 
                 ( (str[end] == (CharT)' ' ) ||
                   (str[end] == (CharT)'\t') ||
                   (str[end] == (CharT)'\n') ) ) end--;
         if (end == -1) return 0;
         int sizeOfNewString = end + 2; // zero terminated string !!
         CharT *ret = new CharT[sizeOfNewString];
         for (int i=0; i < sizeOfNewString; i++) ret[i] = str[i];
         ret[sizeOfNewString-1] = (CharT)0;
         return ret;
      }      
      

      /**
       * trims the start and the end of the string (whitespaces, newlines and 
       * tabs are the trimmed characters). It creates a new string, so the 
       * caller is responsible for deleting the return string which has been 
       * allocated by this method.
       */
      CharT* trim(const CharT *str) const
      {
         CharT* buffer = trimStart(str);
         if (buffer == 0) return 0;
         CharT* ret = trimEnd(buffer);
         if (buffer) delete buffer;
         return ret;
      }
      
   };
}}} // namespace

#endif

