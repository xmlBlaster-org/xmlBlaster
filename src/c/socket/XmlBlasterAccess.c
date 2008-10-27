/*----------------------------------------------------------------------------
Name:      XmlBlasterAccess.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Wraps raw socket connection to xmlBlaster
           Implements sync connection and async callback
           Needs pthread to compile (multi threading).
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:
  LINUX:   gcc -DXmlBlasterAccessMain -D_ENABLE_STACK_TRACE_ -rdynamic -export-dynamic -Wall -pedantic -g -D_REENTRANT -I.. -o XmlBlasterAccessMain XmlBlasterAccess.c ../util/msgUtil.c ../util/Properties.c xmlBlasterSocket.c XmlBlasterAccessUnparsed.c XmlBlasterConnectionUnparsed.c CallbackServerUnparsed.c -lpthread
           g++ -DXmlBlasterAccessMain -DXMLBLASTER_C_COMPILE_AS_CPP -Wall -pedantic -g -D_REENTRANT -I.. -o XmlBlasterAccessMain XmlBlasterAccess.c ../util/msgUtil.c ../util/Properties.c xmlBlasterSocket.c XmlBlasterAccessUnparsed.c XmlBlasterConnectionUnparsed.c CallbackServerUnparsed.c -lpthread
           icc -DXmlBlasterAccessMain -D_ENABLE_STACK_TRACE_ -rdynamic -g -D_REENTRANT -I.. -o XmlBlasterAccessMain XmlBlasterAccess.c ../util/msgUtil.c ../util/Properties.c xmlBlasterSocket.c XmlBlasterAccessUnparsed.c XmlBlasterConnectionUnparsed.c CallbackServerUnparsed.c -lpthread
  WIN:     cl /MT /W4 -DXmlBlasterAccessMain -D_WINDOWS -I.. -I../pthreads /FeXmlBlasterAccessMain.exe  XmlBlasterAccess.c ..\util\msgUtil.c ..\util\Properties.c xmlBlasterSocket.c XmlBlasterAccessUnparsed.c XmlBlasterConnectionUnparsed.c CallbackServerUnparsed.c ws2_32.lib pthreadVC2.lib
           (download pthread for Windows and WinCE from http://sources.redhat.com/pthreads-win32)
  Solaris: cc  -DXmlBlasterAccessMain -v -Xc -g -D_REENTRANT -I.. -o XmlBlasterAccessMain XmlBlasterAccess.c ../util/msgUtil.c ../util/Properties.c xmlBlasterSocket.c XmlBlasterAccessUnparsed.c XmlBlasterConnectionUnparsed.c CallbackServerUnparsed.c -lpthread -lsocket -lnsl
           CC  -DXmlBlasterAccessMain -DXMLBLASTER_C_COMPILE_AS_CPP -g -D_REENTRANT -I.. -o XmlBlasterAccessMain XmlBlasterAccess.c ../util/msgUtil.c ../util/Properties.c xmlBlasterSocket.c XmlBlasterAccessUnparsed.c XmlBlasterConnectionUnparsed.c CallbackServerUnparsed.c -lpthread -lsocket -lnsl

  Linux with libxmlBlasterC.so:
           gcc -DXmlBlasterAccessMain -o XmlBlasterAccessMain XmlBlasterAccess.c  -L../../../lib -lxmlBlasterClientC -I.. -Wl,-rpath=../../../lib -D_REENTRANT  -lpthread
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#if defined(WINCE)
#  if defined(XB_USE_PTHREADS)
#     include <pthreads/pthread.h>
#  else
      /*#include <pthreads/need_errno.h> */
      static int errno=0; /* single threaded workaround*/
#  endif
#else
#  include <errno.h>
#  include <sys/types.h>
#endif
#include <socket/xmlBlasterSocket.h>
#include <socket/xmlBlasterZlib.h>
#include <XmlBlasterAccess.h>
#include <util/XmlUtil.h>

static const int XBTYPE_PING=0;
static const int XBTYPE_POLL=1;

static bool checkArgs(XmlBlasterAccess *xa, const char *methodName,
            bool checkIsConnected, XmlBlasterException *exception);
static bool checkPost(XmlBlasterAccess *xa, const char *methodName,
      void *returnObj, XmlBlasterException *exception);

static bool xmlBlasterIsStateOk(ReturnQos *returnQos);

