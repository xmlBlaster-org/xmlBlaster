/*------------------------------------------------------------------------------
Name:      xmlBlaster/src/c++/util/XmlBCfg.h
Project:   xmlBlaster.org, C++ client library
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   General xmlBlaster include file to handle OS specific behavior
Author:    Martin Johnson
------------------------------------------------------------------------------*/
#if defined(_WINDOWS)
# define Blaster_Export_Flag __declspec (dllexport)
# define Blaster_Import_Flag __declspec (dllimport)
#if defined(DLL_BUILD)
#   define Dll_Export Blaster_Export_Flag
# else
#   define Dll_Export Blaster_Import_Flag
#endif
#else
# define Dll_Export
#endif
