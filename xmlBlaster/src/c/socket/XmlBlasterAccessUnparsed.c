/*----------------------------------------------------------------------------
Name:      XmlBlasterAccessUnparsed.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Wraps raw socket connection to xmlBlaster
           Implements sync connection and async callback
           Needs pthread to compile (multi threading).
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:
  LINUX:   gcc -DXmlBlasterAccessUnparsedMain -Wall -pedantic -g -D_REENTRANT -I.. -o XmlBlasterAccessUnparsedMain XmlBlasterAccessUnparsed.c ../msgUtil.c xmlBlasterSocket.c XmlBlasterConnectionUnparsed.c CallbackServerUnparsed.c -lpthread
  WIN:     cl /MT /W4 -DXmlBlasterAccessUnparsedMain -D_WINDOWS -I.. -I../pthreads /FeXmlBlasterAccessUnparsedMain.exe  XmlBlasterAccessUnparsed.c ..\msgUtil.c xmlBlasterSocket.c XmlBlasterConnectionUnparsed.c CallbackServerUnparsed.c ws2_32.lib pthreadVC.lib
           (download pthread for Windows and WinCE from http://sources.redhat.com/pthreads-win32)
  Solaris: cc  -DXmlBlasterAccessUnparsedMain -v -Xc -g -D_REENTRANT -I.. -o XmlBlasterAccessUnparsedMain XmlBlasterAccessUnparsed.c ../msgUtil.c xmlBlasterSocket.c XmlBlasterConnectionUnparsed.c CallbackServerUnparsed.c -lpthread -lsocket -lnsl
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <errno.h>
#include <sys/types.h>
#include <socket/xmlBlasterSocket.h>
#include <XmlBlasterAccessUnparsed.h>

#ifdef _WINDOWS
#  define ssize_t signed int
#else
#  include <unistd.h> /* sleep(), only used in main */
#  include <sys/time.h> /* gettimeofday() */
#endif

#define  MICRO_SECS_PER_SECOND 1000000
#define  NANO_SECS_PER_SECOND MICRO_SECS_PER_SECOND * 1000

static bool initialize(XmlBlasterAccessUnparsed *xa, UpdateFp update);
static char *xmlBlasterConnect(XmlBlasterAccessUnparsed *xa, const char * const qos, UpdateFp update, XmlBlasterException *exception);
static bool xmlBlasterDisconnect(XmlBlasterAccessUnparsed *xa, const char * const qos, XmlBlasterException *exception);
static char *xmlBlasterPublish(XmlBlasterAccessUnparsed *xa, MsgUnit *msgUnit, XmlBlasterException *exception);
static char *xmlBlasterSubscribe(XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterException *exception);
static char *xmlBlasterUnSubscribe(XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterException *exception);
static char *xmlBlasterErase(XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterException *exception);
static MsgUnitArr *xmlBlasterGet(XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterException *exception);
static char *xmlBlasterPing(XmlBlasterAccessUnparsed *xa, const char * const qos);
static bool isConnected(XmlBlasterAccessUnparsed *xa);
static void responseEvent(void /*XmlBlasterAccessUnparsed*/ *userP, void /*SocketDataHolder*/ *socketDataHolder);
static MsgRequestInfo *preSendEvent(void /*XmlBlasterAccessUnparsed*/ *userP, MsgRequestInfo *msgRequestInfo, XmlBlasterException *exception);
static MsgRequestInfo *postSendEvent(void /*XmlBlasterAccessUnparsed*/ *userP, MsgRequestInfo *msgRequestInfo, XmlBlasterException *exception);
static bool getAbsoluteTime(XmlBlasterAccessUnparsed *xa, struct timespec *abstime);

/**
 * Create an instance for access xmlBlaster. 
 * This is usually the first call of a client.
 * @return NULL if bootstrapping failed. If not NULL you need to free() it when you are done
 * usually by calling freeXmlBlasterAccessUnparsed().
 */