static void xmlBlasterRegisterConnectionListener(struct XmlBlasterAccess *xa, ConnectionListenerCbFp cbFp, void *userData);

static bool _initialize(XmlBlasterAccess *xa, UpdateFp update, XmlBlasterException *exception);
static ConnectReturnQos *xmlBlasterConnect(XmlBlasterAccess *xa, const ConnectQos * connectQos, UpdateFp update, XmlBlasterException *exception);
static bool xmlBlasterDisconnect(XmlBlasterAccess *xa, const DisconnectQos * disconnectQos, XmlBlasterException *exception);
static PublishReturnQos *xmlBlasterPublish(XmlBlasterAccess *xa, MsgUnit *msgUnit, XmlBlasterException *exception);
static PublishReturnQosArr *xmlBlasterPublishArr(XmlBlasterAccess *xa, MsgUnitArr *msgUnitArr, XmlBlasterException *exception);
static void xmlBlasterPublishOneway(XmlBlasterAccess *xa, MsgUnitArr *msgUnitArr, XmlBlasterException *exception);
static SubscribeReturnQos *xmlBlasterSubscribe(XmlBlasterAccess *xa, const SubscribeKey * subscribeKey, const SubscribeQos * subscribeQos, XmlBlasterException *exception);
static UnSubscribeReturnQosArr *xmlBlasterUnSubscribe(XmlBlasterAccess *xa, const UnSubscribeKey * unSubscribeKey, const UnSubscribeQos * unSubscribeQos, XmlBlasterException *exception);
static EraseReturnQosArr *xmlBlasterErase(XmlBlasterAccess *xa, const EraseKey * eraseKey, const EraseQos * eraseQos, XmlBlasterException *exception);
static MsgUnitArr *xmlBlasterGet(XmlBlasterAccess *xa, const GetKey * getKey, const GetQos * getQos, XmlBlasterException *exception);
static PingReturnQos *xmlBlasterPing(XmlBlasterAccess *xa, const PingQos * pingQos, XmlBlasterException *exception);
static bool isConnected(XmlBlasterAccess *xa);

Dll_Export XmlBlasterAccess *getXmlBlasterAccess(int argc, const char* const* argv) {
   XmlBlasterAccess * const xa = (XmlBlasterAccess *)calloc(1, sizeof(XmlBlasterAccess));
   if (xa == 0) return xa;
   xa->argc = argc;
   xa->argv = argv;
   xa->props = createProperties(xa->argc, xa->argv);
   if (xa->props == 0) {
      freeXmlBlasterAccess(xa);
      return (XmlBlasterAccess *)0;
   }
   //xa->isInitialized = false;
   xa->isShutdown = false;
   xa->connectionP = 0;
   xa->userObject = 0; /* A client can use this pointer to point to any client specific information */
   xa->connectionListenerCbFp = 0;
   xa->pingPollTimer = createTimeout("PingPollTimer");
   xa->userFp = 0;
   xa->registerConnectionListener = xmlBlasterRegisterConnectionListener;
   xa->connect = xmlBlasterConnect;
   xa->disconnect = xmlBlasterDisconnect;
   xa->publish = xmlBlasterPublish;
   xa->publishArr = xmlBlasterPublishArr;
   xa->publishOneway = xmlBlasterPublishOneway;
   xa->subscribe = xmlBlasterSubscribe;
   xa->unSubscribe = xmlBlasterUnSubscribe;
   xa->erase = xmlBlasterErase;
   xa->get = xmlBlasterGet;
   xa->ping = xmlBlasterPing;
   xa->isConnected = isConnected;

   xa->pingInterval = 10000;
   xa->retries = -1;
   xa->delay = 5000;
   xa->connnectionState = XBCONSTATE_UNDEF;

   xa->logLevel = parseLogLevel(xa->props->getString(xa->props, "logLevel", "WARN"));
   xa->log = xmlBlasterDefaultLogging;
   xa->logUserP = 0;
   return xa;
}

static void _freeConnectionP(XmlBlasterAccess *xa) {
   XmlBlasterAccessUnparsed *cP = xa->connectionP;
   if (xa == 0) return;
   if (cP != 0) {
      xa->connectionP = 0;
      freeXmlBlasterAccessUnparsed(cP);
   }
}

