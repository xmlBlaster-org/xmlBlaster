/*----------------------------------------------------------------------------
Name:      XmlBlasterAccessUnparsed.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Wraps raw socket connection to xmlBlaster
           Implements sync connection and async callback
           Needs pthread to compile (multi threading).
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:
  LINUX:   gcc -DXmlBlasterAccessUnparsedMain -D_ENABLE_STACK_TRACE_ -rdynamic -export-dynamic -Wall -pedantic -g -D_REENTRANT -I.. -o XmlBlasterAccessUnparsedMain XmlBlasterAccessUnparsed.c ../util/msgUtil.c ../util/Properties.c xmlBlasterSocket.c XmlBlasterConnectionUnparsed.c CallbackServerUnparsed.c -lpthread
           g++ -DXmlBlasterAccessUnparsedMain -DXMLBLASTER_C_COMPILE_AS_CPP -Wall -pedantic -g -D_REENTRANT -I.. -o XmlBlasterAccessUnparsedMain XmlBlasterAccessUnparsed.c ../util/msgUtil.c ../util/Properties.c xmlBlasterSocket.c XmlBlasterConnectionUnparsed.c CallbackServerUnparsed.c -lpthread
           icc -DXmlBlasterAccessUnparsedMain -D_ENABLE_STACK_TRACE_ -rdynamic -g -D_REENTRANT -I.. -o XmlBlasterAccessUnparsedMain XmlBlasterAccessUnparsed.c ../util/msgUtil.c ../util/Properties.c xmlBlasterSocket.c XmlBlasterConnectionUnparsed.c CallbackServerUnparsed.c -lpthread
  WIN:     cl /MT /W4 -DXmlBlasterAccessUnparsedMain -D_WINDOWS -I.. -I../pthreads /FeXmlBlasterAccessUnparsedMain.exe  XmlBlasterAccessUnparsed.c ..\util\msgUtil.c ..\util\Properties.c xmlBlasterSocket.c XmlBlasterConnectionUnparsed.c CallbackServerUnparsed.c ws2_32.lib pthreadVC.lib
           (download pthread for Windows and WinCE from http://sources.redhat.com/pthreads-win32)
  Solaris: cc  -DXmlBlasterAccessUnparsedMain -v -Xc -g -D_REENTRANT -I.. -o XmlBlasterAccessUnparsedMain XmlBlasterAccessUnparsed.c ../util/msgUtil.c ../util/Properties.c xmlBlasterSocket.c XmlBlasterConnectionUnparsed.c CallbackServerUnparsed.c -lpthread -lsocket -lnsl
           CC  -DXmlBlasterAccessUnparsedMain -DXMLBLASTER_C_COMPILE_AS_CPP -g -D_REENTRANT -I.. -o XmlBlasterAccessUnparsedMain XmlBlasterAccessUnparsed.c ../util/msgUtil.c ../util/Properties.c xmlBlasterSocket.c XmlBlasterConnectionUnparsed.c CallbackServerUnparsed.c -lpthread -lsocket -lnsl

  Linux with libxmlBlasterC.so:
           gcc -DXmlBlasterAccessUnparsedMain -o XmlBlasterAccessUnparsedMain XmlBlasterAccessUnparsed.c  -L../../../lib -lxmlBlasterClientC -I.. -Wl,-rpath=../../../lib -D_REENTRANT  -lpthread
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <sys/types.h>
#include <socket/xmlBlasterSocket.h>
#include <XmlBlasterAccessUnparsed.h>

#ifdef _WINDOWS
#  define ssize_t signed int
#else
#  include <pthread.h>  /* The original pthreads.h from the OS, only need for gcc 2.9x: I don't know why, it is in XmlBlasterAccessUnparsed.h already */
#endif

/**
 * Little helper to collect args for the new created thread
 */
typedef struct Dll_Export UpdateContainer {
   XmlBlasterAccessUnparsed *xa;
   MsgUnitArr *msgUnitArrP;
   void *userData;
   XmlBlasterException exception;     /* Holding a clone from the original as the callback thread may use it for another message */
   SocketDataHolder socketDataHolder; /* Holding a clone from the original */
} UpdateContainer;

static bool initialize(XmlBlasterAccessUnparsed *xa, UpdateFp update, XmlBlasterException *exception);
static char *xmlBlasterConnect(XmlBlasterAccessUnparsed *xa, const char * const qos, UpdateFp update, XmlBlasterException *exception);
static bool xmlBlasterDisconnect(XmlBlasterAccessUnparsed *xa, const char * const qos, XmlBlasterException *exception);
static char *xmlBlasterPublish(XmlBlasterAccessUnparsed *xa, MsgUnit *msgUnit, XmlBlasterException *exception);
static QosArr *xmlBlasterPublishArr(XmlBlasterAccessUnparsed *xa, MsgUnitArr *msgUnitArr, XmlBlasterException *exception);
static void xmlBlasterPublishOneway(XmlBlasterAccessUnparsed *xa, MsgUnitArr *msgUnitArr, XmlBlasterException *exception);
static char *xmlBlasterSubscribe(XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterException *exception);
static QosArr *xmlBlasterUnSubscribe(XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterException *exception);
static QosArr *xmlBlasterErase(XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterException *exception);
static MsgUnitArr *xmlBlasterGet(XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterException *exception);
static char *xmlBlasterPing(XmlBlasterAccessUnparsed *xa, const char * const qos, XmlBlasterException *exception);
static bool isConnected(XmlBlasterAccessUnparsed *xa);
static void responseEvent(MsgRequestInfo *msgRequestInfoP, void /*SocketDataHolder*/ *socketDataHolder);
static MsgRequestInfo *preSendEvent(MsgRequestInfo *msgRequestInfo, XmlBlasterException *exception);
static MsgRequestInfo *postSendEvent(MsgRequestInfo *msgRequestInfo, XmlBlasterException *exception);
static bool checkArgs(XmlBlasterAccessUnparsed *xa, const char *methodName, bool checkIsConnected, XmlBlasterException *exception);
static void interceptUpdate(MsgUnitArr *msgUnitArr, void *userData, XmlBlasterException *xmlBlasterException, void/*SocketDataHolder*/ *socketDataHolder);
static bool mutexUnlock(MsgRequestInfo *msgRequestInfoP, XmlBlasterException *exception);

