/*----------------------------------------------------------------------------
Name:      msgUtil.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/
#ifndef XMLBLASTER_MSGUTIL_H
#define XMLBLASTER_MSGUTIL_H

#define XMLBLASTER_DEBUG true

#define bool int
#define true 1
#define false 0

// Holds a message
// All member pointers are allocated with malloc(), you need to free() them
typedef struct MsgUnitStruct {
   char *key;               // XML formatted ASCII string of message key
   size_t contentLen;
   char *content;  // Raw data
   char *qos;               // XML formatted ASCII string of Quality of Service
} MsgUnit;

typedef struct MsgUnitStructArr {
   size_t len;
   MsgUnit *msgUnitArr;
} MsgUnitArr;

#define XMLBLASTEREXCEPTION_ERRORCODE_LEN 56
#define XMLBLASTEREXCEPTION_MESSAGE_LEN 1024
#define XMLBLASTEREXCEPTION_MESSAGE_FMT "%1020s" 
typedef struct XmlBlasterExceptionStruct {
   bool remote; // true if exception is from remote
   char errorCode[XMLBLASTEREXCEPTION_ERRORCODE_LEN];
   char message[XMLBLASTEREXCEPTION_MESSAGE_LEN];
} XmlBlasterException;

// See org.xmlBlaster.util.enum.MethodName.java
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

extern void freeMsgUnitData(MsgUnit *msgUnit);
extern void freeMsgUnit(MsgUnit *msgUnit);
extern void freeMsgUnitArr(MsgUnitArr *msgUnitArr);
extern char *messageUnitToXml(MsgUnit *msg);
extern char *contentToString(char *content, MsgUnit *msg);
extern char *strFromBlobAlloc(const char *blob, const size_t len);
extern char *strcpyAlloc(const char *src);
extern int strcpy_alloc(char **into_string, const char *from_string);
extern char *strncpy0(char * const to, const char * const from, const size_t maxLen);
extern void trim(char *s);
extern char *toReadableDump(char *data, size_t len);


#endif // XMLBLASTER_MSGUTIL_H

