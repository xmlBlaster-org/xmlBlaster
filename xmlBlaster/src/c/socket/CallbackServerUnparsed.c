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
#include <errno.h>
#include <socket/xmlBlasterSocket.h> /* gethostname() */
#include <CallbackServerUnparsed.h>

#if defined(_WINDOWS)
#  define socklen_t int
#  define ssize_t signed int
#elif defined(__alpha)
#  define socklen_t int
#else
#  include <unistd.h>
#endif

static bool useThisSocket(CallbackServerUnparsed *cb, int socketToUse);
static int runCallbackServer(CallbackServerUnparsed *cb);
static bool createCallbackServer(CallbackServerUnparsed *cb);
static bool isListening(CallbackServerUnparsed *cb);
static bool readMessage(CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, XmlBlasterException *exception);
static bool addResponseListener(CallbackServerUnparsed *cb, MsgRequestInfo *msgRequestInfoP, ResponseFp responseEventFp);
static ResponseListener *removeResponseListener(CallbackServerUnparsed *cb, const char *requestId);
static void voidSendResponse(CallbackServerUnparsed *cb, void *socketDataHolder, MsgUnitArr *msgUnitArr);
static void sendResponse(CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, MsgUnitArr *msgUnitArr);
static void voidSendXmlBlasterException(CallbackServerUnparsed *cb, void *socketDataHolder, XmlBlasterException *exception);
static void sendXmlBlasterException(CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, XmlBlasterException *exception);
static void voidSendResponseOrException(bool success, CallbackServerUnparsed *cb, void *socketDataHolder, MsgUnitArr *msgUnitArrP, XmlBlasterException *exception);
static void sendResponseOrException(bool success, CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, MsgUnitArr *msgUnitArrP, XmlBlasterException *exception);
static void shutdownCallbackServer(CallbackServerUnparsed *cb);
static void closeAcceptSocket(CallbackServerUnparsed *cb);

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
      freeCallbackServerUnparsed(cb);
      return (CallbackServerUnparsed *)0;
   }
   cb->listenSocket = -1;
   cb->acceptSocket = -1;
   cb->useThisSocket = useThisSocket;
   cb->runCallbackServer = runCallbackServer;
   cb->isListening = isListening;
   cb->shutdown = shutdownCallbackServer;
   cb->reusingConnectionSocket = false; /* is true if we tunnel callback through the client connection socket */
   cb->logLevel = parseLogLevel(cb->props->getString(cb->props, "logLevel", "WARN"));
   cb->log = xmlBlasterDefaultLogging;
   cb->logUserP = 0;
   cb->hostCB = strcpyAlloc(cb->props->getString(cb->props, "dispatch/callback/plugin/socket/hostname", 0));
   cb->portCB = cb->props->getInt(cb->props, "dispatch/callback/plugin/socket/port", DEFAULT_CALLBACK_SERVER_PORT);
   cb->updateCb = updateCb;
   cb->updateCbUserData = updateCbUserData; /* A optional pointer from the client code which is returned to the update() function call */
   memset(cb->responseListener, 0, MAX_RESPONSE_LISTENER_SIZE*sizeof(ResponseListener));
   cb->addResponseListener = addResponseListener;
   cb->removeResponseListener = removeResponseListener;
   cb->isShutdown = false;
   cb->sendResponse = voidSendResponse;
   cb->sendXmlBlasterException = voidSendXmlBlasterException;
   cb->sendResponseOrException = voidSendResponseOrException;
   return cb;
}

/*
 * @see header
 */
bool useThisSocket(CallbackServerUnparsed *cb, int socketToUse)
{
   struct sockaddr_in localAddr;
   socklen_t size = (socklen_t)sizeof(localAddr);
   memset((char *)&localAddr, 0, (size_t)size);
   if (getsockname(socketToUse, (struct sockaddr *)&localAddr, &size) == -1) {
      if (cb->logLevel>=LOG_WARN) cb->log(cb->logUserP, cb->logLevel, LOG_WARN, __FILE__,
         "Can't determine the local socket host and port, errno=%d", errno);
      return false;
   }
   cb->portCB = (int)ntohs(localAddr.sin_port);
   strcpyRealloc(&cb->hostCB, inet_ntoa(localAddr.sin_addr)); /* inet_ntoa holds the host in an internal static string */

   cb->listenSocket = socketToUse;
   cb->acceptSocket = socketToUse;

   cb->reusingConnectionSocket = true; /* we tunnel callback through the client connection socket */

   if (cb->logLevel>=LOG_INFO) cb->log(cb->logUserP, cb->logLevel, LOG_INFO, __FILE__,
      "Forced callback server to reuse socket descriptor '%d' on localHostname=%s localPort=%d",
                         socketToUse, cb->hostCB, cb->portCB);
   return true;
}

