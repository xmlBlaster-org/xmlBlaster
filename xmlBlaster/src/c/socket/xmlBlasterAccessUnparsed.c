/*----------------------------------------------------------------------------
Name:      xmlBlasterAccessUnparsed.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   wraps raw socket connection to xmlBlaster
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   gcc -Wall -g -c xmlBlasterAccessUnparsed.c callbackServer.c
Compile-Win: cl /MT /W3 /Wp64 -D_WINDOWS -c xmlBlasterAccessUnparsed.c callbackServer.c
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
#include "xmlBlasterSocket.h"
#include "xmlBlasterAccessUnparsed.h"


static bool initConnection(XmlBlasterAccessUnparsed *xb, int argc, char** argv);
static bool getResponse(XmlBlasterAccessUnparsed *xb, ResponseHolder *responseHolder, XmlBlasterException *exception);
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

# if WIN32
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
      if (strcmp(argv[iarg], "-socket.host") == 0)
         strcpy(serverHostName, argv[++iarg]);
      else if (strcmp(argv[iarg], "-dispatch/clientSide/protocol/socket/port") == 0)
         servTcpPort = argv[++iarg];
   }

   if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: Lookup xmlBlaster on -socket.host %s -dispatch/clientSide/protocol/socket/port %s ...\n", serverHostName, servTcpPort);

   memset(xb->secretSessionId, 0, sizeof(xb->secretSessionId));
   memset((char *)&xmlBlasterAddr, 0, sizeof(xmlBlasterAddr));
   xmlBlasterAddr.sin_family=AF_INET;

# if WIN32_NOT_YET_PORTED // Windows gethostbyname is deprecated
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
      xb->socketToXmlBlaster = socket(AF_INET, SOCK_STREAM, 0);
      if (xb->socketToXmlBlaster != -1) {
         int ret=0;
         if ((ret=connect(xb->socketToXmlBlaster, (struct sockaddr *)&xmlBlasterAddr, sizeof(xmlBlasterAddr))) != -1) {
            if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: Connected to xmlBlaster\n");
         }
         else {
            if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: ERROR Connecting to xmlBlaster (connect failed) -socket.host %s -dispatch/clientSide/protocol/socket/port %s failed errno=%d\n", serverHostName, servTcpPort, errno);
            return false;
         }
      }
      else {
         if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: ERROR Connecting to xmlBlaster (socket=-1) -socket.host %s -dispatch/clientSide/protocol/socket/port %s failed errno=%d\n", serverHostName, servTcpPort, errno);
         return false;
      }
   }
   else {
      if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: ERROR Connecting to xmlBlaster (hostP=0) -socket.host %s -dispatch/clientSide/protocol/socket/port %s failed errno=%d\n", serverHostName, servTcpPort, errno);
      return false;
   }
   xb->isInitialized = true;
   return true;
}

bool isConnected(XmlBlasterAccessUnparsed *xb)
{
   return (xb->socketToXmlBlaster > -1) ? true : false;
}

void shutdownConnection(XmlBlasterAccessUnparsed *xb)
{
   if (isConnected(xb)) {
      shutdown(xb->socketToXmlBlaster, 2);
      xb->socketToXmlBlaster = -1;
   }
}

/**
 * Send a message over the socket to xmlBlaster. 
 * @param methodName The name of the remote method to invoke e.g. "connect"
 * @param data The message payload to send
 * @param response The returned data, you need to free it with free(response->data) if we returned true.
 *        Supply NULL for oneway messages.
 * @param exception The exception struct, exception->errorCode is filled on exception.
 *        You need to supply it.
 * @return true if OK and response is filled<br />
           false on error and exception is filled
 */