Dll_Export void freeXmlBlasterAccess(XmlBlasterAccess *xa)
{
   if (xa == 0) {
      char *stack = getStackTrace(10);
      printf("[%s:%d] Please provide a valid XmlBlasterAccess pointer to freeXmlBlasterAccess() %s",
                __FILE__, __LINE__, stack);
      free(stack);
      return;
   }

   if (xa->isShutdown) return; /* Avoid simultaneous multiple calls */
   xa->isShutdown = true;      /* Inhibit access to xa */

   if (xa->logLevel>=XMLBLASTER_LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "freeXmlBlasterAccess() conP=0x%x", xa->connectionP);

   freeXmlBlasterQos(xa->connectQos);
   freeXmlBlasterReturnQos(xa->connectReturnQos);

   freeTimeout(xa->pingPollTimer);
   xa->pingPollTimer = 0;

   _freeConnectionP(xa);

   freeProperties(xa->props);
   free(xa);
}

static void xmlBlasterRegisterConnectionListener(struct XmlBlasterAccess *xa, ConnectionListenerCbFp cbFp, void *userData) {
	xa->connectionListenerCbFp = cbFp;
	xa->connectionListenerUserData = userData;
}

Dll_Export const char *connectionStateToStr(XBCONSTATE state) {
   if (state == XBCONSTATE_SOCKALIVE)
      return "SOCKALIVE";
   else if (state == XBCONSTATE_LOGGEDINALIVE)
      return "LOGGEDINALIVE";
   else if (state == XBCONSTATE_POLLING)
      return "POLLING";
   else if (state == XBCONSTATE_DEAD)
      return "DEAD";
   return "UNDEF";
}

static int changeConnectionStateTo(XmlBlasterAccess *xa, int newState, XmlBlasterException *exception) {
	ConnectionListenerCbFp cb = xa->connectionListenerCbFp;
	int oldState = xa->connnectionState;
	xa->connnectionState = newState;

	/* Ignore same states */
   if (oldState == newState)
      return newState;

	/* Logging only */
   /*
	if ((oldState == XBCONSTATE_ALIVE || oldState == XBCONSTATE_LOGGEDIN)
	      && newState != XBCONSTATE_ALIVE && newState != XBCONSTATE_LOGGEDIN) {
	   if (exception != 0)
	      xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_WARN, __FILE__, "New connectionState=%s, errorCode=%s message=%s",
	            conStateToStr(newState), exception->errorCode, exception->message);
	}
	*/
   if (exception != 0 && *exception->errorCode != 0)
      xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_WARN, __FILE__, "New connectionState=%s, errorCode=%s message=%s",
            connectionStateToStr(newState), exception->errorCode, exception->message);
   else
      xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_INFO, __FILE__, "Transition connectionState %s to %s",
            connectionStateToStr(oldState), connectionStateToStr(newState));

	/* Notify user */
   if (cb != 0) {
    	cb(xa, oldState, newState, exception, xa->connectionListenerUserData);
	}

   return newState;
}

/**
 * Has the connect() method successfully passed?
 * <p>
 * Note that this contains no information about the current connection state
 * of the protocol layer.
 * </p>
 * @return true If the connection() method was invoked without exception
 */
static bool isConnected(XmlBlasterAccess *xa)
{
   if (xa == 0 || xa->isShutdown/* || xa->connectionP == 0*/) {
      return false;
   }
   return xa->connectReturnQos != 0; /*xa->connectionP->isConnected(xa->connectionP);*/
}


Dll_Export const char *XmlBlasterAccessUsage(char *usage)
{
   /* take care not to exceed XMLBLASTER_MAX_USAGE_LEN */
   char tmp[XMLBLASTER_MAX_USAGE_LEN];
   xmlBlasterAccessUnparsedUsage(tmp);
   SNPRINTF(usage, XMLBLASTER_MAX_USAGE_LEN, "%.1600s,%.300s%.146s", tmp,
         "\n   -dispatch/connection/pingInterval"
         "\n                       Pinging every given milliseconds [10000]"
         "\n                       0 switches pinging off"
         "\n   -dispatch/connection/retries"
         "\n                       How often to retry if connection fails (-1 is forever) [-1]"
         "\n                       Set to -1 for failsafe operation",
         "\n   -dispatch/connection/delay"
         "\n                       Delay between connection retries in milliseconds [5000]"
         "\n                       A delay value > 0 switches fails save mode on, 0 switches it off"
                  );

   return usage;
}


