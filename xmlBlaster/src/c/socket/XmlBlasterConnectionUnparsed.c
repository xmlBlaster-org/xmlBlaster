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

#ifndef _WINDOWS
#  include <unistd.h>
#endif

static bool initConnection(XmlBlasterConnectionUnparsed *xb, XmlBlasterException *exception);
static bool getResponse(XmlBlasterConnectionUnparsed *xb, SocketDataHolder *responseSocketDataHolder, XmlBlasterException *exception);
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
static bool checkArgs(XmlBlasterConnectionUnparsed *xb, const char *methodName, XmlBlasterException *exception);

/**
 * Create a new instance to handle a synchronous connection to the server. 
 * This is usually the first call of a client.
 * @return NULL if bootstrapping failed. If not NULL you need to free() it when you are done
 * usually by calling freeXmlBlasterConnectionUnparsed().
 */
XmlBlasterConnectionUnparsed *getXmlBlasterConnectionUnparsed(int argc, char** argv) {
   XmlBlasterConnectionUnparsed *xb = (XmlBlasterConnectionUnparsed *)calloc(1, sizeof(XmlBlasterConnectionUnparsed));
   if (xb == 0) return xb;
   xb->argc = argc;
   xb->argv = argv;
   xb->props = createProperties(xb->argc, xb->argv);
   if (xb->props == 0) {
      freeXmlBlasterConnectionUnparsed(xb);
      return (XmlBlasterConnectionUnparsed *)0;
   }
   xb->socketToXmlBlaster = -1;
   xb->isInitialized = false;
   xb->requestId = 0;
   *xb->secretSessionId = 0;
   xb->initConnection = initConnection;
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
   xb->logLevel = parseLogLevel(xb->props->getString(xb->props, "logLevel", "WARN"));
   xb->log = xmlBlasterDefaultLogging;
   return xb;
}

void freeXmlBlasterConnectionUnparsed(XmlBlasterConnectionUnparsed *xb)
{
   if (xb != 0) {
      freeProperties(xb->props);
      xmlBlasterConnectionShutdown(xb),
      free(xb);
   }
}

/**
 * Connects on TCP/IP level to xmlBlaster
 * @return true If the low level TCP/IP connect to xmlBlaster succeeded
 */
static bool initConnection(XmlBlasterConnectionUnparsed *xb, XmlBlasterException *exception)
{
   const char *servTcpPort = 0;

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
      strncpy0(exception->errorCode, "resource.unavailable", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%.100s:%d] Couldn't find a usable WinSock DLL", __FILE__, __LINE__);
      if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__, exception->message);
      return false;
   }

   if ( LOBYTE( wsaData.wVersion ) != 2 ||
   HIBYTE( wsaData.wVersion ) != 2 ) {
      WSACleanup( );
      strncpy0(exception->errorCode, "resource.unavailable", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%.100s:%d] Couldn't find a usable WinSock DLL which supports version 2.2", __FILE__, __LINE__);
      if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__, exception->message);
      return false; 
   }
