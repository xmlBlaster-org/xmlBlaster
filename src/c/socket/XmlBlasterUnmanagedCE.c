/*----------------------------------------------------------------------------
Name:      XmlBlasterUnmanagedCE.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   See XmlBlasterUnmanagedCE.h
           Access C dll from C# on Windows CE with P/Invoke
Todo:      Callback function (update from Dll to C#)
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <string.h> /* memset */
#include <stdio.h>  /* printf */
#include <XmlBlasterUnmanagedCE.h>
/*#include <locale.h>*/ /* setlocal() */

#if defined(_WINDOWS)
/*#if defined(WINCE)*/

#ifdef __cplusplus
#	define XBFORCE_EXTERNC extern "C"
#else
#	define XBFORCE_EXTERNC
#endif

static const bool freeIt = true;

/*
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
	/*char ret[126];*/
	printf("Hello World from C DLL size=%d '%s', trying now wcs\n", size, p);
   /* wcstombs(ret, (const wchar_t *)p, 126);
	printf("Hello World from C DLL P=%s!\n", p); */
}
XBFORCE_EXTERNC extern void sayHelloEx(XmlBlasterUnmanagedCEException *exception) {
	printf("Hello World from C DLL sayHelloEx\n");
   exception->errorCode = strcpyAlloc("user.test");
   exception->message = strcpyAlloc("Some text");
   exception->remote = 1;
}
   
XBFORCE_EXTERNC extern char *sayHelloRet() {
   printf("dll: Invoke sayHelloRet()\n");
   /*return strcpyAlloc("Hello world allocated \xC3\xB6 in DLL");*/
   return strcpyAlloc("\xE8\xAA\x9E  German:\xC3\xB6  Korean:\xED\x95\x9C \xEA\xB5\xAD\xEC\x96\xB4  Japanese:\xE6\x97\xA5\xE6\x9C\xAC\xE8\xAA\x9E");
}

XBFORCE_EXTERNC void TestCallBack( FPTR pf, int32_t value )
{
   int32_t res;
   printf( "\ndll: Received value: %i", value );
   printf( "\ndll: Passing to callback..." );
   res = (*pf)(value);
   
   if( res )
      printf( "dll: Callback returned %d.\n", res );
   else
      printf( "dll: Callback returned %d.\n", res );
}

/** TODO: transport in xa: now this dll can only handel one client!!! */
//static XmlBlasterUnmanagedCELoggerFp managedLoggerFp;

static void myLogger(void *logUserP, 
                     XMLBLASTER_LOG_LEVEL currLevel,
                     XMLBLASTER_LOG_LEVEL level,
                     const char *location, const char *fmt, ...);

static void myLogger(void *logUserP, 
                     XMLBLASTER_LOG_LEVEL currLevel,
                     XMLBLASTER_LOG_LEVEL level,
                     const char *location, const char *fmt, ...)
{
   /* Guess we need no more than 200 bytes. */
   int n, size = 200;
   char *p = 0;
   va_list ap;
   int32_t lvl = (int32_t)level;
   XmlBlasterUnmanagedCELoggerFp managedLoggerFp = (XmlBlasterUnmanagedCELoggerFp)logUserP;


   if (managedLoggerFp == 0)
      return;

   if (level > currLevel) { /* XMLBLASTER_LOG_ERROR, XMLBLASTER_LOG_WARN, XMLBLASTER_LOG_INFO, XMLBLASTER_LOG_TRACE */
      return;
   }
   if ((p = (char *)malloc (size)) == NULL)
      return;

   for (;;) {
      /* Try to print in the allocated space. */
      va_start(ap, fmt);
      n = VSNPRINTF(p, size, fmt, ap); /* UNIX: vsnprintf(), WINDOWS: _vsnprintf() */
      va_end(ap);
      /* If that worked, print the string to console. */
      if (n > -1 && n < size) {
         /*printf("{%s-%s-%s} [%s] %s\n",
                   __DATE__, __TIME__, getLogLevelStr(level), location, p);*/
         /* Call now the C# logger XmlBlasterUnmanagedCELoggerFp */

         (*managedLoggerFp)(lvl, location, p);
         
         /* The C# code does not free 'p' during its UNICODE marshalling 
            with byteArrayFromIntPtr(false) -> no xmlBlasterUnmanagedCEFree() */
         free(p);
         
         return;
      }
      /* Else try again with more space. */
      if (n > -1)    /* glibc 2.1 */
         size = n+1; /* precisely what is needed */
      else           /* glibc 2.0 */
         size *= 2;  /* twice the old size */
      if ((p = (char *)realloc (p, size)) == NULL) {
         return;
      }
   }
}

