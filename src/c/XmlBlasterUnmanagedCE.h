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
typedef struct XmlBlasterUnmanagedCEException {
   uint32_t remote;
   char *errorCode;
   char *message;
} XmlBlasterUnmanagedCEException;

/*
         public IntPtr key;
         public int contentLen;
         public IntPtr content;
         public IntPtr qos;
         public IntPtr responseQos;
typedef struct MsgUnitUnmanagedCEget {
   char *key;
   uint32_t contentLen;
   char *content;
   char *qos;
   char *responseQos;
} MsgUnitUnmanagedCEget;
*/
/* this only works if the size_t has 4 bytes (from MsgUnit) */
typedef MsgUnit MsgUnitUnmanagedCEpublish;
typedef MsgUnit MsgUnitUnmanagedCEget;


/**
 * Helper struct to pass an array of strings back to C#
 */
typedef struct XmlBlasterUnmanagedCEStringArr {
   const char *str; 
} XmlBlasterUnmanagedCEStringArr;


Dll_Export extern char *xmlBlasterUnmanagedCEMalloc(int32_t size);
Dll_Export extern void xmlBlasterUnmanagedCEFree(char *p);
Dll_Export extern void xmlBlasterUnmanagedCEFreePP(char **pp);
Dll_Export extern void xmlBlasterUnmanagedCEExceptionFree(XmlBlasterUnmanagedCEException *exception);

/*
#define CALLBACK    __stdcall
#define WINAPI      __stdcall
#define WINAPIV     __cdecl
*/
#if defined(_WINDOWS)
#  define XB_CALLBACK_DECL __cdecl
# else/* XMLBLASTER_CLIENT_MONO */
#  define XB_CALLBACK_DECL
#endif
typedef void (XB_CALLBACK_DECL *XmlBlasterUnmanagedCELoggerFp)(int32_t level, const char *location, const char *msg);
typedef void (XB_CALLBACK_DECL *XmlBlasterUnmanagedCEUpdateFp)(const char *cbSessionId, MsgUnit *msgUnit, XmlBlasterUnmanagedCEException *xmlBlasterException);
/*typedef int32_t (__cdecl *FPTR)( int32_t i );*/
typedef int32_t (XB_CALLBACK_DECL *FPTR)( int32_t i );

Dll_Export extern void xmlBlasterUnmanagedCERegisterLogger(struct XmlBlasterAccessUnparsed *xa, XmlBlasterUnmanagedCELoggerFp logger);

Dll_Export extern XmlBlasterAccessUnparsed *getXmlBlasterAccessUnparsedUnmanagedCE(int argc, char** argv);
Dll_Export extern void freeXmlBlasterAccessUnparsedUnmanagedCE(XmlBlasterAccessUnparsed *xmlBlasterAccess);

Dll_Export extern char *xmlBlasterUnmanagedCEConnect(struct XmlBlasterAccessUnparsed *xa, char *qos, XmlBlasterUnmanagedCEUpdateFp update, XmlBlasterUnmanagedCEException *exception);
Dll_Export extern  bool  xmlBlasterUnmanagedCEInitialize(struct XmlBlasterAccessUnparsed *xa, XmlBlasterUnmanagedCEUpdateFp update, XmlBlasterUnmanagedCEException *exception);
Dll_Export extern  bool  xmlBlasterUnmanagedCEDisconnect(struct XmlBlasterAccessUnparsed *xa, char *qos, XmlBlasterUnmanagedCEException *exception);
Dll_Export extern  char *xmlBlasterUnmanagedCEPublish(struct XmlBlasterAccessUnparsed *xa, MsgUnitUnmanagedCEpublish *msgUnit, XmlBlasterUnmanagedCEException *exception);
Dll_Export extern  QosArr *xmlBlasterUnmanagedCEPublishArr(struct XmlBlasterAccessUnparsed *xa, MsgUnitArr *msgUnitArr, XmlBlasterUnmanagedCEException *exception);
Dll_Export extern void xmlBlasterUnmanagedCEPublishOneway(struct XmlBlasterAccessUnparsed *xa, MsgUnit *msgUnitArr, int length, XmlBlasterUnmanagedCEException *exception);
Dll_Export extern  char *xmlBlasterUnmanagedCESubscribe(struct XmlBlasterAccessUnparsed *xa, char *key, char *qos, XmlBlasterUnmanagedCEException *exception);
Dll_Export extern void xmlBlasterUnmanagedCEUnSubscribe(struct XmlBlasterAccessUnparsed *xa, char * key, char * qos, XmlBlasterUnmanagedCEException *exception, uint32_t* pSize, XmlBlasterUnmanagedCEStringArr** ppStruct);
Dll_Export extern void xmlBlasterUnmanagedCEErase(struct XmlBlasterAccessUnparsed *xa, char * key, char * qos, XmlBlasterUnmanagedCEException *exception, uint32_t* pSize, XmlBlasterUnmanagedCEStringArr** ppStruct);
Dll_Export extern  void xmlBlasterUnmanagedCEGet(struct XmlBlasterAccessUnparsed *xa, char * key, char *qos, XmlBlasterUnmanagedCEException *exception, uint32_t* pSize, MsgUnitUnmanagedCEget** ppStruct);
Dll_Export extern  char *xmlBlasterUnmanagedCEPing(struct XmlBlasterAccessUnparsed *xa, char * qos, XmlBlasterUnmanagedCEException *exception);
Dll_Export extern  bool xmlBlasterUnmanagedCEIsConnected(struct XmlBlasterAccessUnparsed *xa);
Dll_Export extern const char *xmlBlasterUnmanagedCEVersion();
Dll_Export extern const char *xmlBlasterUnmanagedCEUsage();

#ifdef __cplusplus
}      /* extern "C" */
#endif
#endif /* _XmlBlasterUnmanagedCE_H */

