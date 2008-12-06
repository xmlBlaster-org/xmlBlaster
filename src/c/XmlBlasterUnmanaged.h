/*----------------------------------------------------------------------------
Name:      XmlBlasterUnmanaged.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Provides simplified access methods to xmlBlaster client C library
           to be usable as .net unmanaged DLL (called by C#) on e.g. Windows XP
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      07/2006
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.html
-----------------------------------------------------------------------------*/
#ifndef _XmlBlasterUnmanaged_H
#define _XmlBlasterUnmanaged_H

/*
 To access this .dll as unmanaged code from C#, the C-API must be simplified,
 for example fixed size arrays like "char errorCode[256]" are tricky.
 We implement here a simple wrapper around XmlBlasterAccessUnparsed.h
 This code is called from xmlBlaster/src/csharp/NativeC.cs
 See: http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.csharp.html
 */


#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP /* 'g++ -DXMLBLASTER_C_COMPILE_AS_CPP ...' allows to compile the lib as C++ code */
extern "C" {
#endif
#endif

#include <XmlBlasterAccessUnparsed.h>

#ifndef WINCE

/**
 * Usage without fixed array size, to avoid 'unsafe' code in C#
 */
typedef struct XmlBlasterUnmanagedException {
   uint32_t remote;
   char *errorCode;
   char *message;
} XmlBlasterUnmanagedException;

/**
 * Helper struct to pass an array of strings back to C#
 */
typedef struct XmlBlasterUnmanagedStringArr {
   const char *str; 
} XmlBlasterUnmanagedStringArr;


typedef const char * (*XmlBlasterUnmanagedUpdateFp)(const char *cbSessionId, const char *key, char *contentStr, int32_t contentLen, const char *qos, XmlBlasterUnmanagedException *xmlBlasterException);

Dll_Export extern XmlBlasterAccessUnparsed *getXmlBlasterAccessUnparsedUnmanaged(int argc, const char* const* argv);
Dll_Export void freeXmlBlasterAccessUnparsedUnmanaged(XmlBlasterAccessUnparsed *xmlBlasterAccess);

Dll_Export extern  char *xmlBlasterUnmanagedConnect(struct XmlBlasterAccessUnparsed *xa, const char * const qos, XmlBlasterUnmanagedUpdateFp update, XmlBlasterUnmanagedException *exception);
Dll_Export extern  bool  xmlBlasterUnmanagedInitialize(struct XmlBlasterAccessUnparsed *xa, XmlBlasterUnmanagedUpdateFp update, XmlBlasterUnmanagedException *exception);
Dll_Export extern  bool  xmlBlasterUnmanagedDisconnect(struct XmlBlasterAccessUnparsed *xa, const char * qos, XmlBlasterUnmanagedException *exception);
Dll_Export extern  char *xmlBlasterUnmanagedPublish(struct XmlBlasterAccessUnparsed *xa, MsgUnit *msgUnit, XmlBlasterUnmanagedException *exception);
Dll_Export extern  QosArr *xmlBlasterUnmanagedPublishArr(struct XmlBlasterAccessUnparsed *xa, MsgUnitArr *msgUnitArr, XmlBlasterUnmanagedException *exception);
Dll_Export extern void xmlBlasterUnmanagedPublishOneway(struct XmlBlasterAccessUnparsed *xa, MsgUnit *msgUnitArr, int length, XmlBlasterUnmanagedException *exception);
Dll_Export extern  char *xmlBlasterUnmanagedSubscribe(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception);
Dll_Export extern void xmlBlasterUnmanagedUnSubscribe(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception, uint32_t* pSize, XmlBlasterUnmanagedStringArr** ppStruct);
Dll_Export extern void xmlBlasterUnmanagedErase(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception, uint32_t* pSize, XmlBlasterUnmanagedStringArr** ppStruct);
Dll_Export extern  void xmlBlasterUnmanagedGet(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception, uint32_t* pSize, MsgUnit** ppStruct);
Dll_Export extern  char *xmlBlasterUnmanagedPing(struct XmlBlasterAccessUnparsed *xa, const char * const qos, XmlBlasterUnmanagedException *exception);
Dll_Export extern  bool xmlBlasterUnmanagedIsConnected(struct XmlBlasterAccessUnparsed *xa);
Dll_Export extern const char *xmlBlasterUnmanagedUsage(void);


#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif

#endif /*!defined(WINCE)*/
#endif /* _XmlBlasterUnmanaged_H */

