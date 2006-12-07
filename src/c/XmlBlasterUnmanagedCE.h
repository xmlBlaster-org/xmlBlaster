/*----------------------------------------------------------------------------
Name:      XmlBlasterUnmanagedCE.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Provides simplified access methods to xmlBlaster client C library
           to be usable from Windows CE .NET Compact Framework 1.0 (CF)
           as a unmanaged DLL (called by C#)
Note:      A managed C++ wrapper is not supported in CF
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      2006-12-07
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.html
See:       P/Invoke for CE: http://msdn2.microsoft.com/en-us/library/Aa446536.aspx
-----------------------------------------------------------------------------*/
#ifndef _XmlBlasterUnmanagedCE_H
#define _XmlBlasterUnmanagedCE_H

#if defined(WINCE)

/*
 To access this .dll as unmanaged code from C#, the C-API must be simplified,
 for example fixed size arrays like "char errorCode[256]" are tricky.
 We implement here a simple wrapper around XmlBlasterAccessUnparsed.h
 This code is called from xmlBlaster/src/csharp/PInvokeCE.cs
 See: http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.csharp.html
 
 We should avoid C++ code as the name mangling makes difficulties,
 so we force C code here.
 See: http://www.opennetcf.org/Forums/topic.asp?TOPIC_ID=255
 */

#include <XmlBlasterAccessUnparsed.h>

#ifdef __cplusplus
extern "C" {
#endif

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


/**
 * Simple function for testing
 */
Dll_Export extern void sayHello();
/* typedef CONST WCHAR *LPCWSTR, *PCWSTR;*/
Dll_Export extern void sayHelloLPCT(LPCTSTR p);
Dll_Export extern void sayHelloP(int32_t size, const char *p);
Dll_Export extern void sayHelloArr(const char* const* argv);
Dll_Export extern void sayHelloEx(XmlBlasterUnmanagedException *xmlBlasterException);
Dll_Export extern char *sayHelloRet();


Dll_Export extern char *xmlBlasterUnmanagedMalloc(int32_t size);
Dll_Export extern void xmlBlasterUnmanagedFree(char *p);
Dll_Export extern void xmlBlasterUnmanagedExceptionFree(XmlBlasterUnmanagedException *exception);

typedef const char * (*XmlBlasterUnmanagedUpdateFp)(const char *cbSessionId, MsgUnit *msgUnit, XmlBlasterUnmanagedException *xmlBlasterException);

Dll_Export extern XmlBlasterAccessUnparsed *getXmlBlasterAccessUnparsedUnmanaged(int argc, const char** argv);
//Dll_Export extern XmlBlasterAccessUnparsed *getXmlBlasterAccessUnparsedUnmanaged(int argc, const char* const* argv);
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
Dll_Export extern const char *xmlBlasterUnmanagedUsage();

#ifdef __cplusplus
}      /* extern "C" */
#endif
#endif /* defined(WINCE) */
#endif /* _XmlBlasterUnmanagedCE_H */

