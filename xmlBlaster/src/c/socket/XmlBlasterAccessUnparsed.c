/*----------------------------------------------------------------------------
Name:      XmlBlasterAccessUnparsed.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   wraps raw socket connection to xmlBlaster
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   gcc -Wall -g -c XmlBlasterAccessUnparsed.c callbackServer.c
Compile-Win: cl /MT /W3 /Wp64 -D_WINDOWS -c XmlBlasterAccessUnparsed.c callbackServer.c
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <errno.h>
#include <sys/types.h>
#ifdef _WINDOWS
#else
#  include <pthread.h>
#endif
#include <socket/xmlBlasterSocket.h>
#include <XmlBlasterAccessUnparsed.h>

#ifdef _WINDOWS
#  define ssize_t signed int
#endif

static bool initConnection(XmlBlasterAccessUnparsed *xb, int argc, char** argv);
static bool getResponse(XmlBlasterAccessUnparsed *xb, SocketDataHolder *responseHolder, XmlBlasterException *exception);
static char *xmlBlasterConnect(XmlBlasterAccessUnparsed *xb, const char * const qos, XmlBlasterException *exception);
static bool xmlBlasterDisconnect(XmlBlasterAccessUnparsed *xb, const char * const qos, XmlBlasterException *exception);
static char *xmlBlasterPublish(XmlBlasterAccessUnparsed *xb, MsgUnit *msgUnit, XmlBlasterException *exception);
static char *xmlBlasterSubscribe(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
static char *xmlBlasterUnSubscribe(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
static char *xmlBlasterErase(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
static MsgUnitArr *xmlBlasterGet(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
static char *xmlBlasterPing(XmlBlasterAccessUnparsed *xb, const char * const qos);
static bool isConnected(XmlBlasterAccessUnparsed *xb);

/**
 * Bootstrap the connection. 
 * This is usually the first call of a client.
 * @return NULL if bootstrapping failed. If not NULL you need to free() it when you are done
 * usually by calling freeXmlBlasterAccessUnparsed().
 */
XmlBlasterAccessUnparsed *getXmlBlasterAccessUnparsed(int argc, char** argv) {
   XmlBlasterAccessUnparsed *xmlBlasterAccess = (XmlBlasterAccessUnparsed *)calloc(1, sizeof(XmlBlasterAccessUnparsed));
   xmlBlasterAccess->socketToXmlBlaster = -1;
   xmlBlasterAccess->isInitialized = false;
   xmlBlasterAccess->requestId = 0;
   *xmlBlasterAccess->secretSessionId = 0;
   xmlBlasterAccess->connect = xmlBlasterConnect;
   xmlBlasterAccess->disconnect = xmlBlasterDisconnect;
   xmlBlasterAccess->publish = xmlBlasterPublish;
   xmlBlasterAccess->subscribe = xmlBlasterSubscribe;
   xmlBlasterAccess->unSubscribe = xmlBlasterUnSubscribe;
   xmlBlasterAccess->erase = xmlBlasterErase;
   xmlBlasterAccess->get = xmlBlasterGet;
   xmlBlasterAccess->ping = xmlBlasterPing;
   xmlBlasterAccess->isConnected = isConnected;
   xmlBlasterAccess->debug = false;
   if (initConnection(xmlBlasterAccess, argc, argv) == false)
      return (XmlBlasterAccessUnparsed *)0;
   return xmlBlasterAccess;
}

void freeXmlBlasterAccessUnparsed(XmlBlasterAccessUnparsed *xmlBlasterAccess)
{
   free(xmlBlasterAccess);
}

