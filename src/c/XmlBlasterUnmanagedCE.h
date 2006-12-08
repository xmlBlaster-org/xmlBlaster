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

/*
         public IntPtr key;
         public int contentLen;
         public IntPtr content;
         public IntPtr qos;
         public IntPtr responseQos;
typedef struct MsgUnitUnmanaged {
   char *key;
   uint32_t contentLen;
   char *content;
   char *qos;
   char *responseQos;
} MsgUnitUnmanaged;
*/
typedef MsgUnit MsgUnitUnmanaged;

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
Dll_Export extern void sayHelloEx(XmlBlasterUnmanagedException *xmlBlasterException);
Dll_Export extern char *sayHelloRet();


Dll_Export extern char *xmlBlasterUnmanagedCEMalloc(int32_t size);
Dll_Export extern void xmlBlasterUnmanagedCEFree(char *p);
Dll_Export extern void xmlBlasterUnmanagedCEFreePP(char **pp);
Dll_Export extern void xmlBlasterUnmanagedCEExceptionFree(XmlBlasterUnmanagedException *exception);

typedef const char * (*XmlBlasterUnmanagedUpdateFp)(const char *cbSessionId, MsgUnit *msgUnit, XmlBlasterUnmanagedException *xmlBlasterException);

Dll_Export extern XmlBlasterAccessUnparsed *getXmlBlasterAccessUnparsedUnmanaged(int argc, char** argv);
Dll_Export void freeXmlBlasterAccessUnparsedUnmanaged(XmlBlasterAccessUnparsed *xmlBlasterAccess);

Dll_Export extern  char *xmlBlasterUnmanagedCEConnect(struct XmlBlasterAccessUnparsed *xa, char *qos, XmlBlasterUnmanagedUpdateFp update, XmlBlasterUnmanagedException *exception);
Dll_Export extern  bool  xmlBlasterUnmanagedCEInitialize(struct XmlBlasterAccessUnparsed *xa, XmlBlasterUnmanagedUpdateFp update, XmlBlasterUnmanagedException *exception);
Dll_Export extern  bool  xmlBlasterUnmanagedCEDisconnect(struct XmlBlasterAccessUnparsed *xa, char *qos, XmlBlasterUnmanagedException *exception);
Dll_Export extern  char *xmlBlasterUnmanagedCEPublish(struct XmlBlasterAccessUnparsed *xa, MsgUnitUnmanaged *msgUnit, XmlBlasterUnmanagedException *exception);
Dll_Export extern  QosArr *xmlBlasterUnmanagedCEPublishArr(struct XmlBlasterAccessUnparsed *xa, MsgUnitArr *msgUnitArr, XmlBlasterUnmanagedException *exception);
Dll_Export extern void xmlBlasterUnmanagedCEPublishOneway(struct XmlBlasterAccessUnparsed *xa, MsgUnit *msgUnitArr, int length, XmlBlasterUnmanagedException *exception);
Dll_Export extern  char *xmlBlasterUnmanagedCESubscribe(struct XmlBlasterAccessUnparsed *xa, char *key, char *qos, XmlBlasterUnmanagedException *exception);
Dll_Export extern void xmlBlasterUnmanagedCEUnSubscribe(struct XmlBlasterAccessUnparsed *xa, char * key, char * qos, XmlBlasterUnmanagedException *exception, uint32_t* pSize, XmlBlasterUnmanagedStringArr** ppStruct);
Dll_Export extern void xmlBlasterUnmanagedCEErase(struct XmlBlasterAccessUnparsed *xa, char * key, char * qos, XmlBlasterUnmanagedException *exception, uint32_t* pSize, XmlBlasterUnmanagedStringArr** ppStruct);
Dll_Export extern  void xmlBlasterUnmanagedCEGet(struct XmlBlasterAccessUnparsed *xa, char * key, char *qos, XmlBlasterUnmanagedException *exception, uint32_t* pSize, MsgUnit** ppStruct);
Dll_Export extern  char *xmlBlasterUnmanagedCEPing(struct XmlBlasterAccessUnparsed *xa, char * qos, XmlBlasterUnmanagedException *exception);
Dll_Export extern  bool xmlBlasterUnmanagedCEIsConnected(struct XmlBlasterAccessUnparsed *xa);
Dll_Export extern const char *xmlBlasterUnmanagedCEUsage();

#ifdef __cplusplus
}      /* extern "C" */
#endif
#endif /* defined(WINCE) */
#endif /* _XmlBlasterUnmanagedCE_H */

