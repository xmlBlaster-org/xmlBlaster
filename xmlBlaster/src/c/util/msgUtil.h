/*----------------------------------------------------------------------------
Name:      xmlBlaster/src/c/util/msgUtil.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/
#ifndef XMLBLASTER_MSGUTIL_H
#define XMLBLASTER_MSGUTIL_H

#include <util/basicDefs.h>

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP /* 'g++ -DXMLBLASTER_C_COMPILE_AS_CPP ...' allows to compile the lib as C++ code */
extern "C" {
#endif
#endif

#define MAX_ERRNO_LEN 256
#define XMLBLASTER_MAX_USAGE_LEN 2048 /* Change XmlBlasterAccessUnparsed.c accordingly */
#define MAX_REQUESTID_LEN 256
#define MAX_SECRETSESSIONID_LEN 256
#define MAX_SESSIONID_LEN 256

/* See org.xmlBlaster.util.enum.MethodName.java */
#define MAX_METHODNAME_LEN 20
#define XMLBLASTER_CONNECT "connect"
#define XMLBLASTER_DISCONNECT "disconnect"
#define XMLBLASTER_PING "ping"
#define XMLBLASTER_UPDATE "update"
#define XMLBLASTER_UPDATE_ONEWAY "updateOneway"
#define XMLBLASTER_PUBLISH "publish"
#define XMLBLASTER_PUBLISH_ONEWAY "publishOneway"
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
Dll_Export extern void xmlBlasterDefaultLogging(XMLBLASTER_LOG_LEVEL currLevel,
                              XMLBLASTER_LOG_LEVEL level,
                              const char *location, const char *fmt, ...);
Dll_Export extern XMLBLASTER_LOG_LEVEL parseLogLevel(const char *logLevelStr);
Dll_Export extern const char *getLogLevelStr(XMLBLASTER_LOG_LEVEL logLevel);
Dll_Export extern bool doLog(XMLBLASTER_LOG_LEVEL currLevel, XMLBLASTER_LOG_LEVEL level);

/**
 * Holds arbitrary raw data and its length
 */
typedef struct XmlBlasterBlobStruct {
   size_t dataLen;
   char *data;
} XmlBlasterBlob;

/** Holds a message
    All member pointers are allocated with malloc(), you need to free() them */
typedef struct MsgUnitStruct {
   const char *key;               /* XML formatted ASCII string of message key */
   size_t contentLen;       /* Number of bytes in content */
   const char *content;           /* Raw data (not 0 terminated) */
   const char *qos;               /* XML formatted ASCII string of Quality of Service */
   char *responseQos;       /* Used to transport the response QoS string back to caller */
} MsgUnit;
/* Note: We use the above 'const' to simplify assignment from C++ like 'msgUnit.key = std::string.c_str() */

/**
 * Holds an array of Messages
 */
typedef struct MsgUnitStructArr {
   /** Oneway updates are marked with true */
   bool isOneway;
   /** Authenticate callback messages, this sessionId is returned by xmlBlaster and was initially passed from the client on login */
   char secretSessionId[MAX_SESSIONID_LEN];
   size_t len;
   MsgUnit *msgUnitArr;
} MsgUnitArr;

/**
 * Holds an array of QoS XML strings returned by unSubscribe() and erase()
 */
typedef struct QosStructArr {
   size_t len;  /* Number of XML QoS strings */
   const char **qosArr;
} QosArr;

/**
 * Used to transport information back to callback functions
 */
typedef struct MsgRequestInfoStruct {
   const char *requestIdStr;
   const char *methodName;
   char responseType;
   XmlBlasterBlob blob;
} MsgRequestInfo;

#define XMLBLASTEREXCEPTION_ERRORCODE_LEN 56
#define XMLBLASTEREXCEPTION_MESSAGE_LEN 1024
typedef struct XmlBlasterException {
   bool remote; /* true if exception is from remote */
   char errorCode[XMLBLASTEREXCEPTION_ERRORCODE_LEN];
   char message[XMLBLASTEREXCEPTION_MESSAGE_LEN];
} XmlBlasterException;

Dll_Export extern void initializeXmlBlasterException(XmlBlasterException *xmlBlasterException);

Dll_Export extern XmlBlasterBlob *blobcpyAlloc(XmlBlasterBlob *blob, const char *data, size_t dataLen);
Dll_Export extern XmlBlasterBlob *freeXmlBlasterBlobContent(XmlBlasterBlob *blob);

Dll_Export extern const char *getXmlBlasterVersion(void);
Dll_Export extern char *getStackTrace(int maxNumOfLines);
Dll_Export extern void sleepMillis(long millis);
Dll_Export extern bool getAbsoluteTime(long relativeTimeFromNow, struct timespec *abstime);
Dll_Export extern void xmlBlasterFree(char *p);
Dll_Export extern void freeMsgUnitData(MsgUnit *msgUnit);
Dll_Export extern void freeMsgUnit(MsgUnit *msgUnit);
Dll_Export extern void freeMsgUnitArr(MsgUnitArr *msgUnitArr);
Dll_Export extern void freeMsgUnitArrInternal(MsgUnitArr *msgUnitArr);
Dll_Export extern void freeQosArr(QosArr *qosArr);
Dll_Export extern char *messageUnitToXml(MsgUnit *msg);
Dll_Export extern char *strFromBlobAlloc(const char *blob, const size_t len);
Dll_Export extern char *strcpyAlloc(const char *src);
Dll_Export extern char *strcpyRealloc(char **dest, const char *src);
Dll_Export extern char *strcatAlloc(char **dest, const char *src);
Dll_Export extern char *strncpy0(char * const to, const char * const from, const size_t maxLen);
Dll_Export extern void trim(char *s);
Dll_Export extern void trimStart(char *s);
Dll_Export extern void trimEnd(char *s);
Dll_Export extern char *blobDump(XmlBlasterBlob *blob);
Dll_Export extern char *toReadableDump(char *data, size_t len);
Dll_Export extern struct hostent * gethostbyname_re (const char *host,struct hostent *hostbuf,char **tmphstbuf,size_t *hstbuflen);


#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif

#endif /* XMLBLASTER_MSGUTIL_H */