/* extern "C" __declspec (dllexport) */
XBFORCE_EXTERNC Dll_Export void xmlBlasterUnmanagedCERegisterLogger(struct XmlBlasterAccessUnparsed *xa,
                           XmlBlasterUnmanagedCELoggerFp logger) {
   /*MessageBox(NULL, L"Entering xmlBlasterUnmanagedCERegisterLogger", _T("Unmanaged"), MB_OK);*/
   /*printf("dll: Register logger\n");*/
   if (logger != 0) {
      /* Register our own logging function */
      xa->log = myLogger;
    //  if (xa->connectionP != 0) xa->connectionP->log = myLogger;
    //  if (xa->callbackP != 0) xa->callbackP->log = myLogger;
      /* Pass a pointer which we can use in myLogger() again */
      xa->logUserP = logger;
    //  if (xa->connectionP != 0) xa->connectionP->logUserP = logger;
    //  if (xa->callbackP != 0) xa->callbackP->logUserP = logger;
   }
   else { /* unregister */
      xa->log = 0;
      xa->logUserP = 0;
      /*
      if (xa->connectionP != 0) xa->connectionP->log = 0;
      if (xa->connectionP != 0) xa->connectionP->logUserP = 0;
      if (xa->callbackP != 0) xa->callbackP->log = 0;
      if (xa->callbackP != 0) xa->callbackP->logUserP = 0;_
      */
   }
   /*xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_ERROR, __FILE__, "Testing logging output only");*/
}

/**
 * malloc size bytes
 * @param size > 0
 * @return p the allocated pointer
 */
XBFORCE_EXTERNC extern char *xmlBlasterUnmanagedCEMalloc(int32_t size) {
   /*printf("dll: xmlBlasterUnmanagedCEMalloc(size=%d)\n", size);*/
   if (size > 0)
      return (char *)malloc(size*sizeof(char));
   return (char *)0;
}

/**
 * Frees the malloced pointer
 * @param p Can be null
 */
XBFORCE_EXTERNC extern void xmlBlasterUnmanagedCEFree(char *p)  {
   /*if (p!=0)
      printf("dll: xmlBlasterUnmanagedCEFree size=%d:\n%s\n", strlen(p), p);*/ /* dangeraous for none strings like IntPtr */
   xmlBlasterFree(p);
}

/**
 * Frees the content of the malloced pointer
 * It is a hack as i don't know how to free the IntPtr which arrived as ** and was allocated on (*ppStruct)
 * for example in unSubscribe(): So we call this function similar as the unSubscribe() ...
 * @param pp Can be null
 */
XBFORCE_EXTERNC extern void xmlBlasterUnmanagedCEFreePP(char **pp)  {
   if (pp==0)
      return;
   else {
      char *p = *pp;
      /*printf("dll: xmlBlasterUnmanagedCEFreePP('%s')\n", ((p!=(char *)0)?p:""));*/
      xmlBlasterFree(p);
   }
}

/**
 * Allocates the members of XmlBlasterUnmanagedCEException
 * You need to free it after usage
 */
XBFORCE_EXTERNC static void convert(XmlBlasterException *in, XmlBlasterUnmanagedCEException *out) {
   if (*in->errorCode != 0) {
      out->errorCode = strcpyAlloc(in->errorCode);
      out->message = strcpyAlloc(in->message);
      out->remote = in->remote;
   }
   else {
      out->errorCode = 0;
   }   
}

