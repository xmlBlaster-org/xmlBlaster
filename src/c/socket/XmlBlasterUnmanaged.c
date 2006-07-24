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

Dll_Export void setTest1(Test1 *test1) {
   printf("C code: setTest1() secretSessionId=%s\n", test1->secretSessionId);
}

Test1 test1_;
Dll_Export Test1 getTest1() {
   printf("C code: getTest1()\n");
   strcpy(test1_.secretSessionId, "Back from C");
   return test1_;
}

Test2 test2_;
Dll_Export Test2 getTest2() {
   test2_.len = 1;
   test2_.p = malloc(sizeof(char *));
   test2_.p[0] = strcpyAlloc("C: Hi array");
   return test2_;   
}

MsgUnit msgUnit_;
MsgUnit msgUnit2_;
Dll_Export int getTest3(MsgUnit** pList) {
   printf("C code: getTest3()\n");
   *pList = malloc(2*sizeof(MsgUnit*));
   msgUnit_.key = strcpyAlloc("<key oid='1'/>");
   msgUnit_.contentLen = 2;
   msgUnit_.content = strcpyAlloc("X");
   msgUnit_.qos = strcpyAlloc("<qos/>");
   msgUnit_.responseQos = 0;
   *pList[0] = msgUnit_;
   msgUnit2_.key = strcpyAlloc("<key oid='2'/>");
   msgUnit2_.contentLen = 2;
   msgUnit2_.content = strcpyAlloc("X");
   msgUnit2_.qos = strcpyAlloc("<qos/>");
   msgUnit2_.responseQos = 0;
   *pList[1] = msgUnit_;
   printf("C code: getTest3() leaving\n");
   return 2;
}

typedef struct MsgUnitUnmanaged {
   const char *key;         /**< XML formatted ASCII string of the message topic */
   size_t contentLen;       /**< Number of bytes in content */
   const char *content;     /**< Raw data (not 0 terminated) */
   const char *qos;         /**< XML formatted ASCII string of Quality of Service */
   char *responseQos;       /**< Used to transport the response QoS string back to caller */
} MsgUnitUnmanaged;

/*
typedef struct MsgUnitUnmanaged
{
   const char *key;
   const char *qos;
   size_t contentLen;
   const char *content;
   char *responseQos;
} MsgUnitUnmanaged;
*/
/*extern "C" PINVOKELIB_API void TestOutArrayOfStructs( int* pSize, MYSTRSTRUCT2** ppStruct )*/
Dll_Export void TestOutArrayOfStructs( uint32_t* pSize, MsgUnit** ppStruct )
{
   int i;
   const int cArraySize = 15;
   *pSize = cArraySize;
   *ppStruct = (MsgUnit*)malloc( cArraySize * sizeof( MsgUnit ));
   {
     MsgUnit* pCurStruct = *ppStruct;
     /*char* buffer;*/
     printf("C: TestOutArrayOfStructs %ud\n", cArraySize);
     for( i = 0; i < cArraySize; i++, pCurStruct++ )
     {
          char tmp[126];
          sprintf(tmp, "<key oid='%d'/>", i);
          pCurStruct->key = strcpyAlloc(tmp);
          pCurStruct->qos = strcpyAlloc("<qos/>");
          pCurStruct->responseQos = 0;
          
          /*
          buffer = (char*)CoTaskMemAlloc( 4 );
          strcpy_s( buffer, 4, "***" );
          pCurStruct->buffer = buffer;
          */
          pCurStruct->content = strcpyAlloc("***");
          pCurStruct->contentLen = strlen(pCurStruct->content);
     }
   }
}

Dll_Export void TestOutArrayOfMsgUnits( int* pSize, MsgUnit** ppStruct )
{
   int i;
   const int cArraySize = 4;
   *pSize = cArraySize;
   *ppStruct = (MsgUnit*)malloc( cArraySize * sizeof( MsgUnit ));
   {
     MsgUnit* pCurStruct = *ppStruct;
     /*char* buffer;*/
     for( i = 0; i < cArraySize; i++, pCurStruct++ )
     {
        char tmp[126];
     printf("C: TestOutArrayOfStructs %ud\n", cArraySize);
          /*
          buffer = (char*)CoTaskMemAlloc( 4 );
          strcpy_s( buffer, 4, "***" );
          pCurStruct->buffer = buffer;
          */
        pCurStruct->content = strcpyAlloc("Some content");
        pCurStruct->contentLen = strlen(pCurStruct->content);
        sprintf(tmp, "<key oid='%d'/>", i);
        pCurStruct->key = strcpyAlloc(tmp);
        pCurStruct->qos = strcpyAlloc("<qos/>");
        pCurStruct->responseQos = strcpyAlloc("<qos><response/></qos>");
     }
   }
}

