/*----------------------------------------------------------------------------
Name:      xmlBlaster/src/c/util/msgUtil.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/
#ifndef XMLBLASTER_MSGUTIL_H
#define XMLBLASTER_MSGUTIL_H

#include <util/basicDefs.h>

#define MAX_ERRNO_LEN 256
#define XMLBLASTER_MAX_USAGE_LEN 2048 /* Change XmlBlasterAccessUnparsed.c accordingly */
#define MAX_REQUESTID_LEN 256
#define MAX_SECRETSESSIONID_LEN 256

/* See org.xmlBlaster.util.enum.MethodName.java */
#define MAX_METHODNAME_LEN 20
#define XMLBLASTER_CONNECT "connect"
#define XMLBLASTER_DISCONNECT "disconnect"
#define XMLBLASTER_PING "ping"
#define XMLBLASTER_UPDATE "update"
#define XMLBLASTER_PUBLISH "publish"
#define XMLBLASTER_GET "get"
#define XMLBLASTER_SUBSCRIBE "subscribe"
#define XMLBLASTER_UNSUBSCRIBE "unSubscribe"
#define XMLBLASTER_ERASE "erase"

typedef enum XMLBLASTER_LOG_LEVEL_ENUM {
   LOG_NOLOG=0,/* don't use */
   LOG_ERROR,  /* supported, use for programming errors */
   LOG_WARN,   /* supported, use for user errors and wrong configurations */
   LOG_INFO,   /* supported, use for success information only */
   LOG_CALL,   /* don't use */
   LOG_TIME,   /* don't use */
   LOG_TRACE,  /* supported, use for debugging purposes */
   LOG_DUMP,   /* supported, use for debugging purposes */
   LOG_PLAIN   /* don't use */
} XMLBLASTER_LOG_LEVEL;
extern void xmlBlasterDefaultLogging(XMLBLASTER_LOG_LEVEL currLevel,
                              XMLBLASTER_LOG_LEVEL level,
                              const char *location, const char *fmt, ...);
extern XMLBLASTER_LOG_LEVEL parseLogLevel(const char *logLevelStr);
extern const char *getLogLevelStr(XMLBLASTER_LOG_LEVEL logLevel);
extern bool doLog(XMLBLASTER_LOG_LEVEL currLevel, XMLBLASTER_LOG_LEVEL level);

/**
 * Holds arbitrary raw data and its length
 */
typedef struct XmlBlasterBlobStruct {
   size_t dataLen;
   char *data;
} XmlBlasterBlob;

/* Holds a message */
/* All member pointers are allocated with malloc(), you need to free() them */
typedef struct MsgUnitStruct {
   char *key;               /* XML formatted ASCII string of message key */
   size_t contentLen;       /* Number of bytes in content */
   char *content;           /* Raw data (not 0 terminated) */
   char *qos;               /* XML formatted ASCII string of Quality of Service */
   char *responseQos;       /* Used to transport the response QoS string back to caller */
} MsgUnit;

typedef struct MsgUnitStructArr {
   bool isOneway;
   size_t len;
   MsgUnit *msgUnitArr;
} MsgUnitArr;

/* Used to transport information back to callback functions */
typedef struct MsgRequestInfoStruct {
   const char *requestIdStr;
   const char *methodName;
   char responseType;
   XmlBlasterBlob blob;
} MsgRequestInfo;

#define XMLBLASTEREXCEPTION_ERRORCODE_LEN 56
#define XMLBLASTEREXCEPTION_MESSAGE_LEN 1024
typedef struct XmlBlasterExceptionStruct {
   bool remote; /* true if exception is from remote */
   char errorCode[XMLBLASTEREXCEPTION_ERRORCODE_LEN];
   char message[XMLBLASTEREXCEPTION_MESSAGE_LEN];
} XmlBlasterException;

extern void initializeXmlBlasterException(XmlBlasterException *xmlBlasterException);

extern XmlBlasterBlob *blobcpyAlloc(XmlBlasterBlob *blob, const char *data, size_t dataLen);
extern XmlBlasterBlob *freeXmlBlasterBlobContent(XmlBlasterBlob *blob);

extern const char *getXmlBlasterVersion();
extern char *getStackTrace(int maxNumOfLines);
extern void sleepMillis(long millis);
extern void freeMsgUnitData(MsgUnit *msgUnit);
extern void freeMsgUnit(MsgUnit *msgUnit);
extern void freeMsgUnitArr(MsgUnitArr *msgUnitArr);
extern char *messageUnitToXml(MsgUnit *msg);
extern char *strFromBlobAlloc(const char *blob, const size_t len);
extern char *strcpyAlloc(const char *src);
extern char *strcpyRealloc(char **dest, const char *src);
extern char *strcatAlloc(char **dest, const char *src);
extern char *strncpy0(char * const to, const char * const from, const size_t maxLen);
extern void trim(char *s);
extern char *blobDump(XmlBlasterBlob *blob);
extern char *toReadableDump(char *data, size_t len);
extern struct hostent * gethostbyname_re (const char *host,struct hostent *hostbuf,char **tmphstbuf,size_t *hstbuflen);


#endif /* XMLBLASTER_MSGUTIL_H */