/**
 * Create an instance for access xmlBlaster. 
 * This is usually the first call of a client. 
 * <br />
 * NOTE: Our properties point on the passed argv memory, so you should
 * not free the original argv memory before you free XmlBlasterAccessUnparsed.
 * @param argc The number of argv properties
 * @param argv The command line properties, see <code>Properties *createProperties(int argc, const char* const* argv)</code>
 *             for a specification
 * @return NULL if bootstrapping failed. If not NULL you need to free() it when you are done
 * usually by calling freeXmlBlasterAccessUnparsed().
 */
Dll_Export XmlBlasterAccessUnparsed *getXmlBlasterAccessUnparsed(int argc, const char* const* argv) {
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)calloc(1, sizeof(XmlBlasterAccessUnparsed));
   if (xa == 0) return xa;
   xa->argc = argc;
   xa->argv = argv;
   xa->props = createProperties(xa->argc, xa->argv);
   if (xa->props == 0) {
      freeXmlBlasterAccessUnparsed(xa);
      return (XmlBlasterAccessUnparsed *)0;
   }
   xa->isInitialized = false;
   xa->userObject = 0; /* A client can use this pointer to point to any client specific information */
   xa->connect = xmlBlasterConnect;
   xa->initialize = initialize;
   xa->disconnect = xmlBlasterDisconnect;
   xa->publish = xmlBlasterPublish;
   xa->publishArr = xmlBlasterPublishArr;
   xa->publishOneway = xmlBlasterPublishOneway;
   xa->subscribe = xmlBlasterSubscribe;
   xa->unSubscribe = xmlBlasterUnSubscribe;
   xa->erase = xmlBlasterErase;
   xa->get = xmlBlasterGet;
   xa->ping = xmlBlasterPing;
   xa->isConnected = isConnected;
   xa->logLevel = parseLogLevel(xa->props->getString(xa->props, "logLevel", "WARN"));
   xa->log = xmlBlasterDefaultLogging;
   xa->logUserP = 0;
   xa->clientsUpdateFp = 0;
   xa->callbackMultiThreaded = xa->props->getBool(xa->props, "plugin/socket/multiThreaded", true);
   xa->callbackMultiThreaded = xa->props->getBool(xa->props, "dispatch/callback/plugin/socket/multiThreaded", xa->callbackMultiThreaded);
   /*   xa->lowLevelAutoAck = xa->props->getBool(xa->props, "plugin/socket/lowLevelAutoAck", false); */
   /*   xa->lowLevelAutoAck = xa->props->getBool(xa->props, "dispatch/callback/plugin/socket/lowLevelAutoAck", xa->lowLevelAutoAck); */
   /* Currently forced to false: needs mutex and reference counter to not freeMsgUnitArr twice */
   xa->lowLevelAutoAck = false;

   if (xa->callbackMultiThreaded == true) {
      xa->log(xa->logUserP, xa->logLevel, LOG_INFO, __FILE__, "Multi threaded callback delivery is activated with -plugin/socket/multiThreaded true");
      /*xa->callbackMultiThreaded = false;*/
   }
   xa->responseTimeout = xa->props->getLong(xa->props, "plugin/socket/responseTimeout", 60000L); /* One minute (given in millis) */
   xa->responseTimeout = xa->props->getLong(xa->props, "dispatch/connection/plugin/socket/responseTimeout", xa->responseTimeout);
   /* ERROR HANDLING ? xa->log(xa->logUserP, xa->logLevel, LOG_WARN, __FILE__, "Your configuration '-plugin/socket/responseTimeout %s' is invalid", argv[iarg]); */
   xa->callbackThreadId = 0;
   xa->threadCounter = 0;

   if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__,
                                "Created handle: -logLevel=%s -plugin/socket/responseTimeout=%ld",
                                getLogLevelStr(xa->logLevel), xa->responseTimeout);
   return xa;
}

Dll_Export void freeXmlBlasterAccessUnparsed(XmlBlasterAccessUnparsed *xa)
{
   int retVal;

   if (xa == 0) {
      char *stack = getStackTrace(10);
      printf("[%s:%d] Please provide a valid XmlBlasterAccessUnparsed pointer to freeXmlBlasterAccessUnparsed() %s",
                __FILE__, __LINE__, stack);
      free(stack);
      return;
   }

   freeProperties(xa->props);

   if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, "freeXmlBlasterAccessUnparsed()");
   if (xa->connectionP != 0) {
      freeXmlBlasterConnectionUnparsed(xa->connectionP);
      xa->connectionP = 0;
   }
   if (xa->callbackP != 0) {
      xa->callbackP->shutdown(xa->callbackP);
      if (!xa->callbackP->isShutdown) {
         retVal = pthread_join(xa->callbackThreadId, 0);
         if (retVal != 0) {
            xa->log(xa->logUserP, xa->logLevel, LOG_ERROR, __FILE__, "join problem return value is %d", retVal);
         }
         else {
            if (xa->logLevel>=LOG_INFO) xa->log(xa->logUserP, xa->logLevel, LOG_INFO, __FILE__,
                                        "Pthread_join(id=%ld) succeeded for callback server thread", xa->callbackThreadId);
         }
         /*         if (xa->logLevel>=LOG_INFO) xa->log(xa->logUserP, xa->logLevel, LOG_INFO, __FILE__,
                                        "Pthread_join(id=%ld) COMMENTED OUT"); */
         xa->callbackThreadId = 0;
      }
      freeCallbackServerUnparsed(xa->callbackP);
      xa->callbackP = 0;
   }

   {  /* Wait for any pending update() dispatcher threads to die */
      int i;
      int num = 100;
      int interval = 10;
      for (i=0; i<num; i++) {
         if ((int)xa->threadCounter < 1)
            break;
         sleepMillis(interval);
         if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__,
             "freeXmlBlasterAccessUnparsed(): Sleeping %d millis for update thread to join. %d/%d", interval, i, num);
      }
   }
   free(xa);
}

/**
 * Creates client side connection object and the callback server. 
 * This method is automatically called by connect() so you usually only
 * call it explicitly if you are interested in the callback server settings.
 * @param clientUpdateFp The clients callback handler function. If NULL our default handler is used
 * @return true on success
 */
