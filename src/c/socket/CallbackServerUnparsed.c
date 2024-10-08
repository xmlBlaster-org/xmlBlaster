/*----------------------------------------------------------------------------
Name:      CallbackServerUnparsed.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Establish a listen socket for xmlBlaster callbacks
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:
  LINUX:   gcc -g -Wall -DUSE_MAIN_CB -I.. -o CallbackServerUnparsed CallbackServerUnparsed.c xmlBlasterSocket.c ../util/msgUtil.c ../util/Properties.c
  WIN:     cl /MT -DUSE_MAIN_CB -D_WINDOWS -I.. CallbackServerUnparsed.c xmlBlasterSocket.c ../util/msgUtil.c ../util/Properties.c ws2_32.lib
  Solaris: cc -g -DUSE_MAIN_CB -I.. -o CallbackServerUnparsed CallbackServerUnparsed.c xmlBlasterSocket.c ../util/msgUtil.c ../util/Properties.c -lsocket -lnsl
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <string.h>
#if defined(WINCE)
#  if defined(XB_USE_PTHREADS)
#     include <pthreads/pthread.h>
#  else
      /*#include <pthreads/need_errno.h> */
      static int errno=0; /* single threaded workaround*/
#  endif
#else
#  include <errno.h>
#endif
#include <socket/xmlBlasterSocket.h> /* gethostname() */
#include <CallbackServerUnparsed.h>
#ifdef __IPhoneOS__
#include <CoreFoundation/CFSocket.h>
#include <XmlBlasterConnectionUnparsed.h>
#endif
static bool waitOnCallbackThreadAlive(CallbackServerUnparsed *cb, long millis);
static bool waitOnCallbackThreadTermination(CallbackServerUnparsed *cb, long millis);
static bool useThisSocket(CallbackServerUnparsed *cb, int socketToUse, int socketToUseUdp);
static int runCallbackServer(CallbackServerUnparsed *cb);
static bool createCallbackServer(CallbackServerUnparsed *cb);
static bool isListening(CallbackServerUnparsed *cb);
static bool readMessage(CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, XmlBlasterException *exception, bool udp);
static ssize_t writenPlain(void *cb, const int fd, const char *ptr, const size_t nbytes);
static ssize_t readnPlain(void *cb, const int fd, char *ptr, const size_t nbytes, XmlBlasterNumReadFunc fpNumRead, void *userP2);
static bool addResponseListener(CallbackServerUnparsed *cb, MsgRequestInfo *msgRequestInfoP, ResponseFp responseEventFp);
static ResponseListener *removeResponseListener(CallbackServerUnparsed *cb, const char *requestId);
static void voidSendResponse(CallbackServerUnparsed *cb, void *socketDataHolder, MsgUnitArr *msgUnitArr);
static void sendResponse(CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, MsgUnitArr *msgUnitArr);
static void voidSendXmlBlasterException(CallbackServerUnparsed *cb, void *socketDataHolder, XmlBlasterException *exception);
static void sendXmlBlasterException(CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, XmlBlasterException *exception);
static void voidSendResponseOrException(XMLBLASTER_C_bool success, CallbackServerUnparsed *cb, void *socketDataHolder, MsgUnitArr *msgUnitArrP, XmlBlasterException *exception);
static void sendResponseOrException(XMLBLASTER_C_bool success, CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, MsgUnitArr *msgUnitArrP, XmlBlasterException *exception);
static void shutdownCallbackServer(CallbackServerUnparsed *cb);
static void closeAcceptSocket(CallbackServerUnparsed *cb);

/*
static void xmlBlasterNumRead_test(void *xb, const size_t currBytesRead, const size_t nbytes) {
   printf("xmlBlasterSocket.c: DEUBG ONLY currBytesRead=%ld nbytes=%ld\n", (long)currBytesRead, (long)nbytes);
}
*/


/**
 * See header for a description.
 */