Dll_Export Key *createXmlBlasterKey(const char * keyP) {
   Key *key = (Key *)calloc(1, sizeof(Key));
   if (key == 0) return key;
   if (keyP != 0)
      key->key = strcpyAlloc(keyP);
   return key;
}


Dll_Export Qos *createXmlBlasterQos(const char * qosP) {
   Qos *qos = (Qos *)calloc(1, sizeof(Qos));
   if (qos == 0) return qos;
   if (qosP != 0)
      qos->qos = strcpyAlloc(qosP);
   else
      qos->qos = strcpyAlloc("<qos/>");
   return qos;
}

/**
 * Aware: Uses allocated qosXml for returnQos->returnQos
 * so you don't need to free qosXml anymore.
 * Freeing of ReturnQos will free qosXml
 */
Dll_Export ReturnQos *createXmlBlasterReturnQos(const char * qosXml) {
   ReturnQos *returnQos = (ReturnQos *)calloc(1, sizeof(ReturnQos));
   if (returnQos == 0) return 0;
   if (qosXml != 0) {
      returnQos->returnQos = (char *)qosXml;
      /*returnQos->returnQos = strcpyAlloc(qosXml);*/
      returnQos->isOk = xmlBlasterIsStateOk;
   }
   return returnQos;
}


/**
 * Aware: Frees QosArr
 */
Dll_Export ReturnQosArr *createXmlBlasterReturnQosArr(QosArr * qosArr) {
   ReturnQosArr *qos = (ReturnQosArr *)calloc(1, sizeof(ReturnQosArr));
   if (qos == 0) return qos;
   if (qosArr != 0) {
      int i;
      qos->len = qosArr->len;
      qos->returnQosArr = (ReturnQos*)calloc(1, qos->len*sizeof(ReturnQos));
      for (i=0; i<qos->len; i++) {
         /*qos->returnQosArr[i] = (ReturnQos)calloc(sizeof(ReturnQos));*/
         qos->returnQosArr[i].isOk = xmlBlasterIsStateOk;
         qos->returnQosArr[i].returnQos = strcpyAlloc(qosArr->qosArr[i]);
      }
      freeQosArr(qosArr);
   }
   return qos;
}

static bool xmlBlasterIsStateOk(ReturnQos *returnQos) {
   if (returnQos == 0) return false;
   return true; /* todo: parse qos xml markup */
}

Dll_Export void freeXmlBlasterKey(Key * key) {
	if (key == 0) return;
	if (key->key != 0) {
		free(key->key);
		key->key = 0;
	}
	free(key);
}

Dll_Export void freeXmlBlasterQos(Qos * qos) {
	if (qos == 0) return;
	if (qos->qos != 0) {
		free(qos->qos);
		qos->qos = 0;
	}
	free(qos);
}

static void freeXmlBlasterReturnQos_(ReturnQos * returnQos, bool freeContainerAsWell) {
   if (returnQos == 0) return;
   if (returnQos->returnQos != 0) {
      free(returnQos->returnQos);
   }
   if (freeContainerAsWell)
      free(returnQos);
}

Dll_Export void freeXmlBlasterReturnQosArr(ReturnQosArr * returnQosArr) {
   if (returnQosArr == 0) return;
   if (returnQosArr->returnQosArr != 0) {
      int i;
      for (i=0; i<returnQosArr->len; i++) {
         ReturnQos *returnQos = &returnQosArr->returnQosArr[i];
         freeXmlBlasterReturnQos_(returnQos, false);
      }
      free(returnQosArr->returnQosArr);
      returnQosArr->returnQosArr = 0;
      returnQosArr->len = 0;
   }
   free(returnQosArr);
}

Dll_Export extern void freeXmlBlasterReturnQos(ReturnQos * returnQos) {
   freeXmlBlasterReturnQos_(returnQos, true);
}

