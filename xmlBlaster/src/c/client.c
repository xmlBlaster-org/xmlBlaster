/*----------------------------------------------------------------------------
Name:      client.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   client connects with raw socket to xmlBlaster
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   gcc -Wall -g -D_REENTRANT -I. -o client client.c msgUtil.c socket/*.c -lpthread
Compile-Win: cl /MT /W3 /Wp64 -D_WINDOWS -I. client.c msgUtil.c socket\*.c ws2_32.lib
Invoke:    client -dispatch/callback/plugin/socket/hostname develop -dispatch/callback/plugin/socket/port 7607 -debug true
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
Date:      05/2003
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#ifdef _WINDOWS
#else
#  include <unistd.h>
#  include <pthread.h>
#endif
#include <XmlBlasterAccessUnparsed.h>
#include <CallbackServerUnparsed.h>

static bool update(MsgUnitArr *msgUnitArr, XmlBlasterException *xmlBlasterException);
static bool debug = false;
static bool help = false;


/**
 * Test the baby
 */
int main(int argc, char** argv)
{
#ifndef _WINDOWS
   pthread_t tid;
#endif
   int iarg;
   char *response = (char *)0;
   bool startCallback = false;
   const char *callbackSessionId = "topSecret";
   XmlBlasterException xmlBlasterException;
   XmlBlasterAccessUnparsed *xb = 0;
   CallbackServerUnparsed *cb = 0;

   printf("[client] Try option '-help' if you need usage informations\n");

   for (iarg=0; iarg < argc; iarg++) {
      if (strcmp(argv[iarg], "-help") == 0 || strcmp(argv[iarg], "--help") == 0)
         help = true;
   }
   if (help) {
      const char *pp =
      "\n  -startCallback       true/false [false]"
      "\n  -debug               true/false [false]"
      "\n\nExample:"
      "\n  client -debug true -startCallback true -dispatch/connection/plugin/socket/hostname server.mars.universe";
      printf("Usage:\n%s%s%s\n", xmlBlasterAccessUnparsedUsage(), callbackServerRawUsage(), pp);
      exit(1);
   }

   for (iarg=0; iarg < argc-1; iarg++) {
      if (strcmp(argv[iarg], "-startCallback") == 0)
         startCallback = !strcmp(argv[++iarg], "true");
      else if (strcmp(argv[iarg], "-debug") == 0)
         debug = !strcmp(argv[++iarg], "true");
   }

   xb = getXmlBlasterAccessUnparsed(argc, argv);
   if (xb == (XmlBlasterAccessUnparsed *)0) {
      printf("[client] Connection failed, please start xmlBlaster server first\n");
      exit(1);
   }
   xb->debug = debug;

#  ifndef _WINDOWS
   if (startCallback) {
      int ret;
      cb = getCallbackServerUnparsed(argc, argv, update);
      cb->useThisSocket(cb, xb->socketToXmlBlaster);
      strncpy0(cb->secretSessionId, callbackSessionId, MAX_SECRETSESSIONID_LEN);
      ret = pthread_create(&tid, 0, (cbFp)cb->initCallbackServer, cb);
      sleep(1);
      printf("[client] Created callback server thread listening on 'socket://%s:%d'\n", cb->hostCB, cb->portCB);
   }
#  endif

   if (xb->ping(xb, 0) == (char *)0) {
      printf("[client] Pinging a not connected server failed -> this is OK\n");
   }
   else {
      printf("[client] ERROR: Pinging a not connected server should not be possible\n");
   }

   {  // connect
      char connectQos[2048];
      char callbackQos[1024];
      if (startCallback) {
         sprintf(callbackQos,
                "<queue relating='callback' maxEntries='100'>"
                "  <callback type='SOCKET' sessionId='%s'>"
                "    socket://%s:%d"
                "  </callback>"
                "</queue>", cb->secretSessionId, cb->hostCB, cb->portCB);
      }
      else {
         *callbackQos = 0;
      }
      sprintf(connectQos,
             "<qos>"
             " <securityService type='htpasswd' version='1.0'>"
             "  <![CDATA["
             "   <user>fritz</user>"
             "   <passwd>secret</passwd>"
             "  ]]>"
             " </securityService>"
             "%s"
             "</qos>", callbackQos);

      response = xb->connect(xb, connectQos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception during connect errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
      free(response);
      printf("[client] Connected to xmlBlaster, do some tests ...\n");
   }

   {
      response = xb->ping(xb, 0);
      if (response == (char *)0) {
         printf("[client] ERROR: Pinging a connected server failed\n");
      }
      else {
         printf("[client] Pinging a connected server, response=%s\n", response);
         free(response);
      }
   }

   if (startCallback) { // subscribe ...
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("[client] Subscribe message 'HelloWorld' ...\n");
      response = xb->subscribe(xb, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in subscribe errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
      printf("[client] Subscribe success, returned status is '%s'\n", response);
      free(response);
   }

   {  // publish ...
      MsgUnit msgUnit;
      printf("[client] Publishing message 'HelloWorld' ...\n");
      msgUnit.key = "<key oid='HelloWorld'/>";
      msgUnit.content = "Some message payload";
      msgUnit.contentLen = strlen("Some message payload");
      msgUnit.qos = "<qos><persistent/></qos>";
      response = xb->publish(xb, &msgUnit, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in publish errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
      printf("[client] Publish success, returned status is '%s'\n", response);
      free(response);
   }

   {  // unSubscribe ...
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("[client] UnSubscribe message 'HelloWorld' ...\n");
      response = xb->unSubscribe(xb, key, qos, &xmlBlasterException);
      if (response) {
         printf("[client] Unsbscribe success, returned status is '%s'\n", response);
         free(response);
      }
      else {
         printf("[client] Caught exception in unSubscribe errorCode=%s, message=%s\n", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
   }

   {  // get synchnronous ...
      size_t i;
      const char *key = "<key queryType='XPATH'>//key</key>";
      const char *qos = "<qos/>";
      MsgUnitArr *msgUnitArr;
      printf("[client] Get synchronous messages with XPath '//key' ...\n");
      msgUnitArr = xb->get(xb, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in get errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
      if (msgUnitArr != (MsgUnitArr *)0) {
         for (i=0; i<msgUnitArr->len; i++) {
            char *contentStr = strFromBlobAlloc(msgUnitArr->msgUnitArr[i].content, msgUnitArr->msgUnitArr[i].contentLen);
            const char *dots = (msgUnitArr->msgUnitArr[i].contentLen > 96) ? " ..." : "";
            printf("\n[client] Received message#%u/%u:\n"
                   "-------------------------------------"
                   "%s\n <content>%.100s%s</content>%s\n"
                   "-------------------------------------\n",
                   i+1, msgUnitArr->len,
                   msgUnitArr->msgUnitArr[i].key,
                   contentStr, dots,
                   msgUnitArr->msgUnitArr[i].qos);
            free(contentStr);
         }
         freeMsgUnitArr(msgUnitArr);
      }
      else {
         printf("[client] Caught exception in get errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
   }


   {  // erase ...
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("[client] Erasing message 'HelloWorld' ...\n");
      response = xb->erase(xb, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in erase errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
      printf("[client] Erase success, returned status is '%s'\n", response);
      free(response);
   }

#  ifndef _WINDOWS
   if (startCallback) {
      printf("[client] Going to sleep 10 seconds as a callback server is active ...\n");
      sleep(10);
      printf("[client] Shutdown callback server 'socket://%s:%d'\n", cb->hostCB, cb->portCB);
      cb->shutdown(cb);
   }
#  endif

   {  // disconnect ...
      if (xb->disconnect(xb, 0, &xmlBlasterException) == false) {
         printf("[client] Caught exception in disconnect, errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
   }

   freeCallbackServerUnparsed(cb);
   freeXmlBlasterAccessUnparsed(xb);
   printf("[client] Good bye.\n");
   exit(0);
}


/**
 * Here we receive the asynchronous callback from xmlBlaster
 *
 * NOTE:
 * After this call the memory of msgUnitArr is freed immediately by callbackServer.c
 * You need to take a copy of all message members if needed out of the scope
 * of this function.
 *
 * @see UpdateQos description in CallackServerUnparsed.h
 */
static bool update(MsgUnitArr *msgUnitArr, XmlBlasterException *xmlBlasterException)
{
   size_t i;

   for (i=0; i<msgUnitArr->len; i++) {
      // Do something useful with the arrived message, here we dump it as XML
      MsgUnit *msgUnit = &msgUnitArr->msgUnitArr[i];
      char *xml = messageUnitToXml(msgUnit);
      printf("[client-CallbackThread-update()] Asynchronous message update arrived:\n%s\n", xml);
      free(xml);
      
      if (!msgUnitArr->isOneway)
         msgUnit->responseQos = strcpyAlloc("<qos/>"); // Return QoS: Everything is OK
   }

   if (false) { // How to throw an exception
      strncpy0(xmlBlasterException->errorCode, "user.notWanted", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      strncpy0(xmlBlasterException->message, "I don't want these messages", XMLBLASTEREXCEPTION_MESSAGE_LEN);
      return false;
   }

   return true;
}


