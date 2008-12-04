/*----------------------------------------------------------------------------
Name:      XmlBlasterConnectionUnparsed.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Include this header in your client code
           The returned strings are not parsed, we need another layer
           doing XML parsing with expat.
           This library is thread safe, multiple client connections may
           be established in parallel.
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      05/2003
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.html
-----------------------------------------------------------------------------*/
#ifndef XMLBLASTER_XmlBlasterConnectionUnparsed_H
#define XMLBLASTER_XmlBlasterConnectionUnparsed_H

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP /* 'g++ -DXMLBLASTER_C_COMPILE_AS_CPP ...' allows to compile the lib as C++ code */
extern "C" {
#endif
#endif
#ifdef __IPhoneOS__
#include <CoreFoundation/CFSocket.h>
#include <CoreFoundation/CFStream.h>
#include <CoreFoundation/CFString.h>
#include <CFNetwork/CFHost.h>
#include <CFNetwork/CFSocketStream.h>

#endif
#include <util/msgUtil.h>
#include <util/queue/QueueInterface.h>
#include <util/Properties.h>

struct XmlBlasterZlibWriteBuffers;
struct XmlBlasterConnectionUnparsedStruct;
typedef struct XmlBlasterConnectionUnparsedStruct XmlBlasterConnectionUnparsed;

/* Declare function pointers to use in struct to simulate object oriented access */
typedef bool  ( * XmlBlasterConnectionUnparsedInitConnection)(XmlBlasterConnectionUnparsed *xb, XmlBlasterException *exception);
typedef bool  ( * XmlBlasterConnectionUnparsedInitQueue)(XmlBlasterConnectionUnparsed *xb, QueueProperties *queueProperties, XmlBlasterException *exception);
typedef char *( * XmlBlasterConnectionUnparsedConnect)(XmlBlasterConnectionUnparsed *xb, const char * const qos, XmlBlasterException *exception);
typedef bool  ( * XmlBlasterConnectionUnparsedDisconnect)(XmlBlasterConnectionUnparsed *xb, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterConnectionUnparsedPublish)(XmlBlasterConnectionUnparsed *xb, MsgUnit *msgUnit, XmlBlasterException *exception);
typedef QosArr *( * XmlBlasterConnectionUnparsedPublishArr)(XmlBlasterConnectionUnparsed *xb, MsgUnitArr *msgUnitArr, XmlBlasterException *exception);
typedef void  ( * XmlBlasterConnectionUnparsedPublishOneway)(XmlBlasterConnectionUnparsed *xb, MsgUnitArr *msgUnitArr, XmlBlasterException *exception);
typedef char *( * XmlBlasterConnectionUnparsedSubscribe)(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef QosArr *( * XmlBlasterConnectionUnparsedUnSubscribe)(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef QosArr *( * XmlBlasterConnectionUnparsedErase)(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef MsgUnitArr *( * XmlBlasterConnectionUnparsedGet)(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterConnectionUnparsedPing)(XmlBlasterConnectionUnparsed *xb, const char * const qos, XmlBlasterException *exception);
typedef bool  ( * XmlBlasterConnectionUnparsedIsConnected)(XmlBlasterConnectionUnparsed *xb);
typedef void  ( * XmlBlasterConnectionUnparsedShutdown)(XmlBlasterConnectionUnparsed *xb);
typedef MsgRequestInfo *( * XmlBlasterConnectionUnparsedPreSendEvent)(MsgRequestInfo *msgRequestInfo, XmlBlasterException *exception);
typedef MsgRequestInfo *( * XmlBlasterConnectionUnparsedPostSendEvent)(MsgRequestInfo *msgRequestInfo, XmlBlasterException *exception);

/**
 * All synchrnous client access to xmlBlaster goes over this struct and its function pointers.
 */
struct Dll_Export XmlBlasterConnectionUnparsedStruct {
   int argc;
   const char * const*argv;
   Properties *props;
#ifdef __IPhoneOS__
	CFSocketRef cfSocketRef;
	CFDataRef socketAddr;
	CFReadStreamRef readStream;
	CFWriteStreamRef writeStream;
#endif
   int socketToXmlBlaster;
	int socketToXmlBlasterUdp;
   char secretSessionId[MAX_SECRETSESSIONID_LEN];
   bool isInitialized;
   XmlBlasterConnectionUnparsedInitConnection initConnection; /**< Used internally or by multi threaded embedding only as this is called by connect() automatically */
   XmlBlasterConnectionUnparsedInitQueue initQueue; /**< Call to initialize persistent queue support on lost connection */
   XmlBlasterConnectionUnparsedConnect connect;
   XmlBlasterConnectionUnparsedDisconnect disconnect;
   XmlBlasterConnectionUnparsedPublish publish;
   XmlBlasterConnectionUnparsedPublishArr publishArr;
   XmlBlasterConnectionUnparsedPublishOneway publishOneway;
   XmlBlasterConnectionUnparsedSubscribe subscribe;
   XmlBlasterConnectionUnparsedUnSubscribe unSubscribe;
   XmlBlasterConnectionUnparsedErase erase;
   XmlBlasterConnectionUnparsedGet get;
   XmlBlasterConnectionUnparsedPing ping;
   XmlBlasterConnectionUnparsedIsConnected isConnected;
   XmlBlasterConnectionUnparsedShutdown shutdown;
   XmlBlasterConnectionUnparsedPreSendEvent preSendEvent; /**< If a callback function pointer is registered it will be called just before sending a message */
   void *preSendEvent_userP;
   XmlBlasterConnectionUnparsedPostSendEvent postSendEvent; /**< If a callback function pointer is registered it will be called just after sending a message */
   void *postSendEvent_userP;
   I_Queue *queueP;
   XMLBLASTER_LOG_LEVEL logLevel;
   XmlBlasterLogging log;
   void *logUserP;               /**< For outside users to pass a user object back to the logging implementation */
   bool useUdpForOneway;         /**< For publishOneway() AND to start callback UDP server (for updateOneway()) */

   /* Socket write access: */
   XmlBlasterWriteToSocketFuncHolder writeToSocket;  /**< The function pointer to write n bytes of plain or compressed data to the socket
                                                  Is initialized in initConnection(), outside users may choose to initialize it to some other function pointer */
   struct XmlBlasterZlibWriteBuffers *zlibWriteBuf; /**< Is null if no "zlib:stream" compression is switched on */

   /* Socket read access: */
   XmlBlasterReadFromSocketFuncHolder readFromSocket; /**< Holding function pointer for compressed/uncompressed socket reads */
   struct XmlBlasterZlibReadBuffers *zlibReadBuf; /**< Is null if no "zlib:stream" compression is switched on */
};
#ifdef __IPhoneOS__

	struct Dll_Export XmlBlasterConnectionUnparsedStruct* globalIPhoneXb;
#endif
	/**
 * Get an instance of this to get xmlBlaster access.
 *
 * Every call creates a new and independent client access instance to xmlBlaster
 */
Dll_Export extern XmlBlasterConnectionUnparsed *getXmlBlasterConnectionUnparsed(int argc, const char* const* argv);

/**
 * Free your instance after accessing xmlBlaster.
 * After freeing  *xmlBlasterAccess is set to null
 * Call example: freeXmlBlasterConnectionUnparsed(&xa->connectionP);
 */
Dll_Export extern void freeXmlBlasterConnectionUnparsed(XmlBlasterConnectionUnparsed **xmlBlasterAccess);

/**
 * Help usage
 */
Dll_Export extern const char *xmlBlasterConnectionUnparsedUsage();


#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif

#endif /* XMLBLASTER_XmlBlasterConnectionUnparsed_H */

