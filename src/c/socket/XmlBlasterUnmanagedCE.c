/*----------------------------------------------------------------------------
Name:      XmlBlasterUnmanagedCE.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   See XmlBlasterUnmanagedCE.h
           Access C dll from C# on Windows CE with P/Invoke
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <string.h> /* memset */
#include <stdio.h>  /* printf */
#include <XmlBlasterUnmanagedCE.h>
//#include <locale.h> /* setlocal() */

#if defined(WINCE)

#ifdef __cplusplus
#	define XBFORCE_EXTERNC extern "C"
#else
#	define XBFORCE_EXTERNC
#endif

/*
 To access this .dll as unmanaged code, the C-API must be simplified,
 for example fixed size arrays like "char errorCode[256]" are tricky.
 We implement here a simple wrapper around XmlBlasterAccessUnparsed.h
 TODO: How to pass byte[] to C and back to C#
  
 We should avoid C++ code as the name mangling makes difficulties,
 so we force C code here.
 See: http://www.opennetcf.org/Forums/topic.asp?TOPIC_ID=255
*/

/**
 * Simple function for testing
 */
XBFORCE_EXTERNC extern void sayHello() {
	printf("Hello World from C DLL!\n");
}
Dll_Export extern void sayHelloLPCT(LPCTSTR p) {
	printf("Hello World from C DLL LPCTSTR=%s!\n", p);
}

XBFORCE_EXTERNC extern void sayHelloP(int32_t size, const char *p) {
	char ret[126];
	printf("Hello World from C DLL size=%d '%s', trying now wcs\n", size, p);
    wcstombs(ret, (const wchar_t *)p, 126); 
	printf("Hello World from C DLL P=%s!\n", p);
}
XBFORCE_EXTERNC extern void sayHelloArr(const char* const* argv) {
	printf("Hello World from C DLL arr!\n");
}

XBFORCE_EXTERNC extern void sayHelloEx(XmlBlasterUnmanagedException *exception) {
	printf("Hello World from C DLL sayHelloEx\n");
   exception->errorCode = strcpyAlloc("user.test");
   exception->message = strcpyAlloc("Some text");
   exception->remote = 1;
}
   
XBFORCE_EXTERNC extern char *sayHelloRet() {
   printf("dll: Invoke sayHelloRet()\n");
   //return strcpyAlloc("Hello world allocated \xC3\xB6 in DLL");
   return strcpyAlloc("\xE8\xAA\x9E  German:\xC3\xB6  Korean:\xED\x95\x9C \xEA\xB5\xAD\xEC\x96\xB4  Japanese:\xE6\x97\xA5\xE6\x9C\xAC\xE8\xAA\x9E");
   //return 0;
}

/**
 * malloc size bytes
 * @param size > 0
 * @return p the allocated pointer
 */
XBFORCE_EXTERNC extern char *xmlBlasterUnmanagedMalloc(int32_t size) {
   /*printf("dll: xmlBlasterUnmanagedMalloc('%d')\n", size);*/
   if (size > 0)
      return (char *)malloc(size*sizeof(char));
   return (char *)0;
}

/**
 * Frees the malloced pointer
 * @param p Can be null
 */
XBFORCE_EXTERNC extern void xmlBlasterUnmanagedFree(char *p)  {
   /*printf("dll: xmlBlasterUnmanagedFree('%s')\n", ((p!=(char *)0)?p:""));*/
   xmlBlasterFree(p);
}

/**
 * Allocates the members of XmlBlasterUnmanagedException
 * You need to free it after usage
 */
XBFORCE_EXTERNC static void convert(XmlBlasterException *in, XmlBlasterUnmanagedException *out) {
   if (*in->errorCode != 0) {
      out->errorCode = strcpyAlloc(in->errorCode);
      out->message = strcpyAlloc(in->message);
      out->remote = in->remote;
   }
   else {
      out->errorCode = 0;
   }   
}