typedef struct StringArr {
   const char *str; 
} StringArr;
Dll_Export void TestOutStringArr( int* pSize, StringArr** ppStruct )
{
   int i;
   const int cArraySize = 4;
   *pSize = cArraySize;
   *ppStruct = (StringArr*)malloc( cArraySize * sizeof( StringArr ));
   {
     StringArr* pCurStruct = *ppStruct;
     /*char* buffer;*/
     for( i = 0; i < cArraySize; i++, pCurStruct++ )
     {
        char tmp[126];
         printf("C: TestOutStringArr %d/%d\n", i, cArraySize);
          /*
          buffer = (char*)CoTaskMemAlloc( 4 );
          */
        sprintf(tmp, "<qos>%d</qos>", i);
        pCurStruct->str = strcpyAlloc(tmp);
     }
   }
}


Dll_Export void TestOutQosArr( char* ppStrArray[], int *pSize )
{
        int i;
        *pSize = 4;
   *ppStrArray = (char*)malloc( (*pSize) * sizeof( char * ));
      printf("C: TestOutQosArr\n");
        for(i = 0; i < (*pSize); i++ ) {
             /*char tmp[256];
             sprintf(tmp, "Hello%d", i);*/
             /* (char*)CoTaskMemAlloc( sizeof(char) * 10 ); */
             ppStrArray[ i ] = strcpyAlloc("Hello");
        }
}


/*extern "C" _declspec(dllexport) int MinArray(int* pData, int length)*/
Dll_Export int MinArray(MsgUnit* pData, int length)
{
   /* Initialise minData to the first element of the pData Array */
   int pos;
   for(pos = 0; pos < length; pos++) {
      MsgUnit msgUnit = pData[pos];
      printf("C: MinArray %s %s\n", msgUnit.key, msgUnit.qos);
    }
    return length;
}

Dll_Export int setMsgUnitArr(MsgUnitUnmanagedArr pData)
{
   int pos;
   printf("C: setMsgUnitArr %d\n", pData.len);
   
   for(pos = 0; pos < pData.len; pos++) {
      MsgUnit msgUnit = pData.msgUnitArr[pos];
      printf("C: setMsgUnitArr %s %d key=%s qos=%s\n",
          pData.secretSessionId, (int)msgUnit.contentLen,
          msgUnit.key, msgUnit.qos);
   }
   return pData.len;
}
/*
Dll_Export int setMsgUnitArr(MsgUnitArr pData)
{
   int pos;
   printf("C: setMsgUnitArr %ld\n", pData.len);
   
   for(pos = 0; pos < pData.len; pos++) {
      MsgUnit msgUnit = pData.msgUnitArr[pos];
      printf("C: setMsgUnitArr %d\n", (int)msgUnit.contentLen);
   }
   return pData.len;
}
*/

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

/*
 * ==3245== Invalid read of size 1
==3245==    at 0x4A21402: strlen (in /usr/lib64/valgrind/amd64-linux/vgpreload_memcheck.so)
==3245==    by 0x48D0A8: mono_string_new (in /usr/bin/mono)
==3245==    by 0x6C8E584: ???
==3245==    by 0x63F9631: runUpdate (XmlBlasterAccessUnparsed.c:866)
==3245==    by 0x50DE192: start_thread (in /lib64/libpthread-2.4.so)
==3245==    by 0x550B45C: clone (in /lib64/libc-2.4.so)
==3245==  Address 0x6F2079656B3C200A is not stack'd, malloc'd or (recently) free'd
 */
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
      
      /* Call C# ... */
      ret = unmanagedUpdate(cbSessionId, &msgUnitArr->msgUnitArr[i], &unmanagedException);

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

