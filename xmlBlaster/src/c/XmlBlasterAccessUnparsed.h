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

#ifdef XB_USE_PTHREADS
#  ifdef _WINDOWS
#    include <pthreads/pthread.h> /* Our pthreads.h: For logging output of thread ID, for Windows and WinCE downloaded from http://sources.redhat.com/pthreads-win32 */
#  else
#    include <pthread.h>      /* The original pthreads.h from the OS */
#  endif
#endif

/* The following comment is used by doxygen for the main html page: */
/*! \mainpage Hints about the C client library usage.
 *
 * \section intro_sec The C client library
 *
 * The xmlBlaster C client library supports access to xmlBlaster with asynchronous callbacks.
 * Details about compilation and its usage can be found in the 
 * http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.socket.html requirement.
 *
 * As a C developer your entry point to use is the struct XmlBlasterAccessUnparsed and
 * a complete overview demo code is HelloWorld3.c
 *
 * \section queue_sec The C persistent queue
 *
 * There is a C based persistent queue available. Currently this C client library and
 * the queue implementation are used in the C++ client library for easy xmlBlaster connection
 * and client side queuing.
 * As a C developer your entry point to use is the struct I_QueueStruct and a source code example
 * is TestQueue.c
 *
 * For details read the
 * http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.queue.html requirement.
 *
 * \section cpp_sec The C++ client library
 * The C++ client library offers many extended client side features compared to the C library.
 * If you need those additional features and the library size is not the primary concern
 * you should consider to use the C++ library.
 *
 * For details read the
 * http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.cpp.html requirement
 * and look at the API documentation at http://www.xmlblaster.org/xmlBlaster/doc/doxygen/cpp/html/index.html
 */


/*
 NOTE: The struct name and the later typedef name are identical, we need this
       to allow in C++ SocketDriver.h a forward declaration of the XmlBlasterAccessUnparsed*
       pointer (to avoid inclusion of this complete header)
*/
struct XmlBlasterAccessUnparsed;

typedef XMLBLASTER_C_bool (*UpdateFp)(MsgUnitArr *msg, void *userData, XmlBlasterException *xmlBlasterException);