CallbackServerUnparsed *getCallbackServerUnparsed(int argc, const char* const* argv,
                        UpdateCbFp updateCb, void *updateCbUserData)
{
   CallbackServerUnparsed *cb = (CallbackServerUnparsed *)calloc(1,
                                sizeof(CallbackServerUnparsed));
   if (cb == 0) return cb;
   cb->props = createProperties(argc, argv);
   if (cb->props == 0) {
      freeCallbackServerUnparsed(&cb);
      return (CallbackServerUnparsed *)0;
   }
   cb->stopListenLoop = false;
   cb->listenSocket = -1; /* Can be reused from XmlBlasterConnectionUnparsed */
   cb->acceptSocket = -1; /* Can be reused from XmlBlasterConnectionUnparsed */
   cb->socketUdp = -1; /* Can be reused from XmlBlasterConnectionUnparsed */
   cb->useThisSocket = useThisSocket;
   cb->runCallbackServer = runCallbackServer;
   cb->isListening = isListening;
   cb->shutdown = shutdownCallbackServer;
   cb->reusingConnectionSocket = false; /* is true if we tunnel callback through the client connection socket */
   cb->logLevel = parseLogLevel(cb->props->getString(cb->props, "logLevel", "WARN"));
   cb->log = xmlBlasterDefaultLogging;
   cb->logUserP = 0;
   cb->waitOnCallbackThreadAlive = waitOnCallbackThreadAlive;
   cb->waitOnCallbackThreadTermination = waitOnCallbackThreadTermination;
   cb->hostCB = strcpyAlloc(cb->props->getString(cb->props, "dispatch/callback/plugin/socket/hostname", 0));
   cb->portCB = cb->props->getInt(cb->props, "dispatch/callback/plugin/socket/port", DEFAULT_CALLBACK_SERVER_PORT);
   cb->updateCb = updateCb;
   cb->updateCbUserData = updateCbUserData; /* A optional pointer from the client code which is returned to the update() function call */
   memset(cb->responseListener, 0, MAX_RESPONSE_LISTENER_SIZE*sizeof(ResponseListener));
   pthread_mutex_init(&cb->responseListenerMutex, NULL); /* returns always 0 */
   cb->addResponseListener = addResponseListener;
   cb->removeResponseListener = removeResponseListener;
   cb->_threadIsAliveOnce = true;
   cb->threadIsAlive = false;
   cb->sendResponse = voidSendResponse;
   cb->sendXmlBlasterException = voidSendXmlBlasterException;
   cb->sendResponseOrException = voidSendResponseOrException;

   cb->writeToSocket.writeToSocketFuncP = writenPlain;
   cb->writeToSocket.userP = cb;

   cb->readFromSocket.readFromSocketFuncP = readnPlain;
   cb->readFromSocket.userP = cb;
   cb->readFromSocket.numReadFuncP = 0; /* xmlBlasterNumRead_test */
   cb->readFromSocket.numReadUserP = 0;
   return cb;
}

/*
 * @see header
 */
bool useThisSocket(CallbackServerUnparsed *cb, int socketToUse, int socketToUseUdp)
{
#ifdef __IPhoneOS__
   #	pragma unused(fd) /*if (socketToUse < 200) printf("CallbackServerUparsed.c: dummy printf to avoid compiler warning\n");*/
   cb->portCB = 12345;
   strcpyRealloc(&cb->hostCB, "127.0.0.1"); /* inet_ntoa holds the host in an internal static string */
   /*
   cb->listenSocket = CFSocketGetNative(globalIPhoneXb->cfSocketRef);

   cb->acceptSocket = CFSocketGetNative(globalIPhoneXb->cfSocketRef);
   */
   cb->listenSocket = 0;

   cb->acceptSocket = 0;

   cb->socketUdp = socketToUseUdp;
   cb->reusingConnectionSocket = true; /* we tunnel callback through the client connection socket */
#else
   struct sockaddr_in localAddr;
   socklen_t size = (socklen_t)sizeof(localAddr);
   memset((char *)&localAddr, 0, (size_t)size);
   if (getsockname(socketToUse, (struct sockaddr *)&localAddr, &size) == -1) {
      if (cb->logLevel>=XMLBLASTER_LOG_WARN) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_WARN, __FILE__,
         "Can't determine the local socket host and port, errno=%d", errno);
      return false;
   }
   cb->portCB = (int)ntohs(localAddr.sin_port);
   strcpyRealloc(&cb->hostCB, inet_ntoa(localAddr.sin_addr)); /* inet_ntoa holds the host in an internal static string */

   cb->listenSocket = socketToUse;
   cb->acceptSocket = socketToUse;
   cb->socketUdp = socketToUseUdp;
   cb->reusingConnectionSocket = true; /* we tunnel callback through the client connection socket */

   if (cb->logLevel>=XMLBLASTER_LOG_INFO) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_INFO, __FILE__,
      "Forced callback server to reuse socket descriptor '%d' on localHostname=%s localPort=%d",
                        socketToUse, cb->hostCB, cb->portCB);
#endif
   return true;
}

/**
 * Wait after pthread_create() until thread is running.
 * @return false if not alive after given millis
 */
static bool waitOnCallbackThreadAlive(CallbackServerUnparsed *cb, long millis) {
   int i;
   const int count = 100;
   const int milliStep = (millis < count) ? 1 : (int)(millis / count);
   for(i=0; i<count; i++) {
      if (cb->_threadIsAliveOnce) {
        return true;
      }
      if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
          "waitOnCallbackThreadAlive(i=%d/%d) waiting %d millis ...", i, count, milliStep);
      sleepMillis(milliStep);
   }
   cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__,
         "waitOnCallbackThreadAlive() failed after %d milliseconds, thread has never reached alive", millis);
   return false;
}

/**
 * For pthread_detached operation only (does not make sense in pthread_join() mode).
 * @return false if not terminated after given millis
 */
static bool waitOnCallbackThreadTermination(CallbackServerUnparsed *cb, long millis) {
	int i;
	const int count = 100;
	const int milliStep = (millis < count) ? 1 : (int)(millis / count);
	for(i=0; i<count; i++) {
	   if (!cb->threadIsAlive) {
		  return true;
	   }
      if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
          "waitOnCallbackThreadTermination(i=%d/%d) waiting %d millis ...", i, count, milliStep);
	   sleepMillis(milliStep);
	}
	cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__,
	      "waitOnCallbackThreadTermination() failed after %d milliseconds, thread has not terminated, it seems to block on the socket", millis);
	return false;
}

