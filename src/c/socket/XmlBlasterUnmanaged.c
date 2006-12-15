/*----------------------------------------------------------------------------
Name:      XmlBlasterUnmanaged.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Simple access layer to be used from .net or Mono
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <string.h> /* memset */
#include <stdio.h> /* printf */
#include <XmlBlasterUnmanaged.h>

#ifndef WINCE

/*
 To access this .dll as unmanaged code, the C-API must be simplified,
 for example fixed size arrays like "char errorCode[256]" are tricky.
 We implement here a simple wrapper around XmlBlasterAccessUnparsed.h
 TODO: How to pass byte[] to C and back to C#
 */

static void convert(XmlBlasterException *in, XmlBlasterUnmanagedException *out) {
   out->errorCode = strcpyAlloc(in->errorCode);
   out->message = strcpyAlloc(in->message);
   out->remote = in->remote;
}

/**
 * We intercept the callbacks here and convert it to a more simple form to
 * be easy transferable to C# (Csharp). 
 */
static XMLBLASTER_C_bool interceptUpdate(MsgUnitArr *msgUnitArr, void *userData, XmlBlasterException *exception) {
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
      char *cbSessionId = strcpyAlloc(msgUnitArr->secretSessionId);
      const char *ret = 0;
      /*
      char *xml = messageUnitToXml(&msgUnitArr->msgUnitArr[i]);
      printf("[client] CALLBACK update(): Asynchronous message update arrived:%s\n",
             xml);
      xmlBlasterFree(xml);
      
      printf("XmlBlasterUnmanaged.c: before update() %d\n", (int)msgUnitArr->len);
      */
      
      /* Call C# ... TODO: how to pass a byte[], the Marshal does not know the contentLen */
      {
      char * contentStr = 
         strFromBlobAlloc(msgUnitArr->msgUnitArr[i].content, msgUnitArr->msgUnitArr[i].contentLen);
      ret = unmanagedUpdate(cbSessionId, msgUnitArr->msgUnitArr[i].key, contentStr,
         (int32_t)msgUnitArr->msgUnitArr[i].contentLen, msgUnitArr->msgUnitArr[i].qos, &unmanagedException);
      xmlBlasterFree(contentStr);
      xmlBlasterFree(cbSessionId);
      }

      if (unmanagedException.errorCode != 0) {
         /* catch first exception set and return */
         strncpy0(exception->errorCode, unmanagedException.errorCode, EXCEPTIONSTRUCT_ERRORCODE_LEN);
         if (unmanagedException.message != 0)
            strncpy0(exception->message, unmanagedException.message, EXCEPTIONSTRUCT_MESSAGE_LEN);
         exception->remote = unmanagedException.remote;
      }
   
      msgUnitArr->msgUnitArr[i].responseQos = strcpyAlloc(ret);
      printf("XmlBlasterUnmanaged.c: update() done with %s\n", ret);
      /* Return QoS: Everything is OK */
   }
   printf("XmlBlasterUnmanaged.c: update() done\n");
   
   return true;
}

Dll_Export XmlBlasterAccessUnparsed *getXmlBlasterAccessUnparsedUnmanaged(int argc, const char* const* argv){
   /** argv seems to be freed by C#, so we clone it here */
   int i=0;
   const char ** ptr = (const char **)malloc(argc*sizeof(char *));
   for (i=0; i<argc; ++i) {
      ptr[i] = strcpyAlloc(argv[i]);
   }
   return getXmlBlasterAccessUnparsed(argc, ptr);
}

Dll_Export void freeXmlBlasterAccessUnparsedUnmanaged(XmlBlasterAccessUnparsed *xmlBlasterAccess) {
   if (xmlBlasterAccess != 0) {
      int i;
      for (i=0; i<xmlBlasterAccess->argc; ++i)
         free((void*)xmlBlasterAccess->argv[i]);
      free((void*)xmlBlasterAccess->argv);
      freeXmlBlasterAccessUnparsed(xmlBlasterAccess);
   }
}

Dll_Export bool xmlBlasterUnmanagedInitialize(struct XmlBlasterAccessUnparsed *xa, XmlBlasterUnmanagedUpdateFp update, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   bool ret = false;
   xa->userFp = (XmlBlasterAccessGenericFp)update;
   ret = xa->initialize(xa, interceptUpdate, &e);
   convert(&e, exception);
   return ret; 
}

Dll_Export char *xmlBlasterUnmanagedConnect(struct XmlBlasterAccessUnparsed *xa, const char * const qos, XmlBlasterUnmanagedUpdateFp update, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   char *ret = 0;
   if (update != 0)
      xa->userFp = (XmlBlasterAccessGenericFp)update;
   ret = xa->connect(xa, qos, interceptUpdate, &e);
   convert(&e, exception);
   return ret; 
}

Dll_Export extern bool xmlBlasterUnmanagedDisconnect(struct XmlBlasterAccessUnparsed *xa, const char * qos, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   bool ret = xa->disconnect(xa, qos, &e);
   convert(&e, exception);
   return ret; 
}

Dll_Export extern char *xmlBlasterUnmanagedPublish(struct XmlBlasterAccessUnparsed *xa, MsgUnit *msgUnit, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   char *ret = xa->publish(xa, msgUnit, &e);
   convert(&e, exception);
   return ret; 
}

Dll_Export extern QosArr *xmlBlasterUnmanagedPublishArr(struct XmlBlasterAccessUnparsed *xa, MsgUnitArr *msgUnitArr, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   QosArr *ret = xa->publishArr(xa, msgUnitArr, &e);
   convert(&e, exception);
   return ret; 
}

Dll_Export extern void xmlBlasterUnmanagedPublishOneway(struct XmlBlasterAccessUnparsed *xa, MsgUnit *msgUnitArr, int length, XmlBlasterUnmanagedException *exception) {
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

Dll_Export extern char *xmlBlasterUnmanagedSubscribe(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   char *ret = xa->subscribe(xa, key, qos, &e);
   convert(&e, exception);
   return ret; 
}

Dll_Export void xmlBlasterUnmanagedUnSubscribe(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception, uint32_t* pSize, XmlBlasterUnmanagedStringArr** ppStruct) {
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
      for (i=0; i<ret->len; i++, pCurStruct++) {
         /*printf("C: Unsubscribe success, returned status is '%s'\n", ret->qosArr[i]);*/
         pCurStruct->str = strcpyAlloc(ret->qosArr[i]);
      }
      freeQosArr(ret);
   }
}

Dll_Export void xmlBlasterUnmanagedErase(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception, uint32_t* pSize, XmlBlasterUnmanagedStringArr** ppStruct) {
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
      for (i=0; i<ret->len; i++, pCurStruct++) {
         pCurStruct->str = strcpyAlloc(ret->qosArr[i]);
      }
      freeQosArr(ret);
   }
}

Dll_Export void xmlBlasterUnmanagedGet(struct XmlBlasterAccessUnparsed *xa,
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

Dll_Export char *xmlBlasterUnmanagedPing(struct XmlBlasterAccessUnparsed *xa, const char * const qos, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   char *ret = xa->ping(xa, qos, &e);
   convert(&e, exception);
   return ret; 
}

Dll_Export bool xmlBlasterUnmanagedIsConnected(struct XmlBlasterAccessUnparsed *xa) {
   return xa->isConnected(xa);
}

Dll_Export const char *xmlBlasterUnmanagedUsage() {
   char *usage = (char *)malloc(XMLBLASTER_MAX_USAGE_LEN*sizeof(char));
   return xmlBlasterAccessUnparsedUsage(usage);
}

#endif /*!defined(WINCE)*/
