/*----------------------------------------------------------------------------
Name:      XmlBlasterConnectionUnparsed.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Wraps raw socket connection to xmlBlaster
           for complete synchronous xmlBlaster access,
           without callbacks and not threading necessary
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <errno.h>
#include <sys/types.h>
#include <socket/xmlBlasterSocket.h>
#include <XmlBlasterConnectionUnparsed.h>


static bool initConnection(XmlBlasterConnectionUnparsed *xb);
static bool getResponse(XmlBlasterConnectionUnparsed *xb, SocketDataHolder *responseHolder, XmlBlasterException *exception);
static char *xmlBlasterConnect(XmlBlasterConnectionUnparsed *xb, const char * const qos, XmlBlasterException *exception);
static bool xmlBlasterDisconnect(XmlBlasterConnectionUnparsed *xb, const char * const qos, XmlBlasterException *exception);
static char *xmlBlasterPublish(XmlBlasterConnectionUnparsed *xb, MsgUnit *msgUnit, XmlBlasterException *exception);
static char *xmlBlasterSubscribe(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
static char *xmlBlasterUnSubscribe(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
static char *xmlBlasterErase(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
static MsgUnitArr *xmlBlasterGet(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
static char *xmlBlasterPing(XmlBlasterConnectionUnparsed *xb, const char * const qos);
static bool isConnected(XmlBlasterConnectionUnparsed *xb);
static void xmlBlasterConnectionShutdown(XmlBlasterConnectionUnparsed *xb);

/**
 * Bootstrap the connection. 
 * This is usually the first call of a client.
 * @return NULL if bootstrapping failed. If not NULL you need to free() it when you are done
 * usually by calling freeXmlBlasterConnectionUnparsed().
 */
XmlBlasterConnectionUnparsed *getXmlBlasterConnectionUnparsed(int argc, char** argv) {
   XmlBlasterConnectionUnparsed *xb = (XmlBlasterConnectionUnparsed *)calloc(1, sizeof(XmlBlasterConnectionUnparsed));
   xb->argc = argc;
   xb->argv = argv;
   xb->socketToXmlBlaster = -1;
   xb->isInitialized = false;
   xb->requestId = 0;
   *xb->secretSessionId = 0;
   xb->connect = xmlBlasterConnect;
   xb->disconnect = xmlBlasterDisconnect;
   xb->publish = xmlBlasterPublish;
   xb->subscribe = xmlBlasterSubscribe;
   xb->unSubscribe = xmlBlasterUnSubscribe;
   xb->erase = xmlBlasterErase;
   xb->get = xmlBlasterGet;
   xb->ping = xmlBlasterPing;
   xb->isConnected = isConnected;
   xb->preSendEvent = 0;
   xb->preSendEvent_userP = 0;
   xb->postSendEvent = 0;
   xb->postSendEvent_userP = 0;
   xb->debug = false;
   if (initConnection(xb) == false) {
      free(xb);
      return (XmlBlasterConnectionUnparsed *)0;
   }
   return xb;
}

void freeXmlBlasterConnectionUnparsed(XmlBlasterConnectionUnparsed *xb)
{
   if (xb != 0) {
      xmlBlasterConnectionShutdown(xb),
      free(xb);
   }
}

