/*----------------------------------------------------------------------------
Name:      callbackServer.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Establish a listen socket for xmlBlaster callbacks
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   gcc -DUSE_MAIN -o callbackServer callbackServer.c
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#ifdef _WINDOWS
#  include <winsock2.h>
#else
#  include <sys/socket.h>
#  include <netinet/in.h>
#  include <netdb.h>
#  include <arpa/inet.h>   // inet_addr()
#  include <unistd.h>      // gethostname(), sleep(seconds)
#endif

#define XMLBLASTER_DEBUG 1

typedef struct MsgUnitStruct {
   char *xmlKey;   /* XML formatted ASCII string of message key */
   char *content;  /* Raw data */
   int contentLength;
   char *qos;      /* XML formatted ASCII string of Quality of Service */
} MsgUnit;


#define MSG_LEN_FIELD_LEN 10
#define MSG_FLAG_FIELD_LEN 6
#define LenFormatStr "%10.10d"
/* sprintf(LenFormatStr, "%%%d.%dd", MSG_LEN_FIELD_LEN, MSG_LEN_FIELD_LEN); */

/* Callback from xmlBlaster */
typedef void (*updateFp)(MsgUnit *msg);

typedef struct callbackDataStruct {
   char *hostCB;
   int portCB;
   /**
    * void update(MsgUnit *msg)   //////(char *key, char *content, int contentLen, char *qos)
    */
   updateFp update;
} callbackData;

/* for pthread */
typedef void * (*cbFp)(void *);

extern void initCallbackServer(callbackData *data);
extern int getLength(char *data);
extern int isListening();
extern int isConnected();
extern void shutdownCallbackServer();
extern char *contentToString(char *content, MsgUnit *msg);
extern char *messageUnitToXml(MsgUnit *msg);

