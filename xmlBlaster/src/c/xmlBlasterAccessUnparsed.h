/*----------------------------------------------------------------------------
Name:      xmlBlasterAccessUnparsed.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Include this header in your client code
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/
#include "msgUtil.h"

/*
#define bool int
#define true 1
#define false 0
*/

struct XmlBlasterAccessUnparsedStruct;
typedef struct XmlBlasterAccessUnparsedStruct XmlBlasterAccessUnparsed;

typedef char *( * XmlBlasterConnect)(XmlBlasterAccessUnparsed *xb, const char * const qos, XmlBlasterException *exception);
typedef bool  ( * XmlBlasterDisconnect)(XmlBlasterAccessUnparsed *xb, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterPublish)(XmlBlasterAccessUnparsed *xb, MsgUnit *msgUnit, XmlBlasterException *exception);
typedef char *( * XmlBlasterSubscribe)(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterUnSubscribe)(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterErase)(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef MsgUnitArr *( * XmlBlasterGet)(XmlBlasterAccessUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
typedef char *( * XmlBlasterPing)(XmlBlasterAccessUnparsed *xb, const char * const qos);
typedef bool  ( * IsConnected)(XmlBlasterAccessUnparsed *xb);

#define MAX_SECRETSESSIONID_LEN 256

/**
 * All client access to xmlBlaster goes over this struct and its function pointers. 
 */
struct XmlBlasterAccessUnparsedStruct {
#  if WIN32
      SOCKET socketToXmlBlaster;
#  else
      int socketToXmlBlaster;
#  endif
   long requestId;
   unsigned char secretSessionId[MAX_SECRETSESSIONID_LEN];
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
};


extern XmlBlasterAccessUnparsed *getXmlBlasterAccessUnparsed(int argc, char** argv);
void freeXmlBlasterAccessUnparsed(XmlBlasterAccessUnparsed *xmlBlasterAccess);



/* Callback from xmlBlaster */
typedef void (*updateFp)(MsgUnit *msg);

typedef struct callbackDataStruct {
   char *hostCB;
   int portCB;
   /**
    * void update(MsgUnit *msg)   //////(char *key, char *content, int contentLen, char *qos)
    */
   updateFp update;
} callbackData;

/* for pthread */
typedef void * (*cbFp)(void *);

void initCallbackServer(callbackData *cbArgs);

