/*----------------------------------------------------------------------------
Name:      client.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   client connects with raw socket to xmlBlaster
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   gcc -o client client.c callbackServer.c -lpthread
Compile-Win: cl /MT -D_WINDOWS client.c callbackServer.c ws2_32.lib
Invoke:    client -socket.hostCB develop -socket.portCB 7608
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#ifdef _WINDOWS
#else
#  include <pthread.h>
#endif
#include "callbackServer.h"

int socketToXmlBlaster = -1;

int initConnection(int argc, char** argv)
{
   int iarg;
   char *servTcpPort = "7608";

   struct sockaddr_in xmlBlasterAddr;
   struct hostent *hostP = NULL;
   struct servent *portP = NULL;

   char serverHostName[256];
   strcpy(serverHostName, "localhost");
   gethostname(serverHostName, 125);

   for (iarg=0; iarg < argc-1; iarg++) {
      if (strcmp(argv[iarg], "-socket.host") == 0)
         strcpy(serverHostName, argv[++iarg]);
      else if (strcmp(argv[iarg], "-socket.port") == 0)
         servTcpPort = argv[++iarg];
   }

   if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: Lookup xmlBlaster on -socket.host %s -socket.port %s ...\n", serverHostName, servTcpPort);

   memset((char *)&xmlBlasterAddr, 0, sizeof(xmlBlasterAddr));
   xmlBlasterAddr.sin_family=AF_INET;
   hostP = gethostbyname(serverHostName);
   portP = getservbyname(servTcpPort, "tcp");
   if (hostP != NULL) {
      xmlBlasterAddr.sin_addr.s_addr = ((struct in_addr *)(hostP->h_addr))->s_addr; //inet_addr("192.168.1.2");
      if (portP != NULL)
         xmlBlasterAddr.sin_port = portP->s_port;
      else
         xmlBlasterAddr.sin_port = htons(atoi(servTcpPort));
      socketToXmlBlaster = socket(AF_INET, SOCK_STREAM, 0);
      if (socketToXmlBlaster != -1) {
         int ret=0;
         if ((ret=connect(socketToXmlBlaster, (struct sockaddr *)&xmlBlasterAddr, sizeof(xmlBlasterAddr))) != -1) {
            if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: Connected to xmlBlaster\n");
         }
         else {
            if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: ERROR Connecting to xmlBlaster -socket.host %s -socket.port %s failed\n", serverHostName, servTcpPort); // errno
         }
      }
   }
   return 0;
}

int isConnected()
{
   return socketToXmlBlaster > -1;
}

void shutdownConnection()
{
   if (isConnected()) {
      shutdown(socketToXmlBlaster, 2);
      socketToXmlBlaster = -1;
   }
}

/**
 * raw data format:
 *   dataLen[10]  flag[4]         data
 * +-------------+--------+-----------------------+
 * The dataLen is the number of bytes of the data (without itself, witout the flag)
 * The dataLen field is of size 10 bytes
 * The flag field is of size 4 bytes (reserved for future)
 */
int sendData(char *data, int len)
{
   int numSent;
   const int headerLen = MSG_LEN_FIELD_LEN+MSG_FLAG_FIELD_LEN;
   char msgHeaderP[MSG_LEN_FIELD_LEN+MSG_FLAG_FIELD_LEN+1]; //[headerLen+1];
   
   /* Send header which contains the length of the data (first 10 bytes) ... */
   sprintf(msgHeaderP, LenFormatStr, len);
   
   /* set all flag bits to zero (pos 10-13) */
   memset(msgHeaderP+MSG_LEN_FIELD_LEN, '\0', MSG_FLAG_FIELD_LEN);

   /* send the header ... */
   numSent = send(socketToXmlBlaster, msgHeaderP, headerLen, 0);
   if (numSent !=  headerLen) {
      if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: ERROR Sent only %d bytes from %d\n", numSent, headerLen);
      return -1;
   }

   /* Send data itself ... */
   numSent = send(socketToXmlBlaster, data, len, 0);
   if (numSent < len) {
      if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: ERROR Sent only %d bytes from %d\n", numSent, len);
   }
   else {
      if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: Sent message with %d bytes: %s\n", numSent, data);
   }
   return numSent;
}

/**
 * raw data format:
 *   len[10]   key       len[10]     qos       len[10]     content
 * +---------+--------+----------+-----------+----------+------------+
 */
void publish(char *key, char *content, int contentLen, char *qos)
{
   int keyLen, qosLen, totalLen;
   char *data;

   if (key == NULL || content == NULL || qos == NULL) {
      if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: ERRROR Invalid argument=NULL in publish(), message not sent.");
      return;
   }
   keyLen = strlen(key);
   qosLen = strlen(qos);
   totalLen = keyLen + contentLen + qosLen + 3*MSG_LEN_FIELD_LEN;

   data = (char *)malloc(totalLen);
   sprintf(data, "%10.10d%s%10.10d%s%10.10d", keyLen, key, qosLen, qos, contentLen);
   memcpy(data + 3*MSG_LEN_FIELD_LEN + keyLen + qosLen, content, contentLen);

   sendData(data, totalLen);

   free(data);
}

/**
 * Here we asynchronous receive the callback from xmlBlaster
 * msg = char *key, char *content, int contentLen, char *qos
 *
 * NOTE: After this call the memory of msg is freed immediately by callbackServer.c
 *       So you need to take a copy of all msg members if needed.
 */
void update(MsgUnit *msg)
{
   char *xml = messageUnitToXml(msg);
   if (XMLBLASTER_DEBUG) printf("client.update(): Asynchronous message update arrived:\n%s\n", xml);
   free(xml);
   /*
   char content[msg->contentLength+1];
   contentToString(content, msg);
   if (XMLBLASTER_DEBUG)
      printf("client.update(): Asynchronous message update arrived:\nkey=%s\ncontent=%s\nqos=%s\n",
             msg->xmlKey, content, msg->qos);
   */
}


int main(int argc, char** argv)
{
#ifndef _WINDOWS
   pthread_t tid;
#endif
   int ret, iarg;
   char *data = NULL;
   callbackData cbArgs;

   cbArgs.hostCB = NULL;
   cbArgs.portCB = 7611;
   cbArgs.update = update;

   for (iarg=0; iarg < argc-1; iarg++) {
      if (strcmp(argv[iarg], "-socket.hostCB") == 0)
         cbArgs.hostCB = argv[++iarg];
      else if (strcmp(argv[iarg], "-socket.portCB") == 0)
         cbArgs.portCB = atoi(argv[++iarg]);
   }

#  ifndef _WINDOWS
   ret = pthread_create(&tid, NULL, (cbFp)initCallbackServer, &cbArgs);
#  endif

   initConnection(argc, argv);
   
   data = "Hello world";
   sendData(data, strlen(data));

   publish("<key oid='cpuinfo'/>", data, strlen(data), "<qos/>");

   if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: going to sleep 100 sec ...\n");
#  ifndef _WINDOWS
   sleep(10000);
#  endif
   exit(0);
}

