/*----------------------------------------------------------------------------
Name:      xmlBlaster/demo/c/socket/HelloWorld3.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Example for all remote method invocations.
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   cd xmlBlaster; build c
           (Win: copy xmlBlaster\src\c\socket\pthreadVC2.dll to your PATH)
           Manually:
            cd xmlBlaster/src/c
            gcc -g -Wall -pedantic -Wno-long-long -D_REENTRANT -I. -o HelloWorld3 
                ../../demo/c/socket/HelloWorld3.c util/?*.c socket/?*.c -pthread
Invoke:    HelloWorld3 -help
See: http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <XmlBlasterAccessUnparsed.h>

/**
 * Here we receive the callback messages from xmlBlaster
 * @param msgUnitArr The received messages
 * @param userData Is the corresponding XmlBlasterAccessUnparsed * pointer
 * @param exception An OUT parameter to transport back an exception
 * @see UpdateFp in XmlBlasterAccessUnparsed.h
 * @see UpdateFp in CallbackServerUnparsed.h
 */
static bool myUpdate(MsgUnitArr *msgUnitArr, void *userData,
                     XmlBlasterException *exception)
{
   size_t i;
   bool testException = false;
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)userData;
   if (userData != 0) ;  /* Supress compiler warning */

   for (i=0; i<msgUnitArr->len; i++) {
      char *xml = messageUnitToXml(&msgUnitArr->msgUnitArr[i]);
      printf("[client] CALLBACK update(): Asynchronous message update arrived:%s\n",
             xml);
      xmlBlasterFree(xml);
      msgUnitArr->msgUnitArr[i].responseQos = 
                  strcpyAlloc("<qos><state id='OK'/></qos>");
      /* Return QoS: Everything is OK */
   }
   if (testException) {
      strncpy0(exception->errorCode, "user.clientCode",
               XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      strncpy0(exception->message, "I don't want these messages",
               XMLBLASTEREXCEPTION_MESSAGE_LEN);
      return false;
   }

   if (xa->callbackMultiThreaded == true) {
      /* publish from inside the update thread,
         see -plugin/socket/multiThreaded true */
      char *response = (char *)0;
      MsgUnit msgUnit;
      XmlBlasterException xmlBlasterException;
      memset(&msgUnit, 0, sizeof(MsgUnit));
      printf("[client] Publishing message 'HelloWorldCb from update thread' ...\n");
      msgUnit.key = strcpyAlloc("<key oid='HelloWorldCb'/>");
      msgUnit.content = strcpyAlloc("Some message payload");
      msgUnit.contentLen = strlen(msgUnit.content);
      msgUnit.qos =strcpyAlloc("<qos><persistent/></qos>");
      response = xa->publish(xa, &msgUnit, &xmlBlasterException);
      freeMsgUnitData(&msgUnit);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in publish errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
      printf("[client] Publish success, returned status is '%s'\n", response);
      xmlBlasterFree(response);
   }
 
   return true;
}