XmlBlasterAccessUnparsed *getXmlBlasterAccessUnparsed(int argc, char** argv) {
   int iarg;
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)calloc(1, sizeof(XmlBlasterAccessUnparsed));
   xa->argc = argc;
   xa->argv = argv;
   xa->isInitialized = false;
   xa->connect = xmlBlasterConnect;
   xa->initialize = initialize;
   xa->disconnect = xmlBlasterDisconnect;
   xa->publish = xmlBlasterPublish;
   xa->subscribe = xmlBlasterSubscribe;
   xa->unSubscribe = xmlBlasterUnSubscribe;
   xa->erase = xmlBlasterErase;
   xa->get = xmlBlasterGet;
   xa->ping = xmlBlasterPing;
   xa->isConnected = isConnected;
   xa->debug = false;
   xa->responseTimeout = 60000; /* One minute (given in millis) */
   memset(&xa->responseBlob, 0, sizeof(XmlBlasterBlob));
   xa->responseType = 0;
   xa->callbackThreadId = 0;
#  ifdef _WINDOWS
   xa->responseMutex = PTHREAD_MUTEX_INITIALIZER;
   xa->responseCond = PTHREAD_COND_INITIALIZER;
#  else
   /* On Linux gcc & SUN CC:
        "parse error before '{' token"
      when initializing directly, so we do a hack here:
      (NOTE: using pthread_cond_init() would be the choice to
      initialize but this only works with dynamic conds)
   */
   {
      pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
      xa->responseMutex = mutex;
   }
   {
      pthread_cond_t cond = PTHREAD_COND_INITIALIZER;
      xa->responseCond = cond;
   }
#  endif

   for (iarg=0; iarg < argc-1; iarg++) {
      if (strcmp(argv[iarg], "-debug") == 0)
         xa->debug = !strcmp(argv[++iarg], "true");
      else if (strcmp(argv[iarg], "-plugin/socket/responseTimeout") == 0)
         sscanf(argv[++iarg], "%ld", &xa->responseTimeout);
   }
   for (iarg=0; iarg < argc-1; iarg++) {
      if (strcmp(argv[iarg], "-dispatch/connection/plugin/socket/responseTimeout") == 0)
         sscanf(argv[++iarg], "%ld", &xa->responseTimeout);
   }
   if (xa->debug) printf("[XmlBlasterAccessUnparsed] Created handle: -debug=%d -plugin/socket/responseTimeout=%ld\n",
                         xa->debug, xa->responseTimeout);
   return xa;
}

void freeXmlBlasterAccessUnparsed(XmlBlasterAccessUnparsed *xa)
{
   int retVal;

   if (xa->debug) printf("[XmlBlasterAccessUnparsed] freeXmlBlasterAccessUnparsed()\n");
   if (xa->connectionP != 0) {
      freeXmlBlasterConnectionUnparsed(xa->connectionP);
      xa->connectionP = 0;
   }
   if (xa->callbackP != 0) {
      xa->callbackP->shutdown(xa->callbackP);
      if (xa->callbackThreadId != 0) {
         retVal = pthread_join(xa->callbackThreadId, 0);
         if (retVal != 0) {
            printf("[XmlBlasterAccessUnparsed] join problem return value is %d\n", retVal);
         }
         else {
            if (xa->debug) printf("[XmlBlasterAccessUnparsed] pthread_join(id=%ld) succeded\n", xa->callbackThreadId);
         }
         xa->callbackThreadId = 0;
      }
      freeCallbackServerUnparsed(xa->callbackP);
      xa->callbackP = 0;
   }

   free(xa);
}

/**
 * Creates client side connection object and the callback server. 
 * This method is automatically called by connect() so you usually only
 * call it explicitly if you are interested in the callback server settings.
 * @return true on success
 */
