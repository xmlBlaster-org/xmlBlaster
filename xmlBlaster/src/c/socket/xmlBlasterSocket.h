/*----------------------------------------------------------------------------
Name:      xmlBlasterSocket.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   SOCKET internal header (not included directly by clients)
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
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
#include <msgUtil.h>

/*
#define bool int
#define true 1
#define false 0
*/

#define  MAX_METHODNAME_LEN 20
#define  MAX_SESSIONID_LEN 256
typedef struct ResponseHolderStruct {
   size_t msgLen;
   bool checksum;
   bool compressed;
   char type;
   char version;
   long requestId;
   char methodName[MAX_METHODNAME_LEN];
   char secretSessionId[MAX_SESSIONID_LEN];
   size_t dataLenUncompressed;
   size_t dataLen;
   char *data; // allocated with malloc, you need to free() it yourself, is compressed if marked as such
} ResponseHolder;

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


extern int readn(int fd, char *ptr, int nbytes);
extern int getLength(char *data);
extern int isListening();
extern void shutdownCallbackServer();
extern char *contentToString(char *content, MsgUnit *msg);
extern char *messageUnitToXml(MsgUnit *msg);
