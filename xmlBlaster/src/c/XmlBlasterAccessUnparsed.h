/*----------------------------------------------------------------------------
Name:      XmlBlasterAccessUnparsed.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This combines the sync xmlBlaster access (XmlBlasterConnectionUnparsed.h)
           with the pure callback implementation (CallbackServerUnparsed.h)
           and adds threading to allow simultaneous access and callbacks.

           Include this header in your client code.

           The returned strings are not parsed, we need another layer
           doing XML parsing with expat.
           This library is thread safe, multiple client connections may
           be established in parallel.
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      05/2003
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.html
-----------------------------------------------------------------------------*/
#include <msgUtil.h>
#include <XmlBlasterConnectionUnparsed.h>
#include <CallbackServerUnparsed.h>
#include <pthread.h> /* For Windows and WinCE download them from http://sources.redhat.com/pthreads-win32 */

struct XmlBlasterAccessUnparsedStruct;
typedef struct XmlBlasterAccessUnparsedStruct XmlBlasterAccessUnparsed;

/* Declare function pointers to use in struct to simulate object oriented access */
typedef char *( * XmlBlasterAccessUnparsedConnect)(XmlBlasterAccessUnparsed *xb, const char * const qos, UpdateFp update, XmlBlasterException *exception);
typedef bool  ( * XmlBlasterAccessUnparsedInitialize)(XmlBlasterAccessUnparsed *xa, UpdateFp update);
typedef bool  ( * XmlBlasterAccessUnparsedDisconnect)(XmlBlasterAccessUnparsed *xb, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterAccessUnparsedPublish)(XmlBlasterAccessUnparsed *xb, MsgUnit *msgUnit, XmlBlasterException *exception);
typedef char *( * XmlBlasterAccessUnparsedSubscribe)(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterAccessUnparsedUnSubscribe)(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterAccessUnparsedErase)(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef MsgUnitArr *( * XmlBlasterAccessUnparsedGet)(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterAccessUnparsedPing)(XmlBlasterAccessUnparsed *xb, const char * const qos);
typedef bool  ( * XmlBlasterAccessUnparsedIsConnected)(XmlBlasterAccessUnparsed *xb);

/**
 * All client access to xmlBlaster goes over this struct and its function pointers. 
 */
struct XmlBlasterAccessUnparsedStruct {
   int argc;
   char **argv;
   XmlBlasterConnectionUnparsed *connectionP;
   CallbackServerUnparsed *callbackP;
   bool isInitialized;
   XmlBlasterAccessUnparsedConnect connect;   
   XmlBlasterAccessUnparsedInitialize initialize;
   XmlBlasterAccessUnparsedDisconnect disconnect;   
   XmlBlasterAccessUnparsedPublish publish;
   XmlBlasterAccessUnparsedSubscribe subscribe;
   XmlBlasterAccessUnparsedUnSubscribe unSubscribe;
   XmlBlasterAccessUnparsedErase erase;
   XmlBlasterAccessUnparsedGet get;
   XmlBlasterAccessUnparsedPing ping;
   XmlBlasterAccessUnparsedIsConnected isConnected;
   bool debug;
   XmlBlasterBlob responseBlob;
   pthread_mutex_t responseMutex; /* Needed for boss/worker model to block until an update arrives */
   pthread_cond_t responseCond;
};


/**
 * Get an instance of this to get xmlBlaster access. 
 * NOTE: Every call creates a new and independent client access instance to xmlBlaster
 */
extern XmlBlasterAccessUnparsed *getXmlBlasterAccessUnparsed(int argc, char** argv);

/**
 * Free your instance after accessing xmlBlaster. 
 */
extern void freeXmlBlasterAccessUnparsed(XmlBlasterAccessUnparsed *xmlBlasterAccess);

/**
 * Help usage
 */
extern const char *xmlBlasterAccessUnparsedUsage();

