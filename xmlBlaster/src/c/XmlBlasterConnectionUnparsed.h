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
#include <msgUtil.h>

struct XmlBlasterConnectionUnparsedStruct;
typedef struct XmlBlasterConnectionUnparsedStruct XmlBlasterConnectionUnparsed;

/* Declare function pointers to use in struct to simulate object oriented access */
typedef char *( * XmlBlasterConnectionUnparsedConnect)(XmlBlasterConnectionUnparsed *xb, const char * const qos, XmlBlasterException *exception);
typedef bool  ( * XmlBlasterConnectionUnparsedDisconnect)(XmlBlasterConnectionUnparsed *xb, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterConnectionUnparsedPublish)(XmlBlasterConnectionUnparsed *xb, MsgUnit *msgUnit, XmlBlasterException *exception);
typedef char *( * XmlBlasterConnectionUnparsedSubscribe)(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterConnectionUnparsedUnSubscribe)(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterConnectionUnparsedErase)(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef MsgUnitArr *( * XmlBlasterConnectionUnparsedGet)(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterConnectionUnparsedPing)(XmlBlasterConnectionUnparsed *xb, const char * const qos);
typedef bool  ( * XmlBlasterConnectionUnparsedIsConnected)(XmlBlasterConnectionUnparsed *xb);
typedef void  ( * XmlBlasterConnectionUnparsedShutdown)(XmlBlasterConnectionUnparsed *xb);
typedef MsgRequestInfo *( * XmlBlasterConnectionUnparsedPreSendEvent)(void *userP, MsgRequestInfo *msgRequestInfo, XmlBlasterException *exception);
typedef MsgRequestInfo *( * XmlBlasterConnectionUnparsedPostSendEvent)(void *userP, MsgRequestInfo *msgRequestInfo, XmlBlasterException *exception);

/**
 * All client access to xmlBlaster goes over this struct and its function pointers. 
 */
struct XmlBlasterConnectionUnparsedStruct {
   int argc;
   char **argv;
   int socketToXmlBlaster;
   long requestId;
   char secretSessionId[MAX_SECRETSESSIONID_LEN];
   bool isInitialized;
   XmlBlasterConnectionUnparsedConnect connect;   
   XmlBlasterConnectionUnparsedDisconnect disconnect;   
   XmlBlasterConnectionUnparsedPublish publish;
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
   bool debug;
};


/**
 * Get an instance of this to get xmlBlaster access. 
 * NOTE: Every call creates a new and independent client access instance to xmlBlaster
 */
extern XmlBlasterConnectionUnparsed *getXmlBlasterConnectionUnparsed(int argc, char** argv);

/**
 * Free your instance after accessing xmlBlaster. 
 */
extern void freeXmlBlasterConnectionUnparsed(XmlBlasterConnectionUnparsed *xmlBlasterAccess);

/**
 * Help usage
 */
extern const char *xmlBlasterConnectionUnparsedUsage();

