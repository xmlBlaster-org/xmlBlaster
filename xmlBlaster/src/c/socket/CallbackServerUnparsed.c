/*----------------------------------------------------------------------------
Name:      CallbackServerUnparsed.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Establish a listen socket for xmlBlaster callbacks
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   gcc -Wall -DUSE_MAIN -o CallbackServerUnparsed CallbackServerUnparsed.c
           cl /MT -DUSE_MAIN -D_WINDOWS CallbackServerUnparsed.c ws2_32.lib
-----------------------------------------------------------------------------*/
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
 * Read the given amount of bytes
 * @return number of bytes read
 */
size_t readn(int fd, char *ptr, size_t nbytes)
{
   size_t nleft, nread;
   nleft = nbytes;
   while(nleft > 0) {
      nread = recv(fd, ptr, (int)nleft, 0);
      if (nread < 0)
         return nread; /* error, return < 0 */
      else if (nread == 0)
         break;        /* EOF */
      nleft -= nread;
      ptr += nread;
   }
   return (nbytes-nleft);
}

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
      printf("[CallbackServer] Starting callback server -dispatch/callback/plugin/socket/hostname %s -dispatch/callback/plugin/socket/port %d ...\n",
             cb->hostCB, cb->portCB);

   /*
    * Get a socket to work with.
    */
   if ((cb->listenSocket = (int)socket(AF_INET, SOCK_STREAM, 0)) < 0) {
       perror("[CallbackServer] socket");
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
       perror("[CallbackServer] bind");
       return;
   }

   /*
    * Listen on the socket.
    */
   if (listen(cb->listenSocket, 5) < 0) {
       perror("[CallbackServer] listen");
       return;
   }

   if (cb->debug) printf("[CallbackServer] Waiting for xmlBlaster to connect ...\n");

   /*
    * Accept connections.  When we accept one, ns
    * will be connected to the client.  cli_addr will
    * contain the address of the client.
    */
   cli_len = (socklen_t)sizeof(cli_addr);
   if ((ns = (int)accept(cb->listenSocket, (struct sockaddr *)&cli_addr, &cli_len)) < 0) {
       perror("[CallbackServer] accept");
       return;
   }
   if (cb->debug) printf("[CallbackServer] XmlBlaster connected from %d:%hd\n", cli_addr.sin_addr.s_addr, cli_addr.sin_port);

   while (1) {
      XmlBlasterException xmlBlasterException;
      *xmlBlasterException.errorCode = 0;

      /*
       * Then we read callback messages ...
       * The first 10 bytes are the message length (as a string)
       */
      numRead = readn(ns, msgLengthP, MSG_LEN_FIELD_LEN);
      if (numRead != MSG_LEN_FIELD_LEN) {
         if (cb->debug) printf("[CallbackServer] ERROR Callback data 'length' from xmlBlaster is corrupted");
         return;
      }
      msgLengthP[numRead] = 0;
      msgLength = atoi(msgLengthP);
      if (cb->debug) printf("[CallbackServer] Callback data from xmlBlaster arrived, message length %d bytes\n", msgLength);

      /* ignore flag bits (pos 10-13) */
      numRead = readn(ns, msgFlagP, MSG_FLAG_FIELD_LEN);
      if (numRead != MSG_FLAG_FIELD_LEN) {
         if (cb->debug) printf("[CallbackServer] ERROR Callback data 'flag' from xmlBlaster is corrupted");
         return;
      }

      /* read the message itself ... */
      rawData = (char *)malloc((msgLength+1)*sizeof(char));
      numRead = readn(ns, rawData, msgLength);
      *(rawData + msgLength) = 0;
      if (cb->debug) printf("[CallbackServer] Callback data from xmlBlaster arrived:\n%s\n", rawData);

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

/*
 * @return The first 10 bytes are analyzed and returned as length info
 */
int getLength(char *data)
{
   char tmp[MSG_LEN_FIELD_LEN+1];
   sprintf(tmp, "%10.10s", data);
   return atoi(tmp);
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

int main(int argc, char** argv)
{
   CallbackServerUnparsed *cb = getCallbackServerUnparsed(argc, argv, myUpdate);
   cb->initCallbackServer(cb);
   cb->shutdown(cb);
   freeCallbackServerUnparsed(cb);
   return 0;
}
#endif // USE_MAIN
