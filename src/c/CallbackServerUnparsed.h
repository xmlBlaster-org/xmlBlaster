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
#include "socket/xmlBlasterSocket.h"

#define DEFAULT_CALLBACK_SERVER_PORT 7611

/* Forward declarations */
struct SocketDataHolder;
struct CallbackServerUnparsedStruct;
typedef struct CallbackServerUnparsedStruct CallbackServerUnparsed;

/* Define function pointers */

/**
 * Use this function directly after creation of the callback server
 * if you want to force to reuse the given socket for callbacks.
 *
 * @param socketToUse Usually pass -1 so that we establish a callback server, else
 *               pass an opened socket (e.g. from XmlBlasterConnectionUnparsed->socketToXmlBlaster)
 * @param socketToUseUdp Usually pass -1 so that we establish a callback server, else
 *               pass an opened socket (e.g. from XmlBlasterConnectionUnparsed->socketToXmlBlaster)
 * @return true on success
 */
typedef bool (* UseThisSocket)(CallbackServerUnparsed *cb, int socketToUse, int socketToUseUdp);

typedef int (* InitCallbackServer)(CallbackServerUnparsed *cb);

/**
 * @return true if the socket is open and waits for callback messages
 */
typedef bool (* IsListening)(CallbackServerUnparsed *cb);

/**
 * Only in pthread_detach() mode
 */
typedef bool (* WaitOnCallbackThreadTermination)(CallbackServerUnparsed *cb, long millis);
typedef bool (* WaitOnCallbackThreadAlive)(CallbackServerUnparsed *cb, long millis);

/**
 * Here we asynchronously receive the callback from xmlBlaster.
 *
 * NOTE: After this call the memory of #MsgUnitArr is freed immediately by #CallbackServerUnparsed
 *       So you need to take a copy of all message members if needed out of the scope of this function.
 *
 * @param msgUnitArr The messages from the server, use MgsUnit#responseQos to transport the return value
 * @param userData An optional pointer from the client with client specific data which is delivered back
 * @param xmlBlasterException This points on a valid #XmlBlasterException struct, so you only need to fill errorCode with strcpy()
 *        and the returned pointer is ignored and the exception is thrown to xmlBlaster.
 * @param socketDataHolder #SocketDataHolder containing socket specific informations, please handle as readonly
 * @return Return true if everything is OK
 *         Return false if you want to throw an exception, please fill xmlBlasterException in such a case.
 *         If false and *xmlBlasterException.errorCode==0 we don't send a return message (useful for update dispatcher thread to do it later)
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.update.html
 */
typedef void (*UpdateCbFp)(MsgUnitArr *msg, void *userData, XmlBlasterException *xmlBlasterException, void/*SocketDataHolder*/ *socketDataHolder);

typedef void (* ShutdownCallbackServerRaw)(CallbackServerUnparsed *cb);

typedef void ( * CallbackServerUnparsedSendResponse)(CallbackServerUnparsed *cb, void/*SocketDataHolder*/ *socketDataHolder, MsgUnitArr *msgUnitArr);
typedef void ( * CallbackServerUnparsedSendXmlBlasterException)(CallbackServerUnparsed *cb, void/*SocketDataHolder*/ *socketDataHolder, XmlBlasterException *exception);
typedef void ( * CallbackServerUnparsedDoRespond)(bool success, CallbackServerUnparsed *cb, void/*SocketDataHolder*/ *socketDataHolder, MsgUnitArr *msgUnitArrP, XmlBlasterException *exception);

#define MAX_RESPONSE_LISTENER_SIZE 100

typedef void (* ResponseFp)(MsgRequestInfo *msgRequestInfoP, void /*SocketDataHolder*/ *socketDataHolder); /* using void * to avoid including the socket specific header */

typedef struct ResponseListenerStruct {
   MsgRequestInfo *msgRequestInfoP;
   /*void *userP;
   const char *requestId;*/
   ResponseFp responseEventFp;
} ResponseListener;

typedef bool ( * AddResponseListener)(CallbackServerUnparsed *cb, MsgRequestInfo *msgRequestInfoP, ResponseFp responseEventFp);
typedef ResponseListener * ( * RemoveResponseListener)(CallbackServerUnparsed *cb, const char *requestId);