static bool initConnection(XmlBlasterConnectionUnparsed *xb)
{
   int iarg;
   char *servTcpPort = "7607";

   struct sockaddr_in xmlBlasterAddr;
   struct hostent hostbuf, *hostP = 0;
   struct servent *portP = 0;

   char *tmphstbuf=0;
   size_t hstbuflen=0;

   char serverHostName[256];

#  ifdef _WINDOWS
   WORD wVersionRequested;
   WSADATA wsaData;
   int err;
   wVersionRequested = MAKEWORD( 2, 2 );
   err = WSAStartup( wVersionRequested, &wsaData );
   if ( err != 0 ) {
      return false; /* Tell the user that we could not find a usable WinSock DLL */
   }

   /* Confirm that the WinSock DLL supports 2.2. */
   if ( LOBYTE( wsaData.wVersion ) != 2 ||
   HIBYTE( wsaData.wVersion ) != 2 ) {
      WSACleanup( );
      return false; 
   }
# endif

   if (xb->isInitialized) {
      return true;
   }

   strcpy(serverHostName, "localhost");
   gethostname(serverHostName, 250);

   for (iarg=0; iarg < xb->argc-1; iarg++) {
      if (strcmp(xb->argv[iarg], "-dispatch/connection/plugin/socket/hostname") == 0)
         strncpy0(serverHostName, xb->argv[++iarg], 250);
      else if (strcmp(xb->argv[iarg], "-dispatch/connection/plugin/socket/port") == 0)
         servTcpPort = xb->argv[++iarg];
      else if (strcmp(xb->argv[iarg], "-debug") == 0)
         xb->debug = !strcmp(xb->argv[++iarg], "true");
   }

   if (xb->debug) printf("[XmlBlasterConnectionUnparsed] Lookup xmlBlaster on -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %s ...\n", serverHostName, servTcpPort);

   *xb->secretSessionId = 0;
   memset((char *)&xmlBlasterAddr, 0, sizeof(xmlBlasterAddr));
   xmlBlasterAddr.sin_family=AF_INET;

# if _WINDOWS_NOT_YET_PORTED /* Windows gethostbyname is deprecated */
   const struct addrinfo hints;
   struct addrinfo** res;
   int getaddrinfo(serverHostName, servTcpPort, &hints, res);
   res->ai_next : ai_family, ai_socktype, and ai_protocol

   ...

   void freeaddrinfo(*res);
# endif
   hostP = gethostbyname_re(serverHostName, &hostbuf, &tmphstbuf, &hstbuflen);
   /* printf("gethostbyname error=%d\n", WSAGetLastError()); */
   portP = getservbyname(servTcpPort, "tcp");
   if (hostP != 0) {
      xmlBlasterAddr.sin_addr.s_addr = ((struct in_addr *)(hostP->h_addr))->s_addr; /* inet_addr("192.168.1.2"); */
      free(tmphstbuf);
      if (portP != 0)
         xmlBlasterAddr.sin_port = portP->s_port;
      else
         xmlBlasterAddr.sin_port = htons((u_short)atoi(servTcpPort));
      xb->socketToXmlBlaster = (int)socket(AF_INET, SOCK_STREAM, 0);
      if (xb->socketToXmlBlaster != -1) {
         int ret=0;
         if ((ret=connect(xb->socketToXmlBlaster, (struct sockaddr *)&xmlBlasterAddr, sizeof(xmlBlasterAddr))) != -1) {
            if (xb->debug) printf("[XmlBlasterConnectionUnparsed] Connected to xmlBlaster\n");
         }
         else {
            if (xb->debug) {
               char errnoStr[MAX_ERRNO_LEN];
               sprintf(errnoStr, "errno=%d %s", errno, strerror(errno)); /* default if strerror_r fails */
#              ifdef _LINUX
               strerror_r(errno, errnoStr, MAX_ERRNO_LEN-1); /* glibc > 2. returns a char*, but should return an int */
#              endif
               printf("[XmlBlasterConnectionUnparsed] ERROR Connecting to xmlBlaster (connect failed) -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %s failed, %s\n", serverHostName, servTcpPort, errnoStr);
            }
            return false;
         }
      }
      else {
         if (xb->debug) printf("[XmlBlasterConnectionUnparsed] ERROR Connecting to xmlBlaster (socket=-1) -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %s failed errno=%d\n", serverHostName, servTcpPort, errno);
         return false;
      }
   }
   else {
      if (xb->debug) printf("[XmlBlasterConnectionUnparsed] ERROR Connecting to xmlBlaster (hostP=0) -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %s failed errno=%d\n", serverHostName, servTcpPort, errno);
      return false;
   }
   xb->isInitialized = true;
   return true;
}

static bool isConnected(XmlBlasterConnectionUnparsed *xb)
{
   return (xb->socketToXmlBlaster > -1) ? true : false;
}

const char *xmlBlasterConnectionUnparsedUsage()
{
   return 
      "\n  -dispatch/connection/plugin/socket/hostname [localhost]"
      "\n                       Where to find xmlBlaster"
      "\n  -dispatch/connection/plugin/socket/port [7607]"
      "\n                       The port where xmlBlaster listens";
}