static bool _initialize(XmlBlasterAccess *xa, UpdateFp clientUpdateFp, XmlBlasterException *exception)
{
   if (checkArgs(xa, "initialize", false, exception) == false) return false;

   if (xa->connectionP) {
   //if (xa->isInitialized) {
      return true;
   }

   xa->clientsUpdateFp = clientUpdateFp;

   _freeConnectionP(xa);
   xa->connectionP = getXmlBlasterAccessUnparsed(xa->argc, xa->argv);
   if (xa->connectionP == 0) {
      strncpy0(exception->errorCode, "resource.outOfMemory", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
               "[%.100s:%d] Creating XmlBlasterAccessUnparsed failed", __FILE__, __LINE__);
      if (xa->logLevel>=XMLBLASTER_LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, exception->message);
      return false;
   }
   xa->connectionP->log = xa->log;
   xa->connectionP->logUserP = xa->logUserP;
   xa->connectionP->userObject = xa;
   xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "Created XmlBlasterAccessUnparsed");

   /*
   -dispatch/connection/pingInterval
                       Pinging every given milliseconds [10000]
                       0 switches pinging off
   -dispatch/connection/retries
                       How often to retry if connection fails (-1 is forever) [-1]
                       Set to -1 for failsafe operation
   -dispatch/connection/delay
                       Delay between connection retries in milliseconds [5000]
                       A delay value > 0 switches fails save mode on, 0 switches it off
    */
   /*<queue relating='connection'><address type="socket" pingInterval='0' retries='-1' delay='10000'/></queue>*/
   xa->pingInterval = xmlBlasterExtractAttributeLong(xa->connectQos->qos, "address", "pingInterval",
         xa->connectionP->props->getLong(xa->connectionP->props, "dispatch/connection/pingInterval", 10000));
   xa->retries = xmlBlasterExtractAttributeLong(xa->connectQos->qos, "address", "retries",
         xa->connectionP->props->getLong(xa->connectionP->props, "dispatch/connection/retries", -1));
   xa->delay = xmlBlasterExtractAttributeLong(xa->connectQos->qos, "address", "delay",
         xa->connectionP->props->getLong(xa->connectionP->props, "dispatch/connection/delay", 5000));

   /* Establish low level IP connection */
   if (xa->connectionP->initialize(xa->connectionP, clientUpdateFp, exception) == false) {
      checkPost(xa, "initialize", 0, exception);
      return false;
   }
   checkPost(xa, "initialize", 0, exception);

   //xa->isInitialized = true;
   if (xa->logLevel>=XMLBLASTER_LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
                                "initialize() successful");
   return true;//xa->isInitialized;
}

/** Called internally only */
static ConnectReturnQos *_xmlBlasterReConnect(XmlBlasterAccess *xa, UpdateFp clientUpdateFp, XmlBlasterException *exception)
{
   char *response = 0;

   if (_initialize(xa, clientUpdateFp, exception) == false) {
      return 0;
   }

   if (xa->logLevel>=XMLBLASTER_LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "Invoking connect()");

   /* Register our function responseEvent() to be notified when the response arrives,
      this is done by preSendEvent() callback called during connect() */

   printf("_xmlBlasterReConnect connectionP=%p\n", xa->connectionP);
   printf("_xmlBlasterReConnect clientUpdateFp=%p\n", clientUpdateFp);
   printf("_xmlBlasterReConnect exception=%p\n", exception);
   printf("_xmlBlasterReConnect connectQos=%p\n", xa->connectQos);
   printf("_xmlBlasterReConnect xa->connectionP->connect=%p\n", xa->connectionP->connect);

   response = xa->connectionP->connect(xa->connectionP, (xa->connectQos==0)?0:xa->connectQos->qos, clientUpdateFp, exception);

   if (checkPost(xa, "connect", response, exception) == false ) return 0;

   freeXmlBlasterReturnQos(xa->connectReturnQos);
   xa->connectReturnQos = 0;
   xa->connectReturnQos = createXmlBlasterReturnQos(response); /* reuses response memory */

   return xa->connectReturnQos;
}