XBFORCE_EXTERNC extern void xmlBlasterUnmanagedExceptionFree(XmlBlasterUnmanagedException *ex) {
   if (ex == 0) return;
   xmlBlasterFree(ex->errorCode);
   ex->errorCode = 0;
   xmlBlasterFree(ex->message);
   ex->message = 0;
   ex->remote = 0;
}


/**
 * We intercept the callbacks here and convert it to a more simple form to
 * be easy transferable to C# (Csharp). 
 */
XBFORCE_EXTERNC static XMLBLASTER_C_bool interceptUpdate(MsgUnitArr *msgUnitArr, void *userData, XmlBlasterException *exception) {
   size_t i;
   XmlBlasterUnmanagedException unmanagedException;
   
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)userData;
   XmlBlasterUnmanagedUpdateFp unmanagedUpdate = (XmlBlasterUnmanagedUpdateFp)(xa->userFp);
   
   if (userData != 0) ;  /* Supress compiler warning */
   if (unmanagedUpdate == 0) return false;
   
   /*memset(&unmanagedException, 0, sizeof(struct XmlBlasterUnmanagedException));*/
   unmanagedException.remote = false;
   unmanagedException.errorCode = (char)0;
   unmanagedException.message = (char)0;
   

   for (i=0; i<msgUnitArr->len; i++) {
      const char *cbSessionId = strcpyAlloc(msgUnitArr->secretSessionId);
      const char *ret = 0;
      /*
      char *xml = messageUnitToXml(&msgUnitArr->msgUnitArr[i]);
      printf("[client] CALLBACK update(): Asynchronous message update arrived:%s\n",
             xml);
      xmlBlasterFree(xml);
      
      printf("XmlBlasterUnmanaged.c: before update() %d\n", (int)msgUnitArr->len);
      */
      
      /* Call C# ..., it may allocate errorCode */
      ret = unmanagedUpdate(cbSessionId, &msgUnitArr->msgUnitArr[i], &unmanagedException);

      if (unmanagedException.errorCode != 0) {
         /* catch first exception set and return */
         strncpy0(exception->errorCode, unmanagedException.errorCode, EXCEPTIONSTRUCT_ERRORCODE_LEN);
         if (unmanagedException.message != 0)
            strncpy0(exception->message, unmanagedException.message, EXCEPTIONSTRUCT_MESSAGE_LEN);
         exception->remote = unmanagedException.remote;
         xmlBlasterUnmanagedExceptionFree(&unmanagedException);
      }
   
      msgUnitArr->msgUnitArr[i].responseQos = strcpyAlloc(ret);
      /* Return QoS: Everything is OK */
   }
   
   return true;
}

/*
 * Don't free argv, we point on it internally
 * @paran argc
 * @param argv [0] contains the exe name, [1] the first argument
 *   "Hello.exe" "-dispatch/connection/plugin/socket/hostname" "192.168.1.2"
 * @return the XmlBlasterAccessUnparsed handle
 */
XBFORCE_EXTERNC Dll_Export XmlBlasterAccessUnparsed *getXmlBlasterAccessUnparsedUnmanaged(int argc, const char** argv){
   /*
   int i=0;
   printf("dll: getXmlBlasterAccessUnparsedUnmanaged('%d')\n", argc);
   for (i=0; i<argc; i++)
      printf("dll: '%s'\n", argv[i]);
   */
   return getXmlBlasterAccessUnparsed(argc, argv);
}
/*
XBFORCE_EXTERNC Dll_Export XmlBlasterAccessUnparsed *getXmlBlasterAccessUnparsedUnmanaged(int argc, const char* const *argv){
   //** argv seems to be freed by C#, so we clone it here *
   int i=0;
   const char ** ptr = (const char **)malloc(argc*sizeof(char *));
   for (i=0; i<argc; ++i) {
      ptr[i] = strcpyAlloc(argv[i]);
   }
   return getXmlBlasterAccessUnparsed(argc, ptr);
}
*/
XBFORCE_EXTERNC Dll_Export void freeXmlBlasterAccessUnparsedUnmanaged(XmlBlasterAccessUnparsed *xmlBlasterAccess) {
   if (xmlBlasterAccess != 0) {
      int i;
      for (i=0; i<xmlBlasterAccess->argc; ++i)
         free((void*)xmlBlasterAccess->argv[i]);
      free((void*)xmlBlasterAccess->argv);
      freeXmlBlasterAccessUnparsed(xmlBlasterAccess);
   }
}

