/*----------------------------------------------------------------------------
Name:      xmlBlasterSocket.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   SOCKET internal header (not included directly by clients)
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/
#include <stdlib.h>
#ifdef _WINDOWS
#  include <winsock2.h>
#  define ssize_t signed int
#else
#  include <sys/socket.h>
#  include <netinet/in.h>
#  include <netdb.h>
#  include <arpa/inet.h>   /* inet_addr() */
#  include <unistd.h>      /* gethostname(), sleep(seconds) */
#endif
#include <msgUtil.h>

#define  MAX_SESSIONID_LEN 256

/* Settings for MSG_FLAG_POS_TYPE */
typedef enum XMLBLASTER_MSG_TYPE_ENUM {
   MSG_TYPE_INVOKE = 73,
   MSG_TYPE_RESPONSE = 82,
   MSG_TYPE_EXCEPTION = 69
} XMLBLASTER_MSG_TYPE;

typedef struct SocketDataHolderStruct {
   size_t msgLen;
   bool checksum;
   bool compressed;
   char type;  /* XMLBLASTER_MSG_TYPE */
   char version;
   char requestId[MAX_REQUESTID_LEN];
   char methodName[MAX_METHODNAME_LEN];
   char secretSessionId[MAX_SESSIONID_LEN];
   size_t dataLenUncompressed;
   XmlBlasterBlob blob; /* blob.data is allocated with malloc, you need to free() it yourself, is compressed if marked as such */
} SocketDataHolder;

#define MSG_LEN_FIELD_LEN 10
#define MSG_FLAG_FIELD_LEN 6
/* static const int MSG_FLAG_FIELD_LEN = 6; */
enum MSG_FLAG_POS_ENUM {
   MSG_FLAG_POS_CHECKSUM = MSG_LEN_FIELD_LEN,
   MSG_FLAG_POS_COMPRESS,
   MSG_FLAG_POS_TYPE,
   MSG_FLAG_POS_RESERVED1,
   MSG_FLAG_POS_RESERVED2,
   MSG_FLAG_POS_VERSION,
   MSG_POS_REQESTID
};

#define XMLBLASTER_VERSION 49


extern ssize_t writen(int fd, char *ptr, size_t nbytes);
extern ssize_t readn(int fd, char *ptr, size_t nbytes);
extern char *encodeSocketMessage(
              enum XMLBLASTER_MSG_TYPE_ENUM msgType,
              const char * const requestId, 
              const char * const methodName,
              const char * const secretSessionId,
              const char *data,
              size_t dataLen,
              bool debug,
              size_t *rawMsgLen);
extern char *encodeMsgUnit(MsgUnit *msgUnit, size_t *totalLen, bool debug);
extern bool parseSocketData(int xmlBlasterSocket, SocketDataHolder *socketDataHolder, XmlBlasterException *exception, bool debug);
extern void convertToXmlBlasterException(XmlBlasterBlob *blob, XmlBlasterException *exception, bool debug);
extern MsgUnitArr *parseMsgUnitArr(size_t dataLen, char *data);