static bool initialize(XmlBlasterAccessUnparsed *xa, UpdateFp updateFp)
{
   int threadRet = 0;

   if (xa->isInitialized) {
      return true;
   }

   xa->connectionP = getXmlBlasterConnectionUnparsed(xa->argc, xa->argv);
   if (xa->connectionP == 0) {
      return false;
   }
   if (xa->connectionP->initConnection(xa->connectionP) == false) /* Establish low level TCP/IP connection */
      return false;

   xa->callbackP = getCallbackServerUnparsed(xa->argc, xa->argv, updateFp);
   if (xa->callbackP == 0) {
      freeXmlBlasterConnectionUnparsed(xa->connectionP);
      return false;
   }

   if (xa->debug) printf("[client] Created CallbackServerUnparsed instance, creating on a separate thread a listener on socket:/*%s:%d...\n",
          xa->callbackP->hostCB, xa->callbackP->portCB);

   xa->callbackP->useThisSocket(xa->callbackP, xa->connectionP->socketToXmlBlaster);

   /* Register our callback funtion which is called just before sending a message */
   xa->connectionP->preSendEvent = preSendEvent;
   xa->connectionP->preSendEvent_userP = xa;

   /* Register our callback funtion which is called just after sending a message */
   xa->connectionP->postSendEvent = postSendEvent;
   xa->connectionP->postSendEvent_userP = xa;

   /* thread blocks on socket listener */
   threadRet = pthread_create(&xa->callbackThreadId, (const pthread_attr_t *)0, (void * (*)(void *))xa->callbackP->runCallbackServer, (void *)xa->callbackP);
   if (threadRet != 0) {
      printf("[XmlBlasterAccessUnparsed] ERROR: Creating thread failed with error number %d", threadRet);
      freeCallbackServerUnparsed(xa->callbackP);
      freeXmlBlasterConnectionUnparsed(xa->connectionP);
      return false;
   }

   xa->isInitialized = true;
   if (xa->debug) printf("[XmlBlasterAccessUnparsed] initialize() successful\n");
   return xa->isInitialized;
}

static bool isConnected(XmlBlasterAccessUnparsed *xa)
{
   if (xa->connectionP == 0) {
      return false;
   }
   return xa->connectionP->isConnected(xa->connectionP);
}

/**
 * Callback from XmlBlasterConnectionInparsed just before a message is sent,
 * the msgRequestInfo contains the requestId used.
 * @param msgRequestInfo Contains some informations about the request
 * @return The same (or a manipulated/encrypted) msgRequestInfo, if NULL the exception is filled. 
 *         If msgRequestInfo->blob.data was changed and malloc()'d by you, the caller will free() it.
 *         If you return NULL you need to call removeResponseListener() to avoid a memory leak.
 */
static MsgRequestInfo *preSendEvent(void *userP, MsgRequestInfo *msgRequestInfo, XmlBlasterException *exception)
{
   bool retVal;
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)userP;

   if (xa->debug) printf("[XmlBlasterAccessUnparsed] preSendEvent(%s) occurred\n", msgRequestInfo->methodName);
   retVal = xa->callbackP->addResponseListener(xa->callbackP, xa, msgRequestInfo->requestIdStr, responseEvent);
   if (retVal == false) {
      strncpy0(exception->errorCode, "user.internal", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterConnectionUnparsed] Couldn't register as response listener");
      if (xa->debug) { printf(exception->message); printf("\n"); }
      return (MsgRequestInfo *)0;
   }

   return msgRequestInfo;
}

/**
 * This function is called by the callback server when a response message arrived (after we send a request). 
 * The xa->responseBlob->data is malloc()'d with the response string, you need to free it. 
 * @param socketDataHolder_ is on the stack and does not need to be freed, the 'data' member is
 *        malloc()'d but will be freed by the caller.
 */
static void responseEvent(void *userP, void /*SocketDataHolder*/ *socketDataHolder) {
   int retVal;
   SocketDataHolder *s = (SocketDataHolder *)socketDataHolder;
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)userP;

   blobcpyAlloc(&xa->responseBlob, s->blob.data, s->blob.dataLen);
   xa->responseType = s->type;
   if (xa->debug) printf("[XmlBlasterAccessUnparsed] responseEvent(msgType=%c, dataLen=%d) occured\n",
                         xa->responseType, xa->responseBlob.dataLen);

   if ((retVal = pthread_mutex_lock(&xa->responseMutex)) != 0) {
      printf("[XmlBlasterAccessUnparsed] ERROR trying to lock responseMutex in responseEvent() %d\n", retVal);
      /* return; */
   }

   if ((retVal = pthread_cond_signal(&xa->responseCond)) != 0) {
      printf("[XmlBlasterAccessUnparsed] ERROR trying to signal waiting thread in responseEvent() %d\n", retVal);
      /* return; */
   }

   if ((retVal = pthread_mutex_unlock(&xa->responseMutex)) != 0) {
      printf("[XmlBlasterAccessUnparsed] ERROR trying to unlock responseMutex in responseEvent() %d\n", retVal);
      /* return; */
   }
}