static void onPingPollTimeout(Timeout *timeout, void *userData, void *userData2) {
   XmlBlasterAccess *xa = (XmlBlasterAccess *)userData;
   int type = (int)(*((int*)userData2)); /* XBTYPE_PING=0 | XBTYPE_POLL=1 */
   char timeStr[64];
   /*ConnectionListenerCbFp cb = xa->connectionListenerCbFp;*/

   if (xa->logLevel>=XMLBLASTER_LOG_INFO)
      xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_INFO, __FILE__,
          "%s Timeout occurred, timer=%s delay=%ld type=%s\n",
         getCurrentTimeStr(timeStr, 64), timeout->name,
         timeout->timeoutContainer.delay, (type==XBTYPE_PING?"PING":"POLL"));

   if (type == XBTYPE_PING) {
      PingQos *pingQos = 0;
      PingReturnQos *pingReturnQos = 0;
      XmlBlasterException exception;
      pingReturnQos = xa->ping(xa, pingQos, &exception); /* Does error handling */
      freeXmlBlasterReturnQos(pingReturnQos);
      /*
      if (*exception.errorCode != 0) {
         if (xa->logLevel>=XMLBLASTER_LOG_WARN)
            xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_WARN,
               __FILE__, "Ping failed: %s %s",
               exception.errorCode, exception.message);
         cb(xa, XBCONSTATE_ALIVE, XBCONSTATE_POLLING, &exception, xa->connectionListenerUserData);
      }
      else {
         if (xa->logLevel>=XMLBLASTER_LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "Ping success");
      }
      */
   }

   else if (type == XBTYPE_POLL) {
      XmlBlasterException xmlBlasterException;
      initializeXmlBlasterException(&xmlBlasterException);
      if (xa->logLevel>=XMLBLASTER_LOG_TRACE)
         xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_TRACE,
               __FILE__, "Polling again (every %ld milli seconds)", xa->delay);
      _freeConnectionP(xa);
      _xmlBlasterReConnect(xa, xa->clientsUpdateFp, &xmlBlasterException);
   }
}

/** Called by user code */
static ConnectReturnQos *xmlBlasterConnect(XmlBlasterAccess *xa, const ConnectQos * connectQos,
                               UpdateFp clientUpdateFp, XmlBlasterException *exception)
{
   ConnectReturnQos *ret = 0;
   if (checkArgs(xa, "connect", false, exception) == false) return 0;

   if (connectQos == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%.100s:%d] Please provide valid argument 'connectQos' to connect()", __FILE__, __LINE__);
      if (xa->logLevel>=XMLBLASTER_LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, exception->message);
      return false;
   }

   if (xa->connectQos != 0) freeXmlBlasterQos(xa->connectQos);
   xa->connectQos = createXmlBlasterQos(connectQos->qos); /* take a clone */

   ret = _xmlBlasterReConnect(xa, clientUpdateFp, exception);

   /* return a clone */
   return (ret==0) ? 0 : createXmlBlasterReturnQos(strcpyAlloc(ret->returnQos));
}

static bool xmlBlasterDisconnect(XmlBlasterAccess *xa, const DisconnectQos * const disconnectQos, XmlBlasterException *exception)
{
   bool p;
   if (checkArgs(xa, "disconnect", true, exception) == false ) return 0;
   p = xa->connectionP->disconnect(xa->connectionP, (disconnectQos==0)?0:disconnectQos->qos, exception);
   if (checkPost(xa, "disconnect", 0, exception) == false ) return 0;
   return p;
}

/**
 * Publish a message to the server.
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @see XmlBlasterAccessUnparsed#publish() for a function documentation
 */
static PublishReturnQos *xmlBlasterPublish(XmlBlasterAccess *xa, MsgUnit *msgUnit, XmlBlasterException *exception)
{
	char *p;
   if (checkArgs(xa, "publish", true, exception) == false ) return 0;
   p = xa->connectionP->publish(xa->connectionP, msgUnit, exception);
   if (checkPost(xa, "publish", p, exception) == false ) return 0;
   return createXmlBlasterReturnQos(p);
}

/**
 * Publish a message array in a bulk to the server.
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @see XmlBlasterAccessUnparsed#publishArr() for a function documentation
 */
static PublishReturnQosArr *xmlBlasterPublishArr(XmlBlasterAccess *xa, MsgUnitArr *msgUnitArr, XmlBlasterException *exception)
{
   QosArr *p;
   if (checkArgs(xa, "publishArr", true, exception) == false ) return 0;
   p = xa->connectionP->publishArr(xa->connectionP, msgUnitArr, exception);
   if (checkPost(xa, "publishArr", p, exception) == false ) return 0;
   return createXmlBlasterReturnQosArr(p);
}

/**
 * Publish a message array in a bulk to the server, we don't receive an ACK.
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @see XmlBlasterAccessUnparsed#publishOneway() for a function documentation
 */
static void xmlBlasterPublishOneway(XmlBlasterAccess *xa, MsgUnitArr *msgUnitArr, XmlBlasterException *exception)
{
   if (checkArgs(xa, "publishOneway", true, exception) == false ) return;
   xa->connectionP->publishOneway(xa->connectionP, msgUnitArr, exception);
   if (checkPost(xa, "publishOneway", 0, exception)) return;
}