/* Declare function pointers to use in struct to simulate object oriented access */
typedef char *( * XmlBlasterAccessUnparsedConnect)(struct XmlBlasterAccessUnparsed *xb, const char * const qos, UpdateFp update, XmlBlasterException *exception);
typedef bool  ( * XmlBlasterAccessUnparsedInitialize)(struct XmlBlasterAccessUnparsed *xa, UpdateFp update, XmlBlasterException *exception);
typedef bool  ( * XmlBlasterAccessUnparsedDisconnect)(struct XmlBlasterAccessUnparsed *xb, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterAccessUnparsedPublish)(struct XmlBlasterAccessUnparsed *xb, MsgUnit *msgUnit, XmlBlasterException *exception);
typedef QosArr *( * XmlBlasterAccessUnparsedPublishArr)(struct XmlBlasterAccessUnparsed *xb, MsgUnitArr *msgUnitArr, XmlBlasterException *exception);
typedef void  ( * XmlBlasterAccessUnparsedPublishOneway)(struct XmlBlasterAccessUnparsed *xb, MsgUnitArr *msgUnitArr, XmlBlasterException *exception);
typedef char *( * XmlBlasterAccessUnparsedSubscribe)(struct XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef QosArr *( * XmlBlasterAccessUnparsedUnSubscribe)(struct XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef QosArr *( * XmlBlasterAccessUnparsedErase)(struct XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef MsgUnitArr *( * XmlBlasterAccessUnparsedGet)(struct XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterAccessUnparsedPing)(struct XmlBlasterAccessUnparsed *xb, const char * const qos, XmlBlasterException *exception);
typedef bool  ( * XmlBlasterAccessUnparsedIsConnected)(struct XmlBlasterAccessUnparsed *xb);

/**
 * All client access to xmlBlaster goes over this struct and its function pointers. 
 *
 * All function pointers expect a 'this' pointer of type #XmlBlasterAccessUnparsed
 * and return XmlBlasterException#errorCode="communication.noConnection" if connection
 * to xmlBlaster is lost.
 *
 * Create an instance of #XmlBlasterAccessUnparsed with a call to #getXmlBlasterAccessUnparsed()
 * and you are ready to access xmlBlaster. Don't forget to free everything when you don't need
 * xmlBlaster access anymore with a call to #freeXmlBlasterAccessUnparsed()
 *
 * See HelloWorld3.c for a complete usage example.
 *
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.socket.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
typedef struct Dll_Export XmlBlasterAccessUnparsed {
  /* public: */
   int argc;                  /**< The number of #argv entries */
   const char * const *argv;  /**< Environment configuration, usually from the command line */
   Properties *props;         /**< Further configuration parameters */
   void *userObject;          /**< A client can use this pointer to point to any client specific information */
   /**
    * Connect to the server. 
    * @param xa The 'this' pointer
    * @param qos The QoS xml markup string to connect, typically
    * <pre>
    * &lt;qos>
    *  &lt;securityService type='htpasswd' version='1.0'>
    *    &lt;user>fritz&lt;/user>
    *    &lt;passwd>secret&lt;/passwd>
    *  &lt;/securityService>
    * &lt;queue relating='callback' maxEntries='100' maxEntriesCache='100'>
    *   &lt;callback type='SOCKET' sessionId='%s'>
    *     socket://myServer.myCompany.com:6645
    *   &lt;/callback>
    * &lt;/queue>
    * &lt;/qos>
    * </pre>
    * @param clientUpdateFp The clients callback function pointer #UpdateFp, if NULL our default handler is used
    * @param The exception struct, exception->errorCode is filled on exception
    * @return The ConnectReturnQos raw xml string, you need to free() it
    * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.connect.html
    */
   XmlBlasterAccessUnparsedConnect connect;   
   /**
    * Creates client side connection object and the callback server and does the low level IP connection. 
    * This method is automatically called by #connect() so you usually only
    * call it explicitly if you are interested in the callback server settings.
    * @param xa The 'this' pointer
    * @param clientUpdateFp The clients callback handler function #UpdateFp. If NULL our default handler is used
    * @return true on success
    */
   XmlBlasterAccessUnparsedInitialize initialize;
   /**
    * Disconnect from server. 
    * @param xa The 'this' pointer
    * @param qos The QoS xml markup string to disconnect
    * @param The exception struct, exception->errorCode is filled on exception
    * @return false on exception
    * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
    */
   XmlBlasterAccessUnparsedDisconnect disconnect;   
   /**
    * Publish a message. 
    *
    * @param xa The 'this' pointer to simulate C++ classes
    * @param msgUnit The message of type #MsgUnit you want to send.
    * @param xmlBlasterException If *xmlBlasterException.errorCode!=0 this #XmlBlasterException
    *        is filled with the exception details and you should ignore the returned QosArr.
    * @return The QoS string with the response from xmlBlaster. You have to free it with a call to #xmlBlasterFree.
    *         If *xmlBlasterException.errorCode!=0 you need to ignore the returned data and don't need to free it.
    * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
    */
   XmlBlasterAccessUnparsedPublish publish;
   /**
    * Publish an array of messages. 
    *
    * @param xa The 'this' pointer to simulate C++ classes
    * @param msgUnitArr The messages of type #MsgUnitArr you want to send.
    * @param xmlBlasterException If *xmlBlasterException.errorCode!=0 this #XmlBlasterException is filled with the exception details and
    *        you should ignore the returned QosArr.
    * @return The #QosArr struct with the response from xmlBlaster. You have to free it with a call to #freeQosArr.
    *         If *xmlBlasterException.errorCode!=0 you need to ignore the returned data and don't need to free it.
    * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
    */
   XmlBlasterAccessUnparsedPublishArr publishArr;
   /**
    * Publish oneway an array of messages. 
    *
    * Oneway messages don't return something, the server does not acknowledge (ACK) them.
    *
    * @param xa The 'this' pointer to simulate C++ classes
    * @param msgUnitArr The messages of type #MsgUnitArr you want to send.
    * @param xmlBlasterException If *xmlBlasterException.errorCode!=0 this #XmlBlasterException is filled with the exception details and
    *        you should ignore the returned QosArr.
    * @return The #QosArr struct with the response from xmlBlaster. You have to free it with a call to #freeQosArr.
    *         If *xmlBlasterException.errorCode!=0 you need to ignore the returned data and don't need to free it.
    * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
    */
   XmlBlasterAccessUnparsedPublishOneway publishOneway;
   /**
    * Subscribe to messages. 
    *
    * @param xa The 'this' pointer to simulate C++ classes
    * @param key The key xml string
    * @param qos The QoS xml string
    * @param xmlBlasterException If *xmlBlasterException.errorCode!=0 this #XmlBlasterException is filled with the exception details and
    *        you should ignore the returned QosArr.
    * @return The QoS string with the response from xmlBlaster. You have to free it with a call to #xmlBlasterFree.
    *         If *xmlBlasterException.errorCode!=0 you need to ignore the returned data and don't need to free it.
    * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.subscribe.html
    */
   XmlBlasterAccessUnparsedSubscribe subscribe;
   XmlBlasterAccessUnparsedUnSubscribe unSubscribe;
   XmlBlasterAccessUnparsedErase erase;
   XmlBlasterAccessUnparsedGet get;
   XmlBlasterAccessUnparsedPing ping;
   /**
    * Check if we are connected to xmlBlaster. 
    * @param xa The 'this' pointer
    * @return #bool true or false
    */
   XmlBlasterAccessUnparsedIsConnected isConnected;
   XMLBLASTER_LOG_LEVEL logLevel;
   XmlBlasterLogging log;
   void *logUserP;                /**< For outside users to pass a user object back to the logging implementation */
  /* private: */
   XmlBlasterConnectionUnparsed *connectionP;
   CallbackServerUnparsed *callbackP;
   bool isInitialized;
   bool isShutdown;
   /**
    * Here we asynchronously receive the callback from xmlBlaster. 
    *
    * NOTE: After this call the memory of #MsgUnitArr is freed immediately by #CallbackServerUnparsed.
    *       So you need to take a copy of all message members if needed out of the scope of this function.
    *
    * @param msgUnitArr The messages of type #MsgUnitArr from the server, use MsgUnit#responseQos to transport the return value.
    *        If responseQos is not NULL it will be free()'d as well by us.
    * @param userData An optional pointer from the client with client specific data which is delivered back.
    *        Here userData is always the '#XmlBlasterAccessUnparsed *' pointer
    * @param xmlBlasterException This points on a valid struct of type #XmlBlasterException,
    *                            so you only need to fill errorCode with #strcpy
    *        and the returned pointer is ignored and the exception is thrown to xmlBlaster.
    * @return Return #bool true if everything is OK
    *         Return false if you want to throw an exception, please fill #XmlBlasterException in such a case.
    *         If false and *xmlBlasterException.errorCode==0 we don't send a return message
    *         (useful for update dispatcher thread to do it later)
    * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.update.html
    * @todo Return void instead of #bool
    */
   UpdateFp clientsUpdateFp;
   bool callbackMultiThreaded;    /**< Shall update messages be transported to the client code in a thread per request? */
   bool lowLevelAutoAck;
   long responseTimeout;
   pthread_t callbackThreadId;
   char threadCounter;
   pthread_mutex_t writenMutex;   /**< Protect writing n bytes to the socket */
   pthread_mutex_t readnMutex;   /**< Protect reading n bytes to the socket */
} XmlBlasterAccessUnparsed;

/**
 * Create an instance to access xmlBlaster. 
 * This is usually the first call of a client. 
 *
 * Every call creates a new and independent client access instance to xmlBlaster
 *
 * Our properties point on the passed argv memory, so you should
 * not free the original argv memory before you free #XmlBlasterAccessUnparsed.
 *
 * @param argc The number of argv properties
 * @param argv The command line properties, see #createProperties() for a specification, can be NULL for argc==0
 * @return NULL if bootstrapping failed. If not NULL you need to free memory when you are done
 * usually by calling #freeXmlBlasterAccessUnparsed().
 */
Dll_Export extern XmlBlasterAccessUnparsed *getXmlBlasterAccessUnparsed(int argc, const char* const* argv);

/**
 * Free your instance after accessing xmlBlaster. 
 */
Dll_Export extern void freeXmlBlasterAccessUnparsed(XmlBlasterAccessUnparsed *xmlBlasterAccess);

/**
 * Help usage
 * @param usage Please pass a string with at least XMLBLASTER_MAX_USAGE_LEN chars allocated (or on stack)
 * @return Your usage pointer filled with informations
 */
Dll_Export extern const char *xmlBlasterAccessUnparsedUsage(char *usage);

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif

#endif /* _XmlBlasterAccessUnparsed_H */

