/*--------------------------------------------------------------------------
Name:      PropertyDef.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data, defines operating system specific stuff.
---------------------------------------------------------------------------*/

#ifndef _UTIL_PROPERTYDEF_H
#define _UTIL_PROPERTYDEF_H

#ifdef _WIN32
#  define FILE_SEP "\\"
#  define PATH_SEP ";"
#else
#  define FILE_SEP "/"
#  define PATH_SEP ":"
#endif

#endif