void freeCallbackServerUnparsed(CallbackServerUnparsed *cb)
{
   if (cb != 0) {
      freeProperties(cb->props);
      shutdownCallbackServer(cb);
      free(cb);
   }
}

static bool addResponseListener(CallbackServerUnparsed *cb, MsgRequestInfo *msgRequestInfoP, ResponseFp responseEventFp) {
   int i;
   if (responseEventFp == 0) {
      return false;
   }
   for (i=0; i<MAX_RESPONSE_LISTENER_SIZE; i++) {
      if (cb->responseListener[i].msgRequestInfoP == 0) {
         cb->responseListener[i].msgRequestInfoP = msgRequestInfoP;
         cb->responseListener[i].responseEventFp = responseEventFp;
         if (cb->logLevel>=LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, LOG_TRACE, __FILE__,
            "addResponseListener(i=%d, requestId=%s)", i, msgRequestInfoP->requestIdStr);
         return true;
      }
   }
   cb->log(cb->logUserP, cb->logLevel, LOG_ERROR, __FILE__,
      "PANIC too many requests (%d) are waiting for a response, you are not registered", MAX_RESPONSE_LISTENER_SIZE);
   return false;
}

static ResponseListener *getResponseListener(CallbackServerUnparsed *cb, const char *requestId) {
   int i;
   if (requestId == 0) {
      return 0;
   }
   for (i=0; i<MAX_RESPONSE_LISTENER_SIZE; i++) {
      if (cb->responseListener[i].msgRequestInfoP == 0) {
         continue;
      }
      if (!strcmp(cb->responseListener[i].msgRequestInfoP->requestIdStr, requestId)) {
         return &cb->responseListener[i];
      }
   }
   cb->log(cb->logUserP, cb->logLevel, LOG_ERROR, __FILE__, "RequestId '%s' is not registered", requestId);
   return 0;
}

static ResponseListener *removeResponseListener(CallbackServerUnparsed *cb, const char *requestId) {
   int i;
   for (i=0; i<MAX_RESPONSE_LISTENER_SIZE; i++) {
      if (cb->responseListener[i].msgRequestInfoP == 0) {
         continue;
      }
      if (!strcmp(cb->responseListener[i].msgRequestInfoP->requestIdStr, requestId)) {
         cb->responseListener[i].msgRequestInfoP = 0;
         return &cb->responseListener[i];
      }
   }
   cb->log(cb->logUserP, cb->logLevel, LOG_ERROR, __FILE__, "Can't remove requestId '%s', requestId is not registered", requestId);
   return (ResponseListener *)0;
}

/**
 * Open a socket, this method is usually called from the new thread (see pthread_create())
 * and only leaves when the connection is lost (on EOF),
 * in this case implicit pthread_exit() is called. 
 *
 * xmlBlaster will connect and receive callback messages.
 * @return 0 on success, 1 on error. The return value is the exit value returned by pthread_join()
 */
