/*----------------------------------------------------------------------------
Name:      xmlBlasterSocket.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   SOCKET internal header (not included directly by clients)
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/
#ifndef XMLBLASTER_SOCKET_H
#define XMLBLASTER_SOCKET_H

#include <stdlib.h>
#include <util/basicDefs.h> /* Needed only to define _WINDOWS */

#ifdef _WINDOWS
                             /* #if _MSC_VER > 1300 (which is VC7) but also for 1400 (which is VC8 2005) */
#include <Winsock2.h>        /* WS2_32.DLL: The newer lib which extends winsock.h, new: Ws2tcpip.h */

/*#  include <winsock.h> */  /* WSOCK32.DLL: The old lib, if you activate replace SD_BOTH with 2 in XmlBlasterConnectionUnparsed.c  */
/* From a mailing list:
   #ifndef INCL_WINSOCK_API_TYPEDEFS
   #define INCL_WINSOCK_API_TYPEDEFS 1
   #endif

   // then include:
   #include <winsock2.h>

   also any header you include ( mainly a concern in your stdafx.h file ) that may suck in the winsock.h file you should do a:

   #define _WINSOCKAPI_
*/
#  define ssize_t signed int
#else
#  if defined(__APPLE__) || defined(__FreeBSD__) || defined(__NetBSD__) || defined(__hpux__)
#    include <sys/types.h>   /* Needed for __FreeBSD__ */
#    include <netinet/in.h>
#  endif
#  include <sys/socket.h>
#  include <netdb.h>
#  include <arpa/inet.h>   /* inet_addr() */
#endif
#include <util/msgUtil.h>

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP /* 'g++ -DXMLBLASTER_C_COMPILE_AS_CPP ...' allows to compile the lib as C++ code */
extern "C" {
#endif
#endif

#define  MAX_MSG_LEN 1000000000

/**
 * Settings for MSG_FLAG_POS_TYPE
 */
typedef enum XMLBLASTER_MSG_TYPE_ENUM {
   MSG_TYPE_INVOKE = 73,
   MSG_TYPE_RESPONSE = 82,
   MSG_TYPE_EXCEPTION = 69
} XMLBLASTER_MSG_TYPE;

/**
 * Helper struct to hold all necessary informations
 */
typedef struct SocketDataHolder {
   size_t msgLen;
   bool checksum;
   bool compressed;
   char type;  /**< XMLBLASTER_MSG_TYPE */
   char version;
   char requestId[MAX_REQUESTID_LEN];
   char methodName[MAX_METHODNAME_LEN];
   char secretSessionId[MAX_SESSIONID_LEN];
   size_t dataLenUncompressed;
   XmlBlasterBlob blob; /**< blob.data is allocated with malloc, you need to free() it yourself, is compressed if marked as such */
} SocketDataHolder;

#define MSG_LEN_FIELD_LEN 10
#define MAX_PACKET_SIZE 10*1024
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

#define XMLBLASTER_SOCKET_VERSION 49


extern void closeSocket(int fd);
extern ssize_t writen(const int fd, const char *ptr, const size_t nbytes);
extern ssize_t readn(const int fd, char *ptr, const size_t nbytes, XmlBlasterNumReadFunc fpNumRead, void *userP);

/**
 * Creates a raw blob to push over a socket as described in the protocol.socket requirement. 
 * @param msgType invoke, response or exception
 * @param requestId The unique request ID for each invocation
 * @param methodName The method name like "PUBLISH"
 * @param secretSessionId The authentication string
 * @param data is returned
 * @param dataLen is returned
 * @param debug Pass true for debugging output to stdout
 * @param rawMsgLen
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @return The raw message, the caller needs to free() it.
 */
extern char *encodeSocketMessage(
              enum XMLBLASTER_MSG_TYPE_ENUM msgType,
              const char * const requestId,    /**< The unique request ID for each invocation */
              const char * const methodName,   
              const char * const secretSessionId,
              const char *data,
              size_t dataLen,
              bool debug,
              size_t *rawMsgLen);
Dll_Export BlobHolder encodeMsgUnit(MsgUnit *msgUnit, bool debug);  /* export for C++ embedding */
Dll_Export BlobHolder encodeMsgUnitArr(MsgUnitArr *msgUnitArr, bool debug);
Dll_Export bool parseSocketData(int xmlBlasterSocket, const XmlBlasterReadFromSocketFuncHolder *fpHolder, SocketDataHolder *socketDataHolder, XmlBlasterException *exception, bool *stopP, bool udp, bool debug);
Dll_Export void convertToXmlBlasterException(const XmlBlasterBlob *blob, XmlBlasterException *exception, bool debug);
Dll_Export void encodeXmlBlasterException(XmlBlasterBlob *blob, const XmlBlasterException *exception, bool debug);
Dll_Export MsgUnitArr *parseMsgUnitArr(size_t dataLen, char *data);
Dll_Export QosArr *parseQosArr(size_t dataLen, char *data);

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
} /* extern "C" */
#endif
#endif

#endif /* XMLBLASTER_SOCKET_H */