static bool initialize(XmlBlasterAccessUnparsed *xa, UpdateFp clientUpdateFp, XmlBlasterException *exception)
{
   int threadRet = 0;

   if (checkArgs(xa, "initialize", false, exception) == false) return false;

   if (xa->isInitialized) {
      return true;
   }

   if (clientUpdateFp == 0) {
      xa->clientsUpdateFp = 0;
      xa->log(xa->logUserP, xa->logLevel, LOG_WARN, "",
        "Your callback UpdateFp pointer is NULL, we use our default callback handler");
   }
   else {
      xa->clientsUpdateFp = clientUpdateFp;
   }

   xa->connectionP = getXmlBlasterConnectionUnparsed(xa->argc, xa->argv);
   if (xa->connectionP == 0) {
      strncpy0(exception->errorCode, "resource.outOfMemory", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
               "[%.100s:%d] Creating XmlBlasterConnectionUnparsed failed", __FILE__, __LINE__);
      if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, exception->message);
      return false;
   }
   xa->connectionP->log = xa->log;
   xa->connectionP->logUserP = xa->logUserP;
   if (xa->connectionP->initConnection(xa->connectionP, exception) == false) /* Establish low level TCP/IP connection */
      return false;

   /* the fourth arg 'xa' is returned as 'void *userData' in update() method */
   xa->callbackP = getCallbackServerUnparsed(xa->argc, xa->argv, interceptUpdate, xa);
   if (xa->callbackP == 0) {
      strncpy0(exception->errorCode, "resource.outOfMemory", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
               "[%.100s:%d] Creating CallbackServerUnparsed failed", __FILE__, __LINE__);
      if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, exception->message);
      freeXmlBlasterConnectionUnparsed(xa->connectionP);
      return false;
   }
   xa->callbackP->log = xa->log;
   xa->callbackP->logUserP = xa->logUserP;

   xa->callbackP->useThisSocket(xa->callbackP, xa->connectionP->socketToXmlBlaster);

   xa->log(xa->logUserP, xa->logLevel, LOG_INFO, __FILE__,
          "Created CallbackServerUnparsed instance, creating on a separate thread a listener on socket://%s:%d...",
          (xa->callbackP->hostCB == 0) ? "" : xa->callbackP->hostCB, xa->callbackP->portCB);

   /* Register our callback funtion which is called just before sending a message */
   xa->connectionP->preSendEvent = preSendEvent;
   xa->connectionP->preSendEvent_userP = xa;

   /* Register our callback funtion which is called just after sending a message */
   xa->connectionP->postSendEvent = postSendEvent;
   xa->connectionP->postSendEvent_userP = xa;

   /* thread blocks on socket listener */
   threadRet = pthread_create(&xa->callbackThreadId, (const pthread_attr_t *)0, (void * (*)(void *))xa->callbackP->runCallbackServer, (void *)xa->callbackP);
   if (threadRet != 0) {
      strncpy0(exception->errorCode, "resource.tooManyThreads", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
               "[%.100s:%d] Creating thread failed with error number %d",
               __FILE__, __LINE__, threadRet);
      if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, exception->message);
      freeCallbackServerUnparsed(xa->callbackP);
      freeXmlBlasterConnectionUnparsed(xa->connectionP);
      return false;
   }

   xa->isInitialized = true;
   if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__,
                                "initialize() successful");
   return xa->isInitialized;
}

static bool isConnected(XmlBlasterAccessUnparsed *xa)
{
   if (xa == 0 || xa->connectionP == 0) {
      return false;
   }
   return xa->connectionP->isConnected(xa->connectionP);
}

/**
 * Callback from XmlBlasterConnectionUnparsed just before a message is sent,
 * the msgRequestInfo contains the requestId used. 
 * This is the clients calling thread.
 * @param userP May not be NULL, is of type XmlBlasterAccessUnparsed *
 * @param msgRequestInfo Contains some informations about the request, may not be NULL
 * @param exception May not be NULL
 * @return The same (or a manipulated/encrypted) msgRequestInfo, if NULL the exception is filled. 
 *         If msgRequestInfoP->blob.data was changed and malloc()'d by you, the caller will free() it.
 *         If you return NULL you need to call removeResponseListener() to avoid a memory leak.
 */
static MsgRequestInfo *preSendEvent(MsgRequestInfo *msgRequestInfoP, XmlBlasterException *exception)
{
   bool retVal;
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)msgRequestInfoP->xa;

   if (!strcmp(XMLBLASTER_PUBLISH_ONEWAY, msgRequestInfoP->methodName))
      return msgRequestInfoP;

   /* ======== Initialize threading ====== */
   msgRequestInfoP->responseMutexIsLocked = false; /* Only to remember if the client thread holds the lock */
#  ifdef _WINDOWS
   msgRequestInfoP->responseMutex = PTHREAD_MUTEX_INITIALIZER;
   msgRequestInfoP->responseCond = PTHREAD_COND_INITIALIZER;
#  else
   /* On Linux gcc & SUN CC:
        "parse error before '{' token"
      when initializing directly, so we do a hack here:
      (NOTE: using pthread_cond_init() would be the choice to
      initialize but this only works with dynamic conds)
   */
   {
      pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
      msgRequestInfoP->responseMutex = mutex;
   }
   {
      pthread_cond_t cond = PTHREAD_COND_INITIALIZER;
      msgRequestInfoP->responseCond = cond;
   }
#  endif

   if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__,
                                "preSendEvent(%s) occurred", msgRequestInfoP->methodName);
   retVal = xa->callbackP->addResponseListener(xa->callbackP, msgRequestInfoP, responseEvent);
   if (retVal == false) {
      strncpy0(exception->errorCode, "user.internal", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
               "[%.100s:%d] Couldn't register as response listener", __FILE__, __LINE__);
      if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, exception->message);
      return (MsgRequestInfo *)0;
   }

   if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__,
                  "preSendEvent(requestId=%s, msgRequestInfoP->responseBlob.dataLen=%d), entering lock",
                  msgRequestInfoP->requestIdStr, msgRequestInfoP->responseBlob.dataLen);
   if ((retVal = pthread_mutex_lock(&msgRequestInfoP->responseMutex)) != 0) {
      strncpy0(exception->errorCode, "user.internal", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
               "[%.100s:%d] Error trying to lock responseMutex %d", __FILE__, __LINE__, retVal);
      if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, exception->message);
      return (MsgRequestInfo *)0;
   }
   msgRequestInfoP->responseMutexIsLocked = true; /* Only if the client thread holds the lock */

   return msgRequestInfoP;
}

/**
 * This function is called by the callback server when a response message arrived (after we send a request). 
 * The xa->responseBlob->data is malloc()'d with the response string, you need to free it. 
 * This method is executed by the callback server thread.
 * @param userP May not be NULL, is of type XmlBlasterAccessUnparsed *
 * @param socketDataHolder is on the stack and does not need to be freed, the 'data' member is
 *        malloc()'d and must be freed by the caller.
 */
