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
#include <socket/xmlBlasterSocket.h>
#include <CallbackServerUnparsed.h>

#ifdef _WINDOWS
#  define socklen_t int
#endif

static void initCallbackServer(CallbackServerUnparsed *cb);
static int isListening(CallbackServerUnparsed *cb);
static void shutdownCallbackServer(CallbackServerUnparsed *cb);

/**
 * See header for a description. 
 */
CallbackServerUnparsed *getCallbackServerUnparsed(int argc, char** argv, UpdateFp update) {
   int iarg;

   CallbackServerUnparsed *cb = (CallbackServerUnparsed *)calloc(1, sizeof(CallbackServerUnparsed));
   cb->listenSocket = -1;
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
   int ns;
   int keyLen, qosLen;
   socklen_t cli_len;
   char *pp;
   char *rawData = NULL;
   struct hostent *hostP = NULL;
   struct sockaddr_in serv_addr, cli_addr;
   char msgLengthP[MSG_LEN_FIELD_LEN+1];
   char msgFlagP[MSG_FLAG_FIELD_LEN+1];
   size_t numRead, msgLength;
   MsgUnit messageUnit;

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
   if ((ns = (int)accept(cb->listenSocket, (struct sockaddr *)&cli_addr, &cli_len)) < 0) {
       perror("[CallbackServerUnparsed] accept");
       return;
   }
   if (cb->debug) printf("[CallbackServerUnparsed] XmlBlaster connected from %d:%hd\n", cli_addr.sin_addr.s_addr, cli_addr.sin_port);

   while (1) {
      XmlBlasterException xmlBlasterException;
      *xmlBlasterException.errorCode = 0;

      /*
       * Then we read callback messages ...
       * The first 10 bytes are the message length (as a string)
       */
      numRead = readn(ns, msgLengthP, MSG_LEN_FIELD_LEN);
      if (numRead != MSG_LEN_FIELD_LEN) {
         if (cb->debug) printf("[CallbackServerUnparsed] ERROR Callback data 'length' from xmlBlaster is corrupted");
         return;
      }
      msgLengthP[numRead] = 0;
      msgLength = atoi(msgLengthP);
      if (cb->debug) printf("[CallbackServerUnparsed] Callback data from xmlBlaster arrived, message length %d bytes\n", msgLength);

      /* ignore flag bits (pos 10-13) */
      numRead = readn(ns, msgFlagP, MSG_FLAG_FIELD_LEN);
      if (numRead != MSG_FLAG_FIELD_LEN) {
         if (cb->debug) printf("[CallbackServerUnparsed] ERROR Callback data 'flag' from xmlBlaster is corrupted");
         return;
      }

      /* read the message itself ... */
      rawData = (char *)malloc((msgLength+1)*sizeof(char));
      numRead = readn(ns, rawData, msgLength);
      *(rawData + msgLength) = 0;
      if (cb->debug) printf("[CallbackServerUnparsed] Callback data from xmlBlaster arrived:\n%s\n", rawData);

      /*
         We keep the raw data, and insert some '\0' (string end),
         and let messageUnit members point to correct memory area:
      */
      pp = rawData;
      keyLen = getLength(pp);
      pp += MSG_LEN_FIELD_LEN;
      messageUnit.key = pp;
      pp += keyLen;
      qosLen = getLength(pp);
      *pp = '\0';             /* terminate key string */

      pp += MSG_LEN_FIELD_LEN;
      messageUnit.qos = pp;
      pp += qosLen;
      messageUnit.contentLen = getLength(pp);
      *pp = '\0';             /* terminate qos string */

      pp += MSG_LEN_FIELD_LEN;
      messageUnit.content = pp;

      char *returnQos = cb->update(&messageUnit, &xmlBlasterException);

      if (*xmlBlasterException.errorCode) {
         // !!! throw the exception to xmlBlaster is missing !!!
         printf("CallbackServerUnparsed.update(): Returning the XmlBlasterException '%s' to the server is not yet implemented:\n%s\n", xmlBlasterException.errorCode, xmlBlasterException.message);
      }
      else {
         // !!! return returnQos data to xmlBlaster is missing !!!
         printf("CallbackServerUnparsed.update(): Returning the UpdateReturnQos '%s' to the server is not yet implemented\n", returnQos);
      }

      free(returnQos);
      free(rawData);
   }
}

bool isListening(CallbackServerUnparsed *cb)
{
   if (cb->listenSocket > -1) {
      return true;
   }
   return false;
}

static void shutdownCallbackServer(CallbackServerUnparsed *cb)
{
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
char *myUpdate(MsgUnit *msg, XmlBlasterException *xmlBlasterException)
{
   // Do something useful with the arrived message
   char *xml = messageUnitToXml(msg);
   printf("client.update(): Asynchronous message update arrived:\n%s\n", xml);
   free(xml);
   if (false) { // How to throw an exception
      strncpy0(xmlBlasterException->errorCode, "user.notWanted", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      strncpy0(xmlBlasterException->message, "I don't want this message", XMLBLASTEREXCEPTION_MESSAGE_LEN);
      return 0;
   }
   return strcpyAlloc("<qos/>"); // Everything is OK
}

/**
 * Invoke: CallbackServerUnparsed -debug true
 */
int main(int argc, char** argv)
{
   CallbackServerUnparsed *cb = getCallbackServerUnparsed(argc, argv, myUpdate);
   printf("[main] Created CallbackServerUnparsed instance, creating listener ...\n");
   cb->initCallbackServer(cb);

   // This code is never reached
   printf("[main] Created socket listener\n");
   cb->shutdown(cb);
   printf("[main] Socket listener is shutdown\n");
   freeCallbackServerUnparsed(cb);
   return 0;
}
#endif // USE_MAIN