XBFORCE_EXTERNC Dll_Export bool xmlBlasterUnmanagedInitialize(struct XmlBlasterAccessUnparsed *xa, XmlBlasterUnmanagedUpdateFp update, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   bool ret = false;
   xa->userFp = (XmlBlasterAccessGenericFp)update;
   ret = xa->initialize(xa, interceptUpdate, &e);
   convert(&e, exception);
   return ret; 
}

XBFORCE_EXTERNC Dll_Export char *xmlBlasterUnmanagedConnect(struct XmlBlasterAccessUnparsed *xa, const char * const qos, XmlBlasterUnmanagedUpdateFp update, XmlBlasterUnmanagedException *exception) {

printf("Hello World from C DLL xmlBlasterUnmanagedConnect\n");
{
   XmlBlasterException e;
   char *ret = 0;
   if (update != 0)
      xa->userFp = (XmlBlasterAccessGenericFp)update;
   printf("dll: xmlBlasterUnmanagedConnect %s  --- %d\n", qos, xa);
   ret = xa->connect(xa, qos, interceptUpdate, &e);
   convert(&e, exception);
   return ret; 
}

}

XBFORCE_EXTERNC Dll_Export extern bool xmlBlasterUnmanagedDisconnect(struct XmlBlasterAccessUnparsed *xa, const char * qos, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   bool ret = xa->disconnect(xa, qos, &e);
   convert(&e, exception);
   return ret; 
}

XBFORCE_EXTERNC Dll_Export extern char *xmlBlasterUnmanagedPublish(struct XmlBlasterAccessUnparsed *xa, MsgUnit *msgUnit, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   char *ret = xa->publish(xa, msgUnit, &e);
   convert(&e, exception);
   return ret; 
}

XBFORCE_EXTERNC Dll_Export extern QosArr *xmlBlasterUnmanagedPublishArr(struct XmlBlasterAccessUnparsed *xa, MsgUnitArr *msgUnitArr, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   QosArr *ret = xa->publishArr(xa, msgUnitArr, &e);
   convert(&e, exception);
   return ret; 
}

XBFORCE_EXTERNC Dll_Export extern void xmlBlasterUnmanagedPublishOneway(struct XmlBlasterAccessUnparsed *xa, MsgUnit *msgUnitArr, int length, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   MsgUnitArr arr;
   /*printf("C: xmlBlasterUnmanagedPublishOneway %d", length);*/
   arr.isOneway = true;
   arr.len = length;
   arr.msgUnitArr = msgUnitArr;
   *arr.secretSessionId = 0;
   xa->publishOneway(xa, &arr, &e);
   convert(&e, exception);
}

XBFORCE_EXTERNC Dll_Export extern char *xmlBlasterUnmanagedSubscribe(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   char *ret = xa->subscribe(xa, key, qos, &e);
   convert(&e, exception);
   return ret; 
}

XBFORCE_EXTERNC Dll_Export void xmlBlasterUnmanagedUnSubscribe(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception, uint32_t* pSize, XmlBlasterUnmanagedStringArr** ppStruct) {
   XmlBlasterException e;
   QosArr *ret = 0;
   initializeXmlBlasterException(&e);
   ret = xa->unSubscribe(xa, key, qos, &e);
   convert(&e, exception);
   if (*e.errorCode != 0) {
      /*printf("C: Caught exception in unSubscribe errorCode=%s, message=%s\n", e.errorCode, e.message);*/
      if (ret) freeQosArr(ret);
      return;
   }
   if (ret) {
      size_t i;
      XmlBlasterUnmanagedStringArr* pCurStruct = 0;
      const uint32_t cArraySize = ret->len;
      *pSize = cArraySize;
      *ppStruct = (XmlBlasterUnmanagedStringArr*)malloc( cArraySize * sizeof( XmlBlasterUnmanagedStringArr ));
      pCurStruct = *ppStruct;
      for (i=0; i<ret->len; i++) {
         /*printf("C: Unsubscribe success, returned status is '%s'\n", ret->qosArr[i]);*/
         pCurStruct->str = strcpyAlloc(ret->qosArr[i]);
      }
      freeQosArr(ret);
   }
}