static int runCallbackServer(CallbackServerUnparsed *cb)
{
   cb->isShutdown = false;

   if (cb->listenSocket == -1) {
      if (createCallbackServer(cb) == false)
         return 1;
   }
   else {
      if (cb->logLevel>=LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, LOG_TRACE, __FILE__,
         "Reusing connection socket to tunnel callback messages");
   }

   for (;;) {
      XmlBlasterException xmlBlasterException;
      SocketDataHolder socketDataHolder;
      MsgUnitArr *msgUnitArrP;
      bool success;      

      memset(&xmlBlasterException, 0, sizeof(XmlBlasterException));

      /* Here we block until a message arrives, see parseSocketData() */
      if (cb->logLevel>=LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, LOG_TRACE, __FILE__,
         "Going to block on socket read until a new message arrives ...");
      success = readMessage(cb, &socketDataHolder, &xmlBlasterException);

      if (success == false) { /* EOF */
         if (!cb->reusingConnectionSocket)
            cb->log(cb->logUserP, cb->logLevel, LOG_WARN, __FILE__, "Lost callback socket connection to xmlBlaster (EOF)");
         closeAcceptSocket(cb);
         break;
      }

      if (*xmlBlasterException.errorCode != 0) {
         cb->log(cb->logUserP, cb->logLevel, LOG_ERROR, __FILE__,
            "Couldn't read message from xmlBlaster: errorCode=%s message=%s",
                   xmlBlasterException.errorCode, xmlBlasterException.message);
         continue;
      }

      if (cb->reusingConnectionSocket &&
          (socketDataHolder.type == (char)MSG_TYPE_RESPONSE || socketDataHolder.type == (char)MSG_TYPE_EXCEPTION)) {
         ResponseListener *listener = getResponseListener(cb, socketDataHolder.requestId);
         if (listener != 0) {
            /* This is a response for a request (no callback for us) */
            MsgRequestInfo *msgRequestInfoP = listener->msgRequestInfoP;
            removeResponseListener(cb, socketDataHolder.requestId);
            listener->responseEventFp(msgRequestInfoP, &socketDataHolder);
            freeBlobHolderContent(&socketDataHolder.blob);
            if (cb->logLevel>=LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, LOG_TRACE, __FILE__,
               "Forwarded message with requestId '%s' to response listener", socketDataHolder.requestId);
            continue;
         }
         else {
            cb->log(cb->logUserP, cb->logLevel, LOG_ERROR, __FILE__,
               "PANIC: Did not expect an INVOCATION '%c'='%d' as a callback",
                   socketDataHolder.type, (int)socketDataHolder.type);
         }
      }

      msgUnitArrP = parseMsgUnitArr(socketDataHolder.blob.dataLen, socketDataHolder.blob.data);
      freeBlobHolderContent(&(socketDataHolder.blob));

      if (cb->logLevel>=LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, LOG_TRACE, __FILE__,
         "Received requestId '%s' callback %s()",
         socketDataHolder.requestId, socketDataHolder.methodName);

      if (strcmp(socketDataHolder.methodName, XMLBLASTER_PING) == 0) {
         size_t i;
         for (i=0; i<msgUnitArrP->len; i++) {
            msgUnitArrP->msgUnitArr[i].responseQos = strcpyAlloc("<qos/>");
         }
      }
      else if (strcmp(socketDataHolder.methodName, XMLBLASTER_UPDATE) == 0 ||
               strcmp(socketDataHolder.methodName, XMLBLASTER_UPDATE_ONEWAY) == 0) {
         if (cb->updateCb != 0) { /* Client has registered to receive callback messages? */
            if (cb->logLevel>=LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, LOG_TRACE, __FILE__,
               "Calling client %s() for requestId '%s' ...",
               socketDataHolder.methodName, socketDataHolder.requestId);
            
            strcpy(msgUnitArrP->secretSessionId, socketDataHolder.secretSessionId);
            msgUnitArrP->isOneway = (strcmp(socketDataHolder.methodName, XMLBLASTER_UPDATE_ONEWAY) == 0);
            cb->updateCb(msgUnitArrP, cb, &xmlBlasterException, &socketDataHolder);
         }
      }
      else {
         cb->log(cb->logUserP, cb->logLevel, LOG_ERROR, __FILE__,
         "Received unknown callback methodName=%s", socketDataHolder.methodName);
      }
   }

   cb->isShutdown = true;
   return 0;
}