XBFORCE_EXTERNC extern void xmlBlasterUnmanagedCEExceptionFree(XmlBlasterUnmanagedCEException *ex) {
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
   XmlBlasterUnmanagedCEException unmanagedException;
   XMLBLASTER_C_bool retVal = true;
   /*MessageBox(NULL, L"Entering interceptUpdate0", _T("Unmanaged"), MB_OK);*/

   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)userData;
   XmlBlasterUnmanagedCEUpdateFp unmanagedUpdate = (XmlBlasterUnmanagedCEUpdateFp)(xa->userFp);

   if (xa->logLevel>=XMLBLASTER_LOG_TRACE)
      xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "Got update message");
   
   if (userData != 0) ;  /* Supress compiler warning */
   if (unmanagedUpdate == 0) return false;
   
   /*memset(&unmanagedException, 0, sizeof(struct XmlBlasterUnmanagedCEException));*/
   unmanagedException.remote = false;
   unmanagedException.errorCode = (char)0;
   unmanagedException.message = (char)0;
   
   for (i=0; i<msgUnitArr->len; i++) {
      const char *cbSessionId = strcpyAlloc(msgUnitArr->secretSessionId);
      /*
      char *xml = messageUnitToXml(&msgUnitArr->msgUnitArr[i]);
      printf("[client] CALLBACK update(): Asynchronous message update arrived:%s\n",
             xml);
      xmlBlasterFree(xml);
      
      printf("XmlBlasterUnmanaged.c: before update() %d\n", (int)msgUnitArr->len);
      */
      
      if (xa->logLevel>=XMLBLASTER_LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "Got update, calling C# ...");

      /* Call C# ..., it may allocate errorCode */
      unmanagedUpdate(cbSessionId, &msgUnitArr->msgUnitArr[i], &unmanagedException);

      if (xa->logLevel>=XMLBLASTER_LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "Got update, calling C# DONE");

      if (unmanagedException.errorCode != 0) {
         /* catch first exception set and return */
         strncpy0(exception->errorCode, unmanagedException.errorCode, EXCEPTIONSTRUCT_ERRORCODE_LEN);
         if (unmanagedException.message != 0)
            strncpy0(exception->message, unmanagedException.message, EXCEPTIONSTRUCT_MESSAGE_LEN);
         exception->remote = unmanagedException.remote;
         xmlBlasterUnmanagedCEExceptionFree(&unmanagedException);
         msgUnitArr->msgUnitArr[i].responseQos = 0;
         xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_WARN, __FILE__, "Rethrowing exception from C# '%s' '%s'", exception->errorCode, exception->message);
         retVal = false;
      }
      else {
         msgUnitArr->msgUnitArr[i].responseQos = strcpyAlloc("<qos><state id='OK'/></qos>");
         /* Return QoS: Everything is OK */
      }
   }
   
   return retVal;
}

/*
 * We take a clone of argv, and not free your argv (crashes for some reason if we free it here)
 * @paran argc
 * @param argv [0] contains the exe name, [1] the first argument, etc.
 *   "Hello.exe" "-dispatch/connection/plugin/socket/hostname" "192.168.1.2"
 * @return the XmlBlasterAccessUnparsed handle
 */
XBFORCE_EXTERNC Dll_Export XmlBlasterAccessUnparsed *getXmlBlasterAccessUnparsedUnmanagedCE(int argc, char** argv){
   int i=0;
   XmlBlasterAccessUnparsed *xa;
   const char ** ptr = (const char **)malloc(argc*sizeof(char *));
   for (i=0; i<argc; ++i) {
      ptr[i] = strcpyAlloc(argv[i]);
      /*printf("dll: getAccess '%s'\n", argv[i]);*/
      /*if (freeIt) { xmlBlasterFree(argv[i]); argv[i] = 0; }*/
   }
   /*if (freeIt) { xmlBlasterFree((char *)argv); }*/
   xa = getXmlBlasterAccessUnparsed(argc, ptr);
   //xa->userObject = ...; // Transports something to the interceptUpdate() method
   return xa;
}