# endif

   if (xb->isInitialized) {
      return true;
   }

   servTcpPort = xb->props->getString(xb->props, "plugin/socket/port", "7607");
   servTcpPort = xb->props->getString(xb->props, "dispatch/connection/plugin/socket/port", servTcpPort);

   strcpy(serverHostName, "localhost");
   gethostname(serverHostName, 250);
   strncpy0(serverHostName, xb->props->getString(xb->props, "plugin/socket/hostname", serverHostName), 250);
   strncpy0(serverHostName, xb->props->getString(xb->props, "dispatch/connection/plugin/socket/hostname", serverHostName), 250);

   if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__,
      "Lookup xmlBlaster on -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %s ...",
      serverHostName, servTcpPort);

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
         const char *localHostName = xb->props->getString(xb->props, "plugin/socket/localHostname", 0);
         int localPort = xb->props->getInt(xb->props, "plugin/socket/localPort", 0);
         localHostName = xb->props->getString(xb->props, "dispatch/connection/plugin/socket/localHostname", localHostName);
         localPort = xb->props->getInt(xb->props, "dispatch/connection/plugin/socket/localPort", localPort);

         /* Sometimes a user may whish to force the local host/port setting (e.g. for firewall tunneling
            and on multi homed hosts */
         if (localHostName != 0 || localPort > 0) {
            struct sockaddr_in localAddr;
            struct hostent localHostbuf, *localHostP = 0;
            char *tmpLocalHostbuf=0;
            size_t localHostbuflen=0;
            memset(&localAddr, 0, sizeof(localAddr));
            localAddr.sin_family = AF_INET;
            if (localHostName) {
               localHostP = gethostbyname_re(localHostName, &localHostbuf, &tmpLocalHostbuf, &localHostbuflen);
               if (localHostP != 0) {
                  localAddr.sin_addr.s_addr = ((struct in_addr *)(localHostP->h_addr))->s_addr; /* inet_addr("192.168.1.2"); */
                  free(tmpLocalHostbuf);
               }
            }
            if (localPort > 0) {
               localAddr.sin_port = htons((unsigned short)localPort);
            }
            if (bind(xb->socketToXmlBlaster, (struct sockaddr *)&localAddr, sizeof(localAddr)) < 0) {
               if (xb->logLevel>=LOG_WARN) xb->log(xb->logLevel, LOG_WARN, __FILE__,
                  "Failed binding local port -dispatch/connection/plugin/socket/localHostname %s -dispatch/connection/plugin/socket/localPort %d",
                     localHostName, localPort);
            }
            else {
               xb->log(xb->logLevel, LOG_INFO, __FILE__,
                  "Bound local port -dispatch/connection/plugin/socket/localHostname %s -dispatch/connection/plugin/socket/localPort %d",
                     localHostName, localPort);
            }
         }

         if ((ret=connect(xb->socketToXmlBlaster, (struct sockaddr *)&xmlBlasterAddr, sizeof(xmlBlasterAddr))) != -1) {
            if (xb->logLevel>=LOG_INFO) xb->log(xb->logLevel, LOG_INFO, __FILE__, "Connected to xmlBlaster");
         }
         else {
            char errnoStr[MAX_ERRNO_LEN];
            SNPRINTF(errnoStr, MAX_ERRNO_LEN, "errno=%d %s", errno, strerror(errno)); /* default if strerror_r fails */
#           ifdef _LINUX
            strerror_r(errno, errnoStr, MAX_ERRNO_LEN-1); /* glibc > 2. returns a char*, but should return an int */
#           endif
            strncpy0(exception->errorCode, "user.configuration", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
            SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
                     "[%.100s:%d] Connecting to xmlBlaster -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %.10s failed, %s",
                     __FILE__, __LINE__, serverHostName, servTcpPort, errnoStr);
            if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__, exception->message);
            return false;
         }
      }
      else {
         strncpy0(exception->errorCode, "user.configuration", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
         SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
                  "[%.100s:%d] Connecting to xmlBlaster (socket=-1) -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %.10s failed errno=%d",
                  __FILE__, __LINE__, serverHostName, servTcpPort, errno);
         return false;
      }
   }
   else {
      strncpy0(exception->errorCode, "user.configuration", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
               "[%.100s:%d] Connecting to xmlBlaster failed, can't determine hostname (hostP=0), -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %.10s, errno=%d",
               __FILE__, __LINE__, serverHostName, servTcpPort, errno);
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
      "\n                       The port where xmlBlaster listens"
      "\n  -dispatch/connection/plugin/socket/localHostname [NULL]"
      "\n                       Force the local IP, useful on multi homed computers"
      "\n  -dispatch/connection/plugin/socket/localPort [0]"
      "\n                       Force the local port, useful to tunnel firewalls";
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
 * @param responseSocketDataHolder The returned data, you need to free it with free(response->data) if we returned true.
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
              SocketDataHolder *responseSocketDataHolder,
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
      xb->log(xb->logLevel, LOG_ERROR, __FILE__, "[%s:%d] Please provide valid exception to sendData()", __FILE__, __LINE__);
      return false;
   }
   initializeXmlBlasterException(exception);

   if (!xb->isConnected(xb)) {
      strncpy0(exception->errorCode, "user.notConnected", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] No connection to xmlBlaster", __FILE__, __LINE__);
      if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__, exception->message);
      return false;
   }

   if (strcmp(XMLBLASTER_CONNECT, methodName) && strlen(xb->secretSessionId) < 1) {
      strncpy0(exception->errorCode, "user.notConnected", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] Please call connect() before invoking '%s'", __FILE__, __LINE__, methodName);
      if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__, exception->message);
      return false;
   }

   if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__,
      "sendData() requestId '%ld' increment to '%ld', dataLen=%d",
      xb->requestId, xb->requestId+1, dataLen_);
   xb->requestId++;
   if (xb->requestId > 1000000000) xb->requestId = 0;
   SNPRINTF(requestIdStr, MAX_REQUESTID_LEN, "%-ld", xb->requestId);

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
         if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__,
            "Re-throw exception from preSendEvent errorCode=%s message=%s", exception->errorCode, exception->message);
         return false;
      }
      if (requestInfoP == 0) {
         strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
         SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] ERROR: returning requestInfo 0 without exception is not supported, please correct your preSendEvent() function.", __FILE__, __LINE__);
         if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__, exception->message);
         return false;
      }
      if (blob.data != requestInfoP->blob.data) {
         /* The callback function has changed/manipulated the user data */
         freeXmlBlasterBlobContent(&blob);
      }
      rawMsg = encodeSocketMessage(msgType, requestIdStr, methodName, xb->secretSessionId,
                             requestInfoP->blob.data, requestInfoP->blob.dataLen, xb->logLevel >= LOG_DUMP, &rawMsgLen);
      freeXmlBlasterBlobContent(&requestInfoP->blob);
   }
   else {
      rawMsg = encodeSocketMessage(msgType, requestIdStr, methodName, xb->secretSessionId,
                             data_, dataLen_, xb->logLevel >= LOG_DUMP, &rawMsgLen);
   }
   
   /* send the header ... */
   numSent = writen(xb->socketToXmlBlaster, rawMsg, (int)rawMsgLen);

   if (numSent == -1) {
      if (xb->logLevel>=LOG_WARN) xb->log(xb->logLevel, LOG_WARN, __FILE__,
                                   "Lost connection to xmlBlaster server");
      xmlBlasterConnectionShutdown(xb);
      strncpy0(exception->errorCode, "user.notConnected", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] Lost connection to xmlBlaster server", __FILE__, __LINE__);
      free(rawMsg);
      return false;
   }

   if (numSent != (int)rawMsgLen) {
      if (xb->logLevel>=LOG_ERROR) xb->log(xb->logLevel, LOG_ERROR, __FILE__,
         "Sent only %d bytes from %u", numSent, rawMsgLen);
      strncpy0(exception->errorCode, "user.connect", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] ERROR Sent only %d bytes from %u", __FILE__, __LINE__, numSent, rawMsgLen);
      free(rawMsg);
      return false;
   }

   free(rawMsg);
   rawMsg = 0;

   if (msgType==MSG_TYPE_RESPONSE || msgType==MSG_TYPE_EXCEPTION)
      return true; /* Responses and exceptions are oneway */

   if (responseSocketDataHolder) { /* if not oneway read the response message */

      if (xb->postSendEvent != 0) {
         /* A callback function pointer is registered to be notified just after sending */
         MsgRequestInfo *requestInfoP;
         MsgRequestInfo requestInfo;
         requestInfo.methodName = methodName;
         requestInfo.requestIdStr = requestIdStr;
         requestInfo.responseType = 0;
         requestInfo.blob.dataLen = 0;
         requestInfo.blob.data = 0;
         /* Here the thread blocks until a response from CallbackServer arrives */
         requestInfoP = xb->postSendEvent(xb->postSendEvent_userP, &requestInfo, exception);
         if (*exception->message != 0) {
            if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__,
               "Re-throw exception from preSendEvent errorCode=%s message=%s", exception->errorCode, exception->message);
            return false;
         }
         if (requestInfoP == 0) {
            printf("[XmlBlasterConnectionUnparsed] TODO: returning requestInfo 0 is not implemented");
         }
         memset(responseSocketDataHolder, 0, sizeof(SocketDataHolder));
         responseSocketDataHolder->type = requestInfoP->responseType;
         responseSocketDataHolder->version = XMLBLASTER_SOCKET_VERSION;
         strncpy0(responseSocketDataHolder->requestId, requestIdStr, MAX_REQUESTID_LEN);
         strncpy0(responseSocketDataHolder->methodName, methodName, MAX_METHODNAME_LEN);

         if (requestInfoP->responseType == MSG_TYPE_EXCEPTION) { /* convert XmlBlasterException thrown from remote */
            convertToXmlBlasterException(&requestInfoP->blob, exception, xb->logLevel >= LOG_DUMP);
            freeXmlBlasterBlobContent(&requestInfoP->blob);
            return false;
         }
         else {
            responseSocketDataHolder->blob.dataLen = requestInfoP->blob.dataLen;
            responseSocketDataHolder->blob.data = requestInfoP->blob.data;     /* The responseSocketDataHolder is now responsible to free(responseSocketDataHolder->blob.data) */
         }
         if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__,
            "requestId '%s' returns dataLen=%d", requestIdStr, requestInfoP->blob.dataLen);
      }
      else {
         /* Wait on the response ourself */
         if (getResponse(xb, responseSocketDataHolder, exception) == false) {  /* false on EOF */
            xb->log(xb->logLevel, LOG_WARN, __FILE__, "Lost connection to xmlBlaster server");
            xmlBlasterConnectionShutdown(xb);
            strncpy0(exception->errorCode, "user.notConnected", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
            SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] Lost connection to xmlBlaster server", __FILE__, __LINE__);
            return false;
         }
         if (responseSocketDataHolder->type == MSG_TYPE_EXCEPTION) { /* convert XmlBlasterException */
            convertToXmlBlasterException(&responseSocketDataHolder->blob, exception, xb->logLevel >= LOG_DUMP);
            freeXmlBlasterBlobContent(&responseSocketDataHolder->blob);
            if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__,
               "Re-throw exception from response errorCode=%s message=%s", exception->errorCode, exception->message);
            return false;
         }
      }

      if (xb->logLevel>=LOG_TRACE) {
         rawMsgStr = blobDump(&responseSocketDataHolder->blob);
         xb->log(xb->logLevel, LOG_TRACE, __FILE__, "Received response msgLen=%u type=%c version=%c requestId=%s methodName=%s dateLen=%u data='%.100s ...'",
                  responseSocketDataHolder->msgLen, responseSocketDataHolder->type, responseSocketDataHolder->version, responseSocketDataHolder->requestId,
                  responseSocketDataHolder->methodName, responseSocketDataHolder->blob.dataLen, rawMsgStr);
         free(rawMsgStr);
      }
   }

   return true;
}

