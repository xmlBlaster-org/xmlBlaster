/*-----------------------------------------------------------------------------
Name:      Base64.h
Project:   xmlBlaster.org
Copyright: 2001-2002 Randy Charles Morin randy@kbcafe.com
           www.kbcafe.com
           http://www.kbcafe.com/articles/HowTo.Base64.pdf
           Allowed to distribute under xmlBlasters LGPL in email
           from Randy Charles Morin <randy@kbcafe.com> from 2004-01-18
Minor change by Marcel Ruff xmlBlaster@marcelruff.info
http://www.xmlBlaster.org
-----------------------------------------------------------------------------*/

#ifndef _UTIL_BASE64_H
#define _UTIL_BASE64_H

#include <util/xmlBlasterDef.h>
# include <string>
# include <vector>

namespace org { namespace xmlBlaster { namespace util {

/**
 * Base64 encoding/decoding. 
 * @author Copyright 2001-2002 Randy Charles Morin randy@kbcafe.com
 * @see http://www.kbcafe.com/articles/HowTo.Base64.pdf
 */
class Dll_Export Base64 {
   static char Encode(unsigned char uc);
   static unsigned char Decode(char c);
   static bool IsBase64(char c);
 public:
   static std::string Encode( const std::vector<unsigned char> & vby);
   static std::vector<unsigned char> Decode( const std::string & str);
};

inline char Base64::Encode(unsigned char uc)
{
   if (uc < 26)
   {
      return 'A'+uc;
   }
   if (uc < 52)
   {
      return 'a'+(uc-26);
   }
   if (uc < 62)
   {
      return '0'+(uc-52);
   }
   if (uc == 62)
   {
      return '+';
   }
   return '/';
}
inline unsigned char Base64::Decode(char c)
{
   if (c >= 'A' && c <= 'Z')
   {
      return c - 'A';
   }
   if (c >= 'a' && c <= 'z')
   {
      return c - 'a' + 26;
   }
   if (c >= '0' && c <= '9')
   {
      return c - '0' + 52;
   }
   if (c == '+')
   {
      return 62;
   }
   return 63;
}
inline bool Base64::IsBase64(char c)
{
   if (c >= 'A' && c <= 'Z')
   {
      return true;
   }
   if (c >= 'a' && c <= 'z')
   {
      return true;
   }
   if (c >= '0' && c <= '9')
   {
      return true;
   }
   if (c == '+')
   {
      return true;
   }
   if (c == '/')
   {
      return true;
   }
   if (c == '=')
   {
      return true;
   }
   return false;
}

}}} // namespace

#endif // _UTIL_BASE64_H

