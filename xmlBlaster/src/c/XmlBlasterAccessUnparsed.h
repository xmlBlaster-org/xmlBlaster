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
#ifndef _XmlBlasterAccessUnparsed_H
#define _XmlBlasterAccessUnparsed_H

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP /* 'g++ -DXMLBLASTER_C_COMPILE_AS_CPP ...' allows to compile the lib as C++ code */
extern "C" {
#endif
#endif

#include <util/msgUtil.h>
#include <util/Properties.h>
#include <XmlBlasterConnectionUnparsed.h>
#include <CallbackServerUnparsed.h>
#include <pthreads/pthread.h> /* For Windows and WinCE (downloaded from http://sources.redhat.com/pthreads-win32) */

/*
 NOTE: The struct name and the later typedef name are identical, we need this
       to allow in C++ SocketDriver.h a forward declaration of the XmlBlasterAccessUnparsed*
       pointer (to avoid inclusion of this complete header)
*/
struct XmlBlasterAccessUnparsed;

/**
 * Here we asynchronous receive the callback from xmlBlaster
 * msg = char *key, char *content, int contentLen, char *qos
 *
 * NOTE: After this call the memory of msgUnitArr is freed immediately by callbackServer.c
 *       So you need to take a copy of all message members if needed out of the scope of this function.
 *
 * @param msgUnitArr The messages from the server, use mgsUnit->responseQos to transport the return value
 * @param userData An optional pointer from the client with client specific data which is delivered back
 * @param xmlBlasterException This points on a valid struct, so you only need to fill errorCode with strcpy
 *        and the returned pointer is ignored and the exception is thrown to xmlBlaster.
 * @return Return true if everything is OK
 *         Return false if you want to throw an exception, please fill xmlBlasterException in such a case.
 *         If false and *xmlBlasterException.errorCode==0 we don't send a return message (useful for update dispatcher thread to do it later)
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.update.html
 */
typedef bool (*UpdateFp)(MsgUnitArr *msg, void *userData, XmlBlasterException *xmlBlasterException);

/* Declare function pointers to use in struct to simulate object oriented access */
typedef char *( * XmlBlasterAccessUnparsedConnect)(struct XmlBlasterAccessUnparsed *xb, const char * const qos, UpdateFp update, XmlBlasterException *exception);
typedef bool  ( * XmlBlasterAccessUnparsedInitialize)(struct XmlBlasterAccessUnparsed *xa, UpdateFp update, XmlBlasterException *exception);
typedef bool  ( * XmlBlasterAccessUnparsedDisconnect)(struct XmlBlasterAccessUnparsed *xb, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterAccessUnparsedPublish)(struct XmlBlasterAccessUnparsed *xb, MsgUnit *msgUnit, XmlBlasterException *exception);
typedef char *( * XmlBlasterAccessUnparsedSubscribe)(struct XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterAccessUnparsedUnSubscribe)(struct XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterAccessUnparsedErase)(struct XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef MsgUnitArr *( * XmlBlasterAccessUnparsedGet)(struct XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterAccessUnparsedPing)(struct XmlBlasterAccessUnparsed *xb, const char * const qos);
typedef bool  ( * XmlBlasterAccessUnparsedIsConnected)(struct XmlBlasterAccessUnparsed *xb);
typedef void  ( * XmlBlasterLogging)(XMLBLASTER_LOG_LEVEL currLevel, XMLBLASTER_LOG_LEVEL level, const char *location, const char *fmt, ...);

/**
 * All client access to xmlBlaster goes over this struct and its function pointers. 
 * All function pointers return exception.errorCode="user.notConnected" if connection
 * to xmlBlaster is lost.
 */
typedef struct Dll_Export XmlBlasterAccessUnparsed {
  /* public: */
   int argc;
   char **argv;
   Properties *props;
   void *userData; /* A client can use this pointer to point to any client specific information */
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
   XMLBLASTER_LOG_LEVEL logLevel;
   XmlBlasterLogging log;
  /* private: */
   XmlBlasterConnectionUnparsed *connectionP;
   CallbackServerUnparsed *callbackP;
   bool isInitialized;
   UpdateFp clientsUpdateFp;
   bool callbackMultiThreaded;    /* Shall update messages be transported to the client code in a thread per request? */
   long responseTimeout;
   XmlBlasterBlob responseBlob;
   char responseType;             /* XMLBLASTER_MSG_TYPE_ENUM */
   pthread_t callbackThreadId;
   pthread_mutex_t responseMutex; /* Needed for boss/worker model to block until an update arrives */
   bool responseMutexIsLocked;
   pthread_cond_t responseCond;
} XmlBlasterAccessUnparsed;

/**
 * Get an instance of this to get xmlBlaster access. 
 * NOTE: Every call creates a new and independent client access instance to xmlBlaster
 */
Dll_Export extern XmlBlasterAccessUnparsed *getXmlBlasterAccessUnparsed(int argc, char** argv);

/**
 * Free your instance after accessing xmlBlaster. 
 */
Dll_Export extern void freeXmlBlasterAccessUnparsed(XmlBlasterAccessUnparsed *xmlBlasterAccess);

/**
 * Help usage
 */
Dll_Export extern const char *xmlBlasterAccessUnparsedUsage(char *usage);

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif

#endif /* _XmlBlasterAccessUnparsed_H */

