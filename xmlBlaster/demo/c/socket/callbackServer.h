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

#define bool int
#define true 1
#define false 0

static const bool XMLBLASTER_DEBUG = true;

typedef struct MsgUnitStruct {
   char *key;               // XML formatted ASCII string of message key
   unsigned char *content;  // Raw data
   int contentLen;
   char *qos;               // XML formatted ASCII string of Quality of Service
} MsgUnit;

// See org.xmlBlaster.util.enum.MethodName.java
//static const char * const CONNECT = "connect";
//enum METHOD_NAME {
#define XMLBLASTER_CONNECT "connect"
#define XMLBLASTER_DISCONNECT "disconnect"
#define XMLBLASTER_PING "ping"
#define XMLBLASTER_EXCEPTION "exception"
#define XMLBLASTER_UPDATE "update"
#define XMLBLASTER_PUBLISH "publish"
#define XMLBLASTER_GET "get"
#define XMLBLASTER_SUBSCRIBE "subscribe"
#define XMLBLASTER_UNSUBSCRIBE "unSubscribe"
#define XMLBLASTER_ERASE "erase"
//};

#define  MAX_METHODNAME_LEN 20
#define  MAX_SESSIONID_LEN 256
typedef struct ResponseHolderStruct {
   long msgLen;
   bool checksum;
   bool compressed;
   char type;
   char version;
   long requestId;
   char methodName[MAX_METHODNAME_LEN];
   char secretSessionId[MAX_SESSIONID_LEN];
   long dataLenUncompressed;
   long dataLen;
   unsigned char *data; // allocated with malloc, you need to free() it yourself, is compressed if marked as such
} ResponseHolder;

#define XMLBLASTEREXCEPTION_ERRORCODE_LEN 56
#define XMLBLASTEREXCEPTION_MESSAGE_LEN 1024
#define XMLBLASTEREXCEPTION_MESSAGE_FMT "%1020s" 
typedef struct XmlBlasterExceptionStruct {
   bool remote; // true if exception is from remote
   char errorCode[XMLBLASTEREXCEPTION_ERRORCODE_LEN];
   char message[XMLBLASTEREXCEPTION_MESSAGE_LEN];
} XmlBlasterException;

#define MSG_LEN_FIELD_LEN 10
#define MSG_FLAG_FIELD_LEN 6
//static const int MSG_FLAG_FIELD_LEN = 6;
enum MSG_FLAG_POS_ENUM {
   MSG_FLAG_POS_CHECKSUM = MSG_LEN_FIELD_LEN,
   MSG_FLAG_POS_COMPRESS,
   MSG_FLAG_POS_TYPE,
   MSG_FLAG_POS_RESERVED1,
   MSG_FLAG_POS_RESERVED2,
   MSG_FLAG_POS_VERSION,
   MSG_POS_REQESTID
};

// Settings for MSG_FLAG_POS_TYPE
enum XMLBLASTER_MSG_TYPE_ENUM {
   MSG_TYPE_INVOKE = 73,
   MSG_TYPE_RESPONSE = 82,
   MSG_TYPE_EXCEPTION = 69
};

#define XMLBLASTER_VERSION 49

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

typedef char *( * XmlBlasterConnect)(const char * const qos, XmlBlasterException *exception);
typedef bool  ( * XmlBlasterDisconnect)(const char * const qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterPublish)(MsgUnit *msgUnit, XmlBlasterException *exception);
typedef bool  ( * IsConnected)();

typedef struct XmlBlasterAccessStruct {
   XmlBlasterConnect connect;   
   XmlBlasterDisconnect disconnect;   
   XmlBlasterPublish publish;
   IsConnected isConnected;
} XmlBlasterAccess;


extern XmlBlasterAccess getXmlBlasterAccess(int argc, char** argv);
extern int readn(int fd, char *ptr, int nbytes);
extern void initConnection(int argc, char** argv);
extern void initCallbackServer(callbackData *data);
extern int getLength(char *data);
extern int isListening();
extern void shutdownCallbackServer();
extern char *contentToString(char *content, MsgUnit *msg);
extern char *messageUnitToXml(MsgUnit *msg);


char *blobcpy_alloc(const unsigned char *blob, const int len);
char *strcpyAlloc(const char *src);
extern int strcpy_alloc(char **into_string, const char *from_string);
char *strncpy0(char * const to, const char * const from, const int maxLen);
void trim(unsigned char *s);
void toReadableDump(unsigned char *data, int len, unsigned char *readable);

