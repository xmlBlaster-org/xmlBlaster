/*----------------------------------------------------------------------------
Name:      XmlBlasterAccessUnparsed.h
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

struct XmlBlasterAccessUnparsedStruct;
typedef struct XmlBlasterAccessUnparsedStruct XmlBlasterAccessUnparsed;

/* Declare function pointers to use in struct to simulate object oriented access */
typedef char *( * XmlBlasterConnect)(XmlBlasterAccessUnparsed *xb, const char * const qos, XmlBlasterException *exception);
typedef bool  ( * XmlBlasterDisconnect)(XmlBlasterAccessUnparsed *xb, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterPublish)(XmlBlasterAccessUnparsed *xb, MsgUnit *msgUnit, XmlBlasterException *exception);
typedef char *( * XmlBlasterSubscribe)(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterUnSubscribe)(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterErase)(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef MsgUnitArr *( * XmlBlasterGet)(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterPing)(XmlBlasterAccessUnparsed *xb, const char * const qos);
typedef bool  ( * IsConnected)(XmlBlasterAccessUnparsed *xb);
typedef const char *( * Usage)(XmlBlasterAccessUnparsed *xb);

#define MAX_SECRETSESSIONID_LEN 256

/**
 * All client access to xmlBlaster goes over this struct and its function pointers. 
 */
struct XmlBlasterAccessUnparsedStruct {
   int socketToXmlBlaster;
   long requestId;
   char secretSessionId[MAX_SECRETSESSIONID_LEN];
   bool isInitialized;
   XmlBlasterConnect connect;   
   XmlBlasterDisconnect disconnect;   
   XmlBlasterPublish publish;
   XmlBlasterSubscribe subscribe;
   XmlBlasterUnSubscribe unSubscribe;
   XmlBlasterErase erase;
   XmlBlasterGet get;
   XmlBlasterPing ping;
   IsConnected isConnected;
   bool debug;
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

