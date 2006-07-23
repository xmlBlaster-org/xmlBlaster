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

/*
 To access this .dll as unmanaged code, the C-API must be simplified,
 for example fixed size arrays like "char errorCode[256]" are tricky.
 We implement here a simple wrapper around XmlBlasterAccessUnparsed.h
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
static bool interceptUpdate(MsgUnitArr *msgUnitArr, void *userData, XmlBlasterException *exception) {
   size_t i;
   XmlBlasterUnmanagedException unmanagedException;
   
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)userData;
   XmlBlasterUnmanagedUpdateFp unmanagedUpdate = (XmlBlasterUnmanagedUpdateFp)(xa->userFp);
   
   if (userData != 0) ;  /* Supress compiler warning */
   if (unmanagedUpdate == 0) return false;
   
   memset(&unmanagedException, 0, sizeof(struct XmlBlasterUnmanagedException));

   for (i=0; i<msgUnitArr->len; i++) {
      const char *cbSessionId = strcpyAlloc(msgUnitArr->secretSessionId);
      const char *ret = 0;
      
      /*printf("XmlBlasterUnmanaged.c: before update() %d\n", (int)msgUnitArr->len);*/
      
      /* Call C# ... */
      ret = unmanagedUpdate(cbSessionId, &msgUnitArr->msgUnitArr[i], &unmanagedException);
      /*printf("XmlBlasterUnmanaged.c: after update() %s\n", ret);*/

      if (unmanagedException.errorCode != 0) {
         /* catch first exception set and return */
         strncpy0(exception->errorCode, unmanagedException.errorCode, EXCEPTIONSTRUCT_ERRORCODE_LEN);
         if (unmanagedException.message != 0)
            strncpy0(exception->message, unmanagedException.message, EXCEPTIONSTRUCT_MESSAGE_LEN);
         exception->remote = unmanagedException.remote;
      }
   
      msgUnitArr->msgUnitArr[i].responseQos = strcpyAlloc(ret);
      /* Return QoS: Everything is OK */
   }
   
   return true;
}

Dll_Export extern bool xmlBlasterUnmanagedInitialize(struct XmlBlasterAccessUnparsed *xa, XmlBlasterUnmanagedUpdateFp update, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   bool ret = false;
   xa->userFp = (XmlBlasterAccessGenericFp)update;
   ret = xa->initialize(xa, interceptUpdate, &e);
   convert(&e, exception);
   return ret; 
}

Dll_Export extern  char *xmlBlasterUnmanagedConnect(struct XmlBlasterAccessUnparsed *xa, const char * const qos, XmlBlasterUnmanagedUpdateFp update, XmlBlasterUnmanagedException *exception) {
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

Dll_Export extern void xmlBlasterUnmanagedPublishOneway(struct XmlBlasterAccessUnparsed *xa, MsgUnitArr *msgUnitArr, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   xa->publishOneway(xa, msgUnitArr, &e);
   convert(&e, exception);
}

Dll_Export extern char *xmlBlasterUnmanagedSubscribe(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   char *ret = xa->subscribe(xa, key, qos, &e);
   convert(&e, exception);
   return ret; 
}

Dll_Export extern QosArr *xmlBlasterUnmanagedUnSubscribe(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   QosArr *ret = xa->unSubscribe(xa, key, qos, &e);
   convert(&e, exception);
   return ret; 
}

Dll_Export extern QosArr *xmlBlasterUnmanagedErase(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   QosArr *ret = xa->erase(xa, key, qos, &e);
   convert(&e, exception);
   return ret; 
}

Dll_Export extern MsgUnitArr *xmlBlasterUnmanagedGet(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   MsgUnitArr *ret = xa->get(xa, key, qos, &e);
   convert(&e, exception);
   return ret; 
}

Dll_Export extern char *xmlBlasterUnmanagedPing(struct XmlBlasterAccessUnparsed *xa, const char * const qos, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   char *ret = xa->ping(xa, qos, &e);
   convert(&e, exception);
   return ret; 
}

Dll_Export extern bool xmlBlasterUnmanagedIsConnected(struct XmlBlasterAccessUnparsed *xa) {
   return xa->isConnected(xa);
}