static void responseEvent(MsgRequestInfo *msgRequestInfoP, void /*SocketDataHolder*/ *socketDataHolder) {
   int retVal;
   SocketDataHolder *s = (SocketDataHolder *)socketDataHolder;
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)msgRequestInfoP->xa;

   if ((retVal = pthread_mutex_lock(&msgRequestInfoP->responseMutex)) != 0) {
      xa->log(xa->logUserP, xa->logLevel, LOG_ERROR, __FILE__, "Trying to lock responseMutex in responseEvent() failed %d", retVal);
      /* return; */
   }
   if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, "responseEvent() responseMutex is LOCKED");

   blobcpyAlloc(&msgRequestInfoP->responseBlob, s->blob.data, s->blob.dataLen);
   msgRequestInfoP->responseType = s->type;

   if ((retVal = pthread_cond_signal(&msgRequestInfoP->responseCond)) != 0) {
      xa->log(xa->logUserP, xa->logLevel, LOG_ERROR, __FILE__, "Trying to signal waiting thread in responseEvent() fails %d", retVal);
      /* return; */
   }

   if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__,
                                "responseEvent(requestId '%s', msgType=%c, dataLen=%d) occurred, wake up signal sent",
                                s->requestId, msgRequestInfoP->responseType, msgRequestInfoP->responseBlob.dataLen);

   if ((retVal = pthread_mutex_unlock(&msgRequestInfoP->responseMutex)) != 0) {
      xa->log(xa->logUserP, xa->logLevel, LOG_ERROR, __FILE__, "Trying to unlock responseMutex in responseEvent() failed %d", retVal);
      /* return; */
   }
   if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, "responseEvent() responseMutex is UNLOCKED");
}

/**
 * Callback function (wait for response) called directly after a message is sent. 
 * @param userP May not be NULL, is of type XmlBlasterAccessUnparsed *
 * @param msgRequestInfo Contains some informations about the request, may not be NULL
 * @param exception May not be NULL
 * @return The returned string from a request is written into msgRequestInfoP->data,
 *         the caller needs to free() it.
 */
static MsgRequestInfo *postSendEvent(MsgRequestInfo *msgRequestInfoP, XmlBlasterException *exception)
{
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)msgRequestInfoP->xa;
   struct timespec abstime;
   bool useTimeout = false;

   if (msgRequestInfoP->rollback) {
      mutexUnlock(msgRequestInfoP, exception);
      return (MsgRequestInfo *)0;
   }

   if (xa->responseTimeout > 0 && getAbsoluteTime(xa->responseTimeout, &abstime) == true) {
      useTimeout = true;
   }

   if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, "postSendEvent(requestId=%s) responseMutex is LOCKED, entering wait ...", msgRequestInfoP->requestIdStr);
   /* Wait for response, the callback server delivers it */
   while (msgRequestInfoP->responseType == 0) { /* Protect for spurious wake ups (e.g. by SIGUSR1) */
      if (useTimeout == true) {
         int error = pthread_cond_timedwait(&msgRequestInfoP->responseCond, &msgRequestInfoP->responseMutex, &abstime);
         if (error == ETIMEDOUT) {
            xa->callbackP->removeResponseListener(xa->callbackP, msgRequestInfoP->requestIdStr);
            strncpy0(exception->errorCode, "resource.exhaust", XMLBLASTEREXCEPTION_ERRORCODE_LEN); /* ErrorCode.RESOURCE_EXHAUST */
            SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%.100s:%d] Waiting on response for '%s()' with requestId=%s timed out after blocking %ld millis",
                    __FILE__, __LINE__, msgRequestInfoP->methodName, msgRequestInfoP->requestIdStr, xa->responseTimeout);
            if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, exception->message);
            return (MsgRequestInfo *)0;
         }
      }
      else {
         pthread_cond_wait(&msgRequestInfoP->responseCond, &msgRequestInfoP->responseMutex); /* Wakes up from responseEvent() */
         if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__,
            "Wake up tread, response of length %d arrived", msgRequestInfoP->responseBlob.dataLen);
      }
   }

   /* if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, "postSendEvent(requestId=%s) i woke up, entering unlock ...", msgRequestInfoP->requestIdStr); */
   if (mutexUnlock(msgRequestInfoP, exception) == false)
      return (MsgRequestInfo *)0;

   msgRequestInfoP->blob.dataLen = msgRequestInfoP->responseBlob.dataLen;
   msgRequestInfoP->blob.data = msgRequestInfoP->responseBlob.data;

   if (xa->logLevel>=LOG_TRACE)
      xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__,
         "Thread #%ld woke up in postSendEvent() for msgType=%c and dataLen=%d",
         msgRequestInfoP->requestIdStr, msgRequestInfoP->responseType, msgRequestInfoP->blob.dataLen);

   msgRequestInfoP->responseType = 0;
   msgRequestInfoP->responseBlob.dataLen = 0;
   msgRequestInfoP->responseBlob.data = 0; /* msgRequestInfoP->blob.data is now responsible to free() the data */
   
   return msgRequestInfoP;
}

/**
 * @param exception The exception struct, can be null
 * @return false on error, the exception struct is filled in this case and the lock is not released
 */
static bool mutexUnlock(MsgRequestInfo *msgRequestInfoP, XmlBlasterException *exception) {
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)msgRequestInfoP->xa;
   int retVal;
   if (msgRequestInfoP->responseMutexIsLocked == false)
      return true;
   msgRequestInfoP->responseMutexIsLocked = false;
   if ((retVal = pthread_mutex_unlock(&msgRequestInfoP->responseMutex)) != 0) {
      char embeddedText[XMLBLASTEREXCEPTION_MESSAGE_LEN];
      if (exception == 0) {
         return false;
      }
      if (*exception->errorCode != 0) {
         SNPRINTF(embeddedText, XMLBLASTEREXCEPTION_MESSAGE_LEN, "{%s:%s}", exception->errorCode, exception->message);
         if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, "Ignoring embedded exception %s: %s", exception->errorCode, exception->message);
      }
      else
         *embeddedText = 0;
      strncpy0(exception->errorCode, "user.internal", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%.100s:%d] ERROR trying to unlock responseMutex, return=%d. Embedded %s", __FILE__, __LINE__, retVal, embeddedText);
      if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, exception->message);
      return false;
   }
   if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, "postSendEvent() responseMutex is UNLOCKED");
   return true;
}

/**
 * @param usage Please pass a string with at least XMLBLASTER_MAX_USAGE_LEN chars allocated (or on stack)
 * @return Your usage pointer filled with informations
 */