static bool initConnection(XmlBlasterAccessUnparsed *xb, int argc, char** argv)
{
   int iarg;
   char *servTcpPort = "7607";

   struct sockaddr_in xmlBlasterAddr;
   struct hostent *hostP = 0;
   struct servent *portP = 0;

   char serverHostName[256];

#  ifdef _WINDOWS
   WORD wVersionRequested;
   WSADATA wsaData;
   int err;
   wVersionRequested = MAKEWORD( 2, 2 );
   err = WSAStartup( wVersionRequested, &wsaData );
   if ( err != 0 ) {
      return false; // Tell the user that we could not find a usable WinSock DLL
   }

   // Confirm that the WinSock DLL supports 2.2.
   if ( LOBYTE( wsaData.wVersion ) != 2 ||
   HIBYTE( wsaData.wVersion ) != 2 ) {
      WSACleanup( );
      return false; 
   }
# endif

   strcpy(serverHostName, "localhost");
   gethostname(serverHostName, 125);

   if (xb->isInitialized) {
      return true;
   }

   for (iarg=0; iarg < argc-1; iarg++) {
      if (strcmp(argv[iarg], "-dispatch/connection/plugin/socket/hostname") == 0)
         strcpy(serverHostName, argv[++iarg]);
      else if (strcmp(argv[iarg], "-dispatch/connection/plugin/socket/port") == 0)
         servTcpPort = argv[++iarg];
   }

   if (xb->debug) printf("[XmlBlasterAccessUnparsed] Lookup xmlBlaster on -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %s ...\n", serverHostName, servTcpPort);

   *xb->secretSessionId = 0;
   memset((char *)&xmlBlasterAddr, 0, sizeof(xmlBlasterAddr));
   xmlBlasterAddr.sin_family=AF_INET;

# if _WINDOWS_NOT_YET_PORTED // Windows gethostbyname is deprecated
   const struct addrinfo hints;
   struct addrinfo** res;
   int getaddrinfo(serverHostName, servTcpPort, &hints, res);
   res->ai_next : ai_family, ai_socktype, and ai_protocol

   ...

   void freeaddrinfo(*res);
# endif
   hostP = gethostbyname(serverHostName);
   //printf("gethostbyname error=%d\n", WSAGetLastError());
   portP = getservbyname(servTcpPort, "tcp");
   if (hostP != 0) {
      xmlBlasterAddr.sin_addr.s_addr = ((struct in_addr *)(hostP->h_addr))->s_addr; //inet_addr("192.168.1.2");
      if (portP != 0)
         xmlBlasterAddr.sin_port = portP->s_port;
      else
         xmlBlasterAddr.sin_port = htons(atoi(servTcpPort));
      xb->socketToXmlBlaster = (int)socket(AF_INET, SOCK_STREAM, 0);
      if (xb->socketToXmlBlaster != -1) {
         int ret=0;
         if ((ret=connect(xb->socketToXmlBlaster, (struct sockaddr *)&xmlBlasterAddr, sizeof(xmlBlasterAddr))) != -1) {
            if (xb->debug) printf("[XmlBlasterAccessUnparsed] Connected to xmlBlaster\n");
         }
         else {
            if (xb->debug) printf("[XmlBlasterAccessUnparsed] ERROR Connecting to xmlBlaster (connect failed) -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %s failed errno=%d\n", serverHostName, servTcpPort, errno);
            return false;
         }
      }
      else {
         if (xb->debug) printf("[XmlBlasterAccessUnparsed] ERROR Connecting to xmlBlaster (socket=-1) -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %s failed errno=%d\n", serverHostName, servTcpPort, errno);
         return false;
      }
   }
   else {
      if (xb->debug) printf("[XmlBlasterAccessUnparsed] ERROR Connecting to xmlBlaster (hostP=0) -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %s failed errno=%d\n", serverHostName, servTcpPort, errno);
      return false;
   }
   xb->isInitialized = true;
   return true;
}

static bool isConnected(XmlBlasterAccessUnparsed *xb)
{
   return (xb->socketToXmlBlaster > -1) ? true : false;
}

const char *xmlBlasterAccessUnparsedUsage()
{
   return 
      "\n  -dispatch/connection/plugin/socket/hostname [localhost]"
      "\n                       Where to find xmlBlaster"
      "\n  -dispatch/connection/plugin/socket/port [7607]"
      "\n                       The port where xmlBlaster listens";
}