/**
 * Used internally only, does no disconnect, only cleanup of socket
 */
static void xmlBlasterConnectionShutdown(XmlBlasterConnectionUnparsed *xb)
{
   if (xb->isConnected(xb)) {
      shutdown(xb->socketToXmlBlaster, 2);
      xb->socketToXmlBlaster = -1;
   }
}

/**
 * Send a message over the socket to xmlBlaster. 
 * @param methodName The name of the remote method to invoke e.g. "connect"
 * @param msgType The type of message: INVOKE, RESPONSE, EXCEPTION
 * @param data The message payload to send
 * @param responseHolder The returned data, you need to free it with free(response->data) if we returned true.
 *        Supply NULL for oneway messages.
 * @param exception The exception struct, exception->errorCode is filled on exception.
 *        You need to supply it.
 * @return true if OK and response is filled (if not oneway or exception or response itself)<br />
           false on error and exception is filled
 */
static bool sendData(XmlBlasterConnectionUnparsed *xb, 
              const char * const methodName,
              enum XMLBLASTER_MSG_TYPE_ENUM msgType,
              const char *data_,
              size_t dataLen_,
              SocketDataHolder *responseHolder,
              XmlBlasterException *exception)
{
   ssize_t numSent;
   size_t rawMsgLen = 0;
   char *rawMsg = (char *)0;
   char *rawMsgStr;
   size_t lenUnzipped = dataLen_;
   char requestIdStr[MAX_REQUESTID_LEN];

   if (data_ == 0) {
      data_ = "";
      dataLen_ = 0;
      lenUnzipped = 0;
   }

   if (exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterConnectionUnparsed] Please provide valid exception to sendData()");
      if (xb->debug) { printf(exception->message); printf("\n"); }
      return false;
   }
   initializeXmlBlasterException(exception);

   if (!xb->isConnected(xb)) {
      strncpy0(exception->errorCode, "user.notConnected", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterConnectionUnparsed] No connection to xmlBlaster\n");
      if (xb->debug) { printf(exception->message); printf("\n"); }
      return false;
   }

   if (strcmp(XMLBLASTER_CONNECT, methodName) && strlen(xb->secretSessionId) < 1) {
      strncpy0(exception->errorCode, "user.notConnected", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterConnectionUnparsed] Please call connect() before invoking '%s'\n", methodName);
      if (xb->debug) { printf(exception->message); printf("\n"); }
      return false;
   }

   if (xb->debug) printf("[XmlBlasterConnectionUnparsed] sendData() requestId '%ld' increment to '%ld', dataLen=%d\n",
                           xb->requestId, xb->requestId+1, dataLen_);
   xb->requestId++;
   if (xb->requestId > 1000000000) xb->requestId = 0;
   sprintf(requestIdStr, "%-ld", xb->requestId);

   if (xb->preSendEvent != 0) {
      /* A callback function pointer is registered to be notified just before sending */
      MsgRequestInfo *requestInfoP;
      MsgRequestInfo requestInfo;
      XmlBlasterBlob blob;
      blobcpyAlloc(&blob, data_, dataLen_); /* Take a clone, the preSendEvent() function may manipulate it */
      requestInfo.methodName = methodName;
      requestInfo.requestIdStr = requestIdStr;
      requestInfo.blob.dataLen = blob.dataLen;
      requestInfo.blob.data = blob.data;
      requestInfoP = xb->preSendEvent(xb->preSendEvent_userP, &requestInfo, exception);
      if (*exception->message != 0) {
         if (xb->debug) printf("[XmlBlasterConnectionUnparsed] Re-throw exception from preSendEvent errorCode=%s message=%s\n", exception->errorCode, exception->message);
         return false;
      }
      if (requestInfoP == 0) {
         strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
         sprintf(exception->message, "[XmlBlasterConnectionUnparsed] ERROR: returning requestInfo 0 without exception is not supported, please correct your preSendEvent() function.");
         if (xb->debug) { printf(exception->message); printf("\n"); }
         return false;
      }
      if (blob.data != requestInfoP->blob.data) {
         /* The callback function has changed/manipulated the user data */
         freeXmlBlasterBlobContent(&blob);
      }
      rawMsg = encodeSocketMessage(msgType, requestIdStr, methodName, xb->secretSessionId,
                             requestInfoP->blob.data, requestInfoP->blob.dataLen, xb->debug, &rawMsgLen);
      freeXmlBlasterBlobContent(&requestInfoP->blob);
   }
   else {
      rawMsg = encodeSocketMessage(msgType, requestIdStr, methodName, xb->secretSessionId,
                             data_, dataLen_, xb->debug, &rawMsgLen);
   }
   
   /* send the header ... */
   numSent = writen(xb->socketToXmlBlaster, rawMsg, (int)rawMsgLen);

   if (numSent == -1) {
      if (xb->debug) printf("[XmlBlasterConnectionUnparsed] Lost connection to xmlBlaster server\n");
      xmlBlasterConnectionShutdown(xb);
      strncpy0(exception->errorCode, "user.notConnected", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterConnectionUnparsed] Lost connection to xmlBlaster server\n");
      return false;
   }

   if (numSent != (int)rawMsgLen) {
      if (xb->debug) printf("[XmlBlasterConnectionUnparsed] ERROR Sent only %d bytes from %u\n", numSent, rawMsgLen);
      strncpy0(exception->errorCode, "user.connect", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterConnectionUnparsed] ERROR Sent only %d bytes from %u\n", numSent, rawMsgLen);
      free(rawMsg);
      return false;
   }

   free(rawMsg);
   rawMsg = 0;

   if (msgType==MSG_TYPE_RESPONSE || msgType==MSG_TYPE_EXCEPTION)
      return true;

   if (responseHolder) { /* if not oneway read the response message */

      if (xb->postSendEvent != 0) {
         /* A callback function pointer is registered to be notified just after sending */
         MsgRequestInfo *requestInfoP;
         MsgRequestInfo requestInfo;
         requestInfo.methodName = methodName;
         requestInfo.requestIdStr = requestIdStr;
         requestInfo.blob.dataLen = 0;
         requestInfo.blob.data = 0;
         requestInfoP = xb->postSendEvent(xb->postSendEvent_userP, &requestInfo, exception);
         if (*exception->message != 0) {
            if (xb->debug) printf("[XmlBlasterConnectionUnparsed] Re-throw exception from preSendEvent errorCode=%s message=%s\n", exception->errorCode, exception->message);
            return false;
         }
         if (requestInfoP == 0) {
            printf("[XmlBlasterConnectionUnparsed] TODO: returning requestInfo 0 is not implemented");
         }
         memset(responseHolder, 0, sizeof(SocketDataHolder));
         responseHolder->type = msgType;
         responseHolder->version = XMLBLASTER_VERSION;
         strncpy0(responseHolder->requestId, requestIdStr, MAX_REQUESTID_LEN);
         strncpy0(responseHolder->methodName, methodName, MAX_METHODNAME_LEN);
         responseHolder->blob.dataLen = requestInfoP->blob.dataLen;
         responseHolder->blob.data = requestInfoP->blob.data;     /* The responseHolder is now responsible to free(responseHolder->blob.data) */
         if (xb->debug) printf("[XmlBlasterConnectionUnparsed] requestId '%s' returns dataLen=%d\n", requestIdStr, requestInfoP->blob.dataLen);
      }
      else {
         /* Wait on the response ourself */
         if (getResponse(xb, responseHolder, exception) == false) {
            if (xb->debug) printf("[XmlBlasterConnectionUnparsed] Lost connection to xmlBlaster server\n");
            xmlBlasterConnectionShutdown(xb);
            strncpy0(exception->errorCode, "user.notConnected", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
            sprintf(exception->message, "[XmlBlasterConnectionUnparsed] Lost connection to xmlBlaster server\n");
            return false;
         }
      }

      if (xb->debug) {
         rawMsgStr = blobDump(&responseHolder->blob);
         printf("[XmlBlasterConnectionUnparsed] Received response msgLen=%u type=%c version=%c requestId=%s methodName=%s dateLen=%u data='%.100s ...'\n",
                  responseHolder->msgLen, responseHolder->type, responseHolder->version, responseHolder->requestId,
                  responseHolder->methodName, responseHolder->blob.dataLen, rawMsgStr);
         free(rawMsgStr);
      }
   }

   return true;
}