void freeCallbackServerUnparsed(CallbackServerUnparsed **cb_)
{
   CallbackServerUnparsed *cb = *cb_;
   if (cb != 0) {
      bool hasTerminated;
      shutdownCallbackServer(cb);
      hasTerminated = cb->waitOnCallbackThreadTermination(cb, 5000);
      freeProperties(cb->props);
      if (hasTerminated) {
         pthread_mutex_destroy(&cb->responseListenerMutex);
    	   free(cb); /* Prefer to have a leak instead of a crash */
      }
      *cb_ = 0;
   }
}

/**
 * Write uncompressed to socket (not thread safe)
 */
static ssize_t writenPlain(void *userP, const int fd, const char *ptr, const size_t nbytes) {
   if (userP) userP = 0; /* To avoid compiler warning */
   return writen(fd, ptr, nbytes);
}

/**
 * Read data from socket, uncompress data if needed (not thread safe)
 */
static ssize_t readnPlain(void * userP, const int fd, char *ptr, const size_t nbytes, XmlBlasterNumReadFunc fpNumRead, void *userP2) {
   if (userP) userP = 0; /* To avoid compiler warning */
   return readn(fd, ptr, nbytes, fpNumRead, userP2);
}

static int responseListenerMutexLock(CallbackServerUnparsed *cb) {
   int retInt = 0;
   if ((retInt = pthread_mutex_lock(&cb->responseListenerMutex)) != 0) {
      char p[XMLBLASTEREXCEPTION_MESSAGE_LEN];
      SNPRINTF(p, XMLBLASTEREXCEPTION_MESSAGE_LEN,
               "[%.100s:%d] Error trying to lock cbMutex %d", __FILE__, __LINE__, retInt);
      cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__, p);
   }
   return retInt;
}

#if defined(_WINDOWS)
static char *strerror_r(int retInt, char * errnoStr, size_t size) {
	/*int ret = */strerror_s(errnoStr, size, retInt);
	return errnoStr;
}
#endif

static int responseListenerMutexUnLock(CallbackServerUnparsed *cb) {
   int retInt = 0;
   if ((retInt = pthread_mutex_unlock(&cb->responseListenerMutex)) != 0) {
      char p[XMLBLASTEREXCEPTION_MESSAGE_LEN];
      char errnoStr[XMLBLASTEREXCEPTION_MESSAGE_LEN];
      strerror_r(retInt, errnoStr, XMLBLASTEREXCEPTION_MESSAGE_LEN);
      SNPRINTF(p, XMLBLASTEREXCEPTION_MESSAGE_LEN,
               "[%.100s:%d] Error trying to unlock cbMutex %d = %s", __FILE__, __LINE__, retInt, errnoStr);
      cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__, p);
   }
   return retInt;
}

static bool addResponseListener(CallbackServerUnparsed *cb, MsgRequestInfo *msgRequestInfoP, ResponseFp responseEventFp) {
   int i;
   if (responseEventFp == 0) {
      return false;
   }
   responseListenerMutexLock(cb);
   for (i=0; i<MAX_RESPONSE_LISTENER_SIZE; i++) {
      if (cb->responseListener[i].msgRequestInfoP == 0) {
         cb->responseListener[i].msgRequestInfoP = msgRequestInfoP;
         cb->responseListener[i].responseEventFp = responseEventFp;
         if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
            "addResponseListener(i=%d, requestId=%s)", i, msgRequestInfoP->requestIdStr);
         responseListenerMutexUnLock(cb);
         return true;
      }
   }
   responseListenerMutexUnLock(cb);
   cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__,
      "PANIC too many requests (%d) are waiting for a response, you are not registered", MAX_RESPONSE_LISTENER_SIZE);
   return false;
}

static ResponseListener *getResponseListener(CallbackServerUnparsed *cb, const char *requestId) {
   int i;
   if (requestId == 0) {
      return 0;
   }
   responseListenerMutexLock(cb);
   for (i=0; i<MAX_RESPONSE_LISTENER_SIZE; i++) {
      const MsgRequestInfo * const pp = cb->responseListener[i].msgRequestInfoP;
      if (pp == 0) {
         continue;
      }
      if (!strcmp(pp->requestIdStr, requestId)) {
         responseListenerMutexUnLock(cb);
         return &cb->responseListener[i];
      }
   }
   responseListenerMutexUnLock(cb);
   cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__, "RequestId '%s' is not registered", requestId);
   return 0;
}

static ResponseListener *removeResponseListener(CallbackServerUnparsed *cb, const char *requestId) {
   int i;
   responseListenerMutexLock(cb);
   for (i=0; i<MAX_RESPONSE_LISTENER_SIZE; i++) {
      const MsgRequestInfo * const pp = cb->responseListener[i].msgRequestInfoP;
      if (pp == 0) {
         continue;
      }
      if (!strcmp(pp->requestIdStr, requestId)) {
         cb->responseListener[i].msgRequestInfoP = 0;
         responseListenerMutexUnLock(cb);
         return &cb->responseListener[i];
      }
   }
   responseListenerMutexUnLock(cb);
   cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__, "Can't remove requestId '%s', requestId is not registered", requestId);
   return (ResponseListener *)0;
}