Dll_Export const char *xmlBlasterAccessUnparsedUsage(char *usage)
{
   /* take care not to exceed XMLBLASTER_MAX_USAGE_LEN */
   SNPRINTF(usage, XMLBLASTER_MAX_USAGE_LEN, "%.950s%.950s%s", xmlBlasterConnectionUnparsedUsage(), callbackServerRawUsage(),
                  "\n   -plugin/socket/multiThreaded  [true]"
                  "\n                       If true the update() call to your client code is a separate thread."
                  "\n   -plugin/socket/responseTimeout  [60000 (one minute)]"
                  "\n                       The time in millis to wait on a response, 0 is forever.");
   
   return usage;
}

/**
 * Connect to the server. 
 * @param qos The QoS to connect, typically
 * <pre>
 *&lt;qos>
 * &lt;securityService type='htpasswd' version='1.0'>
 *   &lt;user>fritz&lt;/user>
 *   &lt;passwd>secret&lt;/passwd>
 * &lt;/securityService>
 *&lt;queue relating='callback' maxEntries='100' maxEntriesCache='100'>
 *  &lt;callback type='SOCKET' sessionId='%s'>
 *    socket://myServer.myCompany.com:6645
 *  &lt;/callback>
 *&lt;/queue>
 *&lt;/qos>
 * </pre>
 * @param clientUpdateFp The clients callback function pointer, if NULL our default handler is used
 * @param The exception struct, exception->errorCode is filled on exception
 * @return The ConnectReturnQos raw xml string, you need to free() it
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterConnect(XmlBlasterAccessUnparsed *xa, const char * const qos,
                               UpdateFp clientUpdateFp, XmlBlasterException *exception)
{
   char *response = 0;
   char *qos_;

   if (checkArgs(xa, "connect", false, exception) == false) return 0;

   /* Is allowed, we use our default handler in this case
   if (clientUpdateFp == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%.100s:%d] Please provide valid argument 'updateFp' to connect()", __FILE__, __LINE__);
      if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, exception->message);
      return false;
   }
   */

   if (qos == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%.100s:%d] Please provide valid argument 'qos' to connect()", __FILE__, __LINE__);
      if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, exception->message);
      return false;
   }

   if (initialize(xa, clientUpdateFp, exception) == false) {
      return false;
   }
   
   if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, "Invoking connect()");

   if (strstr(qos, "<callback") != 0) {
      /* User has given us a callback address */
      qos_ = strcpyAlloc(qos);
   }
   else {
      /* We add the callback sequence with our tunnel callback host and port
         HACK: This is error prone depending on the given qos */
      const char *pos;
      char callbackQos[1024];
      sprintf(callbackQos,
               "<queue relating='callback'>" /* maxEntries='100' maxEntriesCache='100'>" */
               "  <callback type='SOCKET' sessionId='%s'>"
               "    socket://%.120s:%d"
               "  </callback>"
               "</queue>",
               "NoCallbackSessionId", xa->callbackP->hostCB, xa->callbackP->portCB);
      qos_ = (char *)calloc(strlen(qos) + 1024, sizeof(char *));
      pos = strstr(qos, "</qos>");
      if (pos == 0) {
         strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
         SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%.100s:%d] Please provide valid 'qos' markup to connect()", __FILE__, __LINE__);
         if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, exception->message);
         return false;
      }
      strncpy0(qos_, qos, pos-qos+1);
      strcat(qos_, callbackQos);
      strcat(qos_, "</qos>");
   }
   if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, "Connecting with qos=%s", qos_);

   /* Register our function responseEvent() to be notified when the response arrives,
      this is done by preSendEvent() callback called during connect() */

   response = xa->connectionP->connect(xa->connectionP, qos_, exception);

   free(qos_);
   /* freeBlobHolderContent(&xa->responseBlob); */

   /* The response was handled by a callback to postSendEvent */

   if (response == 0) return response;

   if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__,
      "Got response for connect(secretSessionId=%s)", xa->connectionP->secretSessionId);
   return response;
}

/**
 * Disconnect from server. 
 * @param qos The QoS to disconnect
 * @param The exception struct, exception->errorCode is filled on exception
 * @return false on exception
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static bool xmlBlasterDisconnect(XmlBlasterAccessUnparsed *xa, const char * const qos, XmlBlasterException *exception)
{
   bool p;
   if (checkArgs(xa, "disconnect", true, exception) == false ) return 0;
   p = xa->connectionP->disconnect(xa->connectionP, qos, exception);
   return p;
}

/**
 * Publish a message to the server. 
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @see XmlBlasterConnectionUnparsed#publish() for a function documentation
 */
static char *xmlBlasterPublish(XmlBlasterAccessUnparsed *xa, MsgUnit *msgUnit, XmlBlasterException *exception)
{
   char *p;
   if (checkArgs(xa, "publish", true, exception) == false ) return 0;
   p = xa->connectionP->publish(xa->connectionP, msgUnit, exception);
   return p;
}

/**
 * Publish a message array in a bulk to the server. 
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @see XmlBlasterConnectionUnparsed#publishArr() for a function documentation
 */
static QosArr *xmlBlasterPublishArr(XmlBlasterAccessUnparsed *xa, MsgUnitArr *msgUnitArr, XmlBlasterException *exception)
{
   QosArr *p;
   if (checkArgs(xa, "publishArr", true, exception) == false ) return 0;
   p = xa->connectionP->publishArr(xa->connectionP, msgUnitArr, exception);
   return p;
}

/**
 * Publish a message array in a bulk to the server, we don't receive an ACK. 
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @see XmlBlasterConnectionUnparsed#publishOneway() for a function documentation
 */
static void xmlBlasterPublishOneway(XmlBlasterAccessUnparsed *xa, MsgUnitArr *msgUnitArr, XmlBlasterException *exception)
{
   if (checkArgs(xa, "publishOneway", true, exception) == false ) return;
   xa->connectionP->publishOneway(xa->connectionP, msgUnitArr, exception);
}

