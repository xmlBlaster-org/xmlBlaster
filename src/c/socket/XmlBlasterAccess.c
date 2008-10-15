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

static bool checkArgs(XmlBlasterAccess *xa, const char *methodName,
            bool checkIsConnected, XmlBlasterException *exception);

static bool xmlBlasterIsStateOk(ReturnQos *returnQos);

static bool initialize(XmlBlasterAccess *xa, UpdateFp update, XmlBlasterException *exception);
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
   xa->isInitialized = false;
   xa->isShutdown = false;
   xa->connectionP = 0;
   xa->userObject = 0; /* A client can use this pointer to point to any client specific information */
   xa->userFp = 0;
   xa->connect = xmlBlasterConnect;
   xa->initialize = initialize;
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
   xa->logLevel = parseLogLevel(xa->props->getString(xa->props, "logLevel", "WARN"));
   xa->log = xmlBlasterDefaultLogging;
   xa->logUserP = 0;
   return xa;
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

   if (xa->connectionP != 0) {
      freeXmlBlasterAccessUnparsed(xa->connectionP);
      xa->connectionP = 0;
   }

   freeProperties(xa->props);
   free(xa);
}

static bool initialize(XmlBlasterAccess *xa, UpdateFp clientUpdateFp, XmlBlasterException *exception)
{
   if (checkArgs(xa, "initialize", false, exception) == false) return false;

   if (xa->isInitialized) {
      return true;
   }

   if (xa->connectionP) {
      freeXmlBlasterAccessUnparsed(xa->connectionP);
      xa->connectionP = 0;
   }
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


   if (xa->connectionP->initialize(xa->connectionP, clientUpdateFp, exception) == false) /* Establish low level IP connection */
      return false;

   xa->isInitialized = true;
   if (xa->logLevel>=XMLBLASTER_LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
                                "initialize() successful");
   return xa->isInitialized;
}

static bool isConnected(XmlBlasterAccess *xa)
{
   if (xa == 0 || xa->isShutdown || xa->connectionP == 0) {
      return false;
   }
   return xa->connectionP->isConnected(xa->connectionP);
}


Dll_Export const char *XmlBlasterAccessUsage(char *usage)
{
   /* take care not to exceed XMLBLASTER_MAX_USAGE_LEN */
   SNPRINTF(usage, XMLBLASTER_MAX_USAGE_LEN, "%.1600s,%.400s", xmlBlasterAccessUnparsedUsage(usage),
                  "\n   -retries                        -1"
                  "\n                       Number of connection retries, -1 is forever."
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
 * so you don't need to free qosXml enymore.
 * Freing of ReturnQos will free qosXml
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


static ConnectReturnQos *xmlBlasterConnect(XmlBlasterAccess *xa, const ConnectQos * connectQos,
                               UpdateFp clientUpdateFp, XmlBlasterException *exception)
{
   char *response = 0;

   if (checkArgs(xa, "connect", false, exception) == false) return 0;

   if (connectQos == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%.100s:%d] Please provide valid argument 'connectQos' to connect()", __FILE__, __LINE__);
      if (xa->logLevel>=XMLBLASTER_LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, exception->message);
      return false;
   }

   if (initialize(xa, clientUpdateFp, exception) == false) {
      return false;
   }

   if (xa->logLevel>=XMLBLASTER_LOG_TRACE) xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "Invoking connect()");

   /* Register our function responseEvent() to be notified when the response arrives,
      this is done by preSendEvent() callback called during connect() */

   response = xa->connectionP->connect(xa->connectionP, (connectQos==0)?0:connectQos->qos, clientUpdateFp, exception);

   if (response == 0) return 0;

   return createXmlBlasterReturnQos(response);
}

static bool xmlBlasterDisconnect(XmlBlasterAccess *xa, const DisconnectQos * const disconnectQos, XmlBlasterException *exception)
{
   bool p;
   if (checkArgs(xa, "disconnect", true, exception) == false ) return 0;
   p = xa->connectionP->disconnect(xa->connectionP, (disconnectQos==0)?0:disconnectQos->qos, exception);
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
   return msgUnitArr;
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
/*
   if (xa->connectionP == 0) {
      char *stack = getStackTrace(10);
      xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_ERROR, __FILE__, "[%s:%d] No valid connectionP pointer %s() %s",
              __FILE__, __LINE__, methodName, stack);
      free(stack);
      return false;
   }
*/
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

   initializeXmlBlasterException(exception);

   return true;
}