/**
 * Called by listenLoop when a new message has arrived.
 */
static void handleMessage(CallbackServerUnparsed *cb, SocketDataHolder* socketDataHolder, XmlBlasterException* xmlBlasterException, bool success) {

   MsgUnitArr *msgUnitArrP;

   if (success == false) { /* EOF */
      int i;
      if (!cb->reusingConnectionSocket)
         cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_WARN, __FILE__, "Lost callback socket connection to xmlBlaster (EOF)");
      closeAcceptSocket(cb);
      /* Notify pending requests, otherwise they block in their mutex for a minute ... */
      for (i=0; i<MAX_RESPONSE_LISTENER_SIZE; i++) {
         XmlBlasterException exception;
         ResponseListener *listener;
         MsgRequestInfo *msgRequestInfoP;

         responseListenerMutexLock(cb);
         listener = &cb->responseListener[i];
         if (listener->msgRequestInfoP == 0) {
            responseListenerMutexUnLock(cb);
            continue;
         }
         /* Handle waiting MSG_TYPE_INVOKE threads (oneways are not in this list) */
         msgRequestInfoP = listener->msgRequestInfoP;
         cb->responseListener[i].msgRequestInfoP = 0;
         responseListenerMutexUnLock(cb);

         initializeXmlBlasterException(&exception);

         /* Simulate an exception on client side ... */
         socketDataHolder->type = (char)MSG_TYPE_EXCEPTION;
         strncpy0(socketDataHolder->requestId, msgRequestInfoP->requestIdStr, MAX_REQUESTID_LEN);
         strncpy0(socketDataHolder->methodName, msgRequestInfoP->methodName, MAX_METHODNAME_LEN);

         exception.remote = true;
         strncpy0(exception.errorCode, "communication.noConnection", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
         SNPRINTF(exception.message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
               "[%.100s:%d] Lost connection to xmlBlaster with server side EOF", __FILE__, __LINE__);

         encodeXmlBlasterException(&socketDataHolder->blob, &exception, false);

         /* Takes a clone of socketDataHolder->blob */
         listener->responseEventFp(msgRequestInfoP, socketDataHolder);
         /* Now the client thread has wakened up and returns:
          * msgRequestInfoP is invalid now as it was on client thread stack
          */

         freeBlobHolderContent(&socketDataHolder->blob);
         if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
            "Notified pending requestId '%s' about lost socket connection", socketDataHolder->requestId);
      }
      return;
   }

   if (*xmlBlasterException->errorCode != 0) {
      cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__,
         "Couldn't read message from xmlBlaster: errorCode=%s message=%s",
                  xmlBlasterException->errorCode, xmlBlasterException->message);
      return;
   }

   if (cb->reusingConnectionSocket &&
         (socketDataHolder->type == (char)MSG_TYPE_RESPONSE || socketDataHolder->type == (char)MSG_TYPE_EXCEPTION)) {
      ResponseListener *listener = getResponseListener(cb, socketDataHolder->requestId);
      if (listener != 0) {
         /* This is a response for a request (no callback for us) */
         MsgRequestInfo *msgRequestInfoP = listener->msgRequestInfoP;
         removeResponseListener(cb, socketDataHolder->requestId);
         listener->responseEventFp(msgRequestInfoP, socketDataHolder);
         freeBlobHolderContent(&socketDataHolder->blob);
         if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
            "Forwarded message with requestId '%s' to response listener", socketDataHolder->requestId);
         return;
      }
      else {
         cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__,
            "PANIC: Did not expect an INVOCATION '%c'='%d' as a callback",
                  socketDataHolder->type, (int)socketDataHolder->type);
         freeBlobHolderContent(&socketDataHolder->blob);
         return;
      }
   }

   msgUnitArrP = parseMsgUnitArr(socketDataHolder->blob.dataLen, socketDataHolder->blob.data);
   freeBlobHolderContent(&(socketDataHolder->blob));

   if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
      "Received requestId '%s' callback %s()",
      socketDataHolder->requestId, socketDataHolder->methodName);

   if (strcmp(socketDataHolder->methodName, XMLBLASTER_PING) == 0) {
      size_t i;
      for (i=0; i<msgUnitArrP->len; i++) {
         msgUnitArrP->msgUnitArr[i].responseQos = strcpyAlloc("<qos/>");
      }
      sendResponse(cb, socketDataHolder, msgUnitArrP);
      freeMsgUnitArr(msgUnitArrP);
   }
   else if (strcmp(socketDataHolder->methodName, XMLBLASTER_UPDATE) == 0 ||
            strcmp(socketDataHolder->methodName, XMLBLASTER_UPDATE_ONEWAY) == 0) {
      if (cb->updateCb != 0) { /* Client has registered to receive callback messages? */
         if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
            "Calling client %s() for requestId '%s' ...",
            socketDataHolder->methodName, socketDataHolder->requestId);

         strncpy0(msgUnitArrP->secretSessionId, socketDataHolder->secretSessionId, MAX_SESSIONID_LEN);
         msgUnitArrP->isOneway = (strcmp(socketDataHolder->methodName, XMLBLASTER_UPDATE_ONEWAY) == 0);
         cb->updateCb(msgUnitArrP, cb, xmlBlasterException, socketDataHolder);
      }
      else { /* Unexpected update arrived, the client was not interested, see similar behavior in XmlBlasterAccess.java:update() */
         size_t i;
         for (i=0; i<msgUnitArrP->len; i++) {
            msgUnitArrP->msgUnitArr[i].responseQos = strcpyAlloc("<qos><state id='OK'/></qos>");
            cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__,
               "Ignoring unexpected %s() message as client has not registered a callback, requestId is '%s' ...",
               socketDataHolder->methodName, socketDataHolder->requestId);
         }
         sendResponseOrException(true, cb, socketDataHolder, msgUnitArrP, xmlBlasterException);
      }
   }
   else {
      cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__,
      "Received unknown callback methodName=%s", socketDataHolder->methodName);
   }

}


