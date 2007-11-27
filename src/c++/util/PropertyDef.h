/*--------------------------------------------------------------------------
Name:      PropertyDef.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data, defines operating system specific stuff.
---------------------------------------------------------------------------*/

#ifndef _UTIL_PROPERTYDEF_H
#define _UTIL_PROPERTYDEF_H

// _WIN32 is deprecated, use _WINDOWS
#if defined(_WIN32) || defined(_WINDOWS)
#  define FILE_SEP "\\"
#  define PATH_SEP ";"
#else
#  define FILE_SEP "/"
#  define PATH_SEP ":"
#  define _TERM_WITH_COLORS_ 1 // UNIX xterm can display colors (escape sequences) for nicer logging output
#endif

#endif
