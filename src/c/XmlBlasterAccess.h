/*----------------------------------------------------------------------------
Name:      XmlBlasterAccess.h
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
#ifndef _XmlBlasterAccess_H
#define _XmlBlasterAccess_H

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP /* 'g++ -DXMLBLASTER_C_COMPILE_AS_CPP ...' allows to compile the lib as C++ code */
extern "C" {
#endif
#endif

#include <util/msgUtil.h>
#include <util/Properties.h>
#include <XmlBlasterAccessUnparsed.h>

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
 * As a C developer your entry point to use is the struct XmlBlasterAccess and
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
       to allow in C++ SocketDriver.h a forward declaration of the XmlBlasterAccess*
       pointer (to avoid inclusion of this complete header)
*/
struct XmlBlasterAccess;
struct ReturnQosStruct;

typedef bool ( * XmlBlasterIsStateOk)(struct ReturnQosStruct *returnQos);
typedef struct ReturnQosStruct {
   XmlBlasterIsStateOk isOk; /* State Info */
   char *returnQos;
} ReturnQos;
typedef struct ReturnQosStruct ConnectReturnQos;
typedef struct ReturnQosStruct PublishReturnQos;
typedef struct ReturnQosStruct SubscribeReturnQos;
typedef struct ReturnQosStruct UnSubscribeReturnQos;
typedef struct ReturnQosStruct EraseReturnQos;
typedef struct ReturnQosStruct PingReturnQos;
/*
typedef struct PublishReturnQosStructArr {
   uint32_t len;
   PublishReturnQos *returnQosArr;
} PublishReturnQosArr;
typedef struct EraseReturnQosStructArr {
   uint32_t len;
   EraseReturnQos *returnQosArr;
} EraseReturnQosArr;
typedef struct UnSubscribeReturnQosStructArr {
   uint32_t len;
   UnSubscribeReturnQos *returnQosArr;
} UnSubscribeReturnQosArr;
*/
typedef struct ReturnQosStructArr {
   uint32_t len;
   ReturnQos *returnQosArr;
} ReturnQosArr;
typedef struct ReturnQosStructArr PublishReturnQosArr;
typedef struct ReturnQosStructArr UnSubscribeReturnQosArr;
typedef struct ReturnQosStructArr EraseReturnQosArr;

typedef struct KeyStruct {
   char *key;
} Key;
typedef struct KeyStruct PublishKey;
typedef struct KeyStruct GetKey;
typedef struct KeyStruct SubscribeKey;
typedef struct KeyStruct UnSubscribeKey;
typedef struct KeyStruct EraseKey;
typedef struct QosStruct {
   char *qos;
} Qos;
typedef struct QosStruct ConnectQos;
typedef struct QosStruct DisconnectQos;
typedef struct QosStruct PublishQos;
typedef struct QosStruct GetQos;
typedef struct QosStruct SubscribeQos;
typedef struct QosStruct UnSubscribeQos;
typedef struct QosStruct EraseQos;
typedef struct QosStruct PingQos;


/* Design decision: The qos and key remain char* and not ConnectQosStruct* to simplify usage */
/* Declare function pointers to use in struct to simulate object oriented access */
typedef ConnectReturnQos *( * XmlBlasterAccessConnect)(struct XmlBlasterAccess *xb, const ConnectQos * connectQos, UpdateFp update, XmlBlasterException *exception);
typedef bool  ( * XmlBlasterAccessInitialize)(struct XmlBlasterAccess *xa, UpdateFp update, XmlBlasterException *exception);
typedef bool  ( * XmlBlasterAccessDisconnect)(struct XmlBlasterAccess *xb, const DisconnectQos * disconnectQos, XmlBlasterException *exception);
typedef PublishReturnQos *( * XmlBlasterAccessPublish)(struct XmlBlasterAccess *xb, MsgUnit *msgUnit, XmlBlasterException *exception);
typedef PublishReturnQosArr *( * XmlBlasterAccessPublishArr)(struct XmlBlasterAccess *xb, MsgUnitArr *msgUnitArr, XmlBlasterException *exception);
typedef void  ( * XmlBlasterAccessPublishOneway)(struct XmlBlasterAccess *xb, MsgUnitArr *msgUnitArr, XmlBlasterException *exception);
typedef SubscribeReturnQos *( * XmlBlasterAccessSubscribe)(struct XmlBlasterAccess *xb, const SubscribeKey * subscribeKey, const SubscribeQos * subscribeQos, XmlBlasterException *exception);
typedef UnSubscribeReturnQosArr *( * XmlBlasterAccessUnSubscribe)(struct XmlBlasterAccess *xb, const UnSubscribeKey * unSubscribeKey, const UnSubscribeQos * unSubscribeQos, XmlBlasterException *exception);
typedef UnSubscribeReturnQosArr *( * XmlBlasterAccessErase)(struct XmlBlasterAccess *xb, const EraseKey * eraseKey, const EraseQos * eraseQos, XmlBlasterException *exception);
typedef MsgUnitArr *( * XmlBlasterAccessGet)(struct XmlBlasterAccess *xb, const GetKey * getKey, const GetQos * getQos, XmlBlasterException *exception);
typedef PingReturnQos *( * XmlBlasterAccessPing)(struct XmlBlasterAccess *xb, const PingQos * pingQos, XmlBlasterException *exception);
typedef bool  ( * XmlBlasterAccessIsConnected)(struct XmlBlasterAccess *xb);

/**
 * All client access to xmlBlaster goes over this struct and its function pointers.
 *
 * All function pointers expect a 'this' pointer of type #XmlBlasterAccess
 * and return XmlBlasterException#errorCode="communication.noConnection" if connection
 * to xmlBlaster is lost.
 *
 * Create an instance of #XmlBlasterAccess with a call to #getXmlBlasterAccess()
 * and you are ready to access xmlBlaster. Don't forget to free everything when you don't need
 * xmlBlaster access anymore with a call to #freeXmlBlasterAccess()
 *
 * See HelloWorld3.c for a complete usage example.
 *
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.socket.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
typedef struct Dll_Export XmlBlasterAccess {
  /* public: */
   int argc;                  /**< The number of #argv entries */
   const char * const *argv;  /**< Environment configuration, usually from the command line */
   Properties *props;         /**< Further configuration parameters */
   void *userObject;          /**< A client can use this pointer to point to any client specific information */
   XmlBlasterAccessGenericFp userFp; /**< A client can use this function pointer to do any client specific handling */
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
    *                       Is ignored if set by initialize already.
    * @param The exception struct, exception->errorCode is filled on exception
    * @return The ConnectReturnQos raw xml string, you need to free() it
    * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.connect.html
    */
   XmlBlasterAccessConnect connect;
   /**
    * Creates client side connection object and the callback server and does the low level IP connection.
    * This method is automatically called by #connect() so you usually only
    * call it explicitly if you are interested in the callback server settings.
    * @param xa The 'this' pointer
    * @param clientUpdateFp The clients callback handler function #UpdateFp. If NULL our default handler is used
    * @return true on success
    */
   XmlBlasterAccessInitialize initialize;
   /**
    * Disconnect from server.
    * @param xa The 'this' pointer
    * @param qos The QoS xml markup string to disconnect
    * @param The exception struct, exception->errorCode is filled on exception
    * @return false on exception
    * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
    */
   XmlBlasterAccessDisconnect disconnect;
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
   XmlBlasterAccessPublish publish;
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
   XmlBlasterAccessPublishArr publishArr;
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
   XmlBlasterAccessPublishOneway publishOneway;
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
   XmlBlasterAccessSubscribe subscribe;
   XmlBlasterAccessUnSubscribe unSubscribe;
   XmlBlasterAccessErase erase;
   XmlBlasterAccessGet get;
   XmlBlasterAccessPing ping;
   /**
    * Check if we are connected to xmlBlaster.
    * @param xa The 'this' pointer
    * @return #bool true or false
    */
   XmlBlasterAccessIsConnected isConnected;
   XMLBLASTER_LOG_LEVEL logLevel;
   XmlBlasterLogging log;
   void *logUserP;                /**< For outside users to pass a user object back to the logging implementation */
  /* private: */
   XmlBlasterAccessUnparsed *connectionP;
   bool isInitialized;
   bool isShutdown;
} XmlBlasterAccess;