/**
 * Callback function (wait for response) called directly after a message is sent. 
 * @return The returned string from a request is written into msgRequestInfo->data,
 *         the caller needs to free() it.
 */
static MsgRequestInfo *postSendEvent(void *userP, MsgRequestInfo *msgRequestInfo, XmlBlasterException *exception)
{
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)userP;
   int retVal;
   struct timespec abstime;
   bool useTimeout = false;

   if (xa->debug) printf("[XmlBlasterAccessUnparsed] postSendEvent(requestId=%s, xa->responseBlob.dataLen=%d)\n",
                  msgRequestInfo->requestIdStr, xa->responseBlob.dataLen);
   if ((retVal = pthread_mutex_lock(&xa->responseMutex)) != 0) {
      strncpy0(exception->errorCode, "user.internal", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterAccessUnparsed] ERROR trying to lock responseMutex %d\n", retVal);
      if (xa->debug) { printf(exception->message); printf("\n"); }
      return (MsgRequestInfo *)0;
   }
   
   if (xa->responseTimeout > 0) {
      if (getAbsoluteTime(xa, &abstime) == true) {
         useTimeout = true;
      }
   }

   /* Wait for response, the callback server delivers it */
   while (xa->responseBlob.dataLen == 0) { /* Protect for spurious wake ups (e.g. by SIGUSR1) */
      if (useTimeout == true) {
         int error = pthread_cond_timedwait(&xa->responseCond, &xa->responseMutex, &abstime);
         if (error == ETIMEDOUT) {
            xa->callbackP->removeResponseListener(xa->callbackP, msgRequestInfo->requestIdStr);
            strncpy0(exception->errorCode, "resource.exhaust", XMLBLASTEREXCEPTION_ERRORCODE_LEN); /* ErrorCode.RESOURCE_EXHAUST */
            sprintf(exception->message, "[XmlBlasterAccessUnparsed] Waiting on response for '%s()' with requestId=%s timed out after blocking %ld millis",
                                          msgRequestInfo->methodName, msgRequestInfo->requestIdStr, xa->responseTimeout);
            if (xa->debug) { printf(exception->message); printf("\n"); }
            return (MsgRequestInfo *)0;
         }
      }
      else {
         pthread_cond_wait(&xa->responseCond, &xa->responseMutex); /* Wakes up from responseEvent() */
         if (xa->debug) printf("[XmlBlasterAccessUnparsed] Wake up tread, response of length %d arrived\n", xa->responseBlob.dataLen);
      }
   }

   if ((retVal = pthread_mutex_unlock(&xa->responseMutex)) != 0) {
      strncpy0(exception->errorCode, "user.internal", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterAccessUnparsed] ERROR trying to unlock responseMutex %d\n", retVal);
      if (xa->debug) { printf(exception->message); printf("\n"); }
      return (MsgRequestInfo *)0;
   }

   msgRequestInfo->responseType = xa->responseType;
   msgRequestInfo->blob.dataLen = xa->responseBlob.dataLen;
   msgRequestInfo->blob.data = xa->responseBlob.data;

   if (xa->debug) printf("[XmlBlasterAccessUnparsed] Thread wake up in postSendEvent() for msgType=%c and dataLen=%d\n",
                         msgRequestInfo->responseType, msgRequestInfo->blob.dataLen);

   xa->responseType = 0;
   xa->responseBlob.dataLen = 0;
   xa->responseBlob.data = 0; /* msgRequestInfo->blob.data is now responsible to free() the data */
   
   return msgRequestInfo;
}

/**
 * @param usage Please pass a string with at least XMLBLASTER_MAX_USAGE_LEN chars allocated (or on stack)
 * @return Your usage pointer filled with informations
 */
