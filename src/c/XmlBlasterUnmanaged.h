/*----------------------------------------------------------------------------
Name:      XmlBlasterUnmanaged.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Provides simplified access methods to xmlBlaster client C library
           to be usable as .net unmanaged DLL (called by C#)
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
 */


#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP /* 'g++ -DXMLBLASTER_C_COMPILE_AS_CPP ...' allows to compile the lib as C++ code */
extern "C" {
#endif
#endif

#include <XmlBlasterAccessUnparsed.h>

/**
 * Usage without fixed array size, to avoid 'unsafe' code in C#
 */
typedef struct XmlBlasterUnmanagedException {
   uint32_t remote;
   char *errorCode;
   char *message;
} XmlBlasterUnmanagedException;

/*typedef XMLBLASTER_C_bool (*XmlBlasterUnmanagedUpdateFp)(MsgUnitArr *msg, void *userData, XmlBlasterException *xmlBlasterException);*/
typedef const char * (*XmlBlasterUnmanagedUpdateFp)(const char *cbSessionId, MsgUnit *msgUnit, XmlBlasterUnmanagedException *xmlBlasterException);

Dll_Export extern  char *xmlBlasterUnmanagedConnect(struct XmlBlasterAccessUnparsed *xa, const char * const qos, XmlBlasterUnmanagedUpdateFp update, XmlBlasterUnmanagedException *exception);
Dll_Export extern  bool  xmlBlasterUnmanagedInitialize(struct XmlBlasterAccessUnparsed *xa, XmlBlasterUnmanagedUpdateFp update, XmlBlasterUnmanagedException *exception);
/*
Dll_Export extern  char *xmlBlasterUnmanagedConnect(struct XmlBlasterAccessUnparsed *xa, const char * const qos, UpdateFp update, XmlBlasterUnmanagedException *exception);
Dll_Export extern  bool  xmlBlasterUnmanagedInitialize(struct XmlBlasterAccessUnparsed *xa, UpdateFp update, XmlBlasterUnmanagedException *exception);
*/
Dll_Export extern  bool  xmlBlasterUnmanagedDisconnect(struct XmlBlasterAccessUnparsed *xa, const char * qos, XmlBlasterUnmanagedException *exception);
Dll_Export extern  char *xmlBlasterUnmanagedPublish(struct XmlBlasterAccessUnparsed *xa, MsgUnit *msgUnit, XmlBlasterUnmanagedException *exception);
Dll_Export extern  QosArr *xmlBlasterUnmanagedPublishArr(struct XmlBlasterAccessUnparsed *xa, MsgUnitArr *msgUnitArr, XmlBlasterUnmanagedException *exception);
/*Dll_Export extern  void  xmlBlasterUnmanagedPublishOneway(struct XmlBlasterAccessUnparsed *xa, MsgUnitArr *msgUnitArr, XmlBlasterUnmanagedException *exception);*/
Dll_Export extern void xmlBlasterUnmanagedPublishOneway(struct XmlBlasterAccessUnparsed *xa, MsgUnit *msgUnitArr, int length, XmlBlasterUnmanagedException *exception);
Dll_Export extern  char *xmlBlasterUnmanagedSubscribe(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception);
Dll_Export extern  QosArr *xmlBlasterUnmanagedUnSubscribe(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception);
Dll_Export extern  QosArr *xmlBlasterUnmanagedErase(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception);
Dll_Export extern  MsgUnitArr *xmlBlasterUnmanagedGet(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception);
Dll_Export extern  char *xmlBlasterUnmanagedPing(struct XmlBlasterAccessUnparsed *xa, const char * const qos, XmlBlasterUnmanagedException *exception);
Dll_Export extern  bool xmlBlasterUnmanagedIsConnected(struct XmlBlasterAccessUnparsed *xa);


typedef struct Test1 {
   char secretSessionId[256];
} Test1;
Dll_Export extern  void setTest1(Test1 *test1);
Dll_Export extern Test1 getTest1();

typedef struct Test2 {
   int len;
   char **p;
} Test2;
Dll_Export extern Test2 getTest2();
Dll_Export int getTest3(MsgUnit** pList);
Dll_Export int MinArray(MsgUnit* pData, int length);


typedef struct MsgUnitUnmanagedArr {
   char *secretSessionId;
   int len;
   MsgUnit *msgUnitArr;
} MsgUnitUnmanagedArr;

Dll_Export extern int setMsgUnitArr(MsgUnitUnmanagedArr pData);


#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif

#endif /* _XmlBlasterUnmanaged_H */