/**
 * Subscribe a message. 
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.subscribe.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterSubscribe(XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterException *exception)
{
   char *p;
   if (checkArgs(xa, "subscribe", true, exception) == false ) return 0;
   p = xa->connectionP->subscribe(xa->connectionP, key, qos, exception);
   return p;
}

/**
 * UnSubscribe a message from the server. 
 * @return The raw QoS XML strings returned from xmlBlaster, only NULL if an exception is thrown
 *         You need to free it with freeQosArr() after usage
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static QosArr *xmlBlasterUnSubscribe(XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterException *exception)
{
   QosArr *p;
   if (checkArgs(xa, "unSubscribe", true, exception) == false ) return 0;
   p = xa->connectionP->unSubscribe(xa->connectionP, key, qos, exception);
   return p;
}

/**
 * Erase a message from the server. 
 * @return A struct holding the raw QoS XML strings returned from xmlBlaster,
 *         only NULL if an exception is thrown.
 *         You need to freeQosArr() it
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.erase.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static QosArr *xmlBlasterErase(XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterException *exception)
{
   QosArr *p;
   if (checkArgs(xa, "erase", true, exception) == false ) return 0;
   p = xa->connectionP->erase(xa->connectionP, key, qos, exception);
   return p;
}

/**
 * Ping the server. 
 * @param qos The QoS or 0
 * @param exception *errorCode!=0 on failure
 * @return The ping return QoS raw xml string, you need to free() it
 *         or 0 on failure (in which case *exception.errorCode!='\0')
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterPing(XmlBlasterAccessUnparsed *xa, const char * const qos, XmlBlasterException *exception)
{
   char *p;
   if (checkArgs(xa, "ping", true, exception) == false ) return 0;
   p = xa->connectionP->ping(xa->connectionP, qos, exception);
   return p;
}

/**
 * Get a message. 
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.get.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @return NULL on error, please check exception in such a case
 */
static MsgUnitArr *xmlBlasterGet(XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterException *exception)
{
   MsgUnitArr *msgUnitArr;
   if (checkArgs(xa, "get", true, exception) == false ) return 0;
   msgUnitArr = xa->connectionP->get(xa->connectionP, key, qos, exception);
   return msgUnitArr;
}

static bool checkArgs(XmlBlasterAccessUnparsed *xa, const char *methodName,
            bool checkIsConnected, XmlBlasterException *exception)
{
   if (xa == 0) {
      char *stack = getStackTrace(10);
      if (exception == 0) {
         printf("[%s:%d] Please provide a valid XmlBlasterAccessUnparsed pointer to %s() %s",
                  __FILE__, __LINE__, methodName, stack);
      }
      else {
         strncpy0(exception->errorCode, "user.illegalArgument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
         SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
                  "[%.100s:%d] Please provide a valid XmlBlasterAccessUnparsed pointer to %.16s() %s",
                   __FILE__, __LINE__, methodName, stack);
         xa->log(xa->logUserP, xa->logLevel, LOG_ERROR, __FILE__, exception->message);
      }
      free(stack);
      return false;
   }

   if (exception == 0) {
      char *stack = getStackTrace(10);
      xa->log(xa->logUserP, xa->logLevel, LOG_ERROR, __FILE__, "[%s:%d] Please provide valid exception pointer to %s() %s",
              __FILE__, __LINE__, methodName, stack);
      free(stack);
      return false;
   }

   if (checkIsConnected && !xa->isConnected(xa)) {
      char *stack = getStackTrace(10);
      strncpy0(exception->errorCode, "communication.noConnection", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
               "[%.100s:%d] Not connected to xmlBlaster, %s() failed %s",
                __FILE__, __LINE__, methodName, stack);
      free(stack);
      xa->log(xa->logUserP, xa->logLevel, LOG_WARN, __FILE__, exception->message);
      return false;
   }

   initializeXmlBlasterException(exception);

   return true;
}

/**
 * Run by the new created thread, calls the clients update method. 
 * Leaving this pthread start routine does an implicit pthread_exit().
 * @param container Holding all necessary informations, we free it when we are done
 * @return 0 on success, 1 on error. The return value is the exit value returned by pthread_join()
 */
static bool runUpdate(UpdateContainer *container)
{
   XmlBlasterAccessUnparsed *xa = container->xa;
   MsgUnitArr *msgUnitArrP = container->msgUnitArrP;
   void *userData = container->userData;
   CallbackServerUnparsed *cb = (CallbackServerUnparsed*)userData;
   XmlBlasterException *exception = &container->exception;
   SocketDataHolder *socketDataHolder = &container->socketDataHolder;
   bool retVal;

   if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, "Entering runUpdate()");

   retVal = xa->clientsUpdateFp(msgUnitArrP, xa, exception);

   if (xa->lowLevelAutoAck) { /* returned already */
   }
   else {
      cb->sendResponseOrException(retVal, cb, socketDataHolder, msgUnitArrP, exception);
   }

   free(container);

   if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__,
                                "runUpdate: Update thread 0x%x is exiting", (int)pthread_self());
   xa->threadCounter--;
   return (retVal==true) ? 0 : 1;
}

/**
 * Here we receive the callback messages from xmlBlaster, create a thread and dispatch
 * it to the clients update. 
 * @see UpdateFp in CallbackServerUnparsed.h
 */
static void interceptUpdate(MsgUnitArr *msgUnitArrP, void *userData,
                            XmlBlasterException *exception, void /*SocketDataHolder*/ *socketDataHolder)
{
   CallbackServerUnparsed *cb = (CallbackServerUnparsed*)userData;
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)cb->updateCbUserData;

   if (xa->clientsUpdateFp == 0) { /* Client has not registered an update() */
      size_t i;
      bool testException = false;
      bool success = true;

      for (i=0; i<msgUnitArrP->len; i++) {
         const char *key = msgUnitArrP->msgUnitArr[i].key;
         xa->log(xa->logUserP, xa->logLevel, LOG_INFO, __FILE__,
             "CALLBACK update() default handler: Asynchronous message update arrived:%s id=%s, we ignore it in this default handler\n",
             key, ((SocketDataHolder*)socketDataHolder)->requestId);
         msgUnitArrP->msgUnitArr[i].responseQos = strcpyAlloc("<qos><state id='OK'/></qos>");
         /* Return QoS: Everything is OK */
      }
      if (testException) {
         strncpy0(exception->errorCode, "user.clientCode",
                  XMLBLASTEREXCEPTION_ERRORCODE_LEN);
         strncpy0(exception->message, "I don't want these messages",
                  XMLBLASTEREXCEPTION_MESSAGE_LEN);
         success = false;
      }
      cb->sendResponseOrException(success, cb, socketDataHolder, msgUnitArrP, exception);
      return;
   }

   if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__, "interceptUpdate(): Received message");

   if (xa->callbackMultiThreaded == false) {
      bool ret = xa->clientsUpdateFp(msgUnitArrP, xa, exception);
      cb->sendResponseOrException(ret, cb, socketDataHolder, msgUnitArrP, exception);
      return;
   }

   {
      pthread_t tid;
      int threadRet = 0;
      UpdateContainer *container = (UpdateContainer*)malloc(sizeof(UpdateContainer));
      pthread_attr_t attr;

      pthread_attr_init(&attr);
      /* Cleanup all resources after ending the thread, instead of calling pthread_join() */
      pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
      
      container->xa = xa;
      container->msgUnitArrP = msgUnitArrP;
      container->userData = userData;
      memcpy(&container->exception, exception, sizeof(XmlBlasterException));
      memcpy(&container->socketDataHolder, socketDataHolder, sizeof(SocketDataHolder)); /* The blob pointer is freed already by CallbackServerUnparsed */

      if (xa->lowLevelAutoAck) {
         size_t i;
         for (i=0; i<msgUnitArrP->len; i++) {
            msgUnitArrP->msgUnitArr[i].responseQos = strcpyAlloc("<qos><state id='OK'/></qos>");
         }
      }
      /* this thread will deliver the update message to the client code,
         Note: we need a thread pool cache for better performance */
      xa->threadCounter++;
      threadRet = pthread_create(&tid, &attr,
                        (void * (*)(void *))runUpdate, (void *)container);
      if (threadRet != 0) {
         bool ret = false;
         free(container);
         strncpy0(exception->errorCode, "resource.tooManyThreads", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
         SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
                  "[%.100s:%d] Creating thread failed with error number %d, we deliver the message in the same thread",
                  __FILE__, __LINE__, threadRet);
         xa->log(xa->logUserP, xa->logLevel, LOG_ERROR, __FILE__, exception->message);
         ret = xa->clientsUpdateFp(msgUnitArrP, xa, exception);
         cb->sendResponseOrException(ret, cb, socketDataHolder, msgUnitArrP, exception);
         xa->threadCounter--;
         return;
      }

      if (xa->logLevel>=LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, LOG_TRACE, __FILE__,
         "interceptUpdate: Received message and delegated it to a separate thread 0x%x to deliver", (int)tid);

      pthread_attr_destroy (&attr);
   }

   if (xa->lowLevelAutoAck) {
      *exception->errorCode = 0;
      cb->sendResponseOrException(true, cb, socketDataHolder, msgUnitArrP, exception);
   }
}