/**
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
   if (cb->hostCB == 0) {
      strcpyRealloc(&cb->hostCB, "localhost");
      if (gethostname(serverHostName, 125) == 0)
         strcpyRealloc(&cb->hostCB, serverHostName);
   }   

   if (cb->logLevel>=LOG_INFO) cb->log(cb->logUserP, cb->logLevel, LOG_INFO, __FILE__,
      "Starting callback server -dispatch/callback/plugin/socket/hostname %s -dispatch/callback/plugin/socket/port %d ...",
               cb->hostCB, cb->portCB);

   /*
      * Get a socket to work with.
      */
   if ((cb->listenSocket = (int)socket(AF_INET, SOCK_STREAM, 0)) < 0) {
      if (cb->logLevel>=LOG_WARN) cb->log(cb->logUserP, cb->logLevel, LOG_WARN, __FILE__,
         "Failed creating socket for callback server -dispatch/callback/plugin/socket/hostname %s -dispatch/callback/plugin/socket/port %d",
            cb->hostCB, cb->portCB);
         cb->isShutdown = true;
         return false;
   }

   /*
    * Create the address we will be binding to.
    */
   serv_addr.sin_family = AF_INET;
   hostP = gethostbyname_re(cb->hostCB, &hostbuf, &tmphstbuf, &hstbuflen);
   if (hostP != NULL) {
      serv_addr.sin_addr.s_addr = ((struct in_addr *)(hostP->h_addr))->s_addr; /*inet_addr("192.168.1.2"); */
      free(tmphstbuf);
   }
   else
      serv_addr.sin_addr.s_addr = htonl(INADDR_ANY);
   serv_addr.sin_port = htons((u_short)cb->portCB);

   if (bind(cb->listenSocket, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
      if (cb->logLevel>=LOG_WARN) cb->log(cb->logUserP, cb->logLevel, LOG_WARN, __FILE__,
         "Failed binding port for callback server -dispatch/callback/plugin/socket/hostname %s -dispatch/callback/plugin/socket/port %d",
            cb->hostCB, cb->portCB);
      cb->isShutdown = true;
      return false;
   }

   /*
      * Listen on the socket.
      */
   if (listen(cb->listenSocket, 5) < 0) {
      if (cb->logLevel>=LOG_WARN) cb->log(cb->logUserP, cb->logLevel, LOG_WARN, __FILE__,
         "Failed creating listener for callback server -dispatch/callback/plugin/socket/hostname %s -dispatch/callback/plugin/socket/port %d",
            cb->hostCB, cb->portCB);
      cb->isShutdown = true;
      return false;
   }

   if (cb->logLevel>=LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, LOG_TRACE, __FILE__,
      "[CallbackServerUnparsed] Waiting for xmlBlaster to connect ...");

   /*
      * Accept connections.  When we accept one, ns
      * will be connected to the client.  cli_addr will
      * contain the address of the client.
      */
   cli_len = (socklen_t)sizeof(cli_addr);
   if ((cb->acceptSocket = (int)accept(cb->listenSocket, (struct sockaddr *)&cli_addr, &cli_len)) < 0) {
         perror("[CallbackServerUnparsed] accept");
         cb->isShutdown = true;
         return false;
   }
   if (cb->logLevel>=LOG_INFO) cb->log(cb->logUserP, cb->logLevel, LOG_INFO, __FILE__,
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
 * This method blocks until data arrives.
 * <br />
 * The socketDataHolder holds all informations about the returned data from xmlBlaster,
 * on error the exception struct is filled.
 *
 * @param socketDataHolder You need to free(socketDataHolder->data) if return is 'true'.
 * @param exception Contains the exception thrown (on error only)
 * @return true if OK or on exception, false on EOF
 */
static bool readMessage(CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, XmlBlasterException *exception)
{
   return parseSocketData(cb->acceptSocket, socketDataHolder, exception, cb->logLevel >= LOG_DUMP);
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
   memset(&msgUnit, 0, sizeof(MsgUnit));

   for (i=0; i<msgUnitArrP->len; i++) {
      if (cb->logLevel>=LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, LOG_TRACE, __FILE__,
         "Returning the UpdateReturnQos '%s' to the server.",
            msgUnitArrP->msgUnitArr[i].responseQos);

      if (msgUnitArrP->msgUnitArr[i].responseQos != 0) {
         msgUnit.qos = msgUnitArrP->msgUnitArr[i].responseQos;
      }
      else {
         msgUnit.qos = strcpyAlloc("<qos/>");
      }

      if (data == 0) {
         BlobHolder blob = encodeMsgUnit(&msgUnit, cb->logLevel >= LOG_DUMP);
         data = blob.data;
         dataLen = blob.dataLen;
      }
      else {
         BlobHolder blob = encodeMsgUnit(&msgUnit, cb->logLevel >= LOG_DUMP);
         data = (char *)realloc(data, dataLen+blob.dataLen);
         memcpy(data+dataLen, blob.data, blob.dataLen);
         dataLen += blob.dataLen;
         free(blob.data);
      }
   }

   rawMsg = encodeSocketMessage(MSG_TYPE_RESPONSE, socketDataHolder->requestId,
                             socketDataHolder->methodName, socketDataHolder->secretSessionId,
                             data, dataLen, cb->logLevel >= LOG_DUMP, &rawMsgLen);
   free(data);

   /*ssize_t numSent =*/(void) writen(cb->acceptSocket, rawMsg, (int)rawMsgLen);

   free(rawMsg);
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
   
   blob = encodeMsgUnit(&msgUnit, cb->logLevel >= LOG_DUMP);

   rawMsg = encodeSocketMessage(MSG_TYPE_EXCEPTION, socketDataHolder->requestId,
                             socketDataHolder->methodName, socketDataHolder->secretSessionId,
                             blob.data, blob.dataLen, cb->logLevel >= LOG_DUMP, &rawMsgLen);
   free(blob.data);
   free((char *)msgUnit.content);

   /*ssize_t numSent =*/(void) writen(cb->acceptSocket, rawMsg, (int)rawMsgLen);

   free(rawMsg);
}

/** A helper to cast to SocketDataHolder */
static void voidSendResponseOrException(bool success, CallbackServerUnparsed *cb, void *socketDataHolder, MsgUnitArr *msgUnitArrP, XmlBlasterException *exception)
{
   sendResponseOrException(success, cb, (SocketDataHolder *)socketDataHolder, msgUnitArrP, exception);
}

/** takes care of both successful responses as well as exceptions */
static void sendResponseOrException(bool success, CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, MsgUnitArr *msgUnitArrP, XmlBlasterException *exception)
{
   if (! (strcmp(socketDataHolder->methodName, XMLBLASTER_UPDATE_ONEWAY) == 0)) {
      if (success == true) {
         if (cb->logLevel>=LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, LOG_TRACE, __FILE__,
            "update(): Sending response for requestId '%s'", socketDataHolder->requestId);
         sendResponse(cb, socketDataHolder, msgUnitArrP);
      }
      else {
         if (*(exception->errorCode) == 0) {
            if (cb->logLevel>=LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, LOG_TRACE, __FILE__,
               "update(): We don't return anything for requestId '%s', the return message will come later by the client update dispatcher thread", socketDataHolder->requestId);
         }
         else {
            if (cb->logLevel>=LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, LOG_TRACE, __FILE__,
               "update(): Throwing the XmlBlasterException '%s' back to the server:\n%s",
                   exception->errorCode, exception->message);
            sendXmlBlasterException(cb, socketDataHolder, exception);
         }
      }
   }

   freeMsgUnitArr(msgUnitArrP);
}

static void closeAcceptSocket(CallbackServerUnparsed *cb)
{
   if (!cb->reusingConnectionSocket) {
      return; /* not our duty, we only have borrowed the socket from the client side connection */
   }

   if (cb->acceptSocket != -1) {
#     ifdef _WINDOWS
      closesocket(cb->acceptSocket);
#     else
      (void)close(cb->acceptSocket);
#     endif
      cb->acceptSocket = -1;
      if (cb->logLevel>=LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, LOG_TRACE, __FILE__,
         "Closed accept socket");
   }
}

/**
 * Used internally only to close the socket, it blocks until the thread is dead.
 */
static void shutdownCallbackServer(CallbackServerUnparsed *cb)
{
   if (!cb->reusingConnectionSocket) {
      return; /* not our duty, we only have borrowed the socket from the client side connection */
   }

   if (cb->hostCB != 0) {
      free(cb->hostCB);
      cb->hostCB = 0;
   }

   closeAcceptSocket(cb);

   if (isListening(cb)) {
#  ifdef _WINDOWS
      closesocket(cb->listenSocket);
#  else
      (void)close(cb->listenSocket);
#  endif
      cb->listenSocket = -1;
      if (cb->logLevel>=LOG_TRACE) cb->log(cb->logUserP, cb->logLevel, LOG_TRACE, __FILE__,
         "Closed listener socket");
   }

   /*
   for(i=0; i<10; i++) {
      if (cb->isShutdown) {
         return;
      }
      if (cb->debug) printf("[CallbackServerUnparsed] Waiting for thread to die ...");
      sleepMillis(1000);
   }
   printf("[CallbackServerUnparsed] WARNING: Thread has not died after 10 sec");
   */
}

const char *callbackServerRawUsage()
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
   freeCallbackServerUnparsed(cb);
   return 0;
}
#endif /* USE_MAIN_CB */