Dll_Export XmlBlasterAccessUnparsed *getXmlBlasterAccessUnparsedUnmanaged(int argc, const char* const* argv){
   /** argv seems to be freed by C#, so we clone it here */
   int i=0;
   const char ** ptr;
   ptr = malloc(argc*sizeof(char *));
   for (i=0; i<argc; ++i) {
      ptr[i] = strcpyAlloc(argv[i]);
   }
   return getXmlBlasterAccessUnparsed(argc, ptr);
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

/* Ohne "ref MsgUnit"
 * ==4281== Conditional jump or move depends on uninitialised value(s)
==4281==    at 0x548F0B6: vfprintf (in /lib64/libc-2.4.so)
==4281==    by 0x54AB6F8: vsprintf (in /lib64/libc-2.4.so)
==4281==    by 0x54961A7: sprintf (in /lib64/libc-2.4.so)
==4281==    by 0x63B27AB: encodeMsgUnit (xmlBlasterSocket.c:115)
==4281==    by 0x63AFDC3: xmlBlasterPublish (XmlBlasterConnectionUnparsed.c:931)
==4281==    by 0x63ABFF3: xmlBlasterPublish (XmlBlasterAccessUnparsed.c:699)
==4281==    by 0x63B1F5C: xmlBlasterUnmanagedPublish (XmlBlasterUnmanaged.c:319)
==4281==    by 0x64D4132: ???
==4281==    by 0x64D3C71: ???
==4281==    by 0x4106E85: ???
 * 
*/
/*
 * ==3940== Invalid write of size 1
==3940==    at 0x63B28E0: encodeMsgUnit (xmlBlasterSocket.c:129)
==3940==    by 0x63AFDC3: xmlBlasterPublish (XmlBlasterConnectionUnparsed.c:931)
==3940==    by 0x63ABFF3: xmlBlasterPublish (XmlBlasterAccessUnparsed.c:699)
==3940==    by 0x63B1F5C: xmlBlasterUnmanagedPublish (XmlBlasterUnmanaged.c:308)
==3940==    by 0x64D43D0: ???
==3940==    by 0x64D3EFA: ???
==3940==    by 0x4106E85: ???
==3940==  Address 0x0 is not stack'd, malloc'd or (recently) free'd
 * */
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
   printf("C: xmlBlasterUnmanagedPublishOneway %d", length);
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
      printf("C: Caught exception in unSubscribe errorCode=%s, message=%s\n",
                  e.errorCode, e.message);
      if (ret) freeQosArr(ret);
      return;
   }
   if (ret) {
      size_t i;
      XmlBlasterUnmanagedStringArr* pCurStruct = 0;
      const int cArraySize = ret->len;
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

Dll_Export void xmlBlasterUnmanagedErase(struct XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterUnmanagedException *exception, uint32_t* pSize, XmlBlasterUnmanagedStringArr** ppStruct) {
   XmlBlasterException e;
   QosArr *ret = 0;
   initializeXmlBlasterException(&e);
   ret = xa->erase(xa, key, qos, &e);
   convert(&e, exception);
   if (*e.errorCode != 0) {
      printf("C: Caught exception in erase errorCode=%s, message=%s\n",
                  e.errorCode, e.message);
      if (ret) freeQosArr(ret);
      return;
   }
   if (ret) {
      size_t i;
      XmlBlasterUnmanagedStringArr* pCurStruct = 0;
      const int cArraySize = ret->len;
      *pSize = cArraySize;
      *ppStruct = (XmlBlasterUnmanagedStringArr*)malloc( cArraySize * sizeof( XmlBlasterUnmanagedStringArr ));
      pCurStruct = *ppStruct;
      for (i=0; i<ret->len; i++) {
         /*printf("C: Erase success, returned status is '%s'\n", ret->qosArr[i]);*/
         pCurStruct->str = strcpyAlloc(ret->qosArr[i]);
      }
      freeQosArr(ret);
   }
}

Dll_Export extern void xmlBlasterUnmanagedGet(struct XmlBlasterAccessUnparsed *xa,
        const char * const key, const char * qos, XmlBlasterUnmanagedException *exception,
        uint32_t* pSize, MsgUnit** ppStruct) {
   XmlBlasterException e;
   int i;
   MsgUnitArr *msgUnitArr = xa->get(xa, key, qos, &e);
   convert(&e, exception);
   if (*e.errorCode != 0) {
      printf("C: Caught exception in get errorCode=%s, message=%s\n",
                  e.errorCode, e.message);
      return;
   }
   if (msgUnitArr != (MsgUnitArr *)0) {
      const int cArraySize = msgUnitArr->len;
      MsgUnit* pCurStruct = 0;
      *pSize = cArraySize;
      *ppStruct = (MsgUnit*)malloc( cArraySize * sizeof( MsgUnit ));
      pCurStruct = *ppStruct;
      printf("C: xmlBlasterUnmanagedGet %ud\n", cArraySize);
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

Dll_Export extern char *xmlBlasterUnmanagedPing(struct XmlBlasterAccessUnparsed *xa, const char * const qos, XmlBlasterUnmanagedException *exception) {
   XmlBlasterException e;
   char *ret = xa->ping(xa, qos, &e);
   convert(&e, exception);
   return ret; 
}

Dll_Export extern bool xmlBlasterUnmanagedIsConnected(struct XmlBlasterAccessUnparsed *xa) {
   return xa->isConnected(xa);
}

