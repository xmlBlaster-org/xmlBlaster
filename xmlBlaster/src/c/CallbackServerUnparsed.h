/*----------------------------------------------------------------------------
Name:      CallbackServerUnparsed.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Include this header in your client code if you want to establish
           a client side callback server.
           We will call the update() method when messages arrive.
           The received message key and QoS is not parsed, we need another
           layer doing XML parsing with expat.
           This library is thread safe, multiple client callback servers may
           be established in parallel.
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      05/2003
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.html
-----------------------------------------------------------------------------*/
#ifndef XMLBLASTER_CallbackServerUnparsed_H
#define XMLBLASTER_CallbackServerUnparsed_H

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP /* 'g++ -DXMLBLASTER_C_COMPILE_AS_CPP ...' allows to compile the lib as C++ code */
extern "C" {
#endif
#endif

#include <util/msgUtil.h>
#include <util/Properties.h>

#define DEFAULT_CALLBACK_SERVER_PORT 7611

/* Forward declarations */
struct CallbackServerUnparsedStruct;
typedef struct CallbackServerUnparsedStruct CallbackServerUnparsed;

/* Define function pointers */

/**
 * Use this function directly after creation of the callback server
 * if you want to force to reuse the given socket for callbacks. 
 *
 * @param socketToUse Usually pass -1 so that we establish a callback server, else
 *               pass an opened socket (e.g. from XmlBlasterConnectionUnparsed->socketToXmlBlaster)
 * @return true on success
 */
typedef bool (* UseThisSocket)(CallbackServerUnparsed *cb, int socketToUse);

typedef int (* InitCallbackServer)(CallbackServerUnparsed *cb);

/**
 * @return true if the socket is open and waits for callback messages
 */
typedef bool (* IsListening)(CallbackServerUnparsed *cb);

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
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.update.html
 */
typedef bool (*UpdateFp)(MsgUnitArr *msg, void *userData, XmlBlasterException *xmlBlasterException);

typedef void (* ShutdownCallbackServerRaw)(CallbackServerUnparsed *cb);


#define MAX_RESPONSE_LISTENER_SIZE 100

typedef void (* ResponseFp)(void *userP, void /*SocketDataHolder*/ *socketDataHolder); /* using void * to avoid including the socket specific header */

typedef struct ResponseListenerStruct {
   void *userP;
   const char *requestId;
   ResponseFp responseEventFp;
} ResponseListener;

typedef bool ( * AddResponseListener)(CallbackServerUnparsed *cb, void *userP, const char *requestId, ResponseFp responseEventFp);
typedef ResponseListener * ( * RemoveResponseListener)(CallbackServerUnparsed *cb, const char *requestId);

typedef void  ( * CallbackServerUnparsedLogging)(XMLBLASTER_LOG_LEVEL currLevel, XMLBLASTER_LOG_LEVEL level, const char *location, const char *fmt, ...);

/**
 * This structure holds a complete callback server instance. 
 * The function pointers like <i>isListening()</i> allow you to
 * invoke methods on this structure.
 * <br />
 * The function pointer <i>update</i> holds the clients callback function
 * which is invoked when messages arrive. See the description of UpdateFp.
 */
struct CallbackServerUnparsedStruct {
   Properties *props;
   int listenSocket;
   int acceptSocket;
   char * hostCB;
   int portCB;
   bool reusingConnectionSocket; /* is false if we tunnel callback through the client connection socket */
   XMLBLASTER_LOG_LEVEL logLevel;
   CallbackServerUnparsedLogging log;
   /*
    * Is created by the client and used to validate callback messages in update. 
    * This is sent on connect in ConnectQos.
    * (Is different from the xmlBlaster secret session ID)
   char secretSessionId[MAX_SECRETSESSIONID_LEN];
   */
   InitCallbackServer runCallbackServer;
   IsListening isListening;
   ShutdownCallbackServerRaw shutdown; /* For internal use (multi thread) only */
   UpdateFp update;
   void *updateUserData; /* A optional pointer from the client code which is returned to the update() function call */
   UseThisSocket useThisSocket;
   ResponseListener responseListener[MAX_RESPONSE_LISTENER_SIZE];
   AddResponseListener addResponseListener;
   RemoveResponseListener removeResponseListener;
   bool isShutdown;
};

/**
 * Get a new instance of a callback server struct. 
 * This is usually the first call of a client, you need to call runCallbackServer()
 * on the returned pointer to establish a listener.
 * @param argc Number of command line arguments
 * @param argv The command line arguments
 * @param update The function pointer on your update() function which handles the received messages
 *               Please read the documentation of UpdateFp above.
 * @param userData An optional pointer from the client with client specific data
 *               which is delivered back with the update() function
 * @return NULL if allocation or bootstrapping failed. If not NULL you need to free() it when you are done
 * usually by calling freeXmlBlasterConnectionUnparsed().
 */
extern CallbackServerUnparsed *getCallbackServerUnparsed(int argc, char** argv,
                               UpdateFp update, void *userData);

/**
 * free() the CallbackServerUnparsed structure
 */
extern void freeCallbackServerUnparsed(CallbackServerUnparsed *callbackData);

/**
 * Help on configuration
 */
extern const char *callbackServerRawUsage();

/**
 * Function pointer for pthread,
 * the method to invoke on thread creation. 
 */
typedef void * (*cbFp)(void *);


#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif

#endif /* XMLBLASTER_CallbackServerUnparsed_H */