const char *xmlBlasterAccessUnparsedUsage(char *usage)
{
   /* take care not to exceed XMLBLASTER_MAX_USAGE_LEN */
   sprintf(usage, "%.950s%.950s%s", xmlBlasterConnectionUnparsedUsage(), callbackServerRawUsage(),
                  "\n  -plugin/socket/responseTimeout  [60000 (one minute)]"
                  "\n                       The time in millis to wait on a response, 0 is forever");
   
   return usage;
}

/**
 * Connect to the server. 
 * @param qos The QoS to connect
 * @param updateFp The callback function pointer, if NULL an exception is thrown
 * @param The exception struct, exception->errorCode is filled on exception
 * @return The ConnectReturnQos raw xml string, you need to free() it
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterConnect(XmlBlasterAccessUnparsed *xa, const char * const qos, UpdateFp updateFp, XmlBlasterException *exception)
{
   char *response = 0;

   if (updateFp == 0 || exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterAccessUnparsed] Please provide valid arguments to xmlBlasterConnect()");
      if (xa->debug) { printf(exception->message); printf("\n"); }
      return false;
   }

   if (initialize(xa, updateFp) == false) {
      strncpy0(exception->errorCode, "user.notConnected", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterConnectionUnparsed] No connection to xmlBlaster\n");
      if (xa->debug) { printf(exception->message); printf("\n"); }
      return false;
   }
   
   if (xa->debug) printf("[XmlBlasterAccessUnparsed] Invoking connect()\n");

   /* Register our function responseEvent() to be notified when the response arrives,
      this is done by preSendEvent() callback called during connect() */

   response = xa->connectionP->connect(xa->connectionP, qos, exception);

   freeXmlBlasterBlobContent(&xa->responseBlob);

   /* The response was handled by a callback to postSendEvent */

   if (response == 0) return response;

   if (xa->debug) printf("[XmlBlasterAccessUnparsed] Got response for connect(secretSessionId=%s)\n", xa->connectionP->secretSessionId);
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
   return xa->connectionP->disconnect(xa->connectionP, qos, exception);
}

/**
 * Publish a message to the server. 
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @see XmlBlasterConnectionUnparsed#publish() for a function documentation
 */
static char *xmlBlasterPublish(XmlBlasterAccessUnparsed *xa, MsgUnit *msgUnit, XmlBlasterException *exception)
{
   return xa->connectionP->publish(xa->connectionP, msgUnit, exception);
}

/**
 * Subscribe a message. 
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.subscribe.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterSubscribe(XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterException *exception)
{
   return xa->connectionP->subscribe(xa->connectionP, key, qos, exception);
}

/**
 * UnSubscribe a message from the server. 
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterUnSubscribe(XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterException *exception)
{
   return xa->connectionP->unSubscribe(xa->connectionP, key, qos, exception);
}

/**
 * Erase a message from the server. 
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.erase.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterErase(XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterException *exception)
{
   return xa->connectionP->erase(xa->connectionP, key, qos, exception);
}

/**
 * Ping the server. 
 * @param qos The QoS or 0
 * @return The ping return QoS raw xml string, you need to free() it or 0 on failure
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterPing(XmlBlasterAccessUnparsed *xa, const char * const qos)
{
   return xa->connectionP->ping(xa->connectionP, qos);
}

/**
 * Get a message. 
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.get.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @return NULL on error, please check exception in such a case
 */
static MsgUnitArr *xmlBlasterGet(XmlBlasterAccessUnparsed *xa, const char * const key, const char * qos, XmlBlasterException *exception)
{
   return xa->connectionP->get(xa->connectionP, key, qos, exception);
}

/**
 * Fills the given abstime with absolute time, using the given timeout xa->responseTimeout in milliseconds
 * On Linux < 2.5.64 does not support high resolution timers clock_gettime(),
 * but patches are available at http://sourceforge.net/projects/high-res-timers
 * @return true If implemented
 */