#ifdef XmlBlasterAccessUnparsedMain /* compile a standalone test program */

/**
 * Here we receive the callback messages from xmlBlaster
 * FOR TESTING ONLY
 * @see UpdateFp in CallbackServerUnparsed.h
 */
static bool myUpdate(MsgUnitArr *msgUnitArrP, void *userData, XmlBlasterException *xmlBlasterException)
{
   size_t i;
   bool testException = false;
   if (userData != 0) ; /* to avoid compiler warning (we don't need it here) */
   for (i=0; i<msgUnitArrP->len; i++) {
      char *xml = messageUnitToXml(&msgUnitArrP->msgUnitArr[i]);
      printf("[client] CALLBACK update(): Asynchronous message update arrived:%s\n", xml);
      free(xml);
      msgUnitArrP->msgUnitArr[i].responseQos = strcpyAlloc("<qos><state id='OK'/></qos>");
      /* Return QoS: Everything is OK */
   }
   if (testException) {
      strncpy0(xmlBlasterException->errorCode, "user.clientCode",
               XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      strncpy0(xmlBlasterException->message, "I don't want these messages",
               XMLBLASTEREXCEPTION_MESSAGE_LEN);
      return false;
   }
   return true;
}

/**
 * Invoke: XmlBlasterAccessUnparsedMain -logLevel TRACE  -numTests 10
 */
int main(int argc, char** argv)
{
   int ii;
   int numTests = 1;
   bool testCallInitialize = false;

   for (ii=0; ii < argc-1; ii++)
      if (strcmp(argv[ii], "-numTests") == 0) {
         if (sscanf(argv[++ii], "%d", &numTests) != 1)
            printf("[XmlBlasterAccessUnparsed] WARN '-numTests %s' is invalid\n", argv[ii]);
      }

   for (ii=0; ii<numTests; ii++) {
      int iarg;
      char *response = (char *)0;
      /*
       * callbackSessionId:
       * Is created by the client and used to validate callback messages in update. 
       * This is sent on connect in ConnectQos.
       * (Is different from the xmlBlaster secret session ID)
       */
      const char *callbackSessionId = "topSecret";
      XmlBlasterException xmlBlasterException;
      XmlBlasterAccessUnparsed *xa = 0;

      /*
      const char *tmp = getStackTrace(20);
      printf("[client] stackTrace=%s\n", tmp);
      free(tmp);
      */

#     ifdef PTHREAD_THREADS_MAX
         printf("[client] Try option '-help' if you need usage informations, max %d"
                " threads per process are supported on this OS\n", PTHREAD_THREADS_MAX);
#     else
         printf("[client] Try option '-help' if you need usage informations\n");
#     endif

      for (iarg=0; iarg < argc; iarg++) {
         if (strcmp(argv[iarg], "-help") == 0 || strcmp(argv[iarg], "--help") == 0) {
            char usage[XMLBLASTER_MAX_USAGE_LEN];
            const char *pp =
            "\n   -logLevel            ERROR | WARN | INFO | TRACE | DUMP [WARN]"
            "\n   -numTests            How often to run the same tests [1]"
            "\n\nExample:"
            "\n   XmlBlasterAccessUnparsedMain -logLevel TRACE"
                 " -dispatch/connection/plugin/socket/hostname server.mars.universe";
            printf("Usage:\nXmlBlaster C SOCKET client %s\n%s%s\n",
                   getXmlBlasterVersion(), xmlBlasterAccessUnparsedUsage(usage), pp);
            exit(1);
         }
      }

      xa = getXmlBlasterAccessUnparsed(argc, argv);

      if (testCallInitialize) {
         if (xa->initialize(xa, myUpdate, &xmlBlasterException) == false) {
            printf("[client] Connection to xmlBlaster failed,"
                   " please start the server or check your configuration\n");
            freeXmlBlasterAccessUnparsed(xa);
            exit(1);
         }
      }

      {  /* connect */
         char connectQos[2048];
         char callbackQos[1024];

         if (testCallInitialize) {
            SNPRINTF(callbackQos, 1024,
                     "<queue relating='callback' maxEntries='100' maxEntriesCache='100'>"
                     "  <callback type='SOCKET' sessionId='%s'>"
                     "    socket://%.120s:%d"
                     "  </callback>"
                     "</queue>",
                     callbackSessionId, xa->callbackP->hostCB, xa->callbackP->portCB);
         }
         else
            *callbackQos = '\0';
         
         SNPRINTF(connectQos, 2048,
                "<qos>"
                " <securityService type='htpasswd' version='1.0'>"
                "  <![CDATA["
                "   <user>fritz</user>"
                "   <passwd>secret</passwd>"
                "  ]]>"
                " </securityService>"
                "%.1024s"
                "</qos>", callbackQos);

         response = xa->connect(xa, connectQos, myUpdate, &xmlBlasterException);
         if (*xmlBlasterException.errorCode != 0) {
            printf("[client] Caught exception during connect errorCode=%s, message=%s\n",
                   xmlBlasterException.errorCode, xmlBlasterException.message);
            freeXmlBlasterAccessUnparsed(xa);
            exit(1);
         }
         free(response);
         printf("[client] Connected to xmlBlaster, do some tests ...\n");
      }

      response = xa->ping(xa, 0, &xmlBlasterException);
      if (response == (char *)0) {
         printf("[client] ERROR: Pinging a connected server failed: errorCode=%s, message=%s\n",
            xmlBlasterException.errorCode, xmlBlasterException.message);
      }
      else {
         printf("[client] Pinging a connected server, response=%s\n", response);
         free(response);
      }

      { /* subscribe ... */
         const char *key = "<key oid='HelloWorld'/>";
         const char *qos = "<qos/>";
         printf("[client] Subscribe message 'HelloWorld' ...\n");
         response = xa->subscribe(xa, key, qos, &xmlBlasterException);
         if (*xmlBlasterException.errorCode != 0) {
            printf("[client] Caught exception in subscribe errorCode=%s, message=%s\n",
                   xmlBlasterException.errorCode, xmlBlasterException.message);
            xa->disconnect(xa, 0, &xmlBlasterException);
            freeXmlBlasterAccessUnparsed(xa);
            exit(1);
         }
         printf("[client] Subscribe success, returned status is '%s'\n", response);
         free(response);
      }

      {  /* publish ... */
         MsgUnit msgUnit;
         printf("[client] Publishing message 'HelloWorld' ...\n");
         msgUnit.key = strcpyAlloc("<key oid='HelloWorld'/>");
         msgUnit.content = strcpyAlloc("Some message payload");
         msgUnit.contentLen = strlen(msgUnit.content);
         msgUnit.qos =strcpyAlloc("<qos><persistent/></qos>");
         response = xa->publish(xa, &msgUnit, &xmlBlasterException);
         freeMsgUnitData(&msgUnit);
         if (*xmlBlasterException.errorCode != 0) {
            printf("[client] Caught exception in publish errorCode=%s, message=%s\n",
                   xmlBlasterException.errorCode, xmlBlasterException.message);
            xa->disconnect(xa, 0, &xmlBlasterException);
            freeXmlBlasterAccessUnparsed(xa);
            exit(1);
         }
         printf("[client] Publish success, returned status is '%s'\n", response);
         free(response);
      }

      {  /* unSubscribe ... */
         const char *key = "<key oid='HelloWorld'/>";
         const char *qos = "<qos/>";
         printf("[client] UnSubscribe message 'HelloWorld' ...\n");
         response = xa->unSubscribe(xa, key, qos, &xmlBlasterException);
         if (response) {
            printf("[client] Unsubscribe success, returned status is '%s'\n", response);
            free(response);
         }
         else {
            printf("[client] Caught exception in unSubscribe errorCode=%s, message=%s\n",
                   xmlBlasterException.errorCode, xmlBlasterException.message);
            xa->disconnect(xa, 0, &xmlBlasterException);
            freeXmlBlasterAccessUnparsed(xa);
            exit(1);
         }
      }

      {  /* get synchnronous ... */
         size_t i;
         const char *key = "<key queryType='XPATH'>//key</key>";
         const char *qos = "<qos/>";
         MsgUnitArr *msgUnitArr;
         printf("[client] Get synchronous messages with XPath '//key' ...\n");
         msgUnitArr = xa->get(xa, key, qos, &xmlBlasterException);
         if (*xmlBlasterException.errorCode != 0) {
            printf("[client] Caught exception in get errorCode=%s, message=%s\n",
                   xmlBlasterException.errorCode, xmlBlasterException.message);
            xa->disconnect(xa, 0, &xmlBlasterException);
            freeXmlBlasterAccessUnparsed(xa);
            exit(1);
         }
         if (msgUnitArr != (MsgUnitArr *)0) {
            for (i=0; i<msgUnitArr->len; i++) {
               char *contentStr = strFromBlobAlloc(msgUnitArr->msgUnitArr[i].content,
                                                msgUnitArr->msgUnitArr[i].contentLen);
               const char *dots = (msgUnitArr->msgUnitArr[i].contentLen > 96) ?
                                  " ..." : "";
               printf("\n[client] Received message#%u/%u:\n"
                      "-------------------------------------"
                      "%s\n <content>%.100s%s</content>%s\n"
                      "-------------------------------------\n",
                      i+1, msgUnitArr->len,
                      msgUnitArr->msgUnitArr[i].key,
                      contentStr, dots,
                      msgUnitArr->msgUnitArr[i].qos);
               free(contentStr);
            }
            freeMsgUnitArr(msgUnitArr);
         }
         else {
            printf("[client] Caught exception in get errorCode=%s, message=%s\n",
                   xmlBlasterException.errorCode, xmlBlasterException.message);
            xa->disconnect(xa, 0, &xmlBlasterException);
            freeXmlBlasterAccessUnparsed(xa);
            exit(1);
         }
      }


      {  /* erase ... */
         const char *key = "<key oid='HelloWorld'/>";
         const char *qos = "<qos/>";
         printf("[client] Erasing message 'HelloWorld' ...\n");
         response = xa->erase(xa, key, qos, &xmlBlasterException);
         if (*xmlBlasterException.errorCode != 0) {
            printf("[client] Caught exception in erase errorCode=%s, message=%s\n",
                   xmlBlasterException.errorCode, xmlBlasterException.message);
            xa->disconnect(xa, 0, &xmlBlasterException);
            freeXmlBlasterAccessUnparsed(xa);
            exit(1);
         }
         printf("[client] Erase success, returned status is '%s'\n", response);
         free(response);
      }

      if (xa->disconnect(xa, 0, &xmlBlasterException) == false) {
         printf("[client] Caught exception in disconnect, errorCode=%s, message=%s\n",
                xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterAccessUnparsed(xa);
         exit(1);
      }

      freeXmlBlasterAccessUnparsed(xa);
      if (numTests > 1) {
         printf("[client] Successfully finished test #%d from %d\n\n", ii, numTests);
      }
   }
   printf("[client] Good bye.\n");
   return 0; /*exit(0);*/
}
#endif /* #ifdef XmlBlasterAccessUnparsedMain */

