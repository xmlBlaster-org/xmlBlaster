/*----------------------------------------------------------------------------
Name:      CallbackServerUnparsed.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Establish a listen socket for xmlBlaster callbacks
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   gcc -g -Wall -DUSE_MAIN -I.. -o CallbackServerUnparsed CallbackServerUnparsed.c xmlBlasterSocket.c ../msgUtil.c
           cl /MT -DUSE_MAIN -D_WINDOWS CallbackServerUnparsed.c ws2_32.lib
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <socket/xmlBlasterSocket.h>
#include <CallbackServerUnparsed.h>

#ifdef _WINDOWS
#  define socklen_t int
#endif

static bool useThisSocket(CallbackServerUnparsed *cb, int socketToUse);
static void initCallbackServer(CallbackServerUnparsed *cb);
static int isListening(CallbackServerUnparsed *cb);
static bool readMessage(CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, XmlBlasterException *exception);
static void sendResponse(CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, MsgUnitArr *msgUnitArr);
static void sendXmlBlasterException(CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, XmlBlasterException *exception);
static void shutdownCallbackServer(CallbackServerUnparsed *cb);
static void closeAcceptSocket(CallbackServerUnparsed *cb);

/**
 * See header for a description. 
 */
CallbackServerUnparsed *getCallbackServerUnparsed(int argc, char** argv, UpdateFp update)
{
   int iarg;

   CallbackServerUnparsed *cb = (CallbackServerUnparsed *)calloc(1, sizeof(CallbackServerUnparsed));
   cb->listenSocket = -1;
   cb->acceptSocket = -1;
   cb->useThisSocket = useThisSocket;
   cb->initCallbackServer = initCallbackServer;
   cb->isListening = isListening;
   cb->shutdown = shutdownCallbackServer;
   cb->debug = false;
   cb->hostCB = "localhost";
   cb->portCB = DEFAULT_CALLBACK_SERVER_PORT;
   cb->update = update;

   for (iarg=0; iarg < argc-1; iarg++) {
      if (strcmp(argv[iarg], "-dispatch/callback/plugin/socket/hostname") == 0)
         cb->hostCB = argv[++iarg];
      else if (strcmp(argv[iarg], "-dispatch/callback/plugin/socket/port") == 0)
         cb->portCB = atoi(argv[++iarg]);
      else if (strcmp(argv[iarg], "-debug") == 0)
         cb->debug = !strcmp(argv[++iarg], "true");
   }
   return cb;
}

/*
 * @see header
 */
bool useThisSocket(CallbackServerUnparsed *cb, int socketToUse)
{
   struct sockaddr_in localAddr;
   socklen_t size = (socklen_t)sizeof(localAddr);
   memset((char *)&localAddr, 0, size);
   if (getsockname(socketToUse, (struct sockaddr *)&localAddr, &size) == -1) {
      printf("[CallbackServerUnparsed] Can't determine the local socket host and port, errno=%d\n", errno);
      return false;
   }
   cb->portCB = localAddr.sin_port;
   cb->hostCB = inet_ntoa(localAddr.sin_addr);

   cb->listenSocket = socketToUse;
   cb->acceptSocket = socketToUse;

   if (cb->debug) printf("[CallbackServerUnparsed] Forced callback server to reuse socket descriptor '%d' on localHostname=%s localPort=%d\n",
                         socketToUse, cb->hostCB, cb->portCB);
   return true;
}

void freeCallbackServerUnparsed(CallbackServerUnparsed *cb)
{
   free(cb);
}

/**
 * Open a socket
 * xmlBlaster will connect and send callback messages
 */