XBFORCE_EXTERNC Dll_Export void xmlBlasterUnmanagedErase(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception, uint32_t* pSize, XmlBlasterUnmanagedStringArr** ppStruct) {
   XmlBlasterException e;
   QosArr *ret = 0;
   initializeXmlBlasterException(&e);
   ret = xa->erase(xa, key, qos, &e);
   convert(&e, exception);
   if (*e.errorCode != 0) {
      if (ret) freeQosArr(ret);
      return;
   }
   if (ret) {
      size_t i;
      XmlBlasterUnmanagedStringArr* pCurStruct = 0;
      const uint32_t cArraySize = ret->len;
      *pSize = cArraySize;
      *ppStruct = (XmlBlasterUnmanagedStringArr*)malloc( cArraySize * sizeof( XmlBlasterUnmanagedStringArr ));
      pCurStruct = *ppStruct;
      for (i=0; i<ret->len; i++) {
         pCurStruct->str = strcpyAlloc(ret->qosArr[i]);
      }
      freeQosArr(ret);
   }
}

XBFORCE_EXTERNC Dll_Export void xmlBlasterUnmanagedGet(struct XmlBlasterAccessUnparsed *xa,
        const char * const key, const char * qos, XmlBlasterUnmanagedException *exception,
        uint32_t* pSize, MsgUnit** ppStruct) {
   XmlBlasterException e;
   uint32_t i;
   MsgUnitArr *msgUnitArr = xa->get(xa, key, qos, &e);
   convert(&e, exception);
   if (*e.errorCode != 0) {
      return;
   }
   if (msgUnitArr != (MsgUnitArr *)0) {
      const uint32_t cArraySize = msgUnitArr->len;
      MsgUnit* pCurStruct = 0;
      *pSize = cArraySize;
      *ppStruct = (MsgUnit*)malloc( cArraySize * sizeof( MsgUnit ));
      pCurStruct = *ppStruct;
      /*printf("C: xmlBlasterUnmanagedGet %ud\n", cArraySize);*/
      for( i=0; i < cArraySize; i++, pCurStruct++ ) {
         MsgUnit *msgUnit = &msgUnitArr->msgUnitArr[i];
         /* TODO: pass as byte[] */
         pCurStruct->content = strFromBlobAlloc(msgUnit->content, msgUnit->contentLen);
         pCurStruct->contentLen = msgUnit->contentLen;
         pCurStruct->key = strcpyAlloc(msgUnit->key);
         pCurStruct->qos = strcpyAlloc(msgUnit->qos);
         pCurStruct->responseQos = strcpyAlloc("<qos/>");
      }
      freeMsgUnitArr(msgUnitArr);
   }
}

XBFORCE_EXTERNC Dll_Export char *xmlBlasterUnmanagedPing(struct XmlBlasterAccessUnparsed *xa, const char * const qos, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   char *ret = xa->ping(xa, qos, &e);
   convert(&e, exception);
   return ret; 
}

XBFORCE_EXTERNC Dll_Export bool xmlBlasterUnmanagedIsConnected(struct XmlBlasterAccessUnparsed *xa) {
   return xa->isConnected(xa);
}

XBFORCE_EXTERNC Dll_Export const char *xmlBlasterUnmanagedUsage() {
   char *usage = (char *)malloc(XMLBLASTER_MAX_USAGE_LEN*sizeof(char));
   return xmlBlasterAccessUnparsedUsage(usage);
}

#endif /*defined(WINCE)*/