XBFORCE_EXTERNC Dll_Export void freeXmlBlasterAccessUnparsedUnmanagedCE(XmlBlasterAccessUnparsed *xmlBlasterAccess) {
   if (xmlBlasterAccess != 0) {
      int i;
      for (i=0; i<xmlBlasterAccess->argc; ++i)
         free((void*)xmlBlasterAccess->argv[i]);
      free((void*)xmlBlasterAccess->argv);
      freeXmlBlasterAccessUnparsed(xmlBlasterAccess);
   }
}

XBFORCE_EXTERNC Dll_Export bool xmlBlasterUnmanagedCEInitialize(struct XmlBlasterAccessUnparsed *xa,
            XmlBlasterUnmanagedCEUpdateFp update, XmlBlasterUnmanagedCEException *exception) {
   XmlBlasterException e;
   bool ret = false;
   xa->userFp = (XmlBlasterAccessGenericFp)update;
   ret = xa->initialize(xa, interceptUpdate, &e);
   convert(&e, exception);
   return ret; 
}

/**
 * Your qos is freed
 */
XBFORCE_EXTERNC Dll_Export char *xmlBlasterUnmanagedCEConnect(struct XmlBlasterAccessUnparsed *xa,
                           char *qos, XmlBlasterUnmanagedCEUpdateFp update, XmlBlasterUnmanagedCEException *exception) {
   XmlBlasterException e;
   char *ret = 0;
   /*MessageBox(NULL, L"Entering xmlBlasterUnmanagedCEConnect", _T("Unmanaged"), MB_OK);*/
   if (update != 0)
      xa->userFp = (XmlBlasterAccessGenericFp)update;
   ret = xa->connect(xa, qos, interceptUpdate, &e);
   convert(&e, exception);
   if (freeIt) { xmlBlasterFree(qos); qos=0; }
   return ret; 
}

/**
 * Your qos is freed
 */
XBFORCE_EXTERNC Dll_Export extern bool xmlBlasterUnmanagedCEDisconnect(struct XmlBlasterAccessUnparsed *xa,
                           char * qos, XmlBlasterUnmanagedCEException *exception) {
   XmlBlasterException e;
   bool ret = xa->disconnect(xa, qos, &e);
   convert(&e, exception);
   if (freeIt) { xmlBlasterFree(qos); qos=0; }
   return ret; 
}

XBFORCE_EXTERNC Dll_Export extern char *xmlBlasterUnmanagedCEPublish(struct XmlBlasterAccessUnparsed *xa,
                MsgUnitUnmanagedCE *msgUnitUnmanaged, XmlBlasterUnmanagedCEException *exception) {
   XmlBlasterException e;
   char *ret = xa->publish(xa, msgUnitUnmanaged, &e);
   convert(&e, exception);
   if (freeIt) freeMsgUnitData(msgUnitUnmanaged);
   return ret; 
}

XBFORCE_EXTERNC Dll_Export extern QosArr *xmlBlasterUnmanagedCEPublishArr(struct XmlBlasterAccessUnparsed *xa, MsgUnitArr *msgUnitArr, XmlBlasterUnmanagedCEException *exception) {
   XmlBlasterException e;
   QosArr *ret = xa->publishArr(xa, msgUnitArr, &e);
   convert(&e, exception);
   return ret; 
}

XBFORCE_EXTERNC Dll_Export extern void xmlBlasterUnmanagedCEPublishOneway(struct XmlBlasterAccessUnparsed *xa, MsgUnit *msgUnitArr, int length, XmlBlasterUnmanagedCEException *exception) {
   XmlBlasterException e;
   MsgUnitArr arr;
   /*printf("C: xmlBlasterUnmanagedCEPublishOneway %d", length);*/
   arr.isOneway = true;
   arr.len = length;
   arr.msgUnitArr = msgUnitArr;
   *arr.secretSessionId = 0;
   xa->publishOneway(xa, &arr, &e);
   convert(&e, exception);
}