typedef void  ( * CallbackServerUnparsedLogging)(void *logUserP, XMLBLASTER_LOG_LEVEL currLevel, XMLBLASTER_LOG_LEVEL level, const char *location, const char *fmt, ...);

/**
 * This structure holds a complete callback server instance.
 *
 * The function pointers like #isListening() allow you to
 * invoke methods on this structure.
 * <p>
 * The function pointer #updateCb() holds the clients callback function
 * which is invoked when messages arrive. See the description of #UpdateCbFp.
 * </p>
 */
struct CallbackServerUnparsedStruct {
   Properties *props;
   bool stopListenLoop;
   int listenSocket;
   int acceptSocket;
   int socketUdp;
   char * hostCB;
   int portCB;
   bool reusingConnectionSocket; /**< is false if we tunnel callback through the client connection socket */
   XMLBLASTER_LOG_LEVEL logLevel;
   CallbackServerUnparsedLogging log;
   void *logUserP;               /**< For outside users to pass a user object back to their logging implementation */
   WaitOnCallbackThreadAlive waitOnCallbackThreadAlive;
   WaitOnCallbackThreadTermination waitOnCallbackThreadTermination;
   /*
    * Is created by the client and used to validate callback messages in update.
    * This is sent on connect in ConnectQos.
    * (Is different from the xmlBlaster secret session ID)
   char secretSessionId[MAX_SECRETSESSIONID_LEN];
   */
   InitCallbackServer runCallbackServer;
   IsListening isListening;
   ShutdownCallbackServerRaw shutdown; /**< For internal use (multi thread) only */
   UpdateCbFp updateCb;
   void *updateCbUserData; /**< A optional pointer from the client code which is returned to the update() function call */
   UseThisSocket useThisSocket;
   ResponseListener responseListener[MAX_RESPONSE_LISTENER_SIZE];
   AddResponseListener addResponseListener;
   RemoveResponseListener removeResponseListener;
   bool _threadIsAliveOnce; /**< Mark if thread is running (is never reset if once on true!) */
   bool threadIsAlive; /**< Lifecycle of thread */
   CallbackServerUnparsedSendResponse sendResponse;
   CallbackServerUnparsedSendXmlBlasterException sendXmlBlasterException;
   CallbackServerUnparsedDoRespond sendResponseOrException;
   pthread_mutex_t listenMutex;

   /* Socket write access: */
   XmlBlasterWriteToSocketFuncHolder writeToSocket;  /**< The function pointer to write n bytes of plain or compressed data to the socket
                                                  Is initialized in initConnection(), outside users may choose to initialize it to some other function pointer */

   /* Socket read access: */
   XmlBlasterReadFromSocketFuncHolder readFromSocket; /**< Holding function pointer for compressed/uncompressed socket reads */
};

/**
 * Auxiliary struct for passing parameters to listening threads
 */
typedef struct ListenLoopArgsStruct {
   CallbackServerUnparsed* cb;
   bool udp;
} ListenLoopArgs;

/**
 * Get a new instance of a callback server struct.
 * This is usually the first call of a client, you need to call #runCallbackServer()
 * on the returned pointer to establish a listener.
 * @param argc Number of command line arguments
 * @param argv The command line arguments
 * @param updateCb The function pointer on your update() function which handles the received messages
 *               Please read the documentation of #UpdateCbFp above.
 * @param userData An optional pointer from the client with client specific data
 *               which is delivered back with the updateCb() function
 * @return NULL if allocation or bootstrapping failed. If not NULL you need to free() it when you are done
 * usually by calling freeXmlBlasterConnectionUnparsed().
 */
extern CallbackServerUnparsed *getCallbackServerUnparsed(int argc, const char* const* argv,
                               UpdateCbFp updateCb, void *userData);

/**
 * free() the CallbackServerUnparsed structure and sets *callbackData to 0
 * Call freeCallbackServerUnparsed(&cb);
 */
extern void freeCallbackServerUnparsed(CallbackServerUnparsed **callbackData);

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