bool sendData(XmlBlasterAccessUnparsed *xb, 
              const char * const methodName,
              const char *data,
              size_t dataLen,
              ResponseHolder *responseHolder,
              XmlBlasterException *exception)
{
   size_t numSent;
   size_t rawMsgLen = 0;
   char *rawMsg = (char *)0;
   char *rawMsgStr;
   size_t currpos = 0;
   char tmp[256];
   size_t lenUnzipped = dataLen;
   char lenFormatStr[56]; // = "%10.d";
   char lenStr[MSG_LEN_FIELD_LEN+1];

   if (data == 0) {
      data = "";
      dataLen = 0;
      lenUnzipped = 0;
   }

   if (exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "xmlBlasterClient: Please provide valid exception to sendData()");
      if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
      return false;
   }
   *exception->errorCode = 0;

   if (strcmp(XMLBLASTER_CONNECT, methodName) && strlen(xb->secretSessionId) < 1) {
      strncpy0(exception->errorCode, "user.notConnected", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "xmlBlasterClient: Please call connect() before invoking '%s'\n", methodName);
      if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
      return false;
   }

   xb->requestId++;

   rawMsg = (char *)calloc(500 + dataLen, sizeof(char));

   *(rawMsg+MSG_FLAG_POS_TYPE) = MSG_TYPE_INVOKE;
   *(rawMsg+MSG_FLAG_POS_VERSION) = XMLBLASTER_VERSION;

   currpos = MSG_POS_REQESTID;
   //sprintf(tmp, "%ld%s%ld", xb->requestId, methodName, lenUnzipped);
   sprintf(tmp, "%ld", xb->requestId);
   memcpy(rawMsg+currpos, tmp, strlen(tmp)+1); // inclusive '\0'
   currpos += strlen(tmp)+1;

   memcpy(rawMsg+currpos, methodName, strlen(methodName)+1); // inclusive '\0'
   currpos += strlen(methodName)+1;

   memcpy(rawMsg+currpos, xb->secretSessionId, strlen(xb->secretSessionId)+1); // inclusive '\0'
   currpos += strlen(xb->secretSessionId)+1;
   
   sprintf(tmp, "%u", lenUnzipped);
   memcpy(rawMsg+currpos, tmp, strlen(tmp)+1); // inclusive '\0'
   currpos += strlen(tmp)+1;

   memcpy(rawMsg+currpos, data, dataLen);
   rawMsgLen = currpos+dataLen;

   sprintf(lenFormatStr, "%%%d.d", MSG_LEN_FIELD_LEN);
   sprintf(lenStr, lenFormatStr, rawMsgLen);
   memcpy(rawMsg, lenStr, MSG_LEN_FIELD_LEN);
   
   if (XMLBLASTER_DEBUG) {
      rawMsgStr = toReadableDump(rawMsg, rawMsgLen);
      printf("xmlBlasterClient: Sending now %u bytes '%s' -> '%s'\n", rawMsgLen, lenStr, rawMsgStr);
      free(rawMsgStr);
   }

   // send the header ...
   numSent = send(xb->socketToXmlBlaster, rawMsg, (int)rawMsgLen, 0);
   if (numSent !=  rawMsgLen) {
      if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: ERROR Sent only %d bytes from %u\n", numSent, rawMsgLen);
      strncpy0(exception->errorCode, "user.connect", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "xmlBlasterClient: ERROR Sent only %d bytes from %u\n", numSent, rawMsgLen);
      free(rawMsg);
      return false;
   }

   free(rawMsg);
   rawMsg = 0;

   if (responseHolder) { // if not oneway
      if (getResponse(xb, responseHolder, exception) == false) {
         return false;
      }

      if (XMLBLASTER_DEBUG) {
         rawMsgStr = toReadableDump(responseHolder->data, responseHolder->dataLen);
         printf("Received response msgLen=%u type=%c version=%c requestId=%ld methodName=%s dateLen=%u data='%.100s ...'\n",
                  responseHolder->msgLen, responseHolder->type, responseHolder->version, responseHolder->requestId,
                  responseHolder->methodName, responseHolder->dataLen, rawMsgStr);
         free(rawMsgStr);
      }
   }

   return true;
}

