/*----------------------------------------------------------------------------
Name:      callbackServer.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Establish a listen socket for xmlBlaster callbacks
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   gcc -Wall -DUSE_MAIN -o callbackServer callbackServer.c
           cl /MT -DUSE_MAIN -D_WINDOWS callbackServer.c ws2_32.lib
-----------------------------------------------------------------------------*/
#include <string.h>      // strcat()
#include "callbackServer.h"

#define NSTRS       3           /* no. of strings  */

static int sock_fd = -1;

/**
 * Read the given amount of bytes
 * @return number of bytes read
 */
int readn(int fd, char *ptr, int nbytes)
{
   int nleft, nread;
   nleft = nbytes;
   while(nleft > 0) {
      nread = read(fd, ptr, nleft);
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
 * Open a socket
 * xmlBlaster will connect and send callback messages
 */
void initCallbackServer(callbackData *cbArgs)
{
   int ns, cli_len, keyLen, qosLen;
   char *pp;
   char *rawData = NULL;
   struct hostent *hostP = NULL;
   struct sockaddr_in serv_addr, cli_addr;
   char msgLengthP[MSG_LEN_FIELD_LEN+1];
   char msgFlagP[MSG_FLAG_FIELD_LEN+1];
   int numRead, msgLength;
   MsgUnit messageUnit;

   char serverHostName[256];
   if (cbArgs->hostCB == NULL) {
      gethostname(serverHostName, 125);
      cbArgs->hostCB = serverHostName;
   }   

   if (XMLBLASTER_DEBUG) printf("callbackServer: Starting callback server -socket.hostCB %s -socket.portCB %d\n", cbArgs->hostCB, cbArgs->portCB);

   /*
    * Get a socket to work with.
    */
   if ((sock_fd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
       perror("callbackServer: socket");
       return;
   }

   /*
    * Create the address we will be binding to.
    */
   serv_addr.sin_family = AF_INET;
   hostP = gethostbyname(cbArgs->hostCB);
   if (hostP != NULL)
      serv_addr.sin_addr.s_addr = ((struct in_addr *)(hostP->h_addr))->s_addr; //inet_addr("192.168.1.2");
   else
      serv_addr.sin_addr.s_addr = htonl(INADDR_ANY);
   serv_addr.sin_port = htons(cbArgs->portCB);

   if (bind(sock_fd, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
       perror("callbackServer: bind");
       return;
   }

   /*
    * Listen on the socket.
    */
   if (listen(sock_fd, 5) < 0) {
       perror("callbackServer: listen");
       return;
   }

   if (XMLBLASTER_DEBUG) printf("callbackServer: Waiting for xmlBlaster ...\n");

   /*
    * Accept connections.  When we accept one, ns
    * will be connected to the client.  cli_addr will
    * contain the address of the client.
    */
   cli_len = sizeof(cli_addr);
   if ((ns = accept(sock_fd, (struct sockaddr *)&cli_addr, &cli_len)) < 0) {
       perror("callbackServer: accept");
       return;
   }
   if (XMLBLASTER_DEBUG) printf("callbackServer: XmlBlaster connected from %d:%hd\n", cli_addr.sin_addr.s_addr, cli_addr.sin_port);

   while (1) {
      /*
       * Then we read callback messages ...
       * The first 10 bytes are the message length (as a string)
       */
      numRead = readn(ns, msgLengthP, MSG_LEN_FIELD_LEN);
      if (numRead != MSG_LEN_FIELD_LEN) {
         if (XMLBLASTER_DEBUG) printf("callbackServer: ERROR Callback data 'length' from xmlBlaster is corrupted");
         return;
      }
      msgLengthP[numRead] = 0;
      msgLength = atoi(msgLengthP);
      if (XMLBLASTER_DEBUG) printf("callbackServer: Callback data from xmlBlaster arrived, message length %d bytes\n", msgLength);

      /* ignore flag bits (pos 10-13) */
      numRead = readn(ns, msgFlagP, MSG_FLAG_FIELD_LEN);
      if (numRead != MSG_FLAG_FIELD_LEN) {
         if (XMLBLASTER_DEBUG) printf("callbackServer: ERROR Callback data 'flag' from xmlBlaster is corrupted");
         return;
      }

      /* read the message itself ... */
      rawData = (char *)malloc((msgLength+1)*sizeof(char));
      numRead = readn(ns, rawData, msgLength);
      *(rawData + msgLength) = 0;
      if (XMLBLASTER_DEBUG) printf("callbackServer: Callback data from xmlBlaster arrived:\n%s\n", rawData);

      /*
         We keep the raw data, and insert some '\0' (string end),
         and let messageUnit members point to correct memory area:
      */
      pp = rawData;
      keyLen = getLength(pp);
      pp += MSG_LEN_FIELD_LEN;
      messageUnit.xmlKey = pp;
      pp += keyLen;
      qosLen = getLength(pp);
      *pp = '\0';             /* terminate key string */

      pp += MSG_LEN_FIELD_LEN;
      messageUnit.qos = pp;
      pp += qosLen;
      messageUnit.contentLength = getLength(pp);
      *pp = '\0';             /* terminate qos string */

      pp += MSG_LEN_FIELD_LEN;
      messageUnit.content = pp;

      cbArgs->update(&messageUnit);
      
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

int isListening()
{
   return sock_fd > -1;
}

void shutdownCallbackServer()
{
   if (isListening())
      close(sock_fd);
}

/**
 * NOTE: You need to free the returned pointer with free()!
 *
 * @return A ASCII XML formatted message
 */
char *messageUnitToXml(MsgUnit *msg)
{
        //char content[msg->contentLength+1];
   char *content = malloc(msg->contentLength+1);
   int len = 100 + strlen(msg->xmlKey) + msg->contentLength + strlen(msg->qos);
   char *xml = (char *)malloc(len*sizeof(char));
   sprintf(xml, "%s\n<content><![CDATA[%s]]></content>\n%s",
                      msg->xmlKey,
                      contentToString(content, msg), /* append \0 */
                      msg->qos);
        free(content);
   return xml;
}

char *contentToString(char *content, MsgUnit *msg)
{
   strncpy(content, msg->content, msg->contentLength);
   *(content + msg->contentLength) = 0;
   return content;
}


#ifdef USE_MAIN
int main(int argc, char** argv)
{
   int iarg;
   callbackData data;
   data.hostCB = "";
   data.portCB = 7610;

   for (iarg=0; iarg < argc-1; iarg++) {
      if (strcmp(argv[iarg], "-socket.hostCB") == 0)
         data.hostCB = argv[++iarg];
      else if (strcmp(argv[iarg], "-socket.portCB") == 0)
         data.portCB = atoi(argv[++iarg]);
   }

   initCallbackServer(&data);

   shutdownCallbackServer();

   return 0;
}
#endif // USE_MAIN