/**
 * The run method of the two threads (TCP or UDP).
 * <p />
 * The caller must do a pthread_join or pthread_detach to avoid leaking,
 * <br />
 * see freeXmlBlasterAccessUnparsed() for TCP
 * or runCallbackServer() for UDP
 * <p />
 * Set cb->stopListenLoop to false to end the thread
 */
static int listenLoop(ListenLoopArgs* ls)
{
   int rc;
   CallbackServerUnparsed *cb = ls->cb;
   bool udp = ls->udp;
   XmlBlasterException xmlBlasterException;
   SocketDataHolder socketDataHolder;
   bool success;
   bool useUdpForOneway = cb->socketUdp != -1;
   for(;;) {
      memset(&xmlBlasterException, 0, sizeof(XmlBlasterException));
      /* Here we block until a message arrives, see parseSocketData() */
      if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
         "Going to block on socket read until a new message arrives ...");
      if (cb->stopListenLoop) break;
      memset(&socketDataHolder, 0, sizeof(SocketDataHolder));
      success = readMessage(cb, &socketDataHolder, &xmlBlasterException, udp);
      if (cb->stopListenLoop) {
    	  freeBlobHolderContent(&socketDataHolder.blob);
    	  break;
      }
      cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "%s arrived, success=%s", udp ? "UDP" : "TCP", success ? "true" : "false -> EOF");

      if (useUdpForOneway) {
         rc = pthread_mutex_lock(&cb->listenMutex);
         if (rc != 0) /* EINVAL */
            cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__, "pthread_mutex_lock() returned %d.", rc);
      }

      handleMessage(cb, &socketDataHolder, &xmlBlasterException, success);

      if (useUdpForOneway) {
         rc = pthread_mutex_unlock(&cb->listenMutex);
         if (rc != 0) /* EPERM */
            cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__, "pthread_mutex_unlock() returned %d.", rc);
      }

      if (cb->stopListenLoop || !success)
         break;
   }
   /*pthread_exit(NULL);*/
   return 0;
}


/**
 * Started by XmlBlasterAccessUnparsed.c pthread_create(runCallbackServer).
 * <p />
 * Open a socket and only leaves when the connection is lost (on EOF),
 * in this case implicit pthread_exit() is called.
 * <p />
 * xmlBlaster will connect and receive callback messages.
 * @return 0 on success, 1 on error. The return value is the exit value returned by pthread_join()
 */
static int runCallbackServer(CallbackServerUnparsed *cb)
{
   int rc;
   int retVal = 0;

   const bool useUdpForOneway = cb->socketUdp != -1;

   cb->threadIsAlive = true;
   cb->_threadIsAliveOnce = true;

   if (cb->listenSocket == -1) {
      if (createCallbackServer(cb) == false) {
         cb->threadIsAlive = false;
         return 1;
      }
   }
   else {
      if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
         "Reusing connection socket to tunnel callback messages");
   }

   if (useUdpForOneway) {
      ListenLoopArgs* tcpLoop = 0;
      ListenLoopArgs* udpLoop = 0;

      /* We need to create two threads: one for TCP and one for the UDP callback listener */
      pthread_t tcpListenThread, udpListenThread;

      rc = pthread_mutex_init(&cb->listenMutex, NULL); /* rc is always 0 */

      tcpLoop = (ListenLoopArgs*)malloc(sizeof(ListenLoopArgs)); tcpLoop->cb = cb; tcpLoop->udp = false;
      rc = pthread_create(&tcpListenThread, NULL, (void * (*)(void *))listenLoop, tcpLoop);

      udpLoop = (ListenLoopArgs*)malloc(sizeof(ListenLoopArgs)); udpLoop->cb = cb; udpLoop->udp = true;
      rc = pthread_create(&udpListenThread, NULL, (void * (*)(void *))listenLoop, udpLoop);

      if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
            "Waiting to join tcpListenThread ...");
      pthread_join(tcpListenThread, NULL);
      free(tcpLoop);

      if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
            "Waiting to join udpListenThread ...");
      pthread_join(udpListenThread, NULL);
      free(udpLoop);

      rc = pthread_mutex_destroy(&cb->listenMutex);
      if (rc != 0) /* EBUSY */
         cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__, "pthread_mutex_destroy() returned %d, we ignore it", rc);
   }
   else {
      /* TCP only: no separate thread is needed (is called from XmlBlasterAccessUnparsed:pthread_create) */
      ListenLoopArgs tcpLoop; tcpLoop.cb = cb; tcpLoop.udp = false;
      retVal = listenLoop(&tcpLoop);
   }

   if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
         "Callbackserver thread is dying now ...");
   cb->threadIsAlive = false; /* cb can be freed now */
   return retVal;
}