/**
 * Parse the returned message from xmlBlaster. 
 * This method blocks until data arrives.
 * <br />
 * The responseSocketDataHolder holds all informations about the returned data from xmlBlaster,
 * on error the exception struct is filled.
 *
 * @param responseSocketDataHolder You need to free(responseSocketDataHolder->data) if return is 'true'.
 * @param exception Contains the exception thrown (on error only *exception->errorCode!=0)
 * @return true if OK or on exception, false on EOF
 */
static bool getResponse(XmlBlasterConnectionUnparsed *xb, SocketDataHolder *responseSocketDataHolder, XmlBlasterException *exception)
{
   return parseSocketData(xb->socketToXmlBlaster, responseSocketDataHolder, exception, xb->logLevel >= LOG_DUMP);
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
   SocketDataHolder responseSocketDataHolder;
   char *response;
   
   if (qos == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] Please provide valid arguments to xmlBlasterConnect()", __FILE__, __LINE__);
      if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__, exception->message);
      return (char *)0;
   }

   if (initConnection(xb, exception) == false) {
      return (char *)0;
   }

   if (sendData(xb, XMLBLASTER_CONNECT, MSG_TYPE_INVOKE, (const char *)qos,
                (qos == (const char *)0) ? 0 : strlen(qos),
                &responseSocketDataHolder, exception) == false) {
      return (char *)0;
   }

   response = strFromBlobAlloc(responseSocketDataHolder.blob.data, responseSocketDataHolder.blob.dataLen);
   freeXmlBlasterBlobContent(&responseSocketDataHolder.blob);

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
               SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] ERROR Received too long secret sessionId with len=%d, please change setting MAX_SECRETSESSIONID_LEN", __FILE__, __LINE__, len);
               if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__, exception->message);
            }
            strncpy0(xb->secretSessionId, pStart, len);
         }
      }
   }

   if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__,
      "Got response for connect(secretSessionId=%s)", xb->secretSessionId);

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
   if (checkArgs(xb, "disconnect", exception) == false ) return 0;

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
   SocketDataHolder responseSocketDataHolder;
   char *response = 0;

   char *data = encodeMsgUnit(msgUnit, &totalLen, xb->logLevel >= LOG_DUMP);

   if (checkArgs(xb, "publish", exception) == false ) return 0;

   msgUnit->responseQos = 0; /* Initialize properly */

   if (sendData(xb, XMLBLASTER_PUBLISH, MSG_TYPE_INVOKE, data, totalLen,
                &responseSocketDataHolder, exception) == false) {
      free(data);
      return 0;
   }
   free(data);

   response = strFromBlobAlloc(responseSocketDataHolder.blob.data, responseSocketDataHolder.blob.dataLen);
   freeXmlBlasterBlobContent(&responseSocketDataHolder.blob);

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
   SocketDataHolder responseSocketDataHolder;
   char *response;

   if (checkArgs(xb, "subscribe", exception) == false ) return 0;
   
   if (key == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] Please provide valid arguments to xmlBlasterSubscribe()", __FILE__, __LINE__);
      if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__, exception->message);
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
                &responseSocketDataHolder, exception) == false) {
      free(data);
      return (char *)0;
   }
   free(data);

   response = strFromBlobAlloc(responseSocketDataHolder.blob.data, responseSocketDataHolder.blob.dataLen);
   freeXmlBlasterBlobContent(&responseSocketDataHolder.blob);

   if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__,
      "Got response for subscribe(): %s", response);

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
   SocketDataHolder responseSocketDataHolder;
   char *response;

   if (checkArgs(xb, "unSubscribe", exception) == false ) return 0;

   if (key == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] Please provide valid arguments to xmlBlasterUnSubscribe()", __FILE__, __LINE__);
      if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__, exception->message);
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
                &responseSocketDataHolder, exception) == false) {
      free(data);
      return (char *)0;
   }
   free(data);

   response = strFromBlobAlloc(responseSocketDataHolder.blob.data, responseSocketDataHolder.blob.dataLen);
   freeXmlBlasterBlobContent(&responseSocketDataHolder.blob);

   if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__,
      "Got response for unSubscribe(): %s", response);

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
   SocketDataHolder responseSocketDataHolder;
   char *response;

   if (checkArgs(xb, "erase", exception) == false ) return 0;

   if (key == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] Please provide valid arguments to xmlBlasterErase()", __FILE__, __LINE__);
      if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__, exception->message);
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
                &responseSocketDataHolder, exception) == false) {
      free(data);
      return (char *)0;
   }
   free(data);

   response = strFromBlobAlloc(responseSocketDataHolder.blob.data, responseSocketDataHolder.blob.dataLen);
   freeXmlBlasterBlobContent(&responseSocketDataHolder.blob);

   if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__,
      "Got response for erase(): %s", response);

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
   SocketDataHolder responseSocketDataHolder;
   char *response;
   XmlBlasterException exception;

   if (!xb->isInitialized || !xb->isConnected(xb)) return (char *)0;
   
   if (sendData(xb, XMLBLASTER_PING, MSG_TYPE_INVOKE, (const char *)qos,
                (qos == (const char *)0) ? 0 : strlen(qos),
                &responseSocketDataHolder, &exception) == false) {
      return (char *)0;
   }

   response = strFromBlobAlloc(responseSocketDataHolder.blob.data, responseSocketDataHolder.blob.dataLen);
   freeXmlBlasterBlobContent(&responseSocketDataHolder.blob);
   if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__,
      "Got response for ping '%s'", response);
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
   SocketDataHolder responseSocketDataHolder;
   MsgUnitArr *msgUnitArr = 0;

   if (key == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] Please provide valid arguments to xmlBlasterGet()", __FILE__, __LINE__);
      if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__, exception->message);
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
                &responseSocketDataHolder, exception) == false) {
      free(data);
      return (MsgUnitArr *)0; /* exception is filled with details */
   }
   free(data);

   /* Now process the returned messages */

   msgUnitArr = parseMsgUnitArr(responseSocketDataHolder.blob.dataLen, responseSocketDataHolder.blob.data);
   freeXmlBlasterBlobContent(&responseSocketDataHolder.blob);

   if (xb->logLevel>=LOG_TRACE) xb->log(xb->logLevel, LOG_TRACE, __FILE__,
      "Returned %u messages for get()", msgUnitArr->len);

   return msgUnitArr;
}

static bool checkArgs(XmlBlasterConnectionUnparsed *xb, const char *methodName, XmlBlasterException *exception)
{
   if (xb == 0) {
      char *stack = getStackTrace(10);
      printf("[%s:%d] Please provide a valid XmlBlasterAccessUnparsed pointer to %s() %s",
               __FILE__, __LINE__, methodName, stack);
      free(stack);
      return false;
   }

   if (exception == 0) {
      char *stack = getStackTrace(10);
      xb->log(xb->logLevel, LOG_ERROR, __FILE__, "[%s:%d] Please provide valid exception pointer to %s() %s",
              __FILE__, __LINE__, methodName, stack);
      free(stack);
      return false;
   }

   if (!xb->isConnected(xb)) {
      char *stack = getStackTrace(10);
      strncpy0(exception->errorCode, "user.notConnected", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
               "[%.100s:%d] Not connected to xmlBlaster, %s() failed %s",
                __FILE__, __LINE__, methodName, stack);
      free(stack);
      xb->log(xb->logLevel, LOG_WARN, __FILE__, exception->message);
      return false;
   }

   return true;
}