/**
 * Parse the returned message from xmlBlaster. 
 * This method blocks until data arrives.
 * <br />
 * The responseHolder holds all informations about the returned data from xmlBlaster,
 * on error the exception struct is filled.
 *
 * @param responseHolder You need to free(responseHolder->data) if return is 'true'.
 * @param exception Contains the exception thrown (on error only *exception->errorCode!=0)
 * @return true if OK or on exception, false on EOF
 */
static bool getResponse(XmlBlasterConnectionUnparsed *xb, SocketDataHolder *responseHolder, XmlBlasterException *exception)
{
   return parseSocketData(xb->socketToXmlBlaster, responseHolder, exception, xb->debug);
}

/**
 * Connect to the server. 
 * @param qos The QoS to connect
 * @param The exception struct, exception->errorCode is filled on exception
 * @return The raw ConnectReturnQos XML string returned from xmlBlaster,
 *         only NULL if an exception is thrown.
 *         You need to free() it
 * @return The ConnectReturnQos raw xml string, you need to free() it
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.connect.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterConnect(XmlBlasterConnectionUnparsed *xb, const char * const qos, XmlBlasterException *exception)
{
   SocketDataHolder responseHolder;
   char *response;
   
   if (qos == 0 || exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterConnectionUnparsed] Please provide valid arguments to xmlBlasterConnect()");
      if (xb->debug) { printf(exception->message); printf("\n"); }
      return (char *)0;
   }

   if (initConnection(xb) == false) {
      strncpy0(exception->errorCode, "user.configuration", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterConnectionUnparsed] No connection to xmlBlaster, check your configuration");
      if (xb->debug) { printf(exception->message); printf("\n"); }
      return (char *)0;
   }

   if (sendData(xb, XMLBLASTER_CONNECT, MSG_TYPE_INVOKE, (const char *)qos,
                (qos == (const char *)0) ? 0 : strlen(qos),
                &responseHolder, exception) == false) {
      return (char *)0;
   }

   response = strFromBlobAlloc(responseHolder.blob.data, responseHolder.blob.dataLen);
   freeXmlBlasterBlobContent(&responseHolder.blob);

   /* Extract secret session ID from ConnectReturnQos */
   *xb->secretSessionId = 0;
   {
      const char *pEnd = (const char *)0;
      const char *pStart = strstr(response, "sessionId='");
      if (pStart) {
         pStart += strlen("sessionId='");
         pEnd = strstr(pStart, "'");
         if (pEnd) {
            int len = (int)(pEnd - pStart + 1);
            if (len >= MAX_SECRETSESSIONID_LEN) {
               strncpy0(exception->errorCode, "user.response", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
               sprintf(exception->message, "[XmlBlasterConnectionUnparsed] ERROR Received too long secret sessionId with len=%d, please change setting MAX_SECRETSESSIONID_LEN", len);
               if (xb->debug) { printf(exception->message); printf("\n"); }
            }
            strncpy0(xb->secretSessionId, pStart, len);
         }
      }
   }

   if (xb->debug) printf("[XmlBlasterConnectionUnparsed] Got response for connect(secretSessionId=%s)\n", xb->secretSessionId);

   return response;
}