/**
 * Called from separate thread.
 * Is only called if we start a dedicated callback server (not tunneling
 * through the connection socket).
 * @return true The callback server is started, false on error
 */
static bool createCallbackServer(CallbackServerUnparsed *cb)
{
   socklen_t cli_len;
   struct hostent hostbuf, *hostP = NULL;
   struct sockaddr_in serv_addr, cli_addr;
   char *tmphstbuf=NULL;
   size_t hstbuflen=0;
   char serverHostName[256];
   char errP[MAX_ERRNO_LEN];
   if (cb->hostCB == 0) {
      strcpyRealloc(&cb->hostCB, "localhost");
      if (gethostname(serverHostName, 125) == 0)
         strcpyRealloc(&cb->hostCB, serverHostName);
   }

   if (cb->logLevel>=XMLBLASTER_LOG_INFO) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_INFO, __FILE__,
      "Starting callback server -dispatch/callback/plugin/socket/hostname %s -dispatch/callback/plugin/socket/port %d ...",
               cb->hostCB, cb->portCB);

   /*
      * Get a socket to work with.
      */
   if ((cb->listenSocket = (int)socket(AF_INET, SOCK_STREAM, 0)) < 0) {
      if (cb->logLevel>=XMLBLASTER_LOG_WARN) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_WARN, __FILE__,
         "Failed creating socket for callback server -dispatch/callback/plugin/socket/hostname %s -dispatch/callback/plugin/socket/port %d",
            cb->hostCB, cb->portCB);
         cb->threadIsAlive = false;
         return false;
   }

   /*
    * Create the address we will be binding to.
    */
   serv_addr.sin_family = AF_INET;
   *errP = 0;
   hostP = gethostbyname_re(cb->hostCB, &hostbuf, &tmphstbuf, &hstbuflen, errP);

   if (*errP != 0) {
      char message[EXCEPTIONSTRUCT_MESSAGE_LEN];
      SNPRINTF(message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
               "[%.100s:%d] Lookup xmlBlaster failed, %s",
               __FILE__, __LINE__, errP);
      cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_WARN, __FILE__, message);
      *errP = 0;
   }

   if (hostP != NULL) {
      serv_addr.sin_addr.s_addr = ((struct in_addr *)(hostP->h_addr))->s_addr; /*inet_addr("192.168.1.2"); */
      free(tmphstbuf);
   }
   else
      serv_addr.sin_addr.s_addr = htonl(INADDR_ANY);
   serv_addr.sin_port = htons((u_short)cb->portCB);

   if (bind(cb->listenSocket, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
      if (cb->logLevel>=XMLBLASTER_LOG_WARN) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_WARN, __FILE__,
         "Failed binding port for callback server -dispatch/callback/plugin/socket/hostname %s -dispatch/callback/plugin/socket/port %d",
            cb->hostCB, cb->portCB);
      return false;
   }

   /*
      * Listen on the socket.
      */
   if (listen(cb->listenSocket, 5) < 0) {
      if (cb->logLevel>=XMLBLASTER_LOG_WARN) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_WARN, __FILE__,
         "Failed creating listener for callback server -dispatch/callback/plugin/socket/hostname %s -dispatch/callback/plugin/socket/port %d",
            cb->hostCB, cb->portCB);
      return false;
   }

   if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
      "[CallbackServerUnparsed] Waiting for xmlBlaster to connect ...");

   /*
      * Accept connections.  When we accept one, ns
      * will be connected to the client.  cli_addr will
      * contain the address of the client.
      */
   cli_len = (socklen_t)sizeof(cli_addr);
   if ((cb->acceptSocket = (int)accept(cb->listenSocket, (struct sockaddr *)&cli_addr, &cli_len)) < 0) {
      if (cb->logLevel>=XMLBLASTER_LOG_ERROR) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__,
         "[CallbackServerUnparsed] accept failed");
         cb->threadIsAlive = false;
         return false;
   }
   if (cb->logLevel>=XMLBLASTER_LOG_INFO) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_INFO, __FILE__,
      "[CallbackServerUnparsed] XmlBlaster connected from %s:%hd",
                           inet_ntoa(cli_addr.sin_addr), cli_addr.sin_port);
   return true;
}

static bool isListening(CallbackServerUnparsed *cb)
{
   if (cb->listenSocket > -1) {
      return true;
   }
   return false;
}

/**
 * Parse the update message from xmlBlaster.
 * <p>
 * This method blocks until data arrives.
 * </p>
 * The socketDataHolder holds all informations about the returned data from xmlBlaster,
 * on error the exception struct is filled.
 *
 * @param cb The 'this' pointer
 * @param socketDataHolder You need to free(socketDataHolder->data) if return is 'true'.
 * @param exception Contains the exception thrown (on error only)
 * @param udp If UDP to use
 * @return true if OK or on exception, false on EOF
 */