static bool getAbsoluteTime(XmlBlasterAccessUnparsed *xa, struct timespec *abstime)
{
# ifdef _WINDOWS
   return false; /* TODO !!! How to get the current time on Win? */
# else /* LINUX, __sun */
   struct timeval tv;

   memset(abstime, 0, sizeof(struct timespec));

   gettimeofday(&tv, 0);
   abstime->tv_sec = tv.tv_sec;
   abstime->tv_nsec = tv.tv_usec * 1000;  /* microseconds to nanoseconds */

   abstime->tv_sec += xa->responseTimeout / 1000;
   abstime->tv_nsec += (xa->responseTimeout % 1000) * 1000 * 1000;
   if (abstime->tv_nsec >= NANO_SECS_PER_SECOND) {
      abstime->tv_nsec -= NANO_SECS_PER_SECOND;
      abstime->tv_sec += 1;
   }
   return true;
# endif
# if MORE_REALTIME
   clock_gettime(CLOCK_REALTIME, abstime);

   abstime->tv_sec += xa->responseTimeout / 1000;
   abstime->tv_nsec += (xa->responseTimeout % 1000) * 1000 * 1000;
   if (abstime->tv_nsec >= NANO_SECS_PER_SECOND) {
      abstime->tv_nsec -= NANO_SECS_PER_SECOND;
      abstime->tv_sec += 1;
   }
   return true;
# endif
}


#ifdef XmlBlasterAccessUnparsedMain /* compile a standalone test program */

/**
 * Here we receive the callback messages from xmlBlaster
 */
bool myUpdate(MsgUnitArr *msgUnitArr, XmlBlasterException *xmlBlasterException)
{
   size_t i;
   bool testException = false;
   for (i=0; i<msgUnitArr->len; i++) {
      char *xml = messageUnitToXml(&msgUnitArr->msgUnitArr[i]);
      printf("[client] CALLBACK update(): Asynchronous message update arrived:%s\n", xml);
      free(xml);
      msgUnitArr->msgUnitArr[i].responseQos = strcpyAlloc("<qos><state id='OK'/></qos>");
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
 * Invoke: XmlBlasterAccessUnparsedMain -debug true  -numTests 10
 */
int main(int argc, char** argv)
{
   int ii;
   int numTests = 1;
   for (ii=0; ii < argc-1; ii++)
      if (strcmp(argv[ii], "-numTests") == 0)
         sscanf(argv[++ii], "%d", &numTests);

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
      bool debug = false;

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
            "\n  -debug               true/false [false]"
            "\n  -numTests            How often to run the same tests [1]"
            "\n\nExample:"
            "\n  XmlBlasterAccessUnparsedMain -debug true"
                 " -dispatch/connection/plugin/socket/hostname server.mars.universe";
            printf("Usage:\n%s%s\n", xmlBlasterAccessUnparsedUsage(usage), pp);
            exit(1);
         }
      }

      for (iarg=0; iarg < argc-1; iarg++) {
         if (strcmp(argv[iarg], "-debug") == 0)
            debug = !strcmp(argv[++iarg], "true");
      }

      xa = getXmlBlasterAccessUnparsed(argc, argv);
      if (xa->initialize(xa, myUpdate) == false) {
         printf("[client] Connection to xmlBlaster failed,"
                " please start the server or check your configuration\n");
         freeXmlBlasterAccessUnparsed(xa);
         exit(1);
      }

      {  /* connect */
         char connectQos[2048];
         char callbackQos[1024];
         sprintf(callbackQos,
                  "<queue relating='callback' maxEntries='100' maxEntriesCache='100'>"
                  "  <callback type='SOCKET' sessionId='%s'>"
                  "    socket://%s:%d"
                  "  </callback>"
                  "</queue>",
                  callbackSessionId, xa->callbackP->hostCB, xa->callbackP->portCB);
         sprintf(connectQos,
                "<qos>"
                " <securityService type='htpasswd' version='1.0'>"
                "  <![CDATA["
                "   <user>fritz</user>"
                "   <passwd>secret</passwd>"
                "  ]]>"
                " </securityService>"
                "%s"
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

      response = xa->ping(xa, 0);
      if (response == (char *)0) {
         printf("[client] ERROR: Pinging a connected server failed\n");
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