/**
 * Disconnect from server. 
 * @param qos The QoS to disconnect
 * @param The exception struct, exception->errorCode is filled on exception
 * @return false on exception
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.disconnect.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static bool xmlBlasterDisconnect(XmlBlasterConnectionUnparsed *xb, const char * const qos, XmlBlasterException *exception)
{
   if (exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterConnectionUnparsed] Please provide valid arguments to xmlBlasterDisconnect()");
      if (xb->debug) { printf(exception->message); printf("\n"); }
      return false;
   }

   if (sendData(xb, XMLBLASTER_DISCONNECT, MSG_TYPE_INVOKE, (const char *)qos, 
                (qos == (const char *)0) ? 0 : strlen(qos),
                0, exception) == false) {
      return false;
   }

   xmlBlasterConnectionShutdown(xb);
   *xb->secretSessionId = 0;
   return true;
}

/**
 * Publish a message to the server. 
 * @return The raw XML string returned from xmlBlaster, only NULL if an exception is thrown
 *         You need to free() it
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterPublish(XmlBlasterConnectionUnparsed *xb, MsgUnit *msgUnit, XmlBlasterException *exception)
{
   size_t totalLen;
   SocketDataHolder responseHolder;
   char *response = 0;

   char *data = encodeMsgUnit(msgUnit, &totalLen, xb->debug);

   if (sendData(xb, XMLBLASTER_PUBLISH, MSG_TYPE_INVOKE, data, totalLen,
                &responseHolder, exception) == false) {
      free(data);
      return 0;
   }
   free(data);

   response = strFromBlobAlloc(responseHolder.blob.data, responseHolder.blob.dataLen);
   freeXmlBlasterBlobContent(&responseHolder.blob);

   return response;
}

/**
 * Subscribe a message. 
 * @return The raw XML string returned from xmlBlaster, only NULL if an exception is thrown
 *         You need to free() it
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.subscribe.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterSubscribe(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception)
{
   size_t qosLen, keyLen, totalLen;
   char *data;
   size_t currpos = 0;
   SocketDataHolder responseHolder;
   char *response;

   if (key == 0 || exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterConnectionUnparsed] Please provide valid arguments to xmlBlasterSubscribe()");
      if (xb->debug) { printf(exception->message); printf("\n"); }
      return (char *)0;
   }

   if (qos == (const char *)0) {
      qos = "";
   }
   qosLen = strlen(qos);
   keyLen = strlen(key);

   totalLen = qosLen + 1 + keyLen + 1;

   data = (char *)malloc(totalLen);

   memcpy(data+currpos, qos, qosLen+1); /* inclusive '\0' */
   currpos += qosLen+1;

   memcpy(data+currpos, key, keyLen+1); /* inclusive '\0' */
   currpos += keyLen+1;

   if (sendData(xb, XMLBLASTER_SUBSCRIBE, MSG_TYPE_INVOKE, data, totalLen,
                &responseHolder, exception) == false) {
      free(data);
      return (char *)0;
   }
   free(data);

   response = strFromBlobAlloc(responseHolder.blob.data, responseHolder.blob.dataLen);
   freeXmlBlasterBlobContent(&responseHolder.blob);

   if (xb->debug) printf("[XmlBlasterConnectionUnparsed] Got response for subscribe(): %s\n", response);

   return response;
}