static bool readMessage(CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, XmlBlasterException *exception, bool udp)
{
   return parseSocketData(udp ? cb->socketUdp : cb->acceptSocket, &cb->readFromSocket, socketDataHolder,
                          exception, &cb->stopListenLoop, udp, cb->logLevel >= XMLBLASTER_LOG_DUMP);
}

/** A helper to cast to SocketDataHolder */
static void voidSendResponse(CallbackServerUnparsed *cb, void *socketDataHolder, MsgUnitArr *msgUnitArrP)
{
   sendResponse(cb, (SocketDataHolder *)socketDataHolder, msgUnitArrP);
}

static void sendResponse(CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, MsgUnitArr *msgUnitArrP)
{
   char *rawMsg;
   size_t rawMsgLen;
   size_t dataLen = 0;
   char *data = 0;
   size_t i;
   MsgUnit msgUnit; /* we (mis)use MsgUnit for simple transformation of the exception into a raw blob */
   bool allocated = false;
   memset(&msgUnit, 0, sizeof(MsgUnit));

   for (i=0; i<msgUnitArrP->len; i++) {
      if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
         "Returning the UpdateReturnQos '%s' to the server.",
            msgUnitArrP->msgUnitArr[i].responseQos);

      if (msgUnitArrP->msgUnitArr[i].responseQos != 0) {
         msgUnit.qos = msgUnitArrP->msgUnitArr[i].responseQos;
      }
      else {
         msgUnit.qos = strcpyAlloc("<qos/>");
         allocated = true;
      }

      if (data == 0) {
         BlobHolder blob = encodeMsgUnit(&msgUnit, cb->logLevel >= XMLBLASTER_LOG_DUMP);
         data = blob.data;
         dataLen = blob.dataLen;
      }
      else {
         BlobHolder blob = encodeMsgUnit(&msgUnit, cb->logLevel >= XMLBLASTER_LOG_DUMP);
         data = (char *)realloc(data, dataLen+blob.dataLen);
         memcpy(data+dataLen, blob.data, blob.dataLen);
         dataLen += blob.dataLen;
         free(blob.data);
      }
   }

   rawMsg = encodeSocketMessage(MSG_TYPE_RESPONSE, socketDataHolder->requestId,
                             socketDataHolder->methodName, socketDataHolder->secretSessionId,
                             data, dataLen, cb->logLevel >= XMLBLASTER_LOG_DUMP, &rawMsgLen);
   free(data);

   /*ssize_t numSent =*/(void) cb->writeToSocket.writeToSocketFuncP(cb->updateCbUserData, cb->acceptSocket, rawMsg, (int)rawMsgLen);

   free(rawMsg);

   if (allocated) free((char *)msgUnit.qos);
}

static void voidSendXmlBlasterException(CallbackServerUnparsed *cb, void *socketDataHolder, XmlBlasterException *exception)
{
   sendXmlBlasterException(cb, (SocketDataHolder *)socketDataHolder, exception);
}

static void sendXmlBlasterException(CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, XmlBlasterException *exception)
{
   size_t currpos = 0;
   char *rawMsg;
   size_t rawMsgLen;
   BlobHolder blob;
   MsgUnit msgUnit; /* we (mis)use MsgUnit for simple transformation of the exception into a raw blob */
   memset(&msgUnit, 0, sizeof(MsgUnit));

   msgUnit.qos = exception->errorCode;

   /* see XmlBlasterException.toByteArr() and parseByteArr() */
   msgUnit.contentLen = strlen(exception->errorCode) + strlen(exception->message) + 11;
   msgUnit.content = (char *)calloc(msgUnit.contentLen, sizeof(char));

   memcpy((char *)msgUnit.content, exception->errorCode, strlen(exception->errorCode));
   currpos = strlen(exception->errorCode) + 4;

   memcpy((char *)msgUnit.content+currpos, exception->message, strlen(exception->message));

   blob = encodeMsgUnit(&msgUnit, cb->logLevel >= XMLBLASTER_LOG_DUMP);

   rawMsg = encodeSocketMessage(MSG_TYPE_EXCEPTION, socketDataHolder->requestId,
                             socketDataHolder->methodName, socketDataHolder->secretSessionId,
                             blob.data, blob.dataLen, cb->logLevel >= XMLBLASTER_LOG_DUMP, &rawMsgLen);
   free(blob.data);
   free((char *)msgUnit.content);

   /*ssize_t numSent =*/(void) cb->writeToSocket.writeToSocketFuncP(cb->updateCbUserData, cb->acceptSocket, rawMsg, (int)rawMsgLen);

   free(rawMsg);
}

/**
 * A helper to cast to SocketDataHolder
 * Frees msgUnitArrP
 */
static void voidSendResponseOrException(XMLBLASTER_C_bool success, CallbackServerUnparsed *cb, void *socketDataHolder, MsgUnitArr *msgUnitArrP, XmlBlasterException *exception)
{
   sendResponseOrException(success, cb, (SocketDataHolder *)socketDataHolder, msgUnitArrP, exception);
}

/**
 * Takes care of both successful responses as well as exceptions
 * Frees msgUnitArrP
 */