/**
 * Parse the returned message from xmlBlaster. 
 * The responseHolder holds all informations about the returned data from xmlBlaster,
 * on error the exception struct is filled.
 *
 * @param responseHolder You need to free(responseHolder->data) if return is 'true'.
 * @param exception Contains the exception thrown (on error only)
 * @return true if OK and false if exception is filled
 */
static bool getResponse(XmlBlasterAccessUnparsed *xb, ResponseHolder *responseHolder, XmlBlasterException *exception) 
{
   char msgLenPtr[MSG_LEN_FIELD_LEN+1];
   char ptr[MSG_LEN_FIELD_LEN+1];
   char *rawMsg;
   char tmpPtr[256];
   size_t numRead;
   size_t currPos = 0;

   // initialize
   memset(msgLenPtr, 0, MSG_LEN_FIELD_LEN+1);
   memset(ptr, 0, MSG_LEN_FIELD_LEN+1);
   memset(tmpPtr, 0, MSG_LEN_FIELD_LEN+1);
   memset(responseHolder, 0, sizeof(ResponseHolder));

   // read the first 10 bytes to determine the length
   numRead = recv(xb->socketToXmlBlaster, msgLenPtr, MSG_LEN_FIELD_LEN, 0);
   if (numRead != MSG_LEN_FIELD_LEN) {
      strncpy0(exception->errorCode, "user.connect", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "xmlBlasterClient: ERROR Received numRead=%d header bytes but expected %d", numRead, MSG_LEN_FIELD_LEN);
      if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
      return false;
   }
   strcpy(ptr, msgLenPtr);
   trim(ptr);
   sscanf(ptr, "%u", &responseHolder->msgLen);

   // read the complete message
   rawMsg = (char *)calloc(responseHolder->msgLen, sizeof(char));
   memcpy(rawMsg, msgLenPtr, MSG_LEN_FIELD_LEN);
   numRead = recv(xb->socketToXmlBlaster, rawMsg+MSG_LEN_FIELD_LEN, (int)responseHolder->msgLen-MSG_LEN_FIELD_LEN, 0);
   if (numRead != (responseHolder->msgLen-MSG_LEN_FIELD_LEN)) {
      strncpy0(exception->errorCode, "user.response", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "xmlBlasterClient: ERROR Received numRead=%d message bytes but expected %u", numRead, (responseHolder->msgLen-MSG_LEN_FIELD_LEN));
      if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
      free(rawMsg);
      return false;
   }

   responseHolder->type = *(rawMsg+MSG_FLAG_POS_TYPE);
   if (responseHolder->type != MSG_TYPE_RESPONSE && responseHolder->type != MSG_TYPE_EXCEPTION) {
      strncpy0(exception->errorCode, "user.response", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "xmlBlasterClient: ERROR Received response message of type=%c", responseHolder->type);
      if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
      free(rawMsg);
      return false;
   }

   responseHolder->version = *(rawMsg+MSG_FLAG_POS_VERSION);
   if (responseHolder->version != XMLBLASTER_VERSION) {
      strncpy0(exception->errorCode, "user.response", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "xmlBlasterClient: ERROR Received response message of unsupported version=%c", responseHolder->version);
      if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
      free(rawMsg);
      return false;
   }


   currPos = MSG_POS_REQESTID;

   strncpy0(tmpPtr, rawMsg+currPos, 256);
   currPos += strlen(tmpPtr)+1;
   trim(tmpPtr);
   sscanf(tmpPtr, "%ld", &responseHolder->requestId);

   strncpy0(responseHolder->methodName, rawMsg+currPos, MAX_METHODNAME_LEN);
   currPos += strlen(responseHolder->methodName)+1;

   strncpy0(responseHolder->secretSessionId, rawMsg+currPos, MAX_SESSIONID_LEN);
   currPos += strlen(responseHolder->secretSessionId)+1;

   strncpy0(tmpPtr, rawMsg+currPos, 256);
   currPos += strlen(tmpPtr)+1;
   trim(tmpPtr);
   sscanf(tmpPtr, "%u", &responseHolder->dataLenUncompressed);

   // Read the payload
   responseHolder->dataLen = responseHolder->msgLen - currPos;
   responseHolder->data = (char *)malloc(responseHolder->dataLen * sizeof(char));
   memcpy(responseHolder->data, rawMsg+currPos, responseHolder->dataLen);

   free(rawMsg);
   rawMsg = 0;

   if (responseHolder->type == MSG_TYPE_EXCEPTION) { // convert XmlBlasterException
      size_t currpos = 0;
      strncpy0(exception->errorCode, responseHolder->data+currpos, XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      currpos += strlen(exception->errorCode) + 1;
      sprintf(exception->message, XMLBLASTEREXCEPTION_MESSAGE_FMT, responseHolder->data+currpos);
      trim(exception->message);
      
      free(responseHolder->data);
      responseHolder->data = 0;
      return false;
   }

   return true;
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
   ResponseHolder responseHolder;
   char *response;
   
   if (qos == 0 || exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "xmlBlasterClient: Please provide valid arguments to xmlBlasterConnect()");
      if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
      return (char *)0;
   }

   if (sendData(xb, XMLBLASTER_CONNECT, (const char *)qos,
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
               sprintf(exception->message, "xmlBlasterClient: ERROR Received too long secret sessionId with len=%d, please change setting MAX_SECRETSESSIONID_LEN", len);
               if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
            }
            strncpy0(xb->secretSessionId, pStart, len);
         }
      }
   }

   if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: Got response for connect(secretSessionId=%s)", xb->secretSessionId);

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
      sprintf(exception->message, "xmlBlasterClient: Please provide valid arguments to xmlBlasterDisconnect()");
      if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
      return false;
   }

   if (sendData(xb, XMLBLASTER_DISCONNECT, (const char *)qos, 
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
   size_t qosLen, keyLen, totalLen;
   char *data;
   char contentLenStr[126];
   size_t currpos = 0;
   ResponseHolder responseHolder;
   char *response;

   if (msgUnit == 0 || msgUnit->key == 0 || msgUnit->content == 0) {
      if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: ERROR Invalid argument=NULL in publish(), message not sent\n");
      return (char *)0;
   }
   if (msgUnit->qos == 0) msgUnit->qos = "";

   qosLen = strlen(msgUnit->qos);
   keyLen = strlen(msgUnit->key);
   sprintf(contentLenStr, "%d", msgUnit->contentLen);

   totalLen = qosLen + 1 + keyLen + 1 + strlen(contentLenStr) + 1 + msgUnit->contentLen;

   data = (char *)malloc(totalLen);

   memcpy(data+currpos, msgUnit->qos, qosLen+1); // inclusive '\0'
   currpos += qosLen+1;

   memcpy(data+currpos, msgUnit->key, keyLen+1); // inclusive '\0'
   currpos += keyLen+1;

   memcpy(data+currpos, contentLenStr, strlen(contentLenStr)+1); // inclusive '\0'
   currpos += strlen(contentLenStr)+1;

   memcpy(data+currpos, msgUnit->content, msgUnit->contentLen);
   //currpos += msgUnit->contentLen;

   if (sendData(xb, XMLBLASTER_PUBLISH, data, totalLen,
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
   ResponseHolder responseHolder;
   char *response;

   if (key == 0 || exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "xmlBlasterClient: Please provide valid arguments to xmlBlasterSubscribe()");
      if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
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

   if (sendData(xb, XMLBLASTER_SUBSCRIBE, data, totalLen,
                &responseHolder, exception) == false) {
      free(data);
      return (char *)0;
   }
   free(data);

   response = strFromBlobAlloc(responseHolder.data, responseHolder.dataLen);
   free(responseHolder.data);

   if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: Got response for subscribe(): %s", response);

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
   ResponseHolder responseHolder;
   char *response;

   if (key == 0 || exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "xmlBlasterClient: Please provide valid arguments to xmlBlasterUnSubscribe()");
      if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
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

   if (sendData(xb, XMLBLASTER_UNSUBSCRIBE, data, totalLen,
                &responseHolder, exception) == false) {
      free(data);
      return (char *)0;
   }
   free(data);

   response = strFromBlobAlloc(responseHolder.data, responseHolder.dataLen);
   free(responseHolder.data);

   if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: Got response for unSubscribe(): %s", response);

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
   ResponseHolder responseHolder;
   char *response;

   if (key == 0 || exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "xmlBlasterClient: Please provide valid arguments to xmlBlasterErase()");
      if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
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

   if (sendData(xb, XMLBLASTER_ERASE, data, totalLen,
                &responseHolder, exception) == false) {
      free(data);
      return (char *)0;
   }
   free(data);

   response = strFromBlobAlloc(responseHolder.data, responseHolder.dataLen);
   free(responseHolder.data);

   if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: Got response for erase(): %s", response);

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
   ResponseHolder responseHolder;
   char *response;
   XmlBlasterException exception;

   if (!xb->isInitialized || !xb->isConnected(xb)) return (char *)0;
   
   if (sendData(xb, XMLBLASTER_PING, (const char *)qos,
                (qos == (const char *)0) ? 0 : strlen(qos),
                &responseHolder, &exception) == false) {
      return (char *)0;
   }

   response = strFromBlobAlloc(responseHolder.data, responseHolder.dataLen);
   free(responseHolder.data);
   if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: Got response for ping '%s'\n", response);
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
   size_t currIndex;
   ResponseHolder responseHolder;
   MsgUnitArr *msgUnitArr = (MsgUnitArr *)malloc(sizeof(MsgUnitArr));

   if (key == 0 || exception == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "xmlBlasterClient: Please provide valid arguments to xmlBlasterGet()");
      if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
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

   if (sendData(xb, XMLBLASTER_GET, data, totalLen,
                &responseHolder, exception) == false) {
      free(data);
      return (MsgUnitArr *)0;
   }
   free(data);

   currpos = 0;
   currIndex = 0;
   msgUnitArr->len = 10;
   msgUnitArr->msgUnitArr = (MsgUnit *)calloc(msgUnitArr->len, sizeof(MsgUnit));
   while (currpos < responseHolder.dataLen) {
      char ptr[56];

      if (currIndex >= msgUnitArr->len) {
         msgUnitArr->len += 10;
         msgUnitArr->msgUnitArr = (MsgUnit *)realloc(msgUnitArr->msgUnitArr, msgUnitArr->len * sizeof(MsgUnit));
      }

      {
         MsgUnit *msgUnit = &msgUnitArr->msgUnitArr[currIndex++];
        
         // read QoS
         msgUnit->qos = strcpyAlloc(responseHolder.data+currpos);
         currpos += strlen(msgUnit->qos)+1;
        
         // read key
         if (currpos < responseHolder.dataLen) {
            msgUnit->key = strcpyAlloc(responseHolder.data+currpos);
            currpos += strlen(msgUnit->key)+1;
         }
        
         // read content
         if (currpos < responseHolder.dataLen) {
            strcpy(ptr, responseHolder.data+currpos);
            currpos += strlen(ptr)+1;
            trim(ptr);
            sscanf(ptr, "%u", &msgUnit->contentLen);
        
            msgUnit->content = (char *)malloc(msgUnit->contentLen * sizeof(char));
            memcpy(msgUnit->content, responseHolder.data+currpos, msgUnit->contentLen);
            currpos += msgUnit->contentLen;
         }
      }
   }

   if (currIndex == 0) {
      free(msgUnitArr->msgUnitArr);
      msgUnitArr->len = 0;
   }
   else if (currIndex < msgUnitArr->len) {
      msgUnitArr->msgUnitArr = (MsgUnit *)realloc(msgUnitArr->msgUnitArr, currIndex * sizeof(MsgUnit));
      msgUnitArr->len = currIndex; 
   }

   free(responseHolder.data);

   if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: Returned %u messages for get()\n", msgUnitArr->len);

   return msgUnitArr;
}