/**
 * Your key and qos is freed
 */
XBFORCE_EXTERNC Dll_Export extern char *xmlBlasterUnmanagedCESubscribe(struct XmlBlasterAccessUnparsed *xa, char *key, char *qos, XmlBlasterUnmanagedCEException *exception) {
   XmlBlasterException e;
   char *ret = xa->subscribe(xa, key, qos, &e);
   convert(&e, exception);
   if (freeIt) { xmlBlasterFree(key); key=0; }
   if (freeIt) { xmlBlasterFree(qos); qos=0; }
   return ret; 
}

XBFORCE_EXTERNC Dll_Export void xmlBlasterUnmanagedCEUnSubscribe(struct XmlBlasterAccessUnparsed *xa,
   char * key, char * qos, XmlBlasterUnmanagedCEException *exception, uint32_t* pSize, XmlBlasterUnmanagedCEStringArr** ppStruct) {
   XmlBlasterException e;
   QosArr *ret = 0;
   initializeXmlBlasterException(&e);
   ret = xa->unSubscribe(xa, key, qos, &e);
   convert(&e, exception);
   if (freeIt) { xmlBlasterFree(key); key=0; }
   if (freeIt) { xmlBlasterFree(qos); qos=0; }
   if (*e.errorCode != 0) {
      /*printf("C: Caught exception in unSubscribe errorCode=%s, message=%s\n", e.errorCode, e.message);*/
      if (ret) freeQosArr(ret);
      return;
   }
   if (ret) {
      size_t i;
      XmlBlasterUnmanagedCEStringArr* pCurStruct = 0;
      const uint32_t cArraySize = ret->len;
      *pSize = cArraySize;
      *ppStruct = (XmlBlasterUnmanagedCEStringArr*)malloc( cArraySize * sizeof( XmlBlasterUnmanagedCEStringArr ));
      pCurStruct = *ppStruct;
      for (i=0; i<ret->len; i++, pCurStruct++) {
         /*printf("C: Unsubscribe success, returned status is '%s'\n", ret->qosArr[i]);*/
         pCurStruct->str = strcpyAlloc(ret->qosArr[i]);
      }
      freeQosArr(ret);
   }
}

XBFORCE_EXTERNC Dll_Export void xmlBlasterUnmanagedCEErase(struct XmlBlasterAccessUnparsed *xa, char * key,
     char * qos, XmlBlasterUnmanagedCEException *exception, uint32_t* pSize, XmlBlasterUnmanagedCEStringArr** ppStruct) {
   XmlBlasterException e;
   QosArr *ret = 0;
   initializeXmlBlasterException(&e);
   ret = xa->erase(xa, key, qos, &e);
   convert(&e, exception);
   if (freeIt) { xmlBlasterFree(key); key=0; }
   if (freeIt) { xmlBlasterFree(qos); qos=0; }
   if (*e.errorCode != 0) {
      if (ret) freeQosArr(ret);
      return;
   }
   if (ret) {
      size_t i;
      XmlBlasterUnmanagedCEStringArr* pCurStruct = 0;
      const uint32_t cArraySize = ret->len;
      *pSize = cArraySize;
      if (cArraySize == 0) {
         *ppStruct = 0;
      }
      else { 
         *ppStruct = (XmlBlasterUnmanagedCEStringArr*)malloc( cArraySize * sizeof( XmlBlasterUnmanagedCEStringArr ));
         pCurStruct = *ppStruct;
         for (i=0; i<ret->len; i++, pCurStruct++) {
            /*printf("dll: erase success, returned status is '%s'\n", ret->qosArr[i]);*/
            pCurStruct->str = strcpyAlloc(ret->qosArr[i]);
         }
      }
      freeQosArr(ret);
   }
}