static void sendResponseOrException(XMLBLASTER_C_bool success, CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, MsgUnitArr *msgUnitArrP, XmlBlasterException *exception)
{
   if (! (strcmp(socketDataHolder->methodName, XMLBLASTER_UPDATE_ONEWAY) == 0)) {
      if (success == true) {
         if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
            "update(): Sending response for requestId '%s'", socketDataHolder->requestId);
         sendResponse(cb, socketDataHolder, msgUnitArrP);
      }
      else {
         if (*(exception->errorCode) == 0) {
            if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
               "update(): We don't return anything for requestId '%s', the return message will come later by the client update dispatcher thread", socketDataHolder->requestId);
         }
         else {
            if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
               "update(): Throwing the XmlBlasterException '%s' back to the server:\n%s",
                   exception->errorCode, exception->message);
            sendXmlBlasterException(cb, socketDataHolder, exception);
         }
      }
   }

   freeMsgUnitArr(msgUnitArrP);
}

/**
 * Force closing socket
 */
static void closeAcceptSocket(CallbackServerUnparsed *cb)
{
   /* We close even if cb->reusingConnectionSocket is set
     to react instantly on EOF from server side.
     Otherwise the client thread would block until socket response timeout happens (one minute)
   */
   if (cb->acceptSocket != -1) {
      closeSocket(cb->acceptSocket);
      cb->acceptSocket = -1;
      if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
         "Closed accept socket");
   }
}

/**
 * Used internally only to close the socket, calling multiple times makes no harm.
 * <p />
 * Closes socket, tells listenLoop to terminate but does not wait on thread termination
 * and does not destroy cb.
 * <p />
 *
 */
static void shutdownCallbackServer(CallbackServerUnparsed *cb)
{
   if (cb == 0) return;

   if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
         "Shutdown callback server stopListenLoop=%s (changes now to true), reusingConnectionSocket=%s", (cb->stopListenLoop?"true":"false"), (cb->reusingConnectionSocket?"true":"false"));

   cb->stopListenLoop = true;

   if (cb->hostCB != 0) {
      free(cb->hostCB);
      cb->hostCB = 0;
   }

   if (cb->reusingConnectionSocket) {
      return; /* not our duty, we only have borrowed the socket from the client side connection */
   }


   closeAcceptSocket(cb);

   if (isListening(cb)) {
      closeSocket(cb->listenSocket);
      cb->listenSocket = -1;
      if (cb->logLevel>=XMLBLASTER_LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
         "Closed listener socket");
   }

   cb->readFromSocket.numReadFuncP = 0;
}

const char *callbackServerRawUsage(void)
{
   return
      "\n   -dispatch/callback/plugin/socket/hostname [localhost]"
      "\n                       The IP where to establish the callback server."
      "\n                       Can be useful on multi homed hosts."
      "\n   -dispatch/callback/plugin/socket/port [7611]"
      "\n                       The port of the callback server.";
}

#ifdef USE_MAIN_CB
/**
 * Here we receive the callback messages from xmlBlaster
 */
bool myUpdate(MsgUnitArr *msgUnitArr, void *userData, XmlBlasterException *xmlBlasterException, SocketDataHandler socketDataHandler)
{
   size_t i;
   bool testException = false;
   for (i=0; i<msgUnitArr->len; i++) {
      char *xml = messageUnitToXml(&msgUnitArr->msgUnitArr[i]);
      printf("client.update(): Asynchronous message update arrived:%s\n", xml);
      free(xml);
      msgUnitArr->msgUnitArr[i].responseQos = strcpyAlloc("<qos></qos>"); /* Return QoS: Everything is OK */
   }
   if (testException) {
      strncpy0(xmlBlasterException->errorCode, "user.notWanted", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      strncpy0(xmlBlasterException->message, "I don't want these messages", XMLBLASTEREXCEPTION_MESSAGE_LEN);
      return false;
   }
   return true;
}

/**
 * Invoke: CallbackServerUnparsed -logLevel TRACE
 */
int main(int argc, char** argv)
{
   int iarg;
   CallbackServerUnparsed *cb;

   for (iarg=0; iarg < argc; iarg++) {
      if (strcmp(argv[iarg], "-help") == 0 || strcmp(argv[iarg], "--help") == 0) {
         const char *pp =
         "\n  -logLevel            ERROR | WARN | INFO | TRACE [WARN]"
         "\n\nExample:"
         "\n  CallbackServerUnparsed -logLevel TRACE -dispatch/callback/plugin/socket/hostname server.mars.universe";
         printf("Usage:\n%s%s\n", callbackServerRawUsage(), pp);
         exit(1);
      }
   }

   cb = getCallbackServerUnparsed(argc, argv, myUpdate, 0);
   printf("[main] Created CallbackServerUnparsed instance, creating listener on socket://%s:%d...\n", cb->hostCB, cb->portCB);
   cb->runCallbackServer(cb); /* blocks on socket listener */

   /* This code is reached only on socket EOF */

   printf("[main] Socket listener is shutdown\n");
   freeCallbackServerUnparsed(&cb);
   return 0;
}
#endif /* USE_MAIN_CB */