/**
 * Subscribe a message.
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.subscribe.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static SubscribeReturnQos *xmlBlasterSubscribe(XmlBlasterAccess *xa, const SubscribeKey * subscribeKey, const SubscribeQos * subscribeQos, XmlBlasterException *exception)
{
   char *p;
   if (checkArgs(xa, "subscribe", true, exception) == false ) return 0;
   p = xa->connectionP->subscribe(xa->connectionP, (subscribeKey==0)?0:subscribeKey->key, (subscribeQos==0)?0:subscribeQos->qos, exception);
   if (checkPost(xa, "subscribe", p, exception) == false ) return 0;
   return createXmlBlasterReturnQos(p);
}

/**
 * UnSubscribe a message from the server.
 * @return The raw QoS XML strings returned from xmlBlaster, only NULL if an exception is thrown
 *         You need to free it with freeQosArr() after usage
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static UnSubscribeReturnQosArr *xmlBlasterUnSubscribe(XmlBlasterAccess *xa, const UnSubscribeKey * unSubscribeKey, const UnSubscribeQos * unSubscribeQos, XmlBlasterException *exception)
{
   QosArr *p;
   if (checkArgs(xa, "unSubscribe", true, exception) == false ) return 0;
   p = xa->connectionP->unSubscribe(xa->connectionP, (unSubscribeKey==0)?0:unSubscribeKey->key, (unSubscribeQos==0)?0:unSubscribeQos->qos, exception);
   if (checkPost(xa, "unSubscribe", p, exception) == false ) return 0;
   return createXmlBlasterReturnQosArr(p);
}

/**
 * Erase a message from the server.
 * @return A struct holding the raw QoS XML strings returned from xmlBlaster,
 *         only NULL if an exception is thrown.
 *         You need to freeQosArr() it
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.erase.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static EraseReturnQosArr *xmlBlasterErase(XmlBlasterAccess *xa, const EraseKey * eraseKey, const EraseQos * eraseQos, XmlBlasterException *exception)
{
   QosArr *p;
   if (checkArgs(xa, "erase", true, exception) == false ) return 0;
   p = xa->connectionP->erase(xa->connectionP, (eraseKey==0)?0:eraseKey->key, (eraseQos==0)?0:eraseQos->qos, exception);
   if (checkPost(xa, "erase", p, exception) == false ) return 0;
   return createXmlBlasterReturnQosArr(p);
}

/**
 * Ping the server.
 * @param xa The 'this' pointer
 * @param qos The QoS or 0
 * @param exception *errorCode!=0 on failure
 * @return The ping return QoS raw xml string, you need to free() it
 *         or 0 on failure (in which case *exception.errorCode!=0)
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static PingReturnQos *xmlBlasterPing(XmlBlasterAccess *xa, const PingQos * pingQos, XmlBlasterException *exception)
{
   char *p;
   if (checkArgs(xa, "ping", true, exception) == false ) return 0;
   p = xa->connectionP->ping(xa->connectionP, (pingQos==0)?0:pingQos->qos, exception);
   if (checkPost(xa, "ping", p, exception) == false ) return 0;
   return createXmlBlasterReturnQos(p);
}

/**
 * Get a message.
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.get.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @return NULL on error, please check exception in such a case
 */
static MsgUnitArr *xmlBlasterGet(XmlBlasterAccess *xa, const GetKey * const getKey, const GetQos * getQos, XmlBlasterException *exception)
{
   MsgUnitArr *msgUnitArr;
   if (checkArgs(xa, "get", true, exception) == false ) return 0;
   msgUnitArr = xa->connectionP->get(xa->connectionP, (getKey==0)?0:getKey->key, (getQos==0)?0:getQos->qos, exception);
   if (checkPost(xa, "get", msgUnitArr, exception) == false ) return 0;
   return msgUnitArr;
}

/**
 * Post processing of remote calls: check connection status and lauch ping or poll thread.
 */