#if defined(WINCE)
int _tmain(int argc, _TCHAR** argv_wcs) { /* wchar_t==_TCHAR */
   char **argv = convertWcsArgv(argv_wcs, argc);
#else
/**
 * Invoke: HelloWorld3 -logLevel TRACE
 */
int main(int argc, const char* const* argv) {
#endif
   int iarg;
   char *response = (char *)0;
   /*
      * callbackSessionId:
      * Is created by the client and used to validate callback messages in update. 
      * This is sent on connect in ConnectQos.
      * (Is different from the xmlBlaster secret session ID)
      */
   const char *callbackSessionId = "topSecret";
   XmlBlasterException xmlBlasterException;
   XmlBlasterAccessUnparsed *xa = 0;
   int sleepInterval = 0;

   printf("[client] XmlBlaster %s C SOCKET client, try option '-help' if you need"
          " usage informations\n", getXmlBlasterVersion());

   for (iarg=0; iarg < argc; iarg++) {
      if (strcmp(argv[iarg], "-help") == 0 || strcmp(argv[iarg], "--help") == 0) {
         char usage[XMLBLASTER_MAX_USAGE_LEN];
         const char *pp =
         "\n   -sleepInterval      Milliseconds to wait on callback messages [0]"
         "\n\nExample:"
         "\n  HelloWorld3 -logLevel TRACE"
         " -dispatch/connection/plugin/socket/hostname 192.168.2.9"
         " -sleepInterval 100000";
         printf("Usage:\nXmlBlaster C SOCKET client %s\n%s%s\n",
                  getXmlBlasterVersion(), xmlBlasterAccessUnparsedUsage(usage), pp);
         exit(EXIT_FAILURE);
      }
      else if (strcmp(argv[iarg], "-sleepInterval") == 0 && iarg < argc-1)
         sleepInterval = atoi(argv[++iarg]);
   }

   xa = getXmlBlasterAccessUnparsed(argc, (const char* const*)argv);
   if (xa->initialize(xa, myUpdate, &xmlBlasterException) == false) {
      printf("[client] Connection to xmlBlaster failed,"
             " please start the server or check your configuration\n");
      freeXmlBlasterAccessUnparsed(xa);
      exit(EXIT_FAILURE);
   }

   {  /* connect */
      char connectQos[2048];
      char callbackQos[1024];
      sprintf(callbackQos,
               "<queue relating='callback' maxEntries='50000' maxEntriesCache='10000'>"
               "  <callback type='SOCKET' sessionId='%s'>"
               "    socket://%.120s:%d"
               "  </callback>"
               "</queue>",
               callbackSessionId, xa->callbackP->hostCB, xa->callbackP->portCB);
      sprintf(connectQos,
               "<qos>"
               " <securityService type='htpasswd' version='1.0'>"
               "  <![CDATA["
               "   <user>fritz</user>"
               "   <passwd>secret</passwd>"
               "  ]]>"
               " </securityService>"
               "%.1024s"
               "</qos>", callbackQos);

      response = xa->connect(xa, connectQos, myUpdate, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception during connect errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
      xmlBlasterFree(response);
      printf("[client] Connected to xmlBlaster, do some tests ...\n");
   }

   response = xa->ping(xa, 0, &xmlBlasterException);
   if (response == (char *)0) {
      printf("[client] ERROR: Pinging a connected server failed: errorCode=%s, message=%s\n",
             xmlBlasterException.errorCode, xmlBlasterException.message);
      freeXmlBlasterAccessUnparsed(xa);
      exit(EXIT_FAILURE);
   }
   else {
      printf("[client] Pinging a connected server, response=%s\n", response);
      xmlBlasterFree(response);
   }

   { /* subscribe ... */
      const char *key = "<key oid='HelloWorld'/>";
      /*const char *key = "<key queryType='XPATH'>//key</key>";*/
      const char *qos = "<qos/>";
      printf("[client] Subscribe message 'HelloWorld' ...\n");
      response = xa->subscribe(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in subscribe errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
      printf("[client] Subscribe success, returned status is '%s'\n", response);
      xmlBlasterFree(response);
   }
   
   if (sleepInterval > 0) {
      printf("[client] Sleeping now and wait on 'HelloWorld' updates (start a publisher somewhere) ...\n");
      sleepMillis(sleepInterval);
   }

   {  /* publish ... */
      MsgUnit msgUnit;
      memset(&msgUnit, 0, sizeof(MsgUnit));
      printf("[client] Publishing message 'HelloWorld' ...\n");
      msgUnit.key = strcpyAlloc("<key oid='HelloWorld'/>");
      msgUnit.content = strcpyAlloc("Some message payload");
      msgUnit.contentLen = strlen(msgUnit.content);
      msgUnit.qos =strcpyAlloc("<qos><persistent/></qos>");
      response = xa->publish(xa, &msgUnit, &xmlBlasterException);
      freeMsgUnitData(&msgUnit);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in publish errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
      printf("[client] Publish success, returned status is '%s'\n", response);
      xmlBlasterFree(response);
   }
 
   if (true) {  /* publishArr */
      QosArr* resp;
      MsgUnitArr holder;
      memset(&holder, 0, sizeof(MsgUnitArr));
      printf("[client] Publishing messages 'HelloWorld0' and 'HelloWorld1' ...\n");
      holder.len = 2;
      holder.msgUnitArr = (MsgUnit *)calloc(holder.len, sizeof(MsgUnit));
      holder.msgUnitArr[0].key = strcpyAlloc("<key oid='HelloWorld0'/>");
      holder.msgUnitArr[0].content = strcpyAlloc("Some message payload");
      holder.msgUnitArr[0].contentLen = strlen(holder.msgUnitArr[0].content);
      holder.msgUnitArr[0].qos =strcpyAlloc("<qos><persistent/></qos>");

      holder.msgUnitArr[1].key = strcpyAlloc("<key oid='HelloWorld1'/>");
      holder.msgUnitArr[1].content = strcpyAlloc("Some message payload");
      holder.msgUnitArr[1].contentLen = strlen(holder.msgUnitArr[1].content);
      holder.msgUnitArr[1].qos =strcpyAlloc("<qos><persistent/></qos>");

      resp = xa->publishArr(xa, &holder, &xmlBlasterException);
      
      freeMsgUnitArrInternal(&holder);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in publishArr errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
      if (resp) {
         size_t i;
         for (i=0; i<resp->len; i++) {
            printf("[client] PublishArr success, returned status is '%s'\n", resp->qosArr[i]);
         }
         freeQosArr(resp);
      }
   }
 
   if (true) {  /* publishOneway */
      MsgUnitArr holder;
      memset(&holder, 0, sizeof(MsgUnitArr));
      printf("[client] Publishing oneway messages 'HelloWorld0' and 'HelloWorld1' ...\n");
      holder.len = 2;
      holder.msgUnitArr = (MsgUnit *)calloc(holder.len, sizeof(MsgUnit));
      holder.msgUnitArr[0].key = strcpyAlloc("<key oid='HelloWorld0'/>");
      holder.msgUnitArr[0].content = strcpyAlloc("Some message payload");
      holder.msgUnitArr[0].contentLen = strlen(holder.msgUnitArr[0].content);
      holder.msgUnitArr[0].qos =strcpyAlloc("<qos><persistent/></qos>");

      holder.msgUnitArr[1].key = strcpyAlloc("<key oid='HelloWorld1'/>");
      holder.msgUnitArr[1].content = strcpyAlloc("Some message payload");
      holder.msgUnitArr[1].contentLen = strlen(holder.msgUnitArr[1].content);
      holder.msgUnitArr[1].qos =strcpyAlloc("<qos><persistent/></qos>");

      xa->publishOneway(xa, &holder, &xmlBlasterException);
      
      freeMsgUnitArrInternal(&holder);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in publishOneway errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
   }
 
   {  /* unSubscribe ... */
      QosArr* resp;
      const char *key = "<key oid='HelloWorld'/>";
      /*const char *key = "<key queryType='XPATH'>//key</key>";*/
      const char *qos = "<qos/>";
      printf("[client] UnSubscribe message 'HelloWorld' ...\n");
      resp = xa->unSubscribe(xa, key, qos, &xmlBlasterException);
      if (resp) {
         size_t i;
         for (i=0; i<resp->len; i++) {
            printf("[client] Unsubscribe success, returned status is '%s'\n", resp->qosArr[i]);
         }
         freeQosArr(resp);
      }
      else {
         printf("[client] Caught exception in unSubscribe errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
   }

   {  /* get synchronous ... */
      size_t i;
      /*const char *key = "<key oid='HelloWorld'/>";*/
      const char *key = "<key queryType='XPATH'>//key</key>";
      const char *qos = "<qos/>";
      MsgUnitArr *msgUnitArr;
      printf("[client] Get synchronous messages with XPath '//key' ...\n");
      msgUnitArr = xa->get(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in get errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
      if (msgUnitArr != (MsgUnitArr *)0) {
         for (i=0; i<msgUnitArr->len; i++) {
            char *m = messageUnitToXmlLimited(&msgUnitArr->msgUnitArr[i], 100);
            printf("\n[client] Get synchronous returned message#%lu/%lu:\n"
                     "-------------------------------------"
                     "%s\n"
                     "-------------------------------------\n",
                     (unsigned long)(i+1), (unsigned long)msgUnitArr->len, m);
            xmlBlasterFree(m);
         }
         freeMsgUnitArr(msgUnitArr);
      }
      else {
         printf("[client] Caught exception in get errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
   }


   {  /* erase ... */
      QosArr* resp;
      const char *key = "<key oid='HelloWorld'/>";
      /*const char *key = "<key oid='' queryType='XPATH'>//key</key>";*/
      const char *qos = "<qos/>";
      printf("[client] Erasing message 'HelloWorld' ...\n");
      resp = xa->erase(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in erase errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
      if (resp != 0) {
         size_t i;
         for (i=0; i<resp->len; i++) {
            printf("[client] Erase success, returned status is '%s'\n", resp->qosArr[i]);
         }
         freeQosArr(resp);
      }
   }

   sleepMillis(200); /* To allow the callback thread to publish */

   if (xa->disconnect(xa, 0, &xmlBlasterException) == false) {
      printf("[client] Caught exception in disconnect, errorCode=%s, message=%s\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
      freeXmlBlasterAccessUnparsed(xa);
      exit(EXIT_FAILURE);
   }

   freeXmlBlasterAccessUnparsed(xa);
   printf("[client] Good bye.\n");
#  if defined(WINCE)
      freeArgv(argv, argc);
#  endif
   return 0;
}

