/*----------------------------------------------------------------------------
Name:      callbackServer.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Establish a listen socket for xmlBlaster callbacks
Author:    "Marcel Ruff" <ruff@swand.lake.de>
Compile:   gcc -DUSE_MAIN -o callbackServer callbackServer.c
-----------------------------------------------------------------------------*/

#define XMLBLASTER_DEBUG 1

typedef struct MessageUnitStruct {
   char *xmlKey;   /* XML formatted ASCII string of message key */
   char *content;  /* Raw data */
   int contentLength;
   char *qos;      /* XML formatted ASCII string of Quality of Service */
} MessageUnit;


#define MSG_LEN_FIELD_LEN 10
#define MSG_FLAG_FIELD_LEN 4
#define LenFormatStr "%10.10d"
/* sprintf(LenFormatStr, "%%%d.%dd", MSG_LEN_FIELD_LEN, MSG_LEN_FIELD_LEN); */

/* Callback from xmlBlaster */
typedef void (*updateFp)(MessageUnit *msg);

typedef struct callbackDataStruct {
   char *hostCB;
   int portCB;
   /**
    * void update(MessageUnit *msg)   //////(char *key, char *content, int contentLen, char *qos)
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
extern char *contentToString(char *content, MessageUnit *msg);
extern char *messageUnitToXml(MessageUnit *msg);