static bool checkPost(XmlBlasterAccess *xa, const char *methodName,
            void *returnObj, XmlBlasterException *exception)
{
   /* Success: No exception */
   if (exception == 0 || *exception->errorCode == 0) {
      if (!strcmp("initialize", methodName)) {
         /* raw socket connected */
         changeConnectionStateTo(xa, XBCONSTATE_SOCKALIVE, exception);
         return true;
      }
      if (xa->pingInterval > 0 && xa->connnectionState != XBCONSTATE_LOGGEDINALIVE
            && !strcmp("connect", methodName)) {
         if (xa->logLevel>=XMLBLASTER_LOG_INFO)
            xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_INFO,
                  __FILE__, "Connected to xmlBlaster server, pinging now every %ld milli seconds", xa->pingInterval);
         /* reset timer */
         //xa->pingPollTimer->setTimeoutListener(xa->pingPollTimer, onPingPollTimeout, 0, xa, (void*)&XBTYPE_PING);
         /* start pinging */
         xa->pingPollTimer->setTimeoutListener(xa->pingPollTimer, onPingPollTimeout, xa->pingInterval, xa, (void*)&XBTYPE_PING);
      }
      changeConnectionStateTo(xa, XBCONSTATE_LOGGEDINALIVE, exception);
      return true;
   }

   /* Exception occurred */
   if (xa->retries == -1 || xa->retries > 0) {
      /* start polling */
      if (xa->connnectionState != XBCONSTATE_POLLING) {
         if (xa->logLevel>=XMLBLASTER_LOG_WARN)
            xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_WARN,
                  __FILE__, "Lost server connection during %s, polling now every %ld milli seconds", methodName, xa->delay);
         _freeConnectionP(xa);
         changeConnectionStateTo(xa, XBCONSTATE_POLLING, exception);
         /* reset timer */
         //xa->pingPollTimer->setTimeoutListener(xa->pingPollTimer, onPingPollTimeout, 0, xa, (void*)&XBTYPE_POLL);
         /* start poller */
         xa->pingPollTimer->setTimeoutListener(xa->pingPollTimer, onPingPollTimeout, xa->delay, xa, (void*)&XBTYPE_POLL);
      }
   }
   else {
      if (xa->logLevel>=XMLBLASTER_LOG_ERROR)
         xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_ERROR,
               __FILE__, "Lost server connection during %s, giving up as no fail safe mode is configured (-dispatch/connection/retries -1).", methodName);
      /* stop timer */
      xa->pingPollTimer->setTimeoutListener(xa->pingPollTimer, onPingPollTimeout, 0, xa, (void*)&XBTYPE_POLL);
      changeConnectionStateTo(xa, XBCONSTATE_DEAD, exception);
   }

   if (returnObj != 0)
      printf("**** TODO Check if we shall free returnObj!!!\n");
   return false;
}

static bool checkArgs(XmlBlasterAccess *xa, const char *methodName,
            bool checkIsConnected, XmlBlasterException *exception)
{
   if (xa == 0) {
      char *stack = getStackTrace(10);
      if (exception == 0) {
         printf("[%s:%d] Please provide a valid XmlBlasterAccess pointer to %s() %s",
                  __FILE__, __LINE__, methodName, stack);
      }
      else {
         strncpy0(exception->errorCode, "user.illegalArgument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
         SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
                  "[%.100s:%d] Please provide a valid XmlBlasterAccess pointer to %.16s() %s",
                   __FILE__, __LINE__, methodName, stack);
         xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_ERROR, __FILE__, exception->message);
      }
      free(stack);
      return false;
   }

   if (exception == 0) {
      char *stack = getStackTrace(10);
      xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_ERROR, __FILE__, "[%s:%d] Please provide valid exception pointer to %s() %s",
              __FILE__, __LINE__, methodName, stack);
      free(stack);
      return false;
   }

   initializeXmlBlasterException(exception);

   if (strcmp("connect", methodName) && strcmp("initialize", methodName) && xa->connectionP == 0) {
      if (xa->retries == -1 || xa->retries > 0) {
         xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_WARN, __FILE__, "[%s:%d] We are polling %s",
              __FILE__, __LINE__, methodName);
      }
      else {
         char *stack = getStackTrace(10);
         xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_ERROR, __FILE__, "[%s:%d] No valid connectionP pointer %s() %s",
              __FILE__, __LINE__, methodName, stack);
         free(stack);
      }
      return false;
   }

   if (xa->isShutdown || (checkIsConnected && !xa->isConnected(xa))) {
      char *stack = getStackTrace(10);
      strncpy0(exception->errorCode, "communication.noConnection", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
               "[%.100s:%d] Not connected to xmlBlaster, %s() failed %s",
                __FILE__, __LINE__, methodName, stack);
      free(stack);
      xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_WARN, __FILE__, exception->message);
      return false;
   }

   return true;
}
