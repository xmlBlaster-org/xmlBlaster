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

#include <util/msgUtil.h>
#include <util/Properties.h>

struct XmlBlasterConnectionUnparsedStruct;
typedef struct XmlBlasterConnectionUnparsedStruct XmlBlasterConnectionUnparsed;

/* Declare function pointers to use in struct to simulate object oriented access */
typedef bool  ( * XmlBlasterConnectionUnparsedInitConnection)(XmlBlasterConnectionUnparsed *xb, XmlBlasterException *exception);
typedef char *( * XmlBlasterConnectionUnparsedConnect)(XmlBlasterConnectionUnparsed *xb, const char * const qos, XmlBlasterException *exception);
typedef bool  ( * XmlBlasterConnectionUnparsedDisconnect)(XmlBlasterConnectionUnparsed *xb, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterConnectionUnparsedPublish)(XmlBlasterConnectionUnparsed *xb, MsgUnit *msgUnit, XmlBlasterException *exception);
typedef QosArr *( * XmlBlasterConnectionUnparsedPublishArr)(XmlBlasterConnectionUnparsed *xb, MsgUnitArr *msgUnitArr, XmlBlasterException *exception);
typedef void  ( * XmlBlasterConnectionUnparsedPublishOneway)(XmlBlasterConnectionUnparsed *xb, MsgUnitArr *msgUnitArr, XmlBlasterException *exception);
typedef char *( * XmlBlasterConnectionUnparsedSubscribe)(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef QosArr *( * XmlBlasterConnectionUnparsedUnSubscribe)(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef QosArr *( * XmlBlasterConnectionUnparsedErase)(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef MsgUnitArr *( * XmlBlasterConnectionUnparsedGet)(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterConnectionUnparsedPing)(XmlBlasterConnectionUnparsed *xb, const char * const qos);
typedef bool  ( * XmlBlasterConnectionUnparsedIsConnected)(XmlBlasterConnectionUnparsed *xb);
typedef void  ( * XmlBlasterConnectionUnparsedShutdown)(XmlBlasterConnectionUnparsed *xb);
typedef MsgRequestInfo *( * XmlBlasterConnectionUnparsedPreSendEvent)(void *userP, MsgRequestInfo *msgRequestInfo, XmlBlasterException *exception);
typedef MsgRequestInfo *( * XmlBlasterConnectionUnparsedPostSendEvent)(void *userP, MsgRequestInfo *msgRequestInfo, XmlBlasterException *exception);
typedef void  ( * XmlBlasterConnectionUnparsedLogging)(XMLBLASTER_LOG_LEVEL currLevel, XMLBLASTER_LOG_LEVEL level, const char *location, const char *fmt, ...);

/**
 * All client access to xmlBlaster goes over this struct and its function pointers. 
 */
struct Dll_Export XmlBlasterConnectionUnparsedStruct {
   int argc;
   const char * const*argv;
   Properties *props;
   int socketToXmlBlaster;
   long requestId;
   char secretSessionId[MAX_SECRETSESSIONID_LEN];
   bool isInitialized;
   XmlBlasterConnectionUnparsedInitConnection initConnection; /* Used internally or by multi threaded embedding only as this is called by connect() automatically */
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
   XmlBlasterConnectionUnparsedPreSendEvent preSendEvent; /* If a callback function pointer is registered it will be called just before sending a message */
   void *preSendEvent_userP;
   XmlBlasterConnectionUnparsedPostSendEvent postSendEvent; /* If a callback function pointer is registered it will be called just after sending a message */
   void *postSendEvent_userP;
   XMLBLASTER_LOG_LEVEL logLevel;
   XmlBlasterConnectionUnparsedLogging log;
};


/**
 * Get an instance of this to get xmlBlaster access. 
 * NOTE: Every call creates a new and independent client access instance to xmlBlaster
 */
Dll_Export extern XmlBlasterConnectionUnparsed *getXmlBlasterConnectionUnparsed(int argc, const char* const* argv);

/**
 * Free your instance after accessing xmlBlaster. 
 */
Dll_Export extern void freeXmlBlasterConnectionUnparsed(XmlBlasterConnectionUnparsed *xmlBlasterAccess);

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