static void initCallbackServer(CallbackServerUnparsed *cb)
{
   socklen_t cli_len;
   char *rawData = NULL;
   struct hostent *hostP = NULL;
   struct sockaddr_in serv_addr, cli_addr;

   if (cb->listenSocket == -1) {
      char serverHostName[256];
      if (cb->hostCB == NULL) {
         gethostname(serverHostName, 125);
         cb->hostCB = serverHostName;
      }   

      if (cb->debug)
         printf("[CallbackServerUnparsed] Starting callback server -dispatch/callback/plugin/socket/hostname %s -dispatch/callback/plugin/socket/port %d ...\n",
                cb->hostCB, cb->portCB);

      /*
       * Get a socket to work with.
       */
      if ((cb->listenSocket = (int)socket(AF_INET, SOCK_STREAM, 0)) < 0) {
          perror("[CallbackServerUnparsed] socket");
          return;
      }

      /*
       * Create the address we will be binding to.
       */
      serv_addr.sin_family = AF_INET;
      hostP = gethostbyname(cb->hostCB);
      if (hostP != NULL)
         serv_addr.sin_addr.s_addr = ((struct in_addr *)(hostP->h_addr))->s_addr; //inet_addr("192.168.1.2");
      else
         serv_addr.sin_addr.s_addr = htonl(INADDR_ANY);
      serv_addr.sin_port = htons(cb->portCB);

      if (bind(cb->listenSocket, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
          perror("[CallbackServerUnparsed] bind");
          return;
      }

      /*
       * Listen on the socket.
       */
      if (listen(cb->listenSocket, 5) < 0) {
          perror("[CallbackServerUnparsed] listen");
          return;
      }

      if (cb->debug) printf("[CallbackServerUnparsed] Waiting for xmlBlaster to connect ...\n");

      /*
       * Accept connections.  When we accept one, ns
       * will be connected to the client.  cli_addr will
       * contain the address of the client.
       */
      cli_len = (socklen_t)sizeof(cli_addr);
      if ((cb->acceptSocket = (int)accept(cb->listenSocket, (struct sockaddr *)&cli_addr, &cli_len)) < 0) {
          perror("[CallbackServerUnparsed] accept");
          return;
      }
      if (cb->debug) printf("[CallbackServerUnparsed] XmlBlaster connected from %s:%hd\n",
                            inet_ntoa(cli_addr.sin_addr), cli_addr.sin_port);
   }
   else {
      if (cb->debug) printf("[CallbackServerUnparsed] Reusing connection socket to tunnel callback messages\n");
   }

   while (true) {
      XmlBlasterException xmlBlasterException;
      SocketDataHolder socketDataHolder;
      MsgUnitArr *msgUnitArr;
      bool success;      

      memset(&xmlBlasterException, 0, sizeof(XmlBlasterException));

      success = readMessage(cb, &socketDataHolder, &xmlBlasterException);

      if (success == false) { // EOF
         if (cb->debug) printf("[CallbackServerUnparsed] Lost socket connect to client, closing socket\n");
         closeAcceptSocket(cb);
         break;
      }

      if (*xmlBlasterException.errorCode != 0) { // Caught an exception
         if (cb->debug)
            printf("[CallbackServerUnparsed] WARNING: Couldn't read message from xmlBlaster: errorCode=%s message=%s\n",
                   xmlBlasterException.errorCode, xmlBlasterException.message);
         continue;
      }

      msgUnitArr = parseMsgUnitArr(socketDataHolder.dataLen, socketDataHolder.data);

      if (cb->update != 0) { // Client has registered to receive callback messages?
         printf("[CallbackServerUnparsed] Received callback, calling client update() ...\n");
         success = cb->update(msgUnitArr, &xmlBlasterException);
      }
      else {
         success = true;
      }

      if (success == true) {
         sendResponse(cb, &socketDataHolder, msgUnitArr);
      }
      else {
         // !!! throw the exception to xmlBlaster is missing !!!
         printf("CallbackServerUnparsed.update(): Throwing the XmlBlasterException '%s' back to the server:\n%s\n",
                xmlBlasterException.errorCode, xmlBlasterException.message);
         sendXmlBlasterException(cb, &socketDataHolder, &xmlBlasterException);
      }

      free(socketDataHolder.data);
      free(rawData);
      freeMsgUnitArr(msgUnitArr);
   }
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
   return parseSocketData(cb->acceptSocket, socketDataHolder, exception, cb->debug);
}

static void sendResponse(CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, MsgUnitArr *msgUnitArr)
{
   ssize_t numSent;
   char *rawMsg;
   size_t rawMsgLen;
   size_t dataLen;
   char *data = 0;
   size_t i;
   MsgUnit msgUnit; // we (mis)use MsgUnit for simple transformation of the exception into a raw blob
   memset(&msgUnit, 0, sizeof(MsgUnit));

   for (i=0; i<msgUnitArr->len; i++) {
      size_t num;
      char *tmp;
      printf("[CallbackServerUnparsed] Returning the UpdateReturnQos '%s' to the server.\n",
            msgUnitArr->msgUnitArr[i].responseQos);

      if (msgUnitArr->msgUnitArr[i].responseQos != 0) {
         msgUnit.qos = msgUnitArr->msgUnitArr[i].responseQos;
      }
      else {
         msgUnit.qos = strcpyAlloc("<qos/>");
      }

      if (data == 0) {
         data = encodeMsgUnit(&msgUnit, &dataLen, cb->debug);
      }
      else {
         tmp = encodeMsgUnit(&msgUnit, &num, cb->debug);
         data = (char *)realloc(data, dataLen+num);
         memcpy(data+dataLen, tmp, num);
         dataLen += num;
         free(tmp);
      }
   }

   rawMsg = encodeSocketMessage(MSG_TYPE_RESPONSE, socketDataHolder->requestId,
                             socketDataHolder->methodName, cb->secretSessionId,
                             data, dataLen, cb->debug, &rawMsgLen);
   free(data);

   numSent = send(cb->acceptSocket, rawMsg, (int)rawMsgLen, 0);

   free(rawMsg);
}

static void sendXmlBlasterException(CallbackServerUnparsed *cb, SocketDataHolder *socketDataHolder, XmlBlasterException *exception)
{
   ssize_t numSent;
   int currpos = 0;
   char *rawMsg;
   size_t rawMsgLen;
   size_t dataLen;
   char *data;
   MsgUnit msgUnit; // we (mis)use MsgUnit for simple transformation of the exception into a raw blob
   memset(&msgUnit, 0, sizeof(MsgUnit));
   
   msgUnit.qos = exception->errorCode;
   
   // see XmlBlasterException.toByteArr() and parseByteArr()
   msgUnit.contentLen = strlen(exception->errorCode) + strlen(exception->message) + 11;
   msgUnit.content = (char *)calloc(msgUnit.contentLen, sizeof(char));
   
   memcpy(msgUnit.content, exception->errorCode, strlen(exception->errorCode));
   currpos = strlen(exception->errorCode) + 4;
   
   memcpy(msgUnit.content+currpos, exception->message, strlen(exception->message));
   
   data = encodeMsgUnit(&msgUnit, &dataLen, cb->debug);

   rawMsg = encodeSocketMessage(MSG_TYPE_EXCEPTION, socketDataHolder->requestId,
                             socketDataHolder->methodName, cb->secretSessionId,
                             data, dataLen, cb->debug, &rawMsgLen);
   free(data);

   numSent = send(cb->acceptSocket, rawMsg, (int)rawMsgLen, 0);

   free(rawMsg);
}

static void closeAcceptSocket(CallbackServerUnparsed *cb)
{
   if (cb->acceptSocket != -1) {
#    ifdef _WINDOWS
     closesocket(cb->acceptSocket);
#  else
     close(cb->acceptSocket);
#  endif
     cb->acceptSocket = -1;
   }
}

static void shutdownCallbackServer(CallbackServerUnparsed *cb)
{
   closeAcceptSocket(cb);

   if (isListening(cb)) {
#  ifdef _WINDOWS
      closesocket(cb->listenSocket);
#  else
      close(cb->listenSocket);
#  endif
      cb->listenSocket = -1;
   }
}

const char *callbackServerRawUsage()
{
   return 
      "\n  -dispatch/callback/plugin/socket/hostname [localhost]"
      "\n                       The IP where to establish the callback server"
      "\n                       Can be useful on multi homed hosts"
      "\n  -dispatch/callback/plugin/socket/port [7611]"
      "\n                       The port of the callback server";
}

#ifdef USE_MAIN
bool myUpdate(MsgUnitArr *msgUnitArr, XmlBlasterException *xmlBlasterException)
{
   size_t i;
   bool testException = false;
   for (i=0; i<msgUnitArr->len; i++) {
      char *xml = messageUnitToXml(&msgUnitArr->msgUnitArr[i]);
      printf("client.update(): Asynchronous message update arrived:%s\n", xml);
      free(xml);
      msgUnitArr->msgUnitArr[i].responseQos = strcpyAlloc("<qos></qos>"); // Return QoS: Everything is OK
   }
   if (testException) {
      strncpy0(xmlBlasterException->errorCode, "user.notWanted", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      strncpy0(xmlBlasterException->message, "I don't want these messages", XMLBLASTEREXCEPTION_MESSAGE_LEN);
      return false;
   }
   return true;
}

/**
 * Invoke: CallbackServerUnparsed -debug true
 */
int main(int argc, char** argv)
{
   CallbackServerUnparsed *cb = getCallbackServerUnparsed(argc, argv, myUpdate);
   printf("[main] Created CallbackServerUnparsed instance, creating listener on socket://%s:%d...\n", cb->hostCB, cb->portCB);
   cb->initCallbackServer(cb); // blocks on socket listener

   // This code is reached only on socket EOF

   cb->shutdown(cb);
   printf("[main] Socket listener is shutdown\n");
   freeCallbackServerUnparsed(cb);
   return 0;
}
#endif // USE_MAIN