/**
 * Create an instance to access xmlBlaster.
 * This is usually the first call of a client.
 *
 * Every call creates a new and independent client access instance to xmlBlaster
 *
 * Our properties point on the passed argv memory, so you should
 * not free the original argv memory before you free #XmlBlasterAccess.
 *
 * @param argc The number of argv properties
 * @param argv The command line properties, see #createProperties() for a specification, can be NULL for argc==0
 * @return NULL if bootstrapping failed. If not NULL you need to free memory when you are done
 * usually by calling #freeXmlBlasterAccess().
 */
Dll_Export extern XmlBlasterAccess *getXmlBlasterAccess(int argc, const char* const* argv);

/**
 * Free your instance after accessing xmlBlaster.
 */
Dll_Export extern void freeXmlBlasterAccess(XmlBlasterAccess *xmlBlasterAccess);

Dll_Export extern Key *createXmlBlasterKey(const char * keyXml);
Dll_Export extern Qos *createXmlBlasterQos(const char * qosXml);
Dll_Export extern ReturnQos *createXmlBlasterReturnQos(const char * qosXml);
/*Dll_Export extern QosArr *createXmlBlasterQosArr(const char * qosArr);*/
Dll_Export extern void freeXmlBlasterKey(Key * key);
Dll_Export extern void freeXmlBlasterQos(Qos * qos);
Dll_Export extern void freeXmlBlasterReturnQos(ReturnQos * returnQos);
Dll_Export extern void freeXmlBlasterReturnQosArr(ReturnQosArr * returnQosArr);

/**
 * Help usage
 * @param usage Please pass a string with at least XMLBLASTER_MAX_USAGE_LEN chars allocated (or on stack)
 * @return Your usage pointer filled with informations
 */
Dll_Export extern const char *XmlBlasterAccessUsage(char *usage);

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif

#endif /* _XmlBlasterAccess_H */