XBFORCE_EXTERNC Dll_Export void xmlBlasterUnmanagedCEGet(struct XmlBlasterAccessUnparsed *xa,
        char * key, char *qos, XmlBlasterUnmanagedCEException *exception,
        uint32_t* pSize, MsgUnit** ppStruct) {
   XmlBlasterException e;
   uint32_t i;
   MsgUnitArr *msgUnitArr = xa->get(xa, key, qos, &e);
   convert(&e, exception);
   if (freeIt) { xmlBlasterFree(key); key=0; }
   if (freeIt) { xmlBlasterFree(qos); qos=0; }
   if (*e.errorCode != 0) {
      return;
   }
   /*printf("dll: xmlBlasterUnmanagedCEGet building response\n");*/
   if (msgUnitArr != (MsgUnitArr *)0) {
      const uint32_t cArraySize = msgUnitArr->len;
      MsgUnitUnmanagedCE* msgUnitUnmanagedP = 0;
      if (cArraySize == 0) {
         *ppStruct = 0;
         freeMsgUnitArr(msgUnitArr);
         return;
      }

      *pSize = cArraySize;
      *ppStruct = (MsgUnitUnmanagedCE*)malloc( cArraySize * sizeof( MsgUnitUnmanagedCE ));
      msgUnitUnmanagedP = *ppStruct;
      /*printf("dll: xmlBlasterUnmanagedCEGet %ud\n", cArraySize);*/
      /* TODO: It should be possible to pass msgUnitArr->msgUnitArr* directly
         as it has the same memory layout as the IntPtr */
      for(i=0; i < cArraySize; i++, msgUnitUnmanagedP++) {
         MsgUnit *msgUnit = &msgUnitArr->msgUnitArr[i];
         char *cnt = (char *)malloc(msgUnit->contentLen*sizeof(char));
         /*printf("dll: xmlBlasterUnmanagedCEGet processing #%u\n", i);*/
         msgUnitUnmanagedP->contentLen = msgUnit->contentLen;
         if (cnt != 0) {
            size_t j;
            for (j=0; j<msgUnit->contentLen; j++) cnt[j] = (char)msgUnit->content[j];
         }
         msgUnitUnmanagedP->content = cnt;
         msgUnitUnmanagedP->key = strcpyAlloc(msgUnit->key);
         msgUnitUnmanagedP->qos = strcpyAlloc(msgUnit->qos);
         msgUnitUnmanagedP->responseQos = strcpyAlloc("<qos/>");
         /*printf("dll: xmlBlasterUnmanagedCEGet processing #%u key=%s qos=%s\n", i, msgUnitUnmanagedP->key, msgUnitUnmanagedP->qos);*/
      }
      freeMsgUnitArr(msgUnitArr);
   }
}

XBFORCE_EXTERNC Dll_Export char *xmlBlasterUnmanagedCEPing(struct XmlBlasterAccessUnparsed *xa, char *qos, XmlBlasterUnmanagedCEException *exception) {
   XmlBlasterException e;
   char *ret = xa->ping(xa, qos, &e);
   convert(&e, exception);
   if (freeIt) { xmlBlasterFree(qos); qos=0; }
   return ret; 
}

XBFORCE_EXTERNC Dll_Export bool xmlBlasterUnmanagedCEIsConnected(struct XmlBlasterAccessUnparsed *xa) {
   return xa->isConnected(xa);
}

XBFORCE_EXTERNC Dll_Export const char *xmlBlasterUnmanagedCEUsage() {
   char *usage = (char *)malloc(XMLBLASTER_MAX_USAGE_LEN*sizeof(char));
   return xmlBlasterAccessUnparsedUsage(usage);
}

XBFORCE_EXTERNC Dll_Export const char *xmlBlasterUnmanagedCEVersion() {
   char *version = strcpyAlloc(getXmlBlasterVersion());
   return version;
}

#endif /*defined(_WINDOWS)*/
/*#endif*/ /*defined(WINCE)*/
