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
#include <msgUtil.h>

#define DEFAULT_CALLBACK_SERVER_PORT 7611

// Forward declarations
struct CallbackServerUnparsedStruct;
typedef struct CallbackServerUnparsedStruct CallbackServerUnparsed;

// Define function pointers
typedef void (* InitCallbackServer)(CallbackServerUnparsed *cb);
typedef bool (* IsListening)(CallbackServerUnparsed *cb);
typedef char *(*UpdateFp)(MsgUnit *msg, XmlBlasterException *xmlBlasterException);
typedef void (* ShutdownCallbackServerRaw)(CallbackServerUnparsed *cb);

/**
 * This structure holds a complete callback server instance
 */
struct CallbackServerUnparsedStruct {
   int listenSocket;
   const char *hostCB;
   int portCB;
   bool debug;
   InitCallbackServer initCallbackServer;
   IsListening isListening;
   ShutdownCallbackServerRaw shutdown;
   UpdateFp update;
};

/**
 * Get a new instance of a callback server struct. 
 * This is usually the first call of a client, you need to call initCallbackServer()
 * on the returned pointer to establish a listener.
 * @param argc Number of command line arguments
 * @param argv The command line arguments
 * @param update The function pointer on your update() function which handles the received messages
 * @return NULL if bootstrapping failed. If not NULL you need to free() it when you are done
 * usually by calling freeXmlBlasterAccessUnparsed().
 */
extern CallbackServerUnparsed *getCallbackServerUnparsed(int argc, char** argv, UpdateFp update);

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