/**
 * UnSubscribe a message from the server. 
 * @return The raw XML string returned from xmlBlaster, only NULL if an exception is thrown
 *         You need to free() it
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterUnSubscribe(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception)
{
   size_t qosLen, keyLen, totalLen;
   char *data;
   size_t currpos = 0;
   SocketDataHolder responseHolder;
   char *response;

   if (key == 0 || exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterConnectionUnparsed] Please provide valid arguments to xmlBlasterUnSubscribe()");
      if (xb->debug) { printf(exception->message); printf("\n"); }
      return (char *)0;
   }

   if (qos == (const char *)0) {
      qos = "";
   }
   qosLen = strlen(qos);
   keyLen = strlen(key);

   totalLen = qosLen + 1 + keyLen + 1;

   data = (char *)malloc(totalLen);

   memcpy(data+currpos, qos, qosLen+1); /* inclusive '\0' */
   currpos += qosLen+1;

   memcpy(data+currpos, key, keyLen+1); /* inclusive '\0' */
   currpos += keyLen+1;

   if (sendData(xb, XMLBLASTER_UNSUBSCRIBE, MSG_TYPE_INVOKE, data, totalLen,
                &responseHolder, exception) == false) {
      free(data);
      return (char *)0;
   }
   free(data);

   response = strFromBlobAlloc(responseHolder.blob.data, responseHolder.blob.dataLen);
   freeXmlBlasterBlobContent(&responseHolder.blob);

   if (xb->debug) printf("[XmlBlasterConnectionUnparsed] Got response for unSubscribe(): %s\n", response);

   return response;
}

