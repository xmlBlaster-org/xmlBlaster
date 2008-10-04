/*----------------------------------------------------------------------------
Name:      xmlBlaster/src/c/util/msgUtil.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/
#ifndef XMLBLASTER_MSGUTIL_H
#define XMLBLASTER_MSGUTIL_H

#include <util/helper.h>

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

/* See org.xmlBlaster.util.def.MethodName.java */
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

/**
 * Holds arbitrary raw data and its length, see helper.h
 */
typedef BlobHolder XmlBlasterBlob;

/**
 * Holds a message
 * All member pointers are allocated with malloc(), you need to free() them
 * Needs to be consistent with MsgUnitUnmanaged declaration in C# (for P/Invoke)
 */
typedef struct MsgUnit {
   const char *key;         /**< XML formatted ASCII string of the message topic */
   size_t contentLen;       /**< Number of bytes in content */
   const char *content;     /**< Raw data (not 0 terminated) */
   const char *qos;         /**< XML formatted ASCII string of Quality of Service */
   char *responseQos;       /**< Used to transport the response QoS string back to caller */
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
   uint32_t len;
   MsgUnit *msgUnitArr;
} MsgUnitArr;

/**
 * Holds an array of QoS XML strings returned by unSubscribe() and erase()
 */
typedef struct QosStructArr {
   uint32_t len;  /* Number of XML QoS strings */
   const char **qosArr;
} QosArr;

/**
 * Used to transport information back to callback functions
 */
typedef struct MsgRequestInfoStruct {
   void *xa; /* XmlBlasterAccessUnparsed * */
   char requestIdStr[MAX_REQUESTID_LEN];
   const char *methodName;
   char responseType;             /* XMLBLASTER_MSG_TYPE_ENUM */
   XmlBlasterBlob blob;
   XmlBlasterBlob responseBlob;
#ifdef XB_USE_PTHREADS
   pthread_mutex_t responseMutex; /* Needed for boss/worker model to block until an update arrives */
   bool responseMutexIsLocked;
   pthread_cond_t responseCond;
#endif
   bool rollback;
} MsgRequestInfo;

/* See helper.h */
#define XMLBLASTEREXCEPTION_ERRORCODE_LEN EXCEPTIONSTRUCT_ERRORCODE_LEN
#define XMLBLASTEREXCEPTION_MESSAGE_LEN   EXCEPTIONSTRUCT_MESSAGE_LEN
typedef ExceptionStruct                   XmlBlasterException;

/** /node/heron/client/joe/session/9 */
typedef struct SessionName {
   const char *nodeId;      /**< heron */
   const char *subjectId;   /**< joe */
   int sessionId;           /**< 9 */
} SessionName;
Dll_Export SessionName *createSessionName(const char* const absoluteName);
Dll_Export void freeSessionName(SessionName *sessionName);

Dll_Export extern void initializeXmlBlasterException(XmlBlasterException *xmlBlasterException);

Dll_Export extern const char *getXmlBlasterVersion(void);
Dll_Export extern void xmlBlasterFree(char *p);
Dll_Export extern void xmlBlasterFree0(char **p);
Dll_Export extern void freeMsgUnitData(MsgUnit *msgUnit);
Dll_Export extern void freeMsgUnit(MsgUnit *msgUnit);
Dll_Export extern void freeMsgUnitArr(MsgUnitArr *msgUnitArr);
Dll_Export extern void freeMsgUnitArrInternal(MsgUnitArr *msgUnitArr);
Dll_Export extern void freeQosArr(QosArr *qosArr);
Dll_Export extern char *messageUnitToXml(MsgUnit *msg);
Dll_Export extern char *messageUnitToXmlLimited(MsgUnit *msg, int maxContentDumpLen);
Dll_Export extern struct hostent * gethostbyname_re (const char *host,struct hostent *hostbuf,char **tmphstbuf,size_t *hstbuflen,char errP[MAX_ERRNO_LEN]);

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
} /* extern "C" */
#endif
#endif

#endif /* XMLBLASTER_MSGUTIL_H */