void shutdownConnection(XmlBlasterAccessUnparsed *xb)
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
static bool sendData(XmlBlasterAccessUnparsed *xb, 
              const char * const methodName,
              enum XMLBLASTER_MSG_TYPE_ENUM msgType,
              const char *data,
              size_t dataLen,
              SocketDataHolder *responseHolder,
              XmlBlasterException *exception)
{
   ssize_t numSent;
   size_t rawMsgLen = 0;
   char *rawMsg = (char *)0;
   char *rawMsgStr;
   size_t lenUnzipped = dataLen;
   char requestId[MAX_REQUESTID_LEN];

   if (data == 0) {
      data = "";
      dataLen = 0;
      lenUnzipped = 0;
   }

   if (exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterAccessUnparsed] Please provide valid exception to sendData()");
      if (xb->debug) { printf(exception->message); printf("\n"); }
      return false;
   }
   *exception->errorCode = 0;

   if (!xb->isConnected(xb)) {
      strncpy0(exception->errorCode, "user.notConnected", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterAccessUnparsed] No connection to xmlBlaster\n");
      if (xb->debug) { printf(exception->message); printf("\n"); }
      return false;
   }

   if (strcmp(XMLBLASTER_CONNECT, methodName) && strlen(xb->secretSessionId) < 1) {
      strncpy0(exception->errorCode, "user.notConnected", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterAccessUnparsed] Please call connect() before invoking '%s'\n", methodName);
      if (xb->debug) { printf(exception->message); printf("\n"); }
      return false;
   }

   xb->requestId++;
   if (xb->requestId > 1000000000) xb->requestId = 0;
   sprintf(requestId, "%ld", xb->requestId);

   rawMsg = encodeSocketMessage(msgType, requestId, methodName, xb->secretSessionId,
                             data, dataLen, xb->debug, &rawMsgLen);

   // send the header ...
   numSent = send(xb->socketToXmlBlaster, rawMsg, (int)rawMsgLen, 0);

   if (numSent == -1) {
      if (xb->debug) printf("[XmlBlasterAccessUnparsed] Lost connection to xmlBlaster server\n");
      shutdownConnection(xb);
      strncpy0(exception->errorCode, "user.notConnected", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterAccessUnparsed] Lost connection to xmlBlaster server\n");
      return false;
   }

   if (numSent != (int)rawMsgLen) {
      if (xb->debug) printf("[XmlBlasterAccessUnparsed] ERROR Sent only %d bytes from %u\n", numSent, rawMsgLen);
      strncpy0(exception->errorCode, "user.connect", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterAccessUnparsed] ERROR Sent only %d bytes from %u\n", numSent, rawMsgLen);
      free(rawMsg);
      return false;
   }

   free(rawMsg);
   rawMsg = 0;

   if (msgType==MSG_TYPE_RESPONSE || msgType==MSG_TYPE_EXCEPTION)
      return true;

   if (responseHolder) { // if not oneway read the response message
      if (getResponse(xb, responseHolder, exception) == false) {
         if (xb->debug) printf("[XmlBlasterAccessUnparsed] Lost connection to xmlBlaster server\n");
         shutdownConnection(xb);
         strncpy0(exception->errorCode, "user.notConnected", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
         sprintf(exception->message, "[XmlBlasterAccessUnparsed] Lost connection to xmlBlaster server\n");
         return false;
      }

      if (xb->debug) {
         rawMsgStr = toReadableDump(responseHolder->data, responseHolder->dataLen);
         printf("Received response msgLen=%u type=%c version=%c requestId=%s methodName=%s dateLen=%u data='%.100s ...'\n",
                  responseHolder->msgLen, responseHolder->type, responseHolder->version, responseHolder->requestId,
                  responseHolder->methodName, responseHolder->dataLen, rawMsgStr);
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
static bool getResponse(XmlBlasterAccessUnparsed *xb, SocketDataHolder *responseHolder, XmlBlasterException *exception)
{
   return parseSocketData(xb->socketToXmlBlaster, responseHolder, exception, xb->debug);
}

/**
 * Connect to the server. 
 * @param qos The QoS to connect
 * @param The exception struct, exception->errorCode is filled on exception
 * @return The ConnectReturnQos raw xml string, you need to free() it
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterConnect(XmlBlasterAccessUnparsed *xb, const char * const qos, XmlBlasterException *exception)
{
   SocketDataHolder responseHolder;
   char *response;
   
   if (qos == 0 || exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterAccessUnparsed] Please provide valid arguments to xmlBlasterConnect()");
      if (xb->debug) { printf(exception->message); printf("\n"); }
      return (char *)0;
   }

   if (sendData(xb, XMLBLASTER_CONNECT, MSG_TYPE_INVOKE, (const char *)qos,
                (qos == (const char *)0) ? 0 : strlen(qos),
                &responseHolder, exception) == false) {
      return (char *)0;
   }

   response = strFromBlobAlloc(responseHolder.data, responseHolder.dataLen);
   free(responseHolder.data);

   // Extract secret session ID from ConnectReturnQos
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
               sprintf(exception->message, "[XmlBlasterAccessUnparsed] ERROR Received too long secret sessionId with len=%d, please change setting MAX_SECRETSESSIONID_LEN", len);
               if (xb->debug) { printf(exception->message); printf("\n"); }
            }
            strncpy0(xb->secretSessionId, pStart, len);
         }
      }
   }

   if (xb->debug) printf("[XmlBlasterAccessUnparsed] Got response for connect(secretSessionId=%s)", xb->secretSessionId);

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
static bool xmlBlasterDisconnect(XmlBlasterAccessUnparsed *xb, const char * const qos, XmlBlasterException *exception)
{
   if (exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterAccessUnparsed] Please provide valid arguments to xmlBlasterDisconnect()");
      if (xb->debug) { printf(exception->message); printf("\n"); }
      return false;
   }

   if (sendData(xb, XMLBLASTER_DISCONNECT, MSG_TYPE_INVOKE, (const char *)qos, 
                (qos == (const char *)0) ? 0 : strlen(qos),
                0, exception) == false) {
      return false;
   }

   shutdownConnection(xb);
   *xb->secretSessionId = 0;
   return true;
}

/**
 * Publish a message to the server. 
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterPublish(XmlBlasterAccessUnparsed *xb, MsgUnit *msgUnit, XmlBlasterException *exception)
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

   response = strFromBlobAlloc(responseHolder.data, responseHolder.dataLen);
   free(responseHolder.data);
   responseHolder.data = 0;
   return response;
}

/**
 * Subscribe a message. 
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.subscribe.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterSubscribe(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception)
{
   size_t qosLen, keyLen, totalLen;
   char *data;
   size_t currpos = 0;
   SocketDataHolder responseHolder;
   char *response;

   if (key == 0 || exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterAccessUnparsed] Please provide valid arguments to xmlBlasterSubscribe()");
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

   memcpy(data+currpos, qos, qosLen+1); // inclusive '\0'
   currpos += qosLen+1;

   memcpy(data+currpos, key, keyLen+1); // inclusive '\0'
   currpos += keyLen+1;

   if (sendData(xb, XMLBLASTER_SUBSCRIBE, MSG_TYPE_INVOKE, data, totalLen,
                &responseHolder, exception) == false) {
      free(data);
      return (char *)0;
   }
   free(data);

   if (responseHolder.dataLen > 0) {
      response = strFromBlobAlloc(responseHolder.data, responseHolder.dataLen);
   }
   free(responseHolder.data);

   if (xb->debug) printf("[XmlBlasterAccessUnparsed] Got response for subscribe(): %s\n", response);

   return response;
}

/**
 * UnSubscribe a message from the server. 
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterUnSubscribe(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception)
{
   size_t qosLen, keyLen, totalLen;
   char *data;
   size_t currpos = 0;
   SocketDataHolder responseHolder;
   char *response;

   if (key == 0 || exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterAccessUnparsed] Please provide valid arguments to xmlBlasterUnSubscribe()");
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

   memcpy(data+currpos, qos, qosLen+1); // inclusive '\0'
   currpos += qosLen+1;

   memcpy(data+currpos, key, keyLen+1); // inclusive '\0'
   currpos += keyLen+1;

   if (sendData(xb, XMLBLASTER_UNSUBSCRIBE, MSG_TYPE_INVOKE, data, totalLen,
                &responseHolder, exception) == false) {
      free(data);
      return (char *)0;
   }
   free(data);

   response = strFromBlobAlloc(responseHolder.data, responseHolder.dataLen);
   free(responseHolder.data);

   if (xb->debug) printf("[XmlBlasterAccessUnparsed] Got response for unSubscribe(): %s", response);

   return response;
}

/**
 * Erase a message from the server. 
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.erase.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterErase(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception)
{
   size_t qosLen, keyLen, totalLen;
   char *data;
   size_t currpos = 0;
   SocketDataHolder responseHolder;
   char *response;

   if (key == 0 || exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterAccessUnparsed] Please provide valid arguments to xmlBlasterErase()");
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

   memcpy(data+currpos, qos, qosLen+1); // inclusive '\0'
   currpos += qosLen+1;

   memcpy(data+currpos, key, keyLen+1); // inclusive '\0'
   currpos += keyLen+1;

   if (sendData(xb, XMLBLASTER_ERASE, MSG_TYPE_INVOKE, data, totalLen,
                &responseHolder, exception) == false) {
      free(data);
      return (char *)0;
   }
   free(data);

   response = strFromBlobAlloc(responseHolder.data, responseHolder.dataLen);
   free(responseHolder.data);

   if (xb->debug) printf("[XmlBlasterAccessUnparsed] Got response for erase(): %s", response);

   return response;
}

/**
 * Ping the server. 
 * @param qos The QoS or 0
 * @return The ping return QoS raw xml string, you need to free() it or 0 on failure
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterPing(XmlBlasterAccessUnparsed *xb, const char * const qos)
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

   response = strFromBlobAlloc(responseHolder.data, responseHolder.dataLen);
   free(responseHolder.data);
   if (xb->debug) printf("[XmlBlasterAccessUnparsed] Got response for ping '%s'\n", response);
   return response;
}

/**
 * Get a message. 
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.get.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @return NULL on error, please check exception in such a case
 */
static MsgUnitArr *xmlBlasterGet(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception)
{
   size_t qosLen, keyLen, totalLen;
   char *data;
   size_t currpos = 0;
   SocketDataHolder responseHolder;
   MsgUnitArr *msgUnitArr = 0;

   if (key == 0 || exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[XmlBlasterAccessUnparsed] Please provide valid arguments to xmlBlasterGet()");
      if (xb->debug) { printf(exception->message); printf("\n"); }
      return (MsgUnitArr *)0;
   }

   if (qos == (const char *)0) qos = "";
   qosLen = strlen(qos);
   keyLen = strlen(key);

   totalLen = qosLen + 1 + keyLen + 1;

   data = (char *)malloc(totalLen);

   memcpy(data+currpos, qos, qosLen+1); // inclusive '\0'
   currpos += qosLen+1;

   memcpy(data+currpos, key, keyLen+1); // inclusive '\0'
   currpos += keyLen+1;

   if (sendData(xb, XMLBLASTER_GET, MSG_TYPE_INVOKE, data, totalLen,
                &responseHolder, exception) == false) {
      free(data);
      return (MsgUnitArr *)0; // exception is filled with details
   }
   free(data);

   // Now process the returned messages

   msgUnitArr = parseMsgUnitArr(responseHolder.dataLen, responseHolder.data);

   free(responseHolder.data);

   if (xb->debug) printf("[XmlBlasterAccessUnparsed] Returned %u messages for get()\n", msgUnitArr->len);

   return msgUnitArr;
}