/**
 * Erase a message from the server. 
 * @return The raw XML string returned from xmlBlaster, only NULL if an exception is thrown
 *         You need to free() it
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.erase.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterErase(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception)
{
   size_t qosLen, keyLen, totalLen;
   char *data;
   size_t currpos = 0;
   SocketDataHolder responseHolder;
   char *response;

   if (key == 0 || exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterConnectionUnparsed] Please provide valid arguments to xmlBlasterErase()");
      if (xb->debug) { printf(exception->message); printf("\n"); }
      return (char *)0;
   }

   if (qos == (const char *)0) {
      qos = "";
   }
   qosLen = strlen(qos);
   keyLen = strlen(key);

   totalLen = qosLen + 1 + keyLen + 1;

   data = (char *)malloc(totalLen);

   memcpy(data+currpos, qos, qosLen+1); /* inclusive '\0' */
   currpos += qosLen+1;

   memcpy(data+currpos, key, keyLen+1); /* inclusive '\0' */
   currpos += keyLen+1;

   if (sendData(xb, XMLBLASTER_ERASE, MSG_TYPE_INVOKE, data, totalLen,
                &responseHolder, exception) == false) {
      free(data);
      return (char *)0;
   }
   free(data);

   response = strFromBlobAlloc(responseHolder.blob.data, responseHolder.blob.dataLen);
   freeXmlBlasterBlobContent(&responseHolder.blob);

   if (xb->debug) printf("[XmlBlasterConnectionUnparsed] Got response for erase(): %s\n", response);

   return response;
}

/**
 * Ping the server. 
 * @param qos The QoS or 0
 * @return The ping return QoS raw xml string, you need to free() it or 0 on failure
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterPing(XmlBlasterConnectionUnparsed *xb, const char * const qos)
{
   SocketDataHolder responseHolder;
   char *response;
   XmlBlasterException exception;

   if (!xb->isInitialized || !xb->isConnected(xb)) return (char *)0;
   
   if (sendData(xb, XMLBLASTER_PING, MSG_TYPE_INVOKE, (const char *)qos,
                (qos == (const char *)0) ? 0 : strlen(qos),
                &responseHolder, &exception) == false) {
      return (char *)0;
   }

   response = strFromBlobAlloc(responseHolder.blob.data, responseHolder.blob.dataLen);
   freeXmlBlasterBlobContent(&responseHolder.blob);
   if (xb->debug) printf("[XmlBlasterConnectionUnparsed] Got response for ping '%s'\n", response);
   return response;
}

/**
 * Get a message. 
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.get.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @return NULL on error, please check exception in such a case, you need to 
 *         call freeMsgUnitArr(msgUnitArr); after usage.
 */
static MsgUnitArr *xmlBlasterGet(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception)
{
   size_t qosLen, keyLen, totalLen;
   char *data;
   size_t currpos = 0;
   SocketDataHolder responseHolder;
   MsgUnitArr *msgUnitArr = 0;

   if (key == 0 || exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterConnectionUnparsed] Please provide valid arguments to xmlBlasterGet()");
      if (xb->debug) { printf(exception->message); printf("\n"); }
      return (MsgUnitArr *)0;
   }

   if (qos == (const char *)0) qos = "";
   qosLen = strlen(qos);
   keyLen = strlen(key);

   totalLen = qosLen + 1 + keyLen + 1;

   data = (char *)malloc(totalLen);

   memcpy(data+currpos, qos, qosLen+1); /* inclusive '\0' */
   currpos += qosLen+1;

   memcpy(data+currpos, key, keyLen+1); /* inclusive '\0' */
   currpos += keyLen+1;

   if (sendData(xb, XMLBLASTER_GET, MSG_TYPE_INVOKE, data, totalLen,
                &responseHolder, exception) == false) {
      free(data);
      return (MsgUnitArr *)0; /* exception is filled with details */
   }
   free(data);

   /* Now process the returned messages */

   msgUnitArr = parseMsgUnitArr(responseHolder.blob.dataLen, responseHolder.blob.data);
   freeXmlBlasterBlobContent(&responseHolder.blob);

   if (xb->debug) printf("[XmlBlasterConnectionUnparsed] Returned %u messages for get()\n", msgUnitArr->len);

   return msgUnitArr;
}
