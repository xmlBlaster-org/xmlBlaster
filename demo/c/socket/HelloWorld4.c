/*----------------------------------------------------------------------------
 Name:      xmlBlaster/demo/c/socket/HelloWorld4.c
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 Comment:   Example for all remote method invocations.
 Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
 Compile:   cd xmlBlaster; build c
 (Win: copy xmlBlaster\src\c\socket\pthreadVC2.dll to your PATH)
 Manually:
 cd xmlBlaster/src/c
 gcc -g -Wall -pedantic -Wno-long-long -D_REENTRANT -I. -o HelloWorld4
 ../../demo/c/socket/HelloWorld4.c util/?*.c socket/?*.c -pthread
 Invoke:    HelloWorld4 -help
 See: http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 -----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <XmlBlasterAccess.h>

/**
 * Here we receive the callback messages from xmlBlaster
 * @param xa 'this' pointer
 * @param msgUnitArr The received messages, it is freed by the call after this method ends
 * @param exception An OUT parameter to transport back an exception
 * @see UpdateFp in XmlBlasterAccess.h
 * @see UpdateFp in CallbackServerUnparsed.h
 */
static bool myUpdate(struct XmlBlasterAccess* xa, MsgUnitArr *msgUnitArr,
      XmlBlasterException *exception) {
   size_t i;
   bool testException = false;

   for (i = 0; i < msgUnitArr->len; i++) {
      char *xml = messageUnitToXml(&msgUnitArr->msgUnitArr[i]);
      printf(
            "[client4] CALLBACK update(): %p Asynchronous message update arrived:%s\n",
            (void *) xa, xml);
      xmlBlasterFree(xml);
      msgUnitArr->msgUnitArr[i].responseQos = strcpyAlloc(
            "<qos><state id='OK'/></qos>");
      /* Return QoS: Everything is OK */
   }
   if (testException) {
      strncpy0(exception->errorCode, "user.clientCode",
            XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      strncpy0(exception->message, "I don't want these messages",
            XMLBLASTEREXCEPTION_MESSAGE_LEN);
      return false;
   }

   return true;
}

static void connectionListenerCb(struct XmlBlasterAccess *xa,
      XBCONSTATE oldState, XBCONSTATE newState, XmlBlasterException *exception,
      void *userData) {
   const char *conStateUserData = (const char *) userData;
   const char *errorCode = (exception == 0 || exception->errorCode == 0) ? ""
         : exception->errorCode;
   const char *message = (exception == 0 || exception->message == 0) ? ""
         : exception->message;
   printf("%p [%s] connectionListenerCb transition %s to %s %s %s\n",
         (void *) xa, conStateUserData, connectionStateToStr(oldState),
         connectionStateToStr(newState), errorCode, message);
}

#if defined(WINCE)
int _tmain(int argc, _TCHAR** argv_wcs) { /* wchar_t==_TCHAR */
   char **argv = convertWcsArgv(argv_wcs, argc);
#else
/**
 * Invoke: HelloWorld4 -logLevel TRACE
 */
int main(int argc, const char* const * argv) {
#endif
   int iarg;

   /*
    * callbackSessionId:
    * Is created by the client and used to validate callback messages in update.
    * This is sent on connect in ConnectQos.
    * (Is different from the xmlBlaster secret session ID)
    */
   const char *callbackSessionId = "topSecret";
   const char *conStateUserData = "client4";
   XmlBlasterException xmlBlasterException;
   XmlBlasterAccess *xa = 0;
   int sleepInterval = 0;
   char inchar;

   printf(
         "[client4] XmlBlaster %s C SOCKET client, try option '-help' if you need"
            " usage informations\n", getXmlBlasterVersion());

   for (iarg = 0; iarg < argc; iarg++) {
      if (strcmp(argv[iarg], "-help") == 0 || strcmp(argv[iarg], "--help") == 0) {
         char usage[XMLBLASTER_MAX_USAGE_LEN];
         const char *pp =
               "\n   -sleepInterval      Milliseconds to wait on callback messages [0]"
                  "\n\nExample:"
                  "\n  HelloWorld4 -logLevel TRACE"
                  " -dispatch/connection/plugin/socket/hostname 192.168.2.9"
                  " -sleepInterval 100000";
         printf("Usage:\nXmlBlaster C SOCKET client %s\n%s%s\n",
               getXmlBlasterVersion(), XmlBlasterAccessUsage(usage), pp);
         exit(EXIT_FAILURE);
      } else if (strcmp(argv[iarg], "-sleepInterval") == 0 && iarg < argc - 1)
         sleepInterval = atoi(argv[++iarg]);
   }

   xa = getXmlBlasterAccess(argc, (const char* const *) argv);
   xa->registerConnectionListener(xa, connectionListenerCb,
         (void*) conStateUserData);

   { /* connect */
      ConnectQos *connectQos;
      ConnectReturnQos *connectReturnQos;
      char connectQosStr[2048];
      char callbackQos[1024];
      sprintf(callbackQos,
            "<queue relating='callback' maxEntries='50000' maxEntriesCache='10000'>"
               "  <callback type='SOCKET' sessionId='%.120s' pingInterval='30000'>"
               "    socket://127.0.0.1"
               "  </callback>"
               "</queue>", callbackSessionId);
      sprintf(connectQosStr, "<qos>"
         " <securityService type='htpasswd' version='1.0'>"
         "  <![CDATA["
         "   <user>fritz</user>"
         "   <passwd>secret</passwd>"
         "  ]]>"
         " </securityService>"
         "  <session name='client/fritz' timeout='0' maxSessions='1'/>"
         "%.1024s"
         "</qos>", callbackQos);

      connectQos = createXmlBlasterQos(connectQosStr);
      connectReturnQos = xa->connect(xa, connectQos, myUpdate,
            &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         if (startsWith(xmlBlasterException.errorCode, "user.security")) {
            printf(
                  "[client4] Caught exception during connect, giving up: errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
            freeXmlBlasterQos(connectQos);
            freeXmlBlasterAccess(xa);
            exit(EXIT_FAILURE);
         }
         printf(
               "[client4] Caught exception during connect errorCode=%s, message=%s we continue in fail safe mode\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
      } else
         printf("[client4] Connected to xmlBlaster, do some tests ...\n");
      freeXmlBlasterQos(connectQos);
      freeXmlBlasterReturnQos(connectReturnQos);
   }

   while ((inchar = getInputKey("Hit 'c' to continue or 'q' to quit")) != 'c') {
      if (inchar == 'q') {
         freeXmlBlasterAccess(xa);
         exit(0);
      }
   }

   { /* ping */
      PingReturnQos *pingReturn = xa->ping(xa, 0, &xmlBlasterException);
      if (pingReturn == 0) {
         printf(
               "[client4] ERROR: Pinging a connected server failed: errorCode=%s, message=%s\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterAccess(xa);
         exit(EXIT_FAILURE);
      } else {
         printf("[client4] Pinging a connected server, response=%s\n",
               pingReturn->returnQos);
         freeXmlBlasterReturnQos(pingReturn);
      }
   }

   { /* subscribe ... */
      SubscribeKey *key = createXmlBlasterKey("<key oid='HelloWorld'/>");
      /*const char *key = "<key queryType='XPATH'>//key</key>";*/
      SubscribeQos *qos = createXmlBlasterQos(0); /* "<qos/>" */
      SubscribeReturnQos *returnQos = 0;
      printf("[client4] Subscribe message 'HelloWorld' ...\n");
      returnQos = xa->subscribe(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf(
               "[client4] Caught exception in subscribe errorCode=%s, message=%s\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterKey(key);
         freeXmlBlasterQos(qos);
         freeXmlBlasterAccess(xa);
         exit(EXIT_FAILURE);
      }
      printf("[client4] Subscribe success, returned status is '%s' isOk=%d\n",
            returnQos->returnQos, returnQos->isOk(returnQos));
      freeXmlBlasterKey(key);
      freeXmlBlasterQos(qos);
      freeXmlBlasterReturnQos(returnQos);
   }

   if (sleepInterval > 0) {
      printf(
            "[client4] Sleeping now and wait on 'HelloWorld' updates (start a publisher somewhere) ...\n");
      sleepMillis(sleepInterval);
   }

   { /* publish ... */
      MsgUnit msgUnit;
      PublishReturnQos *returnQos = 0;
      memset(&msgUnit, 0, sizeof(MsgUnit));
      printf("[client4] Publishing message 'HelloWorld' ...\n");
      msgUnit.key = strcpyAlloc("<key oid='HelloWorld'/>");
      msgUnit.content = strcpyAlloc("Some message payload");
      msgUnit.contentLen = strlen(msgUnit.content);
      msgUnit.qos = strcpyAlloc("<qos><persistent/></qos>");
      returnQos = xa->publish(xa, &msgUnit, &xmlBlasterException);
      freeMsgUnitData(&msgUnit);
      if (*xmlBlasterException.errorCode != 0) {
         printf(
               "[client4] Caught exception in publish errorCode=%s, message=%s\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterReturnQos(returnQos);
         freeXmlBlasterAccess(xa);
         exit(EXIT_FAILURE);
      }
      printf("[client4] Publish success, returned status is '%s'\n",
            returnQos->returnQos);
      freeXmlBlasterReturnQos(returnQos);
   }

   if (true) { /* publishArr */
      PublishReturnQosArr* resp = 0;
      MsgUnitArr holder;
      memset(&holder, 0, sizeof(MsgUnitArr));
      printf(
            "[client4] Publishing messages 'HelloWorld0' and 'HelloWorld1' ...\n");
      holder.len = 2;
      holder.msgUnitArr = (MsgUnit *) calloc(holder.len, sizeof(MsgUnit));
      holder.msgUnitArr[0].key = strcpyAlloc("<key oid='HelloWorld0'/>");
      holder.msgUnitArr[0].content = strcpyAlloc("Some message payload");
      holder.msgUnitArr[0].contentLen = strlen(holder.msgUnitArr[0].content);
      holder.msgUnitArr[0].qos = strcpyAlloc("<qos><persistent/></qos>");

      holder.msgUnitArr[1].key = strcpyAlloc("<key oid='HelloWorld1'/>");
      holder.msgUnitArr[1].content = strcpyAlloc("Some message payload");
      holder.msgUnitArr[1].contentLen = strlen(holder.msgUnitArr[1].content);
      holder.msgUnitArr[1].qos = strcpyAlloc("<qos><persistent/></qos>");

      resp = xa->publishArr(xa, &holder, &xmlBlasterException);

      freeMsgUnitArrInternal(&holder);
      if (*xmlBlasterException.errorCode != 0) {
         printf(
               "[client4] Caught exception in publishArr errorCode=%s, message=%s\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterReturnQosArr(resp);
         freeXmlBlasterAccess(xa);
         exit(EXIT_FAILURE);
      }
      if (resp) {
         size_t i;
         for (i = 0; i < resp->len; i++) {
            printf("[client4] PublishArr success, returned status is '%s'\n",
                  resp->returnQosArr[i].returnQos);
         }
         freeXmlBlasterReturnQosArr(resp);
      }
   }

   if (true) { /* publishOneway */
      MsgUnitArr holder;
      memset(&holder, 0, sizeof(MsgUnitArr));
      printf(
            "[client4] Publishing oneway messages 'HelloWorld0' and 'HelloWorld1' ...\n");
      holder.len = 2;
      holder.msgUnitArr = (MsgUnit *) calloc(holder.len, sizeof(MsgUnit));
      holder.msgUnitArr[0].key = strcpyAlloc("<key oid='HelloWorld0'/>");
      holder.msgUnitArr[0].content = strcpyAlloc("Some message payload");
      holder.msgUnitArr[0].contentLen = strlen(holder.msgUnitArr[0].content);
      holder.msgUnitArr[0].qos = strcpyAlloc("<qos><persistent/></qos>");

      holder.msgUnitArr[1].key = strcpyAlloc("<key oid='HelloWorld1'/>");
      holder.msgUnitArr[1].content = strcpyAlloc("Some message payload");
      holder.msgUnitArr[1].contentLen = strlen(holder.msgUnitArr[1].content);
      holder.msgUnitArr[1].qos = strcpyAlloc("<qos><persistent/></qos>");

      xa->publishOneway(xa, &holder, &xmlBlasterException);

      freeMsgUnitArrInternal(&holder);
      if (*xmlBlasterException.errorCode != 0) {
         printf(
               "[client4] Caught exception in publishOneway errorCode=%s, message=%s\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccess(xa);
         exit(EXIT_FAILURE);
      }
   }

   { /* unSubscribe ... */
      UnSubscribeReturnQosArr* resp;
      UnSubscribeKey *key = createXmlBlasterKey("<key oid='HelloWorld'/>");
      UnSubscribeQos *qos = createXmlBlasterQos(0);
      printf("[client4] UnSubscribe message 'HelloWorld' ...\n");
      resp = xa->unSubscribe(xa, key, qos, &xmlBlasterException);
      if (resp) {
         size_t i;
         for (i = 0; i < resp->len; i++) {
            printf("[client4] Unsubscribe success, returned status is '%s'\n",
                  resp->returnQosArr[i].returnQos);
         }
         freeXmlBlasterKey(key);
         freeXmlBlasterQos(qos);
         freeXmlBlasterReturnQosArr(resp);
      } else {
         printf(
               "[client4] Caught exception in unSubscribe errorCode=%s, message=%s\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterKey(key);
         freeXmlBlasterQos(qos);
         freeXmlBlasterAccess(xa);
         exit(EXIT_FAILURE);
      }
   }

   { /* get synchronous ... */
      size_t i;
      /*const char *key = "<key oid='HelloWorld'/>";*/
      GetKey *key = createXmlBlasterKey("<key queryType='XPATH'>//key</key>");
      GetQos *qos = createXmlBlasterQos("<qos/>");
      MsgUnitArr *msgUnitArr;
      printf("[client4] Get synchronous messages with XPath '//key' ...\n");
      msgUnitArr = xa->get(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client4] Caught exception in get errorCode=%s, message=%s\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccess(xa);
         exit(EXIT_FAILURE);
      }
      if (msgUnitArr != (MsgUnitArr *) 0) {
         for (i = 0; i < msgUnitArr->len; i++) {
            char *m = messageUnitToXmlLimited(&msgUnitArr->msgUnitArr[i], 100);
            printf("\n[client4] Get synchronous returned message#%lu/%lu:\n"
               "-------------------------------------"
               "%s\n"
               "-------------------------------------\n", (unsigned long) (i
                  + 1), (unsigned long) msgUnitArr->len, m);
            xmlBlasterFree(m);
         }
         freeXmlBlasterKey(key);
         freeXmlBlasterQos(qos);
         freeMsgUnitArr(msgUnitArr);
      } else {
         printf("[client4] Caught exception in get errorCode=%s, message=%s\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterKey(key);
         freeXmlBlasterQos(qos);
         freeXmlBlasterAccess(xa);
         exit(EXIT_FAILURE);
      }
   }

   { /* erase ... */
      EraseReturnQosArr *resp;
      EraseKey *key = createXmlBlasterKey("<key oid='HelloWorld'/>");
      EraseQos *qos = createXmlBlasterQos("<qos/>");
      printf("[client4] Erasing message 'HelloWorld' ...\n");
      resp = xa->erase(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf(
               "[client4] Caught exception in erase errorCode=%s, message=%s\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccess(xa);
         exit(EXIT_FAILURE);
      }
      if (resp != 0) {
         size_t i;
         for (i = 0; i < resp->len; i++) {
            printf("[client4] Erase success, returned status is '%s'\n",
                  resp->returnQosArr[i].returnQos);
         }
         freeXmlBlasterKey(key);
         freeXmlBlasterQos(qos);
         freeXmlBlasterReturnQosArr(resp);
      }
   }

   sleepMillis(200); /* To allow the callback thread to publish */

   if (xa->disconnect(xa, 0, &xmlBlasterException) == false) {
      printf(
            "[client4] Caught exception in disconnect, errorCode=%s, message=%s\n",
            xmlBlasterException.errorCode, xmlBlasterException.message);
      freeXmlBlasterAccess(xa);
      exit(EXIT_FAILURE);
   }

   freeXmlBlasterAccess(xa);
   printf("[client4] Good bye.\n");
#  if defined(WINCE)
   freeArgv(argv, argc);
#  endif
   return 0;
}

